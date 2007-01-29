package org.openuat.util;

import javax.bluetooth.LocalDevice;

import org.apache.log4j.Logger;

/** This class bundles code to initialize and setup different Bluetooth stacks.
 * It is used by Bluetooth client and server classes to perform initialization
 * before Bluetooth is used via JSR82 APIs.
 * @author Rene Mayrhofer
 *
 */
public class BluetoothSupport {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(/*BluetoothSupport.class*/ "org.openuat.util.BluetoothSupport");
	
	private static boolean initialized = false;

	/** This method should be called by all other classes using the JSR82 API
	 * <b>before</b> using it.
	 * @return true if initialization was successful, false otherwise.
	 */
	public static boolean init() {
		if (initialized)
			return true;
		
		if (System.getProperty("os.name") != null && System.getProperty("os.name").startsWith("Linux")) {
			// running on Linux: try to initialize the JSR82 implementations
			return initAvetana();
		}
		else if (System.getProperty( "microedition.platform") != null) {
			// running on J2ME: TODO: check if we have JSR82 support
			initialized = true;
			return true;
		}
		logger.error("No JSR82 support currently known for os.name=" + System.getProperty("os.name"));
		return false;
	}
	
	// TODO: how to disable this selectively for J2ME when we have no reflection? doh
	private static boolean initAvetana() {
		logger.debug("Trying to initializing Avetana JSR82 implementation");
		// Initialize the java stack.
		try {
//			de.avetana.bluetooth.stack.BluetoothStack.init(new de.avetana.bluetooth.stack.AvetanaBTStack());
			LocalDevice localDevice = LocalDevice.getLocalDevice();
			logger.info("Initialized Avetana Bluetooth adapter successfully, local device has address " +
					localDevice.getBluetoothAddress() + " with friendly name '" +
					localDevice.getFriendlyName() + "'");
			initialized = true;
			return true;
		} catch (Exception e) {
			logger.error("Could not initialize local Bluetooth stack. RFCOMM channels will not work");
			e.printStackTrace();
			return false;
		}
	}
	
	/*
	import javax.bluetooth.*;
	import javax.microedition.io.*;
	import com.atinav.BCC;

	public class WirelessDevice implements DiscoveryListener {
	    LocalDevice localDevice = null; 
	    
	    public WirelessDevice (){ 
	        //setting the port number using Atinav's BCC
	        BCC.setPortName("COM1"); 
	        
	        //setting the baud rate using Atinav's BCC
	        BCC.setBaudRate(57600);
	        
	        //connectable mode using Atinav's BCC
	        BCC.setConnectable(true);
	        
	        //Set discoverable mode using Atinav's BCC 
	        BCC.setDiscoverable(DiscoveryAgent.GIAC); 
	        
	        try{
	            localDevice = LocalDevice.getLoaclDevice(); 
	        }
	        catch (BluetoothStateException exp) {
	        }
	        
	        // implementation of methods in DiscoveryListener class
	        // of javax.bluetooth goes here
	        
	        // now do some work
	    }
	}
	 */
}
