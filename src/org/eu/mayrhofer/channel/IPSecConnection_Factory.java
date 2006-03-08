package org.eu.mayrhofer.channel;

/** This is a factory class for generating instances of SecureChannel based on IPSec. Based on the
 * running operating system and installed components, it will select the appropriate IPSecConnection_*
 * implementation to use. 
 * @author Rene Mayrhofer
 */
public class IPSecConnection_Factory {
	/** Returns the appropriate instance of the ipsec secure channel implementation. or null if
	 * no implementation is yet available for the running platform. 
	 */
	public static SecureChannel getImplementation() {
		String osname = System.getProperty("os.name");
		
		if (osname.startsWith("Windows CE"))
			// no implementation for Windows CE/PocketPC
			return null;
		// TODO: how to detect windows XP/2000 explicitly?
		else if (osname.startsWith("Windows"))
			return new IPSecConnection_Windows_VPNTool();
		else if (osname.startsWith("Linux"))
			// TODO: distinguish between openswan and racoon under linux 
			return new IPSecConnection_Openswan();
		else if (osname.startsWith("Mac OS/X"))
			return new IPSecConnection_Racoon();
		else
			return null;
	}
}
