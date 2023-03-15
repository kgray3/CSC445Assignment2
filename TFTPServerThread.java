import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;

public class TFTPServerThread extends Thread {
    protected DatagramSocket socket = null;
    protected BufferedReader in = null;
    protected boolean running = true;
    protected boolean initialConnection = true;
    protected boolean initialTFTPConnection = false;

    public TFTPServerThread() throws IOException {
        this("TFTPServerThread");
    }

    public TFTPServerThread(String name) throws IOException {
        super(name);
        socket = new DatagramSocket(3000);
    }
    String urlString = "";
    byte[] bytes = new byte[6];
    ByteBuffer buffer = ByteBuffer.wrap(bytes);
    DatagramPacket packet = new DatagramPacket(buffer.array(), buffer.remaining());
    long key;
    public void run() {
        while(running) {
            
            try{
                System.out.println(key);
                TFTPPacket response;
                if(initialConnection) {
                    // receive
                    
                    socket.receive(packet);
                    byte[] tempArr = packet.getData();
                    long randomClientNum = EncodingHelper.parseKeyPacket(tempArr);
                    long randomServerNum = (int) (Math.random() * (999999 - 100000)) + 100000;

                    key = randomClientNum ^ randomServerNum;
                    ByteBuffer keyResponse = ByteBuffer.wrap(EncodingHelper.parseLongtoByteArr(randomServerNum));

                    InetAddress address = packet.getAddress();
                    int port = packet.getPort();
                    packet = new DatagramPacket(keyResponse.array(), keyResponse.array().length, address, port);
                    socket.send(packet);
                    initialConnection = false;
                    initialTFTPConnection = true;
                } else if(initialTFTPConnection){
                    bytes = new byte[1024];
                    buffer = ByteBuffer.wrap(bytes);

                    // receive
                    packet = new DatagramPacket(buffer.array(), buffer.remaining());
                    socket.receive(packet);


                    TFTPPacket receivedPacket = new TFTPPacket(ByteBuffer.wrap(EncodingHelper.performXOR(key,packet.getData())));
                    urlString = receivedPacket.getFileName();
                    initialTFTPConnection = false;

                } else {
                    
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
                        packet = new DatagramPacket(EncodingHelper.performXOR(key,buffer.array()), buffer.array().length, address, port);
                        socket.send(packet);

                        socket.receive(packet);

                }
            }


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

    }

    
    
}
