package uk.ac.lancs.relate;

import java.net.InetAddress;
import java.io.Serializable;
import java.util.*;

/**
 ** This class models devices - a combination of a relate dongle
 ** and the corresponding host.  It encapsulates information about
 ** the user, the IP address, its connectivity etc.
 **/
public class Device implements Cloneable, Serializable {

	/** number of permissible IDs for relate objects [0..256] */
	public final static int ID_RANGE = 256;

    public final static int UNDEFINED_ID = -1;
    
	/** ID of the relate dongle attached to a device */
	protected int id = UNDEFINED_ID;
	/** time stamp: when was the most recent message from this device
	 *  received? */
	protected long timeStamp;
	/** user name */
	protected String userName;
	/** machine name */
	protected String machineName;
	/** ip address */
	protected String ipAddress;
	/** whether or not device is reachable over IP */
	protected Boolean reachable;
	/** device type */
	protected String type;
	/** side of device to which the relate dongle is attached */
	protected int side;
	/** dimension of the device in millimeters */
	protected int width, length;
	/** location of the dongle in millimeters from the top left corner as
	 *  defined by width and length */
	protected int dongleLocationX = 0, dongleLocationY = 0;
	/** Calibration info related to all the measurements taken by this device */
	protected Calibration calibration;
	
	/** 
	 * Default constructor
	 * @param id          the ID of the relate dongle attached
	 * @param timeStamp   last time we heard from the device (or -1)
	 * @param userName    name of the user using the device (or null)
	 * @param machineName name of the host (or null)
	 * @param ipAddress   IP address of host (or null)
	 * @param type        the type of the device
	 * @param side        to which side of the device is the relate dongle attached
	 *                    (see @see uk.ac.lancs.relate.Configuration)
	 * @param reachable   whether the device is reachable over IP
	 */
	public Device(int id, long timeStamp, String userName, String machineName,
			String ipAddress, String type, int side, Boolean reachable) {
		super();
		this.id = id;
		this.timeStamp = timeStamp;
		this.userName = userName;
		this.machineName = machineName;
		this.ipAddress = ipAddress;
		this.type = type;
		this.side = side;
		this.width = -1;
		this.length = -1;
		this.dongleLocationX = -1;
		this.dongleLocationY = -1;
		this.reachable = reachable;
		this.calibration = null ;
	}

	public Device(int id, long timeStamp, String userName, String machineName,
			String ipAddress, String type, int side, Boolean reachable, 
			Calibration calibration) {
		super();
		this.id = id;
		this.timeStamp = timeStamp;
		this.userName = userName;
		this.machineName = machineName;
		this.ipAddress = ipAddress;
		this.type = type;
		this.side = side;
		this.width = -1;
		this.length = -1;
		this.dongleLocationX = -1;
		this.dongleLocationY = -1;
		this.reachable = reachable;
		this.calibration = calibration ;
	}
	
    /**
     * @param id
     * @param timeStamp
     * @param userName
     * @param machineName
     * @param ipAddress
     * @param reachable
     * @param calibration
     * @param type
     * @param side
     * @param width
     * @param length
     * @param dongleLocationX
     * @param dongleLocationY
     */
    public Device(int id, long timeStamp, String userName, String machineName,
            String ipAddress, String type, int side, Boolean reachable,
            Calibration calibration,
            int width, int length, int dongleLocationX, int dongleLocationY) {
        super();
        this.id = id;
        this.timeStamp = timeStamp;
        this.userName = userName;
        this.machineName = machineName;
        this.ipAddress = ipAddress;
        this.reachable = reachable;
        this.type = type;
        this.side = side;
        this.width = width;
        this.length = length;
        this.dongleLocationX = dongleLocationX;
        this.dongleLocationY = dongleLocationY;
        this.calibration = calibration;
    }
    
    /**
     * @param id
     * @param timeStamp
     * @param userName
     * @param machineName
     * @param ipAddress
     * @param type
     * @param side
     * @param reachable
     * @param width
     * @param length
     * @param dongleLocationX
     * @param dongleLocationY
     */
    public Device(int id, long timeStamp, String userName, String machineName,
            String ipAddress, String type, int side, Boolean reachable,            
            int width, int length, int dongleLocationX, int dongleLocationY) {
        super();
        this.id = id;
        this.timeStamp = timeStamp;
        this.userName = userName;
        this.machineName = machineName;
        this.ipAddress = ipAddress;
        this.reachable = reachable;
        this.type = type;
        this.side = side;
        this.width = width;
        this.length = length;
        this.dongleLocationX = dongleLocationX;
        this.dongleLocationY = dongleLocationY;
        this.calibration = null;
    }

