package org.jchmlib;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.HashMap;

class TagReader {

    private final HashMap<String, Integer> tagLevels;
    private final ByteBuffer data;
    private final String encoding;
    private int level;

    public TagReader(ByteBuffer data, String encoding) {
        this.data = data;
        this.encoding = encoding;
        tagLevels = new HashMap<String, Integer>();
        level = 0;
    }

    public Tag getNext() {
        Tag ret = new Tag();
        // ret.totalLevel = level;

        if (!data.hasRemaining()) {
            return ret;
        }

        String tagString = readTag();

        if (tagString.startsWith("<!")) { // comment or metadata, skip it
            return getNext();
        }
        if (tagString.startsWith("<?")) { // special data, skip it
            return getNext();
        }
        int tagLevel;
        if (tagString.startsWith("</")) { // a closed tag
            ret.name = tagString.substring(1, tagString.length() - 1).trim().toLowerCase();
            level--;
            // ret.totalLevel = level;
            if (tagLevels.containsKey(ret.name.substring(1))) {
                tagLevel = tagLevels.get(ret.name.substring(1));
                tagLevel--;
            } else {
                tagLevel = 0;
            }
            tagLevels.put(ret.name.substring(1), tagLevel);
            ret.tagLevel = tagLevel;
            return ret;
        }

        // open tag
        ret.name = tagString.substring(1, tagString.length() - 1).trim().toLowerCase();
        if (tagLevels.containsKey(ret.name)) {
            tagLevel = tagLevels.get(ret.name);
            tagLevel++;
        } else {
            tagLevel = 1;
        }
        tagLevels.put(ret.name, tagLevel);
        ret.tagLevel = tagLevel;

        // now read the tag parameters
        tagString = tagString.substring(1);
        int index = tagString.indexOf(" ");
        if (index > 0) {
            ret.name = tagString.substring(0, index).toLowerCase();
            tagString = tagString.substring(index + 1);

            int indexEq = tagString.indexOf("=");
            // int i = 0;
            while (indexEq > 0) {
                // i++;
                String elem = tagString.substring(0, indexEq);
                int indexQuote = tagString.substring(indexEq + 2).indexOf("\"");
                if (indexQuote < 0) {
                    // the hhc file seems broken
                    // System.out.println(tagString);
                    break;
                }
                String value = tagString.substring(indexEq + 2, indexEq + 2 + indexQuote);
                ret.elements.put(elem.toLowerCase(), value);

                tagString = tagString.substring(indexEq + 2 + indexQuote + 2);
                indexEq = tagString.indexOf("=");
            }
        }

        return ret;
    }

    public boolean hasNext() {
        return data.hasRemaining();
    }

    private String readTag() {
        skipWhitespace();

        byte[] buf = new byte[1024];
        int pos = 0;

        peek(); // skip '<'
        buf[pos++] = data.get();
        while (peek() != '>') {
            if (peek() == '=') {
                buf[pos++] = data.get();
                skipWhitespace();
                buf[pos++] = data.get(); // '"' after '='
                while (peek() != '"') {
                    buf[pos++] = data.get();
                }
                buf[pos++] = data.get();
            } else {
                buf[pos++] = data.get();
            }
        }
        buf[pos++] = data.get();

        skipWhitespace();

        String tag;
        try {
            tag = new String(buf, 0, pos, encoding);
        } catch (UnsupportedEncodingException e) {
            // System.err.println("Encoding " + encoding + " unsupported");
            tag = new String(buf, 0, pos);
        }
        return tag;
    }

    private int peek() {
        data.mark();
        int result = data.get();
        data.reset();

        return result;
    }

    private void skipWhitespace() {
        while (hasNext() &&
                Character.isWhitespace((char) peek())) {
            data.get();
        }
    }
}
