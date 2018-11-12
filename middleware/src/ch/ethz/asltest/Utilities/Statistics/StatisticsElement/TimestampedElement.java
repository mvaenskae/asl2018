package ch.ethz.asltest.Utilities.Statistics.StatisticsElement;

public final class TimestampedElement<T extends Number> extends StatisticsElement {

    private final long timestamp;
    private final T element;

    public TimestampedElement(long timestamp, T element)
    {
        this.timestamp = timestamp;
        this.element = element;
    }

    public TimestampedElement(T element)
    {
        this(System.nanoTime(), element);
    }

    public long getTimestamp()
    {
        return this.timestamp;
    }

    @Override
    public String toString()
    {
        return timestamp + " " + element;
    }
}