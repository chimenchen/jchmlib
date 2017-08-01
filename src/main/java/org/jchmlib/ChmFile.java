/*
 * Copyright 2017 chimenchen. All rights reserved.
 */

package org.jchmlib;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.logging.Logger;

/**
 * ChmFile is a class for dealing with Microsoft CHM format files
 * (also known as Html Help files).
 */

public class ChmFile {

    /**
     * Path starts with "/", but not "/#" and "/$".
     */
    public final static int CHM_ENUMERATE_NORMAL = 1;
    /**
     * Path does not start with "/".
     */
    public final static int CHM_ENUMERATE_META = 2;
    /**
     * Path starts with "/#" or "/$".
     */
    public final static int CHM_ENUMERATE_SPECIAL = 4;
    /**
     * Path does not end with "/".
     */
    public final static int CHM_ENUMERATE_FILES = 8;
    /**
     * Path ends with "/".
     */
    public final static int CHM_ENUMERATE_DIRS = 16;
    /**
     * CHM_ENUMERATE_NORMAL |
     * CHM_ENUMERATE_FILES |
     * CHM_ENUMERATE_DIRS
     */
    public final static int CHM_ENUMERATE_USER = 25;
    /**
     * CHM_ENUMERATE_NORMAL |
     * CHM_ENUMERATE_META |
     * CHM_ENUMERATE_SPECIAL |
     * CHM_ENUMERATE_FILES |
     * CHM_ENUMERATE_DIRS
     */
    public final static int CHM_ENUMERATE_ALL = 31;

    final static int FTS_HEADER_LEN = 0x82;  // was 0x32;
    private final static int CHM_LZXC_RESETTABLE_V1_LEN = 0x28;
    private final static int CHM_ITSF_V3_LEN = 0X60;
    private final static int CHM_ITSP_V1_LEN = 0X54;
    private final static int CHM_COMPRESSED = 1;
    private final static int CHM_UNCOMPRESSED = 0;

    // names of sections essential to decompression
    private final static String CHMU_RESET_TABLE =
            "::DataSpace/Storage/MSCompressed/Transform/" +
                    "{7FC28940-9D31-11D0-9B27-00A0C91E9C7C}/" +
                    "InstanceData/ResetTable";
    private final static String CHMU_LZXC_CONTROLDATA =
            "::DataSpace/Storage/MSCompressed/ControlData";
    private final static String CHMU_CONTENT =
            "::DataSpace/Storage/MSCompressed/Content";

    private final static Logger LOG = Logger.getLogger(ChmFile.class.getName());
    /**
     * Mapping from paths to {@link ChmUnitInfo} objects.
     */
    // LinkedHashMap, since we want it to remember the order
    // mappings are inserted.
    private final HashMap<String, ChmUnitInfo> dirMap = new LinkedHashMap<String, ChmUnitInfo>();
    String encoding = "UTF-8";
    /**
     * Mapping from paths to titles.
     */
    private HashMap<String, String> pathToTitle = null;
    private int detectedLCID = -1;
    private String homeFile;
    private String topicsFile;
    private String indexFile;
    private String title;
    private String generator;
    private RandomAccessFile rf;
    private int langIDInItsfHeader;
    /**
     * Offset within file of content section 0
     */
    private long dataOffset;
    private int blockUncompressedLen;
    /**
     * absolute offsets of blocks (when compressed) in file.
     */
    private long[] resetTable;
    private int windowSize;
    private int resetBlockCount;
    private boolean compressionDisabled = false;
    // decompressor
    private LZXInflator lzxInflator;
    /*
     * A {@link ChmTopicsTree} object containing topics in the Chm file.
     */
    private ChmTopicsTree tree;
    private ChmIndexSearcher indexSearcher = null;

    /**
     * Creates a new ChmFile.
     *
     * @param filename the system-dependent filename of the CHM file
     * @throws IOException if the file doesn't exist or the file is of the wrong format.
     */
    public ChmFile(String filename) throws IOException {
        try {
            rf = new RandomAccessFile(filename, "r");
        } catch (Exception e) {
            LOG.info("Error open CHM file: " + e);
            throw new IOException(e);
        }

        readInitialHeaderAndDirectory();
        readResetTable();
        readControlData();
        initInflator();
        initMiscFiles(filename);
    }

