package ch.ethz.asltest.Utilities.Statistics.Handlers;

import ch.ethz.asltest.Utilities.WorkQueue;

public class QueueHandler extends AbstractHandler implements Runnable {

    private final WorkQueue workQueue;

    public QueueHandler(WorkQueue workQueue)
    {
        this.workQueue = workQueue;
    }

    @Override
    public void run()
    {
        while (!this.enabled) { /* busy spin here until enabled, then fall-through */ }
        this.workQueue.queueStatistics.accumulate();
    }

    @Override
    public void enable()
    {
        super.enable();
        this.workQueue.queueStatistics.enableStatistics();
    }

    @Override
    public void disable()
    {
        super.disable();
        this.workQueue.queueStatistics.disableStatistics();
    }
}
