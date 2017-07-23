/* HttpRequest.java 2006/08/22
 *
 * Copyright 2006 Chimen Chen. All rights reserved.
 *
 */

package org.jchmlib.app.net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.regex.Pattern;

public class HttpRequest {

    /**
     * Request METHODS.
     */
    public static final String __GET = "GET";

    private BufferedReader reader;

    private String encoding;

    private URI uri = null;

    private String mimeType;

    private HashMap<String, String> parameters;

    private boolean paramsExtracted;

    /**
     * Constructs a HttpRequest from the given InputStream and the named
     * encoding.
     */
    public HttpRequest(InputStream input, String encoding) {
        this.encoding = encoding;

        try {
            reader = new BufferedReader(new InputStreamReader(input, encoding));
        } catch (UnsupportedEncodingException e) {
            System.err.println("Encoding " + encoding + " unsupported:\n" + e);
            reader = new BufferedReader(new InputStreamReader(input));
        }

        try {
            readHeader(reader);
        } catch (Exception e) {
            System.err.println("Error reading request header:\n" + e);
        }
    }

    /**
     * Read the request line and header.
     */
    private void readHeader(BufferedReader in) throws IOException,
            URISyntaxException {

        // read HTTP request -- the request comes in
        // on the first line, and is of the form:
        // GET <filename> HTTP/1.x
        String line_buffer;
        while (true) {
            line_buffer = in.readLine();
            if (line_buffer == null) {
                return;
            }
            if (line_buffer.length() != 0) {
                break;
            }
        }
        // System.out.println(line_buffer);

        String[] arr = Pattern.compile(" ").split(line_buffer);
        if (arr == null || arr.length != 3
                || !(__GET).equals(arr[0])
                || !(arr[2]).startsWith("HTTP/1")) {
            return;
        }

        String raw_uri = arr[1];
        uri = new URI(raw_uri);
        // loop through and discard rest of request
        do {
            line_buffer = in.readLine();
            // System.out.println(line_buffer);
        } while (line_buffer != null && line_buffer.length() > 0);
    }

    /**
     * Returns the MIME type of the body of the request.
     */
    public String getContentType() {
        if (mimeType != null) {
            return mimeType;
        }

        String path = getPath();

        // get the extension
        String ext = "";
        int indexDot = path.lastIndexOf(".");
        if (indexDot != -1) {
            ext = path.substring(indexDot);
        }
        mimeType = MimeMapper.lookupMime(ext);

        return mimeType;
    }

    /**
     * Returns the value of a request parameter as a
     * <code>String</code>, or <code>null</code> if the parameter
     * does not exist.
     */
    public String getParameter(String name) {
        if (!paramsExtracted) {
            extractParameters();
        }
        return parameters.get(name);
    }

    /**
     * Returns the request path.
     */
    public String getPath() {
        if (uri == null) {
            return null;
        }

        String rawPath = uri.getRawPath();
        try {
            // path of CHM object is encoded using UTF8.
            // FIXME: need to test
            return UDecoder.decode(rawPath, "UTF8", false);
        } catch (Exception e) {
            return null;
        }
    }

    /*
     * Extract Parameters from query string.
     */
    private void extractParameters() {
        if (paramsExtracted) {
            return;
        }
        paramsExtracted = true;

        if (parameters == null) {
            parameters = new LinkedHashMap<String, String>();
        }

        String query = uri.getRawQuery();
        if (query == null) {
            return;
        }

        // key-and-value pairs
        String[] pairs = Pattern.compile("&").split(query);
        int len = pairs.length;
        for (String pair : pairs) {
            String[] s2 = Pattern.compile("=").split(pair);
            if (s2 != null && s2.length == 2) {
                String key, value;
                key = UDecoder.decode(s2[0], encoding, true);
                value = UDecoder.decode(s2[1], encoding, true);
                if (key != null && value != null) {
                    parameters.put(key, value);
                }
            }
        }
    }
}
