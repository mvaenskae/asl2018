package ch.ethz.asltest.Utilities.Statistics.Containers;

import ch.ethz.asltest.Utilities.Misc.Tuple;

import java.util.concurrent.TimeUnit;

abstract public class MiddlewareStatistics {
    public final static int TIME_INTERVAL = 1;
    public final static TimeUnit TIME_UNIT = TimeUnit.SECONDS;
    public final int NANOS_TO_SECOND = 1_000_000_000;

    final static int INITIAL_CAPACITY = 90;

    private static long enabledTimestamp;

    private int secondCounter = 0;
    private long latestElementTimestamp = 0;

    static volatile boolean enabled = false;

    // public abstract void accumulate();

    public static void enableStatistics() {
        enabledTimestamp = System.nanoTime();
        enabled = true;
    }

    public static void disableStatistics()
    {
        enabled = false;
    }

    long getCurrentSecondAsNano()
    {
        return (long) secondCounter * NANOS_TO_SECOND + enabledTimestamp;
    }

    int getSecond()
    {
        return secondCounter;
    }

    private void updateSecond()
    {
        ++secondCounter;
    }

    private long diffToCurrentSecond(long timestamp)
    {
        return timestamp - getCurrentSecondAsNano();
    }

    long diffToLatestTimestamp(long timestamp)
    {
        if (latestElementTimestamp == 0) {
            latestElementTimestamp = enabledTimestamp;
        }
        return timestamp - latestElementTimestamp;
    }

    void updateLatestElementTimestamp(long timestamp)
    {
        this.latestElementTimestamp = timestamp;
    }

    Tuple<Long, Long> splitOverSecondsBoundary(long timestamp)
    {
        updateSecond();
        long previousSecondQueue = diffToLatestTimestamp(getCurrentSecondAsNano());
        updateLatestElementTimestamp(timestamp);
        long nextSecondQueue = diffToCurrentSecond(timestamp);
        return new Tuple<>(previousSecondQueue, nextSecondQueue);
    }

    boolean timestampWithinCurrentSecond(long timestamp)
    {
        return diffToCurrentSecond(timestamp) < NANOS_TO_SECOND;
    }
}
