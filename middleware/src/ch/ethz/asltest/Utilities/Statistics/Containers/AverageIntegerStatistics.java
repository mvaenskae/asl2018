package ch.ethz.asltest.Utilities.Statistics.Containers;

import ch.ethz.asltest.Utilities.Misc.Tuple;
import ch.ethz.asltest.Utilities.Statistics.Element.AccumulationLongElement;

public class AverageIntegerStatistics extends WindowStatistics {

    protected AccumulationLongElement windowElement = new AccumulationLongElement(0L, 0, 0L);
    protected long lastElement = 0;
    private long currElement;

    public AverageIntegerStatistics() {}

    public AverageIntegerStatistics(boolean placeholder) {
        windowAverages.put(0.0, 0.0);
    }

    public void addElement(long timestamp, long element)
    {
        currElement = element;
        if (timestampWithinCurrentWindow(timestamp)) {
            addInWindowBoundary(timestamp);
        } else {
            addOverWindowBoundary(timestamp);
        }
    }

    void addOverWindowBoundary(long timestamp)
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
            windowElement.reuseElement(getCurrentWindowStart(), currElement);
        } else {
            windowElement.reuseElement(getCurrentWindowStart(), 0);

        }
    }

    void addInWindowBoundary(long timestamp)
    {
        // Add to the current window and update internal state
        windowElement.addElement(currElement);
        updateLatestElementTimestamp(timestamp);
    }

    void finishLastWindow(long deltaRemaining)
    {
        // Add element to the previous window, then calculate the average of it
        windowElement.addElement(currElement);
        finishWindow(false);
    }

    void finishWindow(boolean useLastElement)
    {
        double currWindow = getWindow() - TIME_INTERVAL;
        if (currWindow < 0) {
            currWindow = 0;
        }
        if (useLastElement) {
            windowAverages.put(currWindow, 0.0);
        } else {
            windowAverages.put(currWindow, windowElement.getAverage());
        }
    }

    public final void stopStatistics()
    {
        // Dear reader: The following variable is non-sensically named but sound in logic until finishWindow()...
        boolean lastWindowEmpty = windowElement.getElementCount() > 0;
        // Finish the last active window
        if (lastWindowEmpty) {
            updateWindow();
        }
        finishWindow(!lastWindowEmpty);

        long timestamp = getDisabledTimestamp();
        // Split up the current timestamp into current window and next window (after calling it the current window is the next window)
        Tuple<Long, Long> splitTimestamp = splitOverWindowBoundary(timestamp);

        if (splitTimestamp.first > 0) {
            finishWindow(true);
        }

        // Accumulation may have slept for longer than a single boundary crossing
        while (splitTimestamp.second > 0) {
            splitTimestamp = splitOverWindowBoundary(timestamp);
            finishWindow(true);
        }
    }
}