    public Device(int id) {
    	super();
    	this.id = id;
    	this.timeStamp = -1;
    	this.userName = null;
    	this.machineName = null;
    	this.ipAddress = null;
    	this.reachable = null;
    	this.type = null;
    	this.side = -1;
    	this.width = -1;
    	this.length = -1;
    	this.dongleLocationX = -1;
    	this.dongleLocationY = -1;
    	this.calibration = null;
    }

	/**
	 * @return Returns the id.
	 */
	public int getId() {
		return id;
	}
	/**
	 * @param id The id to set.
	 */
	public void setId(int id) {
		this.id = id;
	}
	/**
	 * @return Returns the ipAddress as a string.
	 */
	public String getIpAddress() {
		return ipAddress;
	}
	/** Accessor for IP address that returns a valid INetAddress (or null) */
    public InetAddress getInetAddress() {
    	StringTokenizer st = null;
    	InetAddress adr = null;
    	byte[] nums = null;
    	int i;
    	
    	if (ipAddress != null) {
    		try {
    			if (ipAddress.indexOf('.') > -1) {
    				/* assume that ip address is given as a number */
    				st = new StringTokenizer(ipAddress, ".");
    				if (st.countTokens() > 0) {
    					nums = new byte[st.countTokens()];
    					for (i=0; i<nums.length; i++) 
    						nums[i] = (byte) Integer.parseInt(st.nextToken());
    					adr = InetAddress.getByAddress(nums);
    				}
    			}
     		} catch (Exception e) {
     		    try {
      		        /* assume that ip address is given as a string/name */
      		        adr = InetAddress.getByName(ipAddress);
      		    } catch (Exception ex) {
        		    ex.printStackTrace();
        			adr = null;
        		}
    		} catch (Error err) {
    		    /* catch error for J2ME */
                try {
                    /* assume that ip address is given as a string/name */
                    adr = InetAddress.getByName(ipAddress);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    adr = null;
                }
            }
      		if (adr == null) {
      		    try {
      		        /* assume that ip address is given as a string/name */
      		        adr = InetAddress.getByName(ipAddress);
      		    } catch (Exception e) {
        		    e.printStackTrace();
        			adr = null;
        		}
			}
    	}
    	return adr;
    }

	/**
	 * @return Returns this device calibration info as a Calibration object.
	 */
	public Calibration getCalibration() {
		return calibration;
	}

	/**
	 * @param calibration The Calibration to set.
	 */
	public void setCalibration(Calibration calibration) {
		this.calibration = calibration;
	}
	
    /** testing */
    public static void main(String[] args) {
        Device dev = new Device(3, System.currentTimeMillis(), "test", "test",
                null, "laptop", 0, null);
	    String test = "[Device: id=7,userName=me,machineName=my computer,ipAddress=some.where.org,]";
	    int i;
        
	    if (args != null) {
            for (i=0; i<args.length; i++) {
                dev.setIpAddress(args[i]);
                System.out.println(args[i] + " -> " + dev.getInetAddress());
            }
        }

	    dev = fromString(test);
	    System.out.println(dev);
	    
	    test = "[Device: id=7,userName=me,machineName=my computer,ipAddress=some.where.org,length=340,width=420,dongleLocationX=10,dongleLocationY=70,]";
	    System.out.println(fromString(test));
    }

	/**
	 * @param ipAddress The ipAddress to set.
	 */
	public void setIpAddress(String ipAddress) {
		this.ipAddress = ipAddress;
	}
	/**
	 * @return Returns the machineName.
	 */
	public String getMachineName() {
		return machineName;
	}
	/**
	 * @param machineName The machineName to set.
	 */
	public void setMachineName(String machineName) {
		this.machineName = machineName;
	}
	/**
	 * @return Returns the timeStamp.
	 */
	public long getTimeStamp() {
		return timeStamp;
	}
	/**
	 * @param timeStamp The timeStamp to set.
	 */
	public void setTimeStamp(long timeStamp) {
		this.timeStamp = timeStamp;
	}
	/**
	 * @return Returns the userName.
	 */
	public String getUserName() {
		return userName;
	}
	/**
	 * @param userName The userName to set.
	 */
	public void setUserName(String userName) {
		this.userName = userName;
	}
	
