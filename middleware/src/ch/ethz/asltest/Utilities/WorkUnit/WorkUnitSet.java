package ch.ethz.asltest.Utilities.WorkUnit;

import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

public class WorkUnitSet extends WorkUnit {
    /**
     * SET: SET the value of the specific key.
     *
     * set <key> <flags> <exptime> <bytes> [noreply]\r\n
     * <data block>\r\n
     *
     * <key> Maximum size of 250 Bytes
     * <flags> Either 16 or 32 bits (use 16 here for legacy)
     * <exptime> Maximum of 60*60*24*30 (30 days; else UNIX timestamp)
     * <bytes> Gives length of payload EXCLUDING \r\n (so 2 more Bytes here!)
     * noreply Commands the server to not send a reply (IGNORE THIS FOR ASL!)
     */

    public final byte[] key;
    public final byte[] flags;
    public final long exptime;
    public final int bytes;

    public final byte[] header;
    public byte[] body;

    public final List<Integer> whitespaces;

    public WorkUnitSet(SocketChannel client, byte[] header, ArrayList<byte[]> contents, ArrayList<Integer> whitespace) {
        super(client);
        this.type = WorkUnitType.SET;
        this.header = header;
        this.key = contents.get(0);
        this.flags = contents.get(1);
        this.exptime = Long.parseLong(new String(contents.get(2)));
        this.bytes = Integer.parseInt(new String(contents.get(3)));
        this.whitespaces = whitespace;
    }

    public void setBody(byte[] body) {
        this.body = body;
        this.readyForUsage = true;
    }
}
