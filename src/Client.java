import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class Client {

    public static void main(String[] args) {
        new Client().runClient();
    }

    public void runClient() {
        int port = 51234;
        byte[] buffer = new byte[2048];
        String fileName = "temoprary";
        DatagramSocket receiver = null;
        // retry loop for initializing
        while(true) {

            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setSoTimeout(10000);

                while (true) {

                    // initial request
                    InetAddress serverAddress = InetAddress.getByName("localhost");
                    String initialMessage = "DOWNLOAD " + fileName;
                    DatagramPacket sendPacket = new DatagramPacket(initialMessage.getBytes(), initialMessage.length(), serverAddress, port);
                    socket.send(sendPacket);

                    // Receive response and break apart for assessment
                    buffer = new byte[2048];
                    DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length, serverAddress, port);
                    socket.receive(receivePacket);
                    String message = new String(receivePacket.getData(), 0, receivePacket.getLength()).trim();
                    String[] split = message.split(" ");

                    //response = 0OK " + 1fileName + " 2SIZE " + 3fileSize + " 4PORT " + freePort;

                    // OK found
                    if (split.length == 6 && split[0].equals("OK") && split[1].equals(fileName) ) {
                        long size = Integer.parseInt(split[3]);
                        int newPort = Integer.parseInt(split[5]);

                        while(true){

                        }



                    }


                    // ERR file not found
                    if (split.length == 2 && split[0].equals("ERR") && split[1].equals("NOT_FOUND") ) {
                        System.out.println("File not found ... Exit");
                        socket.close();
                        System.exit(0);
                    }

                    System.out.println("Received: " + message);


                } // main loop
            } catch (IOException e) {
                System.out.println("ERROR: " + e.getMessage());
            } finally {

            }

            System.out.println("Retry Send.....");
        }
    }// Run end
}