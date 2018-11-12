package ch.ethz.asltest.Utilities.Statistics.StatisticsElement;

public final class WorkerElementSet extends WorkerElement {

    public WorkerElementSet(long queueWaitingTime, long memcachedRTT, long totalResponseTime)
    {
        super(queueWaitingTime, memcachedRTT, totalResponseTime);
        this.elementType = WorkerElementType.SET;
    }
}