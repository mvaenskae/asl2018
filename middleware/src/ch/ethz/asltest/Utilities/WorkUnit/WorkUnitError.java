package ch.ethz.asltest.Utilities.WorkUnit;

import java.nio.channels.SocketChannel;

public final class WorkUnitError extends WorkUnit {
    /**
     * ERROR: Means the client sent a nonexistent command name.
     * <p></p>
     * ERROR\r\n
     */

    public final static byte[] header = "ERROR\r\n".getBytes();

    public WorkUnitError(SocketChannel originalSocket)
    {
        super(originalSocket);
        this.type = WorkUnitType.ERROR;
        this.readyForUsage = true;
    }

    @Override
    public byte[] getHeader()
    {
        return WorkUnitError.header;
    }
}
