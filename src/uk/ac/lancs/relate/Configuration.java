package uk.ac.lancs.relate;

import java.awt.*;
import java.awt.event.*;
import java.net.InetAddress;
import java.net.UnknownHostException;

//import javax.swing.*;

import java.io.*;

/**
 * This class provides access to the current configuration,
 * both to the system and to the user through an (AWT based)
 * GUI.  It also needs a major rewrite.
 * @author Henoc AGBOTA, Oliver Baecker, CK
 */
public class Configuration extends Frame {
	
	public int side;
	public String port;
	public String deviceName;
	public String deviceType;
	public String userName;
	public String ipAddress;
	public int devWidth;
	public int devHeight;
	public int dongleOffset;
	ControlPanel cp;
	public Frame frame;
	public boolean saveSettings = false ;
	public String defaultFolder ;
		
	public boolean okClicked = false;
	
	public final static int BACK = 1;
	public final static int RIGHT = 2;
	public final static int FRONT = 3;
	public final static int LEFT = 4;
	private final static int HOST_INFO_LENGTH = 51 ;
	
	public final static int[] SIDES = {BACK, RIGHT, FRONT, LEFT};
	public final static String[] SIDE_NAMES = {"BACK", "RIGHT", "FRONT", "LEFT"};
	public byte[] hostInfo = new byte[HOST_INFO_LENGTH] ;
	
	/** decides whether the panel will display options for a more 
	 * accurate calibration of the dongle position
	 */
	public boolean displayPhysicalDim = true;
	
	public Configuration(String path, SerialConnector connector, boolean show) {
		super();
		this.devWidth = 0;
		this.devHeight = 0;
		this.dongleOffset = 0;
		this.side = BACK;
		this.port = "";
		this.deviceName = "";
		this.deviceType = "";
		this.userName = "";
		this.ipAddress = "";
		String[] portList = connector.getPorts();
//		this.displayPhysicalDim = Relate.getRelate().isDisplayPhysicDim();
		cp = new ControlPanel(path + File.separator, portList);
		/* handler for identity tab */
		UserFieldHandler userHandler = new UserFieldHandler();
		cp.userName.addActionListener(userHandler);
		DeviceFieldHandler deviceHandler = new DeviceFieldHandler();
		cp.deviceName.addActionListener(deviceHandler);
		IPFieldHandler ipHandler = new IPFieldHandler();
		cp.ipAddress.addActionListener(ipHandler);
		/* handler for device tab */
		PortsBoxHandler portHandler = new PortsBoxHandler();
		cp.availablePorts.addItemListener(portHandler);
		DeviceBoxHandler deviceBoxHandler = new DeviceBoxHandler();
		cp.devices.addItemListener(deviceBoxHandler);
		/* general button handler */
		JToggleButtonHandler buttonHandler = new JToggleButtonHandler();
		cp.ok.addActionListener(buttonHandler);
		cp.cancel.addActionListener(buttonHandler);		
		/* data transfer handler */
		cp.browse.addActionListener(buttonHandler);

		frame = new Frame("Relate Configuration");
		frame.setLayout(new BorderLayout());
		frame.add(cp, BorderLayout.CENTER);
		frame.pack();
		//frame.setBounds(0, 20, 460, 600);
		if (show)
		    frame.show();
	}
	
    /**
     * @return Returns the frame.
     */
    public Frame getFrame() {
        return frame;
    }
    
	/**
	 ** Save current settings if (save settings box is ticked); otherwise
	 ** delete any saved settings.
	 ** @param configFile 
	 *					the file to save settings to (i.e. ~/relate.cfg)
	 *  @param considerPhysicDim
	 * 					flag indicating whether the config file contains info
	 * 					about the physical device 
	 */
	public void saveSettings(String configFile, boolean considerPhysicDim) {
	    File f;
	    FileWriter out;
	    String config;
	    config = "";
	    
	    try {
	        if (cp.saveSettings.getState()) {
	        	config = cp.userName.getText() + "\n";
	        	config += cp.deviceName.getText() + "\n";
	        	config += cp.ipAddress.getText() + "\n";
	        	
	        	/* we always consider the physical dimensions of the host device */
	        	config += "true" + "\n";
        		
				config += cp.devWidth.getText() + "\n";
				config += cp.devHeight.getText() + "\n";					
			
				config += (String) cp.availablePorts.getSelectedItem()
							+ "\n";

	            config += (String) cp.devices.getSelectedItem() + "\n";
	            config += cp.dongleSide.getSelectedItem() + "\n";
	            config += getDongleOffset() + "\n";
	        	config += cp.defaultSavingLocation.getText() + "\n";
	        	config += cp.saveFiles.getState() + "\n";
	            f = new File(configFile);
	            out = new FileWriter(f);
	            out.write(config);
	            out.flush();
	            out.close();
	        } else {
	            /* delete existing file */
	            f = new File(configFile);
	            f.delete();
	        }
	    } catch (Exception e) {
	        e.printStackTrace();
	    }
	}
	
