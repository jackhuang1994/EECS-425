// Program: Heartbeat.java
// Author: Mingan Huang 
// Network ID: mxh805

import java.util.Timer;
import java.io.DataOutputStream;
import java.net.Socket;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;

class Heartbeat implements Runnable {
  public static Socket[] neighborSocket;
  
    private Thread heartbeatThread = null;
    private String threadName = "Heartbeat Thread";

    private int interval = -1;
    private Timer timer = new Timer();
  
    private int neighbor = 0;

    
    // Constructor
    public Heartbeat(Socket[] neighborSocket, int heartbeatInterval, int neighbor) {
      this.neighborSocket = neighborSocket;
      
      interval = heartbeatInterval;
      this.neighbor = neighbor;
    }

    // Return intervals between heartbeat
    public int getInterval() {
      return interval;
    }

    // function to change the heartbeat interval
    public void setInterval(int heartbeatInterval) {
      interval = heartbeatInterval;
    }

    //
    // mutator methods
    //

    public void start() {
      if (heartbeatThread == null) {
     heartbeatThread = new Thread(this, threadName);
     heartbeatThread.start();
      }
    }

    @Override
    public void run() {
    }

    // Send heartbeat to neighbor
    public void sendHeartbeat() {
      String heartbeat = "Heartbeat\n";

      DataOutputStream outToNeighbor = null;
      // if connect to neighbor and neighbor connection is not closed
      if (neighborSocket[neighbor] != null && !neighborSocket[neighbor].isClosed()) {
        try {
      outToNeighbor = new DataOutputStream(neighborSocket[neighbor].getOutputStream());
      // send heartbeat to neighbor
      System.out.println("Sending heartbeat to host '"
                           + neighborSocket[neighbor].getInetAddress().getHostName());
      System.out.println();
      outToNeighbor.writeBytes(heartbeat + '\n');
      outToNeighbor.flush();

      // outToNeighbor.close();
        } catch (IOException e) {
          e.printStackTrace();
          System.err.println("Failed to send heartbeat");
          System.out.println();
        }
      }
    }

    // Receiver Heartbeat from neighbor
    public void receiveHeartbeat() {
      if (neighborSocket[neighbor] != null && !neighborSocket[neighbor].isClosed()) {
        try {
          BufferedReader inFromServer = new BufferedReader(
                                                           new InputStreamReader(neighborSocket[neighbor].getInputStream()));

          String heartbeat = inFromServer.readLine();

          // if a neighbor closed the connection
          if (heartbeat == null) {
            neighborSocket[neighbor] = null;
          }
          // if heartbeat is received
          else if (heartbeat.equals("Heartbeat")) {
            System.out.println("Received heartbeat from host '"
                                 + neighborSocket[neighbor].getInetAddress().getHostName());
            System.out.println();
          }
        } catch (IOException e) {
          e.printStackTrace();
          System.err.println("Failed to listen for incoming heartbeat");
          System.out.println();
        }
      }
    }

    // If timeout close the connection
    public void timeout() {
      System.out.println("Connection times out");
      System.out.println();

      // terminate the timer
      timer.cancel();
   
      // close the neighbor
      try {
        if (neighborSocket[neighbor] != null && !neighborSocket[neighbor].isClosed()) {
          neighborSocket[neighbor].close();
          neighborSocket[neighbor] = null;
        }
      } catch (IOException e) {
        e.printStackTrace();
        System.err.println("Failed to time out connection");
        System.out.println();
      }
    }
  }