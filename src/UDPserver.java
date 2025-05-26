import java.io.*;
import java.net.*;
import java.util.Base64;
import java.util.concurrent.Semaphore;
public class UDPserver{

    // Used to count the number of current streams for status animation
    private Semaphore streamCount = new Semaphore(0);
    // Sets if error printing happens or not
    Boolean errLog = false;

    /**
     * Main starting method calls the run method and starts listening
     * @param args The Only argument is the port number if it's not valued it will use a default of 51234
     * If a port is not available or cant open it will close the program
     */
    public static void main(String[] args) {
        UDPserver server = new UDPserver();
        server.runServer(args);
    }

    /**
     * This is the main program for listening.
     * It will listen to the given port and start a new thead when downloading starts
     * @param args - port number
     */
    public void runServer(String[] args) {

        // Set default port and parse input port
        int port = 51232;
        try {
            port = Integer.parseInt(args[0]);
            System.out.println("Server listening on port " + port);
        } catch (NumberFormatException e) {
            System.out.println("Invalid port number. Using default: " + port);
        }

        // Check and set up socket
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket(port);
            socket.setSoTimeout(10000);
        } catch (SocketException e) {
            System.err.println("Failed to open socket on port: " + port + ": " + e.getMessage());
            System.exit(1);
        }

