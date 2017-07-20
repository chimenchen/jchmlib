package org.jchmlib.app;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import org.jchmlib.ChmEnumerator;
import org.jchmlib.ChmFile;
import org.jchmlib.ChmUnitInfo;

public class ChmExtract {

    public static void main(String[] argv) throws IOException {

        if (argv.length < 1) {
            System.out.println("Usage: ChmExtract <chmfile> <outdir>");
            return;
        }

        long time_prev = System.currentTimeMillis();

        ChmFile chmFile = new ChmFile(argv[0]);

        System.out.println("/:" + argv[0]);
        chmFile.enumerate(ChmFile.CHM_ENUMERATE_ALL,
                new Extractor(chmFile, argv[1]));
        long time = System.currentTimeMillis();
        System.out.println("    finished in " + (time - time_prev) + " ms");
        System.out.println();
    }
}

class Extractor implements ChmEnumerator {

    private String basePath;
    private ChmFile chmFile;


    public Extractor(ChmFile chmFile, String basePath) {
        this.chmFile = chmFile;
        if (basePath.endsWith("/")) {
            this.basePath = basePath.substring(0,
                    basePath.length() - 1);
        } else {
            this.basePath = basePath;
        }
    }

    public void enumerate(ChmUnitInfo ui) {
        if (!ui.path.startsWith("/")) {
            return;
        }

        String fullPath = new File(basePath, ui.path).toString();

        if (ui.length != 0) {
            PrintStream out = null;
            try {
                out = new PrintStream(fullPath);
            } catch (IOException e) {
                System.out.println("   fail while opening the newly created file "
                        + ui.path);
            }
            if (out == null) {
                System.out.println("   fail to open the newly created file "
                        + ui.path);
                return;
            }

            ByteBuffer buffer = chmFile.retrieveObject(ui, 0, ui.length);
            if (buffer == null) {
                System.out.println("    extract failed on " + ui.path);
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
        } else {
            if (fullPath.endsWith("/")) {
                new File(fullPath).mkdirs();
            } else {
                new File(fullPath).delete();
            }
        }
    }
}
