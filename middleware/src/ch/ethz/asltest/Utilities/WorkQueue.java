package ch.ethz.asltest.Utilities;

import ch.ethz.asltest.Utilities.Statistics.Containers.QueueStatistics;
import ch.ethz.asltest.Utilities.Packets.WorkUnit.WorkUnit;

import java.util.concurrent.ArrayBlockingQueue;

public final class WorkQueue {
    private final ArrayBlockingQueue<WorkUnit> workUnits;
    public final QueueStatistics queueStatistics;

    public WorkQueue(int size)
    {
        this.workUnits = new ArrayBlockingQueue<>(size);
        this.queueStatistics = new QueueStatistics(this);
    }

    public WorkUnit get() throws InterruptedException
    {
        WorkUnit temp = this.workUnits.take();
        long gotFromQueue = System.nanoTime();
        temp.timestamp.setPopFromQueue(gotFromQueue);
        synchronized (queueStatistics) {
            this.queueStatistics.poppedElement(gotFromQueue);
        }
        return temp;
    }

    public void put(WorkUnit unit) throws InterruptedException
    {
        this.workUnits.put(unit);
        long putOnQueue = System.nanoTime();
        unit.timestamp.setPushOnQueue(putOnQueue);
        synchronized (queueStatistics) {
            this.queueStatistics.pushedElement(putOnQueue);
        }
    }

    public boolean isEmpty()
    {
        return this.workUnits.isEmpty();
    }

    public int getSize()
    {
        return this.workUnits.size();
    }
}