/* Copyright Rene Mayrhofer
 * File created 2006-02
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
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
 * @version 1.0
 */
class IPSecConnection_Windows implements IPSecConnection {
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
	/** To remember the remoteNetwork parameter that was passed in init(). 
	 * @see #init
	 */
	private String remoteNetwork;
	/** To remember the remoteNetmask parameter that was passed in init(). 
	 * @see #init
	 */
	private int remoteNetmask;
	
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
	 * @param useAsDefault If set to true, this channel will be used as default for all
	 *                     further communication. This means that instead of an IPSec
	 *                     transport connection, a tunnel connection with the remote subnet
	 *                     0.0.0.0/0 will be created, effectively routing all IP traffic
	 *                     through this connection.
	 *                     Set to false if in doubt.
	 * @return true if the channel could be initialized, false otherwise. It will return
	 *         false if the channel has already been initialized previously.
	 */
	public boolean init(String remoteHost, boolean useAsDefault) {
		if (! useAsDefault)
			return init(remoteHost, null, 0);
		else
			return init(remoteHost, "0.0.0.0", 0);
	}
	
	/** Initializes an instance of an IPSec connection. This implementation only remembers
	 * remoteHost, remoteNetwork and remoteNetmask in member variables. 
	 * 
	 * This method is an alternative to the init method defined by the SecureChannel
	 * interface. <b>Either of them must be called before any of the others.</b>
	 *
	 * @param remoteHost The remote host to establish the connection to. This string can 
	 *                   either be a hostname, or an IP (version 4 or 6) address.
	 * @param remoteNetwork The remote network behind the IPSec gateway specified with
	 *                      remoteHost, if any. This parameter may be null to indicate
	 *                      that no remote network should be used, but that the IPSec
	 *                      connection should be created only for reaching the remote
	 *                      host. Specifically, if this parameter is set to a network
	 *                      (in IPv4 or IPv6 address notation), then an IPsec <b>tunnel</b>
	 *                      connection will be created. If set to null, an IPSec
	 *                      <b>transport</b> connection will be created.
	 * @param remoteNetmask If remoteNetwork has been set, this parameter should be set
	 *                      to the remote netmask in CIDR notation, i.e. the number of bits
	 *                      that represent the remote network. It must be between 0 and 32
	 *                      for IPv4 remote networks and between 0 and 128 for IPv6 remote
	 *                      networks. If remoteNetwork is null, this parameter is ignored.
	 * @return true if the channel could be initialized, false otherwise. It will return
	 *         false if the channel has already been initialized previously.
	 */
	public boolean init(String remoteHost, String remoteNetwork, int remoteNetmask) {
		if (this.remoteHost != null) {
			logger.error("Can not initialize connection with remote '" + remoteHost + 
					"', already initialized with '" + this.remoteHost + "'");
			return false;
		}

		this.remoteHost = remoteHost;
		if (remoteNetwork != null) {
			this.remoteNetwork = remoteNetwork;
			this.remoteNetmask = remoteNetmask;
		}
		else {
			this.remoteNetwork = null;
			this.remoteNetmask = 0;
		}
		
		logger.info("Initialized with remote '" + this.remoteHost + "', network '" + this.remoteNetwork + "/" + remoteNetmask + "'");
		return true;
	}

