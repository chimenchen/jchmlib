package org.jchmlib;

import java.io.IOException;
import java.nio.ByteBuffer;

class ChmPmglHeader {

    public String signature;       //  0 (PMGL)
    public int free_space;         //  4
    public int unknown_0008;       //  8
    public int block_prev;         //  c
    public int block_next;         // 10

    public ChmPmglHeader(ByteBuffer bb) throws IOException {
        byte[] sbuf = new byte[4];
        bb.get(sbuf);
        signature = new String(sbuf);
        free_space = bb.getInt();
        bb.getInt();
        block_prev = bb.getInt();
        block_next = bb.getInt();
    }

    public String toString() {
        return signature +
                "\n\t free_space:     " + Integer.toHexString(free_space) +
                "\n\t block_prev:     " + Integer.toHexString(block_prev) +
                "\n\t block_next:     " + Integer.toHexString(block_next);
    }
}
