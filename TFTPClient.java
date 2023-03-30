import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;

import packets.ACKPacket;
import packets.DataPacket;
import packets.RequestPacket;

// TO-DO: Add timeouts
public class TFTPClient { 
    private static HashMap<Integer, ByteBuffer> imageBlockHash = new HashMap<>();
    protected static InetAddress address;
    protected static int port;
    protected static long key;
    public static void main(String[] args) throws IOException, InterruptedException {
        // command line arg 0 = windowsize, 1 = drop packets
        boolean dropPackets =false;
        String dP = "f";
        port = 26925;
        
        if(args.length > 1) {
           dP = "t";
        }

        if(dP.equalsIgnoreCase("t")) {
            dropPackets = true;
        }

        int windowSize = Integer.parseInt(args[0]);

        
        // create Datagram socket
        DatagramSocket socket = new DatagramSocket();
        

        // send request
        byte[] bytes = new byte[6];
        ByteBuffer buffer = ByteBuffer.wrap(bytes);

        InetAddress address = InetAddress.getByName("pi.cs.oswego.edu");

        long randomClientNum = (int) (Math.random() * (999999 - 100000)) + 100000;
        ByteBuffer keyResponse = ByteBuffer.wrap(EncodingHelper.parseLongtoByteArr(randomClientNum));

        DatagramPacket packet = new DatagramPacket(keyResponse.array(), keyResponse.array().length, address, 26925);

        long startTime = System.nanoTime();
        socket.send(packet);

        packet = new DatagramPacket(buffer.array(), buffer.array().length);
        socket.receive(packet);

        byte[] tempArr = packet.getData();
        long randomServerNum = EncodingHelper.parseKeyPacket(tempArr);

        key = randomClientNum ^ randomServerNum;

        RequestPacket initialRRQ = new RequestPacket(1, "https://upload.wikimedia.org/wikipedia/en/e/ed/5_Seconds_of_Summer_-_5SOS5.png","octet",windowSize, dP);
        packet = new DatagramPacket(EncodingHelper.performXOR(key,initialRRQ.getPacket().array()), initialRRQ.getPacket().array().length, address, port);
        socket.send(packet);
        
        int expectedBlockNum = 1;
        ArrayList<DataPacket> slidingWindow = new ArrayList<>(); 

        socket.setSoTimeout(3000);
        slidingWindow = receiveWindow(socket, expectedBlockNum, slidingWindow, 0, windowSize);
        
        int lastReceivedPacketBlockNum = 0;
        while(slidingWindow.size() > 0) {
            boolean dropPacket = false;
            if(dropPackets) {
                // calc 1% drop for each packet that comes through
                if((int)((Math.random()* 100) + 1) <= 1) {
                    dropPacket = true;
                }
            }
            //oopsie server did not receive ACK
            if(slidingWindow.get(0).getBlockNum() < expectedBlockNum) {
                System.out.println("jinkies: " + slidingWindow.get(0).getBlockNum());
                ACKPacket ack = new ACKPacket(4,slidingWindow.get(0).getBlockNum() + windowSize - 1);
                packet = new DatagramPacket(EncodingHelper.performXOR(key, ack.getPacket().array()), ack.getPacket().array().length, address, port);
                socket.send(packet);

                slidingWindow.clear();
                slidingWindow = receiveWindow(socket, expectedBlockNum, slidingWindow, expectedBlockNum-1, windowSize);

            } else if(slidingWindow.get(0).getBlockNum() == expectedBlockNum && !dropPacket) { //&& !dropPacket
                System.out.println("EQUALITY: " + expectedBlockNum);
                imageBlockHash.put(slidingWindow.get(0).getBlockNum(), slidingWindow.get(0).getData());

                if(slidingWindow.size() == 1) {
                    ACKPacket ack = new ACKPacket(4,slidingWindow.get(0).getBlockNum());
                    packet = new DatagramPacket(EncodingHelper.performXOR(key, ack.getPacket().array()), ack.getPacket().array().length, address, port);
                    socket.send(packet);
                    //System.out.println("ACKED");
                    imageBlockHash.put(slidingWindow.get(0).getBlockNum(), slidingWindow.get(0).getData());
                    
                    DataPacket lastReceivedPacket = slidingWindow.get(0);
                    slidingWindow.remove(0);
                    expectedBlockNum++;
                    
                    if( lastReceivedPacket.getData().array().length == 512) {
                        receiveWindow(socket, expectedBlockNum, slidingWindow, expectedBlockNum - 1, windowSize);
                    } else {
                        lastReceivedPacketBlockNum = lastReceivedPacket.getBlockNum();
                    }
                    
                } else{
                    slidingWindow.remove(0);
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
                                
                    slidingWindow.clear();
                    slidingWindow = receiveWindow(socket, expectedBlockNum, slidingWindow, expectedBlockNum-1, windowSize);

                    expectedBlockNum = slidingWindow.get(0).getBlockNum();
                                
                }
        }
        

            long duration = System.nanoTime() - startTime;

            ByteBuffer imageByteBuffer = ByteBuffer.allocate(1000000);
            for(int x = 1; x <= lastReceivedPacketBlockNum; x++) {
                imageByteBuffer.put(imageBlockHash.get(x).array());
            }


            OutputStream os = new FileOutputStream("image.jpg");

            byte[] finalImage = Arrays.copyOfRange(imageByteBuffer.array(), 0, DataPacket.getLastIndexOfData(imageByteBuffer.array()));

            double throughput = (((finalImage.length*8)/Math.pow(10,6)))/(duration/1000000000.00);

            System.out.println(throughput); 

            os.write(finalImage);

            ImageCanvas.displayImage("image.jpg");
        
        

            socket.close();


    }



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
            try{
                socket.receive(packet);
                receivedPacket = new DataPacket(ByteBuffer.wrap(EncodingHelper.performXOR(key, packet.getData())));
                if(!imageBlockHash.containsKey(receivedPacket.getBlockNum())) {
                    slidingWindow.add(receivedPacket);
                }
                if(receivedPacket.getData().array().length != 512) {
                    i = expectedBlockNum + slidingWindowSize;
                }
            } catch (SocketTimeoutException e) {
                System.out.println("TIMEOUT");
                // if we no longer receive from server, skip to ACK
                i = expectedBlockNum + slidingWindowSize;
            }
            
        
        }
        return slidingWindow;

    }


}