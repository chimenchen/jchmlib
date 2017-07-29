/*
 * Copyright 2017 chimenchen. All rights reserved.
 */

package org.jchmlib;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * ChmUnitInfo represents an object in a CHM archive.
 */
public class ChmUnitInfo {

    /**
     * The offset is from the beginning of the content section the file is in,
     * after the section has been decompressed (if appropriate).
     * it is relative to dataOffset (for uncompressed/decompressed object)
     */
    long start;
    /**
     * The length also refers to length of the file in the section after decompression.
     **/
    long length;
    int space;
    int flags;
    String path;

    ChmUnitInfo(String path) {
        this.path = path;
        length = 0;
        space = 0;
        flags = ChmFile.CHM_ENUMERATE_DIRS | ChmFile.CHM_ENUMERATE_NORMAL;
    }

    ChmUnitInfo(ByteBuffer bb) throws IOException {
        try {
            // parse str len
            int strLen = (int) ByteBufferHelper.parseCWord(bb);
            // parse path
            path = ByteBufferHelper.parseString(bb, strLen, "UTF8");  // Nonnull
            // parse info
            space = (int) ByteBufferHelper.parseCWord(bb);
            start = ByteBufferHelper.parseCWord(bb);
            length = ByteBufferHelper.parseCWord(bb);
        } catch (Exception e) {
            throw new IOException("Failed to parse CHM unit info", e);
        }

        flags = 0;
        // check for DIRS vs. FILES
        if (path.endsWith("/")) {
            flags |= ChmFile.CHM_ENUMERATE_DIRS;
        } else {
            flags |= ChmFile.CHM_ENUMERATE_FILES;
        }

        // check for NORMAL vs. META
        if (path.startsWith("/")) {
            // check for NORMAL vs. SPECIAL
            if (path.startsWith("/#") ||
                    path.startsWith("/$")) {
                flags |= ChmFile.CHM_ENUMERATE_SPECIAL;
            } else {
                flags |= ChmFile.CHM_ENUMERATE_NORMAL;
            }
        } else {
            flags |= ChmFile.CHM_ENUMERATE_META;
        }

        if (path.endsWith(".hhc") || path.endsWith(".hhk")) {
            flags = ChmFile.CHM_ENUMERATE_META;
        }
    }

    public long getLength() {
        return length;
    }

    public int getFlags() {
        return flags;
    }

    public String getPath() {
        return path;
    }

    public String toString() {
        return "ChmUnitInfo" +
                "\n\t path:           " + path +
                "\n\t start:          " + start +
                "\n\t length:         " + length +
                "\n\t space:          " + space +
                "\n\t flags:          " + flags;
    }
}
