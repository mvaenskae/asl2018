package ch.ethz.asltest.Utilities.WorkUnit;

import java.nio.channels.SocketChannel;

public final class WorkUnitServerError extends WorkUnit {
    /**
     * SERVER_ERROR: Error on the side of the server. Fatal errors will result in disconnects carried out by the server!
     * <p></p>
     * SERVER_ERROR <error>\r\n
     * <p></p>
     * <error> Human-readable error string
     */

    public final byte[] header;

    public WorkUnitServerError(SocketChannel originalSocket, byte[] header)
    {
        super(originalSocket);
        this.type = WorkUnitType.SERVER_ERROR;
        this.header = header;
        this.readyForUsage = true;
    }
}
