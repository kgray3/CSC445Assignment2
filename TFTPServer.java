import java.io.*;

public class TFTPServer {
    public static void main(String[] args) throws IOException {
        new TFTPServerThread().start();
    } 
    
}