    /**
     * Prints the ChmTopicsTree of this .chm archive.
     *
     * @param tree the topics tree returned by buildTopicsTree.
     * @param level the level of tree, starts from 0, used for indentation.
     */
    public static void printTopicsTree(ChmTopicsTree tree, int level) {
        if (tree == null) {
            return;
        }

        for (int i = 0; i < level; i++) {
            System.out.print("    ");
        }

        System.out.println(tree.title + "\t" + tree.path);

        for (ChmTopicsTree child : tree.children) {
            printTopicsTree(child, level + 1);
        }
    }

    /**
     * Find first bit set in a word.
     */
    private static int ffs(int val) {
        int bit = 1, idx = 1;
        while (bit != 0 && (val & bit) == 0) {
            bit <<= 1;
            ++idx;
        }
        if (bit == 0) {
            return 0;
        } else {
            return idx;
        }
    }

    /**
     * @return Path of the main file (default file to show on startup).
     */
    public String getHomeFile() {
        return homeFile;
    }

    /**
     * @return .hhc file which gives a topic tree
     */
    @SuppressWarnings("unused")
    public String getTopicsFile() {
        return topicsFile;
    }

    /**
     * @return .hhk file which gives a index tree
     */
    @SuppressWarnings("unused")
    public String getIndexFile() {
        return indexFile;
    }

    /**
     * @return The title of this .chm archive
     */
    public String getTitle() {
        return title;
    }

    /**
     * @return The generator of this .chm archive. Normally, it would be HHA Version 4.74.8702.
     */
    @SuppressWarnings("unused")
    public String getGenerator() {
        return generator;
    }

    /**
     * @return The character encoding of this .chm archive
     */
    public String getEncoding() {
        return encoding;
    }

    /**
     * @return The language codepage ID of this .chm archive
     */
    @SuppressWarnings("unused")
    public int getDetectedLCID() {
        return detectedLCID;
    }

    private void readInitialHeaderAndDirectory() throws IOException {
        ByteBuffer bb = fetchBytesOrFail(0, CHM_ITSF_V3_LEN, "Failed to read ITSF header");
        ChmItsfHeader itsfHeader = new ChmItsfHeader(bb);
        LOG.info(String.format("Language ID: 0x%x", itsfHeader.langId));

        langIDInItsfHeader = itsfHeader.langId;
        // dirOffset = itsfHeader.dirOffset;
        dataOffset = itsfHeader.dataOffset;

        readDirectory(itsfHeader.dirOffset);
    }

    private void readDirectory(long dirOffset) throws IOException {
        ByteBuffer bb = fetchBytesOrFail(dirOffset, CHM_ITSP_V1_LEN, "Failed to read ITSP header");
        ChmItspHeader itspHeader = new ChmItspHeader(bb);

        // grab essential information from ITSP header
        dirOffset += itspHeader.headerLen;
        // int indexRoot = itspHeader.indexRoot;
        int indexHead = itspHeader.indexHead;
        // dirBlockLen = itspHeader.blockLen;

        // if the index root is -1, this means we don't have any PMGI blocks.
        // as a result, we must use the sole PMGL block as the index root
        // if (indexRoot <= -1) {
        // indexRoot = indexHead;
        // }

        readDirectoryTable(indexHead, dirOffset, itspHeader.blockLen);
    }

    private void readDirectoryTable(int indexHead, long dirOffset, int dirBlockLen)
            throws IOException {
        int curPage = indexHead;
        while (curPage != -1) {
            ByteBuffer buf = fetchBytesOrFail(
                    dirOffset + curPage * dirBlockLen, dirBlockLen,
                    "Failed to read directory table");
            ChmPmglHeader header = new ChmPmglHeader(buf);

            // scan directory listing entries
            while (buf.position() < dirBlockLen - header.freeSpace) {
                // parse a PMGL entry into a ChmUnitInfo object
                ChmUnitInfo ui = new ChmUnitInfo(buf);
                dirMap.put(ui.path.toLowerCase(), ui);
                if (ui.path.toLowerCase().endsWith(".hhc")) {
                    dirMap.put("/@contents", ui);
                }
            }

            // advance to next page
            curPage = header.blockNext;
        }
    }

