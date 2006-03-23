/* Copyright Rene Mayrhofer
 * File created 2006-02
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.channel;

import java.io.File;

import org.apache.log4j.Logger;

/** This is a factory class for generating instances of SecureChannel based on IPSec. Based on the
 * running operating system and installed components, it will select the appropriate IPSecConnection_*
 * implementation to use.
 *  
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class IPSecConnection_Factory {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(IPSecConnection_Factory.class);

	/** Returns the appropriate instance of the ipsec secure channel implementation. or null if
	 * no implementation is yet available for the running platform. 
	 */
	public static IPSecConnection getImplementation() {
		String osname = System.getProperty("os.name");
		String osversion = System.getProperty("os.version");
		logger.debug("Trying to create IPSec secure channel implementation for '" + osname + "'/'" + osversion + "'");
		
		if (osname.startsWith("Windows CE")) {
			// no implementation for Windows CE/PocketPC
			logger.warn("No IPSec secure channel implementation available for Windows CE ('" + osname + "'), returning null");
			return null;
		}
		// TODO: how to detect windows XP/2000 explicitly?
		else if (osname.startsWith("Windows")) {
			logger.debug("Detected Windows");
			return new IPSecConnection_Windows_VPNTool();
		}
		else if (osname.startsWith("Linux")) {
			if (new File("/etc/ipsec.conf").exists() && 
				new File("/etc/ipsec.secrets").exists() &&
				new File("/etc/ipsec.d/dynamic").isDirectory()) {
				logger.debug("Detected Linux/openswan");
				return new IPSecConnection_Openswan();
			}
			else if (new File("/etc/racoon/racoon.conf").exists() &&
					 new File("/etc/racoon/psk.txt").exists() &&
					 new File("/etc/racoon/remote").isDirectory()) {
				logger.debug("Detected Linux/racoon");
				return new IPSecConnection_Racoon();
			}
			else {
				logger.warn("Detected Linux, but no supported IPSec IKE daemon");
				return null;
			}
		}
		else if (osname.startsWith("Mac OS/X")) {
			logger.debug("Detected MacOS/X");
			return new IPSecConnection_Racoon();
		}
		else {
			logger.warn("No IPSec secure channel implementation available for '" + osname + "', returning null");
			return null;
		}
	}
	

    ////////////////////// testing code begins here /////////////////////
    public static void main(String[] args) throws Exception {
    		byte[] key = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};

    		IPSecConnection c = IPSecConnection_Factory.getImplementation();
    		if (c == null) {
    			System.out.println("Could not get secure channel from IPSec factory");
    			return;
    		}
    		System.out.println("Created class " + c + " from factory");
    		
    		System.out.print("Starting connection to " + args[0] + ": ");
    		System.out.print("init=" + c.init(args[0], false));
    		System.out.println(", start=" + c.start(key, false));

    		System.in.read();
    		
    		System.out.println("Connection to " + args[0] + " is now " + c.isEstablished());

    		System.in.read();
    		
    		System.out.print("Stopping connection to " + args[0] + ": ");
    		System.out.println(c.stop());

    		System.in.read();
    		
    		System.out.println("Connection to " + args[0] + " is now " + c.isEstablished());
    }
}
