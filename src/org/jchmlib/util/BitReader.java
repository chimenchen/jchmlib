package org.jchmlib.util;

import java.nio.ByteBuffer;

public class BitReader {

    long bitBuffer;
    int bitsBuffered;
    private ByteBuffer byteBuffer;
    // An LZX bitstream is a sequence of 16 bit integers
    // stored in the order least-significant-byte most-significant-byte.
    // see https://msdn.microsoft.com/en-us/library/bb417343.aspx#bitstream
    private boolean isLzxBitStream;

    public BitReader(ByteBuffer bb, boolean isLzxBitStream) {
        init(bb, isLzxBitStream);
    }

    void init(ByteBuffer bb, boolean isLzxBitStream) {
        byteBuffer = bb;
        bitBuffer = 0L;
        bitsBuffered = 0;
        this.isLzxBitStream = isLzxBitStream;
    }

    /**
     * Ensures there are at least n bits in the bit buffer
     */
    void ensureBits(int n) {
        if (isLzxBitStream) {
            while (bitsBuffered < n) {
                // Attention!
                int lowBits = readUInt8() & 0xFF;
                int highBits = readUInt8() & 0xFF;
                bitBuffer |= (long) ((highBits << 8) | lowBits) << (48 - bitsBuffered);
                bitsBuffered += 16;
            }
        } else {
            while (bitsBuffered < n) {
                int bits = readUInt8() & 0xFF;
                bitBuffer |= (long) bits << (56 - bitsBuffered);
                bitsBuffered += 8;
            }
        }
    }

    /**
     * Extracts (without removing) N bits from the bit buffer
     */
    int peekBits(int n) {
        return (int) (bitBuffer >>> (64 - n));
    }

    void removeBits(int n) {
        bitBuffer <<= n;
        bitsBuffered -= n;
    }

    int readBits(int n) {
        ensureBits(n);
        int result = peekBits(n);
        removeBits(n);
        return result;
    }

    private int readUInt8() {
        if (byteBuffer.hasRemaining()) {
            return byteBuffer.get() & 0xFF;
        }
        // it is possible in decompressing
        return 0;
    }

}
