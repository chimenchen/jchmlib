/* DefaultChmEnumerator.java 2006/05/25
 *
 * Copyright 2006 Chimen Chen. All rights reserved.
 *
 */

package org.jchmlib;

import java.io.PrintStream;

/**
 * A default implementation of <code>ChmEnumerator</code>.
 */
public class DefaultChmEnumerator implements ChmEnumerator {

    private PrintStream out;

    public DefaultChmEnumerator(PrintStream out) {
        this.out = out;
    }

    public void enumerate(ChmUnitInfo ui) {
        out.println(ui.path);
    }
}

