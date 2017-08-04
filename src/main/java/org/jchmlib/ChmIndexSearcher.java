package org.jchmlib;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Search a CHM file using its built-in full-text-search index.
 * <pre>
 * {@code
 * ChmFile chmFile = new ChmFile("test.chm");
 * ChmIndexSearcher searcher = chmFile.getIndexSearcher();
 * if (!searcher.notSearchable) {
 *     HashMap<String, String> urlToTopic = searcher.search("hello", true, true);
 *     ...
 * }
 * }
 * </pre>
 */
public class ChmIndexSearcher extends AbstractIndexSearcher {

    private static final Logger LOG = Logger.getLogger(ChmIndexSearcher.class.getName());

    private final ChmFile chmFile;
    private final ChmUnitInfo uiMain;
    private final ChmUnitInfo uiTopics;
    private final ChmUnitInfo uiUrlTbl;
    private final ChmUnitInfo uiStrings;
    private final ChmUnitInfo uiUrlStr;
    /**
     * not searchable when this CHM files has built-in full-text-search index.
     */
    public boolean notSearchable = false;
    private ChmFtsHeader ftsHeader = null;
    // private WordBuilder wordBuilder = null;
    // private int subQueryStep;
    // private SubQuery subQuery;

    public ChmIndexSearcher(ChmFile chmFile) {
        this.chmFile = chmFile;

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
        }
    }

    @Override
    protected void fixTopic(SearchResult result) {
    }

    // FIXME: fix javadoc
    /*
     * Get search results matching the query.
     *
     * @return a hash map from url to title, or null if the CHM file is has no built-in index
     * ({@code notSearchable == false}) or there is no result found.
     */

    // there are words for CJK characters (each with only one CJK characters),
    // rather than for CJK words (each with multiple CJK characters).
    // to solve this problem, each multibyte char is made a sub-query.
    // that is, we search each multibyte char and combine the results.

    /*
     * @param query one or more words to search in CHM units/objects. CHM units matching the query
     * should include all the words. words are separated by whitespaces.
     * @param wholeWords if true, only consider keywords in builtin index that match a query word
     * exactly. it doesn't apply for CJK query words, since CJK keyword is normally one CJK
     * character. the search can handle CJK query word by searching each CJK character and checking
     * the locations in CHM units.
     * @param titlesOnly search in titles only
     */
    @Override
    protected List<SearchResult> searchSingleWord(
            String query, boolean wholeWords, boolean titlesOnly, Set<String> lastRunFiles) {
        if (notSearchable || query == null || query.equals("")) {
            return null;
        }
        List<SearchResult> results = new ArrayList<SearchResult>();
        try {
            searchWithoutCatch(query, wholeWords, titlesOnly, results);
        } catch (IOException ignored) {
        }
        return results;
    }

    private void searchWithoutCatch(String query, boolean wholeWords, boolean titlesOnly,
            List<SearchResult> results) throws IOException {
        assert results != null;

        if (notSearchable || query == null || query.equals("")) {
            return;
        }

        LOG.info(" <=> sub query " + query);
        byte[] queryAsBytes;
        try {
            queryAsBytes = query.toLowerCase().getBytes(chmFile.encoding);
        } catch (UnsupportedEncodingException ignored) {
            LOG.info("failed to decode query: " + query);
            return;
        }

        if (queryAsBytes.length > query.length()) {
            wholeWords = false;
        }

        int nodeOffset = getLeafNodeOffset(queryAsBytes);
        if (nodeOffset <= 0) {
            return;
        }

        WordBuilder wordBuilder = createWordBuilder();
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
            wordBuilder.wordLength = 0;
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
                    ProcessWlcBlock(wlcCount, wlcSize, wlcOffset, results);
                    if (wholeWords) {
                        return;
                    }
                } else if (cmpResult > 0) {
                    if (!wholeWords && wordBuilder.startsWith(queryAsBytes)) {
                        ProcessWlcBlock(wlcCount, wlcSize, wlcOffset, results);
                    } else {
                        break;
                    }
                }
            }
        } while (!wholeWords && wordBuilder.wordLength > 0 &&
                wordBuilder.startsWith(queryAsBytes) && nodeOffset != 0);
    }

    private WordBuilder createWordBuilder() {
        assert ftsHeader != null && chmFile != null;
        return new WordBuilder(ftsHeader.maxWordLen, chmFile.encoding);
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

            WordBuilder wordBuilder = createWordBuilder();
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

    private void ProcessWlcBlock(long wlcCount, long wlcSize, int wlcOffset,
            List<SearchResult> results) {
        try {
            ProcessWlcBlockWithoutCatch(wlcCount, wlcSize, wlcOffset, results);
        } catch (Exception e) {
            LOG.info("Error processing WLC block: " + e);
        }
    }

    private void ProcessWlcBlockWithoutCatch(long wlcCount, long wlcSize, int wlcOffset,
            List<SearchResult> results) throws IOException {
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
            Set<Integer> locationCodes = new LinkedHashSet<Integer>();
            long lastLocationCode = 0;
            for (int j = 0; j < locationCodeCount; j++) {
                long locationCode = bitReader.getSrInt(ftsHeader.locCodesS, ftsHeader.locCodesR);
                locationCode += lastLocationCode;
                locationCodes.add((int) locationCode);
                lastLocationCode = locationCode;
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
                topic = ByteBufferHelper.parseString(bufStrings, chmFile.encoding);
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
            String url = ByteBufferHelper.parseString(bufUrlStr, chmFile.encoding);
            if (url == null) {
                return;
            }

            if (topic == null || topic.length() == 0) {
                topic = url;
            }

            if (!url.equals("") && !topic.equals("")) {
                addResult(url, topic, locationCodes, results);
            }
        }
    }

    private void addResult(String url, String topic, Set<Integer> locations,
            List<SearchResult> results) {
        assert results != null;
        results.add(new SearchResult("/" + url, topic, locations, locations.size()));
    }

    class WordBuilder {

        final byte[] wordBuffer;
        final String encoding;
        int wordLength;

        WordBuilder(int maxWordLength, String encoding) {
            wordBuffer = new byte[maxWordLength];
            wordLength = 0;
            this.encoding = encoding;
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
                return new String(wordBuffer, 0, wordLength, encoding);
            } catch (UnsupportedEncodingException ignored) {
                return "";
            }
        }
    }
}
