package org.jchmlib;

import java.nio.ByteBuffer;
import org.jchmlib.util.ByteBufferHelper;

// import java.text.DateFormat;
// import java.text.SimpleDateFormat;

class ChmItsfHeader {

    String signature;     /*  0 (ITSF) */
    int version;          /*  4 */
    int header_len;       /*  8 */
    int unknown_000c;     /*  c */
    int last_modified;    /* 10 */
    int lang_id;          /* 14 */
    String dir_uuid;      /* 18 */
    String stream_uuid;   /* 28 */
    long unknown_offset;  /* 38 */
    long unknown_len;     /* 40 */
    long dir_offset;      /* 48 */
    long dir_len;         /* 50 */
    long data_offset;     /* 58 (Not present before V3) */

    public ChmItsfHeader(ByteBuffer bb) {
        byte[] bytes = new byte[4];
        bb.get(bytes);
        signature = new String(bytes);

        version = bb.getInt();
        header_len = bb.getInt();
        unknown_000c = bb.getInt();
        last_modified = ByteBufferHelper.parseBigEndianInt(bb);
        lang_id = bb.getInt();
        bb.get(new byte[32]);
        unknown_len = bb.getLong();
        unknown_offset = bb.getLong();
        dir_offset = bb.getLong();
        dir_len = bb.getLong();
        if (version >= 3) {
            data_offset = bb.getLong();
        } else {
            data_offset = dir_offset + dir_len;
        }
    }

    public String toString() {
        return signature +
                "\n\t version:        0x" + Integer.toHexString(version) + " (" + version + ")" +
                "\n\t header_len:     0x" + Integer.toHexString(header_len) + " (" + header_len
                + ")" +
                "\n\t lang_id:        0x" + Integer.toHexString(lang_id) + " (" + lang_id + ")" +
                "\n\t unknown_offset: 0x" + Long.toHexString(unknown_offset) + " (" + unknown_offset
                + ")" +
                "\n\t unknown_len:    0x" + Long.toHexString(unknown_len) + " (" + unknown_len + ")"
                +
                "\n\t dir_offset:     0x" + Long.toHexString(dir_offset) + " (" + dir_offset + ")" +
                "\n\t dir_len:        0x" + Long.toHexString(dir_len) + " (" + dir_len + ")" +
                "\n\t data_offset:    0x" + Long.toHexString(data_offset) + " (" + data_offset
                + ")";
    }
}
