package ch.ethz.asltest.Utilities.Statistics.Element;

public final class AccumulationLongElement extends StatisticsElement {

    private long timestamp;
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
        this(timestamp, 1, element);
    }

    public void addElement(long element){
        this.elementCount++;
        this.element += element;
    }

    public long getAccumulated()
    {
        return this.element;
    }

    public double getAverage()
    {
        return ((double) this.element)/this.elementCount;
    }

    public void reuseElement(long timestamp, long element)
    {
        this.timestamp = timestamp;
        this.elementCount = 1;
        this.element = element;
    }

    @Override
    public String toString()
    {
        return timestamp + " " + element;
    }
}