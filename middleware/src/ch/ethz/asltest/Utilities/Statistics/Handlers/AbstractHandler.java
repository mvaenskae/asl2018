package ch.ethz.asltest.Utilities.Statistics.Handlers;

public abstract class AbstractHandler {
    volatile boolean enabled = false;

    public void enable()
    {
        this.enabled = true;
    }

    public void disable()
    {
        this.enabled = false;
    }
}
