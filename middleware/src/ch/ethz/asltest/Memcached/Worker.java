package ch.ethz.asltest.Memcached;

import ch.ethz.asltest.Utilities.WorkQueue;
import ch.ethz.asltest.Utilities.WorkUnit.WorkUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
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
    private final List<SelectionKey> selectionKeys = new ArrayList<>(memcachedServers.size());

    // Initialized variably
    private WorkUnit workItem;
    private Selector selector;

    private int invalidHeaders;

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

        // TODO: Here generate statistics and return them in an object/struct/whatevs
        return null;
    }

    private void handleGetRequest()
    {
        if (Worker.isSharded) {

        } else {

        }
    }

    private void handleSetRequest()
    {

    }

    /**
     * Initializes each Worker, i.e. connects to each Memcached-server
     */
    private void initWorkTask() throws IOException
    {
        this.selector = Selector.open();
        SocketChannel sc_temp;
        SelectionKey sk_temp;
        int interestSet = SelectionKey.OP_WRITE | SelectionKey.OP_READ;
        for (int i = 0; i < Worker.memcachedServers.size(); ++i) {
            sc_temp = SocketChannel.open(Worker.memcachedServers.get(i));
            sc_temp.configureBlocking(false);
            while (!sc_temp.finishConnect()) {
                // Busy-spin until connected...
            }
            sk_temp = sc_temp.register(this.selector, interestSet);
            this.selectionKeys.add(sk_temp);
            this.memcachedSockets.add(sc_temp);
            this.logger.info("Thread %d connected to server %s\n", this.id,
                    Worker.memcachedServers.get(i).toString());
        }
    }
}
