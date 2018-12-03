package ch.ethz.asltest.Utilities.Statistics.Element;

import ch.ethz.asltest.Utilities.Statistics.Containers.CounterStatistics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class WorkerGetElement extends WorkerElement {

    // Keep track of the following averages:
    final CounterStatistics memcachedMisses;

    // Final variables only to be computed for the summary of this element.
    double finalMemcachedMissRate;

    public WorkerGetElement() {
        super();
        memcachedMisses = new CounterStatistics();
    }

    public WorkerGetElement(boolean placeholder)
    {
        super(placeholder);
        memcachedMisses = new CounterStatistics(placeholder);
    }

    public void cacheMiss(long timestamp, long missCount)
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

    public void printWindowStatistics(Path basedirectoryPath, String prefix, boolean useSTDOUT) throws IOException
    {
        super.printWindowStatistics(basedirectoryPath, prefix, useSTDOUT);

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

    void getSummary()
    {
        super.getSummary();
        double totalMissCount = memcachedMisses.getWindowAverages().stream().mapToDouble(value -> value.getValue().longValue()).sum();
        double temp = totalMissCount / finalOpCount;
        if (!Double.isFinite(temp)) {
            temp = 0.0;
        }
        finalMemcachedMissRate = temp;
    }

    protected String getTotalsAsString()
    {
        double perWindowMissRate = ((double) finalMemcachedMissRate) / memcachedMisses.getWindowAverages().size();
        if (!Double.isFinite(perWindowMissRate)) {
            perWindowMissRate = 0.0;
        }

        String finalMissRate = String.format("%f %f%s", finalMemcachedMissRate, perWindowMissRate, NEW_LINE);

        return super.getTotalsAsString() + finalMissRate;
    }
}