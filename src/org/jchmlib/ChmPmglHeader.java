package org.jchmlib;

import java.io.IOException;
import java.nio.ByteBuffer;

class ChmPmglHeader {

    public int freeSpace;         //  4
    public int blockNext;         // 10

    public ChmPmglHeader(ByteBuffer bb) throws IOException {
        String signature = ByteBufferHelper.parseString(bb, 4, "ASCII");
        if (!signature.equals("PMGL")) {
            throw new IOException("Unexpected PMGL header signature.");
        }

        try {
            freeSpace = bb.getInt();
            ByteBufferHelper.skip(bb, 8);
            blockNext = bb.getInt();
        } catch (Exception e) {
            throw new IOException("Failed to parse PMGL header", e);
        }
    }
}
