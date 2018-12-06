package ch.ethz.asltest.Utilities.Statistics.Element;

import ch.ethz.asltest.Utilities.Misc.Tuple;
import ch.ethz.asltest.Utilities.Packets.WorkUnit.WorkUnit;
import ch.ethz.asltest.Utilities.Statistics.Containers.AverageIntegerStatistics;
import ch.ethz.asltest.Utilities.Statistics.Containers.CounterStatistics;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public abstract class WorkerElement extends StatisticsElement {

    protected final static int BUCKET_COUNT = 500; // 50ms histogram
    protected final int NANOS_TO_BUCKET = 100_000; // 100µs resolution

    // Keep track of the following averages:
    protected final CounterStatistics numberOfOps;
    protected final AverageIntegerStatistics averageWaitingTimeQueue;
    protected final AverageIntegerStatistics averageServiceTimeMemcached;
    protected final AverageIntegerStatistics averageRTT;

    protected double totalOpsEncountered;

    // Final variables only to be computed for the summary of this element.
    long finalOpCount;
    double finalAverageWaitingTimeQueue;
    double finalAverageServiceTimeMemcached;
    double finalAverageRTT;

    // Each bucket is in the range of [index - 1 * 100µs, index * 100µs) for index > 1.
    protected final long[] histogram = new long[BUCKET_COUNT];

    public WorkerElement() {
        numberOfOps = new CounterStatistics();

        averageWaitingTimeQueue = new AverageIntegerStatistics();
        averageServiceTimeMemcached = new AverageIntegerStatistics();
        averageRTT = new AverageIntegerStatistics();
    }

    public WorkerElement(boolean placeholder)
    {
        numberOfOps = new CounterStatistics(placeholder);

        averageWaitingTimeQueue = new AverageIntegerStatistics(placeholder);
        averageServiceTimeMemcached = new AverageIntegerStatistics(placeholder);
        averageRTT = new AverageIntegerStatistics(placeholder);
    }

    public final void addAverageServiceTimeMemcached(HashMap<SelectionKey, Tuple<Long, Long>> serverTimes)
    {
        // find maximum response time from the HasHMap
        Tuple<Long, Long> slowestResponse =
                serverTimes.entrySet().stream()
                                      .max((entry1, entry2) -> (int) (entry1.getValue().second - entry2.getValue().second))
                                      .map(Map.Entry::getValue)
                                      .orElseThrow(RuntimeException::new);
        averageServiceTimeMemcached.addElement(slowestResponse.first, slowestResponse.second);
    }

    public final void addAverageWaitingTimeQueue(WorkUnit unit)
    {
        long nanosOnQueue = unit.timestamp.getPopFromQueue() - unit.timestamp.getPushOnQueue();
        averageWaitingTimeQueue.addElement(unit.timestamp.getPopFromQueue(), nanosOnQueue);
    }

    public final void addAverageRTT(WorkUnit unit)
    {
        long nanosRTT = unit.timestamp.getReplyOnSocket() - unit.timestamp.getArrivedOnSocket();
        averageRTT.addElement(unit.timestamp.getReplyOnSocket(), nanosRTT);
        putIntoHistogram(nanosRTT);
    }

    public final void incrementOpCounter(long timestamp)
    {
        numberOfOps.addElement(timestamp, 1L);
        this.totalOpsEncountered += 1L;
    }

    public void merge(WorkerElement other)
    {
        this.numberOfOps.addOther(other.numberOfOps);
        this.averageWaitingTimeQueue.averageWithOther(other.averageWaitingTimeQueue, this.totalOpsEncountered, other.totalOpsEncountered);
        this.averageServiceTimeMemcached.averageWithOther(other.averageServiceTimeMemcached, this.totalOpsEncountered, other.totalOpsEncountered);
        this.averageRTT.averageWithOther(other.averageRTT, this.totalOpsEncountered, other.totalOpsEncountered);

        for (int i = 0; i < histogram.length; ++i) {
            histogram[i] += other.histogram[i];
        }
    }

    public void disableStatistics()
    {
        numberOfOps.stopStatistics();
        averageWaitingTimeQueue.stopStatistics();
        averageServiceTimeMemcached.stopStatistics();
        averageRTT.stopStatistics();
    }

    protected void putIntoHistogram(long inNanos)
    {
        long bucket = inNanos / NANOS_TO_BUCKET;
        bucket = (bucket > (histogram.length - 1)) ? histogram.length - 1 : bucket;
        histogram[(int) bucket]++;
    }

    public void printWindowStatistics(Path basedirectoryPath, String prefix, boolean useSTDOUT) throws IOException
    {
        Path filename = Paths.get(basedirectoryPath.toString(), prefix+"opCount.txt");
        numberOfOps.printStatistics(filename, useSTDOUT);

        filename = Paths.get(basedirectoryPath.toString(), prefix+"averageWaitingTimeQueue.txt");
        averageWaitingTimeQueue.printStatistics(filename, useSTDOUT);

        filename = Paths.get(basedirectoryPath.toString(), prefix+"averageServiceTimeMemcached.txt");
        averageServiceTimeMemcached.printStatistics(filename, useSTDOUT);

        filename = Paths.get(basedirectoryPath.toString(), prefix+"averageRTT.txt");
        averageRTT.printStatistics(filename, useSTDOUT);

        filename = Paths.get(basedirectoryPath.toString(), prefix+"histogram.txt");
        String temp = IntStream.range(0, histogram.length)
                .mapToObj(bucket -> String.format("%f %d%s", ((double) bucket) / 10, histogram[bucket], NEW_LINE)).collect(Collectors.joining());
        if (useSTDOUT) {
            System.out.println(temp);
        } else {
            byte[] tempBytes = temp.getBytes();
            Files.write(filename, tempBytes);
        }
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

        return csvLayout;
    }

    public void printCsv(Path basedirectoryPath, String prefix, boolean useSTDOUT) throws IOException
    {
        ArrayList<Map.Entry<Double, ArrayList<Double>>> sortedList = new ArrayList<>(this.getCsv().entrySet());
        sortedList.sort(Comparator.comparing(Map.Entry::getKey));

        Path filename = Paths.get(basedirectoryPath.toString(), prefix+"table.csv");

        StringBuilder csvBuilder = new StringBuilder();
        ArrayList<Double> line;
        boolean header = true;
        for (Map.Entry<Double, ArrayList<Double>> doubleArrayListEntry : sortedList) {
            StringBuilder lineBuilder = new StringBuilder();
            line = doubleArrayListEntry.getValue();
            if (header) {
                header = false;
                csvBuilder.append("Window, QueryCount[op/win], QueueWaitingTime[ns], MemcachedWaitingTime[ns], TimeInMiddleware[ns]");
                if (line.size() > 5) {
                    csvBuilder.append(", MissCounts[key/win]");
                    if (line.size() > 6) {
                        csvBuilder.append(", AverageKeySize[key], KeyCount[key/win]");
                    }
                }
                csvBuilder.append(NEW_LINE);
            }
            csvBuilder.append(doubleArrayListEntry.getKey()).append(", ");
            lineBuilder.append(line.get(0));
            for (int j = 1; j < line.size(); ++j) {
                lineBuilder.append(", ").append(String.format("%f", line.get(j)));
            }
            csvBuilder.append(lineBuilder.toString()).append(NEW_LINE);
        }

        if (useSTDOUT) {
            System.out.println(csvBuilder.toString());
        } else {
            byte[] csvBytes = csvBuilder.toString().getBytes();
            Path csvStatisticsFile = Files.createFile(filename);
            Files.write(csvStatisticsFile, csvBytes);
        }
    }

    void getSummary()
    {
        finalOpCount = numberOfOps.getWindowAverages().stream().mapToLong(value -> value.getValue().longValue()).sum();
        finalAverageWaitingTimeQueue = averageWaitingTimeQueue.getWindowAverages().stream().mapToDouble(Map.Entry::getValue).summaryStatistics().getAverage();
        DoubleSummaryStatistics temp = averageWaitingTimeQueue.getWindowAverages().stream().mapToDouble(Map.Entry::getValue).summaryStatistics();
        temp.getAverage();
        finalAverageServiceTimeMemcached = averageServiceTimeMemcached.getWindowAverages().stream().mapToDouble(Map.Entry::getValue).summaryStatistics().getAverage();
        finalAverageRTT = averageRTT.getWindowAverages().stream().mapToDouble(Map.Entry::getValue).summaryStatistics().getAverage();
    }

    protected String getTotalsAsString()
    {
        double perWindowOpCount = ((double) finalOpCount) / numberOfOps.getWindowAverages().size();
        if (!Double.isFinite(perWindowOpCount)) {
            perWindowOpCount = 0.0;
        }
        return String.format("%d %f%s%f%s%f%s%f%s", finalOpCount, perWindowOpCount, NEW_LINE,
                finalAverageWaitingTimeQueue, NEW_LINE,
                finalAverageServiceTimeMemcached, NEW_LINE,
                finalAverageRTT, NEW_LINE);
    }

    public void printSummary(Path basedirectoryPath, String prefix, boolean useSTDOUT) throws IOException
    {
        getSummary();
        String totals = getTotalsAsString();

        Path filename = Paths.get(basedirectoryPath.toString(), prefix+"summary.txt");
        if (useSTDOUT) {
            System.out.println(totals);
        } else {
            byte[] totalsBytes = totals.getBytes();
            Files.write(filename, totalsBytes);
        }
    }
}