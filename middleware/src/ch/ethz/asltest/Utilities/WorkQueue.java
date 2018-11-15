package ch.ethz.asltest.Utilities;

import ch.ethz.asltest.Utilities.Statistics.Containers.AverageIntegerStatistics;
import ch.ethz.asltest.Utilities.Packets.WorkUnit.WorkUnit;

import java.util.concurrent.ArrayBlockingQueue;

public final class WorkQueue {
    private final ArrayBlockingQueue<WorkUnit> workUnits;
    public final AverageIntegerStatistics queueStatistics = new AverageIntegerStatistics();

    public WorkQueue(int size)
    {
        this.workUnits = new ArrayBlockingQueue<>(size);
    }

    public WorkUnit get() throws InterruptedException
    {
        WorkUnit item = this.workUnits.take();
        updateStatistics(item);
        return item;
    }

    public void put(WorkUnit unit) throws InterruptedException
    {
        this.workUnits.put(unit);
        updateStatistics(unit);
    }

    public boolean isEmpty()
    {
        return this.workUnits.isEmpty();
    }

    private void updateStatistics(WorkUnit item)
    {
        synchronized (queueStatistics) {
            long timestamp = System.nanoTime();
            item.timestamp.setPopFromQueue(timestamp);
            this.queueStatistics.addElement(timestamp, this.workUnits.size());
        }
    }
}