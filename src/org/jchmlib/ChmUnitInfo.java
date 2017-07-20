/* ChmUnitInfo.java 2006/05/25
 *
 * Copyright 2006 Chimen Chen. All rights reserved.
 *
 */

package org.jchmlib;

import java.io.IOException;
import java.nio.ByteBuffer;
import org.jchmlib.util.ByteBufferHelper;

/**
 * ChmUnitInfo represents an object in a CHM archive.
 */
public class ChmUnitInfo {

    public long start;
    public long length;
    public int space;
    public int flags;
    public String path;

    public ChmUnitInfo(ByteBuffer bb) throws IOException {
        // parse str len
        int strLen = (int) ByteBufferHelper.parseCWord(bb);
        // parse path
        path = ByteBufferHelper.parseUTF8(bb, strLen);
        // parse info
        space = (int) ByteBufferHelper.parseCWord(bb);
        start = ByteBufferHelper.parseCWord(bb);
        length = ByteBufferHelper.parseCWord(bb);

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

    public String toString() {
        return "ChmUnitInfo" +
                "\n\t path:           " + path +
                "\n\t start:          " + start +
                "\n\t length:         " + length +
                "\n\t space:          " + space +
                "\n\t flags:          " + flags;
    }
}
