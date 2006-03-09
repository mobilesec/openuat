package org.eu.mayrhofer.channel;

import java.io.File;

import org.apache.log4j.Logger;
import org.eu.mayrhofer.authentication.RelateAuthenticationProtocol;

/** This is a factory class for generating instances of SecureChannel based on IPSec. Based on the
 * running operating system and installed components, it will select the appropriate IPSecConnection_*
 * implementation to use. 
 * @author Rene Mayrhofer
 */
public class IPSecConnection_Factory {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(RelateAuthenticationProtocol.class);

	/** Returns the appropriate instance of the ipsec secure channel implementation. or null if
	 * no implementation is yet available for the running platform. 
	 */
	public static SecureChannel getImplementation() {
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
}
