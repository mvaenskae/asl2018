package ch.ethz.asltest.Utilities.Statistics.Containers;

import ch.ethz.asltest.Utilities.Statistics.MiddlewareStatistics;
import ch.ethz.asltest.Utilities.Statistics.Element.WorkerElement;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class WorkerStatistics extends MiddlewareStatistics {

    /*
     * This class manages local statistics for each worker. It tracks the waiting time for each element popped from the
     * queue (finalized it), a count of elements received from the queue and the service time for each memcached server.
     */

    private final CountIntegerStatistics invalidPacketCounter;
    private final CountIntegerStatistics memcachedMisses;

    public final WorkerElement setElement;
    public final WorkerElement getElement;
    public final WorkerElement multiGetElement;

    public WorkerStatistics() {
        invalidPacketCounter = new CountIntegerStatistics();
        memcachedMisses = new CountIntegerStatistics();

        setElement = new WorkerElement();
        getElement = new WorkerElement();
        multiGetElement = new WorkerElement();
    }

    public WorkerStatistics(boolean placeholder)
    {
        invalidPacketCounter = new CountIntegerStatistics(placeholder);
        memcachedMisses = new CountIntegerStatistics(placeholder);

        setElement = new WorkerElement(placeholder);
        getElement = new WorkerElement(placeholder);
        multiGetElement = new WorkerElement(placeholder);
    }

    public void cacheMiss(long timestamp, int missCount)
    {
        this.memcachedMisses.addElement(timestamp, missCount);
    }

    public void invalidPacketCounter(long timestamp)
    {
        this.invalidPacketCounter.addElement(timestamp, 1L);
    }

    public void addOther(WorkerStatistics other)
    {
        invalidPacketCounter.addOther(other.invalidPacketCounter);
        memcachedMisses.addOther(other.memcachedMisses);
        setElement.addOther(other.setElement);
        getElement.addOther(other.getElement);
        multiGetElement.addOther(other.multiGetElement);
    }

    public void addOtherWeighted(WorkerStatistics other, int normalizer)
    {
        invalidPacketCounter.addOther(other.invalidPacketCounter);
        memcachedMisses.addOther(other.memcachedMisses);
        setElement.addOtherWeighted(other.setElement, normalizer);
        getElement.addOtherWeighted(other.getElement, normalizer);
        multiGetElement.addOtherWeighted(other.multiGetElement, normalizer);
    }

    public void stopStatistics()
    {
        invalidPacketCounter.stopStatistics();
        memcachedMisses.stopStatistics();
        setElement.disableStatistics();
        getElement.disableStatistics();
        multiGetElement.disableStatistics();
    }

    public void printAverageStatistics(Path basedirectoryPath, int normalizer, boolean useSTDOUT) throws IOException
    {
        String prefix = "merged_";
        Path filename = Paths.get(basedirectoryPath.toString(), prefix+"invalidPackets.txt");
        invalidPacketCounter.printStatistics(filename, useSTDOUT);
        filename = Paths.get(basedirectoryPath.toString(), prefix+"missCount.txt");
        memcachedMisses.printStatistics(filename, useSTDOUT);

        setElement.printStatistics(basedirectoryPath, "set_", useSTDOUT);
        getElement.printStatistics(basedirectoryPath, "get_", useSTDOUT);
        multiGetElement.printStatistics(basedirectoryPath, "multiget_", useSTDOUT);

        WorkerElement mergedElement = new WorkerElement(true);
        mergedElement.addOther(setElement);
        mergedElement.addOther(getElement);
        mergedElement.addOther(multiGetElement);

        mergedElement.printStatistics(basedirectoryPath, prefix, useSTDOUT);
    }
}
