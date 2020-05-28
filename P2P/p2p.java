// Program: p2p.java
// Author: Mingan Huang 
// Network ID: mxh805

import java.util.ArrayList;
import java.util.Scanner;
import java.io.File;
import java.io.FileReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.net.UnknownHostException;
import java.net.InetAddress;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Timer;
import java.net.*;
import java.util.Map.Entry;
import java.util.*;


public class p2p {
  private static String fileConfigPeer = "config_peer.txt"; // local ports the peer will be using
  private static InetAddress localHost = null;
  private static int connectionPortNum; // Port number for neighbor connections
  private static ServerSocket welcomeSocket = null;
  private static int fileTransferPortNum; // Port number for file transfer
  private static ServerSocket fileTransferWelcomeSocket = null; 
  private static int udpPortNum;
  private static DatagramSocket UdpSocket;
  
  private static String fileConfigSharing = "config_sharing.txt"; // Contain all files to share with neighbors
  private static ArrayList<String> sharedFiles = new ArrayList<String>();

  private static String fileConfigNeighbors = "config_neighbors.txt"; // Contain host name and port numbers of all neighbors
  private static int numNeighbors = 2; // maximum number of neighbors
  private static String[] ipAddress = new String[numNeighbors];
  private static int[] portNumber = new int[numNeighbors];
  private static Socket[] neighborSocket = new Socket[numNeighbors];

  private static int queryNum = 1;

  // Default Constructor
  public p2p() 
  {
  }
  
  // Main Method
  public static void main(String[] args) throws IOException {
    Scanner portIn = new Scanner(new File(fileConfigPeer));
    connectionPortNum = portIn.nextInt();
    fileTransferPortNum = portIn.nextInt();
    udpPortNum = portIn.nextInt();
    
    System.out.println("listening Port Number: " + connectionPortNum);
    System.out.println("fileTransfer Port Number: " + fileTransferPortNum);
    System.out.println("UDP port number: " + udpPortNum);
    
    // get the name of local host
    try {
      localHost = InetAddress.getLocalHost();
    } catch (UnknownHostException e) {
      System.err.println("Failed to get local host name");
      System.out.println();
    }

    // Start the host
    System.out.println("Local Host '" + localHost.getHostName() + "' starting");
    System.out.println();
    
    //

    // retrieve the files to be shared on the host
    p2p.getSharedFiles();

    // retrieve the neighbors information
    p2p.getNeighbors();

    // create a welcome socket to accept connections
    try {
      welcomeSocket = new ServerSocket(connectionPortNum);
      fileTransferWelcomeSocket = new ServerSocket(fileTransferPortNum);
      UdpSocket = new DatagramSocket(udpPortNum);
    } catch (IOException e) {
      e.printStackTrace();
      System.err.println("Failed to open sockets");
      System.out.println();
    }
    
    // listen for incoming connection requests
    p2pListen listenThread = new p2pListen(welcomeSocket, neighborSocket, numNeighbors);
    listenThread.start();
    p2pFileSend fileSendThread = new p2pFileSend(fileTransferWelcomeSocket);
    fileSendThread.start();

    // wait user input
    p2pInput inputThread = new p2pInput();
    inputThread.start();
  }
  
  // UDP port for discovery protocol
  static class Discovery extends Thread {
    private DatagramSocket socket;
    private boolean running;

    private HashMap<String, Integer> pingPorts;

    private int[] pongs;
    
    // Constructor 
    public Discovery(int sourcePort) throws SocketException {
        pingPorts = new HashMap<>();
        pongs = new int[256];
    }
    @Override
    public void run() {
        running = true;

        while (running) {
            try {
                byte[] buf = new byte[256];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
            } catch (IOException ignored) {}
        }
    }

    public void close() {
        socket.close();
        running = false;
    }
    
  }
  
  // User Commands
  static class p2pInput implements Runnable {

    private Thread inputThread = null;
    private String threadName = "Input Thread";

