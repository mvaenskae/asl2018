package ch.ethz.asltest.Middleware;

import ch.ethz.asltest.Memcached.Worker;
import ch.ethz.asltest.Utilities.Misc.IPPair;
import ch.ethz.asltest.Utilities.Statistics.Containers.MiddlewareStatistics;
import ch.ethz.asltest.Utilities.Packets.PacketParser;
import ch.ethz.asltest.Utilities.Statistics.Containers.QueueStatistics;
import ch.ethz.asltest.Utilities.Statistics.Containers.WorkerStatistics;
import ch.ethz.asltest.Utilities.Statistics.Handlers.QueueHandler;
import ch.ethz.asltest.Utilities.WorkQueue;
import ch.ethz.asltest.Utilities.Packets.WorkUnit.WorkUnit;
import ch.ethz.asltest.Utilities.Statistics.Handlers.WorkerHandler;
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
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.net.StandardSocketOptions.TCP_NODELAY;


public final class MyMiddleware implements Runnable {

    // Constants defining default program behavior
    private final static int CLIENT_QUEUE_SIZE = 256;
    private final static int MAXIMUM_THREADS = 128;
    private final String logDir = "mw-stats";

    // Instance-bound default objects
    private static final Logger logger = LogManager.getLogger(MyMiddleware.class);

    // Instance-bound fields which define the logic of the middleware
    public final AtomicBoolean atomicStopFlag = new AtomicBoolean(false);
    final private InetAddress ip;
    final private int port;
    final private List<InetSocketAddress> memcachedServers;
    final private int numThreadPoolThreads;
    final private boolean isShardedRead;

    // Instance-bound references to objects created by the middleware
    private WorkQueue clientQueue;
    private ExecutorService workerThreads;
    private ArrayList<Worker> workerList;
    private Selector selector;
    private ServerSocketChannel serverChannel;

    // Instance-bound references to statistics instrumentation
    private final ScheduledThreadPoolExecutor statisticsThreads = new ScheduledThreadPoolExecutor(2);
    private QueueHandler queueHandler;
    private WorkerHandler workerHandler;
    private ArrayList<Future<WorkerStatistics>> workerResults;
    WorkerStatistics mergedWorkerStatistics = new WorkerStatistics();
    QueueStatistics queueStatistics;


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
        boolean firstPacket = true;
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

                    if (!key.isValid()) {
                        continue;
                    }

                    if (key.isAcceptable()) {
                        register(selector, this.serverChannel);
                    }

                    if (key.isReadable()) {
                        List<WorkUnit> completeRequest = ((PacketParser) key.attachment()).receiveAndParse(key);
                        if (completeRequest != null && completeRequest.size() > 0) {
                            if (firstPacket) {
                                firstPacket = false;
                                MiddlewareStatistics.enableStatistics();
                                this.queueStatistics = this.clientQueue.queueStatistics;
                            }
                            for (WorkUnit wu : completeRequest) {
                                this.clientQueue.put(wu);
                                wu.timestamp.setPushOnQueue(System.nanoTime());
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

        // Workers don't accept new elements from the queue -- all workers will be interrupted by design
        Worker.setStopFlag(true);

        // Shut down the threadpool
        this.workerThreads.shutdown();
        try {
            if (!this.workerThreads.awaitTermination(10, TimeUnit.SECONDS)) {
                this.workerThreads.shutdownNow();
            }
        } catch (InterruptedException e) {
            this.workerThreads.shutdownNow();
        }

        this.queueStatistics.stopQueue();
        MiddlewareStatistics.disableStatistics();
        // TODO: Also on WorkerStatistics

        this.statisticsThreads.shutdown();
        try {
            if (!this.statisticsThreads.awaitTermination(10, TimeUnit.SECONDS)) {
                this.statisticsThreads.shutdownNow();
            }
        } catch (InterruptedException e) {
            this.statisticsThreads.shutdownNow();
        }

        // Get results from each thread and generate final results
/*        for (Future<WorkerStatistics> workResult : this.workerResults) {
            try {
                this.mergedWorkerStatistics.add(workResult.get(10, TimeUnit.SECONDS));
            } catch (InterruptedException | TimeoutException | ExecutionException e) {
                e.printStackTrace();
            }
        }*/

        try {
            printMiddlewareStatistics(Paths.get(System.getProperty("user.home"), this.logDir));
        } catch (IOException e) {
            e.printStackTrace();
        }
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
        this.workerResults = new ArrayList<>(this.numThreadPoolThreads);
        this.workerList = new ArrayList<>(this.numThreadPoolThreads);
        for (int i = 0; i < this.numThreadPoolThreads; i++) {
            Worker temp = new Worker(i);
            workerList.add(temp);
            this.workerResults.add(this.workerThreads.submit(temp));
        }
    }

    /**
     * Enable the instrumentation to generate performance statistics of the middleware.
     * @param enableHandlers Should the performance statistics handlers be enabled immediately to run?
     */
    private void initMiddlewareStatistics(boolean enableHandlers)
    {
        MiddlewareStatistics.enableStatistics();
        this.queueStatistics = this.clientQueue.queueStatistics;
        /*this.queueHandler = new QueueHandler(this.clientQueue);
        this.workerHandler = new WorkerHandler(this.workerList);

        if (enableHandlers) {
            this.queueHandler.enable();
            this.workerHandler.enable();
        }

        this.statisticsThreads.scheduleAtFixedRate(queueHandler, 0, MiddlewareStatistics.TIME_INTERVAL, MiddlewareStatistics.TIME_UNIT);
        this.statisticsThreads.scheduleAtFixedRate(workerHandler, 0, MiddlewareStatistics.TIME_INTERVAL, MiddlewareStatistics.TIME_UNIT);
        */
    }

    private void printMiddlewareStatistics(Path directoryPath) throws IOException
    {
        if (!Files.exists(directoryPath, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectory(directoryPath);
        }

        StringBuilder temp = new StringBuilder();
        this.queueStatistics.getWindowAverages().forEach(item -> temp.append(item + "\n"));
        byte[] tempBytes = temp.toString().getBytes();
        Path queueStatisticsPath = Paths.get(directoryPath.toString(), "queue_statistics.txt");
        Path queueStatisticsFile = Files.createFile(queueStatisticsPath);
        Files.write(queueStatisticsFile, tempBytes);
    }
}
