package uk.ac.lancs.relate;

/**
 ** Based on code by Martin Strohbach (WeightTableSerialConnector)
 **/

import javax.comm.*;

import java.io.*;
import java.util.*;

/* Make it compiled */
class Relate {
	private Device localDevice = new Device(0, 0, "ich", "mein", "127.0.0.1", "test", 0, new Boolean(true));
	
	void setLocalDevice(Device dev) {
		localDevice = dev;
	}
	
	Device getLocalDevice() {
		return localDevice;
	}
}

/**
 ** This class handles communication protocols, 
 *data's exchange and parsing between a Relate dongle
 *and its host device (laptop, desktop, PDA, projector)
 ** @author  Chris Kray, Henoc Agbota
 **/

public class SerialConnector implements Runnable {
	
	private static SerialConnector sConn = null;
	
	/* testing the Input/OutputStream to better read/write raw bytes */
	private InputStream fis;
	private OutputStream fos;
	private boolean testingFilterStream = true;
	
	/*private BufferedReader b;
	 private BufferedWriter bw;*/
	/** flag indicating wether this instance should continue to run */
	private boolean alive = true;
	/** flag indicating wether this instance is running*/  
	private boolean operational = false;
	/** flag indicating wether this instance is fully initialised*/
	private boolean initialised = false;
	
	private boolean logging = true;
	
	/** flag indicating the dongle's state*/
	private boolean dongle_on = true;

	/** flag indicating the dongle state AFTER sending BLOCK_DONGLE or UNBLOCK_DONGLE*/
	private boolean awaken = true;
	
	/** flag indicating wether still waiting for calibration info*/
	private boolean calibrated = false;
	
	/** list of available ports*/
	private String[] portNames = null;
	private Vector availablePorts = null;
	private CommPortIdentifier portId = null;
	/** serial port object*/
	private SerialPort serialPort = null;
	
	/** unique currently running relate instance*/
	private Relate relate = new Relate();
	
	/** Configuration object used to obtain and send host info*/
	private Configuration configuration = null;
	
	/** queue to push new measurements onto */
//	private MessageQueue queue = null;
	/** relate's event queue */
//	private MessageQueue relateQueue = null;
	
	/** bytes to send to bring dongle into sleep mode*/
	private final static byte[] BLOCK_DONGLE = {67,48} ;
	/** bytes to send to awake dongle from sleep mode*/
	private final static byte[] UNBLOCK_DONGLE = {67,49} ;
	/** bytes to send to awake dongle from sleep mode*/
	private final static byte[] DIAGNOSTIC_ON = {67,50} ;
	/** bytes to send to awake dongle from sleep mode*/
	private final static byte[] DIAGNOSTIC_OFF = {67,51} ;
	/** acknowledgement bytes sent by dongle; 
	 * also served as prefixed bytes to the dongle's relate id */
	private final static byte[] ACK = {65,65} ;
	/*private final static int END_COMM = 90 ;*/
	/** number of bytes for the host info*/
	private final static int HOST_INFO_LENGTH = 51 ;
	/** number of bytes for the ultrasonic calibration info*/
	private final static int CALIBRATION_INFO_LENGTH = 18 ;
	/** number of bytes for the ultrasonic sensor info*/
	private final static int US_SENSOR_INFO_LENGTH = 11 ;
	/** number of bytes for this measurement*/
	private final static int MES_SIZE = 6 ;
	/** number of bits for ultrasonic receiver id*/
	private final static int RX_ID_SIZE = 8 ;
	/** number of bits for ultrasonic sender id*/
	private final static int TX_ID_SIZE = 8 ;
	/** number of bits for this measurement timestamp*/
	private final static int TIMESTAMP_SIZE = 8 ;
	/** number of bits for number of transducers involved in this measurement*/
	private final static int NUMRX_SIZE = 3 ;
	/** number of high bits in distance measurement*/
	private final static int DIST_HIGH_SIZE = 4 ;
	/** number of low bits in distance measurement*/
	private final static int DIST_LOW_SIZE = 7 ;
	/** number of high bits in angle measurement*/
	private final static int ANGLE_HIGH_SIZE = 1 ;
	/** number of low bits in angle measurement*/
	private final static int ANGLE_LOW_SIZE = 7 ;
	/** number of bits for ball switch data*/
	private final static int BALL_SWITCH_SIZE = 1 ;
	/** number of bits for SS high byte*/
	private final static int SS_HIGH_SIZE = 2 ;
	
	/** Prefix to measurement bytes 'M'*/
	private final static int DEVICE_MEASUREMENT_SIGN = 77 ;
	/** this host relate id*/
	private int myRelateId = -1 ;
	/** bytes array containing host information*/
	public byte[] hostInfo = new byte[HOST_INFO_LENGTH] ;
	
	Object changeStateWaiter = new Object();
	
	/** Minimal value for a relate id*/
	private int MIN_ID = 0;
	/** Maximal value for a relate id*/
	private int MAX_ID = 255;
	
