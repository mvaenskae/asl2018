package ch.ethz.asltest.Utilities.Statistics.Element;

import ch.ethz.asltest.Utilities.Misc.Tuple;
import ch.ethz.asltest.Utilities.Packets.WorkUnit.WorkUnit;
import ch.ethz.asltest.Utilities.Statistics.Containers.AverageIntegerStatistics;
import ch.ethz.asltest.Utilities.Statistics.Containers.CountIntegerStatistics;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class WorkerElement extends StatisticsElement {

    private final static int RESOLUTION_PER_SECOND = 10_000; // 100µs resolution
    private final int NANOS_TO_BUCKET = 100_000; // 100µs resolution

    // Keep track of the following averages:
    private final CountIntegerStatistics numberOfOps;
    private final AverageIntegerStatistics averageWaitingTimeQueue;
    private final AverageIntegerStatistics averageServiceTimeMemcached;
    private final AverageIntegerStatistics averageRTT;

    // Each bucket is in the range of [index - 1 * 100µs, index * 100µs) for index > 1.
    private final long[] histogram = new long[RESOLUTION_PER_SECOND];

    public WorkerElement() {
        numberOfOps = new CountIntegerStatistics();
        averageWaitingTimeQueue = new AverageIntegerStatistics();
        averageServiceTimeMemcached = new AverageIntegerStatistics();
        averageRTT = new AverageIntegerStatistics();
    }

    public WorkerElement(boolean placeholder)
    {
        numberOfOps = new CountIntegerStatistics(placeholder);
        averageWaitingTimeQueue = new AverageIntegerStatistics(placeholder);
        averageServiceTimeMemcached = new AverageIntegerStatistics(placeholder);
        averageRTT = new AverageIntegerStatistics(placeholder);
    }

    public void addAverageServiceTimeMemcached(HashMap<SelectionKey, Tuple<Long, Long>> serverTimes)
    {
        // find maximum response time from the HasHMap
        Tuple<Long, Long> slowestResponse =
                serverTimes.entrySet().stream()
                                      .max((entry1, entry2) -> (int) (entry1.getValue().second - entry2.getValue().second))
                                      .map(Map.Entry::getValue)
                                      .orElseThrow(RuntimeException::new);
        averageServiceTimeMemcached.addElement(slowestResponse.first, slowestResponse.second);
    }

    public void addAverageWaitingTimeQueue(WorkUnit unit)
    {
        long nanosOnQueue = unit.timestamp.getPopFromQueue() - unit.timestamp.getPushOnQueue();
        averageWaitingTimeQueue.addElement(unit.timestamp.getPopFromQueue(), nanosOnQueue);
    }

    public void addAverageRTT(WorkUnit unit)
    {
        long nanosRTT = unit.timestamp.getReplyOnSocket() - unit.timestamp.getArrivedOnSocket();
        averageRTT.addElement(unit.timestamp.getReplyOnSocket(), nanosRTT);
        putIntoHistogram(nanosRTT);
    }

    public void incrementOpCounter(long timestamp)
    {
        numberOfOps.addElement(timestamp, 1L);
    }

    public void addOther(WorkerElement other)
    {
        this.numberOfOps.addOther(other.numberOfOps);
        this.averageWaitingTimeQueue.addOther(other.averageWaitingTimeQueue);
        this.averageServiceTimeMemcached.addOther(other.averageServiceTimeMemcached);
        this.averageRTT.addOther(other.averageRTT);

        for (int i = 0; i < histogram.length; ++i) {
            histogram[i] += other.histogram[i];
        }
    }

    public void addOtherWeighted(WorkerElement other, int weighting)
    {
        this.numberOfOps.addOther(other.numberOfOps);
        this.averageWaitingTimeQueue.addOtherWeighted(other.averageWaitingTimeQueue, weighting);
        this.averageServiceTimeMemcached.addOtherWeighted(other.averageServiceTimeMemcached, weighting);
        this.averageRTT.addOtherWeighted(other.averageRTT, weighting);

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

    @Override
    public String toString()
    {
        return super.toString();
    }

    private void putIntoHistogram(long inNanos)
    {
        long bucket = inNanos / NANOS_TO_BUCKET;
        bucket = (bucket > histogram.length) ? histogram.length : bucket;
        histogram[(int) bucket]++;
    }

    public void printStatistics(Path basedirectoryPath, String prefix, boolean useSTDOUT) throws IOException
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
                .mapToObj(bucket -> String.format("%f=%d\n", ((double) bucket) / 10, histogram[bucket])).collect(Collectors.joining());
        if (useSTDOUT) {
            System.out.println(temp);
        } else {
            byte[] tempBytes = temp.getBytes();
            Files.write(filename, tempBytes);
        }
    }
}