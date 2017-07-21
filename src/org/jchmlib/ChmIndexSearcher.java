package org.jchmlib;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jchmlib.util.BitReader;
import org.jchmlib.util.ByteBufferHelper;


public class ChmIndexSearcher {

    private static final Logger LOGGER = Logger.getLogger(ChmIndexSearcher.class.getName());

    private final ChmFile chmFile;
    public boolean notSearchable = false;
    private byte[] queryAsBytes;
    private ChmUnitInfo uiMain;
    private ChmUnitInfo uiTopics;
    private ChmUnitInfo uiUrlTbl;
    private ChmUnitInfo uiStrings;
    private ChmUnitInfo uiUrlStr;
    private ChmFtsHeader ftsHeader = null;
    private WordBuilder wordBuilder = null;
    private ArrayList<IndexSearchResult> results;

    public ChmIndexSearcher(ChmFile chmFile) {
        this.chmFile = chmFile;
    }

    public HashMap<String, String> getResults() {
        if (results == null) {
            return null;
        }

        results.sort(new Comparator<IndexSearchResult>() {
            @Override
            public int compare(IndexSearchResult r1, IndexSearchResult r2) {
                return new Integer(r2.count).compareTo(new Integer(r1.count));
            }
        });

        HashMap<String, String> finalResults = new LinkedHashMap<>();
        for (IndexSearchResult result : results) {
            LOGGER.log(Level.FINE, "#" + result.count + " " + result.topic + " <=> " + result.url);
            finalResults.put(result.url, result.topic);
        }
        return finalResults;
    }

    // FIXME: improve support for CJK.
    // there are words for CJK characters (each with only one CJK characters),
    // rather than for CJK words (each with multiple CJK characters).
    public void search(String query, boolean wholeWords, boolean titlesOnly) throws IOException {
        results = null;

        if (notSearchable) {
            return;
        }

        if (query == null || query.equals("")) {
            return;
        }
        LOGGER.log(Level.FINE, " <=> query " + query);
        queryAsBytes = query.toLowerCase().getBytes(chmFile.codec);

        uiMain = chmFile.resolveObject("/$FIftiMain");
        uiTopics = chmFile.resolveObject("/#TOPICS");
        uiUrlTbl = chmFile.resolveObject("/#URLTBL");
        uiStrings = chmFile.resolveObject("/#STRINGS");
        uiUrlStr = chmFile.resolveObject("/#URLSTR");

        if (uiMain == null || uiTopics == null || uiUrlTbl == null
                || uiStrings == null || uiUrlStr == null) {
            LOGGER.log(Level.FINE, "This CHM file is unsearchable.");
            notSearchable = true;
            return;
        }

        if (ftsHeader == null) {
            ByteBuffer bufFtsHeader = chmFile.retrieveObject(uiMain, 0, ChmFile.FTS_HEADER_LEN);
            if (bufFtsHeader == null) {
                notSearchable = true;
                return;
            }
            bufFtsHeader.order(ByteOrder.LITTLE_ENDIAN);

            ftsHeader = new ChmFtsHeader(bufFtsHeader);
            if (ftsHeader.docIndexS != 2 || ftsHeader.codeCountS != 2
                    || ftsHeader.locCodesS != 2) {
                notSearchable = true;
                return;
            }
        }

        int nodeOffset = getLeafNodeOffset();
        if (nodeOffset == 0) {
            return;
        }

        do {
            // get a leaf node here
            ByteBuffer bufLeafNode = chmFile.retrieveObject(uiMain, nodeOffset, ftsHeader.nodeLen);
            if (bufLeafNode == null) {
                return;
            }
            bufLeafNode.order(ByteOrder.LITTLE_ENDIAN);

            // Leaf node header
            nodeOffset = bufLeafNode.getInt();  // offset to the next leaf node
            ByteBufferHelper.skip(bufLeafNode, 2);
            // length of free space at the end of the current leaf node
            short freeSpace = bufLeafNode.getShort();

            bufLeafNode.limit(ftsHeader.nodeLen - freeSpace);
            initWordBuilder();
            while (bufLeafNode.hasRemaining()) {
                // get a word
                wordBuilder.readWord(bufLeafNode);
                LOGGER.log(Level.FINE, " <=> word " + wordBuilder.getWord());

                // Context (0 for body tag, 1 for title tag)
                byte context = bufLeafNode.get();
                long wlcCount = ByteBufferHelper.parseCWord(bufLeafNode);
                int wlcOffset = bufLeafNode.getInt();
                ByteBufferHelper.skip(bufLeafNode, 2);
                long wlcSize = ByteBufferHelper.parseCWord(bufLeafNode);

                if ((context == 0) && titlesOnly) {
                    continue;
                }

                int cmpResult = wordBuilder.compareWith(queryAsBytes);
                if (cmpResult == 0) {
                    LOGGER.log(Level.FINE, "!found!");
                    ProcessWlcBlock(wlcCount, wlcSize, wlcOffset);
                    return;
                } else if (cmpResult > 0) {
                    if (!wholeWords && wordBuilder.startsWith(queryAsBytes)) {
                        ProcessWlcBlock(wlcCount, wlcSize, wlcOffset);
                    } else {
                        return;
                    }
                }
            }
        } while (!wholeWords && wordBuilder.wordLength > 0 &&
                wordBuilder.startsWith(queryAsBytes) && nodeOffset != 0);
    }

