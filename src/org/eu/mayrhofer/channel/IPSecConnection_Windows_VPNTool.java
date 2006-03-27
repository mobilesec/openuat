/* Copyright Rene Mayrhofer
 * File created 2006-02
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.channel;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedList;

import org.apache.log4j.Logger;
import org.apache.commons.codec.binary.*;

/** This is an implementation of a secure channel using Windows 2000/XP and the VPNtool
 * command line tool, which in turn invokes ipsecpol/ipseccmd command line tools. 
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
class IPSecConnection_Windows_VPNTool implements IPSecConnection {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(IPSecConnection_Windows_VPNTool.class);

	/** To remember the remote host address that was passed in init(). 
	 * @see #init(String, String, int)
	 */
	private String remoteHost = null;
	/** To remember the remoteNetwork and remoteNetmask parameters that were passed in init. 
	 * @see #init(String, String, int)
	 */
	private String remoteNetwork;
	/** To store the temporary path where to create the config file. */
	private String tempPath;

	protected String createConnName(String localAddr, String remoteAddr) {
		return "auto-" + localAddr.replace('.', '_') + "-" + remoteAddr.replace('.', '_');
	}
	
	public IPSecConnection_Windows_VPNTool() {
		//tempPath=System.getenv("TEMP") + "\\";
		tempPath = System.getProperty("java.io.tmpdir") + "\\";
		logger.debug("Temporary path used is '" + tempPath + "'");
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
		if (remoteNetwork != null)
			this.remoteNetwork = remoteNetwork + "/" + remoteNetmask;
		else
			this.remoteNetwork = null;
		
		logger.info("Initialized with remote '" + this.remoteHost + "', network '" + this.remoteNetwork + "'");
		return true;
	}

	/** Creates a new connection entry for Windows XP by creating a config file for and calling
	 * ipsec.exe.
	 * 
	 * @param sharedSecret The PSK to use - this byte array will be HEX-encoded to form a textual representation.
	 * @param persistent Unsupported. It connections will always be persistent until removed again.
	 */
	public boolean start(byte[] sharedSecret, boolean persistent) {
		return start(sharedSecret, null, persistent);
	}
	
	/** Creates a new connection entry for Windows XP by creating a config file for and calling
	 * ipsec.exe.
	 * 
	 * @param caDistinguishedName The CA that is used to sign the certificates, can be null
	 *                            to accept any valid certificate.
	 * @param persistent Supported. If set to true, the connection will be set to auto=start, if set to false,
	 *                   it will be set to auto=add.
	 */
	public boolean start(String caDistinguishedName, boolean persistent) {
		return start(null, caDistinguishedName, persistent);
	}
	
	/** This is the base implementation for the two public start methods.
	 * Either sharedSecret of caDistinguishedName must be null, can't use both! 
	 * (But both can be null, this will indicate X.509 certificate authentication
	 * with any valid certificate.)
	 */
	private boolean start(byte[] sharedSecret, String caDistinguishedName, boolean persistent) {
		if (remoteHost == null) {
			logger.error("Can not start connection, remoteHost not yet set");
			return false;
		}

		logger.debug("Trying to create " + (persistent ? "persistent" : "temporary") + 
				" ipsec connection to host " + remoteHost + (remoteNetwork != null ? " to remote network " + remoteNetwork : ""));
		// TODO: error checks on input parameters!
		
		// basically just create a new file and try to start the connection
		File configConn = new File(tempPath + "ipsec.conf");
		// but if it already exists, better not overwrite it....
		if (configConn.exists()) {
			logger.error("Unable to create IPSec connection to " + remoteHost + ": " + configConn + " already exists.");
			return false;
		}
		try {
			logger.info("Creating config file " + configConn);
			if (! configConn.createNewFile()) {
				logger.error("Unable to create IPSec connection to " + remoteHost + ": " + configConn + " could not be created.");
				return false;
			}
			BufferedWriter writerConn = new BufferedWriter(new FileWriter(configConn));

			logger.info("Creating one connection description for each of the local IP addresses");
			LinkedList allLocalAddrs = Helper.getAllLocalIps();
			while (allLocalAddrs.size() > 0) {
				// for each local address, create one configuration block
				String localAddr = (String) allLocalAddrs.removeFirst();
				logger.debug("Using local address " + localAddr);
				
				writerConn.write("conn " + createConnName(localAddr, remoteHost) + "\n");
				writerConn.write("    left=" + localAddr + "\n");
				// this is necessary so that the certificate ID isn't used for the ipsec.secrets lookup
				writerConn.write("    authmode=SHA1\n");
				writerConn.write("    pfs=yes\n");
				writerConn.write("    network=lan\n");
				writerConn.write("    right=" + remoteHost + "\n");
				// need to use start here unconditionally!
				writerConn.write("    auto=" + "start" /*(persistent ? "start" : "add")*/ + "\n");
				if (sharedSecret != null) {
					writerConn.write("    presharedkey=" + new String(Hex.encodeHex(sharedSecret)) + "\n");
				}
				else {
					writerConn.write("    rightca=\"" + caDistinguishedName + "\"\n");
				}
				if (remoteNetwork == null) {
					writerConn.write("    type=transport\n");
				}
				else {
					writerConn.write("    type=tunnel\n");
					writerConn.write("    rightsubnet=" + remoteNetwork + "\n");
				}
				writerConn.flush();
			}
			writerConn.close();
				
			try {
				Command.executeCommand(new String[] {"ipsec"}, null, tempPath);
				return true;
			}
			catch (ExitCodeException e) {
				logger.error("Command failed: " + e);
				// ignore it, because if one of the connections comes up, we are set
			}
			logger.error("Could not create IPSec policy");
			return false;
		}
		catch (IOException e) {
			logger.error("Could not execute command or get list of local addresses: " + e);
			return false;
		}
	}
	
	public boolean stop() {
		if (remoteHost == null) {
			logger.error("Unable to stop IPSec connection, it has not been inisialized yet (don't know which host to act on)");
			return false;
		}
		
		File configConn = new File(tempPath + "ipsec.conf");
		if (! configConn.exists()) {
			logger.error("Unable to stop IPSec connection to " + remoteHost + ": " + configConn + " does not exists.");
			return false;
		}

		try {
			Command.executeCommand(new String[] {"ipsec", "-off"}, null, tempPath);
			Command.executeCommand(new String[] {"ipsec", "-delete"}, null, tempPath);
			if (! configConn.delete()) {
				logger.error("Unable to stop IPSec connection to " + remoteHost + ": " + configConn + " could not be deleted.");
				return false;
			}
		}
		catch (ExitCodeException e) {
			logger.error("Could not execute command: " + e);
			return false;
		}
		catch (IOException e) {
			logger.error("Could not execute command: " + e);
			return false;
		}
		
		return true;
		//return !isEstablished();
	}
	
	public boolean isEstablished() {
		// how to check?
		return remoteHost != null;
	}

	/** This implementation does nothing at the moment. 
	 * TODO: implement me
	 */
	public int importCertificate(String file, String password, boolean overwriteExisting) {
		return -1;
	}
	
	public void dispose() {
		stop();
	}
}

