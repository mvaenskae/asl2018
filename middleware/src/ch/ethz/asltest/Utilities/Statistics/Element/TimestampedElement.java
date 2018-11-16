package ch.ethz.asltest.Utilities.Statistics.Element;

import java.util.Map;

public final class TimestampedElement implements Map.Entry<Double, Double> {

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

    public void addOther(TimestampedElement other)
    {
        this.element += other.element;
    }

    @Override
    public String toString()
    {
        return String.format("%.2f %.4f", timestamp, element);
    }

    public void addWeightedOther(TimestampedElement other, int weighting)
    {
        this.element += (other.element / weighting);
    }

    public TimestampedElement newWeighted(int weighting)
    {
        return new TimestampedElement(timestamp, element/weighting);
    }

    @Override
    public Double getKey()
    {
        return getTimestamp();
    }

    @Override
    public Double getValue()
    {
        return getElement();
    }

    @Override
    public Double setValue(Double value)
    {
        return this.element = value;
    }
}