    // Default Constructor
    public p2pInput() {
    }

    public void start() {
      if (inputThread == null) {
        inputThread = new Thread(this, threadName);
        inputThread.start();
      }
    }
    
    @Override
    public void run() {
      Scanner in = new Scanner(System.in);
      while (true) {
        String command = in.nextLine();
        
        if (command.toLowerCase().equals("help")) {
          System.out.println();
          System.out.println("List of commands: ");
          System.out.println("Connect to neighbors");
          System.out.println("Get filename e.g.(get 219-0.txt");
          System.out.println("Leave - close all TCP connections with neighboring peers");
          System.out.println("Help - print this help menu");
          System.out.println("Exit - close all TCP connections and terminates the program");
          System.out.println();
        }
        else if (command.toLowerCase().equals("connect"))
          p2p.connect(); // connect to two neighbors
        else if (command.toLowerCase().equals("leave"))
          p2p.leave(); // close the connection
        else if (command.toLowerCase().equals("exit"))
          p2p.exit(); // exit the program
        else if (command.contains(" ")) { // Get file
          String[] getCommand = command.split(" ");
          if (getCommand[0].toLowerCase().equals("get")) {
            p2p.querySending(getCommand[1]);
          } else {
            System.err.println("Invalid command");
            System.out.println();
          }
        } else {
          System.err.println("Invalid command\n");
          System.out.println();
          System.out.println("List of commands: ");
          System.out.println("Connect to neighbors");
          System.out.println("Get filename e.g.(get 219-0.txt)");
          System.out.println("Leave - close all TCP connections with neighboring peers");
          System.out.println("Help - print this help menu");
          System.out.println("Exit - close all TCP connections and terminates the program");
          System.out.println();
        }
      }
    }
  }    
  
  // Read config_sharing.txt
  private static void getSharedFiles() {
    // read the config_sharing.txt file
    try {
      FileReader frCS = new FileReader(fileConfigSharing);
      BufferedReader brCS = new BufferedReader(frCS);

      String fileName = null;
      while ((fileName = brCS.readLine()) != null)
        sharedFiles.add(fileName);

      brCS.close();
      frCS.close();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
      System.err.println("Failed to open file '" + fileConfigSharing);
      System.out.println();
    } catch (IOException e) {
      e.printStackTrace();
      System.err.println("Failed to read file '" + fileConfigSharing);
      System.out.println();
    }
  }

  // Check if the host has the required shared files
  private static boolean hasSharedFiles(String fileName) {
    boolean hasFile = false;

    for (String sharedFile : sharedFiles) {
      if (sharedFile.equals(fileName))
        hasFile = true;
    }

    return hasFile;
  }

  // Read config_neighbors.txt
  private static void getNeighbors() {
    try {
      FileReader frCN = new FileReader(fileConfigNeighbors);
      BufferedReader brCN = new BufferedReader(frCN);

      int i = 0;
      String neighbor = null;
      while ((neighbor = brCN.readLine()) != null) {
        String[] neighborArr = neighbor.split(":");
        ipAddress[i] = neighborArr[0];
        portNumber[i] = Integer.parseInt(neighborArr[1]);
        i++;
      }

      brCN.close();
      frCN.close();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
      System.err.println("Failed to open file '" + fileConfigNeighbors);
      System.out.println();
    } catch (IOException e) {
      e.printStackTrace();
      System.err.println("Failed to read file '" + fileConfigNeighbors);
      System.out.println();
    }
  }
  