        // Set up variables and get root directory
        byte[] receiveData = new byte[2048];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);;
        File root = new File(System.getProperty("user.dir"),"Server_files");

        // Start the animated bar for server status
        StatusBar statusBar = new StatusBar();
        Thread status = new Thread(statusBar);
        status.start();

        // MAIN listening loop
        while (true) {

            // Enable Disable error log printing
            if(!errLog){
                System.setErr(new PrintStream(new OutputStream() {
                    public void write(int b) {}
                }));
            }

            // Catch for timeouts and start listening for requests
            try {
                socket.receive(receivePacket);
                String message = new String(receivePacket.getData(), 0, receivePacket.getLength());
                String[] parts = message.split(" ");
                System.out.println("\rLOG: server received: " + message);

                // Set ERR fallback return message and check received message
                String response = "ERR NOT_PARSEABLE";
                if (parts.length == 2 && parts[0].equals("DOWNLOAD")) {

                    // Get file name
                    String fileName = parts[1];
                    File target = new File(root, fileName);

                    // Set NOT_FOUND fallback response and check the file exists
                    response = "ERR NOT_FOUND";
                    if (target.exists() && target.isFile()) {

                        // Small loop to find a free port between 50,001 and 51,000
                        response = "ERR NO_FREE_PORT";
                        for (int freePort = 50001; freePort <= 51000; freePort++) {
                            try (DatagramSocket ignored1 = new DatagramSocket(freePort)) {

                                // PORT FOUND - send port to the client and start new server thread
                                response = "OK " + fileName + " SIZE: " + target.length() + " PORT: " + freePort;
                                Thread transfer = new Thread(new FileMoverTask( freePort, target));
                                transfer.start();
                                break;

                            } catch (IOException ignored){}
                        }

                    // FILE NOT FOUND
                    } else System.out.println("\rLOG: FILE NOT FOUND: " + message);

                // UNKNOWN request
                } else System.out.println("\rLOG: Unknown request: " + message);

                // SEND response
                socket.send(new DatagramPacket(response.getBytes(), response.length(), receivePacket.getAddress(), receivePacket.getPort()));

            // CATCH ALL - reset the loop and wait again
            } catch (IOException e) {
                System.err.println("\rServer LOG: " + e.getMessage());
            }
        }
    }

    /**
     * File mover thread class used for sending a file to a new port
     */
    private class FileMoverTask implements Runnable {
        private int port , retryCount = 0;
        private String ID;
        private File target;

        /**
         * This is to build and launch a file mover - a thread that handles the file transfur
         * @param port - The port you're going to be listening on
         * @param target - The target file you wish to send
         */
        public FileMoverTask(int port , File target) {
            this.target = target;
            this.port = port;
            this.ID = "FileMoverTask " + target.getName() + " port: " + port;
            streamCount.release();
        }

        @Override
        public void run() { System.out.println("\rLOG: " + ID + " STARTED" );

            // Declare as much as we can before going on to the main loop this significantly improved performance
            String filename = target.getName(), response = "ERR INVALID_COMMAND";
            byte[] buffer , receiveData = new byte[2048];
            Base64.Encoder encoder = Base64.getEncoder();
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            String[] split;

            // Setup file access and socket
            try (
                    RandomAccessFile openFile = new RandomAccessFile(target, "r");
                    DatagramSocket newSocket = new DatagramSocket(port)
            ) {
                newSocket.setSoTimeout(10000);

                // Main file streaming loop
                while (true) {

                    // Receive a message and split it up
                    newSocket.receive(receivePacket);
                    split = new String(receivePacket.getData(), 0, receivePacket.getLength()).trim().split(" ");

                    // Check to make sure we can understand the message
                    if (split.length == 7 && split[0].equals("FILE") && split[1].equals(filename) && split[2].equals("GET") && split[3].equals("START") && split[5].equals("END")) {

                        // Parses start the end points
                        long start = Long.parseLong(split[4]);
                        long end = Long.parseLong(split[6]);
                        int length = (int)(end - start);

                        // Make sure the length is not negative
                        if (length > 0 ){
                            retryCount = 0;
                            buffer = new byte[length];
                            openFile.seek(start);
                            openFile.read(buffer, 0, length);
                            response = "FILE " + filename + " OK START " + start + " END " + end + " DATA " + encoder.encodeToString(buffer);
                        }else{
                            response = "ERR INVALID_COMMAND";
                            retryCount++;
                        }

                    // Check for CLOSE message and set response
                    } else if (split.length == 3 && split[0].equals("FILE") && split[1].equals(filename) && split[2].equals("CLOSE")) {
                        response = "FILE " + filename + " CLOSE_OK";

                    // If we cant work out the message send ERR INVALID_COMMAND
                    } else {
                        response = "ERR INVALID_COMMAND";
                        retryCount++;
                    }

                    // SEND response and check for close condiction
                    newSocket.send(new DatagramPacket(response.getBytes(), response.length(), receivePacket.getAddress(), receivePacket.getPort()));
                    if (response.contains("CLOSE_OK")) break;

                    // Testing
                    // Thread.sleep(2000);
                }

            // CATCH ALL
            } catch (IOException e) { System.err.println("\rERROR: " + ID + e.getMessage());

            // For slow server testing
            //} catch (InterruptedException e) {
            //    throw new RuntimeException(e);

            // Print to log and update semaphore for status bar
            } finally {
                System.out.println("\rLOG: " + ID + " CLOSED" );
                if (streamCount.availablePermits() > 0) {
                    try {
                        streamCount.acquire();
                    } catch (InterruptedException e) {
                        System.err.println("\rSemaphore error: " + e.getMessage() );
                    }
                }
            }
        }
    }


    /**
     * Animated status bar tells you if the server is up or not basically,
     * It worked based on the number of stream counts.
     * If it's more than 0 it will "run"
     */
    public class StatusBar extends Thread {
        @Override
        public void run() {
            int filledBars = 0, direction = 1 , streams = 0;
            String gap = " ";

            // Animation loop
            while (!Thread.currentThread().isInterrupted()) {

                // Waiting animation
                if (streamCount.availablePermits() <= 0) {
                    StringBuilder bar = new StringBuilder("[");
                    for (int i = 0; i <= 20; i++) {
                        if (i == filledBars) {
                            bar.append("█");
                        } else {
                            bar.append(gap);
                        }
                    }
                    bar.append("] " + "Streams = ").append(streamCount.availablePermits()).append(" Waiting ");

                    filledBars += direction;
                    if (filledBars == 20) {
                        direction *= -1;
                    } else if (filledBars == 0) {
                        direction *= -1;
                    }
                    System.out.print("\r" + bar + " ");

                // Running animation
                }else{
                    if (direction == 1){
                        System.out.print("\r[ > > > > > > > > > > ] Streams = " + streamCount.availablePermits() + " Sending ");
                        direction *= -1;
                    }else {
                        System.out.print("\r[> > > > > > > > > > >] Streams = " + streamCount.availablePermits() + " Sending ");
                        direction *= -1;
                    }
                }

                // Catch for interrupt to close thread
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }
}
