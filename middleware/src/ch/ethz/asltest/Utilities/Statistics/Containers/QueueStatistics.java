package ch.ethz.asltest.Utilities.Statistics.Containers;

import ch.ethz.asltest.Utilities.Misc.Tuple;
import ch.ethz.asltest.Utilities.Statistics.StatisticsElement.AccumulationLongElement;
import ch.ethz.asltest.Utilities.Statistics.StatisticsElement.TimestampedElement;
import ch.ethz.asltest.Utilities.WorkQueue;

import java.util.ArrayList;

public final class QueueStatistics extends MiddlewareStatistics {

    /*
     * This class manages simple queue statistics. Due to the requirements it will only accumulate the queue size.
     */

    // Stored values which will be kept for the duration of execution
    private final ArrayList<TimestampedElement> windowAverages = new ArrayList<>(INITIAL_CAPACITY);

    private AccumulationLongElement windowElement = new AccumulationLongElement(0L, 0, 0L);
    private final WorkQueue workQueue;
    private int lastQueueSize;

    public QueueStatistics(WorkQueue workQueue)
    {
        this.workQueue = workQueue;
    }

    //@Override
    public void accumulate()
    {
        if (enabled) {
            windowAverages.add(new TimestampedElement<>(this.workQueue.getSize()));
        }
    }

    public void pushedElement(long timestamp)
    {
        snapshotQueueSize(timestamp);
    }

    public void poppedElement(long timestamp)
    {
        snapshotQueueSize(timestamp);
    }

    private void snapshotQueueSize(long timestamp)
    {
        int queueSize = this.workQueue.getSize();
        if (timestampWithinCurrentSecond(timestamp)) {
            snapshotWithinWindowBoundary(timestamp, queueSize);
        } else {
            snapshotOverWindowBoundaries(timestamp, queueSize);
        }
        lastQueueSize = queueSize;
    }

    private void snapshotOverWindowBoundaries(long timestamp, int queueSize) {
        // Split up the current timestamp into current window and next window (after calling it the current window is the next window)
        Tuple<Long, Long> splitTimestamp = splitOverSecondsBoundary(timestamp);

        // Add the last element to the previous window, then calculate the average of it
        windowElement.addElement(splitTimestamp.first * queueSize);
        windowAverages.add(new TimestampedElement<>(getSecond() - 1, windowElement.getAverage() / NANOS_TO_SECOND));

        // Accumulation may have slept for longer than a single boundary crossing -- infer how many there were
        while (splitTimestamp.second > NANOS_TO_SECOND) {
            splitTimestamp = splitOverSecondsBoundary(timestamp);
            windowAverages.add(new TimestampedElement<>(getSecond() - 1, (double) lastQueueSize));
        }

        // Finally start a new window and store the weightings for averaging
        windowElement = new AccumulationLongElement(getCurrentSecondAsNano(), splitTimestamp.second * queueSize);
    }

    private void snapshotWithinWindowBoundary(long timestamp, int queueSize)
    {
        // Add to the current window and update internal state
        long deltaLatest = diffToLatestTimestamp(timestamp);
        windowElement.addElement(deltaLatest * queueSize);
        updateLatestElementTimestamp(timestamp);
    }

    public final void stopQueue()
    {
        int queueSize = this.workQueue.getSize();
        long diffUntilEndOfLastWindow = diffToLatestTimestamp(getCurrentSecondAsNano() + NANOS_TO_SECOND);

        // Split up the current timestamp into current window and next window (after calling it the current window is the next window)
        Tuple<Long, Long> splitTimestamp = splitOverSecondsBoundary(diffUntilEndOfLastWindow);

        // Add the last element to the previous window, then calculate the average of it
        windowElement.addElement(splitTimestamp.first * queueSize);
        windowAverages.add(new TimestampedElement<>(getSecond() - 1, windowElement.getAverage() / NANOS_TO_SECOND));

        // Accumulation may have slept for longer than a single boundary crossing -- infer how many there were
        while (splitTimestamp.second > 0) {
            splitTimestamp = splitOverSecondsBoundary(diffUntilEndOfLastWindow);
            windowAverages.add(new TimestampedElement<>(getSecond() - 1, (double) lastQueueSize));
        }
    }

    public final ArrayList<TimestampedElement> getWindowAverages()
    {
        return new ArrayList<>(this.windowAverages);
    }
}