package org.jchmlib;

import java.nio.ByteBuffer;

class ChmPmgiHeader {

    public String signature;       //  0 (PMGI)
    public int free_space;         //  4

    public ChmPmgiHeader(ByteBuffer bb) {
        byte[] sbuf = new byte[4];
        bb.get(sbuf);
        signature = new String(sbuf);
        free_space = bb.getInt();
    }

    public String toString() {
        return signature +
                "\n\t free_space:     " + Integer.toHexString(free_space);
    }
}
