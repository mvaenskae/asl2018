package ch.ethz.asltest.Memcached;

import ch.ethz.asltest.Utilities.Misc.MiscHelper;
import ch.ethz.asltest.Utilities.PacketParser;
import ch.ethz.asltest.Utilities.WorkQueue;
import ch.ethz.asltest.Utilities.WorkUnit.*;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.net.StandardSocketOptions.TCP_NODELAY;

public final class Worker implements Callable<Object> {

    // Static fields shared by all workers
    private final static AtomicBoolean stopFlag = new AtomicBoolean();
    private static WorkQueue workQueue;
    private static List<InetSocketAddress> memcachedServers;
    private static boolean isSharded;

    // Instance-based fields
    private final Logger logger;
    private final int id;

    // Initialized by static properties
    private final Map<String, PacketParser> packetParsers = new HashMap<>(memcachedServers.size());
    private final Map<SelectionKey, Integer> fairnessMap = new HashMap<>(memcachedServers.size());

    // Initialized variably
    private WorkUnit workItem;
    private Selector memcachedSelector;
    private Selector memtierSelector;

    private int invalidHeaders = 0;

    private int[] sendCounters = new int[Worker.memcachedServers.size()];

    // Local fields for statistics
    private int getCounter = 0;
    private int setCounter = 0;

    private final WorkerStats workerStats = new WorkerStats();

    /**
     * Sets the workQueue statically which instances are referring to.
     *
     * @param queue workQueue to be used by instances.
     */
    public static void setWorkQueue(final WorkQueue queue)
    {
        Worker.workQueue = queue;
    }

    /**
     * Sets the list of memcached servers each instance is connecting to.
     *
     * @param memcachedServers List of memcached servers used by instances.
     */
    public static void setMemcachedServers(final List<InetSocketAddress> memcachedServers)
    {
        Worker.memcachedServers = memcachedServers;
    }

    /**
     * Sets the GET requests as sharded for each instance of this class.
     *
     * @param isSharded Set the GET reuqests as sharded.
     */
    public static void setIsSharded(final boolean isSharded)
    {
        Worker.isSharded = isSharded;
    }

    /**
     * Empty instantiation procedure, currently does nothing really...
     */
    public Worker(final int i)
    {
        this.id = i;
        this.logger = LogManager.getLogger(Worker.class + "-" + i);
    }

    @Override
    public Object call() throws IOException
    {
        try {
            this.initWorkTask();
        } catch (Exception e) {
            this.logger.fatal("Couldn't initialize worker. Will stop execution on it...");
            this.logger.error(e.getMessage());
            return null;
        }

        try {
            while (!stopFlag.get()) {
                this.workItem = Worker.workQueue.get();
                switch (this.workItem.type) {
                    case SET:
                        this.logger.log(Level.INFO, "Got 'set' from queue.");
                        this.handleSetRequest();
                        break;
                    case GET:
                        this.logger.log(Level.INFO, "Got 'get' from queue.");
                        this.handleGetRequest();
                        break;
                    case INVALID:
                        this.logger.log(Level.INFO, "Invalid request on the queue.");
                        ++invalidHeaders;
                        // Fallthrough
                    default:
                        break;
                }
            }
        } catch (InterruptedException e) {
            this.logger.log(Level.INFO, "Worker {} was interrupted.", this.id);
        } finally {
            // Cleanup for thread termination
            for (SelectionKey sKey : this.memcachedSelector.keys()) {
                sKey.channel().close();
                sKey.cancel();
            }
            this.memcachedSelector.close();
        }

        //TODO: Here generate statistics and return them in an object/struct/whatevs
        return null;
    }

