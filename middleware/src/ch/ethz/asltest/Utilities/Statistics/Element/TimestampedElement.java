package ch.ethz.asltest.Utilities.Statistics.Element;

public final class TimestampedElement extends StatisticsElement {

    private final double timestamp;
    private double element;

    public TimestampedElement(double timestamp, double element)
    {
        this.timestamp = timestamp;
        this.element = element;
    }

    public double getTimestamp()
    {
        return this.timestamp;
    }

    public double getElement()
    {
        return this.element;
    }

    public void addElement(TimestampedElement other)
    {
        this.element += other.element;
    }

    @Override
    public String toString()
    {
        return timestamp + " " + element;
    }
}