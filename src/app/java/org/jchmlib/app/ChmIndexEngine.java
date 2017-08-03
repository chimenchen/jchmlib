package org.jchmlib.app;

import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;
import org.jchmlib.ChmCollectFilesEnumerator;
import org.jchmlib.ChmFile;
import org.jchmlib.ChmUnitInfo;

public class ChmIndexEngine {

    private static final Logger LOG = Logger.getLogger(ChmIndexEngine.class.getName());

    private static final String[][] ESCAPES = {
            {"\"", "quot"}, // " - double-quote
            {"&", "amp"}, // & - ampersand
            {"<", "lt"}, // < - less-than
            {">", "gt"}, // > - greater-than

            // Mapping to escape ISO-8859-1 characters to their named HTML 3.x equivalents.
            {"\u00A0", "nbsp"}, // non-breaking space
            {"\u00A1", "iexcl"}, // inverted exclamation mark
            {"\u00A2", "cent"}, // cent sign
            {"\u00A3", "pound"}, // pound sign
            {"\u00A4", "curren"}, // currency sign
            {"\u00A5", "yen"}, // yen sign = yuan sign
            {"\u00A6", "brvbar"}, // broken bar = broken vertical bar
            {"\u00A7", "sect"}, // section sign
            {"\u00A8", "uml"}, // diaeresis = spacing diaeresis
            {"\u00A9", "copy"}, // © - copyright sign
            {"\u00AA", "ordf"}, // feminine ordinal indicator
            {"\u00AB", "laquo"},
            // left-pointing double angle quotation mark = left pointing guillemet
            {"\u00AC", "not"}, // not sign
            {"\u00AD", "shy"}, // soft hyphen = discretionary hyphen
            {"\u00AE", "reg"}, // ® - registered trademark sign
            {"\u00AF", "macr"}, // macron = spacing macron = overline = APL overbar
            {"\u00B0", "deg"}, // degree sign
            {"\u00B1", "plusmn"}, // plus-minus sign = plus-or-minus sign
            {"\u00B2", "sup2"}, // superscript two = superscript digit two = squared
            {"\u00B3", "sup3"}, // superscript three = superscript digit three = cubed
            {"\u00B4", "acute"}, // acute accent = spacing acute
            {"\u00B5", "micro"}, // micro sign
            {"\u00B6", "para"}, // pilcrow sign = paragraph sign
            {"\u00B7", "middot"}, // middle dot = Georgian comma = Greek middle dot
            {"\u00B8", "cedil"}, // cedilla = spacing cedilla
            {"\u00B9", "sup1"}, // superscript one = superscript digit one
            {"\u00BA", "ordm"}, // masculine ordinal indicator
            {"\u00BB", "raquo"},
            // right-pointing double angle quotation mark = right pointing guillemet
            {"\u00BC", "frac14"}, // vulgar fraction one quarter = fraction one quarter
            {"\u00BD", "frac12"}, // vulgar fraction one half = fraction one half
            {"\u00BE", "frac34"}, // vulgar fraction three quarters = fraction three quarters
            {"\u00BF", "iquest"}, // inverted question mark = turned question mark
            {"\u00C0", "Agrave"}, // А - uppercase A, grave accent
            {"\u00C1", "Aacute"}, // Б - uppercase A, acute accent
            {"\u00C2", "Acirc"}, // В - uppercase A, circumflex accent
            {"\u00C3", "Atilde"}, // Г - uppercase A, tilde
            {"\u00C4", "Auml"}, // Д - uppercase A, umlaut
            {"\u00C5", "Aring"}, // Е - uppercase A, ring
            {"\u00C6", "AElig"}, // Ж - uppercase AE
            {"\u00C7", "Ccedil"}, // З - uppercase C, cedilla
            {"\u00C8", "Egrave"}, // И - uppercase E, grave accent
            {"\u00C9", "Eacute"}, // Й - uppercase E, acute accent
            {"\u00CA", "Ecirc"}, // К - uppercase E, circumflex accent
            {"\u00CB", "Euml"}, // Л - uppercase E, umlaut
            {"\u00CC", "Igrave"}, // М - uppercase I, grave accent
            {"\u00CD", "Iacute"}, // Н - uppercase I, acute accent
            {"\u00CE", "Icirc"}, // О - uppercase I, circumflex accent
            {"\u00CF", "Iuml"}, // П - uppercase I, umlaut
            {"\u00D0", "ETH"}, // Р - uppercase Eth, Icelandic
            {"\u00D1", "Ntilde"}, // С - uppercase N, tilde
            {"\u00D2", "Ograve"}, // Т - uppercase O, grave accent
            {"\u00D3", "Oacute"}, // У - uppercase O, acute accent
            {"\u00D4", "Ocirc"}, // Ф - uppercase O, circumflex accent
            {"\u00D5", "Otilde"}, // Х - uppercase O, tilde
            {"\u00D6", "Ouml"}, // Ц - uppercase O, umlaut
            {"\u00D7", "times"}, // multiplication sign
            {"\u00D8", "Oslash"}, // Ш - uppercase O, slash
            {"\u00D9", "Ugrave"}, // Щ - uppercase U, grave accent
            {"\u00DA", "Uacute"}, // Ъ - uppercase U, acute accent
            {"\u00DB", "Ucirc"}, // Ы - uppercase U, circumflex accent
            {"\u00DC", "Uuml"}, // Ь - uppercase U, umlaut
            {"\u00DD", "Yacute"}, // Э - uppercase Y, acute accent
            {"\u00DE", "THORN"}, // Ю - uppercase THORN, Icelandic
            {"\u00DF", "szlig"}, // Я - lowercase sharps, German
            {"\u00E0", "agrave"}, // а - lowercase a, grave accent
            {"\u00E1", "aacute"}, // б - lowercase a, acute accent
            {"\u00E2", "acirc"}, // в - lowercase a, circumflex accent
            {"\u00E3", "atilde"}, // г - lowercase a, tilde
            {"\u00E4", "auml"}, // д - lowercase a, umlaut
            {"\u00E5", "aring"}, // е - lowercase a, ring
            {"\u00E6", "aelig"}, // ж - lowercase ae
            {"\u00E7", "ccedil"}, // з - lowercase c, cedilla
            {"\u00E8", "egrave"}, // и - lowercase e, grave accent
            {"\u00E9", "eacute"}, // й - lowercase e, acute accent
            {"\u00EA", "ecirc"}, // к - lowercase e, circumflex accent
            {"\u00EB", "euml"}, // л - lowercase e, umlaut
            {"\u00EC", "igrave"}, // м - lowercase i, grave accent
            {"\u00ED", "iacute"}, // н - lowercase i, acute accent
            {"\u00EE", "icirc"}, // о - lowercase i, circumflex accent
            {"\u00EF", "iuml"}, // п - lowercase i, umlaut
            {"\u00F0", "eth"}, // р - lowercase eth, Icelandic
            {"\u00F1", "ntilde"}, // с - lowercase n, tilde
            {"\u00F2", "ograve"}, // т - lowercase o, grave accent
            {"\u00F3", "oacute"}, // у - lowercase o, acute accent
            {"\u00F4", "ocirc"}, // ф - lowercase o, circumflex accent
            {"\u00F5", "otilde"}, // х - lowercase o, tilde
            {"\u00F6", "ouml"}, // ц - lowercase o, umlaut
            {"\u00F7", "divide"}, // division sign
            {"\u00F8", "oslash"}, // ш - lowercase o, slash
            {"\u00F9", "ugrave"}, // щ - lowercase u, grave accent
            {"\u00FA", "uacute"}, // ъ - lowercase u, acute accent
            {"\u00FB", "ucirc"}, // ы - lowercase u, circumflex accent
            {"\u00FC", "uuml"}, // ь - lowercase u, umlaut
            {"\u00FD", "yacute"}, // э - lowercase y, acute accent
            {"\u00FE", "thorn"}, // ю - lowercase thorn, Icelandic
            {"\u00FF", "yuml"}, // я - lowercase y, umlaut
    };
    private static final int MIN_ESCAPE = 2;
    private static final int MAX_ESCAPE = 6;
    private static final HashMap<String, CharSequence> lookupMap;

