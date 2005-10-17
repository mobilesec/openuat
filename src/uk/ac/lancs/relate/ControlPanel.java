package uk.ac.lancs.relate;

import java.awt.*;
import java.awt.event.*;
import java.net.*;

import javax.swing.JTextField;

/**
 * Provides a GUI to access configuration data in Relate.
 * 
 * @version 17-May-2005
 * @author kray
 */
public class ControlPanel extends Panel implements ItemListener {    
    /* identity fields */
    public TextField userName ;
	public TextField deviceName ;
	public TextField ipAddress ;
	public TextField homePage;
	public TextField portrait;
	
	/* device geometry */
	public TextField devWidth;
	public TextField devHeight;
	public TextField devOffsetFromBack;
	public TextField devOffsetFromLeft;
	public Choice dongleSide;
	public Checkbox considerPhysicalDim = new Checkbox("Consider physical dimension");
	
	public Choice availablePorts;
	
	/* general gui controls */
	Panel tabbedPane;
	CardLayout card;
	public Button ok, cancel ;
	public Checkbox saveSettings;
	final static String IDENTITY = "Identity";
	final static String DEVICE = "Device";
	final static String DATATRANSFER = "Data Transfer";

	/* file transfer settings */
	public Checkbox saveFiles;
	public TextField defaultSavingLocation ;
	public Button browse ;
	public String homeFolder = null ;
	
	/* device type related fields */
	public Choice devices;
	public Panel typePanel;
	public ImageIcon[] deviceIcons;
	String[] deviceNames = {"desktop", "PDA", "laptop", "projector"};    
//    public final static String RELATE_BACK = "relate-back64.gif";
//	public final static String RELATE_FRONT = "relate-front64.gif";
//	public final static String RELATE_LEFT = "relate-left64.gif";
//    public final static String RELATE_RIGHT = "relate-right64.gif";
	public final static String DESKTOP = "desktop.jpg";
	public final static String PDA = "pda.jpg";
	public final static String LAPTOP = "laptop.gif";
	public final static String PROJECTOR = "projector.jpg";
	
	/* an AWT substitute for a panel with a labelled border */
	private class BorderPanel extends Panel {
	    Insets border = new Insets(18, 5, 5, 5);
	    String label = null;
	    BorderPanel(String label) {
	        this.label = label;
	    }
	    public Insets getInsets() {
	        return border;
	    }
	    public void paint(Graphics g) {
	        super.paint(g);
	        Dimension size = getSize();
	        Color color = g.getColor();
	        g.setColor(Color.black);
	        g.drawRect(2, 12, size.width-5, size.height-15);
	        g.setColor(getBackground());
	        g.drawLine(6, 12, label.length() * 9 + 6, 12);
	        g.setColor(Color.black);
	        g.drawString(label, 10, 13);
	        g.setColor(color);
	    }
	    
	}
	    
