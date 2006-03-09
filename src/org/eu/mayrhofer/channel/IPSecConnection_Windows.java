package org.eu.mayrhofer.channel;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedList;

import org.apache.log4j.Logger;
import org.apache.commons.codec.binary.*;

/** This is an implementation of a secure channel using the Windows 2000/XP implementation. It is
 * accessed via the ipsec2k library, which provides an API for creating the appropriate registry 
 * entries.
 * 
 * @author Rene Mayrhofer
 *
 */
class IPSecConnection_Windows implements SecureChannel {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(IPSecConnection_Windows.class);
	
	/** These values are from types.h from the ipsec2k library. */
	private static final int CIPHER_3DES = 3;
	//private static final int CIPHER_DES = 1;
	//private static final int MAC_NONE = 0;
	//private static final int MAC_MD5 = 1;
	private static final int MAC_SHA1 = 2;
	//private static final int DHGROUP_LOW = 1;
	private static final int DHGROUP_MED = 2;

	/** To remember the remote host address that was passed in init(). 
	 * @see #init
	 */
	private String remoteHost = null;
	
	/** Remember the GUID of the IPSec policy created by start() to be able to remove it again on stop(). */ 
	private String policy = null;
	
	static {
		System.loadLibrary("ipsecJNI");
	}
	
	private byte[] addressStringToByteArray(String address) {
		try {
			InetAddress addr = InetAddress.getByName(address);
			
			// only deal with IPv4 addresses right now....
			if (addr instanceof Inet4Address) {
				return addr.getAddress();
			}
			else {
				logger.error("The passed address is not an IPv4 address");
				return null;
			}
		} catch (UnknownHostException e) {
			logger.error("Could not parse address or could not resolve hose name:" + e);
			return null;
		}
	}

	public IPSecConnection_Windows() {
	}
	
	/** Initializes an instance of a secure channel. This implementation only remembers
	 * remoteHost in the member variable.
	 * @see #remoteHost 
	 * 
	 * <b>This method must be called before any of the others.</b>
	 *
	 * @param remoteHost The IP address or host name of the remote host.
	 * @return true if the channel could be initialized, false otherwise. It will return
	 *         false if the channel has already been initialized previously.
	 */
	public boolean init(String remoteHost) {
		if (remoteHost != null)
			return false;
		
		this.remoteHost = remoteHost;
		return true;
	}

	/** Creates a new connection entry for Windows 2000/XP. This does not start the connection - it will be 
	 * started when the first matching packet triggers it.
	 * 
	 * @param sharedSecret The PSK to use - this byte array will be HEX-encoded to form a textual representation.
	 * @param persistent Not supported right now. The security policies (in SPD) will always be permanent right now.
	 */
	public boolean start(byte[] sharedSecret, boolean persistent) {
		logger.debug("Trying to create " + (persistent ? "persistent" : "temporary") + " ipsec connection to host " + remoteHost);
		// TODO: error checks on input parameters!

		long handle = createPolicyHandle(CIPHER_3DES, MAC_SHA1, DHGROUP_MED, 600);
		// here too: for each local address
		logger.info("Creating security policy entries for each of the local IP addresses");
		try {
			LinkedList allLocalAddrs = Helper.getAllLocalIps();
			byte[][] addrs = new byte[allLocalAddrs.size()][];
			for (int i=0; i<addrs.length; i++) {
				String localAddr = (String) allLocalAddrs.removeFirst();
				addPolicyPsk(handle,
						addressStringToByteArray(localAddr), addressStringToByteArray("255.255.255.255"),
						addressStringToByteArray(remoteHost), addressStringToByteArray("255.255.255.255"),
						addressStringToByteArray(localAddr), addressStringToByteArray(remoteHost), 
						CIPHER_3DES, MAC_SHA1, true, new String(Hex.encodeHex(sharedSecret)));
			}
			String policyId = registerPolicy(handle);
			if (policyId == null) {
				logger.error("Could not create IPSec policy to address " + remoteHost);
				return false;
			}
			logger.info("Created IPSec policy to address " + remoteHost + 
					" with GUID " + policyId + ", activating now");
			
			if (! activatePolicy(policyId)) {
				logger.error("Could not activate IPSec policy to address " + remoteHost + 
						" with GUID " + policyId);
				return false;
			}
			// remember the GUID for later removal
			this.policy = policyId;
			// since we can't explicitly start the connection with Windows, just return true here when at least
			// he policy could be activated (if not triggered)
			return true;
		}
		catch (IOException e) {
			logger.error("Could not get list of local addresses: " + e);
			return false;
		}
	}
	
	/** Returns true when all the policies that have been registered and activated by start() could be
	 * deactivated and removed, false otherwise. Also returns false when no policies have been installed
	 * previously.
	 */
	public boolean stop() {
		/*if (policies == null) {
			logger.error("Can not stop IPSec connections because no policies have been installed");
			return false;
		}
		
		while (policies.size() > 0) {
			String policyId = (String) policies.removeFirst();
			logger.info("Removing IPSec policy with GUID " + policyId);
			if (! deactivatePolicy(policyId)) {
				logger.error("Could not deactivate IPSec policy with GUID " + policyId);
				// ignore it here, deactivated the others
				continue;
			}
			if (! removePolicy(policyId)) {
				logger.error("Could not remove IPSec policy with GUID " + policyId);
				// ignore it here, remove the others
			}
		}
		
		return policies.size() == 0;*/
		
		if (policy == null) {
			logger.error("Can not stop IPSec connections because no policy has been installed");
			return false;
		}
		logger.info("Removing IPSec policy with GUID " + policy);
		if (! deactivatePolicy(policy)) {
			logger.error("Could not deactivate IPSec policy with GUID " + policy);
			return false;
		}
		if (! removePolicy(policy)) {
			logger.error("Could not remove IPSec policy with GUID " + policy);
			return false;
		}
		policy = null;
		return true;
	}
	
	public boolean isEstablished() {
		// TODO: how to check that??
		return true;
	}

	protected native long createPolicyHandle(int cipher, int mac, int dhgroup, int lifetime);
	protected native boolean addPolicyPsk(long handle, byte[] fromAddress, byte[] fromMask, byte[] toAddress, byte[] toMask, 
			byte[] fromGateway, byte[] toGateway, int cipher, int mac, boolean pfs, String psk);
	protected native boolean addPolicyCA(long handle, byte[] fromAddress, byte[] fromMask, byte[] toAddress, byte[] toMask, 
			byte[] fromGateway, byte[] toGateway, int cipher, int mac, boolean pfs, String caDn);
	protected native String registerPolicy(long handle);
	protected native boolean activatePolicy(String id);
	protected native boolean deactivatePolicy(String id);
	protected native boolean removePolicy(String id);
    
    ////////////////////// testing code begins here /////////////////////
    public static void main(String[] args) throws Exception {
    		byte[] key = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
    	
    		System.out.print("Starting connection to " + args[0] + ": ");
    		IPSecConnection_Openswan c = new IPSecConnection_Openswan();
    		System.out.print("init=" + c.init(args[0]));
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

