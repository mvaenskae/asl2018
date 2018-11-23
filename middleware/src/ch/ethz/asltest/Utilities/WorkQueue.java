package ch.ethz.asltest.Utilities;

import ch.ethz.asltest.Utilities.Statistics.Containers.AverageAreaIntegerStatistics;
import ch.ethz.asltest.Utilities.Packets.WorkUnit.WorkUnit;

import java.util.concurrent.ArrayBlockingQueue;

public final class WorkQueue {
    private final ArrayBlockingQueue<WorkUnit> workUnits;
    public final AverageAreaIntegerStatistics queueStatistics = new AverageAreaIntegerStatistics();

    public WorkQueue(int size)
    {
        this.workUnits = new ArrayBlockingQueue<>(size);
    }

    public WorkUnit get() throws InterruptedException
    {
        WorkUnit item = this.workUnits.take();
        updatePopped(item);
        return item;
    }

    public void put(WorkUnit unit) throws InterruptedException
    {
        updatePushed(unit);
        this.workUnits.put(unit);
    }

    public boolean isEmpty()
    {
        return this.workUnits.isEmpty();
    }

    private void updatePopped(WorkUnit item)
    {
        synchronized (queueStatistics) {
            long timestamp = System.nanoTime();
            item.timestamp.setPopFromQueue(timestamp);
            this.queueStatistics.addElement(timestamp, this.workUnits.size() + 1);
        }
    }

    private void updatePushed(WorkUnit item)
    {
        synchronized (queueStatistics) {
            long timestamp = System.nanoTime();
            item.timestamp.setPushOnQueue(timestamp);
            this.queueStatistics.addElement(timestamp, this.workUnits.size());
        }
    }
}