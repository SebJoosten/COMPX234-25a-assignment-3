import java.io.IOException;
import java.net.*;

public class Server {

    public static void main(String[] args) {
        DatagramSocket socket = null;
        int port = 51234;

        while (true) { // Main while loop keeps re trying

            try {       // Start listening
                socket = new DatagramSocket(port);
                socket.setSoTimeout(10000);

                // Set receive variable 2042 should be enough for 1000 bytes and protocol code
                byte[] receiveData = new byte[2048];

                while (true) {

                    //wait for a packet
                    try {
                        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                        socket.receive(receivePacket);


                        // Get data out of a packet and load it in to variables for later > set-up response
                        String message = new String(receivePacket.getData(), 0, receivePacket.getLength());
                        InetAddress clientAddress = receivePacket.getAddress();
                        int clientPort = receivePacket.getPort();
                        String response = "ERR NOT_PARSEABLE";  // Default IE we dont know whats going on

                        // Split and check message
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

                                        response = "OK " + fileName + " SIZE " + fileSize + " PORT " + freePort;

                                        DatagramPacket sendPacket = new DatagramPacket(response.getBytes(), response.length(), clientAddress, clientPort);
                                        socket.send(sendPacket);
                                        System.out.println("SUCCESS: " + fileName + " Sending to port " + freePort);

                                        // SPIN off new thread

                                        continue; // Start loop again
                                    }
                                }
                                response = "ERR NOT_FOUND"; // File not found OR file name not parsable
                            } // if DOWNLOAD
                        } // if split

                        // Prep and send a return packet
                        System.out.println("ERROR: download request: " + response);
                        DatagramPacket sendPacket = new DatagramPacket(response.getBytes(), response.length(), clientAddress, clientPort);
                        socket.send(sendPacket);

                    }catch (SocketTimeoutException e) {
                        System.out.println("TIMEOUT: No packet received in 10 seconds.");
                    }

                } // Main loop end
            } catch (SocketException e) {
                System.out.println("ERROR: SocketException: " + e.getMessage());
            } catch (IOException e) {
                System.out.println("ERROR: IOException: " + e.getMessage());
            } finally {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                    socket = null;
                }
            } // sub loop
        } // main loop
    }


    /**
     * Free Port Finder quickly looks for a free port.
     * Use the port immediately away before its taken by something else
     * @return - A free port hopefully
     */
    public static int portFinderUDPPort() {
        for (int port = 50001; port <= 51000; port++) {
            try (DatagramSocket socket = new DatagramSocket(port)) {
                return port;
            } catch (IOException ignored) {}
        }
        return -1; // Base case no free port
    }

}
