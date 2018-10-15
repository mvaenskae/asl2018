package ch.ethz.asltest.Utilities.WorkUnit;

import java.nio.channels.SocketChannel;

public class WorkUnitStored extends WorkUnit {
    /**
     * STORED: Expected reply to a SET command.
     *
     * STORED\r\n
     */

    public final byte[] header;

    public WorkUnitStored(SocketChannel originalSocket, byte[] header)
    {
        super(originalSocket);
        this.type = WorkUnitType.STORED;
        this.header = header;
        this.readyForUsage = true;
    }
}
