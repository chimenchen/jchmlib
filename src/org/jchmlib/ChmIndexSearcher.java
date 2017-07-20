package org.jchmlib;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jchmlib.util.BitReader;
import org.jchmlib.util.ByteBufferHelper;
import org.jchmlib.util.GBKHelper;


public class ChmIndexSearcher {

    private static final Logger LOGGER = Logger.getLogger(ChmIndexSearcher.class.getName());

    private ChmFile chmFile;
    private String text;
    private byte[] textAsBytes;
    private HashMap<String, String> results;
    private ChmUnitInfo uiMain;
    private ChmUnitInfo uiTopics;
    private ChmUnitInfo uiUrlTbl;
    private ChmUnitInfo uiStrings;
    private ChmUnitInfo uiUrlStr;
    private ChmFtsHeader header;

    public ChmIndexSearcher(ChmFile chmFile) {
        this.chmFile = chmFile;
    }

    public HashMap<String, String> getResults() {
        return results;
    }

    public void search(String keyword, boolean wholeWords,
            boolean titlesOnly) throws IOException {

        byte word_len, pos;
        String word = null;
        byte[] wordBytes = null;

        if (keyword.equals("")) {
            return;
        }

        text = keyword.toLowerCase();
        textAsBytes = text.getBytes(chmFile.codec);

        LOGGER.log(Level.FINE, " <=> query " + text);

        uiMain = chmFile.resolveObject("/$FIftiMain");
        uiTopics = chmFile.resolveObject("/#TOPICS");
        uiUrlTbl = chmFile.resolveObject("/#URLTBL");
        uiStrings = chmFile.resolveObject("/#STRINGS");
        uiUrlStr = chmFile.resolveObject("/#URLSTR");

        if (uiMain == null || uiTopics == null || uiUrlTbl == null
                || uiStrings == null || uiUrlStr == null) {
            LOGGER.log(Level.FINE, "This CHM file is unsearchable.");
            return;
        }

        ByteBuffer buf = chmFile.retrieveObject(uiMain,
                0,
                ChmFile.FTS_HEADER_LEN);
        if (buf == null) {
            return;
        }
        buf.order(ByteOrder.LITTLE_ENDIAN);
        header = new ChmFtsHeader(buf);

        if (header.doc_index_s != 2 || header.code_count_s != 2
                || header.loc_codes_s != 2) {
            return;
        }

        int node_offset = getLeafNodeOffset();
        if (node_offset == 0) {
            return;
        }

        do {
            // get a leaf node here
            buf = chmFile.retrieveObject(uiMain,
                    node_offset,
                    header.node_len);
            if (buf == null) {
                return;
            }
            buf.order(ByteOrder.LITTLE_ENDIAN);

            long wlc_count, wlc_size;
            int wlc_offset;

            // The leaf nodes begin with a short header,
            // which is followed by entries:

            // Leaf node header
            // Offset Type Comment/Value
            // 0 DWORD Offset to the next leaf node.
            // 0 if this is the last leaf node.
            // 4 WORD 0 (unknown)
            // 6 WORD Length of free space at the end
            // of the current leaf node.
            node_offset = buf.getInt();
            buf.getShort();
            short free_space = buf.getShort();
            buf.limit(header.node_len - free_space);
            while (buf.hasRemaining()) {
                word_len = buf.get();
                pos = buf.get();

                byte[] tempWordBytes = new byte[word_len - 1];
                buf.get(tempWordBytes);

                if (pos == 0) {
                    wordBytes = tempWordBytes;
                    word = new String(tempWordBytes, chmFile.codec);
                } else {
                    byte[] newWordBytes = new byte[pos + word_len - 1];
                    int j;
                    for (j = 0; j < pos; j++) {
                        assert wordBytes != null;
                        newWordBytes[j] = wordBytes[j];
                    }
                    for (j = 0; j < word_len - 1; j++) {
                        newWordBytes[pos + j] = tempWordBytes[j];
                    }
                    wordBytes = newWordBytes;
                    word = new String(newWordBytes, chmFile.codec);
                }

                LOGGER.log(Level.FINE, " <=> word " + word);

                // Context (0 for body tag, 1 for title tag)
                byte context = buf.get();
                wlc_count = ByteBufferHelper.parseCWord(buf);
                wlc_offset = buf.getInt();
                buf.getShort();
                wlc_size = ByteBufferHelper.parseCWord(buf);

                if ((context == 0) && titlesOnly) {
                    continue;
                }

                // if (wholeWords && text.compareToIgnoreCase(word) == 0 ) {
                // int cmpResult = GBKHelper.compare(text, word);
                int cmpResult = GBKHelper.compareBytes(textAsBytes, wordBytes);
                if (cmpResult == 0) {
                    LOGGER.log(Level.FINE, "!" + word + "!");
                    ProcessWLC(wlc_count, wlc_size, wlc_offset);
                    return;
                } else {
                    if (cmpResult < 0) {
                        return;
                    }
                }

                if (!wholeWords && word.startsWith(text)) {
                    ProcessWLC(wlc_count, wlc_size, wlc_offset);
                }
            }
        }
        while (!wholeWords && word != null && word.startsWith(text)
                && node_offset != 0);

    }

