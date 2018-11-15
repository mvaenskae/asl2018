package ch.ethz.asltest.Utilities.Statistics.Containers;

import ch.ethz.asltest.Utilities.Statistics.MiddlewareStatistics;
import ch.ethz.asltest.Utilities.Statistics.Element.TimestampedElement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

public abstract class WindowStatistics extends MiddlewareStatistics {
    /*
     * This class keeps a list of windows which are timestamped and hold an aggregation for said window. The aggregation
     * is done by using an area-under-the-curve approach similar to integrating a function. The assumption made here is
     * that on each update operation the previous value is constant from the moment it was the current newest value. As
     * such this design integrates the area under many "glued on" bars.
     */

    // Stored values which will be kept for the duration of execution
    final ArrayList<TimestampedElement> windowAverages = new ArrayList<>(INITIAL_CAPACITY);

    public abstract void addElement(long timestamp, long element);

    abstract void addOverWindowBoundary(long timestamp);

    abstract void addInWindowBoundary(long timestamp);

    abstract void finishLastWindow(long deltaRemaining);

    public abstract void stopStatistics();

    public final ArrayList<TimestampedElement> getWindowAverages()
    {
        return new ArrayList<>(this.windowAverages);
    }

    public final void printStatistics(Path filepath, boolean useSTDOUT) throws IOException
    {
        StringBuilder temp = new StringBuilder();
        getWindowAverages().forEach(item -> temp.append(item).append("\n"));
        if (useSTDOUT) {
            System.out.println(temp.toString());
        } else {
            byte[] tempBytes = temp.toString().getBytes();
            Path queueStatisticsFile = Files.createFile(filepath);
            Files.write(queueStatisticsFile, tempBytes);
        }
    }

    public final void addOther(WindowStatistics other)
    {
        int arrayDiff = this.windowAverages.size() - other.windowAverages.size();
        int minCount = arrayDiff > 0 ? other.windowAverages.size(): this.windowAverages.size();

        for (int i = 0; i < minCount; ++i) {
            this.windowAverages.get(i).addElement(other.windowAverages.get(i));
        }

        if (arrayDiff > 0) {
            for (int i = minCount + 1; i < other.windowAverages.size(); ++i) {
                this.windowAverages.add(other.windowAverages.get(i));
            }
        }
    }
}