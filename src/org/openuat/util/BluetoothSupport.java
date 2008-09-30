/* Copyright Rene Mayrhofer
 * File created 2007-01-25
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.util;

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
			//return initAvetana();
			// using Bluecove now under Windows and Linux, which doesn't need any init code
			initialized = true;
			return true;
		}else if (System.getProperty("os.name") != null && System.getProperty("os.name").startsWith("Windows XP")) {
			//return initAvetana();
			initialized = true;
			return true;
		}
		else if (System.getProperty( "microedition.platform") != null) {
			// running on J2ME, but need to check if we have JSR82 support
			// TODO: this doesn't return anything on the Nokia 5500
			/*if (System.getProperty("bluetooth.api.version") == null) {
				logger.error("No JSR82 support detected, property bluetooth.api.version not set");
				return false;
			}*/
			initialized = true;
			return true;
		}
		logger.error("No JSR82 support currently known for os.name=" + System.getProperty("os.name"));
		return false;
	}
	
/*	private static boolean initAvetana() {
		logger.debug("Trying to initializing Avetana JSR82 implementation");
		// Initialize the java stack.
		try {
			//de.avetana.bluetooth.stack.BluetoothStack.init(new de.avetana.bluetooth.stack.AvetanaBTStack());
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
	}*/
}
