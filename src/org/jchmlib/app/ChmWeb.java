/* ChmEnumerator.java 06/05/25
 *
 * Copyright 2006 Chimen Chen. All rights reserved.
 *
 */

package org.jchmlib.app;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import org.jchmlib.ChmEnumerator;
import org.jchmlib.ChmFile;
import org.jchmlib.ChmIndexSearcher;
import org.jchmlib.ChmSearchEnumerator;
import org.jchmlib.ChmTopicsTree;
import org.jchmlib.ChmUnitInfo;
import org.jchmlib.app.net.HttpRequest;
import org.jchmlib.app.net.HttpResponse;

/**
 * A simple web server.
 * You can use it to view CHM files.
 */
public class ChmWeb extends Thread {

    private static final Logger LOG = Logger.getLogger(ChmWeb.class.getName());
    private final boolean isRunningFromJar;
    private ServerSocket listen_socket;
    private ChmFile chmFile;
    private String chmFilePath = null;
    private String codec = "UTF8";
    private String resourcesPath;

    public ChmWeb() {
        isRunningFromJar = checkRunningFromJar();

        resourcesPath = System.getProperty("org.util.jchmlib.app.ChmWeb.resources");
        if (resourcesPath == null && !isRunningFromJar) {
            resourcesPath = "resources";
        }
    }

    // reason: some CHM file may use the wrong codec.
    private String fixCodec(String originCodec) {
        // for CJK or the like, use the origin codec.
        // see EncodingHelper for codec names.
        if (!originCodec.equalsIgnoreCase("Latin1") &&
                !originCodec.startsWith("CP")) {
            return originCodec;
        }

        return "UTF8";
    }


    public boolean serveChmFile(int port, String chmFileName) {
        if (getState() == State.RUNNABLE) {  // already started
            return false;
        }

        try {
            chmFilePath = chmFileName;
            chmFile = new ChmFile(chmFileName);
            codec = fixCodec(chmFile.codec);
        } catch (Exception e) {
            System.err.println("Failed to open this CHM file.");
            e.printStackTrace();
            return false;
        }

        listen_socket = tryCreateSocket(port);
        if (listen_socket == null) {
            System.err.println("Failed to find a free port.");
            return false;
        }

        System.out.println("Server started. Now open your browser " +
                "and type\n\t http://localhost:" + listen_socket.getLocalPort() + "/@index.html");

        //Start running Server thread
        start();

        return true;
    }

    private ServerSocket tryCreateSocket(int defaultPort) {
        if (defaultPort > 0) {
            try {
                return new ServerSocket(defaultPort);
            } catch (IOException ex) {
                return null;
            }
        }

        for (int port = 50000; port < 63000; port++) {
            try {
                return new ServerSocket(port);
            } catch (IOException ex) {
                // try next port
            }
        }

        return null;
    }

    public int getServerPort() {
        if (listen_socket == null) {
            return 0;
        } else {
            return listen_socket.getLocalPort();
        }
    }

    public String getChmTitle() {
        if (chmFile == null) {
            return "";
        } else {
            return chmFile.title;
        }
    }

    public String getChmFilePath() {
        return chmFilePath == null ? "" : chmFilePath;
    }

    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Socket client_socket = listen_socket.accept();
                    new ClientHandler(client_socket, chmFile, codec,
                            isRunningFromJar, resourcesPath);
                } catch (SocketException ignored) {
                    break;
                }
            }
        } catch (IOException e) {
            // System.err.println(e);
            e.printStackTrace();
        }
    }

    public void stopServer() {
        if (chmFile == null || listen_socket == null ||
                Thread.currentThread().isInterrupted()) {
            return;
        }

        try {
            listen_socket.close();
        } catch (IOException e) {
            LOG.fine("Error closing listen socket: " + e);
        }

        interrupt();
    }

    public boolean checkRunningFromJar() {
        String className = this.getClass().getName().replace('.', '/');
        String classJar = this.getClass().getResource("/" + className + ".class").toString();
        return classJar.startsWith("jar:");
    }
}

/**
 * The ClientHandler class -- this is where HTTP requests are handled
 */
class ClientHandler extends Thread {

    private static final Logger LOG = Logger.getLogger(ClientHandler.class.getName());

    private final Socket client;
    private final ChmFile chmFile;
    private final boolean isRunningFromJar;

