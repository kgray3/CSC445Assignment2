import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;

// TO-DO: Add timeouts
public class TFTPClient { 

    public static void main(String[] args) throws IOException, InterruptedException {
        // command line arg 0 = windowsize, 1 = drop packets

        String dropPackets = "t";
        int port = 26925;
        // if(args.length > 1) {
        //    dropPackets = "t";
        // }

        int windowSize = 4;//Integer.parseInt(args[0]);

        HashMap<Integer, ByteBuffer> imageBlockHash = new HashMap<>();
        long key;
        // create Datagram socket
        DatagramSocket socket = new DatagramSocket();
        socket.setSoTimeout(10000);

        // send request
        byte[] bytes = new byte[6];
        ByteBuffer buffer = ByteBuffer.wrap(bytes);

        InetAddress address = InetAddress.getByName("localhost");

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
        TFTPPacket initialRRQ = new TFTPPacket(1, "https://upload.wikimedia.org/wikipedia/en/e/ed/5_Seconds_of_Summer_-_5SOS5.png","octet",windowSize, dropPackets);
        packet = new DatagramPacket(EncodingHelper.performXOR(key,initialRRQ.getPacket().array()), initialRRQ.getPacket().array().length, address, port);
        socket.send(packet);

        bytes = new byte[1024];
        buffer = ByteBuffer.wrap(bytes);
        packet = new DatagramPacket(buffer.array(), buffer.remaining());
        socket.receive(packet);
        
        boolean firstPass = true;
        int expectedBlockNum = 1;
        ArrayList<TFTPPacket> slidingWindow = new ArrayList<>();
        int slidingWindowCounter = 1;
        TFTPPacket receivedPacket = new TFTPPacket(ByteBuffer.wrap(EncodingHelper.performXOR(key,packet.getData())));
        if(receivedPacket.getOpCode() == 3) {
            while(receivedPacket.getData().array().length == 512) {
                if((slidingWindowCounter < expectedBlockNum + initialRRQ.getSlidingWindowSize() - 1)) {

                    //System.out.println("We back in the for loop babyyy BlockNum =" + receivedPacket.getBlockNum() );
                    slidingWindow.add(receivedPacket);

                    bytes = new byte[1024];
                    buffer = ByteBuffer.wrap(bytes);
                    packet = new DatagramPacket(buffer.array(), buffer.array().length, address, port);
            
                    try{
                        socket.receive(packet);
                        receivedPacket = new TFTPPacket(ByteBuffer.wrap(EncodingHelper.performXOR(key, packet.getData())));
                    
                        slidingWindowCounter++;
                    } catch(SocketTimeoutException e) {
                        System.out.println("We timed out in the for loop bby");
                        // if we no longer receive from server, skip to ACK
                        slidingWindowCounter = expectedBlockNum + initialRRQ.getSlidingWindowSize();
                    }
                     
                }   else {
                        firstPass = false;
                        slidingWindowCounter = 1000000000;
                        //add last packet of the window
                        slidingWindow.add(receivedPacket);
                        //expectedBlockNum = slidingWindow.get(0).getBlockNum();
                        // edge case for server not receiving ack
                        if(receivedPacket.getBlockNum() < expectedBlockNum) {

                            System.out.println("[Error] Received: " + receivedPacket.getBlockNum() + " Expected: " + expectedBlockNum);
                            slidingWindow.clear();
                            expectedBlockNum = receivedPacket.getBlockNum();
                            slidingWindowCounter = expectedBlockNum;
    
                        }
                        else if(slidingWindow.get(0).getBlockNum() == expectedBlockNum) {

                            System.out.println("EQUALITY: " + expectedBlockNum);
                            TFTPPacket ack = new TFTPPacket(4,slidingWindow.get(0).getBlockNum());
                            packet = new DatagramPacket(EncodingHelper.performXOR(key, ack.getPacket().array()), ack.getPacket().array().length, address, port);
                            socket.send(packet);

                            imageBlockHash.put(slidingWindow.get(0).getBlockNum(), slidingWindow.get(0).getData());
                            slidingWindow.remove(0);

                            expectedBlockNum++;

                            bytes = new byte[1024];
                            buffer = ByteBuffer.wrap(bytes);
                            packet = new DatagramPacket(buffer.array(), buffer.array().length, address, port);
                

                            
                            socket.receive(packet);
                            receivedPacket = new TFTPPacket(ByteBuffer.wrap(EncodingHelper.performXOR(key, packet.getData())));
                    }  else {
                        System.out.println("UH OH, our received block number is not right -> " + "Expected: " + expectedBlockNum);
                        System.out.println("Received: " + slidingWindow.get(0).getBlockNum());
                        slidingWindow.clear();
                        slidingWindowCounter = expectedBlockNum;
                    }
                
                }


            }
           
            slidingWindow.add(receivedPacket);
        
            while(slidingWindow.size() > 0) {
                TFTPPacket ack = new TFTPPacket(4,slidingWindow.get(0).getBlockNum());
                packet = new DatagramPacket(EncodingHelper.performXOR(key, ack.getPacket().array()), ack.getPacket().array().length, address, port);
                socket.send(packet);

                imageBlockHash.put(slidingWindow.get(0).getBlockNum(), slidingWindow.get(0).getData());
                slidingWindow.remove(0);
            }

            long duration = System.nanoTime() - startTime;

            ByteBuffer imageByteBuffer = ByteBuffer.allocate(1000000);
            for(int x = 1; x <= receivedPacket.getBlockNum(); x++) {
                imageByteBuffer.put(imageBlockHash.get(x).array());
            }


            OutputStream os = new FileOutputStream("image.jpg");

            byte[] finalImage = Arrays.copyOfRange(imageByteBuffer.array(), 0, TFTPPacket.getLastIndexOfData(imageByteBuffer.array()));
            
            double throughput = (((finalImage.length*8)/Math.pow(2, 20)))/(duration/1000000000.00);

            System.out.println(throughput);

            os.write(finalImage);




        }
        // display response
        
        

        socket.close();


    }
}