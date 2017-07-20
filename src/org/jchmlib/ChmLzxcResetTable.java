package org.jchmlib;

import java.nio.ByteBuffer;

/**
 * LZXC reset table.
 */
class ChmLzxcResetTable {

    public int version;
    public int block_count;
    public int unknown;
    public int table_offset;
    public long uncompressed_len;
    public long compressed_len;
    public long block_len;

    public ChmLzxcResetTable(ByteBuffer bb) {
        version = bb.getInt();
        block_count = bb.getInt();
        unknown = bb.getInt();
        table_offset = bb.getInt();
        uncompressed_len = bb.getLong();
        compressed_len = bb.getLong();
        block_len = bb.getLong();
    }

    public String toString() {
        return "ChmLzxcResetTable" +
                "\n\tversion:           " + version +
                "\n\tblock_count:       " + block_count +
                "\n\ttable_offset:      " + table_offset +
                "\n\tuncompressed_len:  " + uncompressed_len +
                "\n\tcompressed_len:    " + compressed_len +
                "\n\tblock_len:         " + block_len;
    }

}
