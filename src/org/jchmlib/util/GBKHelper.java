/*
 * GBKHelper.java
 *
 * Copyleft (C) 2006 RatKing. All wrongs reserved.
 */

package org.jchmlib.util;

import java.io.UnsupportedEncodingException;
import java.text.Collator;
import java.util.Locale;

public class GBKHelper {

    public static final int GBK_HIGH_MIN = 0x81;
    public static final int GBK_HIGH_MAX = 0xFE;
    public static final int GBK_LOW_MIN = 0x40;
    public static final int GBK_LOW_MAX = 0xFE;
    public static final int GBK_LOW_EXCEPTION = 0x7F;

    public static boolean isGBK(char c) {
        String str = String.valueOf(c);

        byte[] b;
        try {
            b = str.getBytes("GBK");
        } catch (UnsupportedEncodingException uee) {
            throw new IllegalStateException(
                    "Encoding GBK unsupported: " + uee.getMessage());
        }

        if (b.length == 2) {
            int ch0 = b[0];
            int ch1 = b[1];
            if (ch0 < 0) {
                ch0 += 256;
            }
            if (ch1 < 0) {
                ch1 += 256;
            }
            if (ch0 >= GBK_HIGH_MIN && ch0 <= GBK_HIGH_MAX
                    && ch1 >= GBK_LOW_MIN && ch1 <= GBK_LOW_MAX
                    && ch1 != GBK_LOW_EXCEPTION) {
                return true;
            }
        }

        return false;
    }

    public static int compare(String str1, String str2) {
        Collator zh_CNCollator = Collator.getInstance(new Locale("zh", "CN"));
        if (isGBK(str1.charAt(0)) && isGBK(str2.charAt(0))) {
            if (str1.length() == 1 || str2.length() == 1) {
                return zh_CNCollator.compare(str1.substring(0, 1),
                        str2.substring(0, 1));
            }
        }
        return (zh_CNCollator.compare(str1, str2));
    }

    public static int toUnsignedInt(byte x) {
        return ((int) x) & 0xff;
    }

    public static int compareBytes(byte[] bytes1, byte[] bytes2) {
        for (int i = 0; i < bytes1.length && i < bytes2.length; i++) {
            int byte1 = toUnsignedInt(bytes1[i]);
            int byte2 = toUnsignedInt(bytes2[i]);
            if (byte1 < byte2) {
                return -1;
            } else if (byte1 > byte2) {
                return 1;
            }
        }
        if (bytes1.length < bytes2.length) {
            return -1;
        } else if (bytes1.length > bytes2.length) {
            return 1;
        } else {
            return 0;
        }
    }
}
