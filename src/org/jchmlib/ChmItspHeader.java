package org.jchmlib;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Directory header
 */
class ChmItspHeader {

    String signature;       /*  0 (ITSP) */
    int version;            /*  4 */
    int header_len;         /*  8 */
    int unknown_000c;       /*  c */
    int block_len;          /* 10 */
    int blockidx_intvl;     /* 14 */
    int index_depth;        /* 18 */
    int index_root;         /* 1c */
    int index_head;         /* 20 */
    int unknown_0024;       /* 24 */
    int num_blocks;         /* 28 */
    int unknown_002c;       /* 2c */
    int lang_id;            /* 30 */
    String system_uuid;     /* 34 */
    String unknown_0044;    /* 44 */

    public ChmItspHeader(ByteBuffer bb) throws IOException {
        byte[] sbuf = new byte[4];
        bb.get(sbuf);
        signature = new String(sbuf);
        version = bb.getInt();
        header_len = bb.getInt();
        unknown_000c = bb.getInt();
        block_len = bb.getInt();
        blockidx_intvl = bb.getInt();
        index_depth = bb.getInt();
        index_root = bb.getInt();
        index_head = bb.getInt();
        unknown_0024 = bb.getInt();
        num_blocks = bb.getInt();
        unknown_002c = bb.getInt();
        lang_id = bb.getInt();
        bb.get(new byte[32]);
    }

    public String toString() {
        return signature +
                "\n\t version:        " + Integer.toHexString(version) +
                "\n\t header_len:     " + Integer.toHexString(header_len) +
                "\n\t block_len:      " + Long.toHexString(block_len) +
                "\n\t blockidx_intvl: " + Long.toHexString(blockidx_intvl) +
                "\n\t index_depth:    " + Long.toHexString(index_depth) +
                "\n\t index_root:     " + Long.toHexString(index_root) +
                "\n\t index_head:     " + Long.toHexString(index_head) +
                "\n\t lang_id:        " + Integer.toHexString(lang_id) +
                "\n\t num_blocks:     " + Long.toHexString(num_blocks);
    }
}

