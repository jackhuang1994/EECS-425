// Program: p2pFileSend.java
// Author: Mingan Huang 
// Network ID: mxh805

import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;

class p2pFileSend implements Runnable {
    public static ServerSocket fileTransferWelcomeSocket;
    private Thread threadFileSend = null;
    private String threadName = "File Send Thread";

    // Constructor
    public p2pFileSend(ServerSocket fileTransferWelcomeSocket) {
      this.fileTransferWelcomeSocket = fileTransferWelcomeSocket;
    }

    public void start() {
      if (threadFileSend == null) {
        threadFileSend = new Thread(this, threadName);
        threadFileSend.start();
      }
    }

    @Override
    public void run() {
      while (!fileTransferWelcomeSocket.isClosed()) {
        try {
          // accept incoming file transfer socket
          Socket connection = fileTransferWelcomeSocket.accept();
          System.out.println(
                             "Accepted connection from host '" + connection.getInetAddress().getHostName());
          System.out.println();

          // transfer file
          p2p.queryTransferRequest(connection, connection.getInetAddress());
        } catch (IOException e) {
          System.err.println("Failed while waiting for connection");
          System.out.println();
        }
      }
    }
  }