    private void readResetTable() throws IOException {
        compressionDisabled = false;

        ChmUnitInfo uiResetTable = resolveObject(CHMU_RESET_TABLE);
        ChmUnitInfo uiContent = resolveObject(CHMU_CONTENT);
        ChmUnitInfo uiLzxc = resolveObject(CHMU_LZXC_CONTROLDATA);
        if (uiResetTable == null || uiResetTable.space == CHM_COMPRESSED ||
                uiContent == null || uiContent.space == CHM_COMPRESSED ||
                uiLzxc == null || uiLzxc.space == CHM_COMPRESSED) {
            LOG.fine("Compression is disabled.");
            compressionDisabled = true;
            return;
        }

        ByteBuffer buffer = retrieveObject(uiResetTable);
        if (buffer == null || buffer.remaining() < CHM_LZXC_RESETTABLE_V1_LEN) {
            LOG.fine("Failed to retrieve reset table.");
            compressionDisabled = true;
            return;
        }

        ChmLzxcResetTable resetTableHeader = new ChmLzxcResetTable(buffer);
        blockUncompressedLen = (int) resetTableHeader.blockLen;
        int blockCount = resetTableHeader.blockCount;

        buffer.position(resetTableHeader.tableOffset);
        /* each entry in the reset table is 8-bytes long */
        if (buffer.remaining() < blockCount * 8) {
            throw new IOException("Reset table is corrupted.");
        }

        int contentOffset = (int) uiContent.start;
        resetTable = new long[blockCount + 1];
        for (int i = 0; i < blockCount; i++) {
            resetTable[i] = dataOffset + contentOffset + buffer.getLong();
        }
        resetTable[resetTableHeader.blockCount] = dataOffset + contentOffset +
                resetTableHeader.compressedLen;
    }

    private void readControlData() throws IOException {
        ChmUnitInfo uiLzxc = resolveObject(CHMU_LZXC_CONTROLDATA);
        if (uiLzxc == null) {
            LOG.fine("No LZXC control data found.");
            compressionDisabled = true;
            return;
        }

        ByteBuffer buffer = retrieveObject(uiLzxc, 0, uiLzxc.length);
        if (buffer == null) {
            LOG.fine("Failed to retrieve LZXC control data");
            compressionDisabled = true;
            return;
        }

        ChmLzxcControlData ctlData = new ChmLzxcControlData(buffer);
        resetBlockCount = ctlData.resetInterval / (ctlData.windowSize / 2) *
                ctlData.windowsPerReset;
        windowSize = ctlData.windowSize;
    }

    private void initInflator() {
        // real window size is 2^lwindow_size
        int lwindow_size = ffs(windowSize) - 1;
        lzxInflator = new LZXInflator(lwindow_size);
    }

    private void initMiscFiles(String filename) {
        try {
            initMiscFilesWithoutCatch();
        } catch (Exception ignored) {
        }

        if (topicsFile == null) {
            topicsFile = "";
        }
        if (indexFile == null) {
            indexFile = "";
        }
        if (homeFile == null) {
            homeFile = "";
        }
        if (homeFile.equals("/")) {
            if (resolveObject("/cover.html") != null) {
                homeFile = "/cover.html";
            } else if (resolveObject("/cover.htm") != null) {
                homeFile = "/cover.htm";
            } else if (resolveObject("/index.html") != null) {
                homeFile = "/index.html";
            } else if (resolveObject("/index.htm") != null) {
                homeFile = "/index.htm";
            }
        }
        if (title == null || title.length() == 0) {
            title = filename.replaceFirst("[.][^.]+$", "")
                    .replaceAll(".*[\\\\/]|\\.[^.]*$", "");
        }
        if (encoding == null || encoding.length() == 0) {
            // this langID may still be wrong.
            encoding = EncodingHelper.findEncoding(langIDInItsfHeader);
            LOG.info("Fallback Encoding: " + encoding);
        } else if (encoding.startsWith("CP")) {
            String encodingInItsfHeader = EncodingHelper.findEncoding(langIDInItsfHeader);
            if (!encodingInItsfHeader.startsWith("CP")) { // better
                encoding = encodingInItsfHeader;
                LOG.info("Fixed Encoding: " + encoding);
            }
        }
        if (generator == null) {
            generator = "";
        }
    }

