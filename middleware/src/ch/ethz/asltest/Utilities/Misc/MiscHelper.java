package ch.ethz.asltest.Utilities.Misc;

import java.nio.ByteBuffer;

public final class MiscHelper {

    public static long getLongFromByteArray(byte[] bytes)
    {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.put(bytes);
        buffer.flip();//need flip
        return buffer.getLong();
    }

    public static int getIntFromByteArray(byte[] bytes)
    {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
        buffer.put(bytes);
        buffer.flip();//need flip
        return buffer.getInt();
    }
}
