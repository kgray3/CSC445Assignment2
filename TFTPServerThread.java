import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;

public class TFTPServerThread extends Thread {
    protected DatagramSocket socket = null;
    protected BufferedReader in = null;
    protected boolean running = true;

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
                byte[] bytes = new byte[256];
                ByteBuffer buffer = ByteBuffer.wrap(bytes);

                // receive
                DatagramPacket packet = new DatagramPacket(buffer.array(), buffer.remaining());
                socket.receive(packet);

                // respond
                String response = "Hello World!";

                buffer = ByteBuffer.wrap(response.getBytes());

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