    private void initMiscFilesWithoutCatch() throws IOException {
        // int type, len;
        // String data;

        ChmUnitInfo system = resolveObject("/#SYSTEM");
        if (system == null) {
            LOG.fine("The #SYSTEM object doesn't exist.");
            return;
        }

        ByteBuffer buf = retrieveObject(system);
        if (buf == null) {
            return;
        }

        buf.getInt();
        while (buf.hasRemaining()) {
            int type = buf.getShort();
            int len = buf.getShort();
            switch (type) {
                case 0:
                    topicsFile = "/" + ByteBufferHelper.parseString(buf, len, encoding);
                    LOG.fine("topics file: " + topicsFile);
                    break;
                case 1:
                    indexFile = "/" + ByteBufferHelper.parseString(buf, len, encoding);
                    LOG.fine("index file: " + indexFile);
                    break;
                case 2:
                    homeFile = "/" + ByteBufferHelper.parseString(buf, len, encoding);
                    LOG.info("home file: " + homeFile);
                    break;
                case 3:
                    title = ByteBufferHelper.parseString(buf, len, encoding);
                    LOG.info("title: " + title);
                    break;
                case 4:
                    detectedLCID = buf.getInt();
                    encoding = EncodingHelper.findEncoding(detectedLCID);
                    LOG.info(String.format("Language ID: 0x%x", detectedLCID));
                    LOG.info("Encoding: " + encoding);
                    ByteBufferHelper.skip(buf, len - 4);
                    break;
                case 9:
                    generator = ByteBufferHelper.parseString(buf, len, encoding);
                    LOG.fine("Generator: " + generator);
                    break;
                default:
                    // LOG.fine(String.format("Unknown type: %d, len %d", type, len));
                    ByteBufferHelper.skip(buf, len);
            }
        }
    }

    /**
     * Resolve a particular object from the Chm file.
     *
     * @param objPath the path of the object. It can be "::DataSpace/Storage/MSCompressed/SpanInfo"
     * "/index.html" or "/files/", etc.
     * @return the CHM unit info matching the path, or null if not found.
     */
    public ChmUnitInfo resolveObject(String objPath) {
        if (dirMap != null && objPath != null && dirMap.containsKey(objPath.toLowerCase())) {
            return dirMap.get(objPath.toLowerCase());
        }
        return null;
    }

    /**
     * Retrieve an object.
     *
     * @param ui an abstract representation of the object.
     * @return a ByteBuffer holding the content of the object,
     * or null if ui is invalid or there is error when retrieving the object.
     */
    public ByteBuffer retrieveObject(ChmUnitInfo ui) {
        return retrieveObject(ui, 0, ui.length);
    }

    /**
     * retrieve (part of) an object
     *
     * @param ui an abstract representation of the object.
     * @param addr starting address(relative to start of the object)
     * @param len length(in bytes) to be retrieved.
     *
     * @return a ByteBuffer holding (part of) the content of the object,
     * or null if ui is invalid or there is error when retrieving the object.
     */
    public ByteBuffer retrieveObject(ChmUnitInfo ui, long addr, long len) {

        ByteBuffer buf = null;

        // starting address must be in correct range
        if (addr < 0 || addr >= ui.length) {
            return null;
        }

        // clip length
        if (addr + len > ui.length) {
            len = ui.length - addr;
        }

        // if the file is uncompressed, it's simple
        if (ui.space == CHM_UNCOMPRESSED) {
            // read data
            buf = fetchBytes(dataOffset + ui.start + addr, len);
        } else {
            if (compressionDisabled) {
                return null;
            }

            long numSaved = 0;
            while (numSaved < len) {
                ByteBuffer buf0 = decompressRegion(ui.start + addr + numSaved, len - numSaved);
                if (buf0 == null || buf0.remaining() == 0) {
                    break;
                }

                if (buf == null) {
                    buf = ByteBuffer.allocate((int) len);
                    buf.order(ByteOrder.LITTLE_ENDIAN);
                }

                int numRead = buf0.remaining();
                buf.put(buf0.array(), buf0.arrayOffset() + buf0.position(), numRead);
                numSaved += numRead;
            }

            if (buf != null) {
                buf.limit((int) numSaved);
                buf.position(0);    // wind to the start of the buffer
            }
        }

        return buf;
    }

    /**
     * Decompress a region in the CHM file.
     *
     * @param start starting offset(relative to the start of a CHM file)
     * @param len length in bytes
     */
    private synchronized ByteBuffer decompressRegion(long start, long len) {
        if (len <= 0) {
            return null;
        }

        // figure out what we need to read
        int nBlock = (int) (start / blockUncompressedLen);
        int nOffset = (int) (start % blockUncompressedLen);

        int nLen = (int) len;
        if (nLen > (blockUncompressedLen - nOffset)) {
            nLen = blockUncompressedLen - nOffset;
        }

        // decompress some data
        ByteBuffer buf = decompressBlock(nBlock);
        if (buf == null) {
            return null;
        }
        buf.position(nOffset);
        buf.limit(nOffset + nLen);
        buf.order(ByteOrder.LITTLE_ENDIAN);

        return buf;
    }

