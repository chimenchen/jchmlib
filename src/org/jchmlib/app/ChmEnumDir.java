package org.jchmlib.app;

import java.io.IOException;
import java.io.PrintStream;
import org.jchmlib.ChmEnumerator;
import org.jchmlib.ChmFile;
import org.jchmlib.ChmUnitInfo;

public class ChmEnumDir {

    public static void main(String[] argv) throws IOException {

        if (argv.length < 1) {
            System.out.println("Usage: ChmEnumDir <chmfile> [dir] [dir] ...");
            return;
        }

        ChmFile chmFile = new ChmFile(argv[0]);

        if (argv.length < 2) {
            System.out.println("/:");
            System.out.println(" spc\tstart\tlength\ttype\t\tname");
            System.out.println(" ===\t=====\t======\t====\t\t====");
            chmFile.enumerateDir("/", ChmFile.CHM_ENUMERATE_ALL,
                    new DirEnumerator(System.out));
        } else {
            for (int i = 1; i < argv.length; i++) {
                System.out.println(argv[i] + ":");
                System.out.println(" spc\tstart\tlength\tname");
                System.out.println(" ===\t=====\t======\t====");
                chmFile.enumerateDir(argv[i], ChmFile.CHM_ENUMERATE_ALL,
                        new DirEnumerator(System.out));

            }
        }

    }
}

class DirEnumerator implements ChmEnumerator {

    private PrintStream out;

    public DirEnumerator(PrintStream out) {
        this.out = out;
    }

    public void enumerate(ChmUnitInfo ui) {
        String szBuf;
        if ((ui.flags & ChmFile.CHM_ENUMERATE_NORMAL) != 0) {
            szBuf = "normal ";
        } else if ((ui.flags & ChmFile.CHM_ENUMERATE_SPECIAL) != 0) {
            szBuf = "special ";
        } else if ((ui.flags & ChmFile.CHM_ENUMERATE_META) != 0) {
            szBuf = "meta ";
        } else {
            return;
        }

        if ((ui.flags & ChmFile.CHM_ENUMERATE_DIRS) != 0) {
            szBuf += "dir";
        } else if ((ui.flags & ChmFile.CHM_ENUMERATE_FILES) != 0) {
            szBuf += "file";
        }

        out.println(" " + ui.space + "\t" + ui.start + "\t" +
                ui.length + "\t" + szBuf + "\t" + ui.path);
    }
}
