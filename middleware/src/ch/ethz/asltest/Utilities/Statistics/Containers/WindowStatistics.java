package ch.ethz.asltest.Utilities.Statistics.Containers;

import ch.ethz.asltest.Utilities.Statistics.MiddlewareStatistics;
import ch.ethz.asltest.Utilities.Statistics.Element.TimestampedElement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Time;
import java.util.*;

public abstract class WindowStatistics extends MiddlewareStatistics {
    /*
     * This class keeps a list of windows which are timestamped and hold an aggregation for said window. The aggregation
     * is done by using an area-under-the-curve approach similar to integrating a function. The assumption made here is
     * that on each update operation the previous value is constant from the moment it was the current newest value. As
     * such this design integrates the area under many "glued on" bars.
     */

    // Stored values which will be kept for the duration of execution
    HashMap<Double, Double> windowAverages = new HashMap<>(INITIAL_CAPACITY);

    public abstract void addElement(long timestamp, long element);

    abstract void addOverWindowBoundary(long timestamp);

    abstract void addInWindowBoundary(long timestamp);

    abstract void finishLastWindow(long deltaRemaining);

    public abstract void stopStatistics();

    public final ArrayList<Map.Entry<Double, Double>> getWindowAverages()
    {
        ArrayList<Map.Entry<Double, Double>> sortedList = new ArrayList<>(windowAverages.entrySet());
        sortedList.sort(Comparator.comparing(Map.Entry::getKey));
        return sortedList;
    }

    public final void printStatistics(Path filepath, boolean useSTDOUT) throws IOException
    {
        StringBuilder temp = new StringBuilder();
        getWindowAverages().forEach(item -> {
            temp.append(item.getKey()).append(" ").append(String.format("%f\n", item.getValue()));
        });
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
        other.windowAverages.forEach((key, value) -> this.windowAverages.merge(key, value, (v1, v2) -> v1 + v2));
    }

    public final void averageWithOther(WindowStatistics other, double w_self, double w_other)
    {
        other.windowAverages.forEach((key, value) ->
                this.windowAverages.merge(key, value, (v1, v2) -> ((v1 * w_self) + (v2 * w_other)) / (w_self + w_other)));
    }
}