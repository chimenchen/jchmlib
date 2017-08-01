package org.jchmlib;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Search a CHM file using its built-in full-text-search index.
 * <pre>
 * {@code
 * ChmFile chmFile = new ChmFile("test.chm");
 * ChmIndexSearcher searcher = chmFile.getIndexSearcher();
 * if (!searcher.notSearchable) {
 *     searcher.search("hello", true, true);
 *     HashMap<String, String> results = searcher.getResults();
 *     ...
 * }
 * }
 * </pre>
 */
public class ChmIndexSearcher {

    private static final Logger LOG = Logger.getLogger(ChmIndexSearcher.class.getName());

    private final ChmFile chmFile;
    /**
     * not searchable when this CHM files has built-in full-text-search index.
     */
    public boolean notSearchable = false;
    private ChmUnitInfo uiMain;
    private ChmUnitInfo uiTopics;
    private ChmUnitInfo uiUrlTbl;
    private ChmUnitInfo uiStrings;
    private ChmUnitInfo uiUrlStr;
    private ChmFtsHeader ftsHeader = null;
    private WordBuilder wordBuilder = null;
    private LinkedHashMap<String, IndexSearchResult> results;
    private int subQueryStep;
    private SubQuery subQuery;

    public ChmIndexSearcher(ChmFile chmFile) {
        this.chmFile = chmFile;
        // so as to set notSearchable
        search("jchmlib", true, true);
    }

    /**
     * Get search results matching the query.
     *
     * @return a hash map from url to title, or null if the CHM file is has no built-in index
     * ({@code notSearchable == false}) or there is no result found.
     */
    public HashMap<String, String> getResults() {
        if (results == null || results.size() == 0) {
            return null;
        }

        ArrayList<IndexSearchResult> resultList;
        resultList = new ArrayList<IndexSearchResult>(results.values());

        resultList.sort(new Comparator<IndexSearchResult>() {
            @Override
            public int compare(IndexSearchResult r1, IndexSearchResult r2) {
                return new Integer(r2.totalFrequency).compareTo(r1.totalFrequency);
            }
        });

        HashMap<String, String> finalResults = new LinkedHashMap<String, String>();
        for (IndexSearchResult result : resultList) {
            LOG.fine("#" + result.totalFrequency + " " + result.topic + " <=> " + result.url);
            finalResults.put(result.url, result.topic);
        }
        return finalResults;
    }

    private ArrayList<SubQuery> splitQuery(String originalQuery) {
        ArrayList<SubQuery> queryList = new ArrayList<SubQuery>();
        StringBuilder sb = new StringBuilder();
        boolean lastIsMultibyte = false;
        for (char c : originalQuery.toCharArray()) {
            byte[] bytesForChar;
            try {
                bytesForChar = String.valueOf(c).getBytes(chmFile.encoding);
            } catch (UnsupportedEncodingException e) {
                LOG.info("invalid char " + e);
                continue;
            }

            if (bytesForChar.length > 1) {
                if (sb.length() > 0) {
                    queryList.add(new SubQuery(sb.toString(), true));
                    sb = new StringBuilder();
                }
                queryList.add(new SubQuery(String.valueOf(c), !lastIsMultibyte));
                lastIsMultibyte = true;
            } else if (c == ' ') {
                if (sb.length() > 0) {
                    queryList.add(new SubQuery(sb.toString(), true));
                    sb = new StringBuilder();
                }
                lastIsMultibyte = false;
            } else {
                sb.append(c);
                lastIsMultibyte = false;
            }
        }
        if (sb.length() > 0) {
            queryList.add(new SubQuery(sb.toString(), true));
        }
        return queryList;
    }

    // there are words for CJK characters (each with only one CJK characters),
    // rather than for CJK words (each with multiple CJK characters).
    // to solve this problem, each multibyte char is made a sub-query.
    // that is, we search each multibyte char and combine the results.

