package org.jchmlib.app;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

public class SingleInstanceController extends Thread {

    public static int PORT = 0xbf44;
    private final ChmWebApp app;
    private ServerSocket listenSocket;

    public SingleInstanceController(ChmWebApp app) {
        this.app = app;
        listenSocket = null;
    }

    public boolean tryStartInstance() {
        try {
            listenSocket = new ServerSocket(PORT);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Socket clientSocket = listenSocket.accept();
                handleClient(clientSocket);
            } catch (SocketException ignored) {
                break;
            } catch (IOException ignored) {
            }
        }
    }

    private void handleClient(Socket clientSocket) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    clientSocket.getInputStream(), "UTF8"));

            String portLine = reader.readLine();
            if (!portLine.equals("" + PORT)) {
                return;
            }

            String command = reader.readLine();
            if (command.equalsIgnoreCase("FRONT")) {
                app.bringToFront();
            } else if (command.equalsIgnoreCase("OPEN")) {
                String fileName = reader.readLine();
                app.openFile(new File(fileName));
                app.bringToFront();
            }
        } catch (Exception ignored) {
        }
    }

    public void sendOpenFileRequest(String filename) {
        Socket socket;
        try {
            socket = new Socket("127.0.0.1", PORT);
            PrintStream out = new PrintStream(socket.getOutputStream(), true, "UTF8");
            out.println("" + PORT);
            out.println("OPEN");
            out.println(filename);
        } catch (Exception ignored) {
        }
    }

    public void sendBringToFrontRequest() {
        Socket socket;
        try {
            socket = new Socket("127.0.0.1", PORT);
            PrintStream out = new PrintStream(socket.getOutputStream(), true, "UTF8");
            out.println("" + PORT);
            out.println("FRONT");
        } catch (Exception ignored) {
        }
    }
}
