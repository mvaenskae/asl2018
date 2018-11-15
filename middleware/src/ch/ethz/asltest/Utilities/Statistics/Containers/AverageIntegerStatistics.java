package ch.ethz.asltest.Utilities.Statistics.Containers;

import ch.ethz.asltest.Utilities.Misc.Tuple;
import ch.ethz.asltest.Utilities.Statistics.Element.AccumulationLongElement;
import ch.ethz.asltest.Utilities.Statistics.Element.TimestampedElement;

public class AverageIntegerStatistics extends WindowStatistics {
    AccumulationLongElement windowElement = new AccumulationLongElement(0L, 0, 0L);
    private long lastElement;

    public void addElement(long timestamp, long element)
    {
        if (timestampWithinCurrentWindow(timestamp)) {
            addInWindowBoundary(timestamp);
        } else {
            addOverWindowBoundary(timestamp);
        }
        lastElement = element;
    }

    final void addOverWindowBoundary(long timestamp)
    {
        // Split up the current timestamp into current window and next window (after calling it the current window is the next window)
        Tuple<Long, Long> splitTimestamp = splitOverWindowBoundary(timestamp);

        // Add the last element to the previous window, then calculate the average of it
        finishLastWindow(splitTimestamp.first);

        // Accumulation may have slept for longer than a single boundary crossing -- infer how many there were
        while (splitTimestamp.second > WINDOW_SIZE) {
            splitTimestamp = splitOverWindowBoundary(timestamp);
            finishWindow(true);
        }

        // Finally start a new window and store the weightings for averaging
        windowElement.reuseElement(getCurrentWindowStart(), splitTimestamp.second * lastElement);
    }

    final void addInWindowBoundary(long timestamp)
    {
        // Add to the current window and update internal state
        long deltaLatest = diffToLatestTimestamp(timestamp);
        windowElement.addElement(deltaLatest * lastElement);
        updateLatestElementTimestamp(timestamp);
    }

    final void finishLastWindow(long deltaRemaining)
    {
        // Add element to the previous window, then calculate the average of it
        windowElement.addElement(deltaRemaining * lastElement);
        finishWindow(false);
    }

    void finishWindow(boolean useLastElement)
    {
        if (useLastElement) {
            windowAverages.add(new TimestampedElement(getWindow() - TIME_INTERVAL, (double) lastElement));
        } else {
            windowAverages.add(new TimestampedElement(getWindow() - TIME_INTERVAL, ((double) windowElement.getAccumulated()) / WINDOW_SIZE));
        }
    }

    public final void stopStatistics()
    {
        long diffUntilEndOfLastWindow = diffToLatestTimestamp(getCurrentWindowStart() + WINDOW_SIZE);

        // Split up the current timestamp into current window and next window (after calling it the current window is the next window)
        Tuple<Long, Long> splitTimestamp = splitOverWindowBoundary(diffUntilEndOfLastWindow);

        // Add the last element to the previous window, then calculate the average of it
        finishLastWindow(splitTimestamp.first);

        // Accumulation may have slept for longer than a single boundary crossing -- infer how many there were
        while (splitTimestamp.second > 0) {
            splitTimestamp = splitOverWindowBoundary(diffUntilEndOfLastWindow);
            finishWindow(true);
        }
    }
}