	/**Returns a dongle's offset from an edge of the machine 
	 * @return
	 * 		a dongle's offset (expressed in 
	 * 		tenth of the corresponding machine side ????)
	 */		
	public int getDongleOffset() {
	    String selection = cp.dongleSide.getSelectedItem();
	    int i = 0;
	    if (SIDE_NAMES[0].equals(selection) || SIDE_NAMES[2].equals(selection)) {
	        // back or front
	        try {
	            i = Integer.parseInt(cp.devOffsetFromLeft.getText());
	        } catch (NumberFormatException e) {
	            i = 0;
	        }
	        return i;
	    } else {
	        // left or right
	        // back or front
	        try {
	            i = Integer.parseInt(cp.devOffsetFromBack.getText());
	        } catch (NumberFormatException e) {
	            i = 0;
	        }
	        return i;
	    }	
	}
	
	/**Returns a dongle's x coordinate (assuming the origin is 
	 * in top left corner of the machine)
	 * @return
	 * 		the x coordinate of a dongle in mm
	 */		
	public int getDongleLocationX() {
		int dongleOffset = getDongleOffset();
		switch (side) {
		case BACK:
			return dongleOffset;
		case RIGHT:
			return getDevWidth();
		case FRONT:
			return dongleOffset;
	    case LEFT:
	    	return 0;
		default:
			return -1;

		}
	}
	
	/**Returns a dongle's y coordinate (assuming the origin is 
	 * in top left corner of the machine)
	 * @return
	 * 		the y coordinate of a dongle in mm
	 */	
	public int getDongleLocationY() {
		int dongleOffset = getDongleOffset();
		switch (side) {
		case BACK:
			return 0;
		case RIGHT:
			return dongleOffset;
		case FRONT:
			return getDevHeight();
	    case LEFT:
	    	return dongleOffset;
		default:
			return -1;

		}
	}
	
//    /**Returns a dongle's x coordinate (assuming the origin is 
//     * in top left corner of the machine)
//     * @return
//     *      the x coordinate of a dongle in mm
//     */     
//    public int getDongleLocationX() {
//        int dongleOffset = getDongleOffset();
//        switch (side) {
//        case 1:
//            return getDevWidth() * dongleOffset / 10;
//        case 2:
//            return getDevWidth();
//        case 3:
//            return getDevWidth() * dongleOffset / 10;
//        case 4:
//            return 0;
//        default:
//            return -1;
//
//        }
//    }
//    
//    /**Returns a dongle's y coordinate (assuming the origin is 
//     * in top left corner of the machine)
//     * @return
//     *      the y coordinate of a dongle in mm
//     */ 
//    public int getDongleLocationY() {
//        int dongleOffset = getDongleOffset();
//        switch (side) {
//        case 1:
//            return 0;
//        case 2:
//            return getDevHeight() - (getDevHeight() * dongleOffset / 10);
//        case 3:
//            return getDevHeight();
//        case 4:
//            return getDevHeight() - (getDevHeight() * dongleOffset / 10);
//        default:
//            return -1;
//
//        }
//    }

    /**
	 * Show the configuration frame.
	 */
	public void showGUI() {
	    frame.show();
	}
	
	/**
	 * Hide the configuration frame.
	 */
	public void hideGUI() {
	    frame.hide();
	}
	
