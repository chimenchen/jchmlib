package org.jchmlib.app;

import java.io.IOException;
import org.jchmlib.ChmFile;

@SuppressWarnings("WeakerAccess")
public class ChmTopics {

    public static void main(String[] argv) throws IOException {
        if (argv.length < 1) {
            System.out.println("Usage: ChmTopics <chm-filename>");
            return;
        }
        ChmFile chmFile = new ChmFile(argv[0]);
        ChmFile.printTopicsTree(chmFile.getTopicsTree(), 0);
    }

}
