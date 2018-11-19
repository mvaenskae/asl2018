package ch.ethz.asltest.Utilities.Statistics.Element;

import ch.ethz.asltest.Utilities.Statistics.Containers.CounterStatistics;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;

public class WorkerGetElement extends WorkerElement {

    // Keep track of the following averages:
    final CounterStatistics memcachedMisses;

    public WorkerGetElement() {
        super();
        memcachedMisses = new CounterStatistics();
    }

    public WorkerGetElement(boolean placeholder)
    {
        super(placeholder);
        memcachedMisses = new CounterStatistics(placeholder);
    }

    public void cacheMiss(long timestamp, int missCount)
    {
        this.memcachedMisses.addElement(timestamp, missCount);
    }

    public void incrementOpCounter(long timestamp)
    {
        numberOfOps.addElement(timestamp, 1L);
    }

    public void merge(WorkerGetElement other)
    {
        super.merge(other);
        this.memcachedMisses.addOther(other.memcachedMisses);
    }

    public void addOtherWeighted(WorkerGetElement other, int weighting)
    {
        super.addOtherWeighted(other, weighting);
        this.memcachedMisses.addOther(other.memcachedMisses);
    }

    public void disableStatistics()
    {
        super.disableStatistics();
        memcachedMisses.stopStatistics();
    }

    @Override
    public String toString()
    {
        return super.toString();
    }

    public void printStatistics(Path basedirectoryPath, String prefix, boolean useSTDOUT) throws IOException
    {
        super.printStatistics(basedirectoryPath, prefix, useSTDOUT);

        Path filename = Paths.get(basedirectoryPath.toString(), prefix+"missCount.txt");
        memcachedMisses.printStatistics(filename, useSTDOUT);
    }

    @Override
    public HashMap<Double, ArrayList<Double>> getCsv()
    {
        HashMap<Double, ArrayList<Double>> csvLayout = super.getCsv();

        HashMap<Double, Double> missrate = new HashMap<>();
        numberOfOps.getWindowAverages().forEach(entry ->
                missrate.put(entry.getKey(), entry.getValue())
        );

        memcachedMisses.getWindowAverages().forEach(entry ->
        {
            double missRate = entry.getValue() / missrate.get(entry.getKey());
            if (Double.isNaN(missRate)) {
                missRate = 0.0;
            }
            missrate.put(entry.getKey(), missRate);
        });

        missrate.forEach((entry, value) ->
                csvLayout.get(entry).add(value)
        );

        return csvLayout;
    }
}