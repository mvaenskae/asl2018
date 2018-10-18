package ch.ethz.asltest.Utilities.WorkUnit;

import java.nio.channels.SocketChannel;

public final class WorkUnitEnd extends WorkUnit {
    /**
     * END: Expected final reply to a get(s) command.
     * <p></p>
     * END\r\n
     */

    public final byte[] header;

    public WorkUnitEnd(SocketChannel originalSocket, byte[] header)
    {
        super(originalSocket);
        this.type = WorkUnitType.END;
        this.header = header;
        this.readyForUsage = true;
    }
}
