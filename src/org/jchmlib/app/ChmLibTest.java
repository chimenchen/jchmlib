package org.jchmlib.app;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import org.jchmlib.ChmFile;
import org.jchmlib.ChmUnitInfo;

public class ChmLibTest {

    public static void main(String[] argv) throws IOException {
        if (argv.length < 3) {
            System.out.println("Usage: ChmLibTest <chm-file> <page-file> <dest-page-file>");
            return;
        }

        ChmFile chmFile = new ChmFile(argv[0]);

        System.out.println("Resolving " + argv[1]);

        ChmUnitInfo ui = chmFile.resolveObject(argv[1]);
        if (ui == null) {
            System.out.println("failed to resolve " + argv[1]);
            return;
        }

        System.out.println("Extracting to " + argv[2]);
        ByteBuffer buffer = chmFile.retrieveObject(ui, 0, ui.length);
        if (buffer == null) {
            System.out.println("    extract failed on " + ui.path);
            return;
        }

        PrintStream out;
        try {
            out = new PrintStream(argv[2]);
        } catch (Exception ignored) {
            System.err.println("failed to open output:" + argv[2]);
            return;
        }

        int gotLen = buffer.limit() - buffer.position();
        byte[] bytes = new byte[gotLen];
        buffer.mark();
        while (buffer.hasRemaining()) {
            buffer.get(bytes);
            out.write(bytes, 0, gotLen);
        }
        buffer.reset();
        out.close();
        System.out.println("   finished");
    }
}