    static {
        lookupMap = new HashMap<String, CharSequence>();
        for (final CharSequence[] seq : ESCAPES) {
            lookupMap.put(seq[1].toString(), seq[0]);
        }
    }

    private final HashMap<String, DocumentsForWord> wordToDocuments = new HashMap<>();
    private String wordChars;
    private StringBuilder sbEntity;
    private StringBuilder sbWord;
    private ChmFile chmFile = null;

    public ChmIndexEngine() {
        sbEntity = new StringBuilder();
        sbWord = new StringBuilder();
        wordChars = "$_";
    }

    private static String unescapeHtml3(final String input) {
        StringWriter writer = null;
        int len = input.length();
        int i = 1;
        int st = 0;
        while (true) {
            // look for '&'
            while (i < len && input.charAt(i - 1) != '&') {
                i++;
            }
            if (i >= len) {
                break;
            }

            // found '&', look for ';'
            int j = i;
            while (j < len && j < i + MAX_ESCAPE + 1 && input.charAt(j) != ';') {
                j++;
            }
            if (j == len || j < i + MIN_ESCAPE || j == i + MAX_ESCAPE + 1) {
                i++;
                continue;
            }

            // found escape
            if (input.charAt(i) == '#') {
                // numeric escape
                int k = i + 1;
                int radix = 10;

                final char firstChar = input.charAt(k);
                if (firstChar == 'x' || firstChar == 'X') {
                    k++;
                    radix = 16;
                }

                try {
                    int entityValue = Integer.parseInt(input.substring(k, j), radix);

                    if (writer == null) {
                        writer = new StringWriter(input.length());
                    }
                    writer.append(input.substring(st, i - 1));

                    if (entityValue > 0xFFFF) {
                        final char[] chars = Character.toChars(entityValue);
                        writer.write(chars[0]);
                        writer.write(chars[1]);
                    } else {
                        writer.write(entityValue);
                    }

                } catch (NumberFormatException ex) {
                    i++;
                    continue;
                }
            } else {
                // named escape
                CharSequence value = lookupMap.get(input.substring(i, j));
                if (value == null) {
                    i++;
                    continue;
                }

                if (writer == null) {
                    writer = new StringWriter(input.length());
                }
                writer.append(input.substring(st, i - 1));

                writer.append(value);
            }

            // skip escape
            st = j + 1;
            i = st;
        }

        if (writer != null) {
            writer.append(input.substring(st, len));
            return writer.toString();
        }
        return input;
    }