  // Establish TCP connections to the neighbors
  public static void connect() {
    for (int i = 0; i < numNeighbors; i++) {
      System.out.println("Attempting to connect to host '" + ipAddress[i] + "' at port '" + portNumber[i] + "'");

      try {
        // check if the connection will be a duplicate and not closed
        boolean addedConnection = false;
        for (int j = 0; j < numNeighbors; j++) {
          if (neighborSocket[j] != null) {
            String neighborName = neighborSocket[j].getInetAddress().getHostName();
            String[] brokenNeighborName = neighborName.split("\\.");
            neighborName = brokenNeighborName[0] + ".case.edu";

            if (!neighborSocket[j].isClosed() && ipAddress[i].equals(neighborName)) {
              addedConnection = true;
              // If there is a duplicate connection, do not open connection again
              System.err.println("Connection is duplicate so connection request terminated");
              System.out.println();
            }
          }
        }

        // create connection if not duplicate or has been closed previously
        for (int j = 0; !addedConnection && j < numNeighbors; j++) {
          if (neighborSocket[j] == null || neighborSocket[j].isClosed()) {
            addedConnection = true;
            // Open a connection if there is no duplicate connection
            neighborSocket[i] = new Socket(ipAddress[i], portNumber[i]);
            p2pListenQuery listenQueryThread = new p2pListenQuery(i);
            listenQueryThread.start();
      
            System.out.println("Connection successful");
            System.out.println();
          }
        }
      } catch (IOException e) {
        e.printStackTrace();
        System.err.println(
                           "Failed to create the socket to host '" + ipAddress[i] + "' at port '" + portNumber[i]);
        System.out.println();
        System.out.println("Connection failed");
        System.out.println();
      }
    }
  }

  //
  // Functions Deals with queries
  //

  // Send query to find the coreesponding file
  // Take the input of the name of this file
  public static void querySending(String fileName) {
    String query = "Q:" + localHost.getHostName() + "_" + queryNum + ";" + fileName;

    DataOutputStream outToNeighbor = null;
    for (int i = 0; i < numNeighbors; i++) {
      try {
        if (neighborSocket[i] != null && !neighborSocket[i].isClosed()) {
          outToNeighbor = new DataOutputStream(neighborSocket[i].getOutputStream());
          // Sending query to the host which has this file
          System.out.println("Sending query '" + query + "' to host '"
                               + neighborSocket[i].getInetAddress().getHostName());
          System.out.println();
          outToNeighbor.writeBytes(query + '\n');
          outToNeighbor.flush();
          // Close the DataOutputStream
          // outToNeighbor.close();
        }
      } catch (IOException e) {
        e.printStackTrace();
        System.err.println("Failed to send query to this host");
        System.out.println();
      }
    }
    queryNum++;
  }

  // Listen to incoming query requests
  // Take a input of the origin of this query
  public static void queryListening(int neighbor) {
    while (neighborSocket[neighbor] != null && !neighborSocket[neighbor].isClosed()) {
      try {
        BufferedReader inFromServer = new BufferedReader(
                                                         new InputStreamReader(neighborSocket[neighbor].getInputStream()));

        String query = inFromServer.readLine();

        // if a neighbor closed the connection
        if (query == null) {
          neighborSocket[neighbor] = null;
        }
        // if this query is received
        else {
          System.out.println("Received incoming query '" + query);
          System.out.println();

          p2p.queryType(neighborSocket[neighbor].getInetAddress(), query);
        }
      } catch (IOException e) {
        e.printStackTrace();
        System.err.println("Failed to listen for incoming queries");
        System.out.println();
      }
    }
  }

  // If this query requesting for a file, check if this host has this file. Returns a message that this host has 
  // this file; otherwise forward this query to neighbors
  // If the query is a response message for a previous query, check if the query originated from the current host, 
  // otherwise forward it to the neighbor.
  // If the query is a request to transfer a file, transfer the file.
  // Take inputs of hostname where the query came from, and the query request
  public static void queryType(InetAddress hostName, String query) {
    // if the query is a request for a file
    if (query.charAt(0) == 'Q') {
      p2p.queryFindRequest(hostName, query);
    }
    // if the query is a response message
    else if (query.charAt(0) == 'R') {
      p2p.queryResponse(hostName, query);
    }
  }
  
