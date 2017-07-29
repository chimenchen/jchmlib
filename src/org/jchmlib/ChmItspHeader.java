package org.jchmlib;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Directory header
 */
@SuppressWarnings({"unused", "WeakerAccess"})
class ChmItspHeader {

    int headerLen;         /*  8 */
    int blockLen;          /* 10 */
    int indexRoot;         /* 1c */
    int indexHead;         /* 20 */
    int langID;            /* 30 */

    public ChmItspHeader(ByteBuffer bb) throws IOException {
        String signature = ByteBufferHelper.parseString(bb, 4, "ASCII");
        if (!signature.equals("ITSP")) {
            throw new IOException("Unexpected ITSP header signature.");
        }
        try {
            ByteBufferHelper.skip(bb, 4);
            headerLen = bb.getInt();
            ByteBufferHelper.skip(bb, 4);
            blockLen = bb.getInt();
            ByteBufferHelper.skip(bb, 8);
            indexRoot = bb.getInt();
            indexHead = bb.getInt();
            ByteBufferHelper.skip(bb, 12);
            langID = bb.getInt();
            ByteBufferHelper.skip(bb, 32);
        } catch (Exception e) {
            throw new IOException("Failed to parse ITSP header", e);
        }
    }
}

