package ch.ethz.asltest.Memcached;

import ch.ethz.asltest.Utilities.Misc.Tuple;
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
    private final List<SocketChannel> memcachedSockets = new ArrayList<>(memcachedServers.size());
    private final Map<String, PacketParser> packetParsers = new HashMap<>(memcachedServers.size());
    //private final List<SelectionKey> selectionKeys = new ArrayList<>(memcachedServers.size());

    // Initialized variably
    private WorkUnit workItem;
    private Selector memcachedSelector;
    private Selector memtierSelector;

    private int invalidHeaders = 0;

    private int[] sendCounters = new int[Worker.memcachedServers.size()];

    // Local fields for statistics
    private int getCounter = 0;
    private int setCounter = 0;

    /**
     * Sets the workQueue statically which instances are referring to.
     *
     * @param queue workQueue to be used by instances.
     */
    public static void setWorkQueue(WorkQueue queue)
    {
        Worker.workQueue = queue;
    }

    /**
     * Sets the list of Memcached-servers each instance is connecting to.
     *
     * @param memcachedServers List of Memcached-servers used by instances.
     */
    public static void setMemcachedServers(List<InetSocketAddress> memcachedServers)
    {
        Worker.memcachedServers = memcachedServers;
    }

    /**
     * Sets the GET requests as sharded for each instance of this class.
     *
     * @param isSharded Set the GET reuqests as sharded.
     */
    public static void setIsSharded(boolean isSharded)
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
    public Object call() throws InterruptedException, IOException
    {
        try {
            this.initWorkTask();
        } catch (Exception e) {
            this.logger.fatal("Couldn't initialize worker. Will stop execution on it...");
            this.logger.error(e.getMessage());
            return null;
        }

        while (!stopFlag.get()) {
            // TODO: Local logic which works through the queue
            this.workItem = Worker.workQueue.get();
            this.logger.log(Level.DEBUG, "Got an element from the queue.");
            switch (this.workItem.type) {
                case SET:
                    // TODO: Send to every server, wait for all responses before replying
                    this.handleSetRequest();
                    break;
                case GET:
                    this.handleGetRequest();
                    break;
                case INVALID:
                    ++invalidHeaders;
                    break;
                default:
                    // Dead code per design, still keep this active...
                    break;
            }
        }

        for (SocketChannel channel : this.memcachedSockets) {
            channel.close();
        }

        //TODO: Here generate statistics and return them in an object/struct/whatevs
        return null;
    }

    private void handleGetRequest()
    {
        if (Worker.isSharded) {
            /*TODO
             * High level algorithmic approach:
             * Separate the current amount of keys into this.memcachedSockets.length buckets, trying to balance their sizes.
             * Keep the same amount of counters available, initially at 0.
             * Now using "counter + new" try to balance the counters such that their values stay close together.
             * If any changes are detected, move keys around.
             * Finally send with all three channels.
             */
        } else {
            /**
             *
             */
        }
    }

    /**
     * Method to handle an SET query by memtier, distribute it to all memcache clients and send back the reply to
     * memtier.
     * @throws IOException
     */
    private void handleSetRequest() throws IOException
    {
        this.workItem.sendBackTo.register(this.memtierSelector, SelectionKey.OP_WRITE);

        List<ByteBuffer> packet = new ArrayList<>();
        packet.add(ByteBuffer.wrap(((WorkUnitSet) this.workItem).header));
        packet.add(ByteBuffer.wrap(((WorkUnitSet) this.workItem).body));

        List<WorkUnit> results = memcachedCommunication(packet, Worker.memcachedServers.size());
        packet.clear();

        for (WorkUnit unit : results) {
            if (!(unit instanceof WorkUnitStored)) {
                if (unit instanceof WorkUnitInvalid) {
                    this.logger.log(Level.FATAL, "Memcached returned invalid packet!");
                    packet.add(ByteBuffer.wrap(WorkUnitError.header));
                } else {
                    packet.add(ByteBuffer.wrap(unit.getHeader()));
                    // Note: We NEVER expect the return value to hold a body... This would be plain out incorrect!
                }
                memtierCommunication(packet);
                return;
            }
        }

        // We checked all headers to be of type WorkUnitStored, we definitely have one therefore.
        packet.add(ByteBuffer.wrap(WorkUnitStored.header));
        memtierCommunication(packet);
    }

    /**
     * Method to send using non-blocking IO back to memtier. This method will infer the runtime automatically from the
     * passed parameters (the length) and stop once it's done.
     * @param toSend List of ByteBuffer which are to be sent.
     */
    private void memtierCommunication(List<ByteBuffer> toSend) throws IOException
    {
        boolean responseSent = false;
        while (!responseSent) {
            this.memtierSelector.select();

            Set<SelectionKey> selectedKeys = this.memtierSelector.selectedKeys();
            Iterator<SelectionKey> iter = selectedKeys.iterator();

            while (iter.hasNext()) {
                SelectionKey key = iter.next();
                iter.remove();

                if (key.isValid() && key.isWritable()) {
                    if (key.attachment() instanceof ArrayList) {
                        responseSent = memtierWriteBytes((ArrayList<ByteBuffer>) key.attachment(), key);
                    } else {
                        key.attach(toSend);
                        responseSent = memtierWriteBytes((ArrayList<ByteBuffer>) key.attachment(), key);
                    }
                }
            }
        }
    }

    /**
     * Helper method to send and receive until as many WorkUnits as requested have been returned.
     * @param toSend List of ByteBuffers to send.
     * @param expectedResponsesCount Number of expected responses.
     * @return
     * @throws IOException
     */
    private ArrayList<WorkUnit> memcachedCommunication(List<ByteBuffer> toSend, int expectedResponsesCount) throws IOException
    {
        ArrayList<WorkUnit> result = new ArrayList<>(expectedResponsesCount);
        int actualResponseCount = 0;

        while (actualResponseCount < expectedResponsesCount) {
            try {
                this.memcachedSelector.select();

                Set<SelectionKey> selectedKeys = this.memcachedSelector.selectedKeys();
                Iterator<SelectionKey> iter = selectedKeys.iterator();

                while (iter.hasNext()) {
                    SelectionKey key = iter.next();
                    iter.remove();

                    if (key.isValid() && key.isWritable()) {
                        // TODO: Bugfix here, kinda doesn't write to the second socket... why?
                        /*
                         * The key is writable but due to the nature of requiring a consumable element to send we must
                         * exchange the object.
                         */

                        if (key.attachment() instanceof ArrayList) {
                            memcachedWriteBytes((ArrayList<ByteBuffer>) key.attachment(), key);
                        } else {
                            ArrayList<ByteBuffer> list = new ArrayList<>(toSend.size());
                            list.addAll(toSend);
                            safeAttachMessage(key, list);
                            memcachedWriteBytes((ArrayList<ByteBuffer>) key.attachment(), key);
                        }
                    }

                    if (key.isValid() && key.isReadable()) {
                        /*
                         * Expect responses here.
                         */
                        List<WorkUnit> completeRequest = ((PacketParser) key.attachment()).receiveAndParse(key);
                        if (completeRequest != null) {
                            // TODO: Include logic for errors thrown by memcached... *sigh*
                            actualResponseCount += completeRequest.size();
                            result.addAll(completeRequest);
                            this.logger.log(Level.DEBUG, "Received reply from server {}", ((SocketChannel) key.channel()).getRemoteAddress());
                            //key.interestOps(SelectionKey.OP_WRITE);
                        }
                    }
                }

            } catch (IOException e) {
                this.logger.log(Level.ERROR, "Couldn't send 'set' to memcached :(");
                throw e;
            }
        }

        return result;
    }

    /**
     * Stateful method to send data to a recipient and upon success reattach to the key the respective PacketParser.
     * @param toSend List of ByteBuffer's to send.
     * @param key Key over which to send (uses the underlying socketChannel for it).
     * @throws IOException IOError during the attempt to write to the channel.
     */
    private boolean memtierWriteBytes(List<ByteBuffer> toSend, SelectionKey key) throws IOException
    {
        Tuple<ByteBuffer, Iterator<ByteBuffer>> result = writeBytes(toSend, key);
        ByteBuffer buffer = result.first;
        Iterator<ByteBuffer> iterator = result.second;

        // Everything has been sent at this point, reattach the key's packetParser.
        if (toSend.isEmpty() && buffer != null && !buffer.hasRemaining()) {
            key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
            return true;
        }
        return false;
    }

    /**
     * Stateful method to send data to a recipient and upon success reattach to the key the respective PacketParser.
     * @param toSend List of ByteBuffer's to send.
     * @param key Key over which to send (uses the underlying socketChannel for it).
     * @throws IOException IOError during the attempt to write to the channel.
     */
    private void memcachedWriteBytes(List<ByteBuffer> toSend, SelectionKey key) throws IOException
    {
        Tuple<ByteBuffer, Iterator<ByteBuffer>> result = writeBytes(toSend, key);
        ByteBuffer buffer = result.first;
        Iterator<ByteBuffer> iterator = result.second;

        if (toSend.isEmpty() && buffer != null && !buffer.hasRemaining()) {
            // Everything has been sent at this point, reattach the key's packetParser.
            reattachPacketParser(key);
            key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
            this.logger.log(Level.DEBUG, "Sent request to {}.", ((SocketChannel) key.channel()).getRemoteAddress());
        }
    }

    /**
     * Internal method which will send the list of ByteBuffers to the key's channel and returns the state as a tuple.
     * @param toSend List of ByteBuffers to send.
     * @param key Key on which to send.
     * @return Tuple of the state of this method.
     */
    private Tuple<ByteBuffer, Iterator<ByteBuffer>> writeBytes(List<ByteBuffer> toSend, SelectionKey key) throws IOException
    {
        ByteBuffer buffer = null;
        Iterator<ByteBuffer> iterator = toSend.iterator();

        while (iterator.hasNext()) {
            buffer = iterator.next();
            ((SocketChannel) key.channel()).write(buffer);

            if (!buffer.hasRemaining()) {
                iterator.remove();
            } else {
                // TODO: Do we need to update our interest-sets
                key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
                key.selector().wakeup();
                break;
            }
        }

        return new Tuple<>(buffer, iterator);
    }

    /**
     * Helper method to reattach the packetParser back to the key to allow reading data correctly.
     * @param key SelectionKey which gets its PacketParser back (which still holds the previous state).
     */
    private void reattachPacketParser(SelectionKey key)
    {
        if (key.attachment() instanceof ArrayList) {
            try {
                key.attach(this.packetParsers.get(((SocketChannel) key.channel()).getRemoteAddress().toString()));
            } catch (IOException e) {
                this.logger.log(Level.ERROR, "Problem reattaching PacketParser.");
                e.printStackTrace();
            }
        }
    }

    /**
     * Attached a generic to the SelectionKey iff the current attachment is a PacketParser.
     * @param key SelectionKey on which to change the attachment on.
     * @param toAttach Object to attach to the key.
     */
    private void safeAttachMessage(SelectionKey key, Object toAttach)
    {
        if (key.attachment() instanceof PacketParser) {
            key.attach(toAttach);
        }
    }

    /**
     * Initializes each Worker such that it is ready for communication with the memcached servers.
     */
    private void initWorkTask() throws IOException
    {
        SocketChannel socketChannel;
        PacketParser packetParser;
        String remoteAddress;

        // The instance's selector used for non-blocking IO with memcached
        this.memcachedSelector = Selector.open();
        int interestSet = SelectionKey.OP_WRITE | SelectionKey.OP_READ;

        // The instance's selector used for non-blocking IO with memtier
        this.memtierSelector = Selector.open();

        for (int i = 0; i < Worker.memcachedServers.size(); ++i) {
            // Create a socketChannel that is already connected
            socketChannel = SocketChannel.open(Worker.memcachedServers.get(i));
            socketChannel.configureBlocking(false);

            while (socketChannel.isConnectionPending()) {
                socketChannel.finishConnect();
            }

            // Generate a packetParser for the socketChannel
            remoteAddress = socketChannel.getRemoteAddress().toString();
            packetParser = new PacketParser(remoteAddress);
            this.packetParsers.put(remoteAddress, packetParser);

            // register the socketChannel to this instances' memcachedSelector
            socketChannel.register(this.memcachedSelector, interestSet, packetParser);
            this.memcachedSockets.add(socketChannel);
            this.logger.info("Thread {} connected to server {}", this.id,
                    Worker.memcachedServers.get(i).toString());
        }
    }
}
