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
        DatagramPacket packet = new DatagramPacket(buffer.array(), buffer.remaining(), address, 3000);
        socket.send(packet);

        // response
        packet = new DatagramPacket(buffer.array(), buffer.remaining());
        socket.receive(packet);

        // display response
        String received = new String(packet.getData(), 0, packet.getLength());
        System.out.println(received);

        socket.close();


    }
}