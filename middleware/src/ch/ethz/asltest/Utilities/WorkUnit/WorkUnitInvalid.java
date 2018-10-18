package ch.ethz.asltest.Utilities.WorkUnit;

import java.nio.channels.SocketChannel;

public final class WorkUnitInvalid extends WorkUnit {

    public WorkUnitInvalid(SocketChannel originalSocket)
    {
        super(originalSocket);
        this.type = WorkUnitType.INVALID;
        this.readyForUsage = true;
    }
}