    public void initWordBuilder() {
        if (wordBuilder == null) {
            if (ftsHeader != null && chmFile != null) {
                wordBuilder = new WordBuilder(ftsHeader.maxWordLen, chmFile.codec);
            }
        } else {
            wordBuilder.wordLength = 0;
        }
    }

    private int getLeafNodeOffset() throws IOException {
        int lastNodeOffset = 0;
        int nodeOffset = ftsHeader.nodeOffset;
        int buffSize = ftsHeader.nodeLen;
        short treeDepth = ftsHeader.treeDepth;

        while ((--treeDepth) != 0) {
            if (nodeOffset == lastNodeOffset) {
                return 0;
            }

            lastNodeOffset = nodeOffset;

            ByteBuffer bufIndexNode = chmFile.retrieveObject(uiMain, nodeOffset, buffSize);
            if (bufIndexNode == null) {
                return 0;
            }
            bufIndexNode.order(ByteOrder.LITTLE_ENDIAN);

            // the length of free space at the end of the node.
            short freeSpace = bufIndexNode.getShort();
            bufIndexNode.limit(buffSize - freeSpace);

            initWordBuilder();
            while (bufIndexNode.hasRemaining()) {
                wordBuilder.readWord(bufIndexNode);
                LOGGER.log(Level.FINE, " <=> word " + wordBuilder.getWord());

                int cmpResult = wordBuilder.compareWith(queryAsBytes);
                if (cmpResult >= 0) {
                    LOGGER.log(Level.FINE, "!found index node");
                    // Offset of the leaf node whose last entry is this word
                    nodeOffset = bufIndexNode.getInt();
                    break;
                } else {
                    ByteBufferHelper.skip(bufIndexNode, 6);
                }
            }
        }

        if (nodeOffset == lastNodeOffset) {
            return 0;
        }

        return nodeOffset;
    }

