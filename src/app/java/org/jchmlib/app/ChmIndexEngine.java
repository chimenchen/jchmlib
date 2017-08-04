package org.jchmlib.app;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

    // private int buildIndexStep = -1;
    private final AtomicReference<Integer> buildIndexStep = new AtomicReference<Integer>(-1);

    private final HashMap<String, DocumentsForWord> wordToDocuments = new HashMap<String, DocumentsForWord>();
    private final HashMap<Integer, String> docIdToUrl = new LinkedHashMap<Integer, String>();
    private final Set<String> textExtensions;
    private final Set<String> highFreqWords = new HashSet<String>();
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
            LOG.info("Error building index");
            buildIndexStep.set(-1);
            wordToDocuments.clear();
        }
    }

    private void buildIndexWithoutCatch() {
        if (buildIndexStep.get() >= 0) {
            return;
        }

        if (readIndex()) {
            return;
        }

        LOG.info("Building index for " + chmFile.getTitle());

        ChmCollectFilesEnumerator enumerator = new ChmCollectFilesEnumerator();
        chmFile.enumerate(ChmFile.CHM_ENUMERATE_USER, enumerator);

        int totalFileCount = enumerator.files.size();
        LOG.info("files count: " + totalFileCount);

        int perStep = Math.max(totalFileCount / 100, 1);
        buildIndexStep.set(0);

        int docID = -1;
        int filesProcessed = -1;
        for (ChmUnitInfo ui : enumerator.files) {
            filesProcessed++;
            if (filesProcessed % perStep == 0) {
                buildIndexStep.set(Math.min(buildIndexStep.get() + 1, 99));
                LOG.info("Building index step " + buildIndexStep.get());
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

            docID++;
            docIdToUrl.put(docID, ui.getPath());

            HashMap<String, LocationsInDocument> wordToLocations;
            wordToLocations = new HashMap<String, LocationsInDocument>();

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
                    locationsInDocument = new LocationsInDocument(docID, ui);
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
                int processedCount = docID + 1;
                if (wordDocCount > 2000 || (wordDocCount > 200 && (
                        wordDocCount > totalFileCount * 0.2 &&
                                wordDocCount >= processedCount * 0.9) || (
                        wordDocCount > totalFileCount * 0.3 &&
                                wordDocCount >= processedCount * 0.85) || (
                        wordDocCount > totalFileCount * 0.4
                                && wordDocCount >= processedCount * 0.8))) {
                    // LOG.info(String.format("high frequency %s, %d, %d", word, wordDocCount, docID));

                    for (LocationsInDocument lid : documentsForWord.documents) {
                        lid.locations.clear();
                    }

                    highFreqWords.add(word);
                }
            }
        }
        LOG.info("Finished building index for " + chmFile.getTitle());
        buildIndexStep.set(100);

        try {
            saveIndex();
        } catch (Exception ignored) {
            LOG.fine("Failed to save index: " + ignored);
        }
    }

    private Set<Integer> getLocations(String targetWord, ChmUnitInfo ui) {
        Set<Integer> locations = new HashSet<Integer>();
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

    //FIXME: support partial word and title only search
    @Override
    protected List<SearchResult> searchSingleWord(
            String word, boolean wholeWords, boolean titlesOnly, Set<String> lastRunFiles) {
        if (!wordToDocuments.containsKey(word)) {
            return null;
        }

        List<SearchResult> results = new ArrayList<SearchResult>();

        DocumentsForWord documentsForWord = wordToDocuments.get(word);

        if (highFreqWords.contains(word)) {
            LOG.info(word + " is a high frequency word " + documentsForWord.documents.size());
        }

        for (LocationsInDocument lid : documentsForWord.documents) {
            String url = lid.ui.getPath();
            if (lastRunFiles.size() > 0 && !lastRunFiles.contains(url)) {
                continue;
            }
            Set<Integer> locations = lid.locations.size() == 0 ? getLocations(word, lid.ui) : lid.locations;
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

    private int getCommonStartLength(String word1, String word2) {
        int i;
        for (i = 0; i < word1.length() && i < word2.length(); i++) {
            char c1 = word1.charAt(i);
            char c2 = word2.charAt(i);
            if (c1 != c2) {
                break;
            }
        }
        return i;
    }

    private void saveIndex() throws IOException {
        String path = getIndexFilePath();
        DataOutputStream out = new DataOutputStream(new FileOutputStream(path));

        out.writeInt(1); // version

        LOG.fine(String.format("%d documents", docIdToUrl.size()));
        Varint.writeUnsignedVarInt(docIdToUrl.size(), out);
        for (Map.Entry<Integer, String> entry : docIdToUrl.entrySet()) {
            String url = entry.getValue();
            out.writeUTF(url);
        }

        Varint.writeUnsignedVarInt(wordToDocuments.size(), out);
        String lastWord = "";
        SortedSet<String> words = new TreeSet<String>(wordToDocuments.keySet());
        for (String word : words) {
            DocumentsForWord documentsForWord = wordToDocuments.get(word);
            int wordPos = getCommonStartLength(lastWord, word);
            Varint.writeUnsignedVarInt(wordPos, out);
            out.writeUTF(word.substring(wordPos));
            lastWord = word;
            if (documentsForWord.documents.size() >= docIdToUrl.size() / 2) {
                LOG.fine(String.format("%s, %d", word, documentsForWord.documents.size()));
            }
            Varint.writeUnsignedVarInt(documentsForWord.documents.size(), out);
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
        out.close();
    }

    public boolean readIndex() {
        try {
            readIndexWithoutCatch();
            return true;
        } catch (Throwable ignored) {
            LOG.info("Error loading index from file: " + ignored);
            buildIndexStep.set(-1);
            wordToDocuments.clear();
            return false;
        }
    }

    private void readIndexWithoutCatch() throws IOException {
        buildIndexStep.set(0);
        String path = getIndexFilePath();
        DataInputStream in = new DataInputStream(new FileInputStream(path));
        int version = in.readInt();
        assert version == 1;
        int docCount = Varint.readUnsignedVarInt(in);
        for (int docID = 0; docID < docCount; docID++) {
            // int urlLen = Varint.readUnsignedVarInt(in);
            String url = in.readUTF();
            LOG.fine("" + docID + ": " + url);
            docIdToUrl.put(docID, url);
        }

        int wordCount = Varint.readUnsignedVarInt(in);
        int perStep = Math.max(wordCount / 100, 1);

        String lastWord = "";
        for (int i = 0; i < wordCount; i++) {

            if (i % perStep == 0) {
                buildIndexStep.set(Math.min(buildIndexStep.get() + 1, 99));
            }

            int wordPos = Varint.readUnsignedVarInt(in);
            String partialWord = in.readUTF();
            String word = lastWord.substring(0, wordPos) + partialWord;
            lastWord = word;
            LOG.fine(word);

            DocumentsForWord documentsForWord = new DocumentsForWord();
            wordToDocuments.put(word, documentsForWord);

            int wordDocCount = Varint.readUnsignedVarInt(in);
            for (int j = 0; j < wordDocCount; j++) {
                int docID = Varint.readUnsignedVarInt(in);

                String url = docIdToUrl.get(docID);
                ChmUnitInfo ui = chmFile.resolveObject(url);

                LocationsInDocument locationsInDocument = new LocationsInDocument(docID, ui);
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
        }
        buildIndexStep.set(100);
    }

    enum ParseState {
        OUTSIDE_TAGS, IN_HTML_TAG, IN_QUOTES, IN_HTML_ENTITY
    }

    class LocationsInDocument {

        final int docID;
        final ChmUnitInfo ui;
        final Set<Integer> locations;
        int totalFrequency;

        LocationsInDocument(int docID, ChmUnitInfo ui) {
            this.docID = docID;
            this.ui = ui;
            locations = new LinkedHashSet<Integer>();
            totalFrequency = 0;
        }
    }

    class DocumentsForWord {

        final List<LocationsInDocument> documents = new ArrayList<LocationsInDocument>();
    }
}
