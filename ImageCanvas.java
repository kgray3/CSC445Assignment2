import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;

public class ImageCanvas {
    public static void displayImage(String imageFileName) 
        {
            JFrame f = new JFrame("Received Image");
            ImageIcon icon = new ImageIcon(imageFileName);
            f.add(new JLabel(icon));
            f.pack();
            f.setVisible(true);
        }

}
