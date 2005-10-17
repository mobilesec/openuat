package uk.ac.lancs.relate;

import java.awt.*;
import java.net.URL;

/*
 * This class implements an icon (image with label)
 * that is compatible with AWT.
 * 
 * @version 17-May-2005
 * @author  kray
 */
public class ImageIcon extends Component {
	private int currentWidth = -1;
	private int currentHeight = -1;
	private Image image = null;
	private Label label = null;
	
	/** create a new image icon from a given image */
	public ImageIcon(Image i, String text) {
	    image = i;
	    label = new Label(text);
	}
	
	/** create a new image icon of a given size from a given image */
	public ImageIcon(Image i, String text, int w, int h) {
		image = i ;
	    label = new Label(text);
		currentWidth = w;
		currentHeight = h;
	}
	
	/** create a new image icon from a given file */
	public ImageIcon(String path, String text) {
	    image = getImage(path);
	    label = new Label(text);
	}
	
	/** create a new image icon of a given size from a given file */
	public ImageIcon(String path, String text, int w, int h) {
		image = getImage(path) ;
	    label = new Label(text);
		currentWidth = w;
		currentHeight = h;
	}
	
	/** create a new image icon from a given image URL */
	public ImageIcon(URL url, String text) {
	    image = getImage(url);
	    label = new Label(text);
	}
	
	/** create a new image icon of a given size from a given image URL */
	public ImageIcon(URL url, String text, int w, int h) {
		image = getImage(url) ;
	    label = new Label(text);
		currentWidth = w;
		currentHeight = h;
	}
    
    /** create a new Image icon without text */
    public ImageIcon(String path) {
        image = getImage(path);
        label = new Label("");
    }
	
	/** load an image from a file */
	private Image getImage(String path) {
	    return Toolkit.getDefaultToolkit().getImage(path);
	}
	
	/** load an image from a given URL */
	private Image getImage(URL url) {
	    return Toolkit.getDefaultToolkit().getImage(url);
	}
	
	public Image getImage() {
	    return image;
	}
	
	public int getWidth() {
	    int labelWidth = label.getText().length() * 8; //label.getWidth();
	    
	    if (image == null)
	        currentWidth = labelWidth;
	    else {
	        currentWidth = (labelWidth > image.getWidth(label) ? 
	                labelWidth : image.getWidth(label));
	    }
	    return currentWidth;
	}

	public int getHeight() {
	    int labelHeight = 14; // label.getHeight();
	    //System.out.println(labelHeight);
	    
	    if (image == null)
	        currentHeight = labelHeight;
	    else {
	        currentHeight = labelHeight + image.getHeight(label) + 4;
	    }
	    return currentHeight;
	}

	public Dimension getPreferredSize() {
		Dimension d = new Dimension();
		d.width = this.getWidth();
		d.height = this.getHeight();
		return d;
	}
	public Dimension getMinimumSize() {
		return getPreferredSize();
	}
	
	public void paint(Graphics g) {
	    int offset = (getWidth() - label.getText().length() * 8) / 2; //label.getWidth()) / 2;
	    
	    g.clearRect(0, 0, getWidth(), getHeight());
//	    System.out.println("want to draw '" + label.getText() + "' at " + offset + ", " + (getHeight()-4));
	    if (image != null) {
	        g.drawImage(image, (getWidth() - image.getWidth(label)) / 2, 0, label);
	    }
	    g.drawString(label.getText(), offset, getHeight() - 4);
	}

	/** test method */
	public static void main(String[] args) {
	    Frame f = new Frame("Toast");
	    ImageIcon i = null;
	    try {
	        i = new ImageIcon("/Users/kray/Documents/source/RelateEngine/img/pda.jpg", "Toast");
	        System.out.println(i);
	    } catch (Exception e) {
	        e.printStackTrace();
	    }
	    f.setLayout(new BorderLayout());
	    f.setSize(400, 400);
	    f.add(i, BorderLayout.CENTER);
	    f.show();
	    f.repaint();
	}
}

