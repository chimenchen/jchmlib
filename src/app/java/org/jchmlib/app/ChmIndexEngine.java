package org.jchmlib.app;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.jchmlib.AbstractIndexSearcher;
import org.jchmlib.ChmCollectFilesEnumerator;
import org.jchmlib.ChmFile;
import org.jchmlib.ChmUnitInfo;

public class ChmIndexEngine extends AbstractIndexSearcher {

    private static final Logger LOG = Logger.getLogger(ChmIndexEngine.class.getName());

    private final AtomicReference<Integer> buildIndexStep = new AtomicReference<Integer>(-1);
    private final Set<String> textExtensions;
    private final Set<String> highFreqWords = new HashSet<String>();
    private IndexLoader loader = null;
    private ChmFile chmFile = null;
    private String chmFilePath = "";

    public ChmIndexEngine(ChmFile chmFile, String chmFilePath) {
        this.chmFile = chmFile;
        this.chmFilePath = chmFilePath;

        wordChars = "$_'";

        textExtensions = new HashSet<String>();
        textExtensions.add(".txt");
        textExtensions.add(".htm");
        textExtensions.add(".html");
        textExtensions.add(".xml");
        textExtensions.add(".xhtml");
    }

    public void close() {
        try {
            buildIndexStep.set(-1);
        } catch (Exception ignored) {
        }
    }

    private void addToWord(char c, StringBuilder sbWord, List<String> words) {
        boolean isMB = isMultibyteChar(c);
        boolean isWordChar = Character.isLetterOrDigit(c) || wordChars.indexOf(c) >= 0;
        if (!isMB && isWordChar) {
            sbWord.append(c);
        } else {
            if (sbWord.length() > 0) {
                String word = sbWord.toString().toLowerCase();
                if (!stopWords.contains(word)) {
                    words.add(word);
                }
                sbWord.setLength(0);
            }
            if (isMB && isWordChar) {
                String word = String.valueOf(c);
                if (!stopWords.contains(word)) {
                    words.add(word);
                }
            }
        }
    }

    private List<String> parse(String origin) {
        List<String> words = new ArrayList<String>();

        ParseState state = ParseState.OUTSIDE_TAGS;
        StringBuilder sbEntity = new StringBuilder();
        StringBuilder sbWord = new StringBuilder();

        char quoteChar = '"';
        for (int j = 0; j < origin.length(); j++) {
            char c = origin.charAt(j);
            switch (state) {
                case IN_HTML_TAG:
                    if (c == '"' || c == '\'') {
                        state = ParseState.IN_QUOTES;
                        quoteChar = c;
                    } else if (c == '>') {
                        state = ParseState.OUTSIDE_TAGS;
                    }
                    break;
                case IN_QUOTES:
                    if (c == quoteChar) {
                        state = ParseState.IN_HTML_TAG;
                    }
                    break;
                case IN_HTML_ENTITY:
                    if (Character.isLetterOrDigit(c) || (sbEntity.length() == 1 && c == '#')) {
                        sbEntity.append(c);
                        break;
                    }

                    state = ParseState.OUTSIDE_TAGS;

                    if (c != ';' && c != '<') {
                        if (sbEntity.length() <= 1) {
                            addToWord('&', sbWord, words);
                        }
                        j--; // parse this character again, but in different state
                        break;
                    }

                    if (c == ';') {
                        sbEntity.append(c);
                    }
                    String entity = HtmlEntityParser.parse(sbEntity.toString());
                    if (entity != null) {
                        for (char c2 : entity.toCharArray()) {
                            addToWord(c2, sbWord, words);
                        }
                    }
                    break;
                case OUTSIDE_TAGS:
                    if (c == '<') {
                        state = ParseState.IN_HTML_TAG;
                        addToWord(' ', sbWord, words);
                    } else if (c == '&') {
                        state = ParseState.IN_HTML_ENTITY;
                        sbEntity.setLength(0);
                        sbEntity.append(c);
                    } else {
                        addToWord(c, sbWord, words);
                    }
                    break;
            }
        }

        addToWord(' ', sbWord, words);

        return words;
    }

