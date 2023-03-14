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

                String urlString = "";
                int opCode = 0;

                TFTPPacket response;
                // if(initialConnection) {
                    //response = new TFTPPacket(4,1);
                    urlString = receivedPacket.getFileName();
                    // initialConnection = false;
                    // buffer = response.getPacket();

                    // InetAddress address = packet.getAddress();
                    // int port = packet.getPort();
                    // packet = new DatagramPacket(buffer.array(), buffer.remaining(), address, port);
                    // socket.send(packet);
                // } else {
                    
                    // use HTTP to get image
                    byte[] image = getImageBytesFromURL(urlString);

                    int numOfPackets = (int) Math.ceil(image.length/512.0);

                    for(int i = 0; i < numOfPackets; i++) {
                        int startingIndex = i*512;

                        byte[] currentDataByte = Arrays.copyOfRange(image, startingIndex, startingIndex + 512);

                        response = new TFTPPacket(i+1, ByteBuffer.wrap(currentDataByte));

                        buffer = response.getPacket();

                        InetAddress address = packet.getAddress();
                        int port = packet.getPort();
                        packet = new DatagramPacket(buffer.array(), buffer.array().length, address, port);
                        socket.send(packet);

                        socket.receive(packet);
                    //}

                    //response = new TFTPPacket(receivedPacket.getOpCode(),receivedPacket.getBlockNum());
                }

                // System.out.println(receivedPacket.getOpCode());

                // // respond
                // //String response = "Hello World!";

                // buffer = response.getPacket();

                // InetAddress address = packet.getAddress();
                // int port = packet.getPort();
                // packet = new DatagramPacket(buffer.array(), buffer.remaining(), address, port);
                // socket.send(packet);

            } catch (IOException e) {
                e.printStackTrace();
                running = false;
            }
        }
        socket.close();
    }

    public static byte[] getImageBytesFromURL(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");

        InputStream inputStream = url.openStream();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] bytes = new byte[4096];
        int length;
        while(( length = inputStream.read(bytes)) > 0) {
            baos.write(bytes, 0, length);
        }
        

        return baos.toByteArray();
        // InputStream inputStream = url.openStream();
        // InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
        // StringBuffer stringBuffer = new StringBuffer();
        // int length;

        // while((length = inputStreamReader.read()) != -1) {
        //     stringBuffer.append(length);
        // }

        // byte[] buffer = stringBuffer.toString().getBytes();

        // return buffer;

    }
    
}
