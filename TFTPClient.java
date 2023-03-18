import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;

// TO-DO: Add timeouts and ability to drop packets
public class TFTPClient { 

    public static void main(String[] args) throws IOException, InterruptedException {
        // command line arg 0 = windowsize, 1 = drop packets

        String dropPackets = "f";

        if(args.length > 1) {
            dropPackets = "t";
        }

        int windowSize = Integer.parseInt(args[0]);

        HashMap<Integer, ByteBuffer> imageBlockHash = new HashMap<>();
        long key;
        // create Datagram socket
        DatagramSocket socket = new DatagramSocket();

        // send request
        byte[] bytes = new byte[6];
        ByteBuffer buffer = ByteBuffer.wrap(bytes);

        InetAddress address = InetAddress.getByName("localhost");

        long randomClientNum = (int) (Math.random() * (999999 - 100000)) + 100000;
        ByteBuffer keyResponse = ByteBuffer.wrap(EncodingHelper.parseLongtoByteArr(randomClientNum));

        DatagramPacket packet = new DatagramPacket(keyResponse.array(), keyResponse.array().length, address, 3000);

        long startTime = System.nanoTime();
        socket.send(packet);

        packet = new DatagramPacket(buffer.array(), buffer.array().length);
        socket.receive(packet);

        byte[] tempArr = packet.getData();
        long randomServerNum = EncodingHelper.parseKeyPacket(tempArr);

        key = randomClientNum ^ randomServerNum;
        // add slidingWindow thing here
        TFTPPacket initialRRQ = new TFTPPacket(1, "https://images.thebrag.com/td/uploads/2022/10/5sos-1-768x435.jpg","octet",windowSize, dropPackets);
        packet = new DatagramPacket(EncodingHelper.performXOR(key,initialRRQ.getPacket().array()), initialRRQ.getPacket().array().length, address, 3000);
        socket.send(packet);

        bytes = new byte[1024];
        buffer = ByteBuffer.wrap(bytes);
        packet = new DatagramPacket(buffer.array(), buffer.remaining());
        socket.receive(packet);

        ArrayList<TFTPPacket> slidingWindow = new ArrayList<>();
        int slidingWindowCounter = 1;
        TFTPPacket receivedPacket = new TFTPPacket(ByteBuffer.wrap(EncodingHelper.performXOR(key,packet.getData())));
        if(receivedPacket.getOpCode() == 3) {
            while(receivedPacket.getData().array().length == 512) {
                if(slidingWindowCounter < initialRRQ.getSlidingWindowSize()) {
                    slidingWindow.add(receivedPacket);
                    
                    bytes = new byte[1024];
                    buffer = ByteBuffer.wrap(bytes);
                    packet = new DatagramPacket(buffer.array(), buffer.array().length, address, 3000);
                    
                    socket.receive(packet);
                    
                    receivedPacket = new TFTPPacket(ByteBuffer.wrap(EncodingHelper.performXOR(key, packet.getData())));

                    slidingWindowCounter++;
                }   else {

                // imageBlockHash.put(receivedPacket.getBlockNum(), receivedPacket.getData());
                slidingWindow.add(receivedPacket);
                TFTPPacket ack = new TFTPPacket(4,slidingWindow.get(0).getBlockNum());
                packet = new DatagramPacket(EncodingHelper.performXOR(key, ack.getPacket().array()), ack.getPacket().array().length, address, 3000);
                socket.send(packet);

                imageBlockHash.put(slidingWindow.get(0).getBlockNum(), slidingWindow.get(0).getData());
                slidingWindow.remove(0);

                bytes = new byte[1024];
                buffer = ByteBuffer.wrap(bytes);
                packet = new DatagramPacket(buffer.array(), buffer.array().length, address, 3000);
                
                socket.receive(packet);

                receivedPacket = new TFTPPacket(ByteBuffer.wrap(EncodingHelper.performXOR(key, packet.getData())));
                }


            }
            slidingWindow.add(receivedPacket);
            while(slidingWindow.size() > 0) {
                TFTPPacket ack = new TFTPPacket(4,slidingWindow.get(0).getBlockNum());
                packet = new DatagramPacket(EncodingHelper.performXOR(key, ack.getPacket().array()), ack.getPacket().array().length, address, 3000);
                socket.send(packet);

                imageBlockHash.put(slidingWindow.get(0).getBlockNum(), slidingWindow.get(0).getData());
                slidingWindow.remove(0);
            }

            long duration = System.nanoTime() - startTime;

           
            //imageBlockHash.put(receivedPacket.getBlockNum(), receivedPacket.getData());
            // slidingWindow.add(receivedPacket);

            ByteBuffer imageByteBuffer = ByteBuffer.allocate(1000000);
            for(int x = 1; x <= receivedPacket.getBlockNum(); x++) {
                imageByteBuffer.put(imageBlockHash.get(x).array());
            }


            OutputStream os = new FileOutputStream("image.jpg");

            byte[] finalImage = Arrays.copyOfRange(imageByteBuffer.array(), 0, TFTPPacket.getLastIndexOfData(imageByteBuffer.array()));
            
            double throughput = ((finalImage.length)/Math.pow(2, 20)) * (duration/1000000000.00);

            System.out.println(throughput);

            os.write(finalImage);




        }
        // display response
        
        

        socket.close();


    }
}