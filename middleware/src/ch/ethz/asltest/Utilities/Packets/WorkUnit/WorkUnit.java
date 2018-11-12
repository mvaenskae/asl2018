package ch.ethz.asltest.Utilities.Packets.WorkUnit;

import ch.ethz.asltest.Utilities.Packets.Timestamps;

import java.nio.channels.SocketChannel;

public abstract class WorkUnit {

    public final Timestamps timestamp;
    public final SocketChannel sendBackTo;

    volatile boolean readyForUsage;

    /**
     * The following fields give quick access to respective data for the programmer
     */
    public WorkUnitType type;

    WorkUnit(SocketChannel originalSocket)
    {
        this.sendBackTo = originalSocket;
        this.timestamp = new Timestamps(0);
    }

    WorkUnit(SocketChannel originalSocket, long arrivedOnSocket)
    {
        this.sendBackTo = originalSocket;
        this.timestamp = new Timestamps(arrivedOnSocket);
    }

    public abstract byte[] getHeader();

    public abstract byte[] getBody();

    public WorkUnitType getType()
    {
        return this.type;
    }

    public boolean hasBody()
    {
        return (this.type == WorkUnitType.SET || this.type == WorkUnitType.VALUE);
    }
}