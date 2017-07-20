package org.jchmlib;

import java.nio.ByteBuffer;

/**
 * LZXC control data block.
 */
class ChmLzxcControlData {

    public int size;                   /*  0        */
    public String signature;           /*  4 (LZXC) */
    public int version;                /*  8        */
    public int resetInterval;          /*  c        */
    public int windowSize;             /* 10        */
    public int windowsPerReset;        /* 14        */
    public int unknown_18;             /* 18        */

    public ChmLzxcControlData(ByteBuffer bb) {
        // TODO: unsigned
        size = bb.getInt();
        byte[] bytes = new byte[4];
        bb.get(bytes);
        signature = new String(bytes);
        version = bb.getInt();
        resetInterval = bb.getInt();
        windowSize = bb.getInt();
        windowsPerReset = bb.getInt();

        if (bb.remaining() >= 4) {
            unknown_18 = bb.getInt();
        } else {
            unknown_18 = 0;
        }

        if (version == 2) {
            resetInterval *= 0x8000;
            windowSize *= 0x8000;
        }
    }

    public String toString() {
        return signature +
                "\n\tsize:            " + size +
                "\n\tversion:         " + version +
                "\n\tresetInterval:   " + resetInterval +
                "\n\twindowSize:      " + windowSize +
                "\n\twindowsPerReset: " + windowsPerReset;
    }

}
