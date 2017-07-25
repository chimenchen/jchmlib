package org.jchmlib;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.logging.Logger;
import org.jchmlib.util.BitReader;
import org.jchmlib.util.ByteBufferHelper;


public class ChmIndexSearcher {

    private static final Logger LOG = Logger.getLogger(ChmIndexSearcher.class.getName());

    private final ChmFile chmFile;
    public boolean notSearchable = false;
    private ChmUnitInfo uiMain;
    private ChmUnitInfo uiTopics;
    private ChmUnitInfo uiUrlTbl;
    private ChmUnitInfo uiStrings;
    private ChmUnitInfo uiUrlStr;
    private ChmFtsHeader ftsHeader = null;
    private WordBuilder wordBuilder = null;
    private ArrayList<IndexSearchResult> results;
    private HashMap<String, Integer> urlToHitCount = null;


    public ChmIndexSearcher(ChmFile chmFile) {
        this.chmFile = chmFile;
    }

    public HashMap<String, String> getResults() {
        if (results == null || results.size() == 0) {
            return null;
        }

        results.sort(new Comparator<IndexSearchResult>() {
            @Override
            public int compare(IndexSearchResult r1, IndexSearchResult r2) {
                return new Integer(r2.count).compareTo(r1.count);
            }
        });

        HashMap<String, String> finalResults = new LinkedHashMap<String, String>();
        for (IndexSearchResult result : results) {
            LOG.fine("#" + result.count + " " + result.topic + " <=> " + result.url);
            finalResults.put(result.url, result.topic);
        }
        return finalResults;
    }

    // there are words for CJK characters (each with only one CJK characters),
    // rather than for CJK words (each with multiple CJK characters).
    // to solve this problem, each multibyte char is made a sub-query.
    // that is, we search each multibyte char and combine the results.

    private ArrayList<String> splitQuery(String originalQuery, String codec) {
        ArrayList<String> queryList = new ArrayList<String>();
        StringBuilder sb = new StringBuilder();
        for (char c : originalQuery.toCharArray()) {
            byte[] bytesForChar;
            try {
                bytesForChar = String.valueOf(c).getBytes(codec);
            } catch (UnsupportedEncodingException e) {
                LOG.info("invalid char " + e);
                continue;
            }
            if (bytesForChar.length > 1) {
                if (sb.length() > 0) {
                    queryList.add(sb.toString());
                    sb = new StringBuilder();
                }
                queryList.add(String.valueOf(c));
            } else if (c == ' ') {
                if (sb.length() > 0) {
                    queryList.add(sb.toString());
                    sb = new StringBuilder();
                }
            } else {
                sb.append(c);
            }
        }
        if (sb.length() > 0) {
            queryList.add(sb.toString());
        }
        return queryList;
    }

    public void search(String query, boolean wholeWords, boolean titlesOnly) {
        results = null;
        urlToHitCount = null;

        if (notSearchable || query == null || query.equals("")) {
            return;
        }
        LOG.info(" <=> query " + query);

        ArrayList<String> queryList = splitQuery(query, chmFile.codec);
        for (int i = 0; i < queryList.size(); i++) {
            String subQuery = queryList.get(i);
            try {
                searchWithoutCatch(subQuery, wholeWords, titlesOnly);
            } catch (Exception e) {
                LOG.info("Error in index search" + e);
            }

            if (i == 0 || results == null) {
                continue;
            }

            int expectedHitCount = i + 1;
            ArrayList<IndexSearchResult> newResults = new ArrayList<IndexSearchResult>();
            for (IndexSearchResult result : results) {
                if (urlToHitCount.containsKey(result.url) &&
                        urlToHitCount.get(result.url) == expectedHitCount) {
                    newResults.add(result);
                } else {
                    urlToHitCount.remove(result.url);
                }
            }
            results = newResults;
        }
    }

