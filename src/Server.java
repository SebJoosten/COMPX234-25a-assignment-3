import java.io.IOException;
import java.net.*;

public class Server {

    public static void main(String[] args) {
        Server server = new Server();
        server.runServer(args);
    }

    public void runServer(String[] args) {
        DatagramSocket socket = null;
        int port = 51234;

        while (true) {              // Main while loop keeps re trying
            try {                   // Start listening and set timeout and variable
                socket = new DatagramSocket(port);
                socket.setSoTimeout(10000);
                byte[] receiveData = new byte[2048];

                while (true) {
                    DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                    socket.receive(receivePacket);

                    // Convert a message to string > parse and respond
                    String message = new String(receivePacket.getData(), 0, receivePacket.getLength());
                    String response = "ERR NOT_PARSEABLE";
                    String[] parts;
                    if (message.split(" ", 2).length == 2) {
                        parts = message.split(" ", 2);
                        String command = parts[0];
                        String data = parts.length > 1 ? parts[1] : "";

                        // Check for command
                        if (command.equals("DOWNLOAD")) {
                            if (!data.isEmpty()) {

                                //************ VVV REPLACE CODE VVV ************
                                // Replace with file name check
                                String fileName = "temoprary";
                                System.out.println(data);
                                boolean fileExists = true; // Will work this out later placeholder
                                //************ ^^^ REPLACE CODE ^^^ ************

                                if (fileExists) {
                                    //************ VVV REPLACE CODE VVV ************
                                    // Replace with file details
                                    // Get file details
                                    //File file = new File("example.txt");
                                    //long fileSize = file.length();
                                    long fileSize = 123456;
                                    //************ ^^^ REPLACE CODE ^^^ ************

                                    int freePort = portFinderUDPPort();
                                    if (freePort != -1) {
                                        response = "OK " + fileName + " SIZE " + fileSize + " PORT " + freePort;
                                        Thread thread = new Thread(new FileMoverTask(fileName, freePort));
                                        thread.start();
                                    }
                                }
                            } else response = "ERR NOT_FOUND";
                        } // Command wrong response = "ERR NOT_PARSEABLE"
                    } // Split wrong response = "ERR NOT_PARSEABLE"

                    // Prep and send a return packet
                    System.out.println("Server: " + response + " : " + message);
                    DatagramPacket sendPacket = new DatagramPacket(response.getBytes(), response.length(), receivePacket.getAddress(), receivePacket.getPort());
                    socket.send(sendPacket);

                } // Main loop end
            } catch (IOException e) {
                System.out.println("ERROR: " + e.getMessage());
            } finally {
                if (socket != null && !socket.isClosed()) socket.close();
            } // sub loop
        } // main loop
    }


    /**
     * Free Port Finder quickly looks for a free port.
     * @return - A free port hopefully
     */
    public int portFinderUDPPort() {
        for (int port = 50001; port <= 51000; port++) {
            try (DatagramSocket socket = new DatagramSocket(port)) {
                return port;
            } catch (IOException ignored) {}
        }
        return -1; // Base case no free port
    }


    /**
     * File mover thread
     * Used to spin off a new thread for file transfur
     */
    private class FileMoverTask implements Runnable {
        private int port;
        private String fileName , ID;
        private byte[] receiveData = new byte[2048];
        private DatagramSocket socket = null;

        public FileMoverTask(String fileName, int port) {
            this.fileName = fileName;
            this.port = port;
            this.ID = "FileMoverTask " + fileName + " port: " + port;
        }

        @Override
        public void run() {
            System.out.println("LOG: " + ID + " STARTED" );

            try  {
                socket = new DatagramSocket(port);
                socket.setSoTimeout(10000);

                while (true) {

                    //************ VVV REPLACE CODE VVV ************
                    DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                    socket.receive(receivePacket);

                    // Check packet content
                    // generate return packet
                    String response = "testing 123";

                    // Send a packet
                    DatagramPacket sendPacket = new DatagramPacket(response.getBytes(), response.length(), receivePacket.getAddress(), receivePacket.getPort());
                    socket.send(sendPacket);

                    //************ ^^^ REPLACE CODE ^^^ ************

                }

            } catch (IOException e) {
                System.out.println("ERROR: " + ID + e.getMessage());
            }finally {
                if (!socket.isClosed()) socket.close();
            }
            System.out.println("LOG: " + ID + " CLOSED" );
        }
    }
}