    private String codec;
    private HttpRequest request;
    private HttpResponse response;
    private String requestedFile;
    private String resourcesPath;

    public ClientHandler(Socket client_socket, ChmFile file, String codec,
            boolean isRunningFromJar, String resourcesPath) {
        client = client_socket;
        chmFile = file;
        this.codec = codec;
        this.isRunningFromJar = isRunningFromJar;
        this.resourcesPath = resourcesPath;

        try {
            request = new HttpRequest(client.getInputStream(), this.codec);
            requestedFile = request.getPath();
            if (requestedFile != null && requestedFile.startsWith("/chmweb/")) {
                this.codec = "UTF8";
                request.setEncoding(this.codec);  // for parsing parameters
            }
            response = new HttpResponse(client.getOutputStream(), this.codec);
        } catch (IOException e) {
            e.printStackTrace();
            try {
                client.close();
            } catch (IOException e2) {
                e.printStackTrace();
            }
            return;
        }

        if (requestedFile == null || requestedFile.length() == 0) {
            return;
        }

        start();
    }

    public void run() {
        try {
            if (requestedFile.equals("/")) {
                requestedFile = requestedFile.substring(1);
                deliverSpecial();
            } else if (requestedFile.equalsIgnoreCase("/favicon.ico")) {
                requestedFile = requestedFile.substring(1);
                deliverSpecial();
            } else if (requestedFile.startsWith("/@")) {
                requestedFile = requestedFile.substring(2);
                deliverSpecial();
            } else if (requestedFile.startsWith("/chmweb/")) {
                requestedFile = requestedFile.substring("/chmweb/".length());
                deliverSpecial();
            } else if (requestedFile.endsWith("/")) {// this is a directory
                if (requestedFile.equals("/nonchmweb/")) {
                    requestedFile = "/";
                }
                deliverDir();
            } else { // this is a file
                deliverFile();
            }
        } catch (IOException e) {
            LOG.fine("Failed to handle request:  " + e);
        } finally {
            try {
                client.close();
            } catch (IOException ignored) {
            }
        }
    }

    private String fixChmLink(String homeFile) {
        if (homeFile.equals("/")) {
            return "/nonchmweb/";
        } else if (homeFile.startsWith("/chmweb/")) {
            return "/nonchmweb/" + homeFile.substring("/chmweb/".length());
        } else {
            return homeFile;
        }
    }

    private void deliverDir() {
        response.sendHeader("text/html");
        response.sendString("<html>\n" +
                "<head>\n" +
                "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=" + codec + "\">\n"
                +
                "<title>" + requestedFile + "</title>" +
                "<link rel=\"stylesheet\" href=\"/chmweb/css/chmweb.css\">" +
                "</head>" +
                "<body>\n" +
                "<h1>" + requestedFile + "</h1>" +
                "<table class=\"filelist\">\n" +
                "<thead>\n" +
                "<tr>\n" +
                "  <td>File</td>\n" +
                "  <td class=\"filesize\">Size</td>\n" +
                "</tr>\n" +
                "<thead>\n" +
                "<tbody>\n");

        // /apple/ l=7, 0-6, 0, 0-1
        // /apple/banana/, l=14, 0-13, 6, 0-7
        // / l=1, 0-0
        int index = requestedFile.substring(0, requestedFile.length() - 1).lastIndexOf("/");
        if (index >= 0) {
            String parentDir = requestedFile.substring(0, index + 1);
            parentDir = fixChmLink(parentDir);
            response.sendLine(String.format("<td><a href=\"%s\">%s</a></td>", parentDir, ".."));
            response.sendLine("<td></td>");
        }

        DirChmEnumerator enumerator = new DirChmEnumerator();
        chmFile.enumerateDir(requestedFile, ChmFile.CHM_ENUMERATE_USER, enumerator);

        for (ChmUnitInfo ui : enumerator.files) {
            response.sendLine("<tr>");
            if (ui.getLength() > 0) {
                response.sendLine(String.format("<td class=\"file\"><a href=\"%s\">%s</a></td>",
                        fixChmLink(ui.getPath()), ui.getPath().substring(requestedFile.length())));
                response.sendLine(String.format("<td class=\"filesize\">%d</td>", ui.getLength()));
            } else {
                response.sendLine(String.format("<td class=\"folder\"><a href=\"%s\">%s</a></td>",
                        fixChmLink(ui.getPath()), ui.getPath().substring(requestedFile.length())));
                response.sendLine("<td></td>");
            }
            response.sendLine("</tr>");
        }

        response.sendString("</tbody>\n" +
                "</table>\n" +
                "</body>\n" +
                "</html>\n");
    }

