package ch.ethz.asltest.Utilities.Statistics.StatisticsElement;

public final class AccumulationLongElement extends StatisticsElement {

    private final long timestamp;
    private long elementCount;
    private long element;

    public AccumulationLongElement(long timestamp, int elementCount, long element)
    {
        this.timestamp = timestamp;
        this.elementCount = elementCount;
        this.element = element;
    }

    public AccumulationLongElement(long timestamp, long element)
    {
        this.timestamp = timestamp;
        this.elementCount = 1;
        this.element = element;
    }

    public AccumulationLongElement(long element)
    {
        this(System.nanoTime(), element);
    }

    public void addElement(long element){
        this.elementCount++;
        this.element += element;
    }

    public double getAverage()
    {
        return ((double) this.element) / this.elementCount;
    }

    @Override
    public String toString()
    {
        return timestamp + " " + element;
    }
}