package ch.ethz.asltest.Utilities.Statistics.Containers;

import ch.ethz.asltest.Utilities.Statistics.Element.TimestampedElement;

public final class CountIntegerStatistics extends AverageIntegerStatistics {

    @Override
    void finishWindow(boolean useLastElement)
    {
        if (useLastElement) {
            windowAverages.add(new TimestampedElement(getWindow() - TIME_INTERVAL, 0.0));
        } else {
            windowAverages.add(new TimestampedElement(getWindow() - TIME_INTERVAL, (double) windowElement.getAccumulated() / TIME_INTERVAL));
        }
    }
}