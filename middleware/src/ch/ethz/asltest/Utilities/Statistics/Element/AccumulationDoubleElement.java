package ch.ethz.asltest.Utilities.Statistics.Element;

public final class AccumulationDoubleElement extends StatisticsElement {

    private long timestamp;
    private long elementCount;
    private double element;

    public AccumulationDoubleElement(long timestamp, int elementCount, double element)
    {
        this.timestamp = timestamp;
        this.elementCount = elementCount;
        this.element = element;
    }

    public AccumulationDoubleElement(long timestamp, double element)
    {
        this(timestamp, 1, element);
    }

    public void addElement(double element){
        this.elementCount++;
        this.element += element;
    }

    public double getAccumulated()
    {
        return this.element;
    }

    public double getAverage()
    {
        return this.element/this.elementCount;
    }

    public void reuseElement(long timestamp, double element)
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