	/**
     * @return Returns the side.
     */
    public int getSide() {
        return side;
    }
    /**
     * @param side The side to set.
     */
    public void setSide(int side) {
        this.side = side;
    }
    /**
     * @return Returns the type.
     */
    public String getType() {
        return type;
    }
    /**
     * @param type The type to set.
     */
    public void setType(String type) {
        this.type = type;
    }  
	/**
	 * @return Returns the reachable.
	 */
	public Boolean getReachable() {
		return reachable;
	}
	/**
	 * @param reachable The reachable to set.
	 */
	public void setReachable(Boolean reachable) {
		this.reachable = reachable;
	}

    /**
     * @return Returns the dongleLocationX in mm accuracy.
     */
    public int getDongleLocationX() {
        return dongleLocationX;
    }
    /**
     * @param dongleLocationX The dongleLocationX to set in mm accuracy.
     */
    public void setDongleLocationX(int dongleLocationX) {
        this.dongleLocationX = dongleLocationX;
    }
    /**
     * @return Returns the dongleLocationY in mm accuracy.
     */
    public int getDongleLocationY() {
        return dongleLocationY;
    }
    /**
     * @param dongleLocationY The dongleLocationY to set in mm accuracy.
     */
    public void setDongleLocationY(int dongleLocationY) {
        this.dongleLocationY = dongleLocationY;
    }
    /**
     * @return Returns the length.
     */
    public int getLength() {
        return length;
    }
    /**
     * @param length The length to set.
     */
    public void setLength(int length) {
        this.length = length;
    }
    /**
     * @return Returns the width.
     */
    public int getWidth() {
        return width;
    }
    /**
     * @param width The width to set.
     */
    public void setWidth(int width) {
        this.width = width;
    }
    /**
	 * Pretty printer: output a string representation of this device
	 */
	public String toString() {
	    String result = null;
	    int i;
	    
	    if (getId() > -1) {
	        result = "[Device: id=" +  getId() + ",";
	        if (getType() != null)
	            result += "type=" + getType() + ",";
	        for (i=0; i<Configuration.SIDES.length; i++) {
		        if (getSide() == Configuration.SIDES[i]) {
		            result += "side=" + Configuration.SIDE_NAMES[i] + ",";
		            break;
		        }
	        }
	        if (getUserName() != null)
	            result += "userName=" + getUserName() + ",";
	        if (getMachineName() != null)
	            result += "machineName=" + getMachineName() + ",";
	        if (getIpAddress() != null)
	            result += "ipAddress=" + getIpAddress() + ",";
	        if (getTimeStamp() > -1)
	            result += "timeStamp=" + getTimeStamp() + ",";
	        if (getReachable() != null)
	            result += "reachable=" + getReachable() + ",";	        
	        if (getWidth() != -1)
	            result += "width=" + getWidth() + ",";
	        if (getLength() != -1)
	            result += "length=" + getLength() + ",";
	        if (getDongleLocationX() != -1)
	            result += "dongleLocationX=" + getDongleLocationX() + ",";
	        if (getDongleLocationY() != -1)
	            result += "dongleLocationY=" + getDongleLocationY() + ",";
	        if (getCalibration() != null)
	            result += getCalibration().toString() + ",";

	        result += "]";
	    } else
	        result = "[INVALID DEVICE]";
	    return result;
	}
	
