import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;

import packets.ACKPacket;
import packets.DataPacket;
import packets.RequestPacket;

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
        socket = new DatagramSocket(26925);
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
                System.out.println(key);
                if(initialConnection) {
                    socket.setSoTimeout(60000);
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
                    socket.setSoTimeout(1000);
                    byte[] receivedPacketData = Arrays.copyOfRange(packet.getData(), 0, DataPacket.getLastIndexOfData(packet.getData()));
                    RequestPacket receivedPacket = new RequestPacket(ByteBuffer.wrap(EncodingHelper.performXOR(key,receivedPacketData)));
                    int windowSize = receivedPacket.getSlidingWindowSize();

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

                    
                    ArrayList<DataPacket> imageFrames = getImageArrayList(image);

                    ArrayList<DataPacket> slidingWindow = new ArrayList<>();

                    //add the initial window :)
                    for(int i = 0; i < windowSize; i++) {
                        if(i < imageFrames.size()) {
                            slidingWindow.add(imageFrames.get(i));

                            buffer = imageFrames.get(i).getPacket();
                        
                            InetAddress address = packet.getAddress();
                            int port = packet.getPort();
                            packet = new DatagramPacket(EncodingHelper.performXOR(key,buffer.array()), buffer.array().length, address, port);
                            socket.send(packet);
                            }
                    }

                    while(slidingWindow.size() > 0) {

                        try
                        {
                            socket.receive(packet);
                        
                            ACKPacket clientResponse = new ACKPacket(ByteBuffer.wrap(EncodingHelper.performXOR(key,packet.getData())));

                            if(clientResponse.getBlockNum() == slidingWindow.get(0).getBlockNum() ) {
                            

                            // check if there are anymore frames to add, if so, add to sliding window
                                if(slidingWindow.get(slidingWindow.size()-1).getBlockNum() != imageFrames.get(imageFrames.size()-1).getBlockNum()) {
                                    slidingWindow.add(imageFrames.get(slidingWindow.get(slidingWindow.size()-1).getBlockNum()));

                                    buffer = slidingWindow.get(slidingWindow.size() - 1).getPacket();
                        
                                    InetAddress address = packet.getAddress();
                                    int port = packet.getPort();
                                    packet = new DatagramPacket(EncodingHelper.performXOR(key,buffer.array()), buffer.array().length, address, port);
                                    socket.send(packet);
                                }
                                slidingWindow.remove(0);
                            }
                                
                        } catch(SocketTimeoutException e) {
                            System.out.println("Oh no, we timed out for " + slidingWindow.get(0).getBlockNum());
                            // if a timeout occurs, resend the window
                            buffer = slidingWindow.get(0).getPacket();

                            for(int i = slidingWindow.get(0).getBlockNum(); i < slidingWindow.get(0).getBlockNum() + windowSize; i++) {
                                if(i <= imageFrames.size()) {
                                    buffer = imageFrames.get(i-1).getPacket();
                                    System.out.println(imageFrames.get(i-1).getBlockNum());
                                
                                    InetAddress address = packet.getAddress();
                                    int port = packet.getPort();
                                    packet = new DatagramPacket(EncodingHelper.performXOR(key,buffer.array()), buffer.array().length, address, port);
                                    socket.send(packet);
                                }
                            }
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
        while ((length = inputStream.read(bytes)) > 0) {
            baos.write(bytes, 0, length);
        }

        return baos.toByteArray();

    }

    public static ArrayList<DataPacket> getImageArrayList(byte[] image) {
        ArrayList<DataPacket> a = new ArrayList<>();
        int numOfPackets = (int) Math.ceil(image.length / 512.0);
        for (int i = 0; i < numOfPackets; i++) {
            int startingIndex = i * 512;

            byte[] currentDataByte = Arrays.copyOfRange(image, startingIndex, startingIndex + 512);

            DataPacket response = new DataPacket(i + 1, ByteBuffer.wrap(currentDataByte));

            a.add(response);

        }
        return a;
    }




}
