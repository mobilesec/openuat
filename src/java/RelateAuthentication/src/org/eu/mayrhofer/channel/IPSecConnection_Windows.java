package org.eu.mayrhofer.channel;

import org.apache.log4j.Logger;
import org.apache.commons.codec.binary.*;

/** This is an implementation of a secure channel using the Windows 2000/XP implementation. It is
 * accessed via the ipsec2k library, which provides an API for creating the appropriate registry 
 * antries.
 * 
 * @author Rene Mayrhofer
 *
 */
public class IPSecConnection_Windows implements SecureChannel {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(IPSecConnection_Windows.class);

	/** To remember the remote host address that was passed in start(). */
	private String remoteHost = null;

	public IPSecConnection_Windows() {
	}
	
	/** Creates a new connection entry for Windows 2000/XP. This does not start the connection - it will be 
	 * started when the first matching packet triggers it.
	 * 
	 * @param remoteHost The IP address or host name of the remote host.
	 * @param sharedSecret The PSK to use - this byte array will be HEX-encoded to form a textual representation.
	 * @param persistent Not supported right now. The security policies (in SPD) will always be permanent right now.
	 */
	public boolean start(String remoteHost, byte[] sharedSecret, boolean persistent) {
		this.remoteHost = remoteHost;
		
		logger.debug("Trying to create " + (persistent ? "persistent" : "temporary") + " ipsec connection to host " + remoteHost);
		// TODO: error checks on input parameters!
		

		// since we can't explicitly start the connection with Windows, just return true here
		return true;
	}
	
	public boolean stop() {
		return true;
	}
	
	public boolean isEstablished() {
		return true;
	}

	protected native long createPolicyPsk(byte[] fromAddress, byte[] fromMask, byte[] toAddress, byte[] toMask, 
			byte[] fromGateway, byte[] toGateway, int cipher, int mac, boolean pfs, String psk);
	protected native long createPolicyCA(byte[] fromAddress, byte[] fromMask, byte[] toAddress, byte[] toMask, 
			byte[] fromGateway, byte[] toGateway, int cipher, int mac, boolean pfs, String caDn);
	protected native boolean activatePolicy(long id);
	protected native boolean deactivatePolicy(long id);
	protected native boolean removePolicy(long id);
    
    ////////////////////// testing code begins here /////////////////////
    public static void main(String[] args) throws Exception {
    		byte[] key = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
    	
    		System.out.print("Starting connection to " + args[0] + ": ");
    		IPSecConnection_Windows c = new IPSecConnection_Windows();
    		System.out.println(c.start(args[0], key, false));

    		System.in.read();
    		
    		System.out.println("Connection to " + args[0] + " is now " + c.isEstablished());

    		System.in.read();
    		
    		System.out.print("Stopping connection to " + args[0] + ": ");
    		System.out.println(c.stop());

    		System.in.read();
    		
    		System.out.println("Connection to " + args[0] + " is now " + c.isEstablished());
    }
}

