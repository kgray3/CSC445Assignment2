import java.lang.module.ResolutionException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class TFTPPacket {
    public ByteBuffer packet = ByteBuffer.allocate(1024);
    /*
     * Constructor to create an RRQ/WRQ packet. Has the following inputs:
     *      1) a 2 byte opCode specifying RRQ or WRQ for 
     *         read/write type (1 or 2, respectively)
     *      2) a String filename
     *      3) a Sting mode, denoting type of transmission - for this project
     *          we will be using "octet" mode
     */
    
    public TFTPPacket(int opCode, String fileName, String mode) {
        packet = ByteBuffer.allocate(4 + fileName.length() + mode.length());
        byte[] op= {0, (byte) opCode};
        this.packet.put(ByteBuffer.wrap(op));
        this.packet.put(ByteBuffer.wrap(fileName.getBytes()));
        this.packet.put((byte) 0);
        this.packet.put(ByteBuffer.wrap(mode.getBytes()));
        this.packet.put((byte) 0);

    }

    /*
     * Constructor to create a data packet. Contains the following:
     *      1) A 2 byte OpCode (3) for a data packet
     *      2) A 2 byte block number for packet ordering
     *      3) A 0-512 byte block of data
     */
    public TFTPPacket(int blockNum, ByteBuffer data) {
        
        byte[] b = {0,3};
        this.packet.put(ByteBuffer.wrap(b));
        this.packet.put((byte) blockNum);
        this.packet.put(data);

    }

    /*
     * Received TFTPPacket constructor.
     */
    public TFTPPacket(ByteBuffer response) {
        this.packet = response;
    } 

    /*
     * Received packet :)
     */
    public TFTPPacket(int opCode, int blockNum) {

        byte[] op = {0, (byte) opCode};

        this.packet.put(ByteBuffer.wrap(op));
        this.packet.put((byte) blockNum);

        
    }

    // TO-DO -> ADD TFTP OPTIONS FOR TCP SLIDING WINDOW
    // TO-DO -> ADD ERROR PACKET
    // Standard getters and setters for TFTPPacket class
    public int getOpCode() {
        byte[] p = packet.array();
        
        return p[1];
        //return this.opCode;
    }

    public String getFileName() {
        byte[] filename = new byte[256];
        

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

    public ByteBuffer getPacket() {
        return this.packet;
    }

    public int getBlockNum() {
        byte[] p = packet.array();
        return p[3];
    }

    public ByteBuffer getData() {
        byte[] dataArr = Arrays.copyOfRange(this.packet.array(), 4, 1024 - this.packet.remaining());
        return ByteBuffer.wrap(dataArr);
    }



    
}