  // Deal with find file query
  // Take inputs of hostname where the query came from, and the query request
  public static void queryFindRequest(InetAddress hostName, String query) {
    String[] splitQuery = query.split(":|_|;");
    // Split this query
    DataOutputStream outToNeighbor = null;

    // if the local host does not have the file
    if (!p2p.hasSharedFiles(splitQuery[3])) {
      for (int i = 0; i < numNeighbors; i++) {
        if (neighborSocket[i] != null && !neighborSocket[i].isClosed()) {
          if (!neighborSocket[i].getInetAddress().equals(hostName)) {
            // forwarding query
            try {
              outToNeighbor = new DataOutputStream(neighborSocket[i].getOutputStream());

              System.out.println("File '" + splitQuery[3] + "' not found");
              System.out.println("Forwarding query '" + query + "' to host '"
                                   + neighborSocket[i].getInetAddress().getHostName());
              System.out.println();
              outToNeighbor.writeBytes(query + '\n');
              outToNeighbor.flush();
              
              // outToNeighbor.close();
            } catch (IOException e) {
              e.printStackTrace();
              System.err.println("Failed to forward query");
              System.out.println();
            }
          }
        }
      }
    }
    // if the local host has the file
    else {
      for (int i = 0; i < numNeighbors; i++) {
        if (neighborSocket[i] != null && !neighborSocket[i].isClosed()) {
          if (neighborSocket[i].getInetAddress().equals(hostName)) {
            String responseMessage = "R:" + splitQuery[1] + "_" + splitQuery[2] + ";"
              + localHost.getHostName() + ":" + fileTransferPortNum + ";" + splitQuery[3];

            // responding to query
            try {
              outToNeighbor = new DataOutputStream(neighborSocket[i].getOutputStream());

              System.out.println("File '" + splitQuery[3] + "' found");
              System.out.println("Responding to query '" + query + "' with '" + responseMessage);
              System.out.println();
              outToNeighbor.writeBytes(responseMessage + '\n');
              outToNeighbor.flush();

              // outToNeighbor.close();
            } catch (IOException e) {
              e.printStackTrace();
              System.err.println("Failed to respond to query");
              System.out.println();
            }
          }
        }
      }
    }
  }

  // Deal with response query
  // Take inputs of hostname where the query came from, and the query request
  public static void queryResponse(InetAddress hostName, String query) {
    String[] splitQuery = query.split(":|_|;");

    // if the response is for a query this host sent
    if (splitQuery[1].equals(localHost.getHostName())) {
      // send file request query
      System.out.println("Response '" + query + "' received");
      System.out.println();

      p2p.sendFileRequest(query);
    }
    // if the response is not for a query this host sent
    else {
      DataOutputStream outToNeighbor = null;

      for (int i = 0; i < numNeighbors; i++) {
        if (neighborSocket[i] != null && !neighborSocket[i].isClosed()) {
          if (!neighborSocket[i].getInetAddress().equals(hostName)) {
            // forwarding response
            try {
              outToNeighbor = new DataOutputStream(neighborSocket[i].getOutputStream());

              System.out.println("Forwarding response '" + query);
              System.out.println();
              outToNeighbor.writeBytes(query + '\n');
              outToNeighbor.flush();

              // outToNeighbor.close();
            } catch (IOException e) {
              e.printStackTrace();
              System.err.println("Failed to forward response");
              System.out.println();
            }
          }
        }
      }
    }
  }

