package ch.ethz.asltest.Utilities.WorkUnit;

import java.nio.channels.SocketChannel;

public final class WorkUnitError extends WorkUnit {
    /**
     * ERROR: Means the client sent a nonexistent command name.
     * <p></p>
     * ERROR\r\n
     */

    public final byte[] header;

    public WorkUnitError(SocketChannel originalSocket, byte[] header)
    {
        super(originalSocket);
        this.type = WorkUnitType.ERROR;
        this.header = header;
        this.readyForUsage = true;
    }
}
