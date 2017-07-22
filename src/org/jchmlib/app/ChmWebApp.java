package org.jchmlib.app;

import com.apple.eawt.AppEvent.OpenFilesEvent;
import com.apple.eawt.Application;
import com.apple.eawt.OpenFilesHandler;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.AbstractTableModel;

public class ChmWebApp {

    private final ArrayList<ChmWeb> servers = new ArrayList<>();

    private JFrame frame;
    private JFileChooser fileChooser = null;

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

    private void startGui() {
        JTable table = new JTable(new ChmWebTableModel(servers)) {
            //Implement table cell tool tips.
            public String getToolTipText(MouseEvent e) {
                java.awt.Point p = e.getPoint();
                int rowIndex = rowAtPoint(p);
                int colIndex = columnAtPoint(p);
                try {
                    return getValueAt(rowIndex, colIndex).toString();
                } catch (RuntimeException e1) {
                    //catch null pointer exception if mouse is over an empty line
                    return null;
                }
            }
        };

        JPopupMenu popup = new JPopupMenu();

        JMenuItem menuItemOpenInBrowser = new JMenuItem(new AbstractAction("Open in browser") {
            @Override
            public void actionPerformed(ActionEvent e) {
                int[] rows = table.getSelectedRows();
                for (int row : rows) {
                    if (row < 0 || row >= servers.size()) {
                        continue;
                    }
                    ChmWeb server = servers.get(row);
                    openInBrowser(server);
                }
            }
        });
        popup.add(menuItemOpenInBrowser);

        JMenuItem menuItemCloseServer = new JMenuItem(new AbstractAction("Close") {
            @Override
            public void actionPerformed(ActionEvent e) {
                int[] rows = table.getSelectedRows();
                ArrayList<ChmWeb> serversToClose = new ArrayList<>();
                for (int row : rows) {
                    if (row < 0 || row >= servers.size()) {
                        continue;
                    }
                    ChmWeb server = servers.get(row);
                    serversToClose.add(server);
                }
                for (ChmWeb server : serversToClose) {
                    server.stopServer();
                    servers.remove(server);
                }
                table.updateUI();
            }
        });
        popup.add(menuItemCloseServer);

        JMenuItem menuItemOpenFile = new JMenuItem(new AbstractAction("Open CHM file") {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (fileChooser == null) {
                    fileChooser = new JFileChooser();
                    fileChooser.setMultiSelectionEnabled(true);

                    fileChooser.setFileFilter(new FileFilter() {
                        public String getDescription() {
                            return "HTML Help files (*.chm)";
                        }

                        public boolean accept(File f) {
                            if (f.isDirectory()) {
                                return true;
                            } else {
                                String filename = f.getName().toLowerCase();
                                return filename.endsWith(".chm");
                            }
                        }
                    });
                }

                int r = fileChooser.showDialog(frame, "Open");
                if (r == JFileChooser.APPROVE_OPTION) {
                    File[] files = fileChooser.getSelectedFiles();
                    for (File file : files) {
                        openFile(file);
                    }
                }
            }
        });
        popup.add(menuItemOpenFile);

        JPopupMenu popup2 = new JPopupMenu();

        JMenuItem menuItemOpenFile2 = new JMenuItem("Open CHM file");
        menuItemOpenFile2.addActionListener(menuItemOpenFile.getAction());
        popup2.add(menuItemOpenFile2);

        table.addMouseListener(new MouseAdapter() {
            public void mouseReleased(MouseEvent me) {
                if (me.isPopupTrigger()) {
                    showPopup(me);
                }
            }

            private void showPopup(MouseEvent me) {
                JTable table = (JTable) me.getSource();
                int row = table.rowAtPoint(me.getPoint());
                if (row < 0 || row >= servers.size()) {
                    popup2.show(me.getComponent(), me.getX(), me.getY());
                    return;
                }
                if (!table.isRowSelected(row)) {
                    table.changeSelection(row, 0, false, false);
                }
                popup.show(me.getComponent(), me.getX(), me.getY());
            }

            public void mousePressed(MouseEvent me) {
                if (me.isPopupTrigger()) {
                    showPopup(me);
                    return;
                }
                // only handle double click
                if (me.getClickCount() != 2) {
                    return;
                }

                JTable table = (JTable) me.getSource();
                int row = table.rowAtPoint(me.getPoint());
                if (row < 0 || row >= servers.size()) {
                    return;
                }

                ChmWeb server = servers.get(row);
                openInBrowser(server);
            }
        });

        table.setAutoResizeMode(JTable.AUTO_RESIZE_NEXT_COLUMN);
        table.getColumnModel().getColumn(0).setPreferredWidth(20);
        table.getColumnModel().getColumn(1).setPreferredWidth(100);

        frame = new JFrame("ChmWeb");
        frame.add(table.getTableHeader(), BorderLayout.PAGE_START);
        frame.add(table);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setSize(600, 500);
        frame.setPreferredSize(new Dimension(600, 500));
        frame.pack();

        frame.setDropTarget(new DropTarget() {
            @Override
            public synchronized void dragOver(DropTargetDragEvent evt) {
                evt.acceptDrag(DnDConstants.ACTION_COPY_OR_MOVE);
            }

            public synchronized void drop(DropTargetDropEvent evt) {
                try {
                    if (!evt.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        evt.rejectDrop();
                        return;
                    }

                    evt.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);
                    @SuppressWarnings("unchecked")
                    List<File> droppedFiles = (List<File>) evt.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);
                    for (File file : droppedFiles) {
                        openFile(file);
                    }
                    evt.dropComplete(true);
                    table.updateUI();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        for (ChmWeb server : servers) {
            openInBrowser(server);
        }

        frame.setVisible(true);

        handleMac();
    }

    private void openInBrowser(ChmWeb server) {
        int port = server.getServerPort();
        String url = String.format("http://localhost:%d/@index.html", port);
        if (Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().browse(new URI(url));
            } catch (Exception ignored) {
                ignored.printStackTrace();
            }
        } else {
            Runtime runtime = Runtime.getRuntime();
            try {
                runtime.exec("xdg-open " + url);
            } catch (Exception ignored) {
                ignored.printStackTrace();
            }
        }
    }

    private void addServer(ChmWeb server) {
        servers.add(server);
    }

    private void openFile(File file) {
        ChmWeb server = new ChmWeb();
        if (server.serveChmFile(0, file.getAbsolutePath())) {
            addServer(server);
            openInBrowser(server);
        } else {
            JOptionPane.showMessageDialog(frame, "Failed to open " + file.getName());
        }
    }

    private void handleMac() {
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
                    openFile(file);
                }
            }
        });
    }
}

class ChmWebTableModel extends AbstractTableModel {

    private final String[] columnNames = {"Port", "Title", "File Path"};
    private final ArrayList<ChmWeb> servers;

    ChmWebTableModel(ArrayList<ChmWeb> servers) {
        this.servers = servers;
    }

    public String getColumnName(int col) {
        return columnNames[col];
    }

    public int getColumnCount() {
        return columnNames.length;
    }

    public int getRowCount() {
        return servers.size();
    }

    public Object getValueAt(int row, int col) {
        if (row < 0 || row >= servers.size()) {
            return null;
        }
        if (col < 0 || col >= getColumnCount()) {
            return null;
        }
        ChmWeb server = servers.get(row);
        switch (col) {
            case 0:
                return "" + server.getServerPort();
            case 1:
                return server.getChmTitle();
            case 2:
                return server.getChmFilePath();
        }
        return null;
    }

    public boolean isCellEditable(int row, int col) {
        return false;
    }
}