	/** Creates a new connection entry for Windows 2000/XP. This does not start the connection - it will be 
	 * started when the first matching packet triggers it.
	 * 
	 * @param sharedSecret The PSK to use - this byte array will be HEX-encoded to form a textual representation.
	 * @param persistent Not supported right now. The security policies (in SPD) will always be permanent right now.
	 */
	public boolean start(byte[] sharedSecret, boolean persistent) {
		if (remoteHost == null) {
			logger.error("Can not start connection, remoteHost not yet set");
			return false;
		}

		logger.debug("Trying to create " + (persistent ? "persistent" : "temporary") + 
				" ipsec connection to host " + remoteHost + 
				(remoteNetwork != null ? " to remote network " + remoteNetwork + "/" + remoteNetmask : ""));
		// TODO: error checks on input parameters!

		long handle = createPolicyHandle(CIPHER_3DES, MAC_SHA1, DHGROUP_MED, 600);
		// here too: for each local address
		logger.info("Creating security policy entries for each of the local IP addresses");
		try {
			LinkedList allLocalAddrs = Helper.getAllLocalIps();
			byte[][] addrs = new byte[allLocalAddrs.size()][];
			for (int i=0; i<addrs.length; i++) {
				String localAddr = (String) allLocalAddrs.removeFirst();
				if (remoteNetwork == null) {
					addPolicyPsk(handle,
							addressStringToByteArray(localAddr), addressStringToByteArray("255.255.255.255"),
							addressStringToByteArray(remoteHost), addressStringToByteArray("255.255.255.255"),
							addressStringToByteArray(localAddr), addressStringToByteArray(remoteHost), 
							CIPHER_3DES, MAC_SHA1, true, new String(Hex.encodeHex(sharedSecret)));
				}
				else {
					addPolicyPsk(handle,
							addressStringToByteArray(localAddr), addressStringToByteArray("255.255.255.255"),
							addressStringToByteArray(remoteNetwork), addressStringToByteArray(convertCidrMaskToAddressStyle(remoteNetmask)),
							addressStringToByteArray(localAddr), addressStringToByteArray(remoteHost), 
							CIPHER_3DES, MAC_SHA1, true, new String(Hex.encodeHex(sharedSecret)));
				}
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
	
	/** This is a small helper method to convert from the CIDR style 
	 * network mask to the address style network mask formet, e.g. from
	 * 24 to "255.255.255.0".
	 *
	 * @param cidrMask The number of bits in the netmask.
	 * @return The netmask as address style string.
	 */
	protected String convertCidrMaskToAddressStyle(int cidrMask) {
		String ret = "";
		for (int i=0; i<4; i++) {
			int octet = 0;
			for (int j=0; j<8; j++) {
				if (cidrMask-- > 0)
					octet |= 1;
				octet <<= 1;
			}
			ret += octet;
			if (i<3)
				ret += ".";
		}
		
		logger.debug("Converted CIDR-style netmask '" + cidrMask + "' to address-style netmask '" + ret + "'");
		return ret;
	}

	protected static native long createPolicyHandle(int cipher, int mac, int dhgroup, int lifetime);
	protected static native boolean addPolicyPsk(long handle, byte[] fromAddress, byte[] fromMask, byte[] toAddress, byte[] toMask, 
			byte[] fromGateway, byte[] toGateway, int cipher, int mac, boolean pfs, String psk);
	protected static native boolean addPolicyCA(long handle, byte[] fromAddress, byte[] fromMask, byte[] toAddress, byte[] toMask, 
			byte[] fromGateway, byte[] toGateway, int cipher, int mac, boolean pfs, String caDn);
	protected static native String registerPolicy(long handle);
	protected static native boolean activatePolicy(String id);
	protected static native boolean deactivatePolicy(String id);
	protected static native boolean removePolicy(String id);
	
	/** This native method allows to import an X.509 certificate into the correct Windows certificate store
	 * for use with IPSec authentication.
	 * 
	 * @param file The file name of the certificate to import. It must point to a PKCS#12 encoded file that
	 *             contains the X.509 client certificate and the corresponding private key that should be used
	 *             for authentication as well as the CA certificate chain up to the root CA certificate that
	 *             represents the trusted path of the client certificate. The other end of the IPSec tunnel
	 *             must present a certificate that has been signed by the same CA as the client certificate
	 *             imported from this file.
	 * @param password The password necessary to decrypt the PKCS#12 file.
	 * @param overwriteExisting If true, existing certificates with the same common name and serial number and
	 *                          signed by the same CA will be overwritten.
	 * @return 0 if the certificates and the private key could be imported successfully, 
	 *         1 if the file could not be found or opened,
	 *         2 if the private key could not be decrypted (password mismatch),
	 *         3 if it could not be decoded,
	 *         4 if importing failed,
	 *         5 if (at least one of the) certificates existed already and overwriteExisting was set to false
	 *         5 if anything else went wrong (like parameter error).
	 */
	protected static native int importCertificate(String file, String password, boolean oveŕwriteExisting);
	
	
	
	/////////////////////////// Test code begins here ////////////////////////////
	public static void main(String[] args) {
		String file = args[0], pass = args[1];
		System.out.println("Trying to import certificates into certificate store from file '" + 
				file + "' with password '" + pass + "'");
		
		switch(importCertificate(file, pass, true)) {
		case 0: 
			System.out.println("success");
			break;
		case 1: 
			System.out.println("could not open or read file");
			break;
		case 2: 
			System.out.println("password mismatch");
			break;
		case 3: 
			System.out.println("decode error - is the file a PKCS#12 file?");
			break;
		case 4: 
			System.out.println("import error");
			break;
		case 5: 
			System.out.println("parameter/unspecified error");
			break;
		default: 
			System.out.println("ouch, should not be here");
			break;
		}
	}
}

