package org.jchmlib;

import java.io.IOException;
import java.nio.ByteBuffer;

class ChmItsfHeader {

    int langId;          /* 14 */
    long dirOffset;      /* 48 */
    long dataOffset;     /* 58 (Not present before V3) */

    public ChmItsfHeader(ByteBuffer bb) throws IOException {
        String signature = ByteBufferHelper.parseString(bb, 4, "ASCII");
        if (!signature.equals("ITSF")) {
            throw new IOException("Unexpected ITSF header signature.");
        }
        try {
            int version = bb.getInt();
            ByteBufferHelper.skip(bb, 12);
            langId = bb.getInt();
            ByteBufferHelper.skip(bb, 48);
            dirOffset = bb.getLong();
            long dirLen = bb.getLong();
            if (version >= 3) {
                dataOffset = bb.getLong();
            } else {
                dataOffset = dirOffset + dirLen;
            }
        } catch (Exception e) {
            throw new IOException("Failed to parse ITSF header", e);
        }
    }
}
