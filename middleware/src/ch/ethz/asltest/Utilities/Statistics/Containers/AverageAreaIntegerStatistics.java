package ch.ethz.asltest.Utilities.Statistics.Containers;

import ch.ethz.asltest.Utilities.Misc.Tuple;
import ch.ethz.asltest.Utilities.Statistics.Element.AccumulationLongElement;

public final class AverageAreaIntegerStatistics extends AverageIntegerStatistics {
    public AverageAreaIntegerStatistics() {}

    @Override
    public final void addElement(long timestamp, long element)
    {
        if (timestampWithinCurrentWindow(timestamp)) {
            addInWindowBoundary(timestamp);
        } else {
            addOverWindowBoundary(timestamp);
        }
        lastElement = element;
    }

    @Override
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
        windowElement.reuseElement(getCurrentWindowStart(), 1,splitTimestamp.second * lastElement);
    }

    @Override
    final void addInWindowBoundary(long timestamp)
    {
        // Add to the current window and update internal state
        long deltaLatest = diffToLatestTimestamp(timestamp);
        windowElement.addElement(deltaLatest * lastElement);
        updateLatestElementTimestamp(timestamp);
    }

    @Override
    final void finishLastWindow(long deltaRemaining)
    {
        // Add element to the previous window, then calculate the average of it
        windowElement.addElement(deltaRemaining * lastElement);
        finishWindow(false);
    }

    @Override
    final void finishWindow(boolean useLastElement)
    {
        if (useLastElement) {
            windowAverages.put(getWindow() - TIME_INTERVAL, (double) lastElement);
        } else {
            windowAverages.put(getWindow() - TIME_INTERVAL, ((double) windowElement.getAccumulated()) / WINDOW_SIZE);
        }
    }
}