package ch.ethz.asltest.Middleware;

import ch.ethz.asltest.Utilities.WorkQueue;
import ch.ethz.asltest.Utilities.WorkUnit.WorkUnit;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

public class Worker implements Callable<Object> {

    // Poison-Pill
    private final static AtomicBoolean stopFlag = new AtomicBoolean();

    // Static fields
    private static WorkQueue workQueue;
    private static List<InetSocketAddress> memcachedServers;
    private static boolean isSharded;

    // Instance-based fields
    // Initialized by static properties
    private final List<SocketChannel> memcachedSockets = new ArrayList<>(memcachedServers.size());
    private final List<SelectionKey> selectionKeys = new ArrayList<>(memcachedServers.size());

    // Initialized variably
    private WorkUnit workItem;
    private Selector selector;

    private int invalidHeaders;

    /**
     * Sets the workQueue statically which instances are referring to.
     * @param queue workQueue to be used by instances.
     */
    static void setWorkQueue(WorkQueue queue)
    {
        Worker.workQueue = queue;
    }

    /**
     * Sets the list of Memcached-servers each instance is connecting to.
     * @param memcachedServers List of Memcached-servers used by instances.
     */
    public static void setMemcachedServers(List<InetSocketAddress> memcachedServers) {
        Worker.memcachedServers = memcachedServers;
    }

    /**
     * Sets the GET requests as sharded for each instance of this class.
     * @param isSharded Set the GET reuqests as sharded.
     */
    public static void setIsSharded(boolean isSharded)
    {
        Worker.isSharded = isSharded;
    }

    /**
     * Empty instantiation procedure, currently does nothing really...
     */
    public Worker() {

    }

    @Override
    public Object call() throws InterruptedException, IOException {
        try {
            this.initWorkTask();
        } catch (Exception e) {
            // TODO: Log this problem...
            e.printStackTrace();
            return null;
        }

        while(!stopFlag.get()) {
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
    private void initWorkTask() throws IOException {
        this.selector = Selector.open();
        int interestSet = SelectionKey.OP_CONNECT | SelectionKey.OP_WRITE | SelectionKey.OP_READ;
        for (int i = 0; i < Worker.memcachedServers.size(); ++i) {
            SocketChannel temp = SocketChannel.open(Worker.memcachedServers.get(i));
            temp.configureBlocking(false);
            while (!temp.finishConnect()) {
                // Busy-spin until connected...
            }
            SelectionKey temp2 = temp.register(this.selector, interestSet);
            this.selectionKeys.add(temp2);
            this.memcachedSockets.add(temp);
            System.out.printf("Thread %d connected to server %s\n", Thread.currentThread().getId(),
                                                                    Worker.memcachedServers.get(i).toString());
        }
    }
}
