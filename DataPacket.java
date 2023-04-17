package packets;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class DataPacket {
    ByteBuffer packet = ByteBuffer.allocate(1024);

    /*
     * Constructor to create a TFTP data packet. Contains the following:
     *      1) A 2 byte OpCode (3) for a data packet
     *      2) A 2 byte block number for packet ordering
     *      3) A 0-512 byte block of data
     */
    public DataPacket(int blockNum, ByteBuffer data ) {
        byte[] b = {0,3};

        byte[] c = {0, (byte) blockNum};
        this.packet.put(ByteBuffer.wrap(b));
        this.packet.put(c);
        this.packet.put(data);
    }

    /*
     * Received TFTPPacket constructor.
     */
    public DataPacket(ByteBuffer response) {
        this.packet = response;
    } 

    // Getter to return opCode (Data packet = 3)
    public int getOpCode() {
        byte[] p = packet.array();
        
        return p[1];
    }

    // Getter method to get block number of Data packet.
    public int getBlockNum() {
        byte[] p = packet.array();
        return p[3];
    }

    // Getter method to get the data of a Data packet.
    public ByteBuffer getData() {
            byte[] dataArr = Arrays.copyOfRange(this.packet.array(), 4, getLastIndexOfData(this.packet.array()) + 1);
        return ByteBuffer.wrap(dataArr);
    }

    public ByteBuffer getPacket() {
        return this.packet;
    }
    
    // Helper method to trim received packet to correct length.
    public static int getLastIndexOfData(byte[] b) {
        for(int i = b.length - 1; i >= 0; i--) {
            if(b[i] != 0) {
                return i;
            }
        }

        return 0;
    }
}
