package ch.ethz.asltest.Middleware;

import ch.ethz.asltest.Memcached.Worker;
import ch.ethz.asltest.Utilities.Misc.IPPair;
import ch.ethz.asltest.Utilities.PacketParser;
import ch.ethz.asltest.Utilities.WorkQueue;
import ch.ethz.asltest.Utilities.WorkUnit.WorkUnit;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.net.StandardSocketOptions.TCP_NODELAY;


public final class MyMiddleware implements Runnable {

    // Consts
    private final static int CLIENT_QUEUE_SIZE = 1000;
    private final static int MAXIMUM_THREADS = 128;

    // Local objects
    private static final Logger logger = LogManager.getLogger(MyMiddleware.class);

    // Local fields
    public final AtomicBoolean atomicStopFlag = new AtomicBoolean(false);
    final private InetAddress ip;
    final private int port;
    final private List<InetSocketAddress> memcachedServers;
    final private int numThreadPoolThreads;
    final private boolean isShardedRead;

    private WorkQueue clientQueue;
    private ExecutorService workerThreads;

    private Selector selector;
    private ServerSocketChannel serverChannel;
    private ArrayList<Future<Object>> workerResults;


    /**
     * Constructor for middleware
     *
     * @param ip                   IP of middleware
     * @param port                 Port on which middleware listens
     * @param memcachedServers     IP:Port of servers running memcached
     * @param numThreadPoolThreads Threads within a single Threadpool
     * @param isShardedRead        Sharded reads supported?
     */
    public MyMiddleware(String ip, int port, List<String> memcachedServers, int numThreadPoolThreads, boolean isShardedRead) throws UnknownHostException
    {
        try {
            this.ip = InetAddress.getByName(ip);
        } catch (UnknownHostException e) {
            logger.error("Middleware IP Address malformatted. Stopping now.");
            e.printStackTrace();
            throw e;
        }

        this.port = port;
        this.memcachedServers = new ArrayList<>(memcachedServers.size());
        for (String server : memcachedServers) {
            IPPair temp = IPPair.getIPPair(server);
            this.memcachedServers.add(new InetSocketAddress(temp.ip, temp.port));
        }

        this.numThreadPoolThreads = numThreadPoolThreads < MAXIMUM_THREADS ? numThreadPoolThreads : MAXIMUM_THREADS;
        this.isShardedRead = isShardedRead;
    }

    /**
     * Starts the main logic of the middleware, accepts packets and puts them into a queue
     */
    @Override
    public void run()
    {
        try {
            initNioMiddleware();
        } catch (IOException e) {
            logger.error("Initialization of middleware failed. Stopping now.");
            e.printStackTrace();
            System.exit(-3);
        }

        // Main logic for server to accept new channels and process them with a packet parser before pushing them onto
        // the queue
        while (!this.atomicStopFlag.get()) {
            try {
                this.selector.select();
                Set<SelectionKey> selectedKeys = this.selector.selectedKeys();
                Iterator<SelectionKey> iter = selectedKeys.iterator();

                while (iter.hasNext()) {
                    SelectionKey key = iter.next();
                    iter.remove();

                    if (key.isValid() && key.isAcceptable()) {
                        register(selector, this.serverChannel);
                    }

                    if (key.isValid() && key.isReadable()) {
                        List<WorkUnit> completeRequest = ((PacketParser) key.attachment()).receiveAndParse(key);
                        if (completeRequest != null && completeRequest.size() > 0) {
                            for (WorkUnit wu : completeRequest) {
                                this.clientQueue.put(wu);
                            }
                        }
                    }
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
        while (!this.clientQueue.isEmpty()) {
            // Busyspin here
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
        for (Future<Object> workResult :
                this.workerResults) {
            // TODO: Merge results from threads here
        }

        // TODO: Return merged results via stdout (and logging)
    }

    /**
     * Registers the client onto the channel and makes the channel readable. Further a stateful PacketParser related to
     * this channel is attached.
     *
     * @param selector     Selector on which to register the channel on.
     * @param serverSocket Socket from which to expect clients to start talking.
     * @throws IOException Socket wasn't able to be connected to or client couldn't be registered to selector.
     */
    private static void register(Selector selector, ServerSocketChannel serverSocket) throws IOException
    {
        SocketChannel client = serverSocket.accept();
        MyMiddleware.logger.log(Level.DEBUG, "Connection established from {}", client.getRemoteAddress().toString());
        client.configureBlocking(false);
        client.setOption(TCP_NODELAY, true);
        client.register(selector, SelectionKey.OP_READ, new PacketParser(client.getRemoteAddress().toString()));
    }


    /**
     * Initialize the middleware's networking layer based on Java NIO (sockets bound, no services running).
     */
    private void initNioMiddleware() throws IOException
    {
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
        logger.log(Level.INFO, "Middleware accessible under {}", this.serverChannel.socket().getLocalSocketAddress());

        // Initialize datastructures once the middleware is up
        this.clientQueue = new WorkQueue(CLIENT_QUEUE_SIZE);

        Worker.setWorkQueue(this.clientQueue);
        Worker.setMemcachedServers(this.memcachedServers);
        Worker.setIsSharded(this.isShardedRead);

        this.workerThreads = Executors.newFixedThreadPool(this.numThreadPoolThreads);
        //Collection<Callable<Object>> workers = new ArrayList<>(this.numThreadPoolThreads);
        this.workerResults = new ArrayList<>(this.numThreadPoolThreads);
        for (int i = 0; i < this.numThreadPoolThreads; i++) {
            Worker temp = new Worker(i);
            //workers.add(temp);
            this.workerResults.add(this.workerThreads.submit(temp));
        }
    }
}
