package ch.ethz.asltest.Utilities.Statistics.StatisticsElement;

public final class WorkerElementMultiGet extends WorkerElement {

    public WorkerElementMultiGet(long queueWaitingTime, long memcachedRTT, long totalResponseTime)
    {
        super(queueWaitingTime, memcachedRTT, totalResponseTime);
        this.elementType = WorkerElementType.MULTIGET;
    }
}