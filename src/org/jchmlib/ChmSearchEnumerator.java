/* SearchEnumerator.java 2007/10/12
 *
 * Copyright 2006 Chimen Chen. All rights reserved.
 *
 */

package org.jchmlib;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jchmlib.util.ByteBufferHelper;

/**
 * ChmSearchEnumerator uses regular expression to check whether the
 * content of a given CHM unit matches a query string.<p>
 * <p>
 * The query string may contains one or more keywords, separated
 * by whitespaces. The keyword can itself be a regular expression
 * and can be quoted as well.<p>
 * <p>
 * Here are some typical query strings:
 * <pre> abc
 * abc abc
 * "query string" (go)*
 * Http[a-zA-Z]*Request\(.*\);
 * </pre>
 */
public class ChmSearchEnumerator implements ChmEnumerator {

    public static final String REGEX = "\"[^\"]*\"|[^\\s]+";

    private ChmFile chmFile;
    private Collection<String> keywords;
    private ArrayList<String> results;
    private long startTimestamp;
    private long filesSearched;

    public ChmSearchEnumerator(ChmFile chmFile, String query) {
        startTimestamp = System.currentTimeMillis() / 1000;
        filesSearched = 0;

        this.chmFile = chmFile;
        results = new ArrayList<String>();
        keywords = new ArrayList<String>();

        StringBuilder sb = new StringBuilder();
        int length = query.length();
        boolean quoting = false;
        char c_i;
        for (int i = 0; i < length; i++) {
            c_i = query.charAt(i);
            if (c_i == '\\') {
                char c = query.charAt(i + 1);
                if (c == ' ' || c == 'w' || c == 'W'
                        || c == 's' || c == 'S'
                        || c == 'd' || c == 'D') {
                    sb.append("\\");
                    sb.append(c);
                    i++;
                }
            } else if (c_i == '"') {
                if (!quoting) {
                    quoting = true;
                    sb.append("\\Q");
                } else {
                    quoting = false;
                    sb.append("\\E");
                    if (sb.length() > 4) {
                        keywords.add(new String(sb));
                        // System.out.println(sb);
                    }
                    sb = new StringBuilder();
                }
            } else if (c_i == ' ') {
                if (quoting) {
                    sb.append(' ');
                } else if (!quoting && sb.length() != 0) {
                    keywords.add(new String(sb));
                    // System.out.println(sb);
                    sb = new StringBuilder();
                }
            } else {
                sb.append(c_i);
            }
        }
        if (sb.length() != 0) {
            keywords.add(new String(sb));
            // System.out.println(sb);
        }
    }

    public void enumerate(ChmUnitInfo ui) throws ChmStopEnumeration {
        if (keywords.size() == 0) {
            throw new ChmStopEnumeration();
        }

        filesSearched++;

        if (filesSearched % 100 == 0) {
            long timestamp = System.currentTimeMillis() / 1000;
            if (timestamp - startTimestamp > 30) { // takes longer than 30 seconds
                throw new ChmStopEnumeration();  // stop searching
            }
        }

        ByteBuffer buf = chmFile.retrieveObject(ui);
        if (buf == null) {
            return;
        }
        String data = ByteBufferHelper.peakAsString(buf, chmFile.codec);
        if (data == null) {
            return;
        }

        for (String keyword : keywords) {
            Pattern p = Pattern.compile(keyword);
            Matcher m = p.matcher(data);
            if (m.find()) {
                results.add(ui.path);
                if (results.size() > 100) {
                    throw new ChmStopEnumeration();
                }
                return;
            }
        }
    }

    public ArrayList<String> getResults() {
        return results;
    }
}