    /**
     * @param query one or more words to search in CHM units/objects. CHM units matching the query
     * should include all the words. words are separated by whitespaces.
     * @param wholeWords if true, only consider keywords in builtin index that match a query word
     * exactly. it doesn't apply for CJK query words, since CJK keyword is normally one CJK
     * character. the search can handle CJK query word by searching each CJK character and checking
     * the locations in CHM units.
     * @param titlesOnly search in titles only
     */
    public void search(String query, boolean wholeWords, boolean titlesOnly) {
        results = null;
        if (notSearchable || query == null || query.equals("")) {
            return;
        }
        LOG.info(" <=> query " + query);

        ArrayList<SubQuery> queryList = splitQuery(query);
        for (int i = 0; i < queryList.size(); i++) {
            subQueryStep = i;
            subQuery = queryList.get(i);
            try {
                searchWithoutCatch(subQuery.queryString, wholeWords, titlesOnly);
            } catch (Exception e) {
                LOG.info("Error in index search" + e);
            }

            if (results == null || results.size() == 0) {
                return;
            }
            if (i == 0) {
                continue;
            }

            int expectedHitCount = i + 1;

            Iterator<Entry<String, IndexSearchResult>> entryIterator;
            entryIterator = results.entrySet().iterator();
            while (entryIterator.hasNext()) {
                Map.Entry<String, IndexSearchResult> entry = entryIterator.next();
                if (entry.getValue().hitCount < expectedHitCount) {
                    entryIterator.remove();
                }
            }
        }
    }

    private void searchWithoutCatch(String query, boolean wholeWords, boolean titlesOnly)
            throws IOException {
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
                wordBuilder = new WordBuilder(ftsHeader.maxWordLen, chmFile.encoding);
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
            Set<Long> locationCodes = new LinkedHashSet<Long>();
            long lastLocationCode = 0;
            for (int j = 0; j < locationCodeCount; j++) {
                long locationCode = bitReader.getSrInt(ftsHeader.locCodesS, ftsHeader.locCodesR);
                locationCode += lastLocationCode;
                locationCodes.add(locationCode);
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
                if (!addResult(url, topic, locationCodes)) {
                    return;
                }
            }

        }
    }

    private boolean addResult(String url, String topic, Set<Long> locationCodes) {
        if (subQueryStep > 0 && (results == null || !results.containsKey(url))) {
            return true;
        }

        if (results == null || results.size() < 300) {
            if (results == null) {
                results = new LinkedHashMap<String, IndexSearchResult>();
            }

            IndexSearchResult result;
            if (results.containsKey(url)) {
                result = results.get(url);
                // handle multibyte word (like CJK word)
                // check the current char is right after the last char in the file.
                if (!subQuery.isNewWord) {
                    Set<Long> newLocationCodes = new HashSet<Long>();
                    for (Long location : locationCodes) {
                        Long lastLocation = location - 1;
                        if (result.locationCodes.contains(lastLocation)) {
                            newLocationCodes.add(location);
                            break;
                        }
                    }
                    LOG.fine(String.format("subQuery(%d) %s: %s %s",
                            subQueryStep, subQuery.queryString, url, topic));
                    LOG.fine(result.locationCodes.toString());
                    LOG.fine(locationCodes.toString());
                    if (newLocationCodes.size() > 0) {
                        result.locationCodes = newLocationCodes;
                        result.totalFrequency += newLocationCodes.size();
                        result.hitCount += 1;
                        LOG.fine(result.locationCodes.toString());
                        LOG.fine("match");
                    } else {
                        LOG.fine("NO MATCH");
                    }
                } else {
                    result.totalFrequency += locationCodes.size();
                    result.locationCodes = locationCodes;
                    result.hitCount += 1;
                }
            } else {
                result = new IndexSearchResult();
                result.url = url;
                result.topic = topic;
                result.totalFrequency = locationCodes.size();
                result.locationCodes = locationCodes;
                result.hitCount = 1;
                results.put(url, result);
                LOG.fine(String.format("subQuery(%d) %s:", subQueryStep, subQuery.queryString));
                LOG.fine("locations of " + url + "  " + topic);
                LOG.fine(locationCodes.toString());
            }
            return true;
        } else {
            LOG.fine("Too many results.");
            return false;
        }
    }

    class SubQuery {

        final String queryString;
        // used for multibyte word
        // to ensure the word do exist in the file
        final boolean isNewWord;

        public SubQuery(String queryString, boolean isNewWord) {
            this.queryString = queryString;
            this.isNewWord = isNewWord;
        }
    }

    class IndexSearchResult {

        String url;
        String topic;
        Set<Long> locationCodes;
        int totalFrequency;
        int hitCount;
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