    private void ProcessWLC(long wlc_count, long wlc_size,
            int wlc_offset) throws IOException {
        int wlc_bit = 7;  // FIXME: const?
        long index = 0, count;
        int off = 0;
        int stroff, urloff;
        int j;
        byte tmp;

        ByteBuffer buffer = chmFile.retrieveObject(uiMain, wlc_offset, wlc_size);
        if (buffer == null) {
            LOGGER.log(Level.FINE, "Can't retrieve object:" + uiMain.path);
            return;
        }
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        for (long i = 0; i < wlc_count; i++) {
            if (wlc_bit != 7) {
                ++off;
                wlc_bit = 7;
            }

            BitReader bitReader = new BitReader(buffer, false);
            index += ByteBufferHelper.sr_int(bitReader,
                    wlc_bit,
                    header.doc_index_s,
                    header.doc_index_r);
            ByteBuffer entry = chmFile.retrieveObject(uiTopics, index * 16, 16);
            if (entry == null) {
                LOGGER.log(Level.FINE, "Can't retrieve object:"
                        + uiTopics.path);
                return;
            }
            entry.order(ByteOrder.LITTLE_ENDIAN);
            entry.getInt();
            stroff = entry.getInt();

            String topic;

            ByteBuffer combuf = chmFile.retrieveObject(uiStrings, stroff, 1024);
            if (combuf == null) {
                topic = null;
            } else {
                int size = combuf.capacity();
                byte[] bytebuf = new byte[size];
                j = 0;
                while ((tmp = combuf.get()) != 0) {
                    bytebuf[j] = tmp;
                    j++;
                }
                topic = new String(bytebuf, 0, j, chmFile.codec);
            }

            urloff = entry.getInt();

            combuf = chmFile.retrieveObject(uiUrlTbl, urloff, 12);
            if (combuf == null) {
                return;
            }

            combuf.order(ByteOrder.LITTLE_ENDIAN);
            combuf.getInt();
            combuf.getInt();
            urloff = combuf.getInt();

            combuf = chmFile.retrieveObject(uiUrlStr,
                    urloff + 8,
                    1024);
            if (combuf == null) {
                return;
            }

            byte[] bytebuf = new byte[1024];
            j = 0;
            while ((tmp = combuf.get()) != 0) {
                bytebuf[j] = tmp;
                j++;
            }
            String url = new String(bytebuf, 0, j, chmFile.codec);

            if (topic == null || topic.length() == 0) {
                topic = url;
            }

            if (!url.equals("") && !topic.equals("")) {
                if (!addResult(url, topic)) {
                    return;
                }
            }

            count = ByteBufferHelper.sr_int(bitReader,
                    wlc_bit,
                    header.code_count_s,
                    header.code_count_r);
            for (j = 0; j < count; j++) {
                ByteBufferHelper.sr_int(bitReader,
                        wlc_bit,
                        header.loc_codes_s,
                        header.loc_codes_r);
            }
        }
    }

    // FIXME: some codes duplicated with ProcessWLC
    private int getLeafNodeOffset() throws IOException {
        int test_offset = 0;
        byte word_len, pos;
        String word = null;
        byte[] wordBytes = null;
        int initialOffset = header.node_offset;
        int buffSize = header.node_len;
        short treeDepth = header.tree_depth;

        while ((--treeDepth) != 0) {
            if (initialOffset == test_offset) {
                return 0;
            }

            test_offset = initialOffset;

            ByteBuffer buf = chmFile.retrieveObject(uiMain,
                    initialOffset,
                    buffSize);
            if (buf == null) {
                return 0;
            }
            buf.order(ByteOrder.LITTLE_ENDIAN);

            // The index nodes begin with a WORD indicating the
            // length of free space at the end of the node.
            // This is followed by the entries, which fill up
            // as much of the index node as possible.
            short free_space = buf.getShort();
            buf.limit(buffSize - free_space);
            while (buf.hasRemaining()) {
                word_len = buf.get();
                pos = buf.get();

                byte[] tempWordBytes = new byte[word_len - 1];
                buf.get(tempWordBytes);

                if (pos == 0) {
                    wordBytes = tempWordBytes;
                    word = new String(wordBytes, chmFile.codec);
                } else {
                    assert word != null && wordBytes != null;
                    byte[] newWordBytes = new byte[tempWordBytes.length + pos];
                    System.arraycopy(wordBytes, 0, newWordBytes, 0, pos);
                    System.arraycopy(tempWordBytes, 0, newWordBytes, pos, tempWordBytes.length);
                    wordBytes = newWordBytes;
                    word = new String(wordBytes, chmFile.codec);
                }

                LOGGER.log(Level.FINE, " <=> word " + word);

                int cmpResult = GBKHelper.compareBytes(textAsBytes, wordBytes);
                if (cmpResult <= 0) {
                    LOGGER.log(Level.FINE, "!!" + word);
                    initialOffset = buf.getInt();
                    break;
                }

                buf.getInt();
                buf.getShort();
            }
        }

        if (initialOffset == test_offset) {
            return 0;
        }

        return initialOffset;
    }

    private boolean addResult(String url, String topic) {
        if (results == null) {
            results = new LinkedHashMap<String, String>();
        }
        if (results.size() < 100) {
            results.put(url, topic);
            return true;
        } else {
            LOGGER.log(Level.FINE, "Too many results.");
            return false;
        }
    }

}
