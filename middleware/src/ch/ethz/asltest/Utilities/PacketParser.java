package ch.ethz.asltest.Utilities;

import ch.ethz.asltest.Utilities.Misc.Tuple;
import ch.ethz.asltest.Utilities.WorkUnit.*;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class PacketParser {
    /*
     * This class holds helper functions to correctly parse an incoming packet from memtier, designated for memcached.
     * It is able to interpret packets of operations GET, GETS and SET with arbitrary size keys.
     */

    /*
     * Static fields which hold shared data
     */
    private static final byte[] getHeaderParsing = "get ".getBytes();
    private static final byte[] getsHeaderParsing = "gets ".getBytes();
    private static final byte[] valueHeaderParsing = "VALUE ".getBytes();
    private static final byte[] endHeaderParsing = "END".getBytes();
    private static final byte[] setHeaderParsing = "set ".getBytes();
    private static final byte[] storedHeaderParsing = "STORED\r\n".getBytes();
    private static final byte[] errorHeaderParsing = "ERROR\r\n".getBytes();
    private static final byte[] clientErrorHeaderParsing = "CLIENT_ERROR ".getBytes();
    private static final byte[] serverErrorHeaderParsing = "SERVER_ERROR ".getBytes();

    /*
     * Internal buffer with respective metadata to generate byte[] from it
     */
    private final ByteBuffer buffer = ByteBuffer.allocateDirect(8192);
    private int bufferOffset;
    private int headerOffset;
    private int bodySize;
    private int bodyBufferedSize;

    /*
     * Reference fields which hold parsed results, used to create new WorkUnits
     */
    //private byte[] errorString;
    private byte[] header;
    private byte[] body;
    private WorkUnit workUnit;

    /*
     * Internal state of each PacketParser, need to be reset upon successfully parsing a new packet
     */
    private LineParsingState lineState = LineParsingState.READING;
    private boolean headerFound = false;
    private boolean headerParsed = false;
    private boolean hasBody = false;

    /*
     * Private helper objects
     */
    private final Logger logger;
    private SocketChannel client;

    public PacketParser(String loggerName)
    {
        this.logger = LogManager.getLogger(PacketParser.class + "-" + loggerName);
    }

    public List<WorkUnit> receiveAndParse(SelectionKey key)
    {
        this.client = (SocketChannel) key.channel();

        WorkUnit temp;
        boolean hasMore = false;
        ArrayList<WorkUnit> result = new ArrayList<>();

        while (true) {
            try {
                temp = internalParsing(hasMore);
            } catch (IOException e) {
                this.logger.log(Level.ERROR, "Unexpected problems with this channel, flushing ByteBuffer.");
                this.logger.log(Level.ERROR, e.getMessage());
                break;
            }

            if (temp != null) {
                result.add(temp);
                hasMore = true;
            } else {
                break;
            }
        }

        // Clean the state until new data is available
        // this.resetState();

        return result;
    }

    /**
     * Main parsing logic which will parse at most a single packet and return either a complete packet or nothing to
     * indicate the stream has currently been exhausted and needs to receive new data.
     * @return A complete workUnit on successful parsing, else throws an IOException
     */
    private WorkUnit internalParsing(boolean hasMore) throws IOException
    {
        // If a packet has been parsed and the buffer has been compacted, maybe it would be beneficial to try and fill
        // up any remaining NIC-bound data...
        int lastOffset;
        int lineReadOffset = 0;

        try {
            lastOffset = client.read(this.buffer);
            this.logger.log(Level.DEBUG, "Parsing {} bytes of data received.", lastOffset);
            this.buffer.flip();
        } catch (IOException e) {
            this.buffer.clear();
            throw e;
        }

        if (hasMore) {
            lastOffset += this.buffer.limit();
        }
        this.bufferOffset += lastOffset;

        if (lastOffset == -1) {
            this.logger.log(Level.WARN, "Client closed connection, closing the SocketChannel proactively.");
            this.buffer.clear();
            this.client.close();
            return null;
        }

        if (!headerFound) {
            // We are still receiving for the header
            // Scan here for "EOL" as per specification, store the header once it's found also in a byte[]
            lineReadOffset = this.readLine(lastOffset, false);
        }

        if (headerFound && !headerParsed) {
            // Header fully received, parse it
            this.parseHeader(lineReadOffset);
        }

        if (headerFound && headerParsed && hasBody) {
            // If there is a body expected, parse it in here...
            this.setBody();
        }

        WorkUnit result = null;
        if (this.header != null && (!this.hasBody || this.body != null)) {
            if (workUnit != null) {
                this.logger.log(Level.DEBUG, "Packet found, putting on return list.");
                result = this.workUnit;
                cleanTemporaryState();
            }
        }

        if (!this.buffer.hasRemaining() || (headerFound && headerParsed)) {
            this.buffer.compact();
            this.bufferOffset = 0;
            this.headerOffset = 0;
        }

        this.logger.log(Level.DEBUG, "Parsing finished for current packet.");
        return result;
    }

    private int readLine(int readOffset, boolean flushLine)
    {
        this.logger.log(Level.DEBUG, "Receiving header.");

        boolean shouldBreak = !flushLine && headerFound;
        int endOffset = 0, i = 0;
        if (flushLine) {
            i = readOffset;
        } else {
            i = this.bufferOffset - readOffset;
        }

        for ( ; i < bufferOffset && !shouldBreak; ++i) {
            switch (this.lineState) {
                case READING:
                    if (this.buffer.get(i) == (byte) '\r') {
                        this.lineState = LineParsingState.SLASH;
                    }
                    break;
                case SLASH:
                    if (this.buffer.get(i) == (byte) '\n' && !flushLine) {
                        this.headerFound = true;
                        this.headerOffset = i + 1;
                        this.header = new byte[this.headerOffset];
                        this.buffer.get(this.header, 0, this.headerOffset);
                        this.logger.log(Level.DEBUG, "Header received.");
                    }
                    this.lineState = LineParsingState.READING;
                    shouldBreak = true;
                    endOffset = i + 1;
                    break;
            }
        }

        if (!shouldBreak || flushLine) {
            if (flushLine && this.buffer.position() < endOffset) {
                this.buffer.position(endOffset);
            }
            this.buffer.compact();
        }

        return endOffset;
    }

    /**
     * Internal method which statefully saves the body of the request and marks the WorkUnit as usable.
     */
    private void setBody()
    {
        this.logger.log(Level.DEBUG, "Parsing body.");
        this.bodyBufferedSize = this.bufferOffset - this.headerOffset;
        if (this.bodyBufferedSize >= this.bodySize + 2) {
            this.body = new byte[this.bodySize + 2];
            this.buffer.get(this.body, 0, this.bodySize + 2);

            if (this.body[this.body.length - 2] != '\r' && this.body[this.body.length - 1] != '\n') {
                this.readLine(this.body.length, true);
            } else if (this.body[this.body.length - 1] == '\r') {
                this.readLine(this.body.length - 1, true);
            }

            if (this.workUnit.type.equals(WorkUnitType.SET)) {
                ((WorkUnitSet) this.workUnit).setBody(this.body);
            } else if (this.workUnit.type.equals(WorkUnitType.VALUE)) {
                ((WorkUnitValue) this.workUnit).setBody(this.body);
            }
        } else {
            this.buffer.compact();
        }
    }

    /**
     * Internal method which delegates to the correct parsing method for the first bytes in the header.
     * This method requires a helper method which adjusts the state of this instance accordingly.
     */
    private void parseHeader(int readOffset)
    {
        this.logger.log(Level.DEBUG, "Parsing header.");

        if ((this.header.length > PacketParser.getHeaderParsing.length - 1 &&
                this.header[0] == 'g' && this.header[1] == 'e' && this.header[2] == 't') &&
                (this.header[3] == ' ' ||
                        (this.header.length > PacketParser.getsHeaderParsing.length - 1 &&
                                this.header[3] == 's' && this.header[4] == ' '))) {
            this.parseGetHeader(this.header[PacketParser.getHeaderParsing.length - 1] == ' ' ?
                    PacketParser.getHeaderParsing.length :
                    PacketParser.getsHeaderParsing.length);
        } else if (this.header.length > PacketParser.setHeaderParsing.length - 1 &&
                this.header[0] == 's' && this.header[1] == 'e' && this.header[2] == 't' && this.header[3] == ' ') {
            this.parseSetHeader();
        } else if (this.header.length > PacketParser.valueHeaderParsing.length - 1 &&
                this.header[0] == 'V' && this.header[1] == 'A' && this.header[2] == 'L' && this.header[3] == 'U' &&
                this.header[4] == 'E' && this.header[5] == ' ') {
            this.parseValueHeader();
        } else if (this.header.length > PacketParser.endHeaderParsing.length - 1 &&
                this.header[0] == 'E' && this.header[1] == 'N' && this.header[2] == 'D' && this.header[3] == '\r' &&
                this.header[4] == '\n') {
            this.parseEndHeader();
        } else if (this.header.length > PacketParser.storedHeaderParsing.length - 1 &&
                this.header[0] == 'S' && this.header[1] == 'T' && this.header[2] == 'O' && this.header[3] == 'R' &&
                this.header[4] == 'E' && this.header[5] == 'D' && this.header[6] == '\r' && this.header[7] == '\n') {
            this.parseStoredHeader();
        } else if (this.header.length > PacketParser.errorHeaderParsing.length - 1 &&
                this.header[0] == 'E' && this.header[1] == 'R' && this.header[2] == 'R' && this.header[3] == 'O' &&
                this.header[4] == 'R' && this.header[5] == '\r' && this.header[6] == '\n') {
            this.parseErrorHeader();
        } else if (this.header.length > PacketParser.clientErrorHeaderParsing.length - 1 &&
                this.header[0] == 'C' && this.header[1] == 'L' && this.header[2] == 'I' && this.header[3] == 'E' &&
                this.header[4] == 'N' && this.header[5] == 'T' && this.header[6] == '_' && this.header[7] == 'E' &&
                this.header[8] == 'R' && this.header[9] == 'R' && this.header[10] == 'O' && this.header[11] == 'R' &&
                this.header[12] == ' ') {
            this.parseClientErrorHeader();
        } else if (this.header.length > PacketParser.serverErrorHeaderParsing.length - 1 &&
                this.header[0] == 'S' && this.header[1] == 'E' && this.header[2] == 'R' && this.header[3] == 'V' &&
                this.header[4] == 'E' && this.header[5] == 'R' && this.header[6] == '_' && this.header[7] == 'E' &&
                this.header[8] == 'R' && this.header[9] == 'R' && this.header[10] == 'O' && this.header[11] == 'R' &&
                this.header[12] == ' ') {
            this.parseServerErrorHeader();
        } else {
            this.parseHeaderFailure(readOffset);
        }

        this.headerParsed = true;
    }

    /**
     * Method to clean up state after a packet has been fully parsed.
     */
    private void cleanTemporaryState()
    {
        this.buffer.compact();

        this.bufferOffset = 0;
        this.headerOffset = 0;
        this.bodySize = 0;
        this.bodyBufferedSize = 0;

        this.header = null;
        this.body = null;
        this.workUnit = null;

        this.lineState = LineParsingState.READING;
        this.headerFound = false;
        this.headerParsed = false;
        this.hasBody = false;
    }

    /**
     * Generic header parser which is used to parse whitespace separated fields from a starting offset.
     *
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
        return new Tuple<>(whitespaces, contents);
    }

    /**
     * Helper method to indicate an invalid Memcached request was sent.
     * Per specification until EoL is supposed to be read and then retried again. As such this method does nothing as
     * the whole line has already been "read in" and this is only processing it. Also as no body is expected this method
     * will not set it as such
     */
    private void parseHeaderFailure(int readOffset)
    {
        this.logger.log(Level.DEBUG, "Parsing 'I_N_V_A_L_I_D' header...");
        this.workUnit = new WorkUnitInvalid(this.client);
        this.logger.log(Level.DEBUG, "Parsed 'I_N_V_A_L_I_D' header. Discarding data until end of line.");
        this.readLine(readOffset + 1, true);
        this.buffer.compact();
    }

    /**
     * Parses a GET(S) statefully for this instance.
     *
     * @param readOffset Offset where the first field starts in the buffer.
     */
    private void parseGetHeader(int readOffset)
    {
        this.logger.log(Level.DEBUG, "Parsing 'get' header...");
        ArrayList<Integer> whitespace;
        ArrayList<byte[]> contents;
        Tuple<ArrayList<Integer>, ArrayList<byte[]>> result = this.parseHeaderGeneric(readOffset);
        whitespace = result.first;
        contents = result.second;

        this.workUnit = new WorkUnitGet(this.client, this.header, contents, whitespace);
        this.logger.log(Level.DEBUG, "Parsed 'get' header.");
    }

    /**
     * Parses a SET statefully for this instance.
     */
    private void parseSetHeader()
    {
        this.logger.log(Level.DEBUG, "Parsing 'set' header...");
        this.hasBody = true;
        ArrayList<Integer> whitespace;
        ArrayList<byte[]> contents;
        Tuple<ArrayList<Integer>, ArrayList<byte[]>> result = this.parseHeaderGeneric(PacketParser.setHeaderParsing.length);
        whitespace = result.first;
        contents = result.second;

        this.workUnit = new WorkUnitSet(this.client, this.header, contents, whitespace);
        this.bodySize = ((WorkUnitSet) this.workUnit).bytes;
        this.logger.log(Level.DEBUG, "Parsed 'set' header.");
    }

    /**
     * Parses a VALUE statefully for this instance.
     */
    private void parseValueHeader()
    {
        this.logger.log(Level.DEBUG, "Parsing 'VALUE' header...");
        this.hasBody = true;
        ArrayList<Integer> whitespace;
        ArrayList<byte[]> contents;
        Tuple<ArrayList<Integer>, ArrayList<byte[]>> result = this.parseHeaderGeneric(PacketParser.valueHeaderParsing.length);
        whitespace = result.first;
        contents = result.second;

        this.workUnit = new WorkUnitValue(this.client, this.header, contents, whitespace);
        this.bodySize = ((WorkUnitValue) this.workUnit).bytes;
        this.logger.log(Level.DEBUG, "Parsed 'VALUE' header.");
    }

    /**
     * Parses a CLIENT_ERROR statefully for this instance.
     */
    private void parseClientErrorHeader()
    {
        this.logger.log(Level.DEBUG, "Parsing 'CLIENT_ERROR' header...");
        //int errorLength = this.headerOffset - 2 - PacketParser.clientErrorHeaderParsing.length;
        //this.errorString = new byte[errorLength];
        //this.buffer.get(this.errorString, 0, errorLength);

        this.workUnit = new WorkUnitClientError(this.client, header);
        this.logger.log(Level.DEBUG, "Parsed 'CLIENT_ERROR' header.");
    }

    /**
     * Parses a SERVER_ERROR statefully for this instance.
     */
    private void parseServerErrorHeader()
    {
        this.logger.log(Level.DEBUG, "Parsing 'SERVER_ERROR' header...");
        //int errorLength = this.headerOffset - 2 - PacketParser.serverErrorHeaderParsing.length;
        //this.errorString = new byte[errorLength];
        //this.buffer.get(this.errorString, PacketParser.serverErrorHeader.length, errorLength);

        this.workUnit = new WorkUnitServerError(this.client, header);
        this.logger.log(Level.DEBUG, "Parsed 'SERVER_ERROR' header.");
    }

    /**
     * Parses a ERROR statefully for this instance
     */
    private void parseErrorHeader()
    {
        this.logger.log(Level.DEBUG, "Parsing 'ERROR' header...");
        this.workUnit = new WorkUnitError(this.client);
        this.logger.log(Level.DEBUG, "Parsed 'ERROR' header.");

    }

    /**
     * Parses a STORED statefully for this instance.
     */
    private void parseStoredHeader()
    {
        this.logger.log(Level.DEBUG, "Parsing 'STORED' header...");
        this.workUnit = new WorkUnitStored(this.client);
        this.logger.log(Level.DEBUG, "Parsed 'STORED' header.");
    }

    /**
     * Parses a END statefully for this instance.
     */
    private void parseEndHeader()
    {
        this.logger.log(Level.DEBUG, "Parsing 'END' header...");
        this.workUnit = new WorkUnitEnd(this.client);
        this.logger.log(Level.DEBUG, "Parsed 'END' header.");
    }

    private enum LineParsingState {
        READING, SLASH
    }
}
