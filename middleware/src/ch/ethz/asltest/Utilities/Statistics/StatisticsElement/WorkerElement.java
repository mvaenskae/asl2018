package ch.ethz.asltest.Utilities.Statistics.StatisticsElement;

import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.HashMap;

public abstract class WorkerElement extends StatisticsElement {

    public WorkerElementType elementType;

    public long totalResponseTime;
    public long queueWaitingTime;
    public long memcachedRTT;

    public final HashMap<SelectionKey, ArrayList<Integer>> memcachedResponseTimes = new HashMap<>();

    public WorkerElement(long queueWaitingTime, long memcachedRTT, long totalResponseTime)
    {
        this.queueWaitingTime = queueWaitingTime;
        this.memcachedRTT = memcachedRTT;
        this.totalResponseTime = totalResponseTime;
    }

    @Override
    public String toString()
    {
        return super.toString();
    }
}