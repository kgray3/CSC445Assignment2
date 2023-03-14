import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;

public class TFTPServerThread extends Thread {
    protected DatagramSocket socket = null;
    protected BufferedReader in = null;
    protected boolean running = true;
    protected boolean initialConnection = true;

    public TFTPServerThread() throws IOException {
        this("TFTPServerThread");
    }

    public TFTPServerThread(String name) throws IOException {
        super(name);
        socket = new DatagramSocket(3000);
    }

    public void run() {
        while(running) {
            try{
                byte[] bytes = new byte[1024];
                ByteBuffer buffer = ByteBuffer.wrap(bytes);

                // receive
                DatagramPacket packet = new DatagramPacket(buffer.array(), buffer.remaining());
                socket.receive(packet);


                TFTPPacket receivedPacket = new TFTPPacket(ByteBuffer.wrap(packet.getData()));

                TFTPPacket response;
                if(initialConnection) {
                    response = new TFTPPacket(4,1);
                    initialConnection = false;
                } else {
                    response = new TFTPPacket(receivedPacket.getOpCode(),receivedPacket.getBlockNum());
                }

                System.out.println(receivedPacket.getOpCode());

                // respond
                //String response = "Hello World!";

                buffer = response.getPacket();

                InetAddress address = packet.getAddress();
                int port = packet.getPort();
                packet = new DatagramPacket(buffer.array(), buffer.remaining(), address, port);
                socket.send(packet);

            } catch (IOException e) {
                e.printStackTrace();
                running = false;
            }
        }
        socket.close();
    }
}
