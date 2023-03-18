import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;

// TO-DO: add simulation of dropping packets, add time-outs
public class TFTPServerThread extends Thread {
    protected DatagramSocket socket = null;
    protected BufferedReader in = null;
    protected boolean running = true;
    protected boolean initialConnection = true;
    protected boolean initialTFTPConnection = false;
    protected boolean dropPackets = false;

    public TFTPServerThread() throws IOException {
        this("TFTPServerThread");
    }

    public TFTPServerThread(String name) throws IOException {
        super(name);
        socket = new DatagramSocket(3000);
    }
    String urlString = "";
    ByteBuffer previousImage;
    byte[] bytes;
    ByteBuffer buffer;
    DatagramPacket packet;
    long key;
    public void run() {
        while(running) {
            
            try{
                // System.out.println(key);
                // TFTPPacket response;
                if(initialConnection) {
                    // receive
                    bytes = new byte[6];
                    buffer = ByteBuffer.wrap(bytes);
                    packet = new DatagramPacket(buffer.array(), buffer.remaining());
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

                    initialTFTPConnection = false;

                } else {
                    byte[] receivedPacketData = Arrays.copyOfRange(packet.getData(), 0, TFTPPacket.getLastIndexOfData(packet.getData()));
                    TFTPPacket receivedPacket = new TFTPPacket(ByteBuffer.wrap(EncodingHelper.performXOR(key,receivedPacketData)));
                    int windowSize = receivedPacket.getSlidingWindowSize();
                    
                    boolean dropPacket = false;
                    

                    dropPackets = receivedPacket.getDropPacket();
                    byte[] image;
                    // checkcs if requested image is cached
                    if(urlString.equalsIgnoreCase(receivedPacket.getFileName())) {
                        image = previousImage.array();
                    } else{
                        // use HTTP to get image
                        urlString = receivedPacket.getFileName();
                        image = getImageBytesFromURL(urlString);
                    }
                    previousImage = ByteBuffer.wrap(image);

                    
                    ArrayList<TFTPPacket> imageFrames = getImageArrayList(image);

                    ArrayList<TFTPPacket> slidingWindow = new ArrayList<>();

                    //add the initial window :)
                    for(int i = 0; i < windowSize; i++) {
                        slidingWindow.add(imageFrames.get(i));

                        buffer = imageFrames.get(i).getPacket();
                        
                        InetAddress address = packet.getAddress();
                        int port = packet.getPort();
                        packet = new DatagramPacket(EncodingHelper.performXOR(key,buffer.array()), buffer.array().length, address, port);
                        socket.send(packet);
                    }

                    while(slidingWindow.size() > 0) {
                        socket.receive(packet);

                        if(dropPackets) {
                            // calc 1% drop for each packet that comes through
                            if((int)((Math.random()* 100) + 1) == 1) {
                                dropPacket = true;
                            }
                        }
                        
                        TFTPPacket clientResponse = new TFTPPacket(ByteBuffer.wrap(EncodingHelper.performXOR(key,packet.getData())));

                        if(clientResponse.getBlockNum() == slidingWindow.get(0).getBlockNum() && !dropPacket) {
                            slidingWindow.remove(0);

                            // check if there are anymore frames to add, if so, add to sliding window
                            if(slidingWindow.get(slidingWindow.size()-1).getBlockNum() != imageFrames.get(imageFrames.size()-1).getBlockNum()) {
                                slidingWindow.add(imageFrames.get(slidingWindow.get(slidingWindow.size()-1).getBlockNum()));

                                buffer = slidingWindow.get(slidingWindow.size() - 1).getPacket();
                        
                                InetAddress address = packet.getAddress();
                                int port = packet.getPort();
                                packet = new DatagramPacket(EncodingHelper.performXOR(key,buffer.array()), buffer.array().length, address, port);
                                socket.send(packet);
                            }
                        } else {
                            
                            // uh oh, a packet was dropped...we gotta resend



                            TFTPPacket tempP = slidingWindow.get(0);
                            dropPacket = false;
                            buffer = slidingWindow.get(0).getPacket();

                            slidingWindow.remove(0);
                            slidingWindow.add(tempP);
                            InetAddress address = packet.getAddress();
                            int port = packet.getPort();
                            packet = new DatagramPacket(EncodingHelper.performXOR(key,buffer.array()), buffer.array().length, address, port);
                            socket.send(packet);
                        }
                    }

                initialConnection = true;
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

    public static ArrayList<TFTPPacket> getImageArrayList(byte[] image) {
        ArrayList<TFTPPacket> a = new ArrayList<>();
        int numOfPackets = (int) Math.ceil(image.length/512.0);
        for(int i = 0; i < numOfPackets; i++) {
            int startingIndex = i*512;

            byte[] currentDataByte = Arrays.copyOfRange(image, startingIndex, startingIndex + 512);

            TFTPPacket response = new TFTPPacket(i+1, ByteBuffer.wrap(currentDataByte));

            a.add(response);
                        

        }
        return a;
    }

    public static int blockInArray(int blockNum, ArrayList<TFTPPacket> p) {
        for(int i = 0; i < p.size(); i++) {
            if(p.get(i).getBlockNum() == blockNum) {
                return i;
            }
        }
        return -1;
    }

    
    
}