    private void deliverFile() {
        // resolve object
        ChmUnitInfo ui = chmFile.resolveObject(requestedFile);

        String mimeType = request.getContentType();
        response.sendHeader(mimeType);

        // check to see if file exists
        if (ui == null) {
            if (mimeType.equalsIgnoreCase("text/html")) {
                response.sendString("<html>\n" +
                        "<head>\n" +
                        "<meta http-equiv=\"Content-Type\" content=\"text/html; " +
                        " charset=" + codec + "\">\n" +
                        "<title>404</title>" +
                        "</head>" +
                        "<body>\n" +
                        "404: not found: " + requestedFile +
                        "</body>");
            }
        } else {
            ByteBuffer buffer = chmFile.retrieveObject(ui);
            response.write(buffer, (int) ui.getLength());
        }
    }

    private void deliverSpecial() throws IOException {
        if (requestedFile.length() == 0 || requestedFile.equalsIgnoreCase("index.html")) {
            deliverMain();
        } else if (requestedFile.equalsIgnoreCase("topics.json")) {
            deliverTopicsTree();
        } else if (requestedFile.equalsIgnoreCase("files.json")) {
            deliverFilesTree();
        } else if (requestedFile.equalsIgnoreCase("search.json")) {
            deliverUnifiedSearch();
        } else {
            deliverResource(requestedFile);
        }
    }

    private void deliverMain() {
        response.sendHeader("text/html");
        String homeFile = fixChmLink(chmFile.home_file);
        response.sendLine("<html>\n"
                + "<head>\n"
                + "<meta http-equiv=\"Content-Type\" "
                + " content=\"text/html; charset=" + codec
                + "\">\n"
                + "<title>" + chmFile.title + "</title>\n"
                + "</head>\n"
                + "<frameset cols=\"200, *\">\n"
                + "  <frame src=\"/chmweb/sidebar2.html\" name=\"treefrm\">\n"
                + "  <frame src=\"" + homeFile + "\" name=\"basefrm\">\n"
                + "</frameset>\n"
                + "</html>");
    }

    private void deliverTopicsTree() {
        response.sendHeader("application/json");
        printTopicsTree(chmFile.getTopicsTree(), 0);
        chmFile.releaseLargeTopicsTree();
    }

    private void printTopicsTree(ChmTopicsTree tree, int level) {
        if (tree == null) {
            return;
        }

        String title = tree.title.length() > 0 ? tree.title : "untitled";
        title = title.replace("'", "\\'");

        if (level == 0) {
            response.sendString("[");
            for (ChmTopicsTree child : tree.children) {
                printTopicsTree(child, level + 1);
            }
            response.sendLine("]");
        } else if (!tree.children.isEmpty()) {
            response.sendLine(String.format("['%s', '%s', [", tree.path, title));
            for (ChmTopicsTree child : tree.children) {
                printTopicsTree(child, level + 1);
            }
            response.sendLine("],],");
        } else { // leaf node
            if (tree.path.length() == 0 && title.equalsIgnoreCase("untitled")) {
                return;
            }
            String path = fixChmLink(tree.path);
            response.sendLine(String.format("['%s', '%s'],", path, title));
        }
    }

    private void deliverFilesTree() {
        response.sendHeader("application/json");
        DirChmEnumerator enumerator = new DirChmEnumerator();
        chmFile.enumerate(ChmFile.CHM_ENUMERATE_USER, enumerator);
        ChmTopicsTree tree = buildFilesTree(enumerator.files);
        printTopicsTree(tree, 0);
    }

    private ChmTopicsTree addFileNode(String path, String title, ChmTopicsTree currentDirNode) {
        ChmTopicsTree node = new ChmTopicsTree();
        node.path = path;
        node.title = title;
        node.parent = currentDirNode;
        currentDirNode.children.add(node);
        return node;
    }

