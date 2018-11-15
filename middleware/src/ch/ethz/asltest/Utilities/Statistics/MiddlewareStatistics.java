package ch.ethz.asltest.Utilities.Statistics;

import ch.ethz.asltest.Utilities.Misc.Tuple;

import java.util.concurrent.TimeUnit;

abstract public class MiddlewareStatistics {
    private static volatile boolean enabled = false;

    final protected static int INITIAL_CAPACITY = 90;
    final protected static TimeUnit TIME_UNIT = TimeUnit.SECONDS;

    final protected long WINDOW_SIZE = multiplyGetLong(TIME_INTERVAL, NANOS_TO_SECOND);
    // Defines the size of each window normalized to a second.
    final protected static double TIME_INTERVAL = 1;
    final protected static long NANOS_TO_SECOND = 1_000_000_000;

    private static long enabledTimestamp;

    // Continuous "counter" for the currently active window normalized to a second.
    private double windowNormalized = 0;
    private long latestElementTimestamp = 0;

    public static void enableStatistics() {
        enabledTimestamp = System.nanoTime();
        enabled = true;
    }

    public static void disableStatistics()
    {
        enabled = false;
    }

    private static long multiplyGetLong(double a, double b)
    {
        double res = a * b;
        return Math.round(res);
    }

    protected double getWindow()
    {
        return windowNormalized;
    }

    private void updateWindow()
    {
        windowNormalized += TIME_INTERVAL;
    }

    protected long getCurrentWindowStart()
    {
        return multiplyGetLong(NANOS_TO_SECOND, windowNormalized) + enabledTimestamp;
    }

    private long diffToCurrentWindow(long timestamp)
    {
        return timestamp - getCurrentWindowStart();
    }

    protected long diffToLatestTimestamp(long timestamp)
    {
        if (latestElementTimestamp == 0) {
            latestElementTimestamp = enabledTimestamp;
        }
        return timestamp - latestElementTimestamp;
    }

    protected void updateLatestElementTimestamp(long timestamp)
    {
        this.latestElementTimestamp = timestamp;
    }

    protected Tuple<Long, Long> splitOverWindowBoundary(long timestamp)
    {
        updateWindow();
        long previousSecondQueue = diffToLatestTimestamp(getCurrentWindowStart());
        updateLatestElementTimestamp(timestamp);
        long nextSecondQueue = diffToCurrentWindow(timestamp);
        return new Tuple<>(previousSecondQueue, nextSecondQueue);
    }

    protected boolean timestampWithinCurrentWindow(long timestamp)
    {
        return diffToCurrentWindow(timestamp) < TIME_INTERVAL * WINDOW_SIZE;
    }
}