package ch.ethz.asltest.Utilities.Statistics.Containers;

public class AccumulationStatistics extends AverageIntegerStatistics {

    public AccumulationStatistics() {}

    public AccumulationStatistics(boolean placeholder) {
        super(placeholder);
    }

    @Override
    void finishWindow(boolean useLastElement)
    {
        double currWindow = getWindow() - TIME_INTERVAL;
        if (currWindow < 0) {
            currWindow = 0;
        }
        if (useLastElement) {
            windowAverages.put(currWindow, 0.0);
        } else {
            windowAverages.put(currWindow, ((double) windowElement.getAccumulated()));
        }
    }
}