	/** Prefix to host info 'H'*/
	private static final int HOST_INFO_SIGN = 72;
	/** Prefix to calibration info 'L'*/
	private static final int CALIBRATION_INFO_SIGN = 76;
	/** Prefix to uS sensor info 'U'*/
	private static final int US_SENSOR_INFO_SIGN = 85;
	
	/** flag indicating wether the diagnostic mode is turned on*/  
	public boolean diagnosticMode = false;
	
	/** number of bytes for the firmware version*/
	private static final int FIRMWARE_VERSION_LENGTH = 2;
	
	/** Prefix to firmware version*/
	private static final int FIRMWARE_VERSION_SIGN = 86;
	
	/** Firmware version*/
	public String firmwareVersion = null;
	
	/** Calibration object*/
	private	Calibration calibration = null ;
	
	/** List of devices to exclude (temporary for Oliver)*/
	private final static int[] DEVICE_NON_GRATA = {} ;

	/** Prefix to error code*/
	private static final int ERROR_CODE_SIGN = 69;
	
	/** Firmware version*/
	public int errorCode = -1;

	/** */
	private static int DN_STATE_LENGTH = 0;
	
	/** Prefix to firmware version*/
	private static final int DN_STATE_SIGN = 78;
	
	
	protected SerialConnector(boolean loggingOn) {
		CommPortIdentifier id;
		Enumeration portList;
		int i;
		
		logging = loggingOn;
		operational = false;
		portList = CommPortIdentifier.getPortIdentifiers();
		availablePorts = new Vector();
		/* generate list of (available) ports */
		while ((portList.hasMoreElements()) && (portId == null)) {
			id = (CommPortIdentifier) portList.nextElement();
			log("port " + id + " (" + id.getName() + ") is " +
					(id.isCurrentlyOwned() ? " not " : "") + " available.");
			if (!(id.isCurrentlyOwned())) {
				availablePorts.addElement(id);
			}
		}
		if (availablePorts.size() > 0) {
			portNames = new String[availablePorts.size()];
			for (i=0; i<availablePorts.size(); i++) {
				portNames[i] = ((CommPortIdentifier) availablePorts.elementAt(i)).getName();
			}
		} else {
			log("no ports available");
		}
		
		/*		try{
		 portId = (CommPortIdentifier) CommPortIdentifier.getPortIdentifier("COM1");
		 }catch(NoSuchPortException e){
		 e.printStackTrace();
		 return;
		 }*/
		initialised = true;
	}
	
