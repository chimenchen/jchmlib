/* MimeMapper.java 06/08/22
 *
 * Copyright 2006 Chimen Chen. All rights reserved.
 *
 */

package org.jchmlib.app.net;

import java.util.HashMap;

class MimeMapper {

    private static HashMap<String, String> extToMimeType;

    static {
        extToMimeType = new HashMap<String, String>();
        extToMimeType.put(".htm", "text/html");
        extToMimeType.put(".html", "text/html");
        extToMimeType.put(".hhc", "text/text");
        extToMimeType.put(".hhk", "text/text");
        extToMimeType.put(".css", "text/css");
        extToMimeType.put(".txt", "text/plain");
        extToMimeType.put(".json", "application/json");
        extToMimeType.put(".js", "application/javascript");
        extToMimeType.put(".gif", "image/gif");
        extToMimeType.put(".jpg", "image/jpeg");
        extToMimeType.put(".jpeg", "image/jpeg");
        extToMimeType.put(".jpe", "image/jpeg");
        extToMimeType.put(".bmp", "image/bitmap");
        extToMimeType.put(".png", "image/png");
        extToMimeType.put(".ico", "image/x-icon");
    }

    /**
     * Returns the MIME type of the named extension.
     */
    public static String lookupMime(String ext) {
        ext = ext.toLowerCase();
        // return extToMimeType.getOrDefault(ext, "application/octet-stream");
        if (extToMimeType.containsKey(ext)) {
            return extToMimeType.get(ext);
        } else {
            return "application/octet-stream";
        }
    }
}

