package packets;

import java.nio.ByteBuffer;

public class ACKPacket {
    ByteBuffer packet = ByteBuffer.allocate(4);
    /*
     * ACK packet for TFTP protocol. Includes
     * opcode and block number.
     */
    public ACKPacket(int opCode, int blockNum) {

        byte[] op = {0, (byte) opCode};

        this.packet.put(ByteBuffer.wrap(op));

        byte[] block = {0, (byte) blockNum};
        this.packet.put(block);   
    }

    /*
     * Constructor for a received packet that is thought to be of type
     * ACKPacket (used on server side)
     */
    public ACKPacket(ByteBuffer packet) {
        this.packet = packet;
    }

    // Getter to return the whole RequestPacket as type ByteBuffer
    public ByteBuffer getPacket() {
        return this.packet;
    }

    // Getter to return opCode
    public int getOpCode() {
        byte[] p = packet.array();
        
        return p[1];
    }

    // Getter to return block number
     public int getBlockNum() {
        byte[] p = packet.array();
        return p[3] & 0xff;
    }


}
