package ch.ethz.asltest.Utilities;

import java.net.InetAddress;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;

import ch.ethz.asltest.Utilities.Timestamps

public class WorkUnit {

    public InetAddress source;
    public int sourcePort;

    public Timestamps timestamp;
    public SocketChannel sendBackTo;

    /**
     * The following fields give quick access to respective data for the programmer
     */
    public int command;
    public ArrayList<byte[]> keys;
    public byte[] flags;
    public long exptime;
    public int bodySize;

    public byte[] header;
    public byte[] body;

    public int offsetCount;
    public int whitespaceCount;
    public int[] fieldOffsets;
    public int[] whitespaces;
}