    private ChmTopicsTree buildFilesTree(ArrayList<ChmUnitInfo> files) {
        ChmTopicsTree root = new ChmTopicsTree();
        root.path = "/";
        ChmTopicsTree currentDirNode = root;

        addFileNode(fixChmLink(chmFile.home_file), "Main Page", root);
        addFileNode(fixChmLink("/"), "Root Directory", root);
        for (ChmUnitInfo ui : files) {
            String path = ui.getPath();
            if (path.equals("/")) {
                continue;
            }

            while (currentDirNode != null && !path.startsWith(currentDirNode.path)) {
                currentDirNode = currentDirNode.parent;
            }
            if (currentDirNode == null) {
                break;
            }

            String title = path.substring(currentDirNode.path.length());
            while (true) {
                if (title.length() == 0) {
                    break;
                }
                int index = title.indexOf("/");
                if (index <= 0 || index == title.length() - 1) {
                    break;
                }

                String dirPart = title.substring(0, index + 1);
                String leftPart = title.substring(index + 1);
                currentDirNode = addFileNode(currentDirNode.path + dirPart,
                        dirPart, currentDirNode);

                title = leftPart;
            }

            ChmTopicsTree node = addFileNode(path, title, currentDirNode);
            if (path.endsWith("/")) {
                currentDirNode = node;
            }
        }

        return root;
    }

    private void deliverUnifiedSearch() {
        String query = request.getParameter("q");
        if (query == null) {
            return;
        }
        boolean useRegex = false;
        String sUseRegex = request.getParameter("regex");
        if (sUseRegex != null && sUseRegex.equals("1")) {
            useRegex = true;
        }

        response.sendHeader("application/json");

        ChmIndexSearcher searcher = chmFile.getIndexSearcher();
        if (!useRegex && !searcher.notSearchable) {
            searcher.search(query, false, false);
            HashMap<String, String> results = searcher.getResults();

            if (results != null && results.size() > 0) {
                response.sendLine("{'ok': true, 'results':[");
                for (Map.Entry<String, String> entry : results.entrySet()) {
                    String url = "/" + entry.getKey();
                    String topic = entry.getValue();
                    url = fixChmLink(url);
                    url = url.replace("'", "\\'");
                    topic = topic.replace("'", "\\'");
                    response.sendLine(String.format("['%s', '%s'],", url, topic));
                }
                response.sendLine("],}");
            } else {
                response.sendLine("{'ok': false}");
            }

            return;
        }

        try {
            ChmSearchEnumerator enumerator = new ChmSearchEnumerator(chmFile, query);
            chmFile.enumerate(ChmFile.CHM_ENUMERATE_USER, enumerator);
            ArrayList<String> results = enumerator.getResults();
            if (results.size() == 0) {
                response.sendLine("{'ok': false}");
                return;
            }

            response.sendLine("{'ok': true, 'results':[");
            for (String url : results) {
                String topic = chmFile.getTitleOfObject(url);
                url = url.replace("'", "\\'");
                url = fixChmLink(url);
                topic = topic.replace("'", "\\'");
                response.sendLine(String.format("['%s', '%s'],", url, topic));
            }
            response.sendLine("],}");
        } catch (Exception e) {
            LOG.fine("Failed to handle search:  " + e);
        }
    }

    private void deliverResource(String requestedFile) throws IOException {
        if (resourcesPath != null) {
            String filename = new File(resourcesPath, requestedFile).toString();
            File f = new File(filename);

            // check to see if file exists
            if (f.canRead()) {
                response.sendHeader(request.getContentType());

                RandomAccessFile rf = new RandomAccessFile(filename, "r");
                ByteBuffer in = rf.getChannel().map(
                        FileChannel.MapMode.READ_ONLY, 0, rf.length());
                response.write(in, (int) rf.length());
                return;
            } else if (!isRunningFromJar) {
                response.sendHeader("text/plain");
                response.sendString("404: not found: " + f.getAbsolutePath());
                return;
            }
        }

        assert isRunningFromJar;

        InputStream in = ChmWeb.class.getResourceAsStream("/" + requestedFile);
        if (in == null) {
            response.sendHeader("text/plain");
            response.sendString("404: not found: " + requestedFile);
            return;
        }

        response.sendHeader(request.getContentType());

        byte[] buffer = new byte[1024];
        int size;
        while ((size = in.read(buffer)) != -1) {
            response.write(buffer, 0, size);
        }
    }
}

class DirChmEnumerator implements ChmEnumerator {

    final ArrayList<ChmUnitInfo> files;

    DirChmEnumerator() {
        files = new ArrayList<>();
    }

    public void enumerate(ChmUnitInfo ui) {
        files.add(ui);
    }
}
