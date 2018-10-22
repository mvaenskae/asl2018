package ch.ethz.asltest.Utilities.WorkUnit;

import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

public final class WorkUnitValue extends WorkUnit {
    /**
     * VALUE: Expected reply for each <key> in GET.
     * <p></p>
     * VALUE <key> <flags> <bytes> [<cas_unique>]\r\n
     * <data block>\r\n
     * <p></p>
     * <key> Maximum size of 250 Bytes
     * <flags> Either 16 or 32 bits (use 16 here for legacy)
     * <bytes> Gives length of payload EXCLUDING \r\n (so 2 more Bytes here!)
     * <cas_unique> Unique 64-bit integer for the item
     */

    public final byte[] key;
    public final byte[] flags;
    public final int bytes;

    public final byte[] header;
    public byte[] body;

    public final List<Integer> whitespaces;

    public WorkUnitValue(SocketChannel client, byte[] header, ArrayList<byte[]> contents, ArrayList<Integer> whitespace)
    {
        super(client);
        this.type = WorkUnitType.VALUE;
        this.header = header;
        this.key = contents.get(0);
        this.flags = contents.get(1);
        this.bytes = Integer.parseInt(new String(contents.get(2)));
        this.whitespaces = whitespace;
    }

    public void setBody(byte[] body)
    {
        this.body = body;
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
        return this.body;
    }
}
