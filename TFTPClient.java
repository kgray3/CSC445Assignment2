import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;

public class TFTPClient {

    public static void main(String[] args) throws IOException, InterruptedException {
        HashMap<Integer, ByteBuffer> imageBlockHash = new HashMap<>();
        // create Datagram socket
        DatagramSocket socket = new DatagramSocket();

        // send request
        byte[] bytes = new byte[1024];
        ByteBuffer buffer = ByteBuffer.wrap(bytes);

        //System.out.println(buffer.remaining());
       

        InetAddress address = InetAddress.getByName("localhost");

        TFTPPacket initialRRQ = new TFTPPacket(1, "https://images.thebrag.com/td/uploads/2022/10/5sos-1-768x435.jpg","octet");
        DatagramPacket packet = new DatagramPacket(initialRRQ.getPacket().array(), initialRRQ.getPacket().array().length, address, 3000);
        socket.send(packet);

        packet = new DatagramPacket(buffer.array(), buffer.remaining());
        socket.receive(packet);

        TFTPPacket receivedPacket = new TFTPPacket(ByteBuffer.wrap(packet.getData()));
        if(receivedPacket.getOpCode() == 3) {
            while(receivedPacket.getData().array().length == 512) {
                
                imageBlockHash.put(receivedPacket.getBlockNum(), receivedPacket.getData());
                
                TFTPPacket ack = new TFTPPacket(4,receivedPacket.getBlockNum());
                packet = new DatagramPacket(ack.getPacket().array(), ack.getPacket().array().length, address, 3000);
                socket.send(packet);

                bytes = new byte[1024];
                buffer = ByteBuffer.wrap(bytes);
                packet = new DatagramPacket(buffer.array(), buffer.array().length, address, 3000);
                
                socket.receive(packet);

                receivedPacket = new TFTPPacket(ByteBuffer.wrap(packet.getData()));
            }

            imageBlockHash.put(receivedPacket.getBlockNum(), receivedPacket.getData());

            ByteBuffer imageByteBuffer = ByteBuffer.allocate(1000000);
            for(int x = 1; x <= receivedPacket.getBlockNum(); x++) {
                imageByteBuffer.put(imageBlockHash.get(x).array());
            }

            OutputStream os = new FileOutputStream("image.jpg");

            byte[] finalImage = Arrays.copyOfRange(imageByteBuffer.array(), 0, TFTPPacket.getLastIndexOfData(imageByteBuffer.array()));
            
            
            os.write(finalImage);




        }
        // display response
        
        

        socket.close();


    }
}