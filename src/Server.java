import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

public class Server {

    public static void main(String[] args) {
        DatagramSocket socket = null;
        try {
            // Start listening on socket change later.
            socket = new DatagramSocket(51234);

            // Set receive variable 2042 should be enough for 1000 bytes and protocol code
            byte[] receiveData = new byte[2048];

            while (true) {

                //wait for a packet
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                socket.receive(receivePacket);

                // Get data out of a packet and load it in to variables for later > set-up response
                String message = new String(receivePacket.getData(), 0, receivePacket.getLength());
                InetAddress clientAddress = receivePacket.getAddress();
                int clientPort = receivePacket.getPort();
                String response = "ERR NOT_PARSEABLE";

                // Split and check message and check
                String[] parts;
                if (message.split(" ", 2).length == 2) {

                    parts = message.split(" ", 2);
                    String command = parts[0];
                    String data = parts.length > 1 ? parts[1] : "";

                    if (command.equals("DOWNLOAD")) {
                        if(!data.isEmpty()) {

                            // Check for the file name !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
                            String fileName = "temoprary";

                            System.out.println(data);
                            // For now, print the message !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
                            boolean fileExists = true; // Will work this out later place holder
                            if(fileExists){

                                // Get file details
                                //File file = new File("example.txt");
                                //long fileSize = file.length();
                                long fileSize = 123456;

                                int freePort = portFinderUDPPort();

                                response = "OK " + fileName + " SIZE " + fileSize + " PORT " + freePort;

                            }
                        }
                    }

                }

                // Print message
                System.out.println("Received Download request: Response: " + response);

                // Prep and send a return packet
                DatagramPacket sendPacket = new DatagramPacket(response.getBytes(), response.length(), clientAddress, clientPort);
                socket.send(sendPacket);
            }


        // Catch for exceptions
        } catch (Exception e) {
            System.out.println(e.toString() + " \n Server ");
        } finally {

            // Double check socket close
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }



    /**
     * Free Port Finder quickly looks for a free port.
     * Use the port immediately away before its taken by something elce
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
