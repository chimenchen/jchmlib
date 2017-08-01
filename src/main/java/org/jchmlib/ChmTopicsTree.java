/*
 * Copyright 2017 chimenchen. All rights reserved.
 */

package org.jchmlib;

import java.util.LinkedList;

/**
 * A ChmTopicsTree object contains the topics in a .chm archive.
 */
public final class ChmTopicsTree {

    /**
     * list of children nodes
     */
    public final LinkedList<ChmTopicsTree> children;
    public int id;
    /**
     * Title of the tree node
     */
    public String title;
    /**
     * Path to file under given topic or empty
     */
    public String path;
    /**
     * Pointer to parent tree node, null if no parent
     */
    public ChmTopicsTree parent;

    public ChmTopicsTree() {
        id = 0;
        title = "";
        path = "";
        children = new LinkedList<ChmTopicsTree>();
    }
}
