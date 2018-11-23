package ch.ethz.asltest.Utilities.Statistics.Containers;

import ch.ethz.asltest.Utilities.Statistics.Element.WorkerGetElement;
import ch.ethz.asltest.Utilities.Statistics.Element.WorkerMultiGetElement;
import ch.ethz.asltest.Utilities.Statistics.Element.WorkerSetElement;
import ch.ethz.asltest.Utilities.Statistics.MiddlewareStatistics;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class WorkerStatistics extends MiddlewareStatistics {

    /*
     * This class manages local statistics for each worker. It tracks the waiting time for each element popped from the
     * queue (finalized it), a count of elements received from the queue and the service time for each memcached server.
     */

    private final CounterStatistics invalidPacketCounter;

    public final WorkerSetElement setElement;
    public final WorkerGetElement getElement;
    public final WorkerMultiGetElement multiGetElement;

    public WorkerStatistics() {
        invalidPacketCounter = new CounterStatistics();

        setElement = new WorkerSetElement();
        getElement = new WorkerGetElement();
        multiGetElement = new WorkerMultiGetElement();
    }

    public WorkerStatistics(boolean placeholder)
    {
        invalidPacketCounter = new CounterStatistics(placeholder);

        setElement = new WorkerSetElement(placeholder);
        getElement = new WorkerGetElement(placeholder);
        multiGetElement = new WorkerMultiGetElement(placeholder);
    }

    public void invalidPacketCounter(long timestamp)
    {
        this.invalidPacketCounter.addElement(timestamp, 1L);
    }

    public void addOtherWeighted(WorkerStatistics other, int normalizer)
    {
        invalidPacketCounter.addOther(other.invalidPacketCounter);
        setElement.addOtherWeighted(other.setElement, normalizer);
        getElement.addOtherWeighted(other.getElement, normalizer);
        multiGetElement.addOtherWeighted(other.multiGetElement, normalizer);
    }

    public void stopStatistics()
    {
        invalidPacketCounter.stopStatistics();
        setElement.disableStatistics();
        getElement.disableStatistics();
        multiGetElement.disableStatistics();
    }

    public void printAverageStatistics(Path basedirectoryPath, boolean useSTDOUT) throws IOException
    {
        String prefix = "merged_";
        Path filename = Paths.get(basedirectoryPath.toString(), prefix+"invalidPackets.txt");
        invalidPacketCounter.printStatistics(filename, useSTDOUT);

        setElement.printWindowStatistics(basedirectoryPath, "set_", useSTDOUT);
        setElement.printCsv(basedirectoryPath, "set_", useSTDOUT);
        setElement.printSummary(basedirectoryPath, "set_", useSTDOUT);

        getElement.printWindowStatistics(basedirectoryPath, "get_", useSTDOUT);
        getElement.printCsv(basedirectoryPath, "get_", useSTDOUT);
        getElement.printSummary(basedirectoryPath, "get_", useSTDOUT);

        multiGetElement.printWindowStatistics(basedirectoryPath, "multiget_", useSTDOUT);
        multiGetElement.printCsv(basedirectoryPath, "multiget_", useSTDOUT);
        multiGetElement.printSummary(basedirectoryPath, "multiget_", useSTDOUT);

        WorkerGetElement mergedElement = new WorkerGetElement(true);
        WorkerMultiGetElement totalElement = new WorkerMultiGetElement(true);

        mergedElement.merge(setElement);
        mergedElement.merge(getElement);
        totalElement.merge(multiGetElement);
        totalElement.merge(mergedElement);

        totalElement.printWindowStatistics(basedirectoryPath, prefix, useSTDOUT);
        totalElement.printCsv(basedirectoryPath, prefix, useSTDOUT);
        totalElement.printSummary(basedirectoryPath, prefix, useSTDOUT);
    }
}
