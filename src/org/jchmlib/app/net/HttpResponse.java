package org.jchmlib.app.net;

import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

/**
 * HTTP Response.<p>
 * <p>
 * This class manages the headers, trailers and content streams
 * of a HTTP response.<p>
 * <p>
 * This class is not synchronized. It should be explicitly
 * synchronized if it is used by multiple threads.<p>
 */
public class HttpResponse {

    private PrintStream writer;

    public HttpResponse(OutputStream out, String encoding) {
        try {
            this.writer = new PrintStream(out, true, encoding);
        } catch (UnsupportedEncodingException e) {
            // System.err.println("Encoding " + encoding
            //         + " unsupported:\n" + e);
            this.writer = new PrintStream(out, true);
        }
    }

    public PrintStream getWriter() {
        return writer;
    }

    /**
     * Send a HTTP header to the client
     * The first line is a status message from the server to the client.
     * The second line holds the mime type of the document.
     */
    public void sendHeader(String mimeType) {
        writer.println("HTTP/1.0 200 OK");
        writer.println("Content-type: " + mimeType + "\n");
    }

    /**
     * Writes a string to the client.
     */
    public void sendString(String str) {
        writer.print(str);
    }

    /**
     * Writes a line to the client.
     */
    public void sendLine(String str) {
        writer.println(str);
    }

    public void write(ByteBuffer buffer, int length) {
        if (buffer == null) {
            return;
        }

        byte[] bytes = new byte[length];
        while (buffer.hasRemaining()) {
            buffer.get(bytes);
            writer.write(bytes, 0, bytes.length);
        }
    }

    public void write(byte[] bytes, int offset, int length) {
        if (bytes == null || bytes.length == 0 || length == 0) {
            return;
        }

        writer.write(bytes, offset, length);
    }
}
