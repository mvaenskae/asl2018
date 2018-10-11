package ch.ethz.asltest.Utilities;

import java.util.Arrays;

public class PacketParser {
    /**
     * This class holds helper functions to correctly parse an incoming packet from memtier, designated for memcached.
     * It is able to interpret packets of operations GET, GETS and SET with arbitrary size keys.
     */

    /**
     * SET command:
     * set <key> <flags> <exptime> <bytes> [noreply]\r\n
     * <key> Maximum size of 250 Bytes
     * <flags> Either 16 or 32 bits (use 16 here for legacy)
     * <exptime> Maximum of 60*60*24*30 (30 days; else UNIX timestamp)
     * <bytes> Gives length of payload EXCLUDING \r\n (so 2 more Bytes here!)
     */

    /**
     * GET command:
     * get <key>{1,10}\r\n1
     * gets <key>{1,10}\r\n
     * Delimited by whitespace
     */

    private byte[] getHeader = {'g', 'e', 't'};
    private byte[] setHeader = {'s', 'e', 't'};

    public WorkUnit startParsing(byte[] buffer)
    {
        byte[] header = Arrays.copyOf(buffer, 3);
        if (header.equals(setHeader)) {
            if (header[3] == ' ') {
                // Have SET command
                // TODO: Parse this with SET semantics
            } else {
                // TODO: Log invalid header, put non-valid WorkUnit on queue
            }
        } else if (header.equals(getHeader)) {
            if (buffer[3] == 's') {
                // Have MULTIGET header
                // TODO: Parse this with GETS semantics
            } else if (buffer[3] == ' ') {
                // Have GET header
                // TODO: Parse this with GET semantics
            } else {
                // TODO: Log invalid header, put non-valid WorkUnit on queue
            }
        } else {
            // TODO: Log invalid header, put non-valid WorkUnit on queue
        }
    }
}
