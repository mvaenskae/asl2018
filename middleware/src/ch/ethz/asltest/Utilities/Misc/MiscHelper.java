package ch.ethz.asltest.Utilities.Misc;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public final class MiscHelper {

    public static long getLongFromByteArray(byte[] bytes)
    {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.put(bytes);
        buffer.flip(); //need flip
        return buffer.getLong();
    }

    public static int getIntFromByteArray(byte[] bytes)
    {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
        buffer.put(bytes);
        buffer.flip(); //need flip
        return buffer.getInt();
    }

    public static ByteBuffer deepCopy(ByteBuffer original)
    {
        ByteBuffer copy = ByteBuffer.allocate(original.capacity());
        original.rewind();
        copy.put(original);
        original.rewind();
        copy.flip();
        return copy;
    }

    public static ArrayList<ByteBuffer> deepCopy(List<ByteBuffer> original)
    {
        ArrayList<ByteBuffer> copy = new ArrayList<>();
        for(ByteBuffer buf : original) {
            copy.add(deepCopy(buf));
        }
        return copy;
    }
}