    private void handleGetRequest() throws IOException
    {
        // TODO: Logging here
        // TODO: Refactor this... *sigh*
        this.workItem.sendBackTo.register(this.memtierSelector, SelectionKey.OP_WRITE);
        Map<SelectionKey, Integer> expectedResponseCount;
        WorkUnitGet getRequest = (WorkUnitGet) this.workItem;

        Map<SelectionKey, List<ByteBuffer>> requestMap = new HashMap<>();
        List<ByteBuffer> request = new ArrayList<>();

        if (Worker.isSharded) {
            // Balance number of requests to send out
            expectedResponseCount = this.loadBalancer(getRequest.keys.size());

            /*
             * Update fairness of memcached servers used, update number of expected responses from servers with "END"
             * and generate a List of each memcached server's requests. Additionally only register relevant servers.
             */
            int iterator = 0;
            for (Map.Entry<SelectionKey, Integer> entry : expectedResponseCount.entrySet()) {
                request = new ArrayList<>(entry.getValue());
                if (entry.getValue() != 0) {
                    request.add(ByteBuffer.wrap("get ".getBytes()));
                    addToInterestSet(entry.getKey(), SelectionKey.OP_WRITE);
                }
                for (int i = iterator; i < (entry.getValue() + iterator); ++i) {
                    if (i + 1 != (entry.getValue() + iterator)) {
                        ByteBuffer temp = ByteBuffer.allocate(getRequest.keys.get(i).length + 1);
                        temp.put(getRequest.keys.get(i)).put((byte) ' ');
                        temp.flip();
                        request.add(temp);
                    } else {
                        ByteBuffer temp = ByteBuffer.allocate(getRequest.keys.get(i).length + 2);
                        temp.put(getRequest.keys.get(i)).put((byte) '\r').put((byte) '\n');
                        temp.flip();
                        request.add(temp);
                    }
                }
                iterator += entry.getValue();
                requestMap.put(entry.getKey(), request);

                this.fairnessMap.merge(entry.getKey(), entry.getValue(), Integer::sum);
                expectedResponseCount.merge(entry.getKey(), (entry.getValue() == 0) ? 0 : 1, Integer::sum);
            }
        } else {
            // Set the selected channel to writeable for the memcached server used
            SelectionKey memcachedSocket = getNextServer();
            addToInterestSet(memcachedSocket, SelectionKey.OP_WRITE);

            // Put the number of expected reponses explicitly for each memcached server connected to
            expectedResponseCount = new HashMap<>(Worker.memcachedServers.size());
            this.fairnessMap.keySet().forEach(sKey -> expectedResponseCount.put(sKey, 0));
            expectedResponseCount.merge(memcachedSocket, getRequest.keys.size() + 1, Integer::sum);

            // Update the load on the servers, assumes the requests will get through
            this.fairnessMap.merge(memcachedSocket, getRequest.keys.size(), Integer::sum);

            // Generate the request used for the memcached server
            request.add(ByteBuffer.wrap(getRequest.header));
            requestMap.put(memcachedSocket, request);
        }

        List<WorkUnit> replies = memcachedCommunication(WorkUnitType.GET, requestMap, expectedResponseCount);
        request.clear();

        for (WorkUnit unit : replies) {
            switch (unit.type) {
                case VALUE:
                    request.add(ByteBuffer.wrap(unit.getHeader()));
                    request.add(ByteBuffer.wrap(unit.getBody()));
                    break;
                case END:
                    break;
                case INVALID:
                    this.logger.log(Level.FATAL, "memcached returned invalid packet");
                    request.clear();
                    request.add(ByteBuffer.wrap(WorkUnitError.header));
                    memtierCommunication(request);
                    return;
                default:
                    request.clear();
                    request.add(ByteBuffer.wrap(unit.getHeader()));
                    memtierCommunication(request);
                    return;
            }
        }

        request.add(ByteBuffer.wrap(WorkUnitEnd.header));
        memtierCommunication(request);
    }

