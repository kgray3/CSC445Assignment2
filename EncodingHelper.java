/*
 * Class with helper methods for encoding/decoding
 */
public class EncodingHelper {

    // Method that parses an initial packet for a key of type long
    public static long parseKeyPacket(byte[] arr) {
        String x = "";
        for(int i = 0; i < arr.length; i++) {
            x += "" + arr[i];
        }

        return Long.parseLong(x);
    }

    // Method that stores a long into a byte array where 1 digit = 1 byte
    public static byte[] parseLongtoByteArr(long l) {
        String longString = ("" + l);
        byte[] arr = new byte[longString.length()];

        for(int i = 0; i < arr.length; i++) {
            arr[i] = (byte) Integer.parseInt(longString.substring(i, i+1));
        }

        return arr;
    }

    // Method that performs XOR between each byte of a given byte
    // array and a key of type long
    public static byte[] performXOR(long key, byte[] message) {
        byte[] result = new byte[message.length];

        for(int i = 0; i < result.length; i++) {
            result[i] = (byte) (message[i] ^ key);
        }

        return result;
    }
}
