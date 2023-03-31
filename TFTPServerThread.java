import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;

import packets.ACKPacket;
import packets.DataPacket;
import packets.RequestPacket;

/*
 * Class for the TFTPServer
 */
public class TFTPServerThread extends Thread {
    // globals for socket and connection types
    protected static DatagramSocket socket = null;
    protected BufferedReader in = null;
    protected boolean running = true;
    protected boolean initialConnection = true;
    protected boolean initialTFTPConnection = false;

    public TFTPServerThread() throws IOException {
        this("TFTPServerThread");
    }

    public TFTPServerThread(String name) throws IOException {
        super(name);
        socket = new DatagramSocket(26925);
    }

    String urlString = "";
    // variable to cache the previous image retrieved from the server
    ByteBuffer previousImage;
    byte[] bytes;
    ByteBuffer buffer;
    static DatagramPacket packet;
    // shared key for XOR encoding
    static long key;

    public void run() {
        while(running) {
            
            try{
                System.out.println(key);

                // if this is the initial connection, receive the client key and send the server key
                // to the client
                if(initialConnection) {
                    socket.setSoTimeout(60000);
                    // receive client key packet and parse
                    bytes = new byte[6];
                    buffer = ByteBuffer.wrap(bytes);
                    packet = new DatagramPacket(buffer.array(), buffer.remaining());
                    socket.receive(packet);
                    byte[] tempArr = packet.getData();
                    long randomClientNum = EncodingHelper.parseKeyPacket(tempArr);
                    // generate a 6 digit key to send to client
                    long randomServerNum = (int) (Math.random() * (999999 - 100000)) + 100000;

                    // get shared key by XOR of client key and server key
                    key = randomClientNum ^ randomServerNum;
                    ByteBuffer keyResponse = ByteBuffer.wrap(EncodingHelper.parseLongtoByteArr(randomServerNum));

                    // send server key to client
                    InetAddress address = packet.getAddress();
                    int port = packet.getPort();
                    packet = new DatagramPacket(keyResponse.array(), keyResponse.array().length, address, port);
                    socket.send(packet);
                    initialConnection = false;
                    initialTFTPConnection = true;
                     
                } else if(initialTFTPConnection){
                    bytes = new byte[1024];
                    buffer = ByteBuffer.wrap(bytes);

                    // after the key exchange, receive RRQ packet from client
                    packet = new DatagramPacket(buffer.array(), buffer.remaining());
                    socket.receive(packet);

                    initialTFTPConnection = false;

                } else {
                    // set socket timeout to 1 second (faster than client side)
                    socket.setSoTimeout(1000);

                    // parse request packet
                    byte[] receivedPacketData = Arrays.copyOfRange(packet.getData(), 0, DataPacket.getLastIndexOfData(packet.getData()));
                    RequestPacket receivedPacket = new RequestPacket(ByteBuffer.wrap(EncodingHelper.performXOR(key,receivedPacketData)));
                    
                    // get windowSize from request packet
                    int windowSize = receivedPacket.getSlidingWindowSize();

                    byte[] image;
                    // checks if requested image is cached
                    if(urlString.equalsIgnoreCase(receivedPacket.getFileName())) {
                        image = previousImage.array();
                    } else{
                        // use HTTP to get image
                        urlString = receivedPacket.getFileName();
                        image = getImageBytesFromURL(urlString);
                    }
                    
                    previousImage = ByteBuffer.wrap(image);

                    // ArrayList to store all of the data packets that need to be sent
                    ArrayList<DataPacket> imageFrames = getImageArrayList(image);

                    // ArrayList to track the current window
                    ArrayList<DataPacket> slidingWindow = new ArrayList<>();

                    //add the initial window and send to client :)
                    sendWindow(slidingWindow, imageFrames, windowSize,0);

                    // while there are still packets in the sliding window
                    while(slidingWindow.size() > 0) {

                        try
                        {
                            // try to receive an ACK from the client and parse
                            socket.receive(packet);
                            ACKPacket clientResponse = new ACKPacket(ByteBuffer.wrap(EncodingHelper.performXOR(key,packet.getData())));

                            // If the cumulative ACK's block num matches the last packet's block num in our sliding window
                            if(clientResponse.getBlockNum() == slidingWindow.get(slidingWindow.size()-1).getBlockNum() ) {
                            
                            // check if there are anymore frames to add, if so, add to sliding window and send to client
                                if(slidingWindow.get(slidingWindow.size()-1).getBlockNum() != imageFrames.get(imageFrames.size()-1).getBlockNum()) {
                                    
                                    int startingIndex = slidingWindow.get(slidingWindow.size()-1).getBlockNum();
                                    slidingWindow.clear();
                                    slidingWindow = sendWindow(slidingWindow, imageFrames, windowSize,startingIndex);

                                }else{
                                    // no more packets to send, clear slidingWindow
                                    slidingWindow.clear();
                                }
                                
                            }
                                
                        } catch(SocketTimeoutException e) {
                            // if a timeout occurs, resend the entire window again
                            System.out.println("Oh no, we timed out for window ending with " + slidingWindow.get(slidingWindow.size()-1).getBlockNum());

                            int startingIndex = slidingWindow.get(0).getBlockNum()-1;
                            slidingWindow.clear();
                            slidingWindow = sendWindow(slidingWindow, imageFrames, windowSize, startingIndex);
                        }
                    }
                // reset connection after image is sent
                initialConnection = true;
            }


            } catch (IOException e) {
                e.printStackTrace();
                running = false;
            }
        }
        socket.close();
    }

    // method to send the entire window of packets to the client, if window surpasses number of packets left to send,
    // only the remaining packets are sent.
    public static ArrayList<DataPacket> sendWindow(ArrayList<DataPacket> slidingWindow, ArrayList<DataPacket> imageFrames, int windowSize,
    int startingIndex) throws IOException {
        for(int i = startingIndex; i < startingIndex + windowSize; i++) {
            if(i < imageFrames.size()) {
                    ByteBuffer buffer;
                    slidingWindow.add(imageFrames.get(i));

                    buffer = imageFrames.get(i).getPacket();

                    System.out.println(imageFrames.get(i).getBlockNum());
                    InetAddress address = packet.getAddress();
                    int port = packet.getPort();
                    packet = new DatagramPacket(EncodingHelper.performXOR(key,buffer.array()), buffer.array().length, address, port);

                    socket.send(packet);
                    
                }
        }
        return slidingWindow;

    }

    // fetches image at given URL using HTTP and returns the byte array of the image
    public static byte[] getImageBytesFromURL(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");

        InputStream inputStream = url.openStream();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] bytes = new byte[4096];
        int length;
        while ((length = inputStream.read(bytes)) > 0) {
            baos.write(bytes, 0, length);
        }

        return baos.toByteArray();

    }

    // take image byte array returned from HTTP and put it into TFTP Data Packets
    public static ArrayList<DataPacket> getImageArrayList(byte[] image) {
        ArrayList<DataPacket> a = new ArrayList<>();
        int numOfPackets = (int) Math.ceil(image.length / 512.0);
        for (int i = 0; i < numOfPackets; i++) {
            int startingIndex = i * 512;

            byte[] currentDataByte = Arrays.copyOfRange(image, startingIndex, startingIndex + 512);

            DataPacket response = new DataPacket(i + 1, ByteBuffer.wrap(currentDataByte));

            a.add(response);

        }
        return a;
    }




}