    private void searchWithoutCatch(String query, boolean wholeWords, boolean titlesOnly)
            throws IOException {
        LOG.info(" <=> sub query " + query);
        byte[] queryAsBytes;
        try {
            queryAsBytes = query.toLowerCase().getBytes(chmFile.codec);
        } catch (UnsupportedEncodingException ignored) {
            LOG.info("failed to decode query: " + query);
            return;
        }

        if (queryAsBytes.length > query.length()) {
            wholeWords = false;
        }

        uiMain = chmFile.resolveObject("/$FIftiMain");
        uiTopics = chmFile.resolveObject("/#TOPICS");
        uiUrlTbl = chmFile.resolveObject("/#URLTBL");
        uiStrings = chmFile.resolveObject("/#STRINGS");
        uiUrlStr = chmFile.resolveObject("/#URLSTR");

        if (uiMain == null || uiTopics == null || uiUrlTbl == null
                || uiStrings == null || uiUrlStr == null) {
            LOG.info("This CHM file is unsearchable.");
            notSearchable = true;
            return;
        }

        if (ftsHeader == null) {
            ByteBuffer bufFtsHeader = chmFile.retrieveObject(uiMain, 0, ChmFile.FTS_HEADER_LEN);
            if (bufFtsHeader == null) {
                LOG.info("Failed to get FTS header");
                notSearchable = true;
                return;
            }

            try {
                ftsHeader = new ChmFtsHeader(bufFtsHeader);
            } catch (IOException e) {
                LOG.info("Failed to parse FTS header." + e);
                return;
            }
            if (ftsHeader.docIndexS != 2 || ftsHeader.codeCountS != 2 || ftsHeader.locCodesS != 2) {
                LOG.info("Invalid s values in FTS header");
                notSearchable = true;
                return;
            }
        }

        int nodeOffset = getLeafNodeOffset(queryAsBytes);
        if (nodeOffset <= 0) {
            return;
        }

        do {
            // get a leaf node here
            ByteBuffer bufLeafNode = chmFile.retrieveObject(uiMain, nodeOffset, ftsHeader.nodeLen);
            if (bufLeafNode == null) {
                return;
            }

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
                LOG.fine(" <=> word " + wordBuilder.getWord());

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
                    LOG.fine("!found!");
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

    private void initWordBuilder() {
        if (wordBuilder == null) {
            if (ftsHeader != null && chmFile != null) {
                wordBuilder = new WordBuilder(ftsHeader.maxWordLen, chmFile.codec);
            }
        } else {
            wordBuilder.wordLength = 0;
        }
    }

    private int getLeafNodeOffset(byte[] queryAsBytes) {
        try {
            return getLeafNodeOffsetWithoutCatch(queryAsBytes);
        } catch (Exception e) {
            LOG.info("Failed to get leaf node offset" + e);
            return 0;
        }
    }

    private int getLeafNodeOffsetWithoutCatch(byte[] queryAsBytes) throws IOException {
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

            // the length of free space at the end of the node.
            short freeSpace = bufIndexNode.getShort();
            bufIndexNode.limit(buffSize - freeSpace);

            initWordBuilder();
            while (bufIndexNode.hasRemaining()) {
                wordBuilder.readWord(bufIndexNode);
                LOG.fine(" <=> word " + wordBuilder.getWord());

                int cmpResult = wordBuilder.compareWith(queryAsBytes);
                if (cmpResult >= 0) {
                    LOG.fine("!found index node");
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

    private void ProcessWlcBlock(long wlcCount, long wlcSize, int wlcOffset) {
        try {
            ProcessWlcBlockWithoutCatch(wlcCount, wlcSize, wlcOffset);
        } catch (Exception e) {
            LOG.info("Error processing WLC block: " + e);
        }
    }

    private void ProcessWlcBlockWithoutCatch(long wlcCount, long wlcSize, int wlcOffset)
            throws IOException {

        ByteBuffer bufWlcBlock = chmFile.retrieveObject(uiMain, wlcOffset, wlcSize);
        if (bufWlcBlock == null) {
            LOG.fine("Can't retrieve object:" + uiMain.path);
            return;
        }

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
                LOG.fine("Can't retrieve object:" + uiTopics.path);
                return;
            }
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
            results = new ArrayList<IndexSearchResult>();
        }
        if (urlToHitCount == null) {
            urlToHitCount = new HashMap<String, Integer>();
        }
        if (results.size() < 300) {
            // results.put(url, topic);
            IndexSearchResult result = new IndexSearchResult();
            result.url = url;
            result.topic = topic;
            result.count = count;
            results.add(result);

            if (urlToHitCount.containsKey(url)) {
                urlToHitCount.put(url, urlToHitCount.get(url) + 1);
            } else {
                urlToHitCount.put(url, 1);
            }

            return true;
        } else {
            LOG.fine("Too many results.");
            return false;
        }
    }

    class IndexSearchResult {

        String url;
        String topic;
        int count;
    }

    class WordBuilder {

        final byte[] wordBuffer;
        final String codec;
        int wordLength;

        WordBuilder(int maxWordLength, String codec) {
            wordBuffer = new byte[maxWordLength];
            wordLength = 0;
            this.codec = codec;
        }

        void readWord(ByteBuffer bb) throws IOException {
            int wordLen = bb.get();
            int pos = bb.get();
            readWord(bb, pos, wordLen);
        }

        void readWord(ByteBuffer bb, int pos, int len) throws IOException {
            len -= 1;
            try {
                bb.get(wordBuffer, pos, len);
            } catch (Exception e) {
                throw new IOException(e);
            }
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