	/**
	 * Try to convert string representation of a device to a Device object.
	 * @param  devString the string containing device description
	 * @return a Device object or null if conversion failed
	 */
	public static Device fromString(String devString) {
	    Device result = null;
	    String tmp = null,
	    	type = null,
	    	userName = null,
	    	machineName = null,
	    	ipAddress = null;
	    int id = -1, side = -1, index, endIndex;
	    int length = -1, width = -1, dongleLocationX = -1, dongleLocationY = -1;
	    long timeStamp = -1;
	    Calibration calibration = null;
	    Boolean reachable = null;
	    
	    if (devString != null) {
	        if (devString.startsWith("[Device:")) {
	            index = devString.indexOf("id=");
	            if (index > 0) {
	                endIndex = devString.indexOf(',', index);
	                if (endIndex > 0) {
	                    try {
	                        id = Integer.parseInt(devString.substring(index+3, endIndex));
	                    } catch (NumberFormatException e) {}
	                }
	            }
	            index = devString.indexOf("timeStamp=");
	            if (index > 0) {
	                endIndex = devString.indexOf(',', index);
	                if (endIndex > 0) {
	                    try {
	                        timeStamp = Long.parseLong(devString.substring(index+10, endIndex));
	                    } catch (NumberFormatException e) {}
	                }
	            }
	            index = devString.indexOf("reachable=");
	            if (index > 0) {
	                endIndex = devString.indexOf(',', index);
	                if (endIndex > 0) {
	                    try {
	                        reachable = new Boolean(devString.substring(index+10, endIndex));
	                    } catch (Exception e) {}
	                }
	            }
	            index = devString.indexOf("side=");
	            if (index > 0) {
	                endIndex = devString.indexOf(',', index);
	                if (endIndex > 0) {
	                    tmp = devString.substring(index+5, endIndex);
	                    for (index=0; index<Configuration.SIDE_NAMES.length; index++) {
	                        if (tmp.equals(Configuration.SIDE_NAMES[index])) {
	                            side = Configuration.SIDES[index];
	                            break;
	                        }
	                    }
	                }
	            }
	            index = devString.indexOf("type=");
	            if (index > 0) {
	                endIndex = devString.indexOf(',', index);
	                if (endIndex > 0) {
	                    type = devString.substring(index+5, endIndex);
	                }
	            }
	            index = devString.indexOf("userName=");
	            if (index > 0) {
	                endIndex = devString.indexOf(',', index);
	                if (endIndex > 0) {
	                    userName = devString.substring(index+9, endIndex);
	                }
	            }
	            index = devString.indexOf("machineName=");
	            if (index > 0) {
	                endIndex = devString.indexOf(',', index);
	                if (endIndex > 0) {
	                    machineName = devString.substring(index+12, endIndex);
	                }
	            }
	            index = devString.indexOf("ipAddress=");
	            if (index > 0) {
	                endIndex = devString.indexOf(',', index);
	                if (endIndex > 0) {
	                    ipAddress = devString.substring(index+10, endIndex);
	                }
	            }
	            index = devString.indexOf("width=");
	            if (index > 0) {
	                endIndex = devString.indexOf(',', index);
	                if (endIndex > 0) {
	                    try {
	                        width = Integer.parseInt(devString.substring(index+6, endIndex));
	                    } catch (NumberFormatException e) {}
	                }
	            }
	            index = devString.indexOf("length=");
	            if (index > 0) {
	                endIndex = devString.indexOf(',', index);
	                if (endIndex > 0) {
	                    try {
	                        length = Integer.parseInt(devString.substring(index+7, endIndex));
	                    } catch (NumberFormatException e) {}
	                }
	            } 
	            index = devString.indexOf("dongleLocationX=");
	            if (index > 0) {
	                endIndex = devString.indexOf(',', index);
	                if (endIndex > 0) {
	                    try {
	                        dongleLocationX = Integer.parseInt(devString.substring(index+16, endIndex));
	                    } catch (NumberFormatException e) {}
	                }
	            }
	            index = devString.indexOf("dongleLocationY=");
	            if (index > 0) {
	                endIndex = devString.indexOf(',', index);
	                if (endIndex > 0) {
	                    try {
	                        dongleLocationY = Integer.parseInt(devString.substring(index+16, endIndex));
	                    } catch (NumberFormatException e) {}
	                }
	            }
	            index = devString.indexOf("[Calibration: ");
	            if (index > 0) {
	                endIndex = devString.indexOf(']', index);
	                if (endIndex > 0) {
	                    calibration = Calibration.fromString(devString.substring(index, endIndex));
	                }
	            }
	            if (id > -1) {
	                result = new Device(id, timeStamp, userName, machineName,
	                        			ipAddress, type, side, reachable, calibration,
	                        			width, length, dongleLocationX, dongleLocationY);
	            }
	        }
	    }
	    return result;
	}
	
	/**
	 * implement clone interface
	 * @author kray
	 */
	public Object clone() {
		return new Device(getId(), getTimeStamp(), getUserName(),
				getMachineName(), getIpAddress(), getType(), getSide(),
				getReachable(), getCalibration(), getWidth(), getLength(),
				getDongleLocationX(), getDongleLocationY());
	}
	
