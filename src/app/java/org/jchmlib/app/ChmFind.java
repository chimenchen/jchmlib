package org.jchmlib.app;

import java.io.IOException;
import org.jchmlib.ChmFile;
import org.jchmlib.ChmUnitInfo;

@SuppressWarnings("WeakerAccess")
public class ChmFind {

    public static void main(String[] argv) throws IOException {
        if (argv.length != 2) {
            System.out.println("Usage: ChmFind <chmfile> <object>");
            return;
        }
        ChmFile chmFile = new ChmFile(argv[0]);
        ChmUnitInfo ui = chmFile.resolveObject(argv[1]);
        if (ui == null) {
            System.out.println("Object <" + argv[1] + "> not found!");
        } else {
            System.out.println("Object <" + argv[1] + "> found!");
        }
    }
}
