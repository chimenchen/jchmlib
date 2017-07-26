/* ChmTopicsTree.java 2006/05/25
 *
 * Copyright 2006 Chimen Chen. All rights reserved.
 *
 */

package org.jchmlib;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import org.jchmlib.util.Tag;
import org.jchmlib.util.TagReader;

/**
 * A ChmTopicsTree object contains the topics in a .chm archive.
 * <p>
 *
 * @author Chimen Chen
 */
public final class ChmTopicsTree {

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
    /**
     * list of children nodes
     */
    public LinkedList<ChmTopicsTree> children;
    /**
     * Mapping from paths to titles.
     */
    private HashMap<String, String> pathToTitle;

    public ChmTopicsTree() {
        title = "";
        path = "";
        children = new LinkedList<ChmTopicsTree>();
        pathToTitle = null;
    }

    /**
     * Build the top level ChmTopicsTree.
     *
     * @param buf content
     * @param encoding the encoding of buf
     */
    public static ChmTopicsTree buildTopicsTree(ByteBuffer buf,
            String encoding) {
        ChmTopicsTree tree = new ChmTopicsTree();
        tree.pathToTitle = new LinkedHashMap<String, String>();
        tree.parent = null;
        tree.title = "<Top>";

        ChmTopicsTree curRoot = tree;
        ChmTopicsTree lastNode = tree;

        TagReader tr = new TagReader(buf, encoding);
        while (tr.hasNext()) {
            Tag s = tr.getNext();
            if (s.name == null) {
                break;
            }

            if (s.name.equalsIgnoreCase("ul") && s.tagLevel > 1) {
                curRoot = lastNode;
            } else if (s.name.equalsIgnoreCase("/ul") && s.tagLevel > 0
                    && curRoot.parent != null) {
                lastNode = curRoot;
                curRoot = curRoot.parent;
            } else if (s.name.equalsIgnoreCase("object")
                    && s.elements.get("type")
                    .equalsIgnoreCase("text/sitemap")) {

                lastNode = new ChmTopicsTree();
                lastNode.parent = curRoot;

                s = tr.getNext();
                while (!s.name.equalsIgnoreCase("/object")) {
                    if (s.name.equalsIgnoreCase("param")) {
                        String name = s.elements.get("name");
                        String value = s.elements.get("value");
                        if (name == null) {
                            System.err.println("Illegal content file!");
                        } else if (name.equals("Name")) {
                            lastNode.title = value;
                        } else if (name.equals("Local")) {
                            if (value.startsWith("./")) {
                                value = value.substring(2);
                            }
                            lastNode.path = "/" + value;
                        }
                    }
                    s = tr.getNext();
                }

                curRoot.children.addLast(lastNode);

                if (!"".equals(lastNode.path)) {
                    tree.pathToTitle.put(lastNode.path.toLowerCase(),
                            lastNode.title);
                }
            }
        }

        return tree;

    }

    public String getTitle(String path, String defaultTitle) {
        if (pathToTitle != null
                && pathToTitle.containsKey(path.toLowerCase())) {
            return pathToTitle.get(path.toLowerCase());
        }
        return defaultTitle;
    }
}