	/**
	 ** Load current settings (if they exist) and set fields accordingly.
	 */
	public boolean loadSettings(String configFile) {
		boolean considerPhysicDim = false;
		boolean success = false;
	    File f;
	    BufferedReader in;
	    String config;
	    int i;
	    
	    try {
	        try {
	            f = new File(configFile);
	            in = new BufferedReader(new FileReader(f));

	            config = in.readLine();
	            cp.userName.setText(config) ;
	            config = in.readLine();
	            cp.deviceName.setText(config) ;
	            config = in.readLine();
	            cp.ipAddress.setText(config) ;
	            
	            config = in.readLine();	            
	            if (config.equals("true")){
					cp.considerPhysicalDim.setState(true);
					considerPhysicDim = true;
	            }
	            else if (config.equals("false")){
					cp.considerPhysicalDim.setState(false);
					considerPhysicDim = false;
				}
	            
				//if (considerPhysicDim){
					config = in.readLine();
					cp.devWidth.setText(config);

					config = in.readLine();
					cp.devHeight.setText(config);
				//}
	            
	            config = in.readLine();
	            for (i=0; i<cp.availablePorts.getItemCount(); i++) {
	                if (config.equals((String) cp.availablePorts.getItem(i))) {
	                    /* found port */
	                    cp.availablePorts.select(i);
	                    success = true;
	                    break;
	                }
	            }
	            config = in.readLine();
	            for (i=0; i<cp.devices.getItemCount(); i++) {
	                if (config.equals((String) cp.devices.getItem(i))) {
	                    /* found device type */
	                    cp.devices.select(i);
                        cp.typePanel.removeAll();
	        			cp.typePanel.add(cp.deviceIcons[i], BorderLayout.CENTER);
 	        			break;
	                }
	            }
	            config = in.readLine();
	            String dongleSide = config;
	            for (i=0; i<SIDE_NAMES.length; i++) {
	                if (SIDE_NAMES[i].equals(dongleSide)) {
	                    cp.dongleSide.select(i);
                        break;
	                }
	            }
	            
	            if(considerPhysicDim){
	                config = in.readLine();
		            if (SIDE_NAMES[0].equals(dongleSide) || SIDE_NAMES[2].equals(dongleSide)) {
		                // up or down
		                cp.devOffsetFromLeft.setText(config);
		                cp.devOffsetFromBack.setText("");
		            } else {
		                // left or right
		                cp.devOffsetFromLeft.setText("");
		                cp.devOffsetFromBack.setText(config);
		            }
	            }
	            
	            config = in.readLine();
	            cp.defaultSavingLocation.setText(config) ;
	            
	            config = in.readLine();	            
	            if (config.equals("true"))
	                cp.saveFiles.setState(true);
	            
	            cp.saveSettings.setState(true);
                success = true;
	        } catch (FileNotFoundException fe) {
	            success = false;
	        }
	    } catch (Exception e) {
	        e.printStackTrace();
	    }
	    return success;
	}

