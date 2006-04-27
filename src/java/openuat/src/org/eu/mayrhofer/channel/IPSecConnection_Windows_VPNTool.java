/* Copyright Rene Mayrhofer
 * File created 2006-02
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.channel;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.util.LinkedList;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.apache.commons.codec.binary.*;

/** This is an implementation of a secure channel using Windows 2000/XP and the VPNtool
 * command line tool, which in turn invokes ipsecpol/ipseccmd command line tools. 
 * 
 * This class expects the ipsec.exe command to tbe executable within the system path,
 * so it is recommended to either augment the PATH variable or copy the simple ipsec.exe
 * file e.g. to %SystemRoot%\System32. Additionally, this tool requires the Windows XP
 * support tools to be installed for Windows XP or the ipsecpol.exe to be installed for
 * Windows 2000. The latter can be downloaded from 
 * http://www.microsoft.com/windows2000/techinfo/reskit/tools/existing/ipsecpol-o.asp,
 * and the files Ipsecpol.exe, Ipsecutil.dll, and Text2pol.dll need to be installed
 * and executable from the system path, e.g. again into %SystemRoot%\System32.
 * The latter can not be downloaded without checking for a valid Windows XP license
 * (from http://support.microsoft.com/?kbid=838079), but it is contained on the Windows 
 * XP install CD under \SUPPORT\TOOLS. During installation, the "Complete" installation 
 * option needs to be selected! Then simply copy the ipseccmd.exe again to the system 
 * path, e.g. %SystemRoot%\System32.
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
	private String remoteNetwork = null;
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
	 * @param caDistinguishedName The CA that is used to sign the certificates. It must be set,
	 *                            because Windows will only use X.509 authentication when the
	 *                            CA is explicitly specified. This string must be exactly in the
	 *                            form accepted by Windows, e.g.
	 *                            "C=AT,S=Upper Austria,L=Steyr,O=Gibraltar,OU=Gibraltar development,CN=Gibraltar firewall CA (created at 1143805227),Email=not specified"
	 *                            or "CN=My Test CA", depending on which fields are specified in
	 *                            the certificate. The first example can be used as reference for
	 *                            the names and the order of the fields. 
	 * @param persistent Supported. If set to true, the connection will be set to auto=start, if set to false,
	 *                   it will be set to auto=add.
	 */
	public boolean start(String caDistinguishedName, boolean persistent) {
		return start(null, caDistinguishedName, persistent);
	}
	
	/** This is the base implementation for the two public start methods.
	 * Either sharedSecret of caDistinguishedName must be null, can't use both! 
	 * 
	 * @see #start(byte[], boolean)
	 * @see #start(String, boolean)
	 */
	private boolean start(byte[] sharedSecret, String caDistinguishedName, boolean persistent) {
		if (remoteHost == null) {
			logger.error("Can not start connection, remoteHost not yet set");
			return false;
		}
		if (sharedSecret == null && caDistinguishedName == null) {
			logger.error("Either sharedSecret or caDistringuishedName must be set");
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
				// this doesn't work - need to find out why
				//writerConn.write("    rekey=120s/10000K");
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
					// catch a special case: the any-subnet
					if (remoteNetwork.equals("0.0.0.0/0")) {
						logger.info("Detected special remote network '" + remoteNetwork + 
								"', setting to '*'");
						writerConn.write("    rightsubnet=*\n");
					}
					else
						writerConn.write("    rightsubnet=" + remoteNetwork + "\n");
				}
				writerConn.flush();
			}
			writerConn.close();
				
			try {
				Command.executeCommand(new String[] {"ipsec", "-nosleep"}, null, tempPath);
				// can not return isEstablished() here, because the actual connection can be delayed
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
		
		return !isEstablished();
	}
	
	public boolean isEstablished() {
		if (remoteHost == null) {
			logger.warn("Warning: isEstablished called before start, returning false");
			return false;
		}
		
		try {
			String ret = Command.executeCommand(new String[] {"ipseccmd", "show", "sas"}, null, null);
			SecurityAssociation[] sas = new SecurityAssociationParser(ret).parse();
			if (sas == null) {
				logger.error("Could not parse output of ipseccmd");
				return false;
			}
			
			// and just check if "our" connection is among the returned
			for (int i=0; i<sas.length; i++) {
				if (sas[i].gatewayTo.equals(remoteHost) && 
						(remoteNetwork == null || sas[i].networkTo.equals(remoteNetwork))) {
					logger.info("Connection to host " + remoteHost + 
							(remoteNetwork != null ? ", network " + remoteNetwork : "") +
							" found: local SPI " + sas[i].localSpi + 
							", peer SPI " + sas[i].peerSpi);
					return true;
				}
			}
			// not found
			return false;
		}
		catch (ExitCodeException e) {
			logger.error("Could not execute command: " + e);
			return false;
		}
		catch (IOException e) {
			logger.error("Could not execute command: " + e);
			return false;
		}
	}
	
	private class SecurityAssociation {
		/** These are the tunnel endpoints (gateways), printed for Main Mode SA. */
		public String gatewayFrom, gatewayTo;
		/** These are the networks/addresses for the tunnel, printed for Quick Mode SA. */
		public String networkFrom, networkTo;
		/** Contains the description of the main mode offer (e.g. "3DES SHA1  DH Group 2"), 
		 * printed for Main Mode SA. */
		public String mainOffer;
		/** Contains the description of the quick mode offer (e.g. 
		 * "Encryption 3DES MD5 (24bytes/0rounds) (16secbytes/0secrounds)", 
		 * printed for Quick Mode SA. */
		public String quickOffer;
		/** The local SPI value for this SA. */
		public long localSpi;
		/** The remote SPI value for this SA. */
		public long peerSpi;
		/** The maximum lifetime of the quick mode SA in KBytes, printed for Main Modes SA. */
		public int quickLifetimeKB;
		/** The maximum lifetime of the quick mode SA in seconds, printed for Main Modes SA. */
		public int quickLifeTimeSec;
		/** Contains the authentication description for main mode (e.g. "RSA (Cert) Signature"). */
		public String mainAuth;
		/** These two strings are used to match Main Mode SA with Quick Mode SA. */
		public String initiatorCookie, responderCookie;
		/** True if PFS is used, false otherwise. */
		public boolean pfs;
		// TODO: add support for parsing transport-mode connections
		// TODO: add support for more specifiy protocol filter parsing
	}
	
	private class SecurityAssociationParser {
		private BufferedReader reader;
		
		public SecurityAssociationParser(String ipseccmdOutput) {
			if (ipseccmdOutput == null) {
				logger.error("Received null input");
				throw new IllegalArgumentException("Can not deal with null String");
			}

			reader = new BufferedReader(new StringReader(ipseccmdOutput));
		}
		
		// gets the next significant line (skipping blank and dashed lines)
		private String getNextLine() throws IOException {
			String line = reader.readLine();
			while (line != null && (line.equals("") || line.startsWith("----")))
				line = reader.readLine();
			return line;
		}
		
		public SecurityAssociation[] parse() throws IOException {
			Vector mainSas = new Vector(), allSas = new Vector();

			String line = getNextLine();
			if (line.equals("Main Mode SAs")) {
				logger.debug("Found start of Main Mode SAs");
			}
			else {
				logger.error("Expected start of Main Mode SAs, but got '" + line + "'");
				return null;
			}
			
			line = getNextLine();
			while (line != null && line.startsWith("Main Mode SA #")) {
				SecurityAssociation sa = new SecurityAssociation();
				int saNum = Integer.parseInt(line.substring(14, 
						line.indexOf(':')));
				logger.debug("Parsing Main Mode SA number " + saNum);
				line = getNextLine();
				if (!line.startsWith(" From ")) {
					logger.error("Expected From description in Main mode, but got '" + line + "'");
					return null;
				}
				sa.gatewayFrom = line.substring(6);
				logger.info("  from " + sa.gatewayFrom);
				
				line = getNextLine();
				if (!line.startsWith("  To  ")) {
					logger.error("Expected To description in Main mode, but got '" + line + "'");
					return null;
				}
				sa.gatewayTo = line.substring(6);
				logger.debug("  to " + sa.gatewayTo);
				
				// skip the "Policy Id" line
				getNextLine();
				
				line = getNextLine();
				if (!line.equals(" Offer Used : ")) {
					logger.error("Expected Offer description in Main mode, but got '" + line + "'");
					return null;
				}
				line = getNextLine();
				sa.mainOffer = line.substring(1);
				logger.debug("  offer " + sa.mainOffer);
				line = getNextLine();
				sa.quickLifetimeKB = Integer.parseInt(line.substring(
						line.indexOf("Lifetime")+9, line.indexOf("Kbytes")));
				sa.quickLifeTimeSec = Integer.parseInt(line.substring(
						line.indexOf('/')+1, line.indexOf("seconds")));
				logger.debug("  lifetime " + sa.quickLifetimeKB + "KB/" + sa.quickLifeTimeSec + "s");
				
				line = getNextLine();
				if (!line.startsWith(" Auth Used : ")) {
					logger.error("Expected Auth description in Main mode, but got '" + line + "'");
					return null;
				}
				sa.mainAuth = line.substring(13);
				logger.debug("  auth " + sa.mainAuth);
				
				line = getNextLine();
				if (!line.startsWith(" Initiator cookie ")) {
					logger.error("Expected Initiator Cookie in Main mode, but got '" + line + "'");
					return null;
				}
				sa.initiatorCookie = line.substring(18);
				logger.debug("  initiator cookie " + sa.initiatorCookie);

				line = getNextLine();
				if (!line.startsWith(" Responder cookie ")) {
					logger.error("Expected Responder Cookie in Main mode, but got '" + line + "'");
					return null;
				}
				sa.responderCookie = line.substring(18);
				logger.debug("  responder cookie " + sa.responderCookie);
				
				// and skip the encap line
				line = getNextLine();
				
				// but remember the main SA
				mainSas.add(sa);
				
				line = getNextLine();
			}
			if (line.equals("No SAs")) {
				logger.debug("No Main Mode SAs set");
				line = getNextLine();
			}

			if (line.equals("Quick Mode SAs")) {
				logger.debug("Found start of Quick Mode SAs");
			}
			else {
				logger.error("Expected start of Quick Mode SAs, but got '" + line + "'");
				return null;
			}
			
			line = getNextLine();
			while (line != null && line.startsWith("Quick Mode SA #")) {
				int saNum = Integer.parseInt(line.substring(15, 
						line.indexOf(':')));
				logger.debug("Parsing Quick Mode SA number " + saNum);
				
				// skip Filter Id and next line
				getNextLine();
				getNextLine();
				
				line = getNextLine();
				if (!line.startsWith("  From ")) {
					logger.error("Expected From description in Quick mode, but got '" + line + "'");
					return null;
				}
				// can't find the matching main mode SA now, so remember temporarily
				String from = line.substring(7);
				logger.debug("  from " + from);
				
				line = getNextLine();
				if (!line.startsWith("   To  ")) {
					logger.error("Expected To description in Quick mode, but got '" + line + "'");
					return null;
				}
				String to = line.substring(7);
				// but correct special "Any" case
				if (to.equals("Any")) {
					logger.debug("Detected special 'Any' remote subnet, normalizing");
					to = "0.0.0.0/0";
				}
				logger.debug("  to " + to);
				
				// skip protocol filter and direction lines
				getNextLine();
				getNextLine();
				
				line = getNextLine();
				if (!line.startsWith("  Tunnel From ")) {
					logger.error("Expected Tunnel From description in Quick mode, but got '" + line + "'");
					return null;
				}
				String tunnelFrom = line.substring(14);
				logger.debug("  tunnel from " + tunnelFrom);
				
				line = getNextLine();
				if (!line.startsWith("  Tunnel  To  ")) {
					logger.error("Expected Tunnel To description in Quick mode, but got '" + line + "'");
					return null;
				}
				String tunnelTo = line.substring(14);
				logger.debug("  tunnel to " + tunnelTo);

				// skip the "Policy Id" line
				getNextLine();
				
				line = getNextLine();
				if (!line.equals(" Offer Used : ")) {
					logger.error("Expected Offer description in Quick mode, but got '" + line + "'");
					return null;
				}
				line = getNextLine();
				String quickOffer = line.substring(line.indexOf(":")+2);
				logger.debug("  offer " + quickOffer);
				line = getNextLine();
				long localSpi = Long.parseLong(line.substring(
						line.indexOf("MySpi")+6, line.indexOf("PeerSpi")-1));
				long peerSpi = Long.parseLong(line.substring(
						line.indexOf("PeerSpi")+8));
				logger.debug("  local spi " + localSpi + " peer spi " + peerSpi);

				line = getNextLine();
				if (!line.startsWith("\tPFS : ")) {
					logger.error("Expected PFS description in Quick mode, but got '" + line + "'");
					return null;
				}
				boolean pfs;
				if (line.substring(7, 11).equals("True"))
					pfs = true;
				else if (line.substring(7, 12).equals("False"))
					pfs = false;
				else {
					logger.error("Can't parse PFS value from line '" + line + "'");
					return null;
				}
				logger.debug("  pfs " + pfs);
				
				line = getNextLine();
				if (!line.startsWith(" Initiator cookie ")) {
					logger.error("Expected Initiator Cookie in Quick mode, but got '" + line + "'");
					return null;
				}
				String initiatorCookie = line.substring(18);
				logger.debug("  initiator cookie " + initiatorCookie);

				line = getNextLine();
				if (!line.startsWith(" Responder cookie ")) {
					logger.error("Expected Responder Cookie in Quick mode, but got '" + line + "'");
					return null;
				}
				String responderCookie = line.substring(18);
				logger.debug("  responder cookie " + responderCookie);
				
				// ok, parsed all fields, now try to find the matching Main Mode SA
				boolean matched = false;
				for (int i=0; i<mainSas.size(); i++) {
					SecurityAssociation sa = (SecurityAssociation) mainSas.get(i);
					logger.debug("Checking main mode SA for match: '" +
							sa.gatewayFrom + "' ?= '" + tunnelFrom + "', '" +
							sa.gatewayTo + "' ?= '" + tunnelTo + "', '" +
							sa.initiatorCookie + "' ?= '" + initiatorCookie + "', '" +
							sa.responderCookie + "' ?= '" + responderCookie + "'");
					if (sa.gatewayFrom.equals(tunnelFrom) &&
						sa.gatewayTo.equals(tunnelTo) &&
						sa.initiatorCookie.equals(initiatorCookie) &&
						sa.responderCookie.equals(responderCookie)) {
						logger.debug("Found matching Main Mode SA");
						sa.networkFrom = from;
						sa.networkTo = to;
						sa.quickOffer = quickOffer;
						sa.localSpi = localSpi;
						sa.peerSpi = peerSpi;
						sa.pfs = pfs;
						
						allSas.add(sa);
						matched = true;
						break;
					}
				}
				if (!matched) {
					logger.debug("Could not find matching Main Mode SA while parsing Quick Mode SA");
					// create a new SA
					SecurityAssociation sa = new SecurityAssociation();
					sa.gatewayFrom = tunnelFrom;
					sa.gatewayTo = tunnelTo;
					sa.initiatorCookie = initiatorCookie;
					sa.responderCookie = responderCookie;
					sa.networkFrom = from;
					sa.networkTo = to;
					sa.quickOffer = quickOffer;
					sa.localSpi = localSpi;
					sa.peerSpi = peerSpi;
					sa.pfs = pfs;
					allSas.add(sa);
				}
				
				line = getNextLine();
			}
			if (line.equals("No SAs")) {
				logger.debug("No Quick Mode SAs set");
				line = getNextLine();
			}
			
			// and the last sanity check
			if (line == null || !line.equals("The command completed successfully.")) {
				logger.error("Did not get final command completed message");
				return null;
			}
			
			SecurityAssociation[] saArray = new SecurityAssociation[allSas.size()];
			for (int i=0; i<allSas.size(); i++)
				saArray[i] = (SecurityAssociation) allSas.get(i);
			return saArray;
		}
	}
	
	/** This implementation calls IPSecConnection_Windows.importCertificate to use
	 * the native method to access the Windows certificate store.
	 */
	public int importCertificate(String file, String password, boolean overwriteExisting) {
		return IPSecConnection_Windows.nativeImportCertificate(file, password, overwriteExisting);
	}
	
	public void dispose() {
		stop();
	}
}

