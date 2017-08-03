package org.jchmlib.app;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.jchmlib.ChmFile;
import org.jchmlib.ChmSearchEnumerator;

@SuppressWarnings("WeakerAccess")
public class ChmSlowSearch {

    public static void main(String[] argv) throws IOException {
        if (argv.length < 2) {
            System.out.println("Usage: ChmSlowSearch <chm-file> <keyword> ...");
            return;
        }

        ChmFile chmFile = new ChmFile(argv[0]);

        ChmSearchEnumerator enumerator = new ChmSearchEnumerator(chmFile, argv[1], 0);
        chmFile.enumerate(ChmFile.CHM_ENUMERATE_USER, enumerator);
        HashMap<String, String> results = enumerator.getResults();
        if (results.size() == 0) {
            System.out.println("No match.");
            return;
        }
        for (Map.Entry<String, String> entry : results.entrySet()) {
            String url = entry.getKey();
            String topic = entry.getValue();
            System.out.println(url + " => " + topic);
        }
    }
}