	private class UserFieldHandler implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			Configuration.this.userName = Configuration.this.cp.userName
			.getText();
		}
	}
	
	private class DeviceFieldHandler implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			Configuration.this.deviceName = Configuration.this.cp.deviceName
			.getText();
		}
	}
	
	private class IPFieldHandler implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			Configuration.this.ipAddress = Configuration.this.cp.ipAddress
			.getText();
		}
	}
	
	private class DevWidthFieldHandler implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			Configuration.this.devWidth = getDevWidth();
		}
	}
	
	private class DevHeightFieldHandler implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			Configuration.this.devHeight = getDevHeight();
		}
	}
	
	private class DongleOffsetSliderHandler implements ActionListener {
		public void actionPerformed(ActionEvent e) {
		}
	}
	
	private class PortsBoxHandler implements ItemListener {
		public void itemStateChanged(ItemEvent e) {
			Configuration.this.port = (String) Configuration.this.cp.availablePorts
			.getSelectedItem();
		}
	}
	
	private class DeviceBoxHandler implements ItemListener {
		public void itemStateChanged(ItemEvent e) {
			String deviceType = (String) Configuration.this.cp.devices.getSelectedItem();
			int i, pos;
		    //System.out.println(e + ", " + deviceType);
			Configuration.this.deviceType = deviceType;
			for (i = 0; i < Configuration.this.cp.deviceNames.length; i++) {
				if (Configuration.this.cp.deviceNames[i].equals(deviceType)) {
					pos = i;
					break;
				}
			}
			ImageIcon icon = Configuration.this.cp.deviceIcons[i];
			Configuration.this.cp.typePanel.removeAll();
			Configuration.this.cp.typePanel.add(icon, BorderLayout.CENTER);
			Configuration.this.cp.typePanel.invalidate();
			//Configuration.this.cp.repaint();
		}
	}

	private class JToggleButtonHandler implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			int res ;
			File selectedFolder ;
			if (e.getSource() == Configuration.this.cp.ok) {
			    if (displayPhysicalDim){
			        Configuration.this.devWidth = Configuration.this.getDevWidth();
			        Configuration.this.devHeight = Configuration.this.getDevHeight();
			        Configuration.this.dongleOffset = Configuration.this.getDongleOffset();
			    }
				Configuration.this.side = Configuration.this.getDeviceSide();
				Configuration.this.port = Configuration.this .getDevicePortNumber();
				Configuration.this.deviceName = Configuration.this.getDeviceName();
				Configuration.this.deviceType = Configuration.this.getDeviceType();
				Configuration.this.userName = Configuration.this.getUserName();
				Configuration.this.ipAddress = Configuration.this.getIpAddress();
				okClicked = true;
				//System.out.println(Configuration.this.toString()) ;
				//System.exit(0) ;
			}
			if (e.getSource() == Configuration.this.cp.browse){
				FileDialog fileChooser = new FileDialog(Configuration.this, 
				        								"Choose default folder (inbox)",
				        								FileDialog.LOAD);
				fileChooser.setModal(true);
				fileChooser.show();
				if (fileChooser.getDirectory() != null)
				    cp.defaultSavingLocation.setText(fileChooser.getDirectory());
			}
			if (e.getSource() == Configuration.this.cp.cancel)
			    System.exit(0);
		}
	}
	
	/**
	 * * Check wether the user specified all fields *
	 * 
	 * @return true if all fields are set, false otherwise
	 */
	public boolean fullySpecified() {
		return ((getUserName() != "") && (getDeviceName() != "")
				&& (getDeviceType() != "") && (getDevicePortNumber() != "") && okClicked);
	}
	
	public String toString() {
		String conf = null;
		conf = "USERNAME: " + this.getUserName() + "\n";
		conf += "DEVICE NAME: " + this.getDeviceName() + "\n";
		conf += "DEVICE TYPE: " + this.getDeviceType() + "\n";
		conf += "IP ADDRESS: " + this.getIpAddress() + "\n";
		conf += "DEVICE SIDE: " + this.getDeviceSide() + "\n";
		conf += "PORT NUMBER: " + this.getDevicePortNumber() + "\n";
		return conf;
	}
	
	public int getDeviceSide() {
        int result = -1, i;
        String side = (String) this.cp.dongleSide.getSelectedItem();
        for (i=0; i<SIDE_NAMES.length; i++) {
            if (SIDE_NAMES[i].equals(side)) {
                result = SIDES[i];
                break;
            }
        }
		return result;
	}

	public boolean getSaveSettings() {
		return this.cp.saveSettings.getState() ;
	}
	
	public boolean getSaveFiles() {
		return this.cp.saveFiles.getState() ;
	}

	public String getDefaultFolder() {
		if(cp.defaultSavingLocation.getText() == "")
			return (String) cp.homeFolder ;
		else
			return (String) this.cp.defaultSavingLocation.getText() ;
	}
	
	public String getDevicePortNumber() {
		return (String) this.cp.availablePorts.getSelectedItem();
	}
	
	public String getDeviceType() {
	    return (String) this.cp.devices.getSelectedItem();
	}
	
	public String getDeviceName() {
		return this.cp.deviceName.getText();
	}
	
	public String getUserName() {
	    return this.cp.userName.getText();
	}
	
	public String getIpAddress() {
		return this.cp.ipAddress.getText();
	}
	
	public int getDevWidth(){
		if (cp.devWidth != null){
			int width;
			try {
				width = Integer.parseInt(this.cp.devWidth.getText());
			} catch (NumberFormatException e) {
				return 0;
			}
			return width;
		}
		else
			return 0;
	}
	
	public int getDevHeight(){
		if (cp.devHeight != null){
			int height;
			try {
				height = Integer.parseInt(this.cp.devHeight.getText());
			} catch (NumberFormatException e) {
				return 0;
			}
			return height;
		}
		else
			return 0;
	}
	
	public boolean isConsiderPhysicDim(){
		return this.cp.considerPhysicalDim.getState();
	}
	
	public void setConsiderPhysicDim(boolean bool){
		this.cp.considerPhysicalDim.setState(bool);
	}
	
	public static void printByteArray(byte[] a) {
		for (int i = 0; i < a.length; i++){
			System.out.println("["+i+"]: "+a[i]+"\n") ;
		}
	}
	
	public void reSizeString(String info, int size, int pos) {
		byte[] bytes = null; 
		if(info.length() > size) info = info.substring(0,size-1) ;
		bytes = info.getBytes() ;
		for(int i = pos; i < pos+size; i++){
			if((i-pos) < (bytes.length))
				hostInfo[i] = bytes[i-pos] ;
			else
				hostInfo[i] = 0 ;
		}
	}
	
	public byte byteVal(String dType) {
		byte b = (byte)255 ;
		if(dType.equals("desktop") )
			b = (byte)1 ;
		if(dType.equals("laptop") )
			b = (byte)2 ;
		if(dType.equals("PDA") )
			b = (byte)3 ;
		if(dType.equals("projector") )
			b = (byte)4 ;
		return b ;
	}
	
	
	public byte[] getFormat() {
		byte[] bytes = {}; 
		int i;
		hostInfo[0] = -1 ;
		try {
			bytes = InetAddress.getByName(this.getIpAddress()).getAddress() ;
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		for(i = 1 ; i < 9; i++) {
			if(i < bytes.length+1){
				hostInfo[i] = bytes[i-1] ;
			}
			else
				hostInfo[i] = 0 ;
			//System.out.println("i:"+i+",hostInfo[i]: "+hostInfo[i]+",bytes[i]: "+bytes[i-1]+"\n") ;
		}
		this.reSizeString(this.getUserName(),20,9) ;
		hostInfo[29] = this.byteVal(this.getDeviceType()) ;
		this.reSizeString(this.getDeviceName(),20,30) ;
		hostInfo[50] = (byte)(this.getDeviceSide()) ;
		return hostInfo ;
	}
	
	public static void main(String[] args) {
	}	

}