import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;

// THIS WILL DOWNLOAD TO Client_output2

public class Client2 {
    // Max number of retries before closing this includes timeout retries
    private final int MAX_RETRIES = 5;
    // The starting timeout delay
    private final int INITIAL_RETRY_DELAY_MILLIS = 1000;
    // The maximum time out aloud
    private final int MAX_TIMEOUT = 10000;

    // This changes how it checks for the next block
    // True = it rechecks file size before requesting the next part (SLOWER)
    // False = It just assumes it all went fine and moves up 1000 bytes
    private boolean safer = false;

    /**
     * Main thread used to start a client
     * @param args hostname, Port number, Filename
     */
    public static void main(String[] args) {
        new Client2().runClient(args);
    }

    /**
     * The Main client loop keeps checking and retrying a few times for a download
     * @param args hostname, Port number, Filename
     */
    public void runClient(String[] args) {

        // Check args length
        if(args.length != 3) {
            System.out.println("Usage: java Client <host> <port> <file>");
            System.out.println("Inputs args should = 3");
            System.exit(1);
        }
        String host = args[0];

        // Check and set up socket
        DatagramSocket socket = null;
        int port = 51234; //default
        try {
            port = Integer.parseInt(args[1]);
            socket = new DatagramSocket();
        } catch (Exception e) {
            System.out.println("Usage: java Client <host> <port> <file>");
            System.out.println("Invalid port number: " + args[1]);
            System.exit(1);
        }

        // Check the file name
        if(args[2].isEmpty()) {
            System.out.println("Usage: java Client <host> <port> <file>");
            System.out.println("Invalid or empty file name: " + args[2]);
            System.exit(1);
        }

        // Set up variables
        String fileName = args[2];
        int retryCount = 0, socketTimeout;
        byte[] buffer = new byte[2048];


        // Retry loop for initializing
        while(true) {

            // TRY and make socket and parse response
            try {
                System.out.println("Trying to connect to " + host + ":" + port + " File : " + fileName);
                socket.setSoTimeout(INITIAL_RETRY_DELAY_MILLIS);
                socketTimeout = INITIAL_RETRY_DELAY_MILLIS;
                InetAddress serverAddress = InetAddress.getByName(host);

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

                    // Declare as much as we can before going on to the main loops helps performance
                    long start = 0 , end = 999, size = Long.parseLong(split[3]);
                    int  newPort = Integer.parseInt(split[5]);
                    boolean validPacket = true;
                    double percentage;
                    StringBuilder bar;
                    System.out.println("File transfer "+ fileName + " started on port " + newPort);
                    File root = new File(System.getProperty("user.dir"),"Client_output2");
                    Path filePath = root.toPath().resolve(fileName);
                    File partialFile = new File(filePath.toString());
                    Base64.Decoder decoder = Base64.getDecoder();
                    String encodedData;
                    String requestPart = "FILE " + fileName + " GET START " + start + " END " + end;

                    // RESUME function - If the file exists in part, continue from that part
                    if (partialFile.exists()) {
                        start = partialFile.length();
                        if (start >= size) {
                            requestPart = "FILE " + fileName + " CLOSE";
                        } else {
                            end = Math.min(start + 1000, size);
                            System.out.println("FILE Download resuming from byte " + start);
                            requestPart = "FILE " + fileName + " GET START " + start + " END " + end;
                        }
                    }

                    // File output stream set to true, so we are not holding the data all the time
                    FileOutputStream outputStream = new FileOutputStream(partialFile, true);

                    // START DOWNLOAD LOOP - send data request
                    retryCount = 0;
                    while (retryCount <= MAX_RETRIES) {
                        try{
                            if(validPacket) socket.send(new DatagramPacket(requestPart.getBytes(), requestPart.length(), serverAddress, newPort));
                            socket.setSoTimeout(socketTimeout);
                            socket.receive(receivePacket);
                            message = new String(receivePacket.getData(), 0, receivePacket.getLength()).trim();
                            split = message.split(" ");

                            // CHECK DATA
                            if (split.length >= 8 && split[0].equals("FILE") && split[1].equals(fileName) && split[2].equals("OK")) {

                                // CHECK Bit positions and reconstruct data
                                if (start == Long.parseLong(split[4]) && end == Long.parseLong(split[6])) {

                                    // Set valid packet to true and DECODE data
                                    validPacket = true;
                                    encodedData = String.join(" ", Arrays.copyOfRange(message.split(" ", -1), 8, split.length));
                                    outputStream.write(decoder.decode(encodedData));

                                    // Check for end condiction
                                    if (end == size) {
                                        requestPart = "FILE " + fileName + " CLOSE";
                                        System.out.print("\nDOWNLOAD " + fileName + " COMPLETE");
                                        outputStream.close();
                                        continue;
                                    }

                                    // Increment to next chunk and reduce timeout
                                    retryCount = 0;
                                    if (socketTimeout > INITIAL_RETRY_DELAY_MILLIS) socketTimeout -= 10;
                                    else socketTimeout = INITIAL_RETRY_DELAY_MILLIS;

                                    // Used to select between the safer but slower mode
                                    if(safer){
                                        start = partialFile.length();
                                        end = Math.min(start + 1000, size);
                                    }else{
                                        start = end;
                                        end = Math.min(end + 1000, size);
                                    }

                                    // Prep next message
                                    requestPart = "FILE " + fileName + " GET START " + start + " END " + end;

                                    // Message understood BUT wrong part listen for next packet
                                } else {
                                    System.err.println("\rLog out of sync -> " + split[4] + " & " + split[6] + " Expecting " + start + " & " + end);
                                    validPacket = false;
                                    continue;
                                }

                                // Little percentage print out
                                percentage = (double) end / size * 100;
                                percentage = Math.floor(percentage * 100) / 100;
                                bar = new StringBuilder("[");
                                int filledBars = (int) (percentage / 100 * 30);
                                for (int i = 0; i < 30; i++) {
                                    if (i < filledBars) {
                                        bar.append("█");
                                    } else {
                                        bar.append(" ");
                                    }
                                }
                                bar.append("] ");
                                System.out.print("\rDOWNLOADING FILE " + fileName + " " + bar + percentage + " % at " + end + " Timeout: " + socketTimeout);

                                // FILE CLOSE OK - Throw to exit
                            } else if (split.length == 3 && split[0].equals("FILE") && split[1].equals(fileName) && split[2].equals("CLOSE_OK")) {
                                retryCount = MAX_RETRIES;
                                throw new IOException("FILE CLOSE OK ");
                            }

                            retryCount++;

                            // TIMEOUT Extend the timeout time by one second
                        } catch (SocketTimeoutException e) {
                            System.err.println("\rSocket timed out: " + socketTimeout + " time extended ");
                            retryCount++;
                            validPacket = true;
                            if (socketTimeout < MAX_TIMEOUT) socketTimeout += 1000;
                        }
                    }

                    // Reply isn't understood retry from the start
                    System.out.println("\nDownloading file " + fileName + " Restarting Connection");
                    retryCount = 0;

                    // ERR file isn't found, just exit nothing to do
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