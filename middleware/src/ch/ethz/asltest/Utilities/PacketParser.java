package ch.ethz.asltest.Utilities;

import ch.ethz.asltest.Utilities.Misc.Tuple;
import ch.ethz.asltest.Utilities.WorkUnit.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PacketParser {
    /**
     * This class holds helper functions to correctly parse an incoming packet from memtier, designated for memcached.
     * It is able to interpret packets of operations GET, GETS and SET with arbitrary size keys.
     */

    private final ByteBuffer buffer = ByteBuffer.allocateDirect(8192);
    private int bufferOffset;
    private int headerOffset;
    private int bodySize;
    private int bodyBufferedSize;
    private byte[] errorString;
    private byte[] header;
    private byte[] body;

    private LineParsingState lineState = LineParsingState.READING;
    private boolean headerFound = false;
    private boolean headerParsed = false;
    private boolean hasBody = false;

    private WorkUnit workUnit;
    private SocketChannel client;

    // private static byte[] getHeader = "get".getBytes();
    private static final byte[] valueHeader = "VALUE".getBytes();
    private static final byte[] setHeader = "set".getBytes();
    // private static byte[] storedHeader = "STORED".getBytes();
    // private static byte[] errorHeader = "ERROR".getBytes();
    private static final byte[] clientErrorHeader = "CLIENT_ERROR".getBytes();
    private static final byte[] serverErrorHeader = "SERVER_ERROR".getBytes();

    public List<WorkUnit> receiveAndParse(SelectionKey key)
    {
        this.client = (SocketChannel) key.channel();

        // TODO: Parse single packets with internal method which are added here to the list...
        // This should generalize how many bytes there are still "busy" in this stream and as such we can correctly
        // read them the moment they are on the OS buffer...
        // Any new packet parsed will result in a compact() of the buffer for consistency reasons...

        int lastOffset;
        try {
            lastOffset = client.read(this.buffer);
            this.buffer.flip();
        } catch (IOException e) {
            // TODO: Log this here
            e.printStackTrace();
            return null;
        }
        this.bufferOffset += lastOffset;

        if (!headerFound) {
            // We are still receiving for the header
            // Scan here for "EOL" as per specification, store the header once it's found also in a byte[]
            for (int i = this.bufferOffset - lastOffset; i < bufferOffset; ++i) {
                switch (this.lineState) {
                    case READING:
                        if (this.buffer.get(i) == (byte) '\r') {
                            this.lineState = LineParsingState.SLASH;
                        }
                        break;
                    case SLASH:
                        if (this.buffer.get(i) == (byte) '\n') {
                            this.headerFound = true;
                            this.headerOffset = i + 1;
                            this.header = new byte[this.headerOffset];
                            this.buffer.get(this.header, 0, this.headerOffset);
                        }
                        this.lineState = LineParsingState.READING;
                        break;
                }
            }
        }

        if (!headerParsed) {
            // Header fully received, parse it
            this.parseHeader();
            this.headerParsed = true;
        }

        if (hasBody) {
            // If there is a body expected, parse it in here...
            this.setBody();
        }

        ArrayList<WorkUnit> result = new ArrayList<>();
        if (this.header != null && (!this.hasBody || this.body != null)) {
            if (workUnit != null) {
                result.add(this.workUnit);
                this.resetState();
            }
        }

        this.buffer.compact();
        return result;
    }

    /**
     * Internal method which statefully saves the body of the request.
     */
    private void setBody() {
        this.bodyBufferedSize = this.bufferOffset - this.headerOffset;
        if (this.bodyBufferedSize == this.bodySize + 2) {
            this.body = new byte[this.bodySize + 2];
            this.buffer.get(this.body, 0, this.bodySize + 2);

            if (this.workUnit.type.equals(WorkUnitType.SET)) {
                ((WorkUnitSet) this.workUnit).setBody(this.body);
            } else if (this.workUnit.type.equals(WorkUnitType.VALUE)) {
                ((WorkUnitValue) this.workUnit).setBody(this.body);
            }
        }
    }

    /**
     * Internal method which resets the state of this instance.
     */
    private void resetState()
    {
        // TODO: Complete this...
        this.buffer.compact();

        this.bufferOffset = 0;
        this.headerOffset = 0;
        this.bodyBufferedSize = 0;
        this.bodySize = 0;

        this.errorString = null;
        this.header = null;
        this.body = null;

        this.headerFound = false;
        this.headerParsed = false;
        this.hasBody = false;

        this.lineState = LineParsingState.READING;

        this.workUnit = null;
        this.client = null;
    }

    /**
     * Internal method which delegates to the correct parsing method for the first bytes in the header.
     * This method requires a helper method which adjusts the state of this instance accordingly.
     */
    private void parseHeader()
    {
        if ((this.header[0] == 'g' && this.header[1] == 'e' && this.header[2] == 't') &&
                ((this.header[3] == 's' && this.header[4] == ' ') || this.header[3] == ' ')) {
            this.parseGetHeader(this.header[3] == ' ' ? 4 : 5);
        } else if (this.header[0] == 's' && this.header[1] == 'e' && this.header[2] == 't' && this.header[3] == ' ') {
            this.parseSetHeader();
        } else if (this.header[0] == 'V' && this.header[1] == 'A' && this.header[2] == 'L' && this.header[3] == 'U' &&
                this.header[4] == 'E' && this.header[5] == ' ') {
            this.parseValueHeader();
        } else if (this.header[0] == 'S' && this.header[1] == 'T' && this.header[2] == 'O' && this.header[3] == 'R' &&
                this.header[4] == 'E' && this.header[5] == 'D' && this.header[6] == '\r' && this.header[7] == '\n') {
            this.parseStoredHeader();
        } else if (this.header[0] == 'E' && this.header[1] == 'R' && this.header[2] == 'R' && this.header[3] == 'O' &&
                this.header[4] == 'R' && this.header[5] == '\r' && this.header[6] == '\n') {
            this.parseErrorHeader();
        } else if (this.header[0] == 'C' && this.header[1] == 'L' && this.header[2] == 'I' && this.header[3] == 'E' &&
                this.header[4] == 'N' && this.header[5] == 'T' && this.header[6] == '_' && this.header[7] == 'E' &&
                this.header[8] == 'R' && this.header[9] == 'R' && this.header[10] == 'O' && this.header[11] == 'R' &&
                this.header[12] == ' ') {
            this.parseClientErrorHeader();
        } else if (this.header[0] == 'S' && this.header[1] == 'E' && this.header[2] == 'R' && this.header[3] == 'V' &&
                this.header[4] == 'E' && this.header[5] == 'R' && this.header[6] == '_' && this.header[7] == 'E' &&
                this.header[8] == 'R' && this.header[9] == 'R' && this.header[10] == 'O' && this.header[11] == 'R' &&
                this.header[12] == ' ') {
            this.parseServerErrorHeader();
        } else {
            this.parseHeaderFailure();
        }
    }

    /**
     * Helper method to indicate an invalid Memcached request was sent.
     * Per specification until EoL is supposed to be read and then retried again. As such this method does nothing as
     * the whole line has already been "read in" and this is only processing it. Also as no body is expected this method
     * will not set it as such
     */
    private void parseHeaderFailure()
    {
        this.workUnit = new WorkUnitInvalid(this.client);
    }

    /**
     * Generic header parser which is used to parse whitespace separated fields from a starting offset.
     * @param startOffset: Offset from which to start parsing the header
     */
    private Tuple<ArrayList<Integer>, ArrayList<byte[]>> parseHeaderGeneric(final int startOffset)
    {
        ArrayList<Integer> whitespaces = new ArrayList<>();
        ArrayList<byte[]> contents = new ArrayList<>();
        whitespaces.add(startOffset - 1);

        int lastSpace = startOffset;
        for (int i = startOffset; i < this.headerOffset; ++i) {
            if (this.header[i] == ' ' || i == this.headerOffset - 2) {
                if (i != this.headerOffset - 2) {
                    whitespaces.add(i);
                }
                contents.add(Arrays.copyOfRange(this.header, lastSpace, i));
                lastSpace = i + 1;
            }
        }
        return new Tuple(whitespaces, contents);
    }

    /**
     * Parses a GET(S) statefully for this instance.
     * @param startOffset Offset where the first field starts in the buffer.
     */
    private void parseGetHeader(int startOffset)
    {
        ArrayList<Integer> whitespace;
        ArrayList<byte[]> contents;
        Tuple<ArrayList<Integer>, ArrayList<byte[]>> result = this.parseHeaderGeneric(startOffset);
        whitespace = result.first;
        contents = result.second;

        this.workUnit = new WorkUnitGet(this.client, this.header, contents, whitespace);
    }

    /**
     * Parses a SET statefully for this instance.
     */
    private void parseSetHeader()
    {
        this.hasBody = true;
        ArrayList<Integer> whitespace;
        ArrayList<byte[]> contents;
        Tuple<ArrayList<Integer>, ArrayList<byte[]>> result = this.parseHeaderGeneric(PacketParser.setHeader.length + 1);
        whitespace = result.first;
        contents = result.second;

        this.workUnit = new WorkUnitSet(this.client, this.header, contents, whitespace);
        this.bodySize = ((WorkUnitSet) this.workUnit).bytes;
    }

    private void parseValueHeader()
    {
        this.hasBody = true;
        ArrayList<Integer> whitespace;
        ArrayList<byte[]> contents;
        Tuple<ArrayList<Integer>, ArrayList<byte[]>> result = this.parseHeaderGeneric(PacketParser.valueHeader.length + 1);
        whitespace = result.first;
        contents = result.second;

        this.workUnit = new WorkUnitValue(this.client, this.header, contents, whitespace);
        this.bodySize = ((WorkUnitValue) this.workUnit).bytes;
    }

    /**
     * Parses a CLIENT_ERROR statefully for this instance.
     */
    private void parseClientErrorHeader()
    {
        int errorLength = this.headerOffset - 2 - PacketParser.clientErrorHeader.length - 2; // -2 due to "\r\n"
        this.errorString = new byte[errorLength];
        this.buffer.get(this.errorString, PacketParser.clientErrorHeader.length, errorLength);

        this.workUnit = new WorkUnitClientError(this.client, header);
    }

    /**
     * Parses a SERVER_ERROR statefully for this instance.
     */
    private void parseServerErrorHeader()
    {
        int errorLength = this.headerOffset - 2 - PacketParser.serverErrorHeader.length - 2; // -2 due to "\r\n"
        this.errorString = new byte[errorLength];
        this.buffer.get(this.errorString, PacketParser.serverErrorHeader.length, errorLength);

        this.workUnit = new WorkUnitServerError(this.client, header);
    }

    /**
     * Parses a ERROR statefully for this instance
     */
    private void parseErrorHeader()
    {
        this.workUnit = new WorkUnitError(this.client, header);
    }

    /**
     * Parses a STORED statefully for this instance.
     */
    private void parseStoredHeader()
    {
        this.workUnit = new WorkUnitStored(this.client, header);
    }

    private enum LineParsingState {
        READING, SLASH
    }
}
