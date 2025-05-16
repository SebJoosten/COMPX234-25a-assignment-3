import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.*;
import java.util.Base64;

public class Server {

    public static void main(String[] args) {
        Server server = new Server();
        server.runServer(args);
    }

    public void runServer(String[] args) {
        DatagramSocket socket = null;
        int port = 51234;
        byte[] receiveData = new byte[2048];
        File root = new File(System.getProperty("user.dir"));

        // MAIN listening loop
        while (true) {
            try {   socket = new DatagramSocket(port);
                socket.setSoTimeout(10000);

                // Reserve request Split and parse
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                socket.receive(receivePacket);
                String message = new String(receivePacket.getData(), 0, receivePacket.getLength());
                String response = "ERR NOT_PARSEABLE";
                String[] parts = message.split(" ");
                System.out.println("LOG: server received: " + message);

                // COMMAND OK
                if (parts.length == 2 && parts[0].equals("DOWNLOAD")) {
                    response = "ERR NOT_FOUND";
                    String fileName = parts[1];
                    File target = new File(root, fileName);

                    // FILE OK
                    if (target.exists() && target.isFile()) {
                        response = "ERR NO_FREE_PORT";

                        // Get free port between 50001 & 51000 and start thread
                        for (int freePort = 50001; freePort <= 51000; freePort++) {
                            try (DatagramSocket socketNew = new DatagramSocket(freePort)) {
                                response = "OK " + fileName + " SIZE: " + target.length() + " PORT: " + freePort;
                                Thread thread = new Thread(new FileMoverTask( freePort, target));
                                thread.start();
                                break;
                            } catch (IOException ignored){}
                        }

                        // FILE NOT FOUND
                    } else System.out.println("LOG: FILE NOT FOUND: " + message);

                    // UNKNOWN request
                } else System.out.println("LOG: Unknown request: " + message);

                // SEND response
                Thread.sleep(5);
                socket.send(new DatagramPacket(response.getBytes(), response.length(), receivePacket.getAddress(), receivePacket.getPort()));
                socket.close();

                // CATCH ALL - reset the loop and wait again
            } catch (IOException e) {
                System.out.println("ERROR: " + e.getMessage());
                if (socket != null) socket.close();
            } catch (InterruptedException e) {
                System.out.println("ERROR: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * File mover thread class used for spinning off a sending thread
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
        }

        @Override
        public void run() { System.out.println("LOG: " + ID + " STARTED" );

            // Declare as much as we can before going on to the main loops
            String filename = target.getName(), response = "ERR INVALID_COMMAND";
            byte[] buffer , receiveData = new byte[2048];
            Base64.Encoder encoder = Base64.getEncoder();
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            String[] split;

            // TRY set up port and catch for other file issues
            try (
                    RandomAccessFile openFile = new RandomAccessFile(target, "r");
                    DatagramSocket newSocket = new DatagramSocket(port)
            ) {
                newSocket.setSoTimeout(10000);
                while (true) {

                    newSocket.receive(receivePacket);
                    split = new String(receivePacket.getData(), 0, receivePacket.getLength()).trim().split(" ");

                    if (split.length == 7 && split[0].equals("FILE") && split[1].equals(filename) && split[2].equals("GET") && split[3].equals("START") && split[5].equals("END")) {

                        long start = Long.parseLong(split[4]);
                        long end = Long.parseLong(split[6]);
                        int length = (int)(end - start);

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

                    } else if (split.length == 3 && split[0].equals("FILE") && split[1].equals(filename) && split[2].equals("CLOSE")) {
                        response = "FILE " + filename + " CLOSE_OK";
                    } else {
                        response = "ERR INVALID_COMMAND";
                        retryCount++;
                    }

                    // SEND response
                    newSocket.send(new DatagramPacket(response.getBytes(), response.length(), receivePacket.getAddress(), receivePacket.getPort()));

                    // CHECK for close
                    if (response.contains("CLOSE_OK")) break;
                }

                // CATCH ALL
            } catch (IOException e) { System.out.println("ERROR: " + ID + e.getMessage()); }
            System.out.println("LOG: " + ID + " CLOSED" );
        }
    }
}
