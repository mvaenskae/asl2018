package ch.ethz.asltest.Utilities.WorkUnit;

import java.nio.channels.SocketChannel;

import ch.ethz.asltest.Utilities.Timestamps;

public class WorkUnit {

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