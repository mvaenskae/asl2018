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

    public long getElementCount()
    {
        return this.elementCount;
    }

    public long getAccumulated()
    {
        return this.element;
    }

    public double getAverage()
    {
        double res = ((double) this.element)/this.elementCount;
        if (!Double.isNaN(res)) {
            return res;
        } else {
            return 0.0;
        }
    }

    public void reuseElement(long timestamp, long element)
    {
        reuseElement(timestamp, 0, element);
    }

    public void reuseElement(long timestamp, long elementCount, long element)
    {
        this.timestamp = timestamp;
        this.elementCount = elementCount;
        this.element = element;
    }

    @Override
    public String toString()
    {
        return String.format("%d %d", timestamp, element);
    }
}