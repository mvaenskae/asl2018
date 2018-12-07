package ch.ethz.asltest.Utilities.Statistics.Element;

import ch.ethz.asltest.Utilities.Statistics.Containers.AverageIntegerStatistics;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public final class WorkerMultiGetElement extends WorkerGetElement {

    // Keep track of the following averages:
    private final AverageIntegerStatistics keySizeCounter;
    private final HashMap<String, Long> requestsPerServer = new HashMap<>();

    // Final variables only to be computed for the summary of this element.
    private double finalKeySizes;
    private final HashMap<String, Double> finalAverageKeysPerServer = new HashMap<>();

    public WorkerMultiGetElement() {
        super();
        keySizeCounter = new AverageIntegerStatistics();
    }

    public WorkerMultiGetElement(boolean placeholder)
    {
        super(placeholder);
        keySizeCounter = new AverageIntegerStatistics(placeholder);
    }

    public void recordServerLoad(HashMap<String, Long> distributedGets)
    {
        requestsPerServer.putAll(distributedGets);
    }

    public void recordKeySize(long timestamp, long keySize)
    {
        keySizeCounter.addElement(timestamp, keySize);
    }

    public void cacheMiss(long timestamp, long missCount)
    {
        for (int i = 0; i < missCount; ++i) {
            this.memcachedMisses.addElement(timestamp, 1L);
        }
    }

    public void merge(WorkerMultiGetElement other)
    {
        super.merge(other);
        this.keySizeCounter.averageWithOther(other.keySizeCounter, this.totalOpsEncountered, other.totalOpsEncountered);
        other.requestsPerServer.forEach((key, value) -> this.requestsPerServer.merge(key, value, Long::sum));
    }

    public void disableStatistics()
    {
        super.disableStatistics();
        keySizeCounter.stopStatistics();
    }

    @Override
    public String toString()
    {
        return super.toString();
    }

    public void printWindowStatistics(Path basedirectoryPath, String prefix, boolean useSTDOUT) throws IOException
    {
        super.printWindowStatistics(basedirectoryPath, prefix, useSTDOUT);

        Path filename = Paths.get(basedirectoryPath.toString(), prefix+"keySizeCounter.txt");
        keySizeCounter.printStatistics(filename, useSTDOUT);
    }

    public HashMap<Double, ArrayList<Double>> getCsv()
    {
        HashMap<Double, ArrayList<Double>> csvLayout = super.getCsv();

        // Then add the average key size per window!
        HashMap<Double, Double> averageKeySize = new HashMap<>();
        keySizeCounter.getWindowAverages().forEach(entry ->
                averageKeySize.put(entry.getKey(), entry.getValue())
        );

        numberOfOps.getWindowAverages().forEach(entry -> {
            double keysRequested = averageKeySize.get(entry.getKey()) * entry.getValue();
            averageKeySize.put(entry.getKey(), keysRequested);
        });

        averageKeySize.forEach((entry, value) ->
                csvLayout.get(entry).add(value)
        );

        keySizeCounter.getWindowAverages().forEach(entry ->
                csvLayout.get(entry.getKey()).add(entry.getValue())
        );

        return csvLayout;
    }

    void getSummary()
    {
        finalOpCount = numberOfOps.getWindowAverages().stream().mapToLong(value -> value.getValue().longValue()).sum();
        finalAverageWaitingTimeQueue = averageWaitingTimeQueue.getWindowAverages().stream().mapToDouble(Map.Entry::getValue).summaryStatistics().getAverage();
        finalAverageServiceTimeMemcached = averageServiceTimeMemcached.getWindowAverages().stream().mapToDouble(Map.Entry::getValue).summaryStatistics().getAverage();
        finalAverageRTT = averageRTT.getWindowAverages().stream().mapToDouble(Map.Entry::getValue).summaryStatistics().getAverage();
        finalKeySizes = keySizeCounter.getWindowAverages().stream().mapToLong(value -> value.getValue().longValue()).summaryStatistics().getAverage();
        requestsPerServer.forEach((key, value) -> {
            double temp = ((double) value) / finalOpCount;
            if (!Double.isFinite(temp)) {
                temp = 0.0;
            }
            this.finalAverageKeysPerServer.put(key, temp);
        });

        double totalMissCount = memcachedMisses.getWindowAverages().stream().mapToDouble(value -> value.getValue().longValue()).sum();

        double temp = totalMissCount / finalKeySizes;
        if (!Double.isFinite(temp)) {
            temp = 0.0;
        }
        finalMemcachedMissRate = temp;
    }

    protected String getTotalsAsString()
    {
        StringBuilder keysPerServer = new StringBuilder();
        finalAverageKeysPerServer.forEach((key, value) -> keysPerServer.append(String.format("%f ", value)));
        keysPerServer.deleteCharAt(keysPerServer.length()-1);

        double perWindowKeySizes = ((double) finalKeySizes) / numberOfOps.getWindowAverages().size();
        if (!Double.isFinite(perWindowKeySizes)) {
            perWindowKeySizes = 0.0;
        }

        String perWindowKeys = String.format("%f %f", finalKeySizes, perWindowKeySizes);

        return super.getTotalsAsString() + perWindowKeys + NEW_LINE + keysPerServer.toString() + NEW_LINE;
    }
}