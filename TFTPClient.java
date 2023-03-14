import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;

public class TFTPClient {

    public static void main(String[] args) throws IOException {

        // create Datagram socket
        DatagramSocket socket = new DatagramSocket();

        // send request
        byte[] bytes = new byte[256];
        ByteBuffer buffer = ByteBuffer.wrap(bytes);

        //System.out.println(buffer.remaining());
       

        InetAddress address = InetAddress.getByName("localhost");

        TFTPPacket initialRRQ = new TFTPPacket(1, "test.jpg","octet");
        DatagramPacket packet = new DatagramPacket(initialRRQ.getPacket().array(), initialRRQ.getPacket().array().length, address, 3000);
        System.out.println(new TFTPPacket(ByteBuffer.wrap(packet.getData())).getFileName());
        socket.send(packet);

        // response
        packet = new DatagramPacket(buffer.array(), buffer.remaining());
        socket.receive(packet);
        if(new TFTPPacket(ByteBuffer.wrap(packet.getData())).getOpCode() == 4) {
            System.out.println("hell yea");
        }
        // display response
        
        

        socket.close();


    }
}