	/**
	 ** Default constructor
	 ** @param path path to image folder (devices, relate object)
	 **/
	public ControlPanel(String path, String[] portList) {
		super();
		
		Panel borderPanel;
		Panel panel1, panel2, panel3;
		Choice tabSelector;
		Label label;
		String tmp;
		int i;

		/* card/tabbed GUI control */
		card = new CardLayout();
		tabbedPane = new Panel(card);
		setLayout(new BorderLayout());
		tabSelector = new Choice();
		tabSelector.add(IDENTITY);
		tabSelector.add(DEVICE);
		tabSelector.add(DATATRANSFER);
		tabSelector.addItemListener(this);
		add(tabSelector, BorderLayout.NORTH);

		/* create bottom pane */
		panel1 = new Panel(new BorderLayout());
		saveSettings = new Checkbox("save settings");
		panel1.add(saveSettings, BorderLayout.WEST);
		panel2 = new Panel(new BorderLayout());
		ok = new Button("OK");
		cancel = new Button("Cancel");
		panel2.add(ok, BorderLayout.EAST);
		panel2.add(cancel, BorderLayout.WEST);
		panel1.add(panel2, BorderLayout.EAST);
		add(panel1, BorderLayout.SOUTH);
		
		/* create identity pane */
		panel1 = new Panel(new BorderLayout());
		panel2 = new Panel(new GridLayout(5, 1));
		panel3 = new Panel(new FlowLayout(FlowLayout.TRAILING));
		label = new Label("Name:");
		userName = new TextField(20);
		userName.setText(System.getProperties().getProperty("user.name"));
		panel3.add(label);
		panel3.add(userName);
		panel2.add(panel3);
		panel3 = new Panel(new FlowLayout(FlowLayout.TRAILING));
		label = new Label("Host:");
		deviceName = new TextField(20);
		try {
			tmp = InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			tmp = "";
		}
		deviceName.setText(tmp);
		panel3.add(label);
		panel3.add(deviceName);
		panel2.add(panel3);
		panel3 = new Panel(new FlowLayout(FlowLayout.TRAILING));
		label = new Label("IP address:");
		ipAddress = new TextField(20);
		try {
			tmp = InetAddress.getLocalHost().getHostAddress();
		} catch (UnknownHostException e) {
			tmp = "";
		}
		ipAddress.setText(tmp);
		panel3.add(label);
		panel3.add(ipAddress);
		panel2.add(panel3);
		panel3 = new Panel(new FlowLayout(FlowLayout.TRAILING));
		label = new Label("Home page:");
		homePage = new TextField(20);
		homePage.setEnabled(false);
		panel3.add(label);
		panel3.add(homePage);
		panel2.add(panel3);
		panel3 = new Panel(new FlowLayout(FlowLayout.TRAILING));
		label = new Label("Portrait:");
		portrait = new TextField(20);
		portrait.setEnabled(false);
		panel3.add(label);
		panel3.add(portrait);
		panel2.add(panel3);
		panel1.add(panel2, BorderLayout.CENTER);
		tabbedPane.add(panel1, IDENTITY);
		
		/* create device pane */
		panel1 = new Panel(new BorderLayout());
		borderPanel = new Panel(); //new BorderPanel("Type");
		borderPanel.setLayout(new BorderLayout());
		devices = new Choice();
		for (i=0; i<deviceNames.length; i++) {
		    devices.add(deviceNames[i]);
		}
        devices.addItemListener(this);
		borderPanel.add(devices, BorderLayout.NORTH);
		deviceIcons = new ImageIcon[deviceNames.length];
		deviceIcons[0] = new ImageIcon(path + DESKTOP, deviceNames[0]);
		deviceIcons[1] = new ImageIcon(path + PDA, deviceNames[1]);
		deviceIcons[2] = new ImageIcon(path + LAPTOP, deviceNames[2]);
		deviceIcons[3] = new ImageIcon(path + PROJECTOR, deviceNames[3]);
		typePanel = new Panel(new BorderLayout());
		typePanel.add(deviceIcons[0], BorderLayout.CENTER);
		borderPanel.add(typePanel, BorderLayout.CENTER);
		panel1.add(borderPanel, BorderLayout.EAST);
		borderPanel = new Panel(); //BorderPanel("Geometry");
		borderPanel.setLayout(new GridLayout(5, 1));
		panel3 = new Panel(new FlowLayout(FlowLayout.TRAILING));
		label = new Label("Width (mm):");
		devWidth = new TextField(5);
		panel3.add(label);
		panel3.add(devWidth);
		borderPanel.add(panel3);
		panel3 = new Panel(new FlowLayout(FlowLayout.TRAILING));
		label = new Label("Depth (mm):");
		devHeight = new TextField(5);
		panel3.add(label);
		panel3.add(devHeight);
		borderPanel.add(panel3);
		panel3 = new Panel(new FlowLayout(FlowLayout.TRAILING));
		label = new Label("Dongle attached to");
		dongleSide = new Choice();
		for (i=0; i<Configuration.SIDE_NAMES.length; i++) {
		    dongleSide.add(Configuration.SIDE_NAMES[i]);
		}
		panel3.add(label);
		panel3.add(dongleSide);
		borderPanel.add(panel3);
		panel3 = new Panel(new FlowLayout(FlowLayout.TRAILING));
		label = new Label("Distance to left side (mm):");
		devOffsetFromLeft = new TextField(5);
		panel3.add(label);
		panel3.add(devOffsetFromLeft);
		borderPanel.add(panel3);
		panel3 = new Panel(new FlowLayout(FlowLayout.TRAILING));
		label = new Label("Distance to back side (mm):");
		devOffsetFromBack = new TextField(5);
		panel3.add(label);
		panel3.add(devOffsetFromBack);
		borderPanel.add(panel3);
		panel1.add(borderPanel, BorderLayout.WEST);
		panel3 = new Panel(new FlowLayout(FlowLayout.CENTER));
		label = new Label("Port dongle is attached to:");
		availablePorts = new Choice();
		if (portList != null) {
		    for (i=0; i<portList.length; i++) {
		        availablePorts.add(portList[i]);
		    }
		} else {
		    availablePorts.add("no ports found");
		    availablePorts.setEnabled(false);
		}
		panel3.add(label);
		panel3.add(availablePorts);
		panel1.add(panel3, BorderLayout.SOUTH);
		tabbedPane.add(panel1, DEVICE);
		
		/* create data transfer pane */
		panel1 = new Panel(new BorderLayout());
		panel2 = new Panel(new GridLayout(2, 1));
		panel3 = new Panel(new FlowLayout(FlowLayout.CENTER));
		label = new Label("Save files to");
		defaultSavingLocation = new TextField(20);
		browse = new Button("Browse...");
		panel3.add(label);
		panel3.add(defaultSavingLocation);
		panel3.add(browse);
		panel2.add(panel3);
		panel3 = new Panel(new FlowLayout(FlowLayout.CENTER));
		saveFiles = new Checkbox("always save files without asking");
		panel3.add(saveFiles);
		panel2.add(panel3);
		panel1.add(panel2, BorderLayout.CENTER);
		tabbedPane.add(panel1, DATATRANSFER);
		
		/* add tabbed pane to GUI */
		add(tabbedPane, BorderLayout.CENTER);
	}