    /**
     * Decompress a block.
     */
    private synchronized ByteBuffer decompressBlock(int block) {
        int blockAlign = block % resetBlockCount; // reset interval align

        // check if we need previous blocks
        if (blockAlign != 0) {
            // fetch all required previous blocks since last reset
            for (int i = blockAlign; i > 0; i--) {
                int curBlockIdx = block - i;

                if ((curBlockIdx % resetBlockCount) == 0) {
                    lzxInflator.reset();
                }

                ByteBuffer buf0 = fetchBytes(resetTable[curBlockIdx],
                        resetTable[curBlockIdx + 1] - resetTable[curBlockIdx]);
                if (buf0 == null) {
                    return null;
                }
                // this is necessary!
                lzxInflator.decompress(buf0, blockUncompressedLen);
            }
        } else {
            if ((block % resetBlockCount) == 0) {
                lzxInflator.reset();
            }
        }

        ByteBuffer buf0 = fetchBytes(resetTable[block],
                resetTable[block + 1] - resetTable[block]);
        if (buf0 == null) {
            return null;
        }

        return lzxInflator.decompress(buf0, blockUncompressedLen);
    }

    private boolean unitTypeMatched(ChmUnitInfo ui, int typeBits, int filterBits) {
        return (typeBits & ui.flags) != 0 && !(filterBits != 0 && (filterBits & ui.flags) == 0);
    }

    /**
     * Enumerate the objects in a directories.
     * Directories are objects whose paths end with "/".
     * If a object is directories itself, we won't recursively
     * enumerate the objects in it.
     *
     * @param prefix Prefix of the directories to be enumerated
     * @param what types of directories to be enumerated. could be one of(or the combination of) the
     * following: <ul> <li>CHM_ENUMERATE_NORMAL,</li> <li>CHM_ENUMERATE_META,</li>
     * <li>CHM_ENUMERATE_SPECIAL,</li> <li>CHM_ENUMERATE_FILES,</li> <li>CHM_ENUMERATE_DIRS,</li>
     * <li>CHM_ENUMERATE_ALL,</li> <li>CHM_ENUMERATE_USER.</li> </ul>
     * @param e the enumerator which does something on the enumerated objects(like callback function
     * in C/C++).
     */
    public void enumerateDir(String prefix, int what, ChmEnumerator e) {
        // set to true once we've started
        boolean it_has_begun = false;

        int type_bits = (what & 0x7);
        int filter_bits = (what & 0xF8);

        // initialize pathname state
        if (!prefix.endsWith("/")) {
            prefix += "/";
        }

        String lastPath = null;

        for (ChmUnitInfo ui : dirMap.values()) {
            //check if we should start
            if (!it_has_begun) {
                if (ui.path.startsWith(prefix)) {
                    it_has_begun = true;
                    if (ui.path.equals(prefix)) {
                        continue;
                    }
                } else {
                    continue;
                }
            }
            // check if we should stop
            else if (!ui.path.startsWith(prefix)) {
                return;
            }

            // check if we should include this path
            if (lastPath != null && ui.path.startsWith(lastPath)) {
                continue;
            }

            String title = ui.path.substring(prefix.length());
            int index = title.indexOf("/");
            if (index > 0 && index < title.length() - 1) {
                // not a direct child dir/file under prefix
                // create a fake ui for the direct child dir
                ui = new ChmUnitInfo(ui.path.substring(0, prefix.length() + index + 1));
            }

            lastPath = ui.path;

            if (unitTypeMatched(ui, type_bits, filter_bits)) {
                // call the enumerator
                try {
                    e.enumerate(ui);
                } catch (ChmStopEnumeration ignored) {
                    break;
                }
            }
        }
    }

    /**
     * Enumerate the objects in the .chm archive.
     *
     * @param what types of objects to be enumerated. could be one of(or the combination of) the
     * following: <ul> <li>CHM_ENUMERATE_NORMAL,</li> <li>CHM_ENUMERATE_META,</li>
     * <li>CHM_ENUMERATE_SPECIAL,</li> <li>CHM_ENUMERATE_FILES,</li> <li>CHM_ENUMERATE_DIRS,</li>
     * <li>CHM_ENUMERATE_ALL,</li> <li>CHM_ENUMERATE_USER.</li> </ul>
     * @param e the enumerator which does something on the enumerated objects(like callback function
     * in C/C++).
     */
    public void enumerate(int what, ChmEnumerator e) {

        int type_bits = (what & 0x7);
        int filter_bits = (what & 0xF8);

        for (ChmUnitInfo ui : dirMap.values()) {
            if (unitTypeMatched(ui, type_bits, filter_bits)) {
                // call the enumerator
                try {
                    e.enumerate(ui);
                } catch (ChmStopEnumeration ignored) {
                    break;
                }
            }
        }
    }

