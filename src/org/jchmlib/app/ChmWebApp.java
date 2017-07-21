package org.jchmlib.app;

import com.apple.eawt.AppEvent.OpenFilesEvent;
import com.apple.eawt.Application;
import com.apple.eawt.OpenFilesHandler;
import java.awt.Dimension;
import java.io.File;
import java.util.ArrayList;
import javax.swing.DefaultListModel;
import javax.swing.JFrame;
import javax.swing.JList;

public class ChmWebApp {

    ArrayList<ChmWeb> servers = new ArrayList<>();
    DefaultListModel<String> listModel = new DefaultListModel<>();

    private static void PrintUsage() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        StackTraceElement main = stack[stack.length - 1];
        String mainClass = main.getClassName();

        System.out.println("Usage: " + mainClass + " [-p port] [--no-gui] chm-filename");
    }

    public static void main(String[] argv) {
        int port = 0;
        String chmFileName = null;
        boolean noGui = false;

        int i = 0;
        while (i < argv.length) {
            String arg = argv[i];
            if (arg.equalsIgnoreCase("--port") ||
                    arg.equalsIgnoreCase("-p")) {
                if (i + 1 >= argv.length) {
                    PrintUsage();
                    return;
                }

                i++;
                try {
                    port = Integer.parseInt(argv[i]);
                } catch (NumberFormatException ignored) {
                }

                if (port < 0) {
                    port = 0;
                }

            } else if (arg.equalsIgnoreCase("--no-gui") ||
                    arg.equalsIgnoreCase("-n")) {
                noGui = true;

            } else if (arg.equalsIgnoreCase("--help") ||
                    arg.equalsIgnoreCase("-h")) {
                PrintUsage();
                return;

            } else {
                chmFileName = arg;
                break;
            }

            i++;
        }

        if (noGui) {
            if (chmFileName == null) {
                PrintUsage();
                return;
            }
            ChmWeb server = new ChmWeb();
            server.serveChmFile(port, chmFileName);
        } else {
            ChmWebApp app = new ChmWebApp();
            if (chmFileName != null) {
                ChmWeb server = new ChmWeb();
                if (server.serveChmFile(port, chmFileName)) {
                    app.addServer(server);
                }
            }
            app.startGui();
        }
    }

    void startGui() {
        if (listModel.isEmpty()) {
            listModel.addElement("ChmWeb");
        }
        JList<String> listServers = new JList<String>(listModel);
        JFrame frame = new JFrame("ChmWeb");
        frame.add(listServers);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 500);
        frame.setPreferredSize(new Dimension(600, 500));
        frame.pack();
        frame.setVisible(true);

        handleMac();
    }

    void addServer(ChmWeb server) {
        servers.add(server);
        listModel.addElement(getLineForServer(server));
    }

    String getLineForServer(ChmWeb server) {
        String line = "localhost:" + server.getServerPort() + "    " +
                server.getChmFilePath() + "    " + server.getChmTitle();
        return line;
    }

    void handleMac() {
        //First, check for if we are on OS X so that it doesn't execute on
        //other platforms. Note that we are using contains() because it was
        //called Mac OS X before 10.8 and simply OS X afterwards
        if (!System.getProperty("os.name").contains("OS X")) {
            return;
        }
        Application a = Application.getApplication();
        a.setOpenFileHandler(new OpenFilesHandler() {
            @Override
            public void openFiles(OpenFilesEvent e) {
                for (File file : e.getFiles()) {
                    ChmWeb server = new ChmWeb();
                    if (server.serveChmFile(0, file.getAbsolutePath())) {
                        addServer(server);
                    }
                }
            }
        });
    }
}
