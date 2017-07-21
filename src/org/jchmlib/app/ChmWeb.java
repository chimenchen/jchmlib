/* ChmEnumerator.java 06/05/25
 *
 * Copyright 2006 Chimen Chen. All rights reserved.
 *
 */

package org.jchmlib.app;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.jchmlib.ChmEnumerator;
import org.jchmlib.ChmFile;
import org.jchmlib.ChmIndexSearcher;
import org.jchmlib.ChmSearchEnumerator;
import org.jchmlib.ChmTopicsTree;
import org.jchmlib.ChmUnitInfo;
import org.jchmlib.app.net.HTMLEncode;
import org.jchmlib.app.net.HttpRequest;
import org.jchmlib.app.net.HttpResponse;

/**
 * A simple web server.
 * You can use it to view CHM files.
 */
public class ChmWeb extends Thread {

    private ServerSocket listen_socket;
    private ChmFile chmFile;
    private String chmFilePath = null;
    private boolean isRunningFromJar;
    private String resourcesPath;

    public ChmWeb() {
        isRunningFromJar = checkRunningFromJar();

        resourcesPath = System.getProperty("org.util.jchmlib.app.ChmWeb.resources");
        if (resourcesPath == null && !isRunningFromJar) {
            resourcesPath = "resources";
        }
    }

    public boolean serveChmFile(int port, String chmFileName) {
        if (getState() == State.RUNNABLE) {  // already started
            return false;
        }

        listen_socket = tryCreateSocket(port);
        if (listen_socket == null) {
            System.err.println("Failed to find a free port.");
            return false;
        }

        try {
            chmFilePath = chmFileName;
            chmFile = new ChmFile(chmFileName);
        } catch (IOException e) {
            System.err.println("Failed to open this CHM file.");
            e.printStackTrace();
            return false;
        }

        System.out.println("Server started. Now open your browser " +
                "and type\n\t http://localhost:" + listen_socket.getLocalPort() + "/@index.html");

        //Start running Server thread
        start();

        return true;
    }

    ServerSocket tryCreateSocket(int defaultPort) {
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
            while (true) {
                Socket client_socket = listen_socket.accept();
                new ClientHandler(client_socket, chmFile,
                        isRunningFromJar, resourcesPath);
            }
        } catch (IOException e) {
            // System.err.println(e);
            e.printStackTrace();
        }
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

    private final Socket client;
    private final ChmFile chmFile;
    private final boolean isRunningFromJar;

    private HttpRequest request;
    private HttpResponse response;
    private String requestedFile;
    private String resourcesPath;

    /* used in printTopicsTree */
    private int n;

    public ClientHandler(Socket client_socket, ChmFile file,
            boolean isRunningFromJar, String resourcesPath) {
        chmFile = file;
        client = client_socket;
        this.isRunningFromJar = isRunningFromJar;
        this.resourcesPath = resourcesPath;

        try {
            request = new HttpRequest(client.getInputStream(),
                    chmFile.codec);
            response = new HttpResponse(client.getOutputStream(),
                    chmFile.codec);
        } catch (IOException e) {
            // System.err.println(e);
            e.printStackTrace();
            try {
                client.close();
            } catch (IOException e2) {
                // System.err.println(e);
                e.printStackTrace();
            }
            return;
        }

        requestedFile = request.getPath();
        if (requestedFile == null || requestedFile.length() == 0) {
            return;
        }

        start();
    }

    public void run() {
        try {
            if (requestedFile.equalsIgnoreCase("/favicon.ico")) {
                deliverSpecial();
            } else if (requestedFile.startsWith("/@")) {
                deliverSpecial();
            } else if (requestedFile.endsWith("/")) {// this is a directory
                deliverDir();
            } else { // this is a file
                deliverFile();
            }
        } catch (IOException e) {
            // System.err.println(e);
            e.printStackTrace();
        } finally {
            try {
                client.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void deliverDir() {
        response.sendHeader("text/html");
        response.sendString("<html>\n" +
                "<head>\n" +
                "<meta http-equiv=\"Content-Type\" content=\"text/html; " +
                " charset=" + chmFile.codec + "\">\n" +
                "<title>" + chmFile.title + "</title>" +
                "</head>" +
                "<body>\n" +
                "<table border=0 cellspacing=0 cellpadding=0 width=100%>\n" +
                "<tr><td align=right nowrap>" +
                "<a href=\"" + chmFile.home_file + "\">Main Page</a>&nbsp;\n" +
                "<a href=\"/@index.html\">Frame View</a> &nbsp;" +
                "</td></tr>\n" +
                "<tr><td align=left>" +
                "<h2><u>CHM Contents:</u></h2>" +
                "</td></tr>\n" +
                "<tr>\n" +
                "<table width=\"100%\">\n" +
                "<tr>\n" +
                "  <td align=right><b>Size: &nbsp&nbsp<b>\n" +
                "   <br><hr>\n" +
                "  </td>\n" +
                "  <td><b>File:<b><br><hr></td>\n" +
                "</tr>\n" +
                "<tt>\n");

        chmFile.enumerateDir(requestedFile,
                ChmFile.CHM_ENUMERATE_USER,
                new DirChmEnumerator(response.getWriter()));

        response.sendString("</tt>\n" +
                "</tr>\n" +
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
                response.sendString("404: not found: " + requestedFile);
            }
            return;
        }

        /* pump the data out */
        ByteBuffer buffer = chmFile.retrieveObject(ui);

        response.write(buffer, (int) ui.length);
    }

    private void deliverSpecial() throws IOException {
        if (requestedFile.startsWith("/@")) {
            requestedFile = requestedFile.substring(2);
        } else if (requestedFile.equalsIgnoreCase("/favicon.ico")) {
            requestedFile = requestedFile.substring(1);
        }

        if (requestedFile.equalsIgnoreCase("index.html")) {
            deliverMain();
            return;
        } else if (requestedFile.equalsIgnoreCase("tree.html")) {
            deliverTree();
            return;
        } else if (requestedFile.equalsIgnoreCase("search.html")) {
            deliverSearch();
            return;
        } else if (requestedFile.equalsIgnoreCase("search2.html")) {
            deliverSearch2();
            return;
        }

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

    private void deliverMain() {
        response.sendHeader("text/html");
        response.sendLine("<html>\n"
                + "<head>\n"
                + "<meta http-equiv=\"Content-Type\" "
                + " content=\"text/html; charset=" + chmFile.codec
                + "\">\n"
                + "<title>" + chmFile.title + "</title>\n"
                + "</head>\n"
                + "<frameset cols=\"200, *\">\n"
                + "  <frame src=\"@tree.html\" name=\"treefrm\">\n"
                + "  <frame src=\"" + chmFile.home_file + "\" name=\"basefrm\">\n"
                + "</frameset>\n"
                + "</html>");
    }

    private void deliverTree() {
        int expandLevel = 4;
        String query = request.getParameter("expand");
        if (query != null) {
            expandLevel = Integer.parseInt(query);
        }

        n = 1;
        deliverMenu(1);
        response.sendString("<div class=\"directory\">\n"
                + "<div style=\"display: block;\">\n");
        printTopicsTree(chmFile.getTopicsTree(), 0, expandLevel);
        response.sendString("</div>\n</div>\n</div>\n\n");
        chmFile.releaseLargeTopicsTree();
    }

    private void deliverSearch() {
        deliverMenu(2);
        response.sendString("<p>Type in the word(s) to search for:</p>\n");

        String query = request.getParameter("searchdata");
        if (query == null) {
            deliverSearchForm();
            response.sendLine("</div></div></body></html>");
            return;
        }

        deliverSearchForm(query);

        try {
            ChmIndexSearcher searcher = chmFile.getIndexSearcher();
            searcher.search(query, false, false);
            HashMap<String, String> results = searcher.getResults();

            if (results == null) {
                if (searcher.notSearchable) {
                    response.sendString("<p>This CHM file doesn't support full-text search.</p>");
                } else {
                    response.sendString("<p>No match found for " + query + ".</p>");
                }
            } else {
                for (Map.Entry<String, String> entry : results.entrySet()) {
                    String url = entry.getKey();
                    String topic = entry.getValue();
                    response.sendString("<p>"
                            + "<a class=\"el\" href=\"" + url + "\""
                            + "   target=\"basefrm\">" + topic
                            + "</a>" + "</p>");
                }
            }
        } catch (IOException ignored) {
        }
        response.sendString("</div></div></body></html>");
    }

    private void deliverSearch2() {
        deliverMenu(3);
        response.sendString("<p>Type in the word(s) to search for:</p>\n");

        String query = request.getParameter("searchdata");
        if (query == null) {
            deliverSearchForm();
            response.sendLine("</div></body></html>");
            return;
        }

        deliverSearchForm(query);

        try {
            ChmSearchEnumerator enumerator = new ChmSearchEnumerator(chmFile, query);
            chmFile.enumerate(ChmFile.CHM_ENUMERATE_USER, enumerator);
            ArrayList<String> results = enumerator.getResults();
            if (results.size() == 0) {
                response.sendLine("<p>No match found for " + query
                        + ".</p>");
                return;
            }

            int i = 0;
            for (String url : results) {
                i++;
                String title = chmFile.getTitleOfObject(url);
                response.sendLine("<p><a class=\"el\" " + " "
                        + " href=\"" + url + "\" "
                        + " target=\"basefrm\">[" + i + "]" + title
                        + "</a></p>");
            }
        } catch (Exception e) {
            // System.err.println(e);
            e.printStackTrace();
        }
        response.sendLine("</div></div></body></html>");
    }

    private void deliverMenu(int selected) {
        response.sendHeader("text/html");
        response.sendString("<html>\n"
                + "<head>\n"
                + "<meta http-equiv=\"Content-Type\" "
                + " content=\"text/html; charset=" + chmFile.codec + "\">\n"
                + "<title>Search</title>\n"
                + "<link rel=\"STYLESHEET\" type=\"text/css\" href=\"@tree.css\"/>\n"
                + "<script type=\"text/javascript\" src=\"@search.js\"></script>\n"
                + "<script type=\"text/javascript\" src=\"@tree.js\"></script>\n"
                + "</head>\n"
                + "<body>\n"
                + "<div class=\"menu\">\n"
                + "<p>\n"
                + "<a href=\"@tree.html\""
                + ((selected == 1) ? " class=\"active\"" : "")
                + ">"
                + "Topics"
                + "<a/>|"
                + "<a href=\"@search.html\""
                + ((selected == 2) ? " class=\"active\"" : "")
                + ">"
                + "Search"
                + "<a/>|"
                + "<a href=\"@search2.html\""
                + ((selected == 3) ? " class=\"active\"" : "")
                + ">"
                + "Search(slow)"
                + "<a/>|\n"
                + "<a href=\"/\" target=\"basefrm\">Direcroty Listing</a>\n"
                + "</p>\n"
                + "</div>\n"
                + "<div class=\"side_content\">\n");
    }

    private void deliverSearchForm() {
        response.sendLine("<form name=\"searchform\">\n"
                + "<table width=\"95%\">\n"
                + "  <tr>\n"
                + "    <td>\n"
                + "      <input type=\"text\" name=\"searchdata\" "
                + "        id=\"searchdata\" "
                + "        style=\"width:100%\">"
                + "    </td>\n"
                + "    <td nowrap width=\"50\">\n"
                + "      <input type=\"submit\" name=\"searchbutton\" "
                + "           value=\"Search\" style=\"width:100%\">\n"
                + "    </td>\n" + "  </tr>\n"
                + "</table>\n" + "</form>");
    }

    private void deliverSearchForm(String query) {
        if (query == null) {
            deliverSearchForm();
        }

        response.sendLine("<form name=\"searchform\">\n"
                + "<table width=\"95%\">\n"
                + "  <tr>\n"
                + "    <td>\n"
                + "      <input type=\"text\" name=\"searchdata\" "
                + "        id=\"searchdata\" "
                + "        value=\"" + HTMLEncode.encode(query) + "\""
                + "        style=\"width:100%\">"
                + "    </td>\n"
                + "    <td>\n"
                + "      <input type=\"submit\" name=\"searchbutton\" "
                + "           value=\"Search\" style=\"width:100%\">\n"
                + "    </td>\n"
                + "  </tr>\n"
                + "  <tr>\n"
                + "    <td>\n"
                + "      <input type=button value=\"Remove Highlight\" "
                + "             onclick=\"unhighlight()\"/>\n"
                + "    </td>\n"
                + "    <td>\n"
                + "      <input type=button value=Highlight "
                + "             onclick=\"findIt()\">\n"
                + "    </td>\n"
                + "  </tr>\n"
                + "</table>\n"
                + "</form>");
    }

    private void printTopicsTree(ChmTopicsTree tree, int level,
            int expandLevel) {
        if (tree == null) {
            return;
        }

        String title = tree.title.length() > 0 ? tree.title : "untitled";

        response.sendLine("<p>");

        for (int i = 0; i < level - 1; i++) {
            response.sendLine("<img src=\"@ftv2blank.png\" "
                    + " title=\"&nbsp;\" width=16 height=22 />");
        }

        if (level == 0) { // top level
            for (ChmTopicsTree child : tree.children) {
                printTopicsTree(child, level + 1, expandLevel);
            }
            response.sendString("</p>\n");
        } else if (!tree.children.isEmpty()) {
            if (level >= expandLevel) {
                response.sendLine("<a href=\"@tree.html?expand="
                        + (expandLevel + 1) + "\">"
                        + "<img src=@ftv2folderclosed.png "
                        + " title=\"Click to expand\" border=0></a> "
                        + "<a class=el href=\"" + tree.path + "\" "
                        + "   target=basefrm>" + title + "</a>"
                        + "</p>");
                return;
            }

            response.sendLine("<img src=\"@ftv2folderclosed.png\" "
                    + " title=\"" + title + "\" "
                    + " width=24 height=22 "
                    + " onclick=\"toggleFolder(\'folder" + n
                    + "\', this)\"/>" + "<a class=\"el\" href=\""
                    + tree.path + "\" " + " target=\"basefrm\">"
                    + title + "" + "</a>" + "</p>\n");
            response.sendLine("<div id=\"folder" + n + "\">");

            n++; // n is used to identify folders (topics with sub-topics)

            for (ChmTopicsTree child : tree.children) {
                printTopicsTree(child, level + 1, expandLevel);
            }
            response.sendLine("</div>");
        } else { // leaf node
            if (tree.path.length() == 0 && title.equalsIgnoreCase("untitled")) {
                return;
            }
            response.sendLine("<img src=\"@ftv2doc.png\" "
                    + "   title=\"" + title + "\" "
                    + "   width=24 height=22 />"
                    + "<a class=\"el\" href=\"" + tree.path + "\" "
                    + "   target=\"basefrm\">" + title + "</a>"
                    + "</p>");
        }
    }
}

// FIXME: redesign web server for better user experience.
// especially: the topics page.

class DirChmEnumerator implements ChmEnumerator {

    private final PrintStream out;

    public DirChmEnumerator(PrintStream out) {
        this.out = out;
    }

    public void enumerate(ChmUnitInfo ui) {
        out.println("<tr>\n");
        out.println("\t<td align=right>" + ui.length
                + " &nbsp&nbsp</td>");
        out.println("\t<td><a href=\"" + ui.path + "\">" + ui.path
                + "</a></td>\n");
        out.println("</tr>");
    }
}
