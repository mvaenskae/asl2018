package ch.ethz.asltest.Utilities.Statistics.Element;

import ch.ethz.asltest.Utilities.Statistics.Containers.AccumulationStatistics;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public final class WorkerMultiGetElement extends WorkerGetElement {

    // Keep track of the following averages:
    private final AccumulationStatistics keySizeCounter;
    private final HashMap<String, Long> requestsPerServer = new HashMap<>();

    // Final variables only to be computed for the summary of this element.
    private long finalKeySizes;
    private final HashMap<String, Double> finalAverageKeysPerServer = new HashMap<>();

    public WorkerMultiGetElement() {
        super();
        keySizeCounter = new AccumulationStatistics();
    }

    public WorkerMultiGetElement(boolean placeholder)
    {
        super(placeholder);
        keySizeCounter = new AccumulationStatistics(placeholder);
    }

    public void recordServerLoad(HashMap<String, Long> distributedGets)
    {
        requestsPerServer.putAll(distributedGets);
    }

    public void recordKeySize(long timestamp, long keySize)
    {
        keySizeCounter.addElement(timestamp, keySize);
    }

    @Override
    public void merge(WorkerElement other)
    {
        super.merge(other);
        this.keySizeCounter.addOther(other.numberOfOps);
    }

    public void merge(WorkerMultiGetElement other)
    {
        super.merge(other);
        this.keySizeCounter.addOther(other.keySizeCounter);
        other.requestsPerServer.forEach((key, value) -> this.requestsPerServer.merge(key, value, Long::sum));

    }

    public void addOtherWeighted(WorkerMultiGetElement other, int weighting)
    {
        super.addOtherWeighted(other, weighting);
        this.keySizeCounter.addOther(other.keySizeCounter);
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
        HashMap<Double, ArrayList<Double>> csvLayout = new HashMap<>();

        numberOfOps.getWindowAverages().forEach(entry ->
        {
            csvLayout.put(entry.getKey(), new ArrayList<>());
            csvLayout.get(entry.getKey()).add(entry.getValue());
        });

        averageWaitingTimeQueue.getWindowAverages().forEach(entry ->
                csvLayout.get(entry.getKey()).add(entry.getValue())
        );

        averageServiceTimeMemcached.getWindowAverages().forEach(entry ->
                csvLayout.get(entry.getKey()).add(entry.getValue())
        );

        averageRTT.getWindowAverages().forEach(entry ->
                csvLayout.get(entry.getKey()).add(entry.getValue())
        );

        // Here modify the missCount to match on the actual elements!
        HashMap<Double, Double> missrate = new HashMap<>();
        keySizeCounter.getWindowAverages().forEach(entry ->
                missrate.put(entry.getKey(), entry.getValue())
        );

        memcachedMisses.getWindowAverages().forEach(entry ->
        {
            double missRate = entry.getValue() / missrate.get(entry.getKey());
            if (!Double.isFinite(missRate)) {
                missRate = 0.0;
            }
            missrate.put(entry.getKey(), missRate);
        });

        missrate.forEach((entry, value) ->
                csvLayout.get(entry).add(value)
        );

        // Then add the average key size per window!
        HashMap<Double, Double> averageKeySize = new HashMap<>();
        keySizeCounter.getWindowAverages().forEach(entry ->
                averageKeySize.put(entry.getKey(), entry.getValue())
        );

        numberOfOps.getWindowAverages().forEach(entry -> {
            double keySize = averageKeySize.get(entry.getKey()) / entry.getValue();
            if (!Double.isFinite(keySize)) {
                keySize = 0.0;
            }
            averageKeySize.put(entry.getKey(), keySize);
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
        finalKeySizes = keySizeCounter.getWindowAverages().stream().mapToLong(value -> value.getValue().longValue()).sum();
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

        String perWindowKeys = String.format("%d %f", finalKeySizes, perWindowKeySizes);

        return super.getTotalsAsString() + perWindowKeys + NEW_LINE + keysPerServer.toString() + NEW_LINE;
    }
}