    /**
     * Retrieves the ChmTopicsTree of this .chm archive.
     *
     * @return a topics tree
     */
    public ChmTopicsTree getTopicsTree() {
        if (tree != null) {
            return tree;
        }

        ChmUnitInfo ui = resolveObject("/@contents");
        if (ui == null) {
            return null;
        }

        ByteBuffer buf = retrieveObject(ui, 0, ui.length);
        if (buf == null) {
            return null;
        }

        tree = buildTopicsTree(buf, encoding);
        return tree;
    }

    /**
     * Build the top level ChmTopicsTree.
     *
     * @param buf content
     * @param encoding the encoding of buf
     * @return the topics tree
     */
    private ChmTopicsTree buildTopicsTree(ByteBuffer buf, String encoding) {
        pathToTitle = new LinkedHashMap<String, String>();

        int currentID = 0;

        ChmTopicsTree tree = new ChmTopicsTree();
        tree.parent = null;
        tree.title = "<Top>";
        tree.id = currentID++;

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
                lastNode.id = currentID++;
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
                    pathToTitle.put(lastNode.path.toLowerCase(),
                            lastNode.title);
                }
            }
        }

        tree.id = currentID;
        return tree;
    }

    /**
     * Gets the title of a given path from topics tree.
     *
     * @param path path of a Chm object (namely, a chm unit).
     * @return the title of the Chm object,
     * or return the path if no topic found for the object.
     */
    public String getTitleOfObject(String path) {
        if (pathToTitle == null && tree == null) {
            tree = getTopicsTree();
        }

        if (pathToTitle != null
                && pathToTitle.containsKey(path.toLowerCase())) {
            return pathToTitle.get(path.toLowerCase());
        }
        return path;
    }

    /**
     * Some files have large topics tree.
     * You can release it once it is no longer needed.
     * @param forceRelease release it even when the topics tree is not large.
     */
    @SuppressWarnings("SameParameterValue")
    public void releaseLargeTopicsTree(boolean forceRelease) {
        if (forceRelease || (tree != null && tree.id > 60000)) {
            tree = null;
        }
    }

    /**
     * Perform a full-text search on a .chm archive.
     * To do this, the .chm archive should be searchable.
     *
     * @param text keyword.
     * @param wholeWords if false, matches indices that starts with text.
     * @param titlesOnly if true, search titles only;
     * @return a hash map from url to title.
     */
    @SuppressWarnings("SameParameterValue")
    public HashMap<String, String> indexSearch(
            String text, boolean wholeWords, boolean titlesOnly) {
        ChmIndexSearcher searcher = getIndexSearcher();
        searcher.search(text, wholeWords, titlesOnly);
        return searcher.getResults();
    }

    public ChmIndexSearcher getIndexSearcher() {
        if (indexSearcher == null) {
            indexSearcher = new ChmIndexSearcher(this);
        }
        return indexSearcher;
    }

    private ByteBuffer fetchBytesWithoutCatch(long offset, long len)
            throws IllegalArgumentException, IOException {
        ByteBuffer buf = rf.getChannel().map(
                FileChannel.MapMode.READ_ONLY, offset, len);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        return buf;
    }

    private synchronized ByteBuffer fetchBytesOrFail(long offset, long len, String exceptionMessage)
            throws IOException {
        try {
            return fetchBytesWithoutCatch(offset, len);
        } catch (Exception e) {
            if (exceptionMessage == null || exceptionMessage.length() == 0) {
                exceptionMessage = "Failed to fetch bytes";
            }
            LOG.fine(exceptionMessage + ": " + e);
            throw new IOException(exceptionMessage, e);
        }

    }

    private synchronized ByteBuffer fetchBytes(long offset, long len) {
        try {
            return fetchBytesWithoutCatch(offset, len);
        } catch (Exception e) {
            LOG.fine("Failed to fetch bytes: " + e);
            return null;
        }
    }
}
