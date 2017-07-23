/* UDecoder.java 2006/05/25
 *
 * Copyright 2006 Chimen Chen. All rights reserved.
 *
 */

package org.jchmlib.app.net;

import java.util.logging.Logger;
import org.jchmlib.util.ByteBufferHelper;

/**
 * Utility class for HTML form decoding. This class contains static methods for
 * decoding a String from the <CODE>application/x-www-form-urlencoded</CODE>
 * MIME format.
 */
public class UDecoder {

    private static final Logger LOG = Logger.getLogger(UDecoder.class.getName());

    public static String decode(String s, String enc, boolean query) {
        LOG.fine(String.format("URL decode (%s): %s", enc, s));
        if (s == null || enc == null || enc.length() == 0) {
            return null;
        }

        boolean needToChange = false;
        int numChars = s.length();
        byte[] bytes = new byte[numChars];
        int totalBytes = 0;

        int i = 0;
        while (i < numChars) {
            char c = s.charAt(i);
            switch (c) {
                case '+':
                    if (query) {
                        bytes[totalBytes++] = charAsByte(' ');
                        needToChange = true;
                    } else {
                        bytes[totalBytes++] = charAsByte('+');
                    }
                    i++;
                    break;
                case '%':
                /*
                 * Starting with this instance of %, process all consecutive
                 * substrings of the form %xy. Each substring %xy will yield a
                 * byte. Convert all consecutive bytes obtained this way to
                 * whatever character(s) they represent in the provided
                 * encoding.
                 */
                    try {
                        while (((i + 2) < numChars) && (c == '%')) {
                            byte b = (byte) Integer.parseInt(
                                    s.substring(i + 1, i + 3), 16);
                            bytes[totalBytes++] = b;

                            i += 3;
                            if (i < numChars) {
                                c = s.charAt(i);
                            }
                        }

                        // A trailing, incomplete byte encoding such as
                        // "%x" will cause an exception to be thrown
                        if ((i < numChars) && (c == '%')) {
                            throw new IllegalArgumentException(
                                    "UDecoder: Incomplete trailing escape (%) pattern");
                        }
                    } catch (NumberFormatException e) {
                        return null;
                    }
                    needToChange = true;
                    break;
                default:
                    try {
                        // just in case this is a multibyte character
                        byte[] bytesForChar = String.valueOf(c).getBytes(enc);
                        for (byte b : bytesForChar) {
                            bytes[totalBytes++] = b;
                        }
                    } catch (Exception ignored) {
                        return null;
                    }

                    i++;
                    break;
            }
        }

        if (!needToChange) {
            return s;
        }
        return ByteBufferHelper.bytesToString(bytes, 0, totalBytes, enc);
    }

    private static byte charAsByte(char c) {
        return (byte) (c & 0x00FF);
    }
}
