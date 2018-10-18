package ch.ethz.asltest.Utilities.WorkUnit;

import ch.ethz.asltest.Utilities.Timestamps;

import java.nio.channels.SocketChannel;

public abstract class WorkUnit {

    public final Timestamps timestamp;
    public final SocketChannel sendBackTo;

    public volatile boolean readyForUsage;

    /**
     * The following fields give quick access to respective data for the programmer
     */
    public WorkUnitType type;

    public WorkUnit(SocketChannel originalSocket)
    {
        // TODO: Fix this timestamp here, it's semanticaly incorrect
        this.timestamp = new Timestamps(0);
        this.sendBackTo = originalSocket;
    }
}