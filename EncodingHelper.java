public class EncodingHelper {
    public static long parseKeyPacket(byte[] arr) {
        String x = "";
        for(int i = 0; i < arr.length; i++) {
            x += "" + arr[i];
        }

        return Long.parseLong(x);
    }

    public static byte[] parseLongtoByteArr(long l) {
        String longString = ("" + l);
        byte[] arr = new byte[longString.length()];

        for(int i = 0; i < arr.length; i++) {
            arr[i] = (byte) Integer.parseInt(longString.substring(i, i+1));
        }

        return arr;
    }

    public static byte[] performXOR(long key, byte[] message) {
        byte[] result = new byte[message.length];

        for(int i = 0; i < result.length; i++) {
            result[i] = (byte) (message[i] ^ key);
        }

        return result;
    }
}
