package ch.ethz.asltest.Utilities.WorkUnit;

import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

public final class WorkUnitGet extends WorkUnit {
    /**
     * GET: GET the value of the specific key(s).
     * <p></p>
     * get <key>{1,10}\r\n
     * gets <key>{1,10}\r\n
     * <p></p>
     * Delimited by whitespace
     */

    public final List<byte[]> keys;

    public final byte[] header;

    public final List<Integer> whitespaces;

    public WorkUnitGet(SocketChannel originalSocket, byte[] header, ArrayList<byte[]> keys, List<Integer> whitespaces)
    {
        super(originalSocket);
        this.type = WorkUnitType.GET;
        this.header = header;
        this.keys = keys;
        this.whitespaces = whitespaces;
        this.readyForUsage = true;
    }

    @Override
    public byte[] getHeader()
    {
        return this.header;
    }
}
