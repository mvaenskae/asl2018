package ch.ethz.asltest.Utilities.Statistics.StatisticsElement;

public final class WorkerElementGet extends WorkerElement {

    public WorkerElementGet(long queueWaitingTime, long memcachedRTT, long totalResponseTime)
    {
        super(queueWaitingTime, memcachedRTT, totalResponseTime);
        this.elementType = WorkerElementType.GET;
    }
}