    public void itemStateChanged(ItemEvent e) {
        String device;
        int i;
        if (e.getSource() == devices) {
            device = (String) e.getItem();
            for (i=0; i<deviceNames.length; i++) {
                if (deviceNames[i].equals(device)) {
                    typePanel.removeAll();
                    typePanel.add(deviceIcons[i], BorderLayout.CENTER);
                    typePanel.validate();
                    invalidate();
                    validate();
                    break;
                }
            }
        } else
            card.show(tabbedPane, (String) e.getItem());
    }


//		/* check flag in Relate to find out if the physic dim glue should be drawn */
//		this.displayPhysicDim = Relate.getRelate().isDisplayPhysicDim();
//		JComponent[] c = new JComponent[3];
//		Dimension dim = new Dimension(80,80);
//		int i;
//		
//		setLayout(new BorderLayout());
//
//		c[0] = createFirstPanel(path);
//		add(c[0], BorderLayout.NORTH);
//		
//		c[1] = createSecondPanel(path);
//		add(c[1], BorderLayout.CENTER);
//
//		c[2] = createThirdPanel(portList);
//		add(c[2], BorderLayout.SOUTH);
//
//	}
//	/**
//	 ** Create a lower panel: port configuration, save settings, Ok and Cancel.
//	 **/
//	protected JPanel createThirdPanel(String[] portList) {
//		JPanel p = new JPanel(new BorderLayout());
//		JPanel portPanel = new JPanel(new FlowLayout());
//		JPanel fileTransferPanel = new JPanel(new FlowLayout());
//		Box b3 = Box.createHorizontalBox();
//		Box b1 = Box.createVerticalBox();
//		Box b2 = Box.createVerticalBox();
//		Box tempBox = Box.createHorizontalBox();
//		JLabel defaultFolder = new JLabel("Default folder");
//
//		portPanel.setBorder(new TitledBorder("Relate Dongle port"));
//		if (portList == null) {
//			portList = new String[1];
//			portList[0] = "[disabled]";
//		}
//		availablePorts = new JComboBox(portList);
//		portPanel.add(availablePorts);
//
//		b2.setBorder(new TitledBorder("Files transfer settings"));
//		defaultSavingLocation = new JTextField(new JFileChooser().getCurrentDirectory().getName()) ;
//		homeFolder = defaultSavingLocation.getText() ;
//		browse = new JButton("Browse") ;
//		tempBox.add(defaultFolder) ;
//		tempBox.add(Box.createHorizontalGlue());
//		tempBox.add(Box.createHorizontalStrut(20));
//		tempBox.add(defaultSavingLocation) ;
//		tempBox.add(browse) ;
//		b2.add(tempBox) ;
//		saveFiles = new JCheckBox("Always save files");
//		b2.add(saveFiles) ;
//		
//		saveSettings = new JCheckBox("Save settings");
//		b3.add(saveSettings);
//		b3.add(Box.createHorizontalGlue());
//		b3.add(Box.createHorizontalStrut(30));
//		
//		considerPhysicDim = new JCheckBox("Scale icons");
//		b3.add(considerPhysicDim);
//		b3.add(Box.createHorizontalGlue());
//		b3.add(Box.createHorizontalStrut(30));
//		
//		cancel = new JButton("Cancel");
//		ok = new JButton("OK") ;
//		b3.add(cancel);
//		b3.add(ok);
//		b1.add(portPanel);
//		b1.add(b2);
//		b1.add(b3);
//		
//		p.add(b1) ;
//		return p;
//	}
//	
//	/**
//	 ** Create the panel for configuring the device type and the
//	 ** orientation of the dongle (i.e. the side where it is plugged in)
//	 ** @param  path     path to image files (e.g. desktop)
//	 ** @param  portList list of available ports
//	 ** @return the panel
//	 */
//	protected JPanel createSecondPanel(String path) {
//	    JPanel p = new JPanel(new BorderLayout());
//	    Box b1, b2, dimPart, donglePart, tmp;
//	    Box topSection, middleSection, buttomSection, innerSection;
//	    JLabel devWidthLabel, devHeightLabel;	   
//		int i;
//
//		devices = new JComboBox(deviceNames);
//
//		p.setBorder(new TitledBorder("Device type and dongle location"));
//		deviceIcons = new ImageIcon[4];
//		deviceIcons[0] = new ImageIcon(path + DESKTOP);
//		deviceIcons[1] = new ImageIcon(path + PDA);
//		deviceIcons[2] = new ImageIcon(path + LAPTOP);
//		deviceIcons[3] = new ImageIcon(path + PROJECTOR);
//		JLabel empty1 = new JLabel() ;
//		JLabel empty3 = new JLabel() ;
//		JLabel empty7 = new JLabel() ;
//		JLabel empty9 = new JLabel() ;
//		if(displayPhysicDim){
//		devWidth = new JTextField("0", 2);
//		devHeight = new JTextField("0", 2);
//		}
//		
//		
//		centerPanel = new JPanel(new BorderLayout());
//		centerLabel = new JLabel(deviceIcons[0]);
//		
//		if (displayPhysicDim) {
//			devWidthLabel = new JLabel("Device width (mm)");
//			devHeightLabel = new JLabel("Device height (mm)");
//			dimPart = Box.createHorizontalBox();
//			dimPart.add(Box.createHorizontalStrut(5));
//			dimPart.add(devWidthLabel);
//			dimPart.add(Box.createHorizontalStrut(5));
//			dimPart.add(devWidth);
//			dimPart.add(Box.createHorizontalStrut(5));
//			dimPart.add(devHeightLabel);
//			dimPart.add(Box.createHorizontalStrut(5));
//			dimPart.add(devHeight);
//			dimPart.add(Box.createHorizontalStrut(5));
//
//			p.add(dimPart, BorderLayout.NORTH);
//			p.add(Box.createVerticalStrut(10));
//		}
//		
//		centerLabel.setHorizontalAlignment(SwingConstants.CENTER);
//		centerLabel.setVerticalAlignment(SwingConstants.CENTER);
//		centerPanel.add(devices, BorderLayout.NORTH);
//		centerPanel.add(centerLabel);
//						
//		down = new JToggleButton(new ImageIcon(path + RELATE_FRONT));
//		down.setHorizontalAlignment(SwingConstants.CENTER);
//		down.setVerticalAlignment(SwingConstants.CENTER);
//		down.setMaximumSize(new Dimension(80, 80));
//		right = new JToggleButton(new ImageIcon(path + RELATE_RIGHT));
//		right.setHorizontalAlignment(SwingConstants.CENTER);
//		right.setVerticalAlignment(SwingConstants.CENTER);
//		right.setMaximumSize(new Dimension(80, 80));
//		up = new JToggleButton(new ImageIcon(path + RELATE_BACK));
//		up.setHorizontalAlignment(SwingConstants.CENTER);
//		up.setVerticalAlignment(SwingConstants.CENTER);
//		up.setMaximumSize(new Dimension(80, 80));
//		left = new JToggleButton(new ImageIcon(path + RELATE_LEFT));
//		left.setHorizontalAlignment(SwingConstants.CENTER);
//		left.setVerticalAlignment(SwingConstants.CENTER);
//		left.setMaximumSize(new Dimension(80, 80));
//		
//		if(displayPhysicDim){
//		sliderTop = new JSlider(JSlider.HORIZONTAL, 0, 10, 0);
//		sliderButtom = new JSlider(JSlider.HORIZONTAL, 0, 10, 0);
//		sliderLeft = new JSlider(JSlider.VERTICAL, 0, 10, 0);
//		sliderRight =new JSlider(JSlider.VERTICAL, 0, 10, 0);
//		}
//		
//		innerSection = Box.createHorizontalBox();
//		if (displayPhysicDim)
//			innerSection.add(sliderLeft, BorderLayout.WEST);
//		
//		tmp = Box.createVerticalBox();
//		
//		if (displayPhysicDim)
//			tmp.add(sliderTop, BorderLayout.NORTH);
//		tmp.add(centerPanel, BorderLayout.CENTER);
//		if (displayPhysicDim)
//			tmp.add(sliderButtom, BorderLayout.SOUTH);
//
//		innerSection.add(tmp, BorderLayout.CENTER);
//		if (displayPhysicDim)
//			innerSection.add(sliderRight, BorderLayout.EAST);
//				
//		topSection = Box.createHorizontalBox();
//		topSection.add(Box.createHorizontalGlue());
//		topSection.add(up);
//		topSection.add(Box.createHorizontalGlue());
//		
//		donglePart = Box.createVerticalBox();
//		donglePart.add(topSection, BorderLayout.NORTH);
//		
//		
//		middleSection = Box.createHorizontalBox();
//		middleSection.add(left);
//		middleSection.add(innerSection);
//		middleSection.add(right);
//		
//		donglePart.add(middleSection, BorderLayout.CENTER);
//		
//		buttomSection = Box.createHorizontalBox();
//		buttomSection.add(Box.createHorizontalGlue());
//		buttomSection.add(down);
//		buttomSection.add(Box.createHorizontalGlue());
//		
//		donglePart.add(buttomSection, BorderLayout.SOUTH);
//		
//		p.add(donglePart, BorderLayout.SOUTH);
//
//		
//		return p;
//	}
//	
//	/**
//	 ** Creates the host information panel (user/device name; ipAddress)
//	 */
//	protected JPanel createFirstPanel(String path) {
//		String tmp;
//		JPanel p = new JPanel();
//		Box b = Box.createVerticalBox();
//		Box line;
//		JLabel[] l = new JLabel[3];
//		int i;
//		Dimension dim, dim2;
//
//		userName = new JTextField(System.getProperties().getProperty("user.name"),15) ;
//		try {
//			tmp = InetAddress.getLocalHost().getHostName();
//		} catch (UnknownHostException e) {
//			tmp = "";
//		}
//		deviceName = new JTextField(tmp,15) ;
//		try {
//			tmp = InetAddress.getLocalHost().getHostAddress();
//		} catch (UnknownHostException e) {
//			tmp = "";
//		}
//		ipAddress = new JTextField(tmp,15) ;
//		
//		l[0] = new JLabel("User name ");
//		l[1] = new JLabel("Host name ");
//		l[2] = new JLabel("IP address ");
//		dim = new Dimension(90, 20);
//		for (i=0; i<3; i++) {
//		    l[i].setPreferredSize(dim);
//		    l[i].setMaximumSize(dim);
//		    l[i].setMinimumSize(dim);
//		}
//
//		p.setBorder(new TitledBorder("Local host information"));
//		line = Box.createHorizontalBox();
//		line.add(l[0]);
//		line.add(Box.createHorizontalGlue());
//		line.add(userName);
//		b.add(line);
//		line = Box.createHorizontalBox();
//		line.add(l[1]);
//		line.add(Box.createHorizontalGlue());
//		line.add(deviceName);
//		b.add(line);
//		line = Box.createHorizontalBox();
//		line.add(l[2]);
//		line.add(Box.createHorizontalGlue());
//		line.add(ipAddress);
//		b.add(line);
//		p.add(b);
//		return p;
//	}
	
	
	public static void main(String[] args) {
		ControlPanel scroll = null;
		Frame frame = new Frame();
		String[] portList = {"COM1","COM2","COM3","COM4","LPT1","LPT2"} ;
		if (args == null)
			scroll = new ControlPanel("img/",portList);
		else if (args.length == 0)
			scroll = new ControlPanel("img/",portList);
		else
			scroll = new ControlPanel(args[0],portList);
		frame.setLayout(new BorderLayout());
		frame.add(scroll, BorderLayout.CENTER);
		frame.pack();
		frame.setBounds(40,40, 480, 640);
		frame.show();
	}	
	
}