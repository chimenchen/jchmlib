package org.jchmlib.app;

import java.io.IOException;
import java.util.HashMap;
import org.jchmlib.ChmFile;

@SuppressWarnings("WeakerAccess")
public class ChmSearch {

    public static void main(String[] argv) throws IOException {
        if (argv.length != 2) {
            System.out.println("Usage: ChmSearch <chmfile> <object>");
        }
        ChmFile chmFile = new ChmFile(argv[0]);
        HashMap<String, String> results = chmFile.indexSearch(argv[1], true, false);
        if (results == null) {
            System.out.println("Object <" + argv[1] + "> not found!");
        } else {
            System.out.println("Object <" + argv[1] + "> found!");
            for (String url : results.keySet()) {
                String topic = results.get(url);
                System.out.println(topic + ":\t\t " + url);
            }
        }
    }
}
