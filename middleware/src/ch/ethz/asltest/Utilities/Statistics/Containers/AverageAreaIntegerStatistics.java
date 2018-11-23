package ch.ethz.asltest.Utilities.Statistics.Containers;

import ch.ethz.asltest.Utilities.Misc.Tuple;
import ch.ethz.asltest.Utilities.Statistics.Element.AccumulationLongElement;

public final class AverageAreaIntegerStatistics extends AverageIntegerStatistics {
    public AverageAreaIntegerStatistics() {}

    public AverageAreaIntegerStatistics(boolean placeholder)
    {
        super(placeholder);

    }

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

        boolean lateStart = windowAverages.size() < 1 && windowElement.getElementCount() == 0;

        if (lateStart) {
            // Edge case where statistics for this queue were not collected from the very first second
            finishWindow(true);
        } else {
            // Add the last element to the previous window, then calculate the average of it
            finishLastWindow(splitTimestamp.first);
        }

        // Accumulation may have slept for longer than a single boundary crossing -- infer how many there were
        while (splitTimestamp.second > WINDOW_SIZE) {
            splitTimestamp = splitOverWindowBoundary(timestamp);
            finishWindow(true);
        }

        if (lateStart) {
            // Finally start a new window
            windowElement.reuseElement(getCurrentWindowStart(), 1,splitTimestamp.second * lastElement);
        } else {
            windowElement.reuseElement(getCurrentWindowStart(), 0);

        }
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
        double currWindow = getWindow() - TIME_INTERVAL;
        if (currWindow < 0) {
            currWindow = 0;
        }
        if (useLastElement) {
            windowAverages.put(currWindow, (double) lastElement);
        } else {
            windowAverages.put(currWindow, ((double) windowElement.getAccumulated()) / WINDOW_SIZE);
        }
    }
}