	/** Get the only instance of the SerialConnector.*/
	public static SerialConnector getSerialConnector() {
		if (sConn == null)
			sConn = new SerialConnector(false);
		while (!(sConn.initialised))
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				
			}
			return sConn;
	}
	
	/** Get the only instance of the SerialConnector.*/
	public static SerialConnector getSerialConnector(boolean loggingOn) {
		if (sConn == null)
			sConn = new SerialConnector(loggingOn);
		while (!(sConn.initialised))
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				
			}
			return sConn;
	}
	
	/** return list of available serial ports */
	public String[] getPorts() {
		return portNames;
	}

	/** return Calibration object */
	public Calibration getCalibration() {
		return calibration ;
	}
	
	public void startLogging() {
		logging = true;
	}
	public void stopLogging() {
		logging = false;
	}
	
	private boolean setReceiveOptions() {
		try {
/*			serialPort.close();
			serialPort = (SerialPort) portId.open("RelatePort", 2000);*/
			serialPort.setSerialPortParams(57600,
					SerialPort.DATABITS_8,
					SerialPort.STOPBITS_1,
					SerialPort.PARITY_NONE);
			serialPort.setFlowControlMode(SerialPort.FLOWCONTROL_NONE);
			return true;
		} catch (UnsupportedCommOperationException e) {
			log("UnsupportedCommOperationException") ;
			e.printStackTrace();
			return false;
		}
/*		catch (PortInUseException e) {
			log("PostInUseException") ;
			e.printStackTrace();
			return false;
		}*/
		catch (Exception e) {
			log("not all parameters supported? ingnoring exception") ;
			//e.printStackTrace();
			return true;
		}
	}
	
	private boolean setSendOptions() {
		try {
/*			serialPort.close();
			serialPort = (SerialPort) portId.open("RelatePort", 2000);*/
			serialPort.setSerialPortParams(19200, SerialPort.DATABITS_8,
					SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
			serialPort.setFlowControlMode(SerialPort.FLOWCONTROL_NONE);
			return true;
		} catch (UnsupportedCommOperationException e) {
			log("UnsupportedCommOperationException");
			e.printStackTrace();
			return false;
		}
/*		catch (PortInUseException e) {
			log("PostInUseException") ;
			e.printStackTrace();
			return false;
		}*/
		catch (Exception e) {
			log("not all parameters supported? ingnoring exception") ;
			//e.printStackTrace();
			return true;
		}
	}
	
	/** initialise connector. 
	 @param rel           is the Relate instance that will receive 
	 notifications about new object information 
	 @param queue         the queue to push new measurements onto
	 @param configuration the current configuration
	 @param minID         the lowest valid ID for relate objects
	 @param maxID         the highest valid ID for relate objects
	 @return the ID of the attached dongle or -1 if something went wrong
	 **/
	public int connect(Relate rel, /*MessageQueue queue, */Configuration configuration,
			int minID, int maxID) {
		int result = -1;
		int i;
		String port = configuration.getDevicePortNumber();
		
/*		this.queue = queue;
		this.relateQueue = rel.getQueue();*/	
		this.hostInfo = configuration.getFormat(); 
		
		this.MIN_ID = minID;
		this.MAX_ID = maxID;
		/*System.out.print("hostInfo: \n");
		 printByteArray(hostInfo) ;*/
		if (port != null) {
			for (i=0; i<availablePorts.size(); i++) {
				if (port.equals(((CommPortIdentifier) availablePorts.elementAt(i)).getName())) {
					portId = (CommPortIdentifier) availablePorts.elementAt(i);
					break;
				}
			}
			if (portId == null) {
				log("no port named '" + port + "' found!");
			} else {
				if (portId.isCurrentlyOwned()) {
					log("port " + port + " is currently in use by " +
							portId.getCurrentOwner());
				} else {
					/* port seems to exist and is available */
					try {
						serialPort = (SerialPort) portId.open("RelatePort", 2000);
						log("port " + port + " seems to exist and is available..");
					} catch (Exception e) {
						//System.out.println("PortInUseException") ;
						e.printStackTrace();
						return -1;
					}
					
					result = pcSend(hostInfo, true);
					log("local device id: "+result+"\n") ;
					
					/*temporary fix to turn on diagnostic mode*/
					int res = pcSend(DIAGNOSTIC_ON, false);
					diagnosticMode = true ;

					if (!setReceiveOptions())
						return -1;
					
					/*temporary fix to turn off diagnostic mode*/
					/*res = pcSend(DIAGNOSTIC_OFF, false);
					 diagnosticMode = false ;*/
					
					operational = true;
//					relate = rel;
				}
			}
		}
		this.configuration = configuration;
		return result;
	}
	
	public void disconnect() {
		/*b = null;*/
		if (serialPort != null) {
			serialPort.close();
			serialPort = null;
			operational = false;
		}
	}
	
	public void log(Object o) {
	    if (logging)
	        System.out.println("[SerialConnector] " + o);
	}
	
	public void die() {
		alive = false;
	}
	
	/* convert one byte to int */
	private static int unsign(byte b) {
		return (b<0?256+b:b);
	}
	/* convert two bytes to int */
	private static int unsign2(byte hi, byte lo) {
		return (unsign(hi)*256+unsign(lo));
	}
	
	
	/** initialise connector. 
	 @param size the size of the mask
	 @return size number of all-set-to-1 low bits
	 **/
	private static int doMask(int size) {
		int i, mask ;
		mask = 0 ;
		for(i = 1; i <= size; ++i) {
			mask = (mask << 1) | 1 ;
		}
		return mask ;
	}
	
	/** parse bytes read over the serial port and try to recognise an event
	 * 	@author Henoc AGBOTA
	 ** @return the event that was parsed from the bytes, or null if no event
	 ** was recognised */
	public RelateEvent parseEvent(byte[] bytes) {
		RelateEvent result = null;
		Device d ;
		int i, 
		/** ultrasonic receiver id*/
		rxId = -1, 
		/** ultrasonic sender id*/
		txId = -1,
		/** measurement's timestamp from the dongle*/
		timestamp = -1,
		/** distance value in decimal mode*/
		dist = -1,
		/** angle value in decimal mode*/
		angle = -1,
		/** high bits value for the measured distance*/
		hDist = -1,
		/** low bits value for the measured distance*/
		lDist = -1,
		/** high bits value for the measured angle*/
		hAngle = -1,
		/** low bits value for the measured angle*/
		lAngle = -1,
		/** number of transducers which record a valid signal for this measurement*/ 
		trans = -1,
		/** number of transducers which record a valid signal for this measurement*/ 
		ballSwitch = -1,
		/** Average background levels for sensors uS1,uS2,uS4*/ 
		ablUs1,ablUs2,ablUs4,
		/** Maximal background levels for sensors uS1,uS2,uS4*/ 
		mblUs1,mblUs2,mblUs4,
		/** Threshold levels for sensors uS1,uS2,uS4*/ 
		thlUs1,thlUs2,thlUs4 ,
		/** TOF values for sensors uS1,uS2,uS4*/ 
		tofUs1,tofUs2,tofUs4,
		/** SS high bytes,low bytes and decimal values for sensors uS1,uS2,uS4*/ 
		hSSUs1,hSSUs2,hSSUs4,lSSUs1,lSSUs2,lSSUs4,ssUs1,ssUs2,ssUs4;
		/** Validity flag values for sensors uS1,uS2,uS4*/ 
		boolean validUs1,validUs2,validUs4, inMotion ;
		int mask, relateTime, numberOfEntries, awareconSyncRate ;
		SensorReading sensorReading = null ;
		Vector dicoveryListIds = null, dicoveryListTimes = null ;
		DongleNetworkState dnState = null ;
		
		if (bytes != null) {
			d = null ;
			if (bytes.length == MES_SIZE || bytes.length == (MES_SIZE+US_SENSOR_INFO_LENGTH)) {
				
				/* First three lower bytes respectively represent 
				 * rxId, txId and timestamp */
				rxId = bytes[0];
				txId = bytes[1];
				timestamp = unsign(bytes[2]);
				
				/* From higher to lower bits, 
				 * fourth byte represents: trans (3), hDist (4) and a spare bit (1) */
				mask = doMask(NUMRX_SIZE) ;
				mask <<= (1 + DIST_HIGH_SIZE) ;
				trans = (bytes[3] & mask)>>(1 + DIST_HIGH_SIZE) ;
				
				mask = doMask(DIST_HIGH_SIZE) ;
				mask = mask << 1 ;
				hDist = (bytes[3] & mask)>>1 ;
				//System.out.println("hDist: "+ hDist +" \n") ;
				
				/* From higher to lower bits, 
				 * fifth byte represents: lDist (7) and ball switch bit (1) */
				mask = doMask(DIST_LOW_SIZE) ;
				mask = mask << BALL_SWITCH_SIZE ;
				lDist = (bytes[4] & mask) ;
				//				System.out.println("lDist: "+ lDist +" \n") ;
				
				mask = doMask(BALL_SWITCH_SIZE) ;
				ballSwitch = (bytes[4] & mask) ;
				inMotion = (ballSwitch == 1) ;
				//System.out.println("ballSwitch: "+ ballSwitch +" \n") ;
				
				/* From higher to lower bits, 
				 * sixth byte represents: hAngle (1) lAngle (7) */
				mask = doMask(ANGLE_HIGH_SIZE) ;
				mask = mask << ANGLE_LOW_SIZE ;
				hAngle = (bytes[5] & mask)>> ANGLE_LOW_SIZE ;
				//System.out.println("hAngle: "+ hAngle +" \n") ;
				
				mask = doMask(ANGLE_LOW_SIZE) ;
				lAngle = (bytes[5] & mask)<<1 ;
				//System.out.println("lAngle: "+ lAngle +" \n") ;
				
				dist = (hDist * 256) + lDist ;
				angle = (hAngle * 256) + lAngle ;
				/*if(rxId == myRelateId)
				 System.out.println("rxID= "+rxId+", txID= "
				 +txId+", timestamp= "+timestamp+", trans= "
				 +trans+", dist= "+dist+", angle= "
				 +angle+", ballSwitch= "+ballSwitch+"\n") ;*/
				/* sanity check */ 
				d = new Device(rxId,
						System.currentTimeMillis(),configuration.getDeviceName(),
						configuration.getUserName(),
						configuration.getIpAddress(),configuration.getDeviceType(),
						configuration.getDeviceSide(),new Boolean(true)) ;
				
				if ((0 <= angle) && (angle <= 360)) {
					if(rxId == myRelateId && diagnosticMode &&
							bytes.length == (MES_SIZE+US_SENSOR_INFO_LENGTH)){
						/*log("Got uS sensor info..!!") ;
						 printByteArray(bytes) ;*/
						
						/* From higher to lower bits, 
						 * 2 bytes respectively for uS1,uS2,uS4 TOFs
						 */
						tofUs1 = unsign2(bytes[6],bytes[7]) ;
						tofUs2 = unsign2(bytes[8],bytes[9]) ;
						tofUs4 = unsign2(bytes[10],bytes[11]) ;
						
						/* From higher to lower bits, 
						 * fifth byte represents: hSSUs4,hSSUs2,hSSUs1;
						 * 2 bits each . The first two bits of this fifth byte
						 * contain no info*/
						mask = doMask(SS_HIGH_SIZE) ;
						mask <<= (2*SS_HIGH_SIZE) ;
						hSSUs4 = (bytes[12] & mask)>>(2*SS_HIGH_SIZE) ;
						
						mask = doMask(SS_HIGH_SIZE) ;
						mask <<= (SS_HIGH_SIZE) ;
						hSSUs2 = (bytes[12] & mask)>>(SS_HIGH_SIZE) ;
						
						mask = doMask(SS_HIGH_SIZE) ;
						hSSUs1 = (bytes[12] & mask) ;
						
						/* From higher to lower bits, 
						 * 1 byte respectively for uS1,uS2,uS4 low bytes
						 */
						lSSUs1 = bytes[13] ;
						lSSUs2 = bytes[14] ;
						lSSUs4 = bytes[15] ;
						
						ssUs1 = unsign2((byte)hSSUs1,(byte)lSSUs1) ;
						ssUs2 = unsign2((byte)hSSUs2,(byte)lSSUs2) ;
						ssUs4 = unsign2((byte)hSSUs4,(byte)lSSUs4) ;
						
						/* From higher to lower bits, 
						 * first byte represents: validUs4,validUs2,validUs1;
						 * The first five bits of this first byte
						 * contain no info*/
						mask = 4 ; //100
						validUs4 = ((bytes[16] & mask)>>2)==1?true:false ;
						mask = 2 ; //010
						validUs2 = ((bytes[16] & mask)>>1)==1?true:false ;
						mask = 1 ; //001
						validUs1 = ((bytes[16] & mask))==1?true:false ;
						sensorReading = new SensorReading(tofUs1,tofUs2,tofUs4,
								ssUs1,ssUs2,ssUs4,validUs1,validUs2,validUs4) ;
						result = new RelateEvent(RelateEvent.NEW_MEASUREMENT, d,
								new Measurement(rxId, txId,
										dist, angle,
										trans,
										System.currentTimeMillis(),
										timestamp,inMotion,sensorReading),
										System.currentTimeMillis()
/*,null*/);
						/*log(sensorReading.toString()) ;	
						 log("Got uS sensor info..!!") ;
						 printByteArray(bytes) ;	*/				
					}else {
						result = new RelateEvent(RelateEvent.NEW_MEASUREMENT, d,
								new Measurement(rxId, txId,
										dist, angle,
										trans,
										System.currentTimeMillis(),
										timestamp,inMotion),
										System.currentTimeMillis()
/*,null*/);
					}
					//log(result);
					/* filter events */
					if (invalidID(rxId) || invalidID(txId)) {
						/*
						 System.out.println("invalid ID:" + (invalidID(rxId) ? "" + rxId : "") + " " +
						 (invalidID(txId) ? "" + txId : ""));
						 */
						result = null;
					}
				}
			}
			if( (bytes.length == HOST_INFO_LENGTH) && 
					(bytes[29]==1 || bytes[29]==2 ||bytes[29]==3 || bytes[29]==4) 
					&& (bytes[50]==1 || bytes[50]==2 ||bytes[50]==3 ||bytes[50]==4) ){
				d = new Device(unsign(bytes[0]),
						System.currentTimeMillis(),getHostInfoUserName(bytes),
						getHostInfoMachineName(bytes),
						getHostInfoIp(bytes),getHostInfoDeviceType(bytes),
						bytes[50],new Boolean(true)) ;
				result = new RelateEvent(RelateEvent.DEVICE_INFO, d,null,
						System.currentTimeMillis()
/*,null*/);
				//printHostInfo(bytes) ;
				/* filter events */
				if (invalidID(unsign(bytes[0]))) {
					/*System.out.println("invalid ID:" + (invalidID(rxId) ? "" + rxId : "") + " " +
					 (invalidID(txId) ? "" + txId : ""));*/
					result = null;
				}
			}
			if (bytes.length == CALIBRATION_INFO_LENGTH) {
				
				/* From higher to lower bits, 
				 * 2 bytes respectively for uS1,uS2,uS4 ABLs
				 * 2 bytes respectively for uS1,uS2,uS4 MBLs 
				 * 2 bytes respectively for uS1,uS2,uS4 thresholds  
				 */
				ablUs1 = unsign2(bytes[0],bytes[1]) ;
				ablUs2 = unsign2(bytes[2],bytes[3]) ;
				ablUs4 = unsign2(bytes[4],bytes[5]) ;
				mblUs1 = unsign2(bytes[6],bytes[7]) ;
				mblUs2 = unsign2(bytes[8],bytes[9]) ;
				mblUs4 = unsign2(bytes[10],bytes[11]) ;
				thlUs1 = unsign2(bytes[12],bytes[13]) ;
				thlUs2 = unsign2(bytes[14],bytes[15]) ;
				thlUs4 = unsign2(bytes[16],bytes[17]) ;
				calibration = new Calibration(ablUs1,ablUs2,ablUs4,
						mblUs1,mblUs2,mblUs4,thlUs1,thlUs2,thlUs4) ;
				log(calibration.toString()) ;
				result = new RelateEvent(RelateEvent.CALIBRATION_INFO, relate.getLocalDevice(),
						null,System.currentTimeMillis(),
/*null,*/calibration);
				calibrated = true ;
			}if(bytes[0] == DN_STATE_SIGN && bytes.length == DN_STATE_LENGTH) {
				dicoveryListIds = new Vector() ;
				dicoveryListTimes = new Vector() ;
				relateTime = unsign(bytes[1]) ;
				numberOfEntries = unsign(bytes[2]) ;
				for(i = 0 ; i < numberOfEntries; i++) {
					dicoveryListIds.add(new Integer(unsign(bytes[i+3]))) ;
				}
				for(i = 0 ; i < numberOfEntries; i++) {
					dicoveryListTimes.add(new Integer(unsign(bytes[i+3+numberOfEntries]))) ;
				}
				awareconSyncRate = unsign(bytes[DN_STATE_LENGTH-1]) ;
				dnState = new DongleNetworkState(relateTime,numberOfEntries,
						dicoveryListIds,dicoveryListTimes,awareconSyncRate) ;
				result = new RelateEvent(RelateEvent.DN_STATE, relate.getLocalDevice(),
						null,System.currentTimeMillis(),null,dnState);
			}
		}
		return result;
	}
	
	public static void printByteArray(byte[] a) {
		for (int i = 0; i < a.length; i++){
			System.out.println("["+i+"]: "+unsign(a[i])+"\n") ;
		}
	}
	
	public void printHostInfo(byte[] a) {
		int i ;
		String hostInfo = "Host info:";
		if (a.length == HOST_INFO_LENGTH)
			hostInfo += (" ID: "+a[0]) ;
		hostInfo += (" IP: "+getHostInfoIp(a)) ;
		hostInfo += (" User name: "+getHostInfoUserName(a)) ;
		hostInfo += (" Device type: "+getHostInfoDeviceType(a)) ;
		hostInfo += (" Machine Name: "+getHostInfoMachineName(a)) ;
		hostInfo += (" Side: "+a[50]) ;
		log(hostInfo);
	}
	
	public static String getHostInfoIp(byte[] a) {
		int i, imax ;
		String result = "" ;
		if(a[5] != 0)
			imax = 9 ;
		else
			imax = 5 ;
		if (a.length == HOST_INFO_LENGTH)
			for (i = 1; i < imax; i++){
				result = result + unsign(a[i]) ;
				if(i<(imax-1))
					result = result + '.' ;
			}
		return result ;
	}
	
	public static String getHostInfoUserName(byte[] a) {
		int i ;
		String result = "" ;
		if (a.length == HOST_INFO_LENGTH)
			for (i = 9; i < 29; i++){
				if (a[i] != 0)
					result = result + (char)a[i];
			}
		return result ;
	}
	
	public static String getHostInfoMachineName(byte[] a) {
		int i ;
		String result = "" ;
		if (a.length == HOST_INFO_LENGTH)
			for (i = 30; i < 50; i++){
				if (a[i] != 0)
					result = result + (char)a[i];
			}
		return result ;
	}
	
	
	public static String getHostInfoDeviceType(byte[] a) {
		String result = "" ;
		if(a[29] == 1)
			result = "desktop" ;
		if(a[29] == 2)
			result = "laptop" ;
		if(a[29] == 3)
			result = "PDA" ;
		if(a[29] == 4)
			result = "projector" ;
		return result ;
	}
	
	public boolean sendOnly(byte[] data){
		boolean res = true ;
		try {
			fos.write(data) ;
			fos.flush() ;
		} catch (IOException ex) {
			res = false ;
			log("sending info to dongle failed due to " + ex);
			ex.printStackTrace();
		}
		if (!setReceiveOptions())
			res = false;
		return res ;
	}
	
	public boolean sendHostInfo(){
		boolean res = true, timeOut = true ;
		long theTimer ;
		byte[] ackBytes = new byte[hostInfo.length] ;
		int i ;
		
		try {
			fos.write((byte)72) ;
			theTimer =  System.currentTimeMillis();				
			fos.write(hostInfo) ;
			fos.flush() ;
			log("Host Info sent") ;
			ackBytes[0] = (byte)fis.read();
			timeOut = true ;
			while(ackBytes[0] != hostInfo[0] && res) {
				ackBytes[0] = (byte)fis.read();
				timeOut = (System.currentTimeMillis() - theTimer) < 10000;
			}
			if(ackBytes[0] == hostInfo[0]) {
				//log("i: "+0+", ackBytes "+unsign(ackBytes[0])+"\n") ;
				for(i = 1 ; i < hostInfo.length; i++) {
					ackBytes[i] = (byte)fis.read();
					//log("i: "+i+", ackBytes "+unsign(ackBytes[i])+"\n") ;
					if(ackBytes[i] != hostInfo[i]){
						res = false ;
						break ;
					}
				}
			}
		} catch (IOException ex) {
			res = false ;
			log("sending host info to dongle failed due to " + ex);
			ex.printStackTrace();
		}
		if (!setReceiveOptions())
			res = false;
		return res ;
	}
	
	/**
	 * Try to catch the dongle's attention by
	 * repeatedly sending '10101...'
	 * @return the dongle ID
	 */
	private int getDongleAttention() {
		int localId = -1,i ;
		int inputbufferlen = 0, theByte, counter = 0;
		boolean unacknowledged = true ;
		long startTime = 0 ;
		long lastTry = 0;
		
		try {
			setSendOptions();
			fis = serialPort.getInputStream();
			fos = serialPort.getOutputStream();
			startTime =  System.currentTimeMillis();				
			
			while (unacknowledged) {
				counter++ ;
				/*Discard everything currently in the serial input buffer.*/
				inputbufferlen=fis.available();
				for(i = 0 ; i < inputbufferlen; i++) fis.read();
				/*Send garbage..*/
				fos.write((byte)0xAA);
				fos.flush() ;
				lastTry = System.currentTimeMillis();
				do {
					while ((fis.available()>0) && unacknowledged) //check to see if there's something coming in
					{
						theByte = fis.read();
						if (theByte == ACK[0]) {
							theByte = fis.read();
							if (theByte == ACK[1]) {
								localId = fis.read();
								unacknowledged = false;
								log("Got first ACK and Id: "+localId+"\n");
								log("time to get dongle's attention: "+(System.currentTimeMillis() - startTime)+" ms");
								
							}
						}	
					}
				} while ((System.currentTimeMillis() - lastTry < 3) && unacknowledged);
			}
		} catch (IOException ex) {
			log("Geting dongle's attention failed due to " + ex);
			ex.printStackTrace();
		}
		log("Number of trials before getting ACK:"+counter) ;
		log("Time from garbage to ack: "+(System.currentTimeMillis()-lastTry));
		return localId ;
	}
	
	/**
	 * Sends the host information to the dongle and receives ID of
	 * the dongle.
	 * @param data, the data to be sent; ackBack, true if acknowledgment required
	 * @return the dongle ID or -1 if something went wrong.
	 */
	public int pcSend(byte[] data, boolean ackBack) {
		boolean stop = false ;
		
		do {
			myRelateId = getDongleAttention() ;
			if(!ackBack)
				stop = sendOnly(data) ;
			else {
				hostInfo[0] = (byte)myRelateId ;
				stop = sendHostInfo() ;
			}
		}while(!stop) ;
		return myRelateId;
	}
	
	/** set the state of the dongle
	 *  @param on if true, turn dongle on, if false, turn it off
	 */
	public void setDongleState(boolean on) {
		dongle_on = on;
		synchronized(changeStateWaiter) {
			try {
				changeStateWaiter.wait();
			} catch (InterruptedException e) {
			}
		}
	}
	
	/** change the dongle state according to dongle_on */
	private void changeDongleState() {		
		try {
			if (dongle_on) {
				/* starting up */
				pcSend(UNBLOCK_DONGLE,false) ;
				awaken = true ;
			} else {
				/* shutting down */
				pcSend(BLOCK_DONGLE,false) ;
				awaken = false ;
				log("shut down dongle");
			}
		} catch (Exception e) {
			log("changing dongle state to " + (dongle_on ? "on" : "off") + "failed!");
			e.printStackTrace();
		}
		synchronized(changeStateWaiter) {
			changeStateWaiter.notify();
		}
	}
	
	/** filter invalid IDs i.e. those not falling in the specified range */
	private boolean invalidID(int id) {
		boolean res = false ;
		if ((id < MIN_ID) || (id > MAX_ID))
			res = true;
		for (int i = 0; i < DEVICE_NON_GRATA.length; i++){
			if (id == DEVICE_NON_GRATA[i]) {
				res = true ;
				break ;
			}
		}
		return res ;
	}
	
	public void run() {
		int theByte;
		int i=0, relateTime, numberOfEntries;
		boolean newLine = false;
		String line = null;
		String garbage = "grrrrrr" ;
		byte[] bytes = null; 
		byte[] mesbytes = null, dnStateBytes = null;
		byte[] hostInfoBytes = null, calibrationInfoBytes = null, 
		usSensorInfoBytes = null, firmwareVersionBytes = null;
		byte[] stringDelimiters = {13,10} ;
		RelateEvent event = null;
		boolean last_dongle_state = dongle_on;
		
		while (alive) {
			/* testing the raw stream */
			try {
				i = 0;
				setReceiveOptions();
				fis = serialPort.getInputStream();
				while(alive) {
					if (dongle_on != last_dongle_state) {
						last_dongle_state = dongle_on;
						changeDongleState();
					}
					if(awaken) {
						theByte = fis.read();					
						/*System.out.print(theByte + " ") ;*/
						if(theByte == DEVICE_MEASUREMENT_SIGN) {
							mesbytes = new byte[MES_SIZE] ;
							for (int j = 0 ;j < MES_SIZE; j++){
								mesbytes[j] = (byte)fis.read() ;
							}
							/*If local measurement, check for sensor info..*/
							if(mesbytes[0] == myRelateId && diagnosticMode){
								theByte = fis.read();
								if(theByte == US_SENSOR_INFO_SIGN) {
									usSensorInfoBytes = new byte[MES_SIZE+US_SENSOR_INFO_LENGTH] ;
									for (int j = MES_SIZE ;j < MES_SIZE+US_SENSOR_INFO_LENGTH; j++){
										usSensorInfoBytes[j] = (byte)fis.read() ;
									}
									for (int j = 0 ;j < MES_SIZE; j++){
										usSensorInfoBytes[j] = mesbytes[j] ;
									}
									event = parseEvent(usSensorInfoBytes);
								}else {
									/*log("could not get uS sensor info event");*/
								}
							}else {
								event = parseEvent(mesbytes);
							}
/*							if (event != null) {							
								if (event.getType() == RelateEvent.NEW_MEASUREMENT) {
									queue.addMessage(event);
								}
								relateQueue.addMessage(event.clone());
							} else {
								//log("could not parse measurement event");
							}*/
						} /*else if (theByte == END_COMM) {
						log("dongle is sleeping.");
						} */
						else if(theByte == HOST_INFO_SIGN){
							hostInfoBytes = new byte[HOST_INFO_LENGTH] ;
							for (int j = 0 ;j < HOST_INFO_LENGTH; j++){
								hostInfoBytes[j] = (byte)fis.read() ;
							}
							event = parseEvent(hostInfoBytes);
/*							if (event != null) {
								if (event.getType() == RelateEvent.DEVICE_INFO) {
									queue.addMessage(event);
								}
								relateQueue.addMessage(event.clone());
							} else {
								log("could not parse host info event");
							}*/
						}else if(theByte == CALIBRATION_INFO_SIGN && !calibrated) {
							calibrationInfoBytes = new byte[CALIBRATION_INFO_LENGTH] ;
							for (int j = 0 ;j < CALIBRATION_INFO_LENGTH; j++){
								calibrationInfoBytes[j] = (byte)fis.read() ;
							}
							event = parseEvent(calibrationInfoBytes);
/*							if (event != null) {
								if (event.getType() == RelateEvent.CALIBRATION_INFO) {
									queue.addMessage(event);
								}
								relateQueue.addMessage(event.clone());
							} else {
								log("could not parse calibration info event");
							}*/
						}else if(theByte == FIRMWARE_VERSION_SIGN && firmwareVersion == null) {
							firmwareVersionBytes = new byte[FIRMWARE_VERSION_LENGTH] ;
							for (int j = 0 ;j < FIRMWARE_VERSION_LENGTH; j++){
								firmwareVersionBytes[j] = (byte)fis.read() ;
							}
							firmwareVersion = ""+unsign(firmwareVersionBytes[0])+"."+
							+unsign(firmwareVersionBytes[1]) ;
							log("The firmware version for dongle "+myRelateId+" is "+
									firmwareVersion+".") ;
						}else if(theByte == ERROR_CODE_SIGN && diagnosticMode) {
							errorCode = unsign((byte)fis.read()) ;
							event = new RelateEvent(RelateEvent.ERROR_CODE, relate.getLocalDevice(),
									null,System.currentTimeMillis(),null,new Integer(errorCode));
/*							if (event != null) {
								queue.addMessage(event);
								relateQueue.addMessage(event.clone());
							} else {
								log("could not error code event");
							}*/
							/*log("error code: "+errorCode) ;*/
						}else if(theByte == DN_STATE_SIGN && diagnosticMode) {
							relateTime = unsign((byte)fis.read()) ;
							numberOfEntries = unsign((byte)fis.read()) ;
							DN_STATE_LENGTH = 3+(2*numberOfEntries)+1 ;
							dnStateBytes = new byte[DN_STATE_LENGTH] ;
							for (int j = 0 ;j < DN_STATE_LENGTH-3; j++){
								dnStateBytes[j+3] = (byte)fis.read() ;
							}
							dnStateBytes[0] = (byte)DN_STATE_SIGN ;
							dnStateBytes[1] = (byte)relateTime ;
							dnStateBytes[2] = (byte)numberOfEntries ;
							/*printByteArray(dnStateBytes) ;*/
							event = parseEvent(dnStateBytes);
/*							if (event != null) {
								if (event.getType() == RelateEvent.DN_STATE) {
									queue.addMessage(event);
								}
								relateQueue.addMessage(event.clone());
							} else {
								log("could not parse dongle network state event");
							}*/
						}
					}/*else {       //Dongle probably sleeping..
						if (dongle_on != last_dongle_state) {
							last_dongle_state = dongle_on;
							log("nothing to read..!!");
							changeDongleState();
						}
					}*/
				} 
			}catch (Exception ex) {
				log("failure in main loop: " + ex);
				ex.printStackTrace();
				try {
					Thread.sleep(2000);
				} catch (InterruptedException e) {
					
				}
			}
		}	
		disconnect();
	}
	
	//Testing
	public static void main(String args[]){
		SerialConnector connector = SerialConnector.getSerialConnector();
		Configuration configuration = new Configuration("./img/", connector, true);
		
		/* wait for the user to specify all fields */
		while (!configuration.fullySpecified()) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
			}
		}
		
		/* connect to serial port */
		connector.connect(null, /*new MessageQueue(), */configuration, 0, 20);
		configuration.hideGUI();
		connector.run();
	}
	
}
