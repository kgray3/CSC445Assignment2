package packets;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/*
 * Object Class for TFTP RRQ/WRQ packets.
 */
public class RequestPacket {
    public ByteBuffer packet;

    /*
     * Constructor to create an RRQ/WRQ packet. Has the following inputs:
     *      1) a 2 byte opCode specifying RRQ or WRQ for 
     *         read/write type (1 or 2, respectively)
     *      2) a String filename
     *      3) a Sting mode, denoting type of transmission - for this project
     *          we will be using "octet" mode
     *      4) windowSize - an int specifying the size of the window
     *         for the sliding window protocol
     *
     */
    public RequestPacket(int opCode, String fileName, String mode, int windowSize, String dropPackets) {
        packet = ByteBuffer.allocate(9 + fileName.length() + mode.length() + "WindowSize".length() +"DropPackets".length() + dropPackets.getBytes().length);
        byte[] op= {0, (byte) opCode};
        this.packet.put(ByteBuffer.wrap(op));
        this.packet.put(ByteBuffer.wrap(fileName.getBytes()));
        this.packet.put((byte) 0);
        this.packet.put(ByteBuffer.wrap(mode.getBytes()));
        this.packet.put((byte) 0);
        this.packet.put("WindowSize".getBytes());
        this.packet.put((byte) 0 );
        this.packet.put((byte) windowSize);
        this.packet.put((byte) 0);
        this.packet.put("DropPackets".getBytes());
        this.packet.put((byte) 0);
        this.packet.put(dropPackets.getBytes());
        this.packet.put((byte) 0);

    }

    /*
     * Constructor for a received packet that is thought to be of type
     * RequestPacket (used on server side)
     */
    public RequestPacket(ByteBuffer packet) {
        this.packet = packet;
    }

    // Getter to return opCode (RRQ=1 and WRQ=2)
    public int getOpCode() {
        byte[] p = packet.array();
        
        return p[1];
    }

    // Getter to return the file name
    public String getFileName() {
        byte[] filename = new byte[1024];
        

        for(int i = 2; i < packet.array().length; i++) {
            if(packet.array()[i] == 0) {
                ByteBuffer b = ByteBuffer.wrap(filename);
                return StandardCharsets.UTF_8.decode(b).toString();
            } else {
                filename[i - 2] = packet.array()[i];
            }
        }

        return filename.toString(); 
    }

    // Getter to return the mode ("octet")
    public String getMode() {
        byte[] mode = new byte[256];
         int modeCounter = 0;
         boolean secondZero = false;
        for(int i = 2; i < packet.array().length; i ++) {
             if(packet.array()[i] == 0 && secondZero) {
                 ByteBuffer b = ByteBuffer.wrap(mode);
                 return StandardCharsets.UTF_8.decode(b).toString();
             } else if (packet.array()[i] == 0) {
                 secondZero = true;
             } else if(secondZero) {
                 mode[modeCounter] = packet.array()[i];
                 modeCounter++;
             }
        }
        return mode.toString();
     }

     // Getter to get the dropped packet TFTP option
     public boolean getDropPacket() {
        byte[] p = this.packet.array();

        byte[] dropPacket = {p[getLastIndexOfData(p)]};

        String choice = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(dropPacket)).toString();

        if(choice.equalsIgnoreCase("t")) {
            return true;
        } else {
            return false;
        }

    }

    // Getter to get the value of the "SlidingWindow" option
    public int getSlidingWindowSize() {
        byte[] p = packet.array();
        int zeroCounter =0;

        for(int i = 0; i < p.length; i++) {
            if(zeroCounter == 4 && p[i] != 0) {
                return p[i] & 0xff;
            } else if(p[i] == 0) {
                zeroCounter++;
            }
        }
        return 1;
    }

    // Getter to return the whole RequestPacket as type ByteBuffer
    public ByteBuffer getPacket() {
        return this.packet;
    }

    // Helper method to determine the end of the packet
    public static int getLastIndexOfData(byte[] b) {
        for(int i = b.length - 1; i >= 0; i--) {
            if(b[i] != 0) {
                return i;
            }
        }

        return 0;
    }



}
