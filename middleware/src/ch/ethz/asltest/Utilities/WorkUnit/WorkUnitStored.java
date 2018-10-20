package ch.ethz.asltest.Utilities.WorkUnit;

import java.nio.channels.SocketChannel;

public final class WorkUnitStored extends WorkUnit {
    /**
     * STORED: Expected reply to a SET command.
     * <p></p>
     * STORED\r\n
     */

    public final static byte[] header = "STORED\r\n".getBytes();

    public WorkUnitStored(SocketChannel originalSocket)
    {
        super(originalSocket);
        this.type = WorkUnitType.STORED;
        this.readyForUsage = true;
    }

    @Override
    public byte[] getHeader()
    {
        return WorkUnitStored.header;
    }
}
