package org.jchmlib;

import java.nio.ByteBuffer;

class BitReader {

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

    /**
     * Parse a kind of integer of variant length.
     * The integer is encoded using the scale and root method.
     * http://www.nongnu.org/chmspec/latest/Internal.html#FIftiMain_scale_root
     */
    public long getSrInt(byte s, byte r) {
        if (s != 2) {
            return ~(long) 0;
        }

        int count = 0;
        while (readBits(1) == 1) {
            count++;
        }

        int n_bits = r + ((count > 0) ? count - 1 : 0);
        long ret = readBits(n_bits);
        if (count > 0) {
            ret |= (long) 1 << n_bits;
        }
        return ret;
    }

}
