package org.jchmlib;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * LZXC control data block.
 */
class ChmLzxcControlData {

    int resetInterval;          /*  c        */
    int windowSize;             /* 10        */
    int windowsPerReset;        /* 14        */

    public ChmLzxcControlData(ByteBuffer bb) throws IOException {
        try {
            ByteBufferHelper.skip(bb, 4);
            String signature = ByteBufferHelper.parseString(bb, 4, "ASCII");
            if (!signature.equals("LZXC")) {
                throw new IOException("Unexpected LZXC header signature.");
            }
            int version = bb.getInt();
            resetInterval = bb.getInt();
            windowSize = bb.getInt();
            if (version == 2) {
                resetInterval *= 0x8000;
                windowSize *= 0x8000;
            }
            windowsPerReset = bb.getInt();
        } catch (Exception e) {
            throw new IOException("Failed to parse LZXC control data", e);
        }
    }
}
