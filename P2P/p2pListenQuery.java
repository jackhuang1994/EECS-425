// Program: p2pListenQuery.java
// Author: Mingan Huang 
// Network ID: mxh805

class p2pListenQuery implements Runnable {
    private Thread threadListenQuery = null;
    private String threadName = "Listen Query Thread";

    private int neighbor = 0;

    // Constructor
    public p2pListenQuery(int neighbor) {
      this.neighbor = neighbor;
    }

    public void start() {
      if (threadListenQuery == null) {
        threadListenQuery = new Thread(this, threadName);
        threadListenQuery.start();
      }
    }

    @Override
    public void run() {
      p2p.queryListening(neighbor);
    }
  }