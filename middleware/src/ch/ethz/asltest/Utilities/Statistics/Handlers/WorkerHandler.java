package ch.ethz.asltest.Utilities.Statistics.Handlers;

import ch.ethz.asltest.Memcached.Worker;

import java.util.ArrayList;

public class WorkerHandler extends AbstractHandler implements Runnable {

    private final ArrayList<Worker> workerList;

    public WorkerHandler(ArrayList<Worker> workerList)
    {
        this.workerList = workerList;
    }

    @Override
    public void run()
    {
        while (!this.enabled) { /* busy spin here until enabled, then fall-through */ }
        //workerList.forEach(worker -> worker.workerStats.accumulate());
    }

    @Override
    public void enable()
    {
        super.enable();
        workerList.forEach(worker -> worker.workerStats.enableStatistics());
    }

    @Override
    public void disable()
    {
        super.disable();
        workerList.forEach(worker -> worker.workerStats.disableStatistics());
    }
}