    private void ProcessWlcBlock(long wlcCount, long wlcSize, int wlcOffset) throws IOException {
        ByteBuffer bufWlcBlock = chmFile.retrieveObject(uiMain, wlcOffset, wlcSize);
        if (bufWlcBlock == null) {
            LOGGER.log(Level.FINE, "Can't retrieve object:" + uiMain.path);
            return;
        }
        bufWlcBlock.order(ByteOrder.LITTLE_ENDIAN);

        long docIndex = 0;
        for (long i = 0; i < wlcCount; i++) {
            BitReader bitReader = new BitReader(bufWlcBlock, false);
            docIndex += bitReader.getSrInt(ftsHeader.docIndexS, ftsHeader.docIndexR);

            // locations of the word in the topics
            long locationCodeCount = bitReader.getSrInt(
                    ftsHeader.codeCountS, ftsHeader.codeCountR);
            for (int j = 0; j < locationCodeCount; j++) {
                // move forward. result not used
                bitReader.getSrInt(ftsHeader.locCodesS, ftsHeader.locCodesR);
            }

            ByteBuffer entry = chmFile.retrieveObject(uiTopics, docIndex * 16, 16);
            if (entry == null) {
                LOGGER.log(Level.FINE, "Can't retrieve object:" + uiTopics.path);
                return;
            }
            entry.order(ByteOrder.LITTLE_ENDIAN);
            entry.getInt();
            int strOffset = entry.getInt();
            int urlOffset = entry.getInt();

            String topic;
            ByteBuffer bufStrings = chmFile.retrieveObject(uiStrings, strOffset, 1024);
            if (bufStrings == null) {
                topic = null;
            } else {
                topic = ByteBufferHelper.parseString(bufStrings, chmFile.codec);
            }

            ByteBuffer bufUrlTable = chmFile.retrieveObject(uiUrlTbl, urlOffset, 12);
            if (bufUrlTable == null) {
                return;
            }
            bufUrlTable.order(ByteOrder.LITTLE_ENDIAN);
            ByteBufferHelper.skip(bufUrlTable, 8); // bufUrlTable.getInt(); // bufUrlTable.getInt();
            int urlStrOffset = bufUrlTable.getInt();

            ByteBuffer bufUrlStr = chmFile.retrieveObject(uiUrlStr, urlStrOffset + 8, 1024);
            if (bufUrlStr == null) {
                return;
            }
            String url = ByteBufferHelper.parseString(bufUrlStr, chmFile.codec);
            if (url == null) {
                return;
            }

            if (topic == null || topic.length() == 0) {
                topic = url;
            }

            if (!url.equals("") && !topic.equals("")) {
                if (!addResult(url, topic, (int) locationCodeCount)) {
                    return;
                }
            }

        }
    }

    private boolean addResult(String url, String topic, int count) {
        if (results == null) {
            // results = new LinkedHashMap<>();
            results = new ArrayList<>();
        }
        if (results.size() < 100) {
            // results.put(url, topic);
            IndexSearchResult result = new IndexSearchResult();
            result.url = url;
            result.topic = topic;
            result.count = count;
            results.add(result);
            return true;
        } else {
            LOGGER.log(Level.FINE, "Too many results.");
            return false;
        }
    }

    class IndexSearchResult {

        String url;
        String topic;
        int count;
    }

    class WordBuilder {

        byte[] wordBuffer;
        int wordLength;
        String codec;

        WordBuilder(int maxWordLength, String codec) {
            wordBuffer = new byte[maxWordLength];
            wordLength = 0;
            this.codec = codec;
        }

        void readWord(ByteBuffer bb) {
            int wordLen = bb.get();
            int pos = bb.get();
            readWord(bb, pos, wordLen);
        }

        void readWord(ByteBuffer bb, int pos, int len) {
            len -= 1;
            bb.get(wordBuffer, pos, len);
            wordLength = pos + len;
        }

        private int toUInt8(byte x) {
            return ((int) x) & 0xff;
        }

        int compareWith(byte[] right) {
            for (int i = 0; i < wordLength && i < right.length; i++) {
                int byte1 = toUInt8(wordBuffer[i]);
                int byte2 = toUInt8(right[i]);
                if (byte1 < byte2) {
                    return -1;
                } else if (byte1 > byte2) {
                    return 1;
                }
            }
            if (wordLength < right.length) {
                return -1;
            } else if (wordLength > right.length) {
                return 1;
            } else {
                return 0;
            }
        }

        boolean startsWith(byte[] right) {
            if (right.length > wordLength) {
                return false;
            }
            for (int i = 0; i < right.length; i++) {
                int byte1 = toUInt8(wordBuffer[i]);
                int byte2 = toUInt8(right[i]);
                if (byte1 != byte2) {
                    return false;
                }
            }
            return true;
        }

        String getWord() {
            if (wordLength == 0) {
                return "";
            }
            try {
                return new String(wordBuffer, 0, wordLength, codec);
            } catch (UnsupportedEncodingException ignored) {
                return "";
            }
        }
    }
}
