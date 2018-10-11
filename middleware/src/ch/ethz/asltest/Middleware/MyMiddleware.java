package ch.ethz.asltest.Middleware;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import ch.ethz.asltest.Utilities.*;


public class MyMiddleware implements Runnable {
    // Consts
    private final static int CLIENT_QUEUE_SIZE = 100000;
    private final static int MAXIMUM_THREADS = 128;

    // Local objects
    private static Logger logger = LogManager.getLogger(MyMiddleware.class.getName());

    // Local fields
    public AtomicBoolean atomicStopFlag = new AtomicBoolean(false);
    final private InetAddress ip;
    final private int port;
    final private List<InetAddress> memcachedServers;
    final private int numThreadPoolThreads;
    final private boolean isShardedRead;

    private WorkQueue clientQueue;
    private ExecutorService workerThreads;

    private Selector selector;
    private ServerSocketChannel serverChannel;
    private List<Future<Object>> workTaskResult;


    /**
     * Constructor for middleware
     * @param ip IP of middleware
     * @param port Port on which middleware listens
     * @param memcachedServers IP:Port of servers running memcached
     * @param numThreadPoolThreads Threads within a single Threadpool
     * @param isShardedRead Sharded reads supported?
     */
    public MyMiddleware(String ip, int port, List<String> memcachedServers, int numThreadPoolThreads, boolean isShardedRead) throws UnknownHostException {
        try {
            this.ip = InetAddress.getByName(ip);
        } catch (UnknownHostException e) {
            logger.error("Middleware IP Address malformatted. Stopping now.");
            e.printStackTrace();
            throw e;
        }

        this.port = port;
        this.memcachedServers = new ArrayList<>(memcachedServers.size());
        for (String server: memcachedServers) {
            try {
                this.memcachedServers.add(InetAddress.getByName(server));
            } catch (UnknownHostException e) {
                logger.error("Memcached server IP Address malformatted. Stopping now.");
                e.printStackTrace();
                throw e;
            }
        }

        this.numThreadPoolThreads = numThreadPoolThreads < MAXIMUM_THREADS ? numThreadPoolThreads : MAXIMUM_THREADS;
        this.isShardedRead = isShardedRead;
    }

    /**
     * Starts the main logic of the middleware, accepts packets and puts them into a queue
     */
    @Override
    public void run() {
        try {
            initNioMiddleware();
        } catch (IOException | InterruptedException e) {
            logger.error("Initialization of middleware failed. Stopping now.");
            e.printStackTrace();
            System.exit(-3);
        }

        // TODO: Make this part of each packetParser...
        ByteBuffer buffer = ByteBuffer.allocate(8192);

        // Main logic for server to accept new channels and process them with a packet parser before pushing them onto
        // the queue
        while(!this.atomicStopFlag.get()) {
            try {
                this.selector.select();
                Set<SelectionKey> selectedKeys = this.selector.selectedKeys();
                Iterator<SelectionKey> iter = selectedKeys.iterator();

                while (iter.hasNext()) {
                    SelectionKey key = iter.next();

                    if (key.isAcceptable()) {
                        register(selector, this.serverChannel);
                    }

                    if (key.isReadable()) {
                        WorkUnit parsedPacket = parsePacket(buffer, key);
                        if (parsedPacket != null) {
                            this.clientQueue.put(parsedPacket);
                        }
                    }
                    iter.remove();
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }

        // Close the socket channel
        try {
            this.serverChannel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Wait until the queue is empty
        while(!this.clientQueue.isEmpty()) {
            // TODO: Busyspin here
        }

        // Shut down the threadpool
        this.workerThreads.shutdown();
        try {
            if (!this.workerThreads.awaitTermination(10, TimeUnit.SECONDS)) {
                this.workerThreads.shutdownNow();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            this.workerThreads.shutdownNow();
        }

        // Get results from each thread and generate final results
        for (Future<Object> workResult:
             this.workTaskResult) {
            // TODO: Merge results from threads here
        }

        // TODO: Return merged results via stdout (and logging)
    }

    /**
     * Registers the client onto the channel and makes the channel readable. Further a stateful PacketParser related to
     * this channel is attached.
     * @param selector Selector on which to register the channel on.
     * @param serverSocket Socket from which to expect clients to start talking.
     * @throws IOException
     */
    private static void register(Selector selector, ServerSocketChannel serverSocket) throws IOException {
        SocketChannel client = serverSocket.accept();
        client.configureBlocking(false);
        // TODO: Allocate a buffer here for each packet parser spawned...
        client.register(selector, SelectionKey.OP_READ, new PacketParser());
    }

    /**
     * Method to call the packet parser after accepting locally as many bytes as the channel has to offer.
     * @param buffer
     * @param key SelectionKey on which to operate on
     * @return
     * @throws IOException
     */
    private static WorkUnit parsePacket(ByteBuffer buffer, SelectionKey key) throws IOException {
        // TODO: FIX ME!
        SocketChannel client = (SocketChannel) key.channel();

        boolean headerFinished = false;
        boolean bodyFinished = false;
        int bodySize = 0;
        byte[] body = null;

        int bufferSize = 0;

        while (!headerFinished) {
            bufferSize = client.read(buffer);
            for (int i = 0; i < bufferSize; ++i) {
                // TODO: Rudimentary parsing here...
            }
        }

        body = new byte[bodySize+2];
        int bodyOffset = 0;
        bufferSize = 0;

        while (!bodyFinished) {
            bufferSize = client.read(buffer);
            for (int i = 0; i < bufferSize; ++i) {
                body[i+bodyOffset] = buffer.get(i);
            }
            bodyOffset = bodyOffset + bufferSize + 1;
            if (bodyOffset == (bodySize + 1)) {
                bodyFinished = true;
            }
        }

        return null;
    }

    /**
     * Initialize the middleware's networking layer based on Java NIO (sockets bound, no services running).
     */
    private void initNioMiddleware() throws IOException, InterruptedException {
        this.selector = Selector.open();
        this.serverChannel = ServerSocketChannel.open();
        try {
            this.serverChannel.bind(new InetSocketAddress(this.ip, this.port));
        } catch (IOException e) {
            logger.error("Socket could not be opened, middleware not initialized!");
            throw e;
        }
        this.serverChannel.configureBlocking(false);
        this.serverChannel.register(selector, SelectionKey.OP_ACCEPT, null);
        System.out.printf("Middleware accessible under %s\n", this.serverChannel.socket().getLocalSocketAddress());

        // Initialize datastructures once the middleware is up
        this.clientQueue = new WorkQueue(CLIENT_QUEUE_SIZE);

        WorkTask.setWorkQueue(this.clientQueue);
        WorkTask.setMemcachedServers(this.memcachedServers);
        WorkTask.setIsSharded(this.isShardedRead);

        Collection<Callable<Object>> workTasks = new ArrayList<>(this.numThreadPoolThreads);
        for (int i = 0; i < this.numThreadPoolThreads; i++) {
            workTasks.add(new WorkTask());
        }

        this.workerThreads = Executors.newFixedThreadPool(this.numThreadPoolThreads);
        try {
            this.workTaskResult = workerThreads.invokeAll(workTasks);
        } catch (InterruptedException e) {
            logger.error("Couldn't start up threads. Stopping middleware.");
            this.serverChannel.close();
            e.printStackTrace();
            throw e;
        }
    }
}
