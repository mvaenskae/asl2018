package ch.ethz.asltest.Utilities.WorkUnit;

import java.nio.channels.SocketChannel;

public final class WorkUnitEnd extends WorkUnit {
    /**
     * END: Expected final reply to a get(s) command.
     * <p></p>
     * END\r\n
     */

    public static final byte[] header = "END\r\n".getBytes();

    public WorkUnitEnd(SocketChannel originalSocket)
    {
        super(originalSocket);
        this.type = WorkUnitType.END;
        this.readyForUsage = true;
    }

    @Override
    public byte[] getHeader()
    {
        return WorkUnitEnd.header;
    }

    @Override
    public byte[] getBody()
    {
        return null;
    }
}