    /**
     * Handle an SET query by memtier, distribute it to all memcached clients and send back the reply to memtier.
     *
     * @throws IOException On communication failure.
     */
    private void handleSetRequest() throws IOException
    {
        // TODO: Logging here
        this.workItem.sendBackTo.register(this.memtierSelector, SelectionKey.OP_WRITE);
        WorkUnitSet setRequest = (WorkUnitSet) this.workItem;

        Map<SelectionKey, List<ByteBuffer>> requestMap = new HashMap<>(Worker.memcachedServers.size());

        List<ByteBuffer> request = new ArrayList<>();
        request.add(ByteBuffer.wrap(setRequest.header));
        request.add(ByteBuffer.wrap(setRequest.body));

        // Generate requests for each memcached server
        this.fairnessMap.keySet().forEach(sKey -> requestMap.put(sKey, MiscHelper.shallowCopy(request)));

        // Number of expected reponses for each memcached server connected to and mark them all active
        HashMap<SelectionKey, Integer> expectedResponseCount = new HashMap<>(Worker.memcachedServers.size());
        this.memcachedSelector.keys().forEach(sKey -> {
                    expectedResponseCount.put(sKey, 1);
                    addToInterestSet(sKey, SelectionKey.OP_WRITE);
                }
        );

        List<WorkUnit> replies = memcachedCommunication(WorkUnitType.SET, requestMap, expectedResponseCount);
        request.clear();

        for (WorkUnit unit : replies) {
            switch (unit.type) {
                case STORED:
                    break;
                case INVALID:
                    this.logger.log(Level.FATAL, "memcached returned invalid request!");
                    request.add(ByteBuffer.wrap(WorkUnitError.header));
                    memtierCommunication(request);
                    return;
                default:
                    request.add(ByteBuffer.wrap(unit.getHeader()));
                    memtierCommunication(request);
                    return;
            }
        }

        // We checked all headers to be of type WorkUnitStored, we definitely have one therefore.
        request.add(ByteBuffer.wrap(WorkUnitStored.header));
        memtierCommunication(request);
    }

    /**
     * Send non-blocking with Memtier client. Communication is dependent on the number of elements passed.
     *
     * Requires: Requires: interestOps of all required keys set appropriately (for this.memtierSelector).
     * Ensures: interestOps of all keys consumed (i.e. set to state prior to setting them up).
     *
     * @param toSend List of ByteBuffer which are to be sent.
     */
    private void memtierCommunication(List<ByteBuffer> toSend) throws IOException
    {
        // TODO: Logging here
        boolean responseSent = false;
        SelectionKey sKeyUsed = null;

        while (!responseSent) {
            this.memtierSelector.select();

            Set<SelectionKey> selectedKeys = this.memtierSelector.selectedKeys();
            Iterator<SelectionKey> iter = selectedKeys.iterator();

            while (iter.hasNext()) {
                SelectionKey key = iter.next();
                iter.remove();

                if (key.isValid() && key.isWritable()) {
                    if (!(key.attachment() instanceof ArrayList)) {
                        key.attach(toSend);
                    }
                    responseSent = memtierWriteBytes((ArrayList<ByteBuffer>) key.attachment(), key);
                    sKeyUsed = key;
                }
            }
        }

        removeFromInterestSet(sKeyUsed, SelectionKey.OP_WRITE);
    }

