import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;

public class Client {
    private final int MAX_RETRIES = 10;

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

            // TRY and make socket and parse response
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setSoTimeout(1000);
                InetAddress serverAddress = InetAddress.getByName("localhost");

                // INITIAL request
                String initialMessage = "DOWNLOAD " + fileName;
                DatagramPacket sendPacket = new DatagramPacket(initialMessage.getBytes(), initialMessage.length(), serverAddress, port);
                socket.send(sendPacket);

                // RECEIVE response and SPLIT
                DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length, serverAddress, port);
                socket.receive(receivePacket);
                String message = new String(receivePacket.getData(), 0, receivePacket.getLength()).trim();
                String[] split = message.split(" ");

                // RESPONSE OK
                if (split.length == 6 && split[0].equals("OK") && split[1].equals(fileName) ) {

                    // Declare as much as we can before going on to the main loops
                    long start = 0 , end = 999, size = Long.parseLong(split[3]);
                    int  newPort = Integer.parseInt(split[5]);
                    System.out.println("File transfer "+ fileName + " started on port " + newPort);
                    File root = new File(System.getProperty("user.dir"));
                    Path filePath = root.toPath().resolve("test" + fileName);
                    File partialFile = new File(filePath.toString());
                    Base64.Decoder decoder = Base64.getDecoder();
                    String encodedData;
                    String requestPart = "FILE " + fileName + " GET START " + start + " END " + end;

                    // RESUME function - If the file exists in part, continue from that part
                    if (partialFile.exists()) {
                        start = partialFile.length();
                        if (start >= size) {
                            retryCount = MAX_RETRIES;
                            requestPart = "FILE " + fileName + " CLOSE";
                        } else {
                            end = Math.min(start + 1000, size);
                            System.out.println("FILE Download resuming from byte " + start);
                            requestPart = "FILE " + fileName + " GET START " + start + " END " + end;
                        }
                    }

                    FileOutputStream outputStream = new FileOutputStream(partialFile, true);
                    retryCount = 0;

                    // START DOWNLOAD LOOP - send data request
                    while(retryCount < 10){

                        // RECEIVE data packet convert to string and split
                        socket.send(new DatagramPacket(requestPart.getBytes(), requestPart.length(), serverAddress, newPort));
                        socket.receive(receivePacket);
                        message = new String(receivePacket.getData(), 0, receivePacket.getLength()).trim();
                        split = message.split(" ");

                        // CHECK DATA
                        if (split.length >= 8 && split[0].equals("FILE") && split[1].equals(fileName) && split[2].equals("OK") ) {

                            // CHECK Bit positions and reconstruct data
                            if(start == Long.parseLong(split[4]) && end == Long.parseLong(split[6])){

                                encodedData = String.join(" ", Arrays.copyOfRange(message.split(" ", -1), 8, split.length));
                                outputStream.write(decoder.decode(encodedData));

                                if(end == size) {
                                    requestPart = "FILE " + fileName + " CLOSE";
                                    System.out.print("\nDOWNLOAD " + fileName +  " COMPLETE");
                                    outputStream.close();
                                    continue;
                                }

                                // Increment to next chunk
                                retryCount = 0;
                                start = end;
                                end = Math.min(end + 1000, size);
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
                            bar.append("] ");
                            System.out.print("\rDOWNLOADING FILE " + fileName + " " + bar + percentage + " % " );

                            // FILE CLOSE OK - Throw to exit
                        } else if (split.length == 3 && split[0].equals("FILE") && split[1].equals(fileName) && split[2].equals("CLOSE_OK")) {
                            retryCount = MAX_RETRIES;
                            throw new IOException("FILE CLOSE OK ");
                        } else System.out.println("\nERROR: parsing GOT -- > " + message + " ON PORT: " + newPort);

                        retryCount ++;

                        // RETRIES EXCEEDED - Throw to exit
                    }  throw new IOException("ERROR: parsing Response after 10 retries");

                    // FILE NOT FOUND - Throw to exit
                } else if (split.length == 2 && split[0].equals("ERR") && split[1].equals("NOT_FOUND") ) {
                    socket.close();
                    retryCount = MAX_RETRIES;
                    throw new IOException("FILE NOT FOUND ");
                }

                // CATCH INITIAL parsing and IO errors - check for EXIT
            } catch (IOException e) {
                System.out.println(e.getMessage() + " " + retryCount + "/" + MAX_RETRIES);
                retryCount++;
                if (retryCount >= MAX_RETRIES) {
                    System.err.println(e.getMessage() + " EXITING ......");
                    System.exit(1);
                }
            }
        }
    }
}