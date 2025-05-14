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

                                if (!fileExists) {
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
                                    } else response = "ERR NO_FREE_PORT";
                                } else response = "ERR NOT_FOUND";
                            } // Command wrong response = "ERR NOT_PARSEABLE"
                        } // Command wrong response = "ERR NOT_PARSEABLE"
                    } // Split wrong response = "ERR NOT_PARSEABLE"

                    // Prep and send a return packet
                    System.out.println("Server: " + response + " : " + message);
                    DatagramPacket sendPacket = new DatagramPacket(response.getBytes(), response.length(), receivePacket.getAddress(), receivePacket.getPort());
                    socket.send(sendPacket);

                } // Main loop end
            } catch (IOException e) {
                System.out.println("ERROR: " + e.getMessage());
                if (socket != null) socket.close();
            } // Sub loop
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
     * File mover thread class used for spinning off a sending thread
     */
    private class FileMoverTask implements Runnable {
        private int port;
        private String fileName , ID ;
        private byte[] receiveData = new byte[2048];
        private DatagramSocket socket = null;

        /**
         * This is what you call to start a new thread running for the file transfur
         * @param fileName - The file name for conformation, and so we know what to send
         * @param port - The port the new connection is on
         */
        public FileMoverTask(String fileName, int port) {
            this.fileName = fileName;
            this.port = port;
            this.ID = "FileMoverTask " + fileName + " port: " + port;
        }

        @Override
        public void run() { System.out.println("LOG: " + ID + " STARTED" );

            try  {
                socket = new DatagramSocket(port);
                socket.setSoTimeout(15000);
                String message, response;

                // Start listening loop. Resets when a packet is formatted incorrectly
                while (true) {
                    DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                    socket.receive(receivePacket);
                    message = new String(receivePacket.getData(), 0, receivePacket.getLength()).trim();
                    String[] split = message.split(" ");

                    // Check the received packet > make response and send it otherwise restart
                    if (split.length == 7 && split[0].equals("FILE") && split[1].equals(fileName) && split[2].equals("GET") && split[3].equals("START") && split[5].equals("END")) {
                        int start = Integer.parseInt(split[4]);
                        int end = Integer.parseInt(split[6]);

                        //  get the file content and formulate response string else re try
                        response = " The parts of the file to send back";

                    // Check for close
                    } else if(split.length == 3 && split[0].equals("FILE") && split[1].equals(fileName) && split[2].equals("CLOSE")){
                        response = "FILE " + fileName + " CLOSE_OK";
                    } else continue;

                    // Send
                    DatagramPacket sendPacket = new DatagramPacket(response.getBytes(), response.length(), receivePacket.getAddress(), receivePacket.getPort());
                    socket.send(sendPacket);
                    if (response.contains("CLOSE_OK")) break; // if close then exit the loop

                } // Loop until the file is finished
            } catch (IOException e) {
                System.out.println("ERROR: " + ID + e.getMessage());
            }
            System.out.println("LOG: " + ID + " CLOSED" );
        }
    }
}
