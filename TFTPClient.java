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
    public static void main(String[] args) throws IOException, InterruptedException {
        // command line arg 0 = windowsize, 1 = drop packets
        boolean dropPackets =false;
        String dP = "t";
        int port = 26925;
        
        // if(args.length > 1) {
        //    dP = "t";
        // }

        if(dP.equalsIgnoreCase("t")) {
            dropPackets = true;
        }

        int windowSize = 50;//Integer.parseInt(args[0]);

        
        long key;
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
        // add slidingWindow thing here
        RequestPacket initialRRQ = new RequestPacket(1, "https://upload.wikimedia.org/wikipedia/en/e/ed/5_Seconds_of_Summer_-_5SOS5.png","octet",windowSize, dP);
        packet = new DatagramPacket(EncodingHelper.performXOR(key,initialRRQ.getPacket().array()), initialRRQ.getPacket().array().length, address, port);
        socket.send(packet);

        bytes = new byte[1024];
        buffer = ByteBuffer.wrap(bytes);
        packet = new DatagramPacket(buffer.array(), buffer.remaining());
        socket.receive(packet);
        
        int expectedBlockNum = 1;
        ArrayList<DataPacket> slidingWindow = new ArrayList<>();
        int slidingWindowCounter = 1;
        DataPacket receivedPacket = new DataPacket(ByteBuffer.wrap(EncodingHelper.performXOR(key,packet.getData())));
        boolean dropPacket = false;
        if(receivedPacket.getOpCode() == 3) {
            socket.setSoTimeout(3000);
            while(receivedPacket.getData().array().length == 512) {
                //boolean dropPacket = false;
                if((slidingWindowCounter < expectedBlockNum + initialRRQ.getSlidingWindowSize() - 1)) {

                    if(!dropPacket) {
                        slidingWindow.add(receivedPacket);
                        System.out.println("For loop (!dropPacket) -> BlockNum =" + receivedPacket.getBlockNum() );
                    }
                    

                    bytes = new byte[1024];
                    buffer = ByteBuffer.wrap(bytes);
                    packet = new DatagramPacket(buffer.array(), buffer.array().length, address, port);
            
                    try{
                        socket.receive(packet);
                        receivedPacket = new DataPacket(ByteBuffer.wrap(EncodingHelper.performXOR(key, packet.getData())));
                    
                        if(dropPacket) {
                            slidingWindow.add(receivedPacket);
                            System.out.println("For loop (dropPacket) -> BlockNum =" + receivedPacket.getBlockNum() );
                        }
                        slidingWindowCounter++;
                    } catch(SocketTimeoutException e) {
                        System.out.println("TIMEOUT");
                        // if we no longer receive from server, skip to ACK
                        slidingWindowCounter = expectedBlockNum + initialRRQ.getSlidingWindowSize();
                    }
                     
                }   else {
                        if(!dropPacket && receivedPacket.getData().array().length == 512){
                        slidingWindow.add(receivedPacket);
                        }
                        dropPacket = false;
                        if(dropPackets) {
                        // calc 1% drop for each packet that comes through
                            if((int)((Math.random()* 100) + 1) <= 1) {
                            dropPacket = true;
                            }
                        }
                        slidingWindowCounter = 1000000000;
                        //add last packet of the window

                        
                        //expectedBlockNum = slidingWindow.get(0).getBlockNum();
                        // edge case for server not receiving ack
                        if(receivedPacket.getBlockNum() < expectedBlockNum) {

                            System.out.println("[Error] Received: " + receivedPacket.getBlockNum() + " Expected: " + expectedBlockNum);
                            slidingWindow.clear();
                            expectedBlockNum = receivedPacket.getBlockNum();
                            slidingWindowCounter = expectedBlockNum;
    
                        }
                        else if(slidingWindow.get(0).getBlockNum() == expectedBlockNum && !dropPacket) {

                            System.out.println("EQUALITY: " + expectedBlockNum);
                            ACKPacket ack = new ACKPacket(4,slidingWindow.get(0).getBlockNum());
                            packet = new DatagramPacket(EncodingHelper.performXOR(key, ack.getPacket().array()), ack.getPacket().array().length, address, port);
                            socket.send(packet);

                            imageBlockHash.put(slidingWindow.get(0).getBlockNum(), slidingWindow.get(0).getData());
                            slidingWindow.remove(0);

                            expectedBlockNum++;

                            bytes = new byte[1024];
                            buffer = ByteBuffer.wrap(bytes);
                            packet = new DatagramPacket(buffer.array(), buffer.array().length, address, port);
                

                            
                            socket.receive(packet);
                            receivedPacket = new DataPacket(ByteBuffer.wrap(EncodingHelper.performXOR(key, packet.getData())));
                    }  else {
                        if(dropPacket){
                            System.out.println("Simulating dropped packet for " + expectedBlockNum);
                        } else {
                            System.out.println("Dropped packet " + expectedBlockNum);
                            dropPacket = true;
                        }
                        
                        slidingWindow.clear();
                        slidingWindowCounter = expectedBlockNum - 1;
                    }
                
                }


            }
           
            slidingWindow.add(receivedPacket);
        
            while(slidingWindow.size() > 0 || dropPacket) {
                if((slidingWindowCounter < expectedBlockNum + initialRRQ.getSlidingWindowSize())) {
                    

                    bytes = new byte[1024];
                    buffer = ByteBuffer.wrap(bytes);
                    packet = new DatagramPacket(buffer.array(), buffer.array().length, address, port);
            
                    try{
                        socket.receive(packet);
                        
                        receivedPacket = new DataPacket(ByteBuffer.wrap(EncodingHelper.performXOR(key, packet.getData())));
                        System.out.println("For loop (!dropPacket) -> BlockNum =" + receivedPacket.getBlockNum() );
                        slidingWindow.add(receivedPacket);

                        if(receivedPacket.getData().array().length != 512) {
                            slidingWindowCounter = expectedBlockNum + initialRRQ.getSlidingWindowSize();
                        } else {
                            slidingWindowCounter++;
                        }
                        
                    } catch(SocketTimeoutException e) {
                        System.out.println("TIMEOUT");
                        // if we no longer receive from server, skip to ACK
                        slidingWindowCounter = expectedBlockNum + initialRRQ.getSlidingWindowSize();
                    }

                } else {
                    if(!dropPacket && receivedPacket.getData().array().length == 512){
                        slidingWindow.add(receivedPacket);
                        }
                        dropPacket = false;
                        if(dropPackets) {
                        // calc 1% drop for each packet that comes through
                            if((int)((Math.random()* 100) + 1) <= 1) {
                                dropPacket = true;
                            }
                        }
                    slidingWindowCounter = 1000000000;
                    //add last packet of the window
                         
                    if(receivedPacket.getBlockNum() < expectedBlockNum) {

                        System.out.println("[Error] Received: " + receivedPacket.getBlockNum() + " Expected: " + expectedBlockNum);
                        slidingWindow.clear();
                        expectedBlockNum = receivedPacket.getBlockNum();
                        slidingWindowCounter = expectedBlockNum;

                    }
                    else if(slidingWindow.get(0).getBlockNum() == expectedBlockNum && !dropPacket) {
                        System.out.println("EQUALITY: " + expectedBlockNum);
                        ACKPacket ack = new ACKPacket(4,slidingWindow.get(0).getBlockNum());
                        packet = new DatagramPacket(EncodingHelper.performXOR(key, ack.getPacket().array()), ack.getPacket().array().length, address, port);
                        socket.send(packet);

                        imageBlockHash.put(slidingWindow.get(0).getBlockNum(), slidingWindow.get(0).getData());
                        
                        slidingWindow.remove(0);

                        expectedBlockNum++;
                    } else {
                        if(dropPacket){
                                System.out.println("Simulating dropped packet for " + expectedBlockNum);
                            } else {
                                System.out.println("Dropped packet " + expectedBlockNum);
                                dropPacket = true;
                            }
                        slidingWindow.clear();
                        slidingWindowCounter = expectedBlockNum;
                    }

            }
        }

            long duration = System.nanoTime() - startTime;

            ByteBuffer imageByteBuffer = ByteBuffer.allocate(1000000);
            for(int x = 1; x <= receivedPacket.getBlockNum(); x++) {
                imageByteBuffer.put(imageBlockHash.get(x).array());
            }


            OutputStream os = new FileOutputStream("image.jpg");

            byte[] finalImage = Arrays.copyOfRange(imageByteBuffer.array(), 0, DataPacket.getLastIndexOfData(imageByteBuffer.array()));

            double throughput = (((finalImage.length*8)/Math.pow(10,6)))/(duration/1000000000.00);

            System.out.println(throughput); 

            os.write(finalImage);

            //ImageCanvas.displayImage("image.jpg");




        }
        // display response
        
        

        socket.close();


    }




}