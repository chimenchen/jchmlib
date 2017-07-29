package org.jchmlib;

import java.io.IOException;
import java.nio.ByteBuffer;

class ChmFtsHeader {

    // Offset to the single leaf node (if there are no index nodes),
    // or the root index node of the tree (if there are any index nodes).
    final int nodeOffset;   // 0x14
    final short treeDepth;  // 0x18
    // Scale for encoding of the document index in the WLCs
    final byte docIndexS;   // 0x1E
    // Root size for encoding of the document index in the WLCs
    final byte docIndexR;   // 0x1F
    // Scale for encoding of the code count in the WLCs
    final byte codeCountS;  // 0x20
    // Root size for encoding of the code count in the WLCs
    final byte codeCountR;  // 0x21
    // Scale for encoding of the location codes in the WLCs
    final byte locCodesS;   // 0x22
    // Root size for encoding of the location codes in the WLCs
    final byte locCodesR;   // 0x23
    // size of each of the leaf and index nodes (4096)
    final int nodeLen;      // 0x2e
    // length of the longest word
    final int maxWordLen;   // 0x3e

    ChmFtsHeader(ByteBuffer bb) throws IOException {
        ByteBufferHelper.skip(bb, 0x14);
        nodeOffset = bb.getInt();
        treeDepth = bb.getShort();
        ByteBufferHelper.skip(bb, 4);
        docIndexS = bb.get();
        docIndexR = bb.get();
        codeCountS = bb.get();
        codeCountR = bb.get();
        locCodesS = bb.get();
        locCodesR = bb.get();
        ByteBufferHelper.skip(bb, 10);
        nodeLen = bb.getInt();
        ByteBufferHelper.skip(bb, 12);
        maxWordLen = bb.getInt();
    }
}
