/*
 * Copyright 2017 chimenchen. All rights reserved.
 */

package org.jchmlib;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.logging.Logger;

/**
 * ByteBufferHelper provides some ByteBuffer-relating methods.
 */
class ByteBufferHelper {

    private static final Logger LOG = Logger.getLogger(ByteBufferHelper.class.getName());

    public static void skip(ByteBuffer bb, int count) throws IOException {
        try {
            bb.position(bb.position() + count);
        } catch (IllegalArgumentException e) {
            throw new IOException(e);
        }
    }

    /**
     * parse a compressed DWORD (a variant length integer)
     */
    public static long parseCWord(ByteBuffer bb) throws IOException {
        try {
            long accumulator = 0;
            byte temp = bb.get();
            while (temp < 0) {  // if the most significant bit is 1
                accumulator <<= 7;
                accumulator += temp & 0x7f;
                temp = bb.get();
            }
            return (accumulator << 7) + temp;

        } catch (BufferUnderflowException e) {
            throw new IOException(e);
        }
    }

    public static String parseString(ByteBuffer bb, String encoding) {
        int len = bb.remaining();
        return parseString(bb, len, encoding);
    }

    /**
     * Parses a String using the named Charset Encoding.
     */
    public static String parseString(ByteBuffer bb, int strLen, String encoding) {
        int length = Math.min(bb.remaining(), strLen);
        if (length <= 0) {
            return "";
        }

        byte[] buf = new byte[length];
        bb.get(buf);

        for (int j = 0; j < buf.length; j++) {
            if (buf[j] == 0) {
                length = j;
                break;
            }
        }

        return bytesToString(buf, 0, length, encoding);
    }

    /**
     * Gets the remaining contents of the given java.nio.ByteBuffer
     * (i.e. the bytes between its position and limit) as a String.
     * Leaves the position of the ByteBuffer unchanged.
     */
    public static String peakAsString(ByteBuffer buf, String encoding) {
        if (buf == null || buf.remaining() == 0) {
            return "";
        }
        // First check to see if the input buffer has a backing array;
        // if so, we can just use it, to save making a copy of the data
        byte[] bytes;
        if (buf.hasArray()) {
            bytes = buf.array();
            return bytesToString(bytes, buf.position(), buf.remaining(),
                    encoding);
        } else {
            // Remember the original position of the buffer
            int pos = buf.position();
            bytes = new byte[buf.remaining()];
            buf.get(bytes);
            // Reset the original position of the buffer
            buf.position(pos);
            return bytesToString(bytes, encoding);
        }
    }

    private static String bytesToString(byte[] bytes, String encoding) {
        return bytesToString(bytes, 0, bytes.length, encoding);
    }

    private static String bytesToString(byte[] bytes, int offset,
            int length, String encoding) {
        String result;
        try {
            result = new String(bytes, offset, length, encoding);
        } catch (UnsupportedEncodingException ignored) {
            result = new String(bytes, offset, length);
            LOG.info("String not in encoding " + encoding + ": " + ignored);
            LOG.info("String in default encoding:" + result);
        }
        return result;
    }
}