	/**
	 * equality check: if two devices have the same id, they are considered equal.
	 * @author Chris Kray
	 * @return true if two devices are equal
	 */
	public boolean equals(Device device) {
		boolean result = false;
		if (device != null) {
			result = (getId() == device.getId());
			/*if (result) {
			    if (getUserName() == null)
			        result = result && (device.getUserName() == null);
			    else
			        result = result && getUserName().equals(device.getUserName());
			    if (getMachineName() == null)
			        result = result && (device.getMachineName() == null);
			    else
			        result = result && getMachineName().equals(device.getMachineName());
			}*/
		}
		return result;
	}
	
	/**
	 * Update information contained in this device with information contained in
	 * argument (by replacing old with new where new information is available).
	 * @param  newInfo new device information 
	 * @return true if there was any new information except; false otherwise
	 */
	public boolean update(Device newInfo) {
	    boolean result = false;
	    if (newInfo != null) {
	        if (newInfo.getId() == getId()) {
	            /* new info refers to same device */
	            if (getTimeStamp() < newInfo.getTimeStamp()) {
					setTimeStamp(newInfo.getTimeStamp());
					result = true;
	            }
				if ((getUserName() == null) && (newInfo.getUserName() != null)) {
				    /* user name is not set and new info is available */
				    result = true;
					setUserName(newInfo.getUserName());
				} else if (getUserName() != null) {
				    /* user name is set but there is new info */
				    result = !(getUserName().equals(newInfo.getUserName())) || result;
				    setUserName(newInfo.getUserName());
				}
				if ((getMachineName() == null) && (newInfo.getMachineName() != null)) {
				    /* machine name is not set and new info is available */
				    result = true;
					setMachineName(newInfo.getMachineName());
				} else if (getMachineName() != null) {
				    /* machine name is set but there is new info */
				    result = !(getMachineName().equals(newInfo.getMachineName())) || result;
				    setMachineName(newInfo.getMachineName());
				}
				if (getIpAddress() == null) {
				    /* ip address not set and new info may be available */
				    result = (newInfo.getIpAddress() != null) || result;
					setIpAddress(newInfo.getIpAddress());
				} else if (newInfo.getIpAddress() != null) {
				    /* new info for IP address ? */
				    result = !(getIpAddress().equals(newInfo.getIpAddress())) || result;
					setIpAddress(newInfo.getIpAddress());
				}
				if (newInfo.getReachable() != null) {
				    /* update reachable info only if set specifically */
				    result = !(newInfo.getReachable().equals(getReachable())) || result;
					setReachable(newInfo.getReachable());
				}
				if (getType() == null) {
				    /* type not set and new info may be available */
				    result = (newInfo.getType() != null) || result;
					setType(newInfo.getType());
				} else if (newInfo.getType() != null) {
				    /* new info for type ? */
				    result = !(getType().equals(newInfo.getType())) || result;
					setType(newInfo.getType());
				}
				if (newInfo.getSide() != 0) {
				    /* new side information may be available */
				    result = (getSide() != newInfo.getSide()) || result;
					setSide(newInfo.getSide());
				}
				if (newInfo.getLength() != -1) {
				    /* new length information may be available */
				    result = (getLength() != newInfo.getLength()) || result;
				    setLength(newInfo.getLength());
				}
				if (newInfo.getWidth() != -1) {
				    /* new width information may be available */
				    result = (getWidth() != newInfo.getWidth()) || result;
				    setWidth(newInfo.getWidth());
				}
				if (newInfo.getDongleLocationX() != -1) {
				    /* new information may be available */
				    result = (getDongleLocationX() != newInfo.getDongleLocationX()) || result;
				    setDongleLocationX(newInfo.getDongleLocationX());
				}
				if (newInfo.getDongleLocationY() != -1) {
				    /* new information may be available */
				    result = (getDongleLocationY() != newInfo.getDongleLocationY()) || result;
				    setDongleLocationY(newInfo.getDongleLocationY());
				}
				if (newInfo.getCalibration() != null) {
				    /* new calibration information may be available */
				    result = (!newInfo.getCalibration().equals(getCalibration())) || result;
				    setCalibration(newInfo.getCalibration());
				}
	        }
	    }
	    return result;
	}
}