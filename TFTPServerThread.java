import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;

import packets.ACKPacket;
import packets.DataPacket;
import packets.RequestPacket;

// TO-DO: add simulation of dropping packets, add time-outs
public class TFTPServerThread extends Thread {
    protected static DatagramSocket socket = null;
    protected BufferedReader in = null;
    protected boolean running = true;
    protected boolean initialConnection = true;
    protected boolean initialTFTPConnection = false;

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
    static DatagramPacket packet;
    static long key;

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
                    sendWindow(slidingWindow, imageFrames, windowSize,0);


                    while(slidingWindow.size() > 0) {

                        try
                        {
                            socket.receive(packet);
                        
                            ACKPacket clientResponse = new ACKPacket(ByteBuffer.wrap(EncodingHelper.performXOR(key,packet.getData())));

                            if(clientResponse.getBlockNum() == slidingWindow.get(slidingWindow.size()-1).getBlockNum() ) {
                            

                            // check if there are anymore frames to add, if so, add to sliding window
                                if(slidingWindow.get(slidingWindow.size()-1).getBlockNum() != imageFrames.get(imageFrames.size()-1).getBlockNum()) {
                                    
                                    int startingIndex = slidingWindow.get(slidingWindow.size()-1).getBlockNum();
                                    slidingWindow.clear();
                                    slidingWindow = sendWindow(slidingWindow, imageFrames, windowSize,startingIndex);

                                }else{
                                    //int startingIndex = slidingWindow.get(0).getBlockNum();
                                    slidingWindow.clear();

                                    //sendWindow(slidingWindow, imageFrames, windowSize, startingIndex - 1);
                                }
                                
                            }
                                
                        } catch(SocketTimeoutException e) {
                            System.out.println("Oh no, we timed out for window ending with " + slidingWindow.get(slidingWindow.size()-1).getBlockNum());
                            // if a timeout occurs, resend the window
                            //buffer = slidingWindow.get(0).getPacket();

                            int startingIndex = slidingWindow.get(0).getBlockNum()-1;
                            slidingWindow.clear();
                            slidingWindow = sendWindow(slidingWindow, imageFrames, windowSize, startingIndex);
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

    public static ArrayList<DataPacket> sendWindow(ArrayList<DataPacket> slidingWindow, ArrayList<DataPacket> imageFrames, int windowSize,
    int startingIndex) throws IOException {
        for(int i = startingIndex; i < startingIndex + windowSize; i++) {
            if(i < imageFrames.size()) {
                    ByteBuffer buffer;
                    slidingWindow.add(imageFrames.get(i));

                    buffer = imageFrames.get(i).getPacket();

                    System.out.println(imageFrames.get(i).getBlockNum());
                    InetAddress address = packet.getAddress();
                    int port = packet.getPort();
                    packet = new DatagramPacket(EncodingHelper.performXOR(key,buffer.array()), buffer.array().length, address, port);

                    socket.send(packet);
                    
                }
        }
        return slidingWindow;

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
