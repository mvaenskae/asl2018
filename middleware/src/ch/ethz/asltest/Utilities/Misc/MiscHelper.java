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

// --Commented out by Inspection START (20/10/18 18:19):
//    public static int getIntFromByteArray(byte[] bytes)
//    {
//        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
//        buffer.put(bytes);
//        buffer.flip(); //need flip
//        return buffer.getInt();
//    }
// --Commented out by Inspection STOP (20/10/18 18:19)

    private static ByteBuffer deepCopy(ByteBuffer original)
    {
        ByteBuffer copy = ByteBuffer.allocate(original.capacity());
        original.rewind();
        copy.put(original);
        original.rewind();
        copy.flip();
        return copy;
    }

    public static ArrayList<ByteBuffer> shallowCopy(List<ByteBuffer> original)
    {
        ArrayList<ByteBuffer> copy = new ArrayList<>(original.size());
        for(ByteBuffer buf : original) {
            copy.add(buf.duplicate());
        }
        return copy;
    }

    public static ArrayList<ByteBuffer> deepCopy(List<ByteBuffer> original)
    {
        ArrayList<ByteBuffer> copy = new ArrayList<>(original.size());
        for(ByteBuffer buf : original) {
            copy.add(deepCopy(buf));
        }
        return copy;
    }
}
