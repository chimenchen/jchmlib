package org.jchmlib;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * LZXC reset table.
 */
@SuppressWarnings({"unused", "WeakerAccess"})
class ChmLzxcResetTable {

    final int blockCount;
    final int tableOffset;
    final long uncompressedLen;
    final long compressedLen;
    final long blockLen;

    ChmLzxcResetTable(ByteBuffer bb) throws IOException {
        try {
            ByteBufferHelper.skip(bb, 4); // version = bb.getInt();
            blockCount = bb.getInt();
            ByteBufferHelper.skip(bb, 4); // unknown = bb.getInt();
            tableOffset = bb.getInt();
            uncompressedLen = bb.getLong();
            compressedLen = bb.getLong();
            blockLen = bb.getLong();
        } catch (Exception e) {
            throw new IOException("Failed to parse LZXC reset table");
        }
    }
}
