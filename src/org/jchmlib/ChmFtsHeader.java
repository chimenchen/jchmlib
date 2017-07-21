package org.jchmlib;

import java.nio.ByteBuffer;
import org.jchmlib.util.ByteBufferHelper;

public class ChmFtsHeader {

    // Offset to the single leaf node (if there are no index nodes),
    // or the root index node of the tree (if there are any index nodes).
    public int nodeOffset;   /* 0x14 */
    public short treeDepth;  /* 0x18 */
    // Scale for encoding of the document index in the WLCs
    public byte docIndexS;   /* 0x1E */
    // Root size for encoding of the document index in the WLCs
    public byte docIndexR;   /* 0x1F */
    // Scale for encoding of the code count in the WLCs
    public byte codeCountS;  /* 0x20 */
    // Root size for encoding of the code count in the WLCs
    public byte codeCountR;  /* 0x21 */
    // Scale for encoding of the location codes in the WLCs
    public byte locCodesS;   /* 0x22 */
    // Root size for encoding of the location codes in the WLCs
    public byte locCodesR;   /* 0x23 */
    // size of each of the leaf and index nodes (4096)
    public int nodeLen;      /* 0x2e */
    public int maxWordLen;   /* 0x3e */

    public ChmFtsHeader(ByteBuffer bb) {
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