  // Deal with transfer file query
  // Take inputs of the socket the query came from and the host name the query came from
  public static void queryTransferRequest(Socket socket, InetAddress hostName) {
    // get the transfer file query request
    String query = null;
    try {
      BufferedReader inFromClient = new BufferedReader(new InputStreamReader(socket.getInputStream()));
      query = inFromClient.readLine();

      System.out.println(
                         "Received transfer file query request '" + query + "' from host '" + hostName.getHostName() + "'");
    } catch (IOException e) {
      e.printStackTrace();
      System.err.println("Failed to receive transfer file query request");
      System.out.println();
    }

    String[] splitQuery = query.split(":");

    File sharedFile = new File("shared/" + splitQuery[1]);
    byte[] buffer = new byte[(int) sharedFile.length()];
    try {
      // get the file information
      BufferedInputStream bisSharedFile = new BufferedInputStream(new FileInputStream(sharedFile));
      bisSharedFile.read(buffer, 0, buffer.length);

      // transfer the file information
      System.out.println("Transferring file '" + splitQuery[1] + "' to host '" + hostName.getHostName());
      System.out.println();
      DataOutputStream outToClient = new DataOutputStream(socket.getOutputStream());
      outToClient.write(buffer, 0, buffer.length);
      outToClient.flush();

      bisSharedFile.close();
      outToClient.close();
      socket.close();
    } catch (IOException e) {
      e.printStackTrace();
      System.err.println("Failed to transfer file");
      System.out.println();
    }
  }


  // Request a file from another host
  // Take an input of the response query
  public static void sendFileRequest(String query) {
    String[] splitQuery = query.split(":|_|;");

    // initialize connection to the host that has the file
    Socket fileTransfer = null;
    try {
      fileTransfer = new Socket(splitQuery[3] + ".case.edu", Integer.parseInt(splitQuery[4]));
      System.out.println("Connection successful");
      System.out.println();
    } catch (NumberFormatException e) {
      e.printStackTrace();
      System.err.println("Failed to determine port number to host");
      System.out.println();
      System.out.println("Connection failed");
      System.out.println();
    } catch (IOException e) {
      e.printStackTrace();
      System.err.println(
                         "Error creating the socket to host '" + splitQuery[3] + "' at port '" + splitQuery[4]);
      System.out.println();
      System.out.println("Connection failed");
      System.out.println();
    }

    String fileRequest = "T:" + splitQuery[5];
    System.out.println("Requesting file '" + fileRequest + "' from host '" + splitQuery[3]);
    System.out.println();

    DataOutputStream outToServer = null;
    InputStream inFromServer = null;
    File receivedFile = new File("obtained/" + splitQuery[5]);
    FileOutputStream fosReceivedFile = null;
    // Send the file to the obtained folder of requesting host
    try {
      outToServer = new DataOutputStream(fileTransfer.getOutputStream());
      inFromServer = fileTransfer.getInputStream();

      // request the file
      outToServer.writeBytes(fileRequest + '\n');
      outToServer.flush();

      // create a new file
      receivedFile.createNewFile();
      fosReceivedFile = new FileOutputStream(receivedFile);
      BufferedOutputStream bosReceivedFile = new BufferedOutputStream(fosReceivedFile);
   
      // write the incoming file data
      int byteRead = 0;

      while ((byteRead = inFromServer.read()) != -1) {
     bosReceivedFile.write(byteRead);
   }

      System.out.println("Received requested file '" + splitQuery[5]);
      System.out.println();

      bosReceivedFile.close();
      fosReceivedFile.close();

      // outToServer.close();
      // inFromServer.close();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
      System.err.println("Failed to open file '" + splitQuery[5]);
      System.out.println();
    } catch (IOException e) {
      e.printStackTrace();
      System.err.println("Failed to receive file transfer");
      System.out.println();
    }
  }

  // Close all connetctions with neighbors
  public static void leave() {
    for (int i = 0; i < numNeighbors; i++) {
      try {
        if (neighborSocket[i] != null && !neighborSocket[i].isClosed()) {
          neighborSocket[i].close();
          neighborSocket[i] = null;
        }
      } catch (IOException e) {
        e.printStackTrace();
        System.err.println("Failed to close the socket");
        System.out.println();
      }
    }
  }

  // Close all connections in this program
  public static void exit() {
    // close listening socket
    try {
      welcomeSocket.close();
      fileTransferWelcomeSocket.close();
    } catch (IOException e) {
      e.printStackTrace();
      System.err.println("Failed to close the socket");
      System.out.println();
    }

    // close connections with neighbors
    p2p.leave();

    // terminate
    System.exit(0);
  }
}