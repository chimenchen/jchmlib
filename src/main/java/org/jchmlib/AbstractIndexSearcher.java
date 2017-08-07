package org.jchmlib;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;

public abstract class AbstractIndexSearcher {

    private static final Logger LOG = Logger.getLogger(ChmIndexSearcher.class.getName());
    protected final Set<String> stopWords = new HashSet<String>();
    protected String wordChars = "";

    @SuppressWarnings("unused")
    public void setWordChars(String wordChars) {
        this.wordChars = wordChars;
    }

    public void addStopWords(String word) {
        stopWords.add(word);
    }

    protected abstract List<SearchResult> searchSingleWord(
            String word, boolean wholeWords, boolean titlesOnly, Set<String> lastRunFiles);

    protected Set<String> getInitialResults(List<SubQuery> subQueries) {
        return new HashSet<String>();
    }

    protected abstract void fixTopic(SearchResult result);

    @SuppressWarnings("WeakerAccess")
    protected void addResult(boolean isPhraseStart, SearchResult doc, HashMap<String, SearchResult> results) {
        assert results != null;
        String key = doc.url;
        if (!results.containsKey(key)) {
            doc.hitCount = 1;
            results.put(key, doc);
        } else {
            SearchResult result = results.get(key);
            if (isPhraseStart) {
                result.totalFrequency += doc.totalFrequency;
                result.lastFrequency = doc.totalFrequency;
                result.locations = doc.locations;
                result.hitCount += 1;
            } else {
                Set<Integer> newLocationCodes = new LinkedHashSet<Integer>();
                for (Integer location : doc.locations) {
                    Integer lastLocation = location - 1;
                    if (result.locations.contains(lastLocation)) {
                        newLocationCodes.add(location);
                    }
                }
                if (newLocationCodes.size() > 0) {
                    // LOG.fine(String.format("SubQuery %s, %s", doc.url, doc.topic != null ? doc.topic : ""));
                    // LOG.fine("Locations[last]: " + result.locations);
                    // LOG.fine("Locations[cur]:  " + doc.locations);
                    // LOG.fine("Locations[new]:  " + newLocationCodes);
                    result.locations = newLocationCodes;
                    result.totalFrequency -= result.lastFrequency;
                    result.lastFrequency = newLocationCodes.size();
                    result.totalFrequency += result.lastFrequency;
                    result.hitCount += 1;
                }
            }
        }
    }

    @SuppressWarnings("SameParameterValue")
    public HashMap<String, String> search(String originalQuery,
            boolean wholeWords, boolean titlesOnly, int maxResults) {
        HashMap<String, SearchResult> results = new LinkedHashMap<String, SearchResult>();

        List<SubQuery> subQueries = splitQuery(originalQuery);

        Set<String> lastRunFiles = getInitialResults(subQueries);

        int subQueryStep = -1;
        for (SubQuery subQuery : subQueries) {
            subQueryStep++;

            LOG.fine(String.format("SubQuery[%d]: %s, %s, %s",
                    subQueryStep, subQuery.queryString, subQuery.isPhraseStart, subQuery.isInPhrase));
            List<SearchResult> documentsForWord = searchSingleWord(
                    subQuery.queryString, subQuery.isInPhrase || wholeWords, titlesOnly,
                    lastRunFiles);
            if (documentsForWord == null || documentsForWord.size() == 0) {
                return null;
            }

            for (SearchResult doc : documentsForWord) {
                String key = doc.url;
                if (subQueryStep > 0 && !results.containsKey(key)) {
                    continue;
                }
                addResult(subQuery.isPhraseStart, doc, results);
            }

            if (results.size() == 0) {
                return null;
            }
            if (subQueryStep > 0) {
                Iterator<Entry<String, SearchResult>> it = results.entrySet().iterator();
                while (it.hasNext()) {
                    if (it.next().getValue().hitCount < subQueryStep + 1) {
                        it.remove();
                    }
                }
            }
            lastRunFiles = results.keySet();
        }

        if (results.size() == 0) {
            return null;
        }

        ArrayList<SearchResult> resultList = new ArrayList<SearchResult>(results.values());
        Collections.sort(resultList, new Comparator<SearchResult>() {
            @Override
            public int compare(SearchResult r1, SearchResult r2) {
                return new Integer(r2.totalFrequency).compareTo(r1.totalFrequency);
            }
        });

        HashMap<String, String> finalResults = new LinkedHashMap<String, String>();
        for (SearchResult result : resultList) {
            fixTopic(result);
            finalResults.put(result.url, result.topic);
            if (maxResults > 0 && finalResults.size() >= maxResults) {
                break;
            }
        }
        return finalResults;
    }

    protected boolean isMultibyteChar(char c) {
        try {
            return String.valueOf(c).getBytes("UTF8").length > 1;
        } catch (UnsupportedEncodingException ignored) {
            return true;
        }
    }

    @SuppressWarnings("WeakerAccess")
    protected List<SubQuery> splitQuery(String originalQuery) {
        List<SubQuery> queryList = new ArrayList<SubQuery>();

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
                    String word = sb.toString().toLowerCase();
                    if (!stopWords.contains(word)) {
                        queryList.add(new SubQuery(word, !isInPhrase));
                    }
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
                    String word = String.valueOf(c);
                    if (!stopWords.contains(word)) {
                        queryList.add(new SubQuery(word, !isInPhrase));
                    }
                    isInPhrase = true;
                }
            }
        }

        if (sb.length() > 0) {
            String word = sb.toString().toLowerCase();
            if (!stopWords.contains(word)) {
                queryList.add(new SubQuery(word, !isInPhrase));
            }
        }

        for (int i=1; i<queryList.size(); i++) {
            SubQuery subQuery = queryList.get(i);
            if (!subQuery.isPhraseStart) {
                queryList.get(i - 1).isInPhrase = true;
            }
        }

        for (SubQuery subQuery : queryList) {
            LOG.fine(String.format("SubQuery: %s, %s, %s",
                    subQuery.queryString, subQuery.isPhraseStart, subQuery.isInPhrase));
        }

        return queryList;
    }


    protected class SearchResult {
        public final String url;
        public String topic;
        public Set<Integer> locations;
        public int lastFrequency;
        public int totalFrequency;
        public int hitCount;
        public SearchResult(String url, String topic, Set<Integer> locations, int totalFrequency) {
            this.url = url;
            this.topic = topic;
            this.locations = locations;
            this.lastFrequency = totalFrequency;
            this.totalFrequency = totalFrequency;
            hitCount = 1;
        }
    }

    protected class SubQuery {

        public final String queryString;
        public final boolean isPhraseStart;
        public boolean isInPhrase;

        public SubQuery(String queryString, boolean isNewWord) {
            this.queryString = queryString;
            this.isPhraseStart = isNewWord;
            // may be fixed later
            this.isInPhrase = !isNewWord;
        }
    }
}
