import java.io.File;
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
        byte[] receiveData = new byte[2048];


        while (true) {
            try {   socket = new DatagramSocket(port);
                    socket.setSoTimeout(10000);

                    // Reserve request Split and parse
                    DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                    socket.receive(receivePacket);
                    String message = new String(receivePacket.getData(), 0, receivePacket.getLength());
                    String response = "ERR NOT_PARSEABLE";
                    String[] parts = message.split(" ");

                    // COMMAND OK
                    if (parts.length == 2 && parts[0].equals("DOWNLOAD")) {
                        String fileName = parts[1];

                        File root = new File(System.getProperty("user.dir"));
                        File target = new File(root, fileName);
                        System.out.println("Downloading " + fileName + " to " + target.getAbsolutePath() + "  " + target.exists());

                        // FILE OK
                        if (target.exists() && target.isFile()) {

                            long fileSize = target.length();
                            int freePort = portFinderUDPPort();

                            // PORT OK
                            if (freePort != -1) {
                                response = "OK " + fileName + " SIZE " + fileSize + " PORT " + freePort;
                                Thread thread = new Thread(new FileMoverTask(fileName, freePort));
                                thread.start();

                            } else response = "ERR NO_FREE_PORT";
                        } else response = "ERR NOT_FOUND";
                    }

                    // Tiny delay then send a packet this is so the thread has time to start
                    Thread.sleep(5);
                    DatagramPacket sendPacket = new DatagramPacket(response.getBytes(), response.length(), receivePacket.getAddress(), receivePacket.getPort());
                    socket.send(sendPacket);
                    socket.close();

            } catch (IOException e) {
                System.out.println("ERROR: " + e.getMessage());
                if (socket != null) socket.close();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
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
        private int port , retryCount = 0;
        private String fileName , ID ;
        private byte[] receiveData = new byte[2048];
        private DatagramSocket newSocket = null;

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
                newSocket = new DatagramSocket(port);
                newSocket.setSoTimeout(10000);
                String message, response;

                boolean test = false;

                // RECEIVE data request and REPLY
                while (true) {
                    DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                    newSocket.receive(receivePacket);
                    message = new String(receivePacket.getData(), 0, receivePacket.getLength()).trim();
                    String[] split = message.split(" ");

                    // IF OK grab the data block
                    if (split.length == 7 && split[0].equals("FILE") && split[1].equals(fileName) && split[2].equals("GET") && split[3].equals("START") && split[5].equals("END")) {
                        int start = Integer.parseInt(split[4]);
                        int end = Integer.parseInt(split[6]);
                        retryCount = 0;

                        if (start > 600000 && test == false) {
                            start = 600001;
                            test = true;
                        }

                        //  get the file content and formulate response string else re try
                        response = "FILE " + fileName + " OK START " + start + " END " + end + " DATA <encoded_data> ";



                    // IF CLOSE OR NOT RECOGNISED
                    } else if(split.length == 3 && split[0].equals("FILE") && split[1].equals(fileName) && split[2].equals("CLOSE")){
                        response = "FILE " + fileName + " CLOSE_OK";
                    } else {
                        response = "ERR INVALID_COMMAND";
                        retryCount++;
                    }

                    // SEND PACKET - CHECK EXIT
                    DatagramPacket sendPacket = new DatagramPacket(response.getBytes(), response.length(), receivePacket.getAddress(), receivePacket.getPort());
                    newSocket.send(sendPacket);
                    if (response.contains("CLOSE_OK") || retryCount > 10) break;
                }

            } catch (IOException e) { System.out.println("ERROR: " + ID + e.getMessage()); }
            System.out.println("LOG: " + ID + " CLOSED" );
        }
    }
}
