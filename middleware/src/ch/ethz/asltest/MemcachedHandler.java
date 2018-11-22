package ch.ethz.asltest;

import ch.ethz.asltest.Utilities.Misc.Tuple;
import ch.ethz.asltest.Utilities.Packets.PacketParser;
import ch.ethz.asltest.Utilities.Statistics.Containers.WorkerStatistics;
import ch.ethz.asltest.Utilities.WorkQueue;
import ch.ethz.asltest.Utilities.Packets.WorkUnit.*;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.net.StandardSocketOptions.TCP_NODELAY;

public final class MemcachedHandler implements Callable<WorkerStatistics> {

    // Static fields shared by all workers
    private final static AtomicBoolean stopFlag = new AtomicBoolean(); // stops the instance (if true)
    private static WorkQueue workQueue; // reference to queue from which to pop memtier requests from
    private static List<InetSocketAddress> memcachedServers; // list of all memcached servers
    private static boolean isSharded; // boolean which sets the memcached selector to sharded reads (if true)

    // Instance-based fields
    private final Logger logger; // local logging reference
    private final int id; // id of instance

    // Instance-local fields, initialized at compile-time
    private final WorkerStatistics workerStats = new WorkerStatistics();
    // map of packet parsers bound to each communicating party (required for attachment purposes and limits GC)
    private final Map<String, PacketParser> packetParsers = new HashMap<>(memcachedServers.size());
    // map keeping track of usage per server
    // TODO: USE THIS AS LOAD BALANCER (ugly but we only return the total statistics for it)
    private final Map<SelectionKey, Integer> fairnessMap = new HashMap<>(memcachedServers.size());

    // Instance-local fields, initialized at run-time
    private WorkUnit workItem; // reference to local request to be handled
    private Selector memcachedSelector; // selector used for selecting active keys for communicating with servers
    private Selector memtierSelector; // selector used for selecting active keys for communicating with clients

    /**
     * Sets the workQueue statically which instances are referring to.
     *
     * @param queue workQueue to be used by instances.
     */
    public static void setWorkQueue(final WorkQueue queue)
    {
        MemcachedHandler.workQueue = queue;
    }

    /**
     * Sets the list of memcached servers each instance is connecting to.
     *
     * @param memcachedServers List of memcached servers used by instances.
     */
    public static void setMemcachedServers(final List<InetSocketAddress> memcachedServers)
    {
        MemcachedHandler.memcachedServers = memcachedServers;
    }

    /**
     * Sets the GET requests as sharded for each instance of this class.
     *
     * @param isSharded Set the GET reuqests as sharded.
     */
    public static void setIsSharded(final boolean isSharded)
    {
        MemcachedHandler.isSharded = isSharded;
    }

    /**
     * Set the status of the stopFlag (which will stop the thread from popping any requests from the workqueue.
     * @param isStopped Should workers stop accepting new units of work?
     */
    public static void setStopFlag(final boolean isStopped)
    {
        stopFlag.set(isStopped);
    }

    // Constructor

    /**
     * Empty instantiation procedure, currently does nothing really...
     */
    public MemcachedHandler(final int i)
    {
        this.id = i;
        this.logger = LogManager.getLogger(MemcachedHandler.class + "-" + i);
    }

    // Instance methods

    @Override
    public WorkerStatistics call() throws IOException
    {
        try {
            this.initWorkTask();
        } catch (Exception e) {
            logger.log(Level.FATAL, "MAIN: Couldn't initialize worker. Will stop execution on it...");
            logger.log(Level.ERROR, e.getMessage());
            return null;
        }

        try {
            while (!stopFlag.get()) {
                logger.log(Level.DEBUG, "Trying to pop request from queue...");
                this.workItem = MemcachedHandler.workQueue.get();
                switch (this.workItem.type) {
                    case SET:
                        this.workerStats.setElement.incrementOpCounter(this.workItem.timestamp.getPopFromQueue());
                        this.workerStats.setElement.addAverageWaitingTimeQueue(this.workItem);
                        this.logger.log(Level.INFO, "MAIN: Popped SET.");
                        this.handleSetRequest();
                        break;
                    case GET:
                        int keyCount = ((WorkUnitGet) this.workItem).keys.size();
                        if (keyCount > 1) {
                            this.workerStats.multiGetElement.incrementOpCounter(
                                    this.workItem.timestamp.getPopFromQueue());
                            this.workerStats.multiGetElement.recordKeySize(this.workItem.timestamp.getPopFromQueue(),
                                    keyCount);
                            this.workerStats.multiGetElement.addAverageWaitingTimeQueue(this.workItem);
                            this.logger.log(Level.INFO, "MAIN: Popped multiGET ({}).", keyCount);
                        } else {
                            this.workerStats.getElement.incrementOpCounter(this.workItem.timestamp.getPopFromQueue());
                            this.workerStats.getElement.addAverageWaitingTimeQueue(this.workItem);
                            this.logger.log(Level.INFO, "MAIN: Popped GET.");
                        }
                        this.handleGetRequest();
                        break;
                    case INVALID:
                        this.workerStats.invalidPacketCounter(this.workItem.timestamp.getPopFromQueue());
                        this.logger.log(Level.WARN, "MAIN: Popped INVALID.");
                        break;
                    default:
                        this.logger.log(Level.FATAL, "MAIN: Popped UNKNOWN!");
                        break;
                }
            }
        } catch (InterruptedException e) {
            this.logger.log(Level.WARN,
                    "MAIN: Interrupted, cleaning up and disconnecting all attached participants...");
        } finally {
            // Cleanup for thread termination
            for (SelectionKey sKey : this.memcachedSelector.keys()) {
                sKey.channel().close();
                sKey.cancel();
            }
            // TODO: Save the final statistics of fairness for GETs on each worker
            this.memcachedSelector.close();
        }

        return this.workerStats;
    }

    private Map<SelectionKey, Integer> generateShardedGetRequest(Map<SelectionKey, ByteBuffer> requestMap)
    {
        logger.log(Level.DEBUG, "GET: Preparing sharded request for memcached.");
        WorkUnitGet getRequest = (WorkUnitGet) this.workItem;

        // Balance number of requests to send out
        Map<SelectionKey, Integer> expectedResponseCount = this.loadBalancer(getRequest.keys.size());

        /*
         * Update fairness of memcached servers used, update number of expected responses from servers with "END"
         * and generate a List of each memcached server's requests. Additionally only register relevant servers.
         */
        int iterator = 0;

        for (Map.Entry<SelectionKey, Integer> entry : expectedResponseCount.entrySet()) {

            // List to store keys in for the current memcache server (its Selectionkey to be specific)
            ArrayList<ByteBuffer> request = new ArrayList<>(entry.getValue());

            // If this server expect load, mark the SelectionKey as writeable and add the packet preamble.
            if (entry.getValue() != 0) {
                request.add(ByteBuffer.wrap("get ".getBytes()));
                addToInterestSet(entry.getKey(), SelectionKey.OP_WRITE);
            }

            // Iterate over all keys and pick on a FCFS basis (single use only)
            for (int i = iterator; i < (entry.getValue() + iterator); ++i) {
                if (i + 1 != (entry.getValue() + iterator)) {
                    ByteBuffer temp = ByteBuffer.allocate(getRequest.keys.get(i).length + 1);
                    temp.put(getRequest.keys.get(i)).put((byte) ' ').flip();
                    request.add(temp);
                } else {
                    ByteBuffer temp = ByteBuffer.allocate(getRequest.keys.get(i).length + 2);
                    temp.put(getRequest.keys.get(i)).put((byte) '\r').put((byte) '\n').flip();
                    request.add(temp);
                }
            }

            iterator += entry.getValue();
            requestMap.put(entry.getKey(), getAsSingleByteBuffer(request));

            this.fairnessMap.merge(entry.getKey(), entry.getValue(), Integer::sum);
            expectedResponseCount.merge(entry.getKey(), (entry.getValue() == 0) ? 0 : 1, Integer::sum);
        }

        logger.log(Level.DEBUG, "GET: Generated sharded request for memcached.");
        return expectedResponseCount;
    }

    private Map<SelectionKey, Integer> generateGetRequest(Map<SelectionKey, ByteBuffer> requestMap)
    {
        logger.log(Level.DEBUG, "GET: Preparing normal request for memcached.");
        WorkUnitGet getRequest = (WorkUnitGet) this.workItem;

        // Set the selected channel to writeable for the memcached server used
        SelectionKey memcachedSocket = getNextServer();
        addToInterestSet(memcachedSocket, SelectionKey.OP_WRITE);

        // Put the number of expected reponses explicitly for each memcached server connected to
        Map<SelectionKey, Integer> expectedResponseCount = new HashMap<>(MemcachedHandler.memcachedServers.size());
        this.fairnessMap.keySet().forEach(sKey -> expectedResponseCount.put(sKey, 0));
        expectedResponseCount.merge(memcachedSocket, getRequest.keys.size() + 1, Integer::sum);

        // Update the load on the servers, assumes the requests will get through
        this.fairnessMap.merge(memcachedSocket, getRequest.keys.size(), Integer::sum);

        // Generate the request used for the memcached server
        ByteBuffer request = ByteBuffer.allocateDirect(getRequest.header.length);
        request.put(getRequest.header).flip();

        // Remember which server is to respond based on the SelectionKey and how much
        requestMap.put(memcachedSocket, request);

        logger.log(Level.DEBUG, "GET: Generated normal request for memcached.");
        return expectedResponseCount;
    }

    private void handleGetRequest() throws IOException
    {
        this.workItem.sendBackTo.register(this.memtierSelector, SelectionKey.OP_WRITE);
        Map<SelectionKey, Integer> expectedResponseCount;
        Map<SelectionKey, ByteBuffer> requestMap = new HashMap<>();

        if (MemcachedHandler.isSharded) {
            expectedResponseCount = generateShardedGetRequest(requestMap);
        } else {
            expectedResponseCount = generateGetRequest(requestMap);
        }

        logger.log(Level.DEBUG, "GET: Sending request to memcached...");
        List<WorkUnit> replies = memcachedCommunication(WorkUnitType.GET, requestMap, expectedResponseCount);
        logger.log(Level.DEBUG, "GET: Received reply, checking results.");
        ByteBuffer replyBuffer;
        List<ByteBuffer> reply = new ArrayList<>();

        for (WorkUnit unit : replies) {
            switch (unit.type) {
                case VALUE:
                    reply.add(ByteBuffer.wrap(unit.getHeader()));
                    reply.add(ByteBuffer.wrap(unit.getBody()));
                    break;
                case END:
                    break;
                case INVALID:
                    this.logger.log(Level.FATAL, "GET: memcached returned invalid packet.");
                    replyBuffer = ByteBuffer.allocateDirect(WorkUnitError.header.length);
                    replyBuffer.put(WorkUnitError.header).flip();
                    memtierCommunication(replyBuffer);
                    return;
                default:
                    this.logger.log(Level.ERROR, "GET: memcached didn't like the request.");
                    replyBuffer = ByteBuffer.allocateDirect(unit.getHeader().length);
                    replyBuffer.put(unit.getHeader()).flip();
                    memtierCommunication(replyBuffer);
                    return;
            }
        }

        reply.add(ByteBuffer.wrap(WorkUnitEnd.header));
        replyBuffer = getAsSingleByteBuffer(reply);
        this.logger.log(Level.DEBUG, "GET: Processed reply for memtier.");
        this.logger.log(Level.DEBUG, "GET: Sending reply for memtier.");
        memtierCommunication(replyBuffer);
    }

    /**
     * Handle an SET query by memtier, distribute it to all memcached clients and send back the reply to memtier.
     *
     * @throws IOException On communication failure.
     */
    private void handleSetRequest() throws IOException
    {
        this.workItem.sendBackTo.register(this.memtierSelector, SelectionKey.OP_WRITE);
        WorkUnitSet setRequest = (WorkUnitSet) this.workItem;

        logger.log(Level.DEBUG, "SET: Generating request for memcached.");
        Map<SelectionKey, ByteBuffer> requestMap = new HashMap<>(MemcachedHandler.memcachedServers.size());

        ByteBuffer requestBuffer = ByteBuffer.allocate(setRequest.header.length + setRequest.body.length);
        requestBuffer.put(setRequest.header).put(setRequest.body).flip();

        // Generate requests for each memcached server
        this.fairnessMap.keySet().forEach(sKey -> requestMap.put(sKey, requestBuffer.duplicate()));

        // Number of expected responses for each memcached server connected to and mark them all active
        HashMap<SelectionKey, Integer> expectedResponseCount = new HashMap<>(MemcachedHandler.memcachedServers.size());
        this.memcachedSelector.keys().forEach(sKey -> {
                    expectedResponseCount.put(sKey, 1);
                    addToInterestSet(sKey, SelectionKey.OP_WRITE);
                }
        );
        logger.log(Level.DEBUG, "SET: Generated request for memcached.");

        logger.log(Level.DEBUG, "SET: Sending request to memcached...");
        List<WorkUnit> replies = memcachedCommunication(WorkUnitType.SET, requestMap, expectedResponseCount);
        logger.log(Level.DEBUG, "SET: Received reply, checking results.");

        ByteBuffer replyBuffer;
        for (WorkUnit unit : replies) {
            switch (unit.type) {
                case STORED:
                    break;
                case INVALID:
                    this.logger.log(Level.FATAL, "SET: memcached returned invalid packet.");
                    replyBuffer = ByteBuffer.allocateDirect(WorkUnitError.header.length);
                    replyBuffer.put(WorkUnitError.header).flip();
                    memtierCommunication(replyBuffer);
                    return;
                default:
                    this.logger.log(Level.ERROR, "SET: memcached didn't like the request.");
                    replyBuffer = ByteBuffer.allocateDirect(unit.getHeader().length);
                    replyBuffer.put(unit.getHeader()).flip();
                    memtierCommunication(replyBuffer);
                    return;
            }
        }

        // We checked all headers to be of type WorkUnitStored, we definitely have one therefore.
        replyBuffer = ByteBuffer.allocateDirect(WorkUnitStored.header.length);
        replyBuffer.put(WorkUnitStored.header).flip();
        this.logger.log(Level.DEBUG, "SET: Processed reply for memtier.");
        this.logger.log(Level.DEBUG, "SET: Sending reply for memtier.");
        memtierCommunication(replyBuffer);
    }

