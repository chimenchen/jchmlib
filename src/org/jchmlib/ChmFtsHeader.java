package org.jchmlib;

import java.nio.ByteBuffer;

public class ChmFtsHeader {

    public int node_offset;   /* 0x14 */
    public short tree_depth;  /* 0x18 */
    public int unknown1a;     /* 0x1A */
    public byte doc_index_s;  /* 0x1E */
    public byte doc_index_r;  /* 0x1F */
    public byte code_count_s; /* 0x20 */
    public byte code_count_r; /* 0x21 */
    public byte loc_codes_s;  /* 0x22 */
    public byte loc_codes_r;  /* 0x23 */
    public int node_len;      /* 0x2e */

    public ChmFtsHeader(ByteBuffer bb) {
        byte[] bytes = new byte[0x14];
        bb.get(bytes);
        node_offset = bb.getInt();
        tree_depth = bb.getShort();
        unknown1a = bb.getInt();
        doc_index_s = bb.get();
        doc_index_r = bb.get();
        code_count_s = bb.get();
        code_count_r = bb.get();
        loc_codes_s = bb.get();
        loc_codes_r = bb.get();
        bytes = new byte[10];
        bb.get(bytes);
        node_len = bb.getInt();
    }
}
