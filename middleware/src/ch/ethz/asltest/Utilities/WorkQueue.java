package ch.ethz.asltest.Utilities;

import java.util.concurrent.ArrayBlockingQueue;

public class WorkQueue {
    public ArrayBlockingQueue<WorkUnit> workUnits;

    public WorkQueue(int size) {
        this.workUnits = new ArrayBlockingQueue<>(size);
    }

    private ArrayBlockingQueue<WorkUnit> getList() {
        return this.workUnits;
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