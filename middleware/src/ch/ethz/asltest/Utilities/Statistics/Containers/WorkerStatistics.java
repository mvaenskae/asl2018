package ch.ethz.asltest.Utilities.Statistics.Containers;

import ch.ethz.asltest.Utilities.Statistics.MiddlewareStatistics;
import ch.ethz.asltest.Utilities.Statistics.Element.WorkerElement;

public final class WorkerStatistics extends MiddlewareStatistics {

    /*
     * This class manages local statistics for each worker. It tracks the waiting time for each element popped from the
     * queue (finalized it), a count of elements received from the queue and the service time for each memcached server.
     */

    private final CountIntegerStatistics invalidPacketCounter = new CountIntegerStatistics();
    private final CountIntegerStatistics memcachedMisses = new CountIntegerStatistics();

    public WorkerElement setElement = new WorkerElement();
    public WorkerElement getElement = new WorkerElement();
    public WorkerElement multiGetElement = new WorkerElement();

    public void cacheMiss(long timestamp, int missCount)
    {
        this.memcachedMisses.addElement(timestamp, missCount);
    }

    public void cacheMiss(long timestamp)
    {
        this.memcachedMisses.addElement(timestamp, 1L);
    }

    public void invalidPacketCounter(long timestamp)
    {
        this.invalidPacketCounter.addElement(timestamp, 1L);
    }

    public void addOther(WorkerStatistics other)
    {
        invalidPacketCounter.addOther(other.invalidPacketCounter);
        memcachedMisses.addOther(other.memcachedMisses);
        setElement.addOther(other.setElement);
        getElement.addOther(other.getElement);
        multiGetElement.addOther(other.multiGetElement);
    }

    public void stopStatistics()
    {
        invalidPacketCounter.stopStatistics();
        memcachedMisses.stopStatistics();
        setElement.disableStatistics();
        getElement.disableStatistics();
        multiGetElement.disableStatistics();
    }

/*
    public synchronized void insertWorkerElement(WorkerElement workerElement)
    {
        switch (workerElement.elementType) {
            case SET:
                setCounter++;
                putIntoHistogram(workerElement.memcachedRTT, this.histogramCounterSet);
                queueWaitingTimeSet.add(workerElement.queueWaitingTime);
                totalTimeSet.add(workerElement.totalResponseTime);
                break;
            case GET:
                getCounter++;
                putIntoHistogram(workerElement.memcachedRTT, this.histogramCounterGet);
                queueWaitingTimeGet.add(workerElement.queueWaitingTime);
                totalTimeGet.add(workerElement.totalResponseTime);
                break;
            case MULTIGET:
                multiGetCounter++;
                putIntoHistogram(workerElement.memcachedRTT, this.histogramCounterMultiGet);
                queueWaitingTimeMultiGet.add(workerElement.queueWaitingTime);
                totalTimeMultiGet.add(workerElement.totalResponseTime);
                break;
        }
    }


    private void sumUpAndClear(ArrayList<Long> averageList, ArrayList<Long> windowList)
    {
        averageList.add(windowList.stream().mapToLong(Long::longValue).sum());
        windowList.clear();
    }*/
}
