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
        int retryCount = 0;

        // retry loop for initializing
        while(true) {
            int port = 51234;
            byte[] buffer = new byte[2048];
            String fileName = "959309.png";
            DatagramSocket receiver = null;

            // TRY and make socket and parse response
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setSoTimeout(10000);
                InetAddress serverAddress = InetAddress.getByName("localhost");

                // INITIAL request
                String initialMessage = "DOWNLOAD " + fileName;
                DatagramPacket sendPacket = new DatagramPacket(initialMessage.getBytes(), initialMessage.length(), serverAddress, port);
                socket.send(sendPacket);

                // RECEIVE response and SPLIT
                buffer = new byte[2048];
                DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length, serverAddress, port);
                socket.receive(receivePacket);
                String message = new String(receivePacket.getData(), 0, receivePacket.getLength()).trim();
                String[] split = message.split(" ");

                // RESPONSE OK
                if (split.length == 6 && split[0].equals("OK") && split[1].equals(fileName) ) {

                    long start = 0 , end = 999, size = Long.parseLong(split[3]);
                    int  newPort = Integer.parseInt(split[5]);
                    System.out.println("File transfer "+ fileName + " started on port " + newPort);
                    String requestPart = "FILE " + fileName + " GET START " + start + " END " + end;
                    System.out.println("CLIENT connected on port: "  + newPort);
                    retryCount = 0;

                    // START DOWNLOAD LOOP - send data request
                    while(retryCount < 10){

                        // IF FILE COMPLETE --------------- NEEDS CLOSING CONDICTION
                        if(end > size) {
                            requestPart = "FILE " + fileName + " CLOSE";
                            System.out.print("\nDOWNLOAD " + fileName +  " COMPLETE");
                        }


                        sendPacket = new DatagramPacket(requestPart.getBytes(), requestPart.length(), serverAddress, newPort);
                        socket.send(sendPacket);

                        // RECEIVE data packet convert to string and split
                        receivePacket = new DatagramPacket(buffer, buffer.length, serverAddress, newPort);
                        socket.receive(receivePacket);
                        message = new String(receivePacket.getData(), 0, receivePacket.getLength()).trim();
                        split = message.split(" ");

                        // CHECK DATA
                        if (split.length == 9 && split[0].equals("FILE") && split[1].equals(fileName) && split[2].equals("OK") ) {

                            if(start == Integer.parseInt(split[4]) && end == Integer.parseInt(split[6])){
                                String data = split[8];
                                retryCount = 0;
                                start = end;
                                end += 1000;
                                requestPart = "FILE " + fileName + " GET START " + start + " END " + end;
                            } else {
                            System.out.println("<-- HALT!!! \nERROR: parsing GOT -> " + split[4] + " & " + split[6] + " Expecting " + start + " & " + end);
                            retryCount++;
                            continue;
                            }

                            // Little percentage print out
                            double percentage = (double) end / size * 100;
                            percentage = Math.floor(percentage * 100) / 100;
                            StringBuilder bar = new StringBuilder("[");
                            int filledBars = (int) (percentage / 100 * 30 );
                            for (int i = 0; i < 30; i++) {
                                if (i < filledBars) {
                                    bar.append("█");
                                } else {
                                    bar.append(" ");
                                }
                            }
                            bar.append("]");
                            System.out.print("\rDOWNLOADING FILE " + fileName + " " + percentage + " % " + bar.toString() );

                        // FILE CLOSE OK - Throw to exit
                        } else if (split.length == 3 && split[0].equals("FILE") && split[1].equals(fileName) && split[2].equals("CLOSE_OK")) {
                            retryCount = 10;
                            throw new IOException("FILE CLOSE OK ");
                        } else System.out.println("\nERROR: parsing GOT -- > " + message + " ON PORT: " + newPort);

                        retryCount ++;

                    // RETRIES EXCEEDED - Throw to exit
                    }  throw new IOException("ERROR: parsing Response after 10 retries");

                // FILE NOT FOUND - Throw to exit
                } else if (split.length == 2 && split[0].equals("ERR") && split[1].equals("NOT_FOUND") ) {
                    socket.close();
                    retryCount = 10;
                    throw new IOException("FILE NOT FOUND ");
                }

            // CATCH INITIAL parsing and IO errors - check for EXIT
            } catch (IOException e) {
                System.out.println(e.getMessage() + " " + retryCount + "/10" );
                retryCount++;
                if (retryCount >= 10) {
                    System.out.println(e.getMessage() + " EXITING ......");
                    System.exit(0);
                }
            }
        }
    }
}