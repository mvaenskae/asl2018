package ch.ethz.asltest.Utilities.WorkUnit;

import java.nio.channels.SocketChannel;

public final class WorkUnitClientError extends WorkUnit {
    /**
     * CLIENT_ERROR: Error in the input line of the command.
     * <p></p>
     * CLIENT_ERROR <error>\r\n
     * <p></p>
     * <error> Human-readable error string
     */

    public final byte[] header;

    public WorkUnitClientError(SocketChannel originalSocket, byte[] header)
    {
        super(originalSocket);
        this.type = WorkUnitType.CLIENT_ERROR;
        this.header = header;
        this.readyForUsage = true;
    }

    @Override
    public byte[] getHeader()
    {
        return this.header;
    }

    @Override
    public byte[] getBody()
    {
        return null;
    }
}