    /**
     * Send and receive non-blocking until as many WorkUnits as requested have been returned with memcached servers.
     *
     * Requires: interestOps of all required keys set appropriately (for this.memcachedSelector).
     * Ensures: interestOps of all keys consumed (i.e. set to state prior to setting them up).
     *
     * @param toSend List of ByteBuffers to send.
     * @param expectedResponsesCount Number of expected responses.
     * @return List of replies from memcached servers.
     * @throws IOException Upon communication failure.
     */
    private List<WorkUnit> memcachedCommunication(WorkUnitType type, Map<SelectionKey, List<ByteBuffer>> toSend, Map<SelectionKey, Integer> expectedResponsesCount) throws IOException
    {
        // TODO: Logging here
        int numberOfResponses = expectedResponsesCount.values().stream().mapToInt(i -> i.intValue()).sum();

        List<WorkUnit> result = new ArrayList<>(numberOfResponses);
        int actualResponseCount = 0;

        while (actualResponseCount < numberOfResponses) {
            try {
                this.memcachedSelector.select();

                Set<SelectionKey> selectedKeys = this.memcachedSelector.selectedKeys();
                Iterator<SelectionKey> iter = selectedKeys.iterator();

                while (iter.hasNext()) {
                    SelectionKey key = iter.next();
                    iter.remove();

                    if (!key.isValid()) {
                        continue;
                    }

                    if (key.isWritable()) {
                        /*
                         * The key is writable but due to the nature of requiring a consumable element to send we must
                         * exchange the object.
                         */

                        if (!(key.attachment() instanceof ArrayList)) {
                            List<ByteBuffer> elementsToSend = toSend.get(key);
                            key.attach(elementsToSend);
                        }
                        memcachedWriteBytes((List<ByteBuffer>) key.attachment(), key);
                    }

                    if (key.isReadable()) {
                        List<WorkUnit> completeRequest = ((PacketParser) key.attachment()).receiveAndParse(key);

                        if (completeRequest != null) {
                            if (expectedResponsesCount.get(key) > 0 && expectedResponsesCount.get(key) - completeRequest.size() >= 0) {
                                // Only add if our current expected is within range!
                                expectedResponsesCount.put(key, expectedResponsesCount.get(key) - completeRequest.size());
                                actualResponseCount += completeRequest.size();
                                result.addAll(completeRequest);
                                if (type == WorkUnitType.GET && expectedResponsesCount.get(key) > 0) {
                                    if (result.stream().anyMatch(unit -> unit.type == WorkUnitType.END)) {
                                        actualResponseCount += expectedResponsesCount.get(key);
                                        expectedResponsesCount.put(key, 0);
                                    }
                                }

                                this.logger.log(Level.DEBUG, "Reply from memcached server {} of {} replies.", ((SocketChannel) key.channel()).getRemoteAddress(), completeRequest.size());
                                removeFromInterestSet(key, SelectionKey.OP_READ);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                this.logger.log(Level.ERROR, "Communication failed with memcached.");
                throw e;
            }
        }

        return result;
    }

    /**
     * Stateful method to send data to a recipient and upon success reattach to the key the respective PacketParser.
     *
     * Requires: True
     * Ensures: key is not listening to write anymore.
     *
     * @param toSend List of ByteBuffer's to send.
     * @param key Key over which to send (uses the underlying socketChannel for it).
     * @throws IOException IOError during the attempt to write to the channel.
     */
    private boolean memtierWriteBytes(List<ByteBuffer> toSend, SelectionKey key) throws IOException
    {
        // TODO: Logging here
        ByteBuffer buffer = writeBytes(toSend, key);

        if (toSend.isEmpty() && buffer != null && !buffer.hasRemaining()) {
            removeFromInterestSet(key, SelectionKey.OP_WRITE);
            return true;
        }
        return false;
    }

    /**
     * Stateful method to send data to a recipient and upon success reattach to the key the respective PacketParser.
     *
     * Requires: True
     * Ensures: key is not listening to write anymore.
     *
     * @param toSend List of ByteBuffer's to send.
     * @param key Key over which to send (uses the underlying socketChannel for it).
     * @throws IOException IOError during the attempt to write to the channel.
     */
    private void memcachedWriteBytes(List<ByteBuffer> toSend, SelectionKey key) throws IOException
    {
        // TODO: Logging here
        ByteBuffer buffer = writeBytes(toSend, key);

        if (toSend.isEmpty() && buffer != null && !buffer.hasRemaining()) {
            // Everything has been sent at this point, reattach the key's packetParser.
            reattachPacketParser(key);
            removeFromInterestSet(key, SelectionKey.OP_WRITE);
            addToInterestSet(key, SelectionKey.OP_READ);
            this.logger.log(Level.DEBUG, "Sent request to {}.", ((SocketChannel) key.channel()).getRemoteAddress());
        }
    }

    /**
     * Method which will send the list of ByteBuffers to the key's channel and returns the state as a tuple.
     *
     * Requires: True
     * Ensures: If data not sent, configure key's interestSet for writing.
     *
     * @param toSend List of ByteBuffers to send.
     * @param key Key on which to send.
     * @return Tuple of the state of this method.
     */
    private ByteBuffer writeBytes(List<ByteBuffer> toSend, SelectionKey key) throws IOException
    {
        // TODO: Logging here
        ByteBuffer buffer = null;
        Iterator<ByteBuffer> iterator = toSend.iterator();

        while (iterator.hasNext()) {
            buffer = iterator.next();
            ((SocketChannel) key.channel()).write(buffer);

            if (!buffer.hasRemaining()) {
                iterator.remove();
            }
        }

        return buffer;
    }

    /**
     * Helper method to reattach the packetParser back to the key to allow reading data correctly.
     * @param key SelectionKey which gets its PacketParser back (which still holds the previous state).
     */
    private void reattachPacketParser(SelectionKey key)
    {
        try {
            key.attach(this.packetParsers.get(((SocketChannel) key.channel()).getRemoteAddress().toString()));
        } catch (IOException e) {
            this.logger.log(Level.ERROR, "Couldn't reattach channel's PacketParser.");
            e.printStackTrace();
        }
    }

    private void removeFromInterestSet(SelectionKey key, int operation)
    {
        key.interestOps(key.interestOps() & ~operation);
    }

    private void addToInterestSet(SelectionKey key, int operation)
    {
        key.interestOps(key.interestOps() | operation);
    }

    /**
     * Helper method for single invocations of fixed-length input to know which memcached gets the query next based on
     * fairness.
     */
    private SelectionKey getNextServer()
    {
        int minimum = Collections.min(this.fairnessMap.values());
        SelectionKey last = null;
        for (Map.Entry<SelectionKey, Integer> entry : this.fairnessMap.entrySet()) {
            if (entry.getValue() == minimum) {
                return entry.getKey();
            }
            last = entry.getKey();
        }

        this.logger.log(Level.ERROR, "Couldn't find minimum in Map of memcached servers. Getting some server.");
        return last;
    }

    /**
     * Helper method for single invocations of fixed-length input to know which memcached gets the query next based on
     * fairness.
     */
    private Map<SelectionKey, Integer> loadBalancer(int numberOfRequests)
    {
        int availableRequests = numberOfRequests;
        HashMap<SelectionKey, Integer> result = new HashMap<>(Worker.memcachedServers.size());
        this.fairnessMap.keySet().forEach(sKey -> result.put(sKey, 0));

        if (Worker.memcachedServers.size() == 1) {
            SelectionKey temp = getNextServer();
            result.merge(temp, 1, Integer::sum);
        } else if (Worker.memcachedServers.size() == 2) {
            Map.Entry<SelectionKey, Integer> min, max;
            ArrayList<Map.Entry<SelectionKey, Integer>> arrayEntry = new ArrayList<>(2);
            arrayEntry.addAll(this.fairnessMap.entrySet());

            if (arrayEntry.get(0).getValue() > arrayEntry.get(1).getValue()) {
                min = arrayEntry.get(1);
                max = arrayEntry.get(0);
            } else {
                min = arrayEntry.get(0);
                max = arrayEntry.get(1);
            }

            int deltaMaxMin = max.getValue() - min.getValue();

            if (deltaMaxMin >= availableRequests) {
                // We cannot even fairly distribute to one server... Try best effort
                this.logger.log(Level.TRACE, "Load balancing unsuccessful. Best effort following.");
                result.merge(min.getKey(), availableRequests, Integer::sum);
                return result;
            }

            availableRequests -= deltaMaxMin;
            result.merge(min.getKey(), deltaMaxMin, Integer::sum);

            int splitMin = availableRequests / 2;
            int splitMax = ((availableRequests % 2) == 1) ? splitMin + 1 : splitMin;
            this.logger.log(Level.TRACE, "Load balancing sharded reads amongs memcached servers.");
            result.merge(min.getKey(), splitMin, Integer::sum);
            result.merge(max.getKey(), splitMax, Integer::sum);
        } else {
            Map.Entry<SelectionKey, Integer> min, mid, max, temp;
            ArrayList<Map.Entry<SelectionKey, Integer>> arrayEntry = new ArrayList<>(3);
            arrayEntry.addAll(this.fairnessMap.entrySet());

            if (arrayEntry.get(1).getValue() > arrayEntry.get(2).getValue()) {
                temp = arrayEntry.get(1);
                arrayEntry.set(1, arrayEntry.get(2));
                arrayEntry.set(2, temp);
            }
            if (arrayEntry.get(0).getValue() > arrayEntry.get(2).getValue()) {
                temp = arrayEntry.get(0);
                arrayEntry.set(0, arrayEntry.get(2));
                arrayEntry.set(2, temp);
            }
            if (arrayEntry.get(0).getValue() > arrayEntry.get(1).getValue()) {
                temp = arrayEntry.get(0);
                arrayEntry.set(0, arrayEntry.get(1));
                arrayEntry.set(1, temp);
            }

            max = arrayEntry.get(2);
            mid = arrayEntry.get(1);
            min = arrayEntry.get(0);

            int deltaMidMin = mid.getValue() - min.getValue();

            if (deltaMidMin >= availableRequests) {
                // We cannot even fairly distribute to one server... Try best effort
                this.logger.log(Level.TRACE, "Load balancing unsuccessful. Best effort following.");
                result.merge(min.getKey(), availableRequests, Integer::sum);
                return result;
            }

            availableRequests -= deltaMidMin;
            result.merge(min.getKey(), deltaMidMin, Integer::sum);

            int deltaMaxMid = max.getValue() - mid.getValue();
            int splitMin, splitMid, splitMax;
            if (deltaMaxMid >= availableRequests) {
                // We cannot balance nicely the rest of requests to the two smallest buckets
                splitMin = deltaMaxMid / 2;
                splitMid = ((deltaMaxMid % 2) == 1) ? splitMin + 1 : splitMin;
                this.logger.log(Level.TRACE, "Load balancing unsuccessful. Best effort following.");
                result.merge(min.getKey(), splitMin, Integer::sum);
                result.merge(mid.getKey(), splitMid, Integer::sum);
                return result;
            }

            availableRequests -= deltaMaxMid;

            if (availableRequests % 3 == 0) {
                splitMin = splitMid = splitMax = availableRequests / 3;
            } else if (availableRequests % 3 == 1) {
                splitMin = availableRequests / 3 + 1;
                splitMid = splitMax = splitMin - 1;
            } else {
                splitMin = splitMid = availableRequests / 3 + 1;
                splitMax = splitMin - 1;
            }
            this.logger.log(Level.TRACE, "Load balancing sharded reads amongst memcached servers.");
            result.merge(min.getKey(), splitMin, Integer::sum);
            result.merge(mid.getKey(), splitMid, Integer::sum);
            result.merge(max.getKey(), splitMax, Integer::sum);
        }

        return result;
    }

    /**
     * Initializes each Worker such that it is ready for communication with the memcached servers.
     */
    private void initWorkTask() throws IOException
    {
        SocketChannel socketChannel;
        PacketParser packetParser;
        String remoteAddress;

        // The instance's selector used for non-blocking IO with memcached and memtier
        this.memcachedSelector = Selector.open();
        this.memtierSelector = Selector.open();
        int interestSet = 0; // Per default we are not interested in anything for the registered SocketChannel

        for (InetSocketAddress memcachedServer : Worker.memcachedServers) {
            // Create a socketChannel that is already connected
            socketChannel = SocketChannel.open(memcachedServer);
            socketChannel.configureBlocking(false);
            socketChannel.setOption(TCP_NODELAY, true);

            while (socketChannel.isConnectionPending()) {
                socketChannel.finishConnect();
            }

            // Generate a packetParser for the socketChannel
            remoteAddress = socketChannel.getRemoteAddress().toString();
            packetParser = new PacketParser(remoteAddress);
            this.packetParsers.put(remoteAddress, packetParser);

            // Register the socketChannel to this instances' memcachedSelector
            this.fairnessMap.put(socketChannel.register(this.memcachedSelector, interestSet, packetParser), 0);
            this.logger.info("Thread {} connected to server {}", this.id, memcachedServer.toString());
        }
    }
}
