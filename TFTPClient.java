import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;

import packets.ACKPacket;
import packets.DataPacket;
import packets.RequestPacket;

/*
 * Class for the TFTP Client
 */
public class TFTPClient { 
    // hash for storing the image packets in order
    private static HashMap<Integer, ByteBuffer> imageBlockHash = new HashMap<>();
    // fields for connection args
    protected static InetAddress address;
    protected static int port;
    // global for shared key
    protected static long key;
    public static void main(String[] args) throws IOException, InterruptedException {
        // command line args: 0 = windowsize, 1 = drop packets

        // boolean for if the user is dropping packets
        boolean dropPackets =false;
        // string form of dropPackets (kind of redundant, i know)
        String dP = "f";
        
        port = 26925;
        
        // if we have more than argument, then user
        // wants to drop packets
        if(args.length > 1) {
           dP = "t";
           dropPackets = true;
        }

        // command line arg for getting window size 
        int windowSize = Integer.parseInt(args[0]);

        
        // create Datagram socket
        DatagramSocket socket = new DatagramSocket();
        

        // send request
        byte[] bytes = new byte[6];
        ByteBuffer buffer = ByteBuffer.wrap(bytes);

        InetAddress address = InetAddress.getByName("localhost");

        // Generate a random client num of 6 digits and send to server
        long randomClientNum = (int) (Math.random() * (999999 - 100000)) + 100000;
        ByteBuffer keyResponse = ByteBuffer.wrap(EncodingHelper.parseLongtoByteArr(randomClientNum));
        DatagramPacket packet = new DatagramPacket(keyResponse.array(), keyResponse.array().length, address, 26925);
        
        // start throughput measurement when initial connection is made
        long startTime = System.nanoTime();
        
        socket.send(packet);

        // receive response key from server and parse
        packet = new DatagramPacket(buffer.array(), buffer.array().length);
        socket.receive(packet);
        byte[] tempArr = packet.getData();
        long randomServerNum = EncodingHelper.parseKeyPacket(tempArr);

        // XOR server key with client key for shared key
        key = randomClientNum ^ randomServerNum;

        // send TFTP RRQ request for an image URL
        RequestPacket initialRRQ = new RequestPacket(1, "https://upload.wikimedia.org/wikipedia/en/e/ed/5_Seconds_of_Summer_-_5SOS5.png","octet",windowSize, dP);
        packet = new DatagramPacket(EncodingHelper.performXOR(key,initialRRQ.getPacket().array()), initialRRQ.getPacket().array().length, address, port);
        socket.send(packet);
        
        // variable to track what data packet block number a server expects
        int expectedBlockNum = 1;

        // variable to track packets in the sliding window
        ArrayList<DataPacket> slidingWindow = new ArrayList<>(); 

        // set the socket timeout to 3 seconds
        socket.setSoTimeout(3000);

        // receive the first window of packets from the server
        slidingWindow = receiveWindow(socket, expectedBlockNum, slidingWindow, 0, windowSize);
        
        // var used for error-checking received packets (checks ordering)
        int lastReceivedPacketBlockNum = 0;
        
        // While slidingWindow has packets to ACK
        while(slidingWindow.size() > 0) {

            // var for if we are dropping the current packet 
            boolean dropPacket = false;
            if(dropPackets) {
                // calc 1% drop for each packet that comes through
                if((int)((Math.random()* 100) + 1) <= 1) {
                    dropPacket = true;
                }
            }
            
            // if the first packet in the window is what expect and we're not simulating a drop,
            // put the packet into the imageBlockHash
            if(slidingWindow.get(0).getBlockNum() == expectedBlockNum && !dropPacket) { //&& !dropPacket
                System.out.println("EQUALITY: " + expectedBlockNum);
                imageBlockHash.put(slidingWindow.get(0).getBlockNum(), slidingWindow.get(0).getData());

                // if we are checking the last packet in the window,
                // send a cumulative ACK to the server
                if(slidingWindow.size() == 1) {
                    ACKPacket ack = new ACKPacket(4,slidingWindow.get(0).getBlockNum());
                    packet = new DatagramPacket(EncodingHelper.performXOR(key, ack.getPacket().array()), ack.getPacket().array().length, address, port);
                    socket.send(packet);
    
                    // imageBlockHash.put(slidingWindow.get(0).getBlockNum(), slidingWindow.get(0).getData());
                    
                    DataPacket lastReceivedPacket = slidingWindow.get(0);
                    slidingWindow.remove(0);
                    expectedBlockNum++;
                    
                    // if the last packet of the window has datasize < 512 -- the server has no more packets to send
                    // otherwise receive
                    if( lastReceivedPacket.getData().array().length == 512) {
                        receiveWindow(socket, expectedBlockNum, slidingWindow, expectedBlockNum - 1, windowSize);
                    } else {
                        lastReceivedPacketBlockNum = lastReceivedPacket.getBlockNum();
                    }
                    
                } else{
                    // remove current packet, so we can move onto next packet
                    slidingWindow.remove(0);
                    //increment expectedBlockNum
                    expectedBlockNum++;
                }
                
            } else {
                    //re-send window
                    if(dropPacket){
                        System.out.println("Simulating dropped packet for " + expectedBlockNum);
                    } else {
                        System.out.println("Dropped packet " + expectedBlockNum);
                        dropPacket = true;
                    }
                             
                    // clear the window before receiving dropped window from the server
                    slidingWindow.clear();
                    slidingWindow = receiveWindow(socket, expectedBlockNum, slidingWindow, expectedBlockNum-1, windowSize);

                    // reset expectedBlockNum to the beginning of the resent window
                    expectedBlockNum = slidingWindow.get(0).getBlockNum();
                                
                }
        }
        
            // since sending is done, we calculate the duration
            long duration = System.nanoTime() - startTime;

            // put together image on client side
            ByteBuffer imageByteBuffer = ByteBuffer.allocate(1000000);
            for(int x = 1; x <= lastReceivedPacketBlockNum; x++) {
                imageByteBuffer.put(imageBlockHash.get(x).array());
            }
            OutputStream os = new FileOutputStream("image.jpg");
            byte[] finalImage = Arrays.copyOfRange(imageByteBuffer.array(), 0, DataPacket.getLastIndexOfData(imageByteBuffer.array()));

            // calculate and display throughput
            double throughput = (((finalImage.length*8)/Math.pow(10,6)))/(duration/1000000000.00);
            System.out.println(throughput); 

            os.write(finalImage);

            ImageCanvas.displayImage("image.jpg");
        
            socket.close();


    }


