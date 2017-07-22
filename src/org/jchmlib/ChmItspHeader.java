package org.jchmlib;

import java.io.IOException;
import java.nio.ByteBuffer;
import org.jchmlib.util.ByteBufferHelper;

/**
 * Directory header
 */
class ChmItspHeader {

    int headerLen;         /*  8 */
    int blockLen;          /* 10 */
    int indexRoot;         /* 1c */
    int indexHead;         /* 20 */

    public ChmItspHeader(ByteBuffer bb) throws IOException {
        String signature = ByteBufferHelper.parseString(bb, 4, "ASCII");
        if (!signature.equals("ITSP")) {
            throw new IOException("Unexpected ITSP header signature.");
        }
        try {
            ByteBufferHelper.skip(bb, 4); // version = bb.getInt();
            headerLen = bb.getInt();
            ByteBufferHelper.skip(bb, 4);
            blockLen = bb.getInt();
            ByteBufferHelper.skip(bb, 8);
            indexRoot = bb.getInt();
            indexHead = bb.getInt();
            ByteBufferHelper.skip(bb, 48); // unknown0024 = bb.getInt();
        } catch (Exception e) {
            throw new IOException("Failed to parse ITSP header", e);
        }
    }
}

