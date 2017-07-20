package org.jchmlib.app;

import java.io.IOException;
import java.util.ArrayList;
import org.jchmlib.ChmFile;
import org.jchmlib.ChmSearchEnumerator;

public class ChmSlowSearch {

    public static void main(String[] argv) throws IOException {
        if (argv.length < 2) {
            System.out.println("Usage: ChmSlowSearch <chm-file> <keyword> ...");
            return;
        }

        ChmFile chmFile = new ChmFile(argv[0]);

        ChmSearchEnumerator enumerator = new ChmSearchEnumerator(chmFile, argv[1]);
        chmFile.enumerate(ChmFile.CHM_ENUMERATE_USER, enumerator);
        ArrayList<String> results = enumerator.getResults();
        if (results.size() == 0) {
            System.out.println("No match.");
            return;
        }
        for (String path : results) {
            System.out.println(path);
        }
    }
}

