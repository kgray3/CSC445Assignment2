package packets;

import java.nio.ByteBuffer;

public class ErrorPacket {
    public ByteBuffer packet = ByteBuffer.allocate(1024);
    /*
     * TFTP error Packet
     */
    public ErrorPacket(int errorCode, String errMsg) {
        byte[] op = {0, 5};
        byte[] err = {0, (byte) errorCode};

        this.packet.put(op);
        this.packet.put(err);
        this.packet.put(errMsg.getBytes());
        this.packet.put((byte) 0);

    }
}
