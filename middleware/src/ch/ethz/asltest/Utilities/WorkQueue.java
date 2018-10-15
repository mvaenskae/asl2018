package ch.ethz.asltest.Utilities;

import ch.ethz.asltest.Utilities.WorkUnit.WorkUnit;

import java.util.concurrent.ArrayBlockingQueue;

public class WorkQueue {
    private final ArrayBlockingQueue<WorkUnit> workUnits;

    public WorkQueue(int size) {
        this.workUnits = new ArrayBlockingQueue<>(size);
    }

    public int getSize() {
        return this.workUnits.size();
    }

    public WorkUnit get() throws InterruptedException {
        return this.workUnits.take();
    }

    public void put(WorkUnit unit) throws InterruptedException {
        this.workUnits.put(unit);
    }

    public boolean isEmpty() {
        return this.workUnits.isEmpty();
    }
}