    // Method to receive a window of packets from the server
    public static ArrayList<DataPacket> receiveWindow(DatagramSocket socket, int expectedBlockNum, ArrayList<DataPacket> slidingWindow,
     int startingIndex, int slidingWindowSize) throws IOException {
        byte[] bytes = new byte[1024];
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        DatagramPacket packet = new DatagramPacket(buffer.array(), buffer.remaining());
        DataPacket receivedPacket;
        for(int i = startingIndex; i < expectedBlockNum + slidingWindowSize - 1; i++) {
            
            bytes = new byte[1024];
            buffer = ByteBuffer.wrap(bytes);
            packet = new DatagramPacket(buffer.array(), buffer.array().length, address, port);
            // try to receive packet
            try{
                socket.receive(packet);
                receivedPacket = new DataPacket(ByteBuffer.wrap(EncodingHelper.performXOR(key, packet.getData())));
                
                // discard packets that have already been received
                if(!imageBlockHash.containsKey(receivedPacket.getBlockNum())) {
                    slidingWindow.add(receivedPacket);
                }
                // make sure the server still has packets to send
                if(receivedPacket.getData().array().length != 512) {
                    i = expectedBlockNum + slidingWindowSize;
                }
            } catch (SocketTimeoutException e) {
                System.out.println("TIMEOUT");
                // if we no longer receive from server, skip to ACK
                i = expectedBlockNum + slidingWindowSize;
            }
            
        
        }

        // return the new window of received packets
        return slidingWindow;

    }


}