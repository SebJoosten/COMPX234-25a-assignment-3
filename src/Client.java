import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;

public class Client {

    public static void main(String[] args) {
        new Client().runClient();
    }

    public void runClient() {

        // retry loop for initializing
        while(true) {
            int port = 51234;
            byte[] buffer = new byte[2048];
            String fileName = "temoprary";
            DatagramSocket receiver = null;

            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setSoTimeout(10000);

                // Initial request
                InetAddress serverAddress = InetAddress.getByName("localhost");
                String initialMessage = "DOWNLOAD " + fileName;
                DatagramPacket sendPacket = new DatagramPacket(initialMessage.getBytes(), initialMessage.length(), serverAddress, port);
                System.out.println("Initial Sending: " + initialMessage);
                socket.send(sendPacket);

                // Receive response and break apart for assessment
                buffer = new byte[2048];
                DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length, serverAddress, port);
                socket.receive(receivePacket);
                String message = new String(receivePacket.getData(), 0, receivePacket.getLength()).trim();
                System.out.println("Initial Received: " + message);
                String[] split = message.split(" ");

                // OK found > check port and size parse
                if (split.length == 6 && split[0].equals("OK") && split[1].equals(fileName) ) {
                    long size , current = 0;
                    int newPort;
                    try {
                        size = Long.parseLong(split[3]);
                        newPort = Integer.parseInt(split[5]);

                        while(true){

                            // Send request
                            String requestPart = "FILE " + fileName + " GET START " + current + " END " + (current + 1000);
                            sendPacket = new DatagramPacket(requestPart.getBytes(), requestPart.length(), serverAddress, newPort);
                            System.out.println("Secondary Sending: " + requestPart);
                            socket.send(sendPacket);

                            // receive request
                            buffer = new byte[2048];
                            receivePacket = new DatagramPacket(buffer, buffer.length, serverAddress, newPort);
                            socket.receive(receivePacket);
                            message = new String(receivePacket.getData(), 0, receivePacket.getLength()).trim();
                            System.out.println("Received: " + message);
                            split = message.split(" ");

                            if (split.length == 9 && split[0].equals("FILE") && split[1].equals(fileName) && split[2].equals("OK") ) {
                                String data = "";
                                int start = 0, end = 0;

                                // Parse data
                                try {
                                    start = Integer.parseInt(split[4]);
                                    end = Integer.parseInt(split[6]);
                                    data = split[8];
                                } catch (NumberFormatException e) {
                                    throw new NumberFormatException("ERROR: Port or size number failed to parse");
                                }
                            }

                            requestPart = "FILE " + fileName + " CLOSE";
                            sendPacket = new DatagramPacket(requestPart.getBytes(), requestPart.length(), serverAddress, newPort);
                            socket.send(sendPacket);
                            socket.close();
                            throw new IOException("Socket closed");

                        }// end download loop
                    } catch (NumberFormatException e) {
                        throw new NumberFormatException("ERROR: Port or size number failed to parse");
                    }

                // Else error message
                } else if (split.length == 2 && split[0].equals("ERR") && split[1].equals("NOT_FOUND") ) {
                    System.out.println("File not found ... Exit");
                    socket.close();
                    System.exit(0);
                }

            } catch (IOException e) {
                System.out.println("ERROR: " + e.getMessage());
            }
            System.out.println("Retry Send.....");

        }// main loop
    }// Run end
}