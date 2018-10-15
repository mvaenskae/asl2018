package ch.ethz.asltest.Utilities.WorkUnit;

import java.nio.channels.SocketChannel;

public class WorkUnitError extends WorkUnit {
    /**
     * ERROR: Means the client sent a nonexistent command name.
     *
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
