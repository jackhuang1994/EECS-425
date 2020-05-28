// Program: p2pListen.java
// Author: Mingan Huang 
// Network ID: mxh805

import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;

class p2pListen implements Runnable {
    public static ServerSocket welcomeSocket;
    public static Socket[] neighborSocket;
    public static int numNeighbors;
    private Thread threadListen = null;
    private String threadName = "Listening Thread";

    // Constructor
    public p2pListen(ServerSocket welcomeSocket, Socket[] neighborSocket, int numNeighbors) {
      this.welcomeSocket = welcomeSocket;
      this.neighborSocket = neighborSocket;
      this.numNeighbors = numNeighbors;
    }
    
    public void start() {
      if (threadListen == null) {
        threadListen = new Thread(this, threadName);
        threadListen.start();
      }
    }

    @Override
    public void run() {
      while (!welcomeSocket.isClosed()) {
        try {
          Socket connection = welcomeSocket.accept();

          boolean addedConnection = false;
          for (int i = 0; !addedConnection && i < numNeighbors; i++) {
            // if there is no connection or this connection is closed
            if (neighborSocket[i] == null || neighborSocket[i].isClosed()) {
              addedConnection = true;
              // make connection with this neighbor
              neighborSocket[i] = connection;
              
              p2pListenQuery listenQueryThread = new p2pListenQuery(i);
              listenQueryThread.start();
            }
          }
          // Accept this connection
          System.out.println(
                             "Accepted connection from host '" + connection.getInetAddress().getHostName());
          System.out.println();
        } catch (IOException e) {
          e.printStackTrace();
          System.err.println("Failed while waiting for connection");
          System.out.println();
        }
      }
    }
  }