    public List<String> parse(String origin) {
        List<String> words = new ArrayList<String>();

        ParseState state = ParseState.OUTSIDE_TAGS;
        sbEntity = new StringBuilder();
        sbWord = new StringBuilder();

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
                            addToWord('&', words);
                        }
                        j--; // parse this character again, but in different state
                        break;
                    }

                    if (c == ';') {
                        sbEntity.append(c);
                    }
                    String entity = unescapeHtml3(sbEntity.toString());
                    if (entity != null) {
                        for (char c2 : entity.toCharArray()) {
                            addToWord(c2, words);
                        }
                    }
                    break;
                case OUTSIDE_TAGS:
                    if (c == '<') {
                        state = ParseState.IN_HTML_TAG;
                        addToWord(' ', words);
                    } else if (c == '&') {
                        state = ParseState.IN_HTML_ENTITY;
                        sbEntity.setLength(0);
                        sbEntity.append(c);
                    } else {
                        addToWord(c, words);
                    }
                    break;
            }
        }

        addToWord(' ', words);

        return words;
    }

    private boolean isMultibyteChar(char c) {
        try {
            return String.valueOf(c).getBytes("UTF8").length > 1;
        } catch (UnsupportedEncodingException ignored) {
            return true;
        }
    }

    private void addToWord(char c, List<String> words) {
        boolean isMB = isMultibyteChar(c);
        boolean isWordChar = Character.isLetterOrDigit(c) || wordChars.indexOf(c) >= 0;
        if (!isMB && isWordChar) {
            sbWord.append(c);
        } else {
            if (sbWord.length() > 0) {
                words.add(sbWord.toString().toLowerCase());
                sbWord.setLength(0);
            }
            if (isMB && isWordChar) {
                words.add(String.valueOf(c));
            }
        }
    }

    private ArrayList<SubQuery> splitQuery(String originalQuery) {
        ArrayList<SubQuery> queryList = new ArrayList<SubQuery>();

        StringBuilder sb = new StringBuilder();
        boolean isInPhrase = false;
        boolean isInQuote = false;

        for (char c : originalQuery.toCharArray()) {
            boolean isMB = isMultibyteChar(c);
            boolean isWordChar = Character.isLetterOrDigit(c) || wordChars.indexOf(c) >= 0;

            if (!isMB && isWordChar) {
                sb.append(c);
            } else {
                if (sb.length() > 0) {
                    queryList.add(new SubQuery(sb.toString().toLowerCase(), !isInPhrase));
                    sb.setLength(0);
                    isInPhrase = true;
                }

                if (!isInQuote && c == '"') {
                    isInQuote = true;
                } else if (isInQuote && c == '"') {
                    isInQuote = false;
                } else if (!isInQuote && c == ' ') {
                    isInPhrase = false;
                }

                if (isMB && isWordChar) {
                    queryList.add(new SubQuery(String.valueOf(c), !isInPhrase));
                    isInPhrase = true;
                }
            }
        }

        if (sb.length() > 0) {
            queryList.add(new SubQuery(sb.toString().toLowerCase(), !isInPhrase));
        }

        return queryList;
    }

    public void buildIndex(ChmFile chmFile) {
        this.chmFile = chmFile;
        LOG.info("Building index for " + chmFile.getTitle());
        ChmCollectFilesEnumerator enumerator = new ChmCollectFilesEnumerator();
        chmFile.enumerate(ChmFile.CHM_ENUMERATE_USER, enumerator);
        LOG.info("files count: " + enumerator.files.size());
        int docID = -1;
        for (ChmUnitInfo ui : enumerator.files) {
            String content = chmFile.retrieveObjectAsString(ui);
            if (content == null || content.length() == 0) {
                continue;
            }

            List<String> words = parse(content);
            if (words.size() == 0) {
                continue;
            }

            docID++;

            HashMap<String, LocationsInDocument> wordToLocations = new HashMap<>();
            int wordLocation = -1;
            for (String word : words) {
                wordLocation++;
                LocationsInDocument locationsInDocument;
                if (wordToLocations.containsKey(word)) {
                    locationsInDocument = wordToLocations.get(word);
                } else {
                    locationsInDocument = new LocationsInDocument(docID, ui);
                    wordToLocations.put(word, locationsInDocument);
                }
                locationsInDocument.locations.add(wordLocation);
            }

            for (Map.Entry<String, LocationsInDocument> entry : wordToLocations.entrySet()) {
                String word = entry.getKey();
                LocationsInDocument locationsInDocument = entry.getValue();

                DocumentsForWord documentsForWord;
                if (wordToDocuments.containsKey(word)) {
                    documentsForWord = wordToDocuments.get(word);
                } else {
                    documentsForWord = new DocumentsForWord();
                    wordToDocuments.put(word, documentsForWord);
                }
                documentsForWord.documents.add(locationsInDocument);
            }
        }
        LOG.info("Finished building index for " + chmFile.getTitle());
    }

    public HashMap<String, String> search(String originalQuery) {
        if (chmFile == null) {  //FIXME: check indexing status
            return null;
        }

        HashMap<Integer, IndexSearchResult> results = null;

        ArrayList<SubQuery> subQueries = splitQuery(originalQuery);

        for (SubQuery subQuery : subQueries) {
            LOG.info(String.format("%s, %s", subQuery.queryString, subQuery.isPhraseStart));
        }

        int subQueryStep = -1;
        for (SubQuery subQuery : subQueries) {
            subQueryStep++;

            if (!wordToDocuments.containsKey(subQuery.queryString)) {
                return null;
            }

            DocumentsForWord documentsForWord = wordToDocuments.get(subQuery.queryString);
            for (LocationsInDocument lid : documentsForWord.documents) {
                int docID = lid.docID;

                if (subQueryStep > 0 && (results == null || !results.containsKey(docID))) {
                    continue;
                }

                if (results == null) {
                    results = new LinkedHashMap<>();
                }

                IndexSearchResult result;
                if (!results.containsKey(docID)) {
                    result = new IndexSearchResult();
                    result.ui = lid.ui;
                    result.totalFrequency = lid.locations.size();
                    result.locations = lid.locations;
                    result.hitCount = 1;
                    results.put(docID, result);
                } else {
                    result = results.get(docID);
                    if (subQuery.isPhraseStart) {
                        result.totalFrequency += lid.locations.size();
                        result.locations = lid.locations;
                        result.hitCount += 1;
                    } else {
                        Set<Integer> newLocationCodes = new LinkedHashSet<Integer>();
                        for (Integer location : lid.locations) {
                            Integer lastLocation = location - 1;
                            if (result.locations.contains(lastLocation)) {
                                newLocationCodes.add(location);
                            }
                        }
                        LOG.info(String.format("subQuery(%d) %s: %d %s",
                                subQueryStep, subQuery.queryString, docID, lid.ui.getPath()));
                        LOG.info(result.locations.toString());
                        LOG.info(lid.locations.toString());
                        if (newLocationCodes.size() > 0) {
                            result.locations = newLocationCodes;
                            result.totalFrequency += newLocationCodes.size();
                            result.hitCount += 1;
                            LOG.info("match");
                        } else {
                            LOG.info("NO MATCH");
                        }
                    }
                }
            }

            if (results == null || results.size() == 0) {
                return null;
            }
            if (subQueryStep == 0) {
                continue;
            }

            int expectedHitCount = subQueryStep + 1;

            Iterator<Entry<Integer, IndexSearchResult>> entryIterator;
            entryIterator = results.entrySet().iterator();
            while (entryIterator.hasNext()) {
                Map.Entry<Integer, IndexSearchResult> entry = entryIterator.next();
                if (entry.getValue().hitCount < expectedHitCount) {
                    entryIterator.remove();
                }
            }
        }

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
            LOG.info("#" + result.totalFrequency + " " + " <=> " + result.ui.getPath());
            String url = result.ui.getPath();
            String topic = chmFile.getTitleOfObject(url);
            finalResults.put(url, topic);
            if (finalResults.size() >= 500) {
                break;
            }
        }
        return finalResults;
    }

    enum ParseState {
        OUTSIDE_TAGS, IN_HTML_TAG, IN_QUOTES, IN_HTML_ENTITY
    }

    class IndexSearchResult {

        ChmUnitInfo ui;
        Set<Integer> locations;
        int totalFrequency;
        int hitCount;
    }

    class LocationsInDocument {

        final int docID;
        final ChmUnitInfo ui;
        final Set<Integer> locations;

        LocationsInDocument(int docID, ChmUnitInfo ui) {
            this.docID = docID;
            this.ui = ui;
            locations = new LinkedHashSet<>();
        }
    }

    class DocumentsForWord {

        final List<LocationsInDocument> documents;

        DocumentsForWord() {
            documents = new ArrayList<LocationsInDocument>();
        }
    }

    class SubQuery {

        final String queryString;
        final boolean isPhraseStart;

        public SubQuery(String queryString, boolean isNewWord) {
            this.queryString = queryString;
            this.isPhraseStart = isNewWord;
        }
    }
}

