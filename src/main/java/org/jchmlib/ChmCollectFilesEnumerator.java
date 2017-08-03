package org.jchmlib;

import java.util.ArrayList;

public class ChmCollectFilesEnumerator implements ChmEnumerator {

    public final ArrayList<ChmUnitInfo> files;

    public ChmCollectFilesEnumerator() {
        files = new ArrayList<ChmUnitInfo>();
    }

    public void enumerate(ChmUnitInfo ui) {
        files.add(ui);
    }
}
