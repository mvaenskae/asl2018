package ch.ethz.asltest.Middleware;

import ch.ethz.asltest.Utilities.WorkQueue;
import ch.ethz.asltest.Utilities.WorkUnit;


import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

public class WorkTask implements Callable<Object> {

    private final static AtomicBoolean stopFlag = new AtomicBoolean();

    private static WorkQueue workQueue;
    private static List<InetAddress> memcachedServers;
    private static boolean isSharded;

    static final void setWorkQueue(WorkQueue queue)
    {
        WorkTask.workQueue = queue;
    }

    public static void setMemcachedServers(List<InetAddress> memcachedServers) {
        WorkTask.memcachedServers = memcachedServers;
    }

    public static void setIsSharded(boolean isSharded)
    {
        WorkTask.isSharded = isSharded;
    }

    @Override
    public Object call() throws InterruptedException {
        try {
            this.initWorkTask();
        } catch (Exception e) {
            return null;
        }
        while(!stopFlag.get()) {
            // TODO: Local logic which works through the queue
            WorkTask.workQueue.get();
        }

        // TODO: Here generate statistics and return them in an object/struct/whatevs
        return null;
    }

    private void initWorkTask()
    {
        // Connect to Memcached servers here
    }
}