    /**
     * Send non-blocking with Memtier client. Communication is dependent on the number of elements passed.
     *
     * Requires: Requires: interestOps of all required keys set appropriately (for this.memtierSelector).
     * Ensures: interestOps of all keys consumed (i.e. set to state prior to setting them up).
     *
     * @param toSend List of ByteBuffer which are to be sent.
     */
    private void memtierCommunication(ByteBuffer toSend) throws IOException
    {
        boolean responseSent = false;
        SelectionKey sKeyUsed = null;

        this.logger.log(Level.DEBUG, "TIER: Sending to memtier...");
        while (!responseSent) {
            this.memtierSelector.select();

            Set<SelectionKey> selectedKeys = this.memtierSelector.selectedKeys();
            Iterator<SelectionKey> iter = selectedKeys.iterator();

            while (iter.hasNext()) {
                SelectionKey key = iter.next();
                iter.remove();

                if (key.isValid() && key.isWritable()) {
                    if (!(key.attachment() instanceof ByteBuffer)) {
                        key.attach(toSend);
                        // Maybe use as start for communication? But pings should cover this...
                    }
                    responseSent = memtierWriteBytes((ByteBuffer) key.attachment(), key);
                    sKeyUsed = key;
                }
            }
        }
        this.logger.log(Level.DEBUG, "TIER: Finished sending to memtier.");

        switch (workItem.type) {
            case SET:
                workerStats.setElement.addAverageRTT(workItem);
                break;
            case GET:
                if (((WorkUnitGet) workItem).keys.size() > 1) {
                    workerStats.multiGetElement.addAverageRTT(workItem);
                } else {
                    workerStats.getElement.addAverageRTT(workItem);
                }
                break;
            default:
                // Just for completeness sake -- this is dead code
                break;
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
    private List<WorkUnit> memcachedCommunication(WorkUnitType type, Map<SelectionKey, ByteBuffer> toSend,
                                                  Map<SelectionKey, Integer> expectedResponsesCount) throws IOException
    {
        int numberOfResponses = expectedResponsesCount.values().stream().mapToInt(Integer::intValue).sum();

        List<WorkUnit> result = new ArrayList<>(numberOfResponses);
        int actualResponseCount = 0;

        HashMap<SelectionKey, Long> memcachedTimings = new HashMap<>();
        HashMap<SelectionKey, Tuple<Long, Long>> memcachedDeltas = new HashMap<>();
        boolean firstMessageStartedSending = false;

        logger.log(Level.DEBUG, "CACHED: Sending to memcached...");
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

                        if (!(key.attachment() instanceof ByteBuffer)) {
                            ByteBuffer elementsToSend = toSend.get(key);
                            key.attach(elementsToSend);
                            if (!firstMessageStartedSending) {
                                firstMessageStartedSending = true;
                                long timestamp = System.nanoTime();
                                this.workItem.timestamp.setQueryToMemcached(timestamp);
                                memcachedTimings.put(key, timestamp);
                            } else {
                                memcachedTimings.computeIfAbsent(key, val -> System.nanoTime());
                            }
                        }
                        // Send the attached element
                        memcachedWriteBytes((ByteBuffer) key.attachment(), key);
                    }

                    if (key.isReadable()) {
                        // Expecting responses from memcached
                        List<WorkUnit> completeRequest = ((PacketParser) key.attachment()).receiveAndParse(key);

                        if (completeRequest != null) {
                            // Some replies from memcached were received
                            long maybeFinished = System.nanoTime();

                            // Disregard illegal replies from memcached (i.e. too many replies sent)
                            if (expectedResponsesCount.get(key) > 0 &&
                                    expectedResponsesCount.get(key) - completeRequest.size() >= 0) {
                                // Only add if our current expected is within range!
                                expectedResponsesCount.put(key,
                                        expectedResponsesCount.get(key) - completeRequest.size());
                                actualResponseCount += completeRequest.size();
                                result.addAll(completeRequest);

                                if (type == WorkUnitType.GET && expectedResponsesCount.get(key) > 0) {
                                    if (result.stream().anyMatch(unit -> unit.type == WorkUnitType.END)) {
                                        // memcached server doesn't have all keys and indicated End of Transfer.
                                        long memcachedStarted = memcachedTimings.get(key);
                                        memcachedDeltas.put(key, new Tuple<>(maybeFinished,
                                                maybeFinished - memcachedStarted));

                                        if (this.workItem.type == WorkUnitType.GET) {
                                            if (((WorkUnitGet) this.workItem).keys.size() > 1) {
                                                this.workerStats.multiGetElement.cacheMiss(maybeFinished,
                                                        expectedResponsesCount.get(key));
                                            } else {
                                                this.workerStats.getElement.cacheMiss(maybeFinished,
                                                        expectedResponsesCount.get(key));
                                            }
                                        }
                                        actualResponseCount += expectedResponsesCount.get(key);
                                        expectedResponsesCount.put(key, 0);

                                        // Remove the key from the interest set as no further replies expected.
                                        removeFromInterestSet(key, SelectionKey.OP_READ);
                                    }
                                } else if (expectedResponsesCount.get(key) <= 0) {
                                    // memcached server sent expected amount of responses, stop listening to it
                                    long memcachedStarted = memcachedTimings.get(key);
                                    memcachedDeltas.put(key, new Tuple<>(maybeFinished,
                                            maybeFinished - memcachedStarted));

                                    // Remove the key from the interest set as no further replies expected.
                                    removeFromInterestSet(key, SelectionKey.OP_READ);
                                }

                                if (actualResponseCount >= numberOfResponses) {
                                    workItem.timestamp.setReplyFromMemcached(maybeFinished);
                                }

                                this.logger.log(Level.DEBUG,
                                        "CACHED: Reply from memcached-server {} of {} elements.",
                                        ((SocketChannel) key.channel()).getRemoteAddress(), completeRequest.size());
                            }
                        }
                    }
                }
            } catch (IOException e) {
                this.logger.log(Level.ERROR, "CACHED: Communication failure with memcached!");
                throw e;
            }
            this.logger.log(Level.INFO, "CACHED: Finished communication.");
        }

        switch (workItem.type) {
            case SET:
                workerStats.setElement.addAverageServiceTimeMemcached(memcachedDeltas);
                break;
            case GET:
                if (((WorkUnitGet) workItem).keys.size() > 1) {
                    workerStats.multiGetElement.addAverageServiceTimeMemcached(memcachedDeltas);
                } else {
                    workerStats.getElement.addAverageServiceTimeMemcached(memcachedDeltas);
                }
                break;
            default:
                // Just for completeness sake -- this is dead code
                break;
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
    private boolean memtierWriteBytes(ByteBuffer toSend, SelectionKey key) throws IOException
    {
        ((SocketChannel) key.channel()).write(toSend);

        if (!toSend.hasRemaining()) {
            // Request has been sent to memtier client attached to SelectionKey key; adjust this keys interestOps & log
            removeFromInterestSet(key, SelectionKey.OP_WRITE);
            workItem.timestamp.setReplyOnSocket(System.nanoTime());
            this.logger.log(Level.DEBUG, "TIER: Sent reply to {}.",
                    ((SocketChannel) key.channel()).getRemoteAddress());
            return true;
        } else {
            return false;
        }
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
    private void memcachedWriteBytes(ByteBuffer toSend, SelectionKey key) throws IOException
    {
        ((SocketChannel) key.channel()).write(toSend);

        if (!toSend.hasRemaining()) {
            // Request has been sent to memcached server attached to SelectionKey key; update state to old attachment
            // and adjust this keys interestOps
            reattachPacketParser(key);
            removeFromInterestSet(key, SelectionKey.OP_WRITE);
            addToInterestSet(key, SelectionKey.OP_READ);
            this.logger.log(Level.DEBUG, "CACHED: Sent request to {}.",
                    ((SocketChannel) key.channel()).getRemoteAddress());
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
//    private boolean sendBytes(ByteBuffer toSend, SelectionKey key) throws IOException
//    {
//        ((SocketChannel) key.channel()).write(toSend);
//       return toSend.hasRemaining();

//        ByteBuffer buffer = null;
//        Iterator<ByteBuffer> iterator = toSend.iterator();
//
//        while (iterator.hasNext()) {
//            buffer = iterator.next();
//            ((SocketChannel) key.channel()).write(buffer);
//
//            if (!buffer.hasRemaining()) {
//                iterator.remove();
//            }
//        }
//
//        return false;
//    }

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

        this.logger.log(Level.ERROR, "Couldn't find minimum in map of memcached servers. Choosing random server.");
        return last;
    }

    /**
     * Helper method for single invocations of fixed-length input to know which memcached gets the query next based on
     * fairness.
     */
    private Map<SelectionKey, Integer> loadBalancer(int numberOfRequests)
    {
        int availableRequests = numberOfRequests;
        HashMap<SelectionKey, Integer> result = new HashMap<>(MemcachedHandler.memcachedServers.size());
        this.fairnessMap.keySet().forEach(sKey -> result.put(sKey, 0));

        if (MemcachedHandler.memcachedServers.size() == 1) {
            SelectionKey temp = getNextServer();
            result.merge(temp, 1, Integer::sum);
        } else if (MemcachedHandler.memcachedServers.size() == 2) {
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
     * Initializes each MemcachedHandler such that it is ready for communication with the memcached servers.
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

        for (InetSocketAddress memcachedServer : MemcachedHandler.memcachedServers) {
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

    private ByteBuffer getAsSingleByteBuffer(List<ByteBuffer> partialBuffer)
    {
        int bufferSize = partialBuffer.stream().mapToInt(Buffer::limit).sum();
        ByteBuffer result = ByteBuffer.allocateDirect(bufferSize);
        partialBuffer.forEach(result::put);
        result.flip();
        return result;
    }

}
