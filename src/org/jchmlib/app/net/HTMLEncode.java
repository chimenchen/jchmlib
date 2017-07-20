package org.jchmlib.app.net;

import java.util.HashMap;
import java.util.LinkedHashMap;


/**
 * Converts a String to HTML by converting all special characters to HTML-entities.
 */
public final class HTMLEncode {

    private static final String[] ENTITIES = {
            ">",
            "&gt;",
            "<",
            "&lt;",
            "&",
            "&amp;",
            "\"",
            "&quot;",
            "'",
            "&#039;",
            "\\",
            "&#092;",
            "\u00a9",
            "&copy;",
            "\u00ae",
            "&reg;"};
    private static HashMap<String, String> entityTableEncode = null;

    /**
     * Utility class, don't instantiate.
     */
    private HTMLEncode() {
        // unused
    }

    private static synchronized void buildEntityTables() {
        entityTableEncode = new LinkedHashMap<String, String>();

        for (int i = 0; i < ENTITIES.length; i += 2) {
            if (!entityTableEncode.containsKey(ENTITIES[i])) {
                entityTableEncode.put(ENTITIES[i], ENTITIES[i + 1]);
            }
        }
    }

    /**
     * Converts a String to HTML by converting all special characters to HTML-entities.
     */
    public static String encode(String s) {
        return encode(s, "\n");
    }

    /**
     * Converts a String to HTML by converting all special characters to HTML-entities.
     */
    public static String encode(String s, String cr) {
        if (entityTableEncode == null) {
            buildEntityTables();
        }
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() * 2);
        char ch;
        for (int i = 0; i < s.length(); ++i) {
            ch = s.charAt(i);
            if ((ch >= 63 && ch <= 90) || (ch >= 97 && ch <= 122) || (ch == ' ')) {
                sb.append(ch);
            } else if (ch == '\n') {
                sb.append(cr);
            } else {
                String chEnc = encodeSingleChar(String.valueOf(ch));
                if (chEnc != null) {
                    sb.append(chEnc);
                } else {
                    // // Not 7 Bit use the unicode system
                    // sb.append("&#");
                    // sb.append(new Integer(ch).toString());
                    // sb.append(';');
                    sb.append(ch);
                }
            }
        }
        return sb.toString();
    }

    /**
     * Converts a single character to HTML
     */
    private static String encodeSingleChar(String ch) {
        return entityTableEncode.get(ch);
    }

}
