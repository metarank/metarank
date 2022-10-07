package ai.metarank.util;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Taken from the Bazel:
 * https://github.com/bazelbuild/bazel/blob/master/src/main/java/com/google/devtools/build/lib/util/VarInt.java
 */
public class VarNum {
    public static void putVarLong(long v, DataOutput sink) throws IOException {
        long highBits = v;
        do {
            long lowBits = highBits & 0x7F;
            byte b = (byte) (int) lowBits;
            highBits >>>= 7;
            if (highBits > 0L) {
                b = (byte) (b | 0x80);
            }
            sink.writeByte(b);
        }
        while (highBits > 0L);
    }

    public static void putVarInt(int v, DataOutput sink) throws IOException {
        while (true) {
            int bits = v & 0x7f;
            v >>>= 7;
            if (v == 0) {
                sink.writeByte((byte) bits);
                return;
            }
            sink.writeByte((byte) (bits | 0x80));
        }
    }


    public static int getVarInt(DataInput src) throws IOException {
        int tmp;
        if ((tmp = src.readByte()) >= 0) {
            return tmp;
        }
        int result = tmp & 0x7f;
        if ((tmp = src.readByte()) >= 0) {
            result |= tmp << 7;
        } else {
            result |= (tmp & 0x7f) << 7;
            if ((tmp = src.readByte()) >= 0) {
                result |= tmp << 14;
            } else {
                result |= (tmp & 0x7f) << 14;
                if ((tmp = src.readByte()) >= 0) {
                    result |= tmp << 21;
                } else {
                    result |= (tmp & 0x7f) << 21;
                    result |= (tmp = src.readByte()) << 28;
                    while (tmp < 0) {
                        // We get into this loop only in the case of overflow.
                        // By doing this, we can call getVarInt() instead of
                        // getVarLong() when we only need an int.
                        tmp = src.readByte();
                    }
                }
            }
        }
        return result;
    }

    public static long getVarLong(DataInput src) throws IOException {
        int idx = 0;
        long longValue = 0L;
        byte b = 0;
        do {
            b = src.readByte();
            long lowByte = b & 0x7F;
            lowByte <<= idx * 7;
            longValue |= lowByte;
            idx++;
        }
        while (b < 0);

        return longValue;
    }
}