    public boolean isSearchable() {
        return buildIndexStep.get() == 100;
    }

    public int getBuildIndexStep() {
        return buildIndexStep.get();
    }

    private boolean isTextFile(ChmUnitInfo ui) {
        String path = ui.getPath();
        int indexDot = ui.getPath().lastIndexOf(".");
        if (indexDot != -1) {
            String ext = path.substring(indexDot).toLowerCase();
            return textExtensions.contains(ext);
        }
        return false;
    }

    public void buildIndex() {
        try {
            buildIndexWithoutCatch();
        } catch (Throwable ignored) {
            LOG.info("Error building index: " + ignored);
            buildIndexStep.set(-1);
        }
    }

    private void buildIndexWithoutCatch() throws IOException {
        if (buildIndexStep.get() >= 0) {
            return;
        }

        if (readIndex()) {
            return;
        }

        LOG.info("Building index for " + chmFile.getTitle());
        buildIndexStep.set(0);

        ChmCollectFilesEnumerator enumerator = new ChmCollectFilesEnumerator();
        chmFile.enumerate(ChmFile.CHM_ENUMERATE_USER, enumerator);

        int totalFileCount = enumerator.files.size();
        LOG.info("files count: " + totalFileCount);
        int perStep = Math.max(totalFileCount / 100, 1);

        HashMap<String, DocumentsForWord> wordToDocuments = new HashMap<String, DocumentsForWord>();
        HashMap<Integer, String> docIdToUrl = new LinkedHashMap<Integer, String>();

        int filesProcessed = -1;
        int docID = -1;
        int partitionID = -1;
        int partitionDocCount = 0;
        long partitionWordCount = 0;

        for (ChmUnitInfo ui : enumerator.files) {
            filesProcessed++;

            if (buildIndexStep.get() < 0) { // interrupted
                break;
            }
            if (filesProcessed % perStep == 0) {
                buildIndexStep.set(Math.min(buildIndexStep.get() + 1, 99));
                LOG.info("Building index step " + buildIndexStep.get());
                LOG.info("total word count " + partitionWordCount);
                LOG.info("filesProcessed " + filesProcessed + "/" + totalFileCount + " docID "
                        + docID);

                if (partitionDocCount >= 5000 || partitionWordCount >= 10000000) { // FIXME
                    partitionID++;
                    saveIndexPartition(partitionID, docIdToUrl, wordToDocuments);
                    wordToDocuments = new LinkedHashMap<String, DocumentsForWord>();
                    docIdToUrl = new LinkedHashMap<Integer, String>();
                    partitionDocCount = 0;
                    partitionWordCount = 0;
                }
            }

            if (!isTextFile(ui)) {
                continue;
            }

            String content = chmFile.retrieveObjectAsString(ui);
            if (content == null || content.length() == 0) {
                continue;
            }

            List<String> words = parse(content);
            if (words.size() == 0) {
                continue;
            }
            partitionWordCount += words.size();

            partitionDocCount++;
            docID++;
            docIdToUrl.put(docID, ui.getPath());

            HashMap<String, LocationsInDocument> wordToLocations = new HashMap<String, LocationsInDocument>();
            int wordLocation = -1;
            for (String word : words) {
                wordLocation++;

                if (word.length() > 16 || stopWords.contains(word)) {
                    continue;
                }

                LocationsInDocument locationsInDocument;
                if (wordToLocations.containsKey(word)) {
                    locationsInDocument = wordToLocations.get(word);
                } else {
                    locationsInDocument = new LocationsInDocument(docID, ui.getPath());
                    wordToLocations.put(word, locationsInDocument);
                }
                if (!highFreqWords.contains(word)) {
                    locationsInDocument.locations.add(wordLocation);
                }
                locationsInDocument.totalFrequency += 1;
            }

            for (Entry<String, LocationsInDocument> entry : wordToLocations.entrySet()) {
                String word = entry.getKey();
                LocationsInDocument locationsInDocument = entry.getValue();

                if (locationsInDocument.locations.size() > 500) {
                    locationsInDocument.locations.clear();
                }

                DocumentsForWord documentsForWord;
                if (wordToDocuments.containsKey(word)) {
                    documentsForWord = wordToDocuments.get(word);

                } else {
                    documentsForWord = new DocumentsForWord();
                    wordToDocuments.put(word, documentsForWord);
                }
                documentsForWord.documents.add(locationsInDocument);

                // ignore locations of high frequency word
                int wordDocCount = documentsForWord.documents.size();
                if (wordDocCount > 1000 && wordDocCount >= partitionDocCount * 0.95) {
                    // LOG.fine(String.format("high frequency word %s, %d/%d",
                    // word, wordDocCount, partitionDocCount));
                    for (LocationsInDocument lid : documentsForWord.documents) {
                        lid.locations.clear();
                    }
                    highFreqWords.add(word);
                }
            }
        }

        partitionID++;
        saveIndexPartition(partitionID, docIdToUrl, wordToDocuments);

        mergeIndexPartitions(partitionID + 1);

        LOG.info("Finished building index for " + chmFile.getTitle());

        readIndex();
    }

    private Set<Integer> getLocations(String targetWord, String url) {
        Set<Integer> locations = new HashSet<Integer>();

        ChmUnitInfo ui = chmFile.resolveObject(url);
        if (ui == null) {
            return locations;
        }

        String content = chmFile.retrieveObjectAsString(ui);
        if (content == null || content.length() == 0) {
            return locations;
        }

        List<String> words = parse(content);
        if (words.size() == 0) {
            return locations;
        }

        int wordLocation = -1;
        for (String word : words) {
            wordLocation++;
            if (word.equals(targetWord)) {
                locations.add(wordLocation);
            }
        }

        return locations;
    }

    protected Set<String> getInitialResults(List<SubQuery> subQueries) {
        List<String> words = new ArrayList<String>();
        for (SubQuery subQuery : subQueries) {
            words.add(subQuery.queryString);
        }

        Collections.sort(words, new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return new Integer(loader.getDocCount(o1)).compareTo(loader.getDocCount(o2));
            }
        });

        Set<String> results = new HashSet<String>();
        int subQueryStep = -1;
        for (String word : words) {
            subQueryStep++;

            DocumentsForWord documentsForWord = loader.loadDocumentsForWord(word);
            if (subQueryStep == 0) {
                if (documentsForWord != null) {
                    for (LocationsInDocument lid : documentsForWord.documents) {
                        results.add(lid.url);
                    }
                } else {
                    return results;
                }
            } else {
                if (documentsForWord == null) {
                    results.clear();
                    return results;
                } else {
                    Set<String> newResults = new HashSet<String>();
                    for (LocationsInDocument lid : documentsForWord.documents) {
                        if (results.contains(lid.url)) {
                            newResults.add(lid.url);
                        }
                    }
                    results = newResults;
                }
            }
        }
        return results;
    }

    //FIXME: support partial word and title only search
    @Override
    protected List<SearchResult> searchSingleWord(
            String word, boolean wholeWords, boolean titlesOnly, Set<String> lastRunFiles) {
        DocumentsForWord documentsForWord = loader.loadDocumentsForWord(word);
        if (documentsForWord == null) {
            return null;
        }

        List<SearchResult> results = new ArrayList<SearchResult>();

        for (LocationsInDocument lid : documentsForWord.documents) {
            String url = lid.url;
            if (lastRunFiles.size() > 0 && !lastRunFiles.contains(url)) {
                continue;
            }
            Set<Integer> locations =
                    lid.locations.size() == 0 ? getLocations(word, url) : lid.locations;
            results.add(new SearchResult(url, null, locations, lid.totalFrequency));
        }

        return results;
    }

    @Override
    protected void fixTopic(SearchResult result) {
        if (chmFile != null) {
            result.topic = chmFile.getTitleOfObject(result.url);
        } else {
            result.topic = result.url;
        }
    }

    private String getIndexFilePath() {
        String userHome = System.getProperty("user.home");
        File chmwebDir = new File(userHome, ".chmweb");
        //noinspection ResultOfMethodCallIgnored
        chmwebDir.mkdirs();

        File chm = new File(chmFilePath);
        File index = new File(chmwebDir, chm.getName() + ".index");
        return index.toString();
    }

    private void saveIndexPartition(int partitionID,
            HashMap<Integer, String> docIdToUrl,
            HashMap<String, DocumentsForWord> wordToDocuments) throws IOException {
        String path = getIndexFilePath() + "." + partitionID;
        LOG.info("Partition " + partitionID + " " + path);

        IndexWriter writer = new IndexWriter();
        writer.open(path);

        writer.writeDocId2Url(docIdToUrl);

        SortedSet<String> words = new TreeSet<String>(wordToDocuments.keySet());
        for (String word : words) {
            DocumentsForWord documentsForWord = wordToDocuments.get(word);
            writer.writeWord(word, documentsForWord);
        }
        writer.endLastWord();

        writer.writeDictionary();
        writer.close();
    }

    private void mergeIndexPartitions(int partitionCount) throws IOException {
        String path = getIndexFilePath();

        if (partitionCount == 1) {
            String partitionPath = path + ".0";
            File file1 = new File(partitionPath);
            File file2 = new File(path);
            //noinspection ResultOfMethodCallIgnored
            file1.renameTo(file2);
            return;
        }

        HashMap<Integer, String> docIdToUrl = new HashMap<Integer, String>();
        IndexLoader[] partitions = new IndexLoader[partitionCount];
        for (int partitionID = 0; partitionID < partitionCount; partitionID++) {
            String partitionPath = getIndexFilePath() + "." + partitionID;
            IndexLoader partition = new IndexLoader();
            partitions[partitionID] = partition;
            partition.open(partitionPath);

            for (Entry<Integer, String> entry : partition.docIdToUrl.entrySet()) {
                docIdToUrl.put(entry.getKey(), entry.getValue());
            }
        }

        IndexWriter writer = new IndexWriter();
        writer.open(path);

        writer.writeDocId2Url(docIdToUrl);

        int currentIndex = 0;
        String currentWord = "";
        while (currentIndex != -1) {
            currentIndex = -1;
            for (int k = 0; k < partitionCount; k++) {
                IndexLoader partition = partitions[k];
                if (partition.hasNext()) {
                    if (currentIndex == -1 || partition.getWord().compareTo(currentWord) < 0) {
                        currentIndex = k;
                        currentWord = partition.getWord();
                    }
                }
            }

            if (currentIndex != -1) {
                IndexLoader partition = partitions[currentIndex];
                int docCount = partition.getDocCount(currentWord);
                byte[] buf = partition.getPostings(currentWord);
                writer.writeWord(currentWord, docCount, buf);
                partition.advance();
            }
        }
        writer.endLastWord();

        writer.writeDictionary();
        writer.close();

        for (int k = 0; k < partitionCount; k++) {
            IndexLoader partition = partitions[k];
            partition.delete();
        }
    }

    public boolean readIndex() {
        try {
            readIndexWithoutCatch();
            return true;
        } catch (Throwable ignored) {
            LOG.fine("Error loading index from file: " + ignored);
            buildIndexStep.set(-1);
            return false;
        }
    }

    private void readIndexWithoutCatch() throws IOException {
        if (buildIndexStep.get() < 0) {
            buildIndexStep.set(0);
        }

        String path = getIndexFilePath();
        loader = new IndexLoader();
        loader.open(path);

        LOG.fine("Finished reading index.");

        buildIndexStep.set(100);
    }

    enum ParseState {
        OUTSIDE_TAGS, IN_HTML_TAG, IN_QUOTES, IN_HTML_ENTITY
    }

    class LocationsInDocument {

        final int docID;
        final String url;
        final Set<Integer> locations;
        int totalFrequency;

        LocationsInDocument(int docID, String url) {
            this.docID = docID;
            this.url = url;
            locations = new LinkedHashSet<Integer>();
            totalFrequency = 0;
        }
    }

    class DocumentsForWord {

        final List<LocationsInDocument> documents = new ArrayList<LocationsInDocument>();
    }

    class WordPostingInfo {

        int offset;
        int length;
        int docCount;

        WordPostingInfo() {
            offset = 0;
            length = 0;
        }
    }

    class IndexWriter {

        final HashMap<String, WordPostingInfo> wordToPostings;
        DataOutputStream out;
        int postingOffset;
        String lastWord;

        IndexWriter() {
            wordToPostings = new LinkedHashMap<String, WordPostingInfo>();

            lastWord = "";
        }

        void open(String filename) throws IOException {
            out = new DataOutputStream(new FileOutputStream(filename));

            out.writeInt(1); // version
        }

        void writeDocId2Url(HashMap<Integer, String> docIdToUrl) throws IOException {
            Varint.writeUnsignedVarInt(docIdToUrl.size(), out);
            for (Map.Entry<Integer, String> entry : docIdToUrl.entrySet()) {
                int docId = entry.getKey();
                String url = entry.getValue();
                Varint.writeUnsignedVarInt(docId, out);
                out.writeUTF(url);
            }

            postingOffset = out.size();
        }

        private void addWord(String word, int docCount) {
            if (!word.equals(lastWord)) {
                endLastWord();
                if (wordToPostings.containsKey(lastWord)) {
                    WordPostingInfo lastPostingInfo = wordToPostings.get(lastWord);
                    lastPostingInfo.length = out.size() - lastPostingInfo.offset;
                }
                WordPostingInfo postingInfo = new WordPostingInfo();
                postingInfo.offset = out.size();
                wordToPostings.put(word, postingInfo);
                postingInfo.docCount = docCount;

                lastWord = word;
            } else {
                WordPostingInfo postingInfo = wordToPostings.get(word);
                postingInfo.docCount += docCount;
            }
        }

        private void endLastWord() {
            if (lastWord.length() > 0 && wordToPostings.containsKey(lastWord)) {
                WordPostingInfo lastPostingInfo = wordToPostings.get(lastWord);
                lastPostingInfo.length = out.size() - lastPostingInfo.offset;
                // LOG.fine(String.format("Word %s: %d, %d, docCount=%d",
                //         lastWord, lastPostingInfo.offset, lastPostingInfo.length,
                //         lastPostingInfo.docCount));
            }
        }

        void writeWord(String word, DocumentsForWord documentsForWord) throws IOException {
            addWord(word, documentsForWord.documents.size());

            for (LocationsInDocument lid : documentsForWord.documents) {
                Varint.writeUnsignedVarInt(lid.docID, out);
                Varint.writeUnsignedVarInt(lid.totalFrequency, out);
                Varint.writeUnsignedVarInt(lid.locations.size(), out);
                int lastLoc = 0;
                for (Integer loc : lid.locations) {
                    int currentLoc = loc;
                    int relativeLoc = currentLoc - lastLoc;
                    Varint.writeUnsignedVarInt(relativeLoc, out);
                    lastLoc = currentLoc;
                }
            }
        }

        void writeWord(String word, int docCount, byte[] buf) throws IOException {
            addWord(word, docCount);
            out.write(buf);
        }

        void writeDictionary() throws IOException {
            int postingLength = out.size() - postingOffset;

            int dictionaryOffset = out.size();
            Varint.writeUnsignedVarInt(wordToPostings.size(), out);
            for (Entry<String, WordPostingInfo> it : wordToPostings.entrySet()) {
                String word = it.getKey();
                WordPostingInfo postingInfo = it.getValue();
                out.writeUTF(word);
                Varint.writeUnsignedVarInt(postingInfo.offset, out);
                Varint.writeUnsignedVarInt(postingInfo.length, out);
                Varint.writeUnsignedVarInt(postingInfo.docCount, out);
            }
            int dictionaryLength = out.size() - dictionaryOffset;

            out.writeInt(postingOffset);
            out.writeInt(postingLength);
            out.writeInt(dictionaryOffset);
            out.writeInt(dictionaryLength);
        }

        void close() throws IOException {
            out.close();
        }
    }

    @SuppressWarnings("unused")
    class IndexLoader {

        String filename;
        RandomAccessFile in;
        int postingOffset;
        int postingLength;
        int dictionaryOffset;
        int dictionaryLength;
        LinkedList<String> words;
        HashMap<String, WordPostingInfo> wordToPostings;
        HashMap<Integer, String> docIdToUrl;

        void open(String filename) throws IOException {
            this.filename = filename;
            in = new RandomAccessFile(filename, "r");

            docIdToUrl = new HashMap<Integer, String>();
            // in.seek(0);
            int version = in.readInt();
            assert version == 1;
            int docCount = Varint.readUnsignedVarInt(in);
            for (int i = 0; i < docCount; i++) {
                int docID = Varint.readUnsignedVarInt(in);
                String url = in.readUTF();
                docIdToUrl.put(docID, url);
            }

            in.seek(in.length() - 16);
            postingOffset = in.readInt();
            postingLength = in.readInt();
            dictionaryOffset = in.readInt();
            dictionaryLength = in.readInt();

            words = new LinkedList<String>();
            wordToPostings = new LinkedHashMap<String, WordPostingInfo>();
            in.seek(dictionaryOffset);
            int wordCount = Varint.readUnsignedVarInt(in);
            for (int i = 0; i < wordCount; i++) {
                String word = in.readUTF();
                WordPostingInfo postingInfo = new WordPostingInfo();
                postingInfo.offset = Varint.readUnsignedVarInt(in);
                postingInfo.length = Varint.readUnsignedVarInt(in);
                postingInfo.docCount = Varint.readUnsignedVarInt(in);
                wordToPostings.put(word, postingInfo);
                words.add(word);
            }
        }

        boolean hasNext() {
            return words.size() > 0;
        }

        void advance() {
            words.pop();
        }

        String getWord() {
            return words.getFirst();
        }

        int getDocCount(String word) {
            if (wordToPostings.containsKey(word)) {
                WordPostingInfo postingInfo = wordToPostings.get(word);
                return postingInfo.docCount;
            }
            return 0;
        }

        byte[] getPostings(String word) throws IOException {
            WordPostingInfo postingInfo = wordToPostings.get(word);
            in.seek(postingInfo.offset);
            byte[] buf = new byte[postingInfo.length];
            in.read(buf);
            return buf;
        }

        void delete() throws IOException {
            in.close();
            //noinspection ResultOfMethodCallIgnored
            new File(filename).delete();
        }

        DocumentsForWord loadDocumentsForWord(String word) {
            try {
                return loadDocumentsForWordWithoutCatch(word);
            } catch (IOException ignored) {
                return null;
            }
        }

        DocumentsForWord loadDocumentsForWordWithoutCatch(String word) throws IOException {
            if (!wordToPostings.containsKey(word)) {
                return null;
            }

            DocumentsForWord documentsForWord = new DocumentsForWord();

            WordPostingInfo postingInfo = wordToPostings.get(word);
            int offset = postingInfo.offset;
            int length = postingInfo.length;
            in.seek(offset);
            while (in.getFilePointer() < offset + length) {
                int docID = Varint.readUnsignedVarInt(in);
                String url = docIdToUrl.get(docID);

                LocationsInDocument locationsInDocument = new LocationsInDocument(docID, url);
                documentsForWord.documents.add(locationsInDocument);

                locationsInDocument.totalFrequency = Varint.readUnsignedVarInt(in);

                int locCount = Varint.readUnsignedVarInt(in);
                int lastLoc = 0;
                for (int k = 0; k < locCount; k++) {
                    int relativeLoc = Varint.readUnsignedVarInt(in);
                    int currentLoc = relativeLoc + lastLoc;
                    lastLoc = currentLoc;
                    locationsInDocument.locations.add(currentLoc);
                }

                if (locationsInDocument.totalFrequency > 0 && locCount == 0) {
                    highFreqWords.add(word);
                }
            }

            return documentsForWord;
        }
    }
}
