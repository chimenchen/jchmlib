/*
 * Copyright 2017 chimenchen. All rights reserved.
 */

package org.jchmlib;

/**
 * ChmEnumerator is a <i>visitor</i> of <code>ChmFile</code>.
 * <p>
 * Used by the following functions of <code>ChmFile</code>:
 * <pre>
 * enumerateDir(String prefix, int what, ChmEnumerator e)
 * enumerate(int what, ChmEnumerator e)
 * </pre>
 *
 * @see ChmFile
 * @see ChmUnitInfo
 */
public interface ChmEnumerator {

    /**
     * Does something on the ChmUnitInfo.
     */
    void enumerate(ChmUnitInfo ui) throws ChmStopEnumeration;
}

