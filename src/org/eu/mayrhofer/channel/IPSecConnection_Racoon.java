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
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedList;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;
import org.apache.commons.codec.binary.*;

/** This is an implementation of a secure channel using the racoon IPSec implementation, which is
 * used by MacOSX, NetBSD, FreeBSD, and can be used under Linux and OpenBSD. The file "/etc/racoon/racoon.conf"
 * should include a line "include "/etc/racoon/remote/*.conf" ;"
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
class IPSecConnection_Racoon implements IPSecConnection {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(IPSecConnection_Racoon.class);

	/** To remember the remote host address that was passed in init(). 
	 * @see #init
	 */
	private String remoteHost = null;
	/** To remember the remoteNetwork and remoteNetmask parameters that were passed in init. 
	 * @see #init
	 */
	private String remoteNetwork;

	public IPSecConnection_Racoon() {
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

	/** Creates a new connection entry for racoon, sets the kernel security policies (in SPD), and tries to
	 * start that connection.
	 * 
	 * @param sharedSecret The PSK to use - this byte array will be HEX-encoded to form a textual representation.
	 * @param persistent Not supported right now. The security policies (in SPD) will only be temporary right now and
	 *                   will not persist a reboot.
	 */
	public boolean start(byte[] sharedSecret, boolean persistent) {
		if (remoteHost == null) {
			logger.error("Can not start connection, remoteHost not yet set");
			return false;
		}
		
		logger.debug("Trying to create " + (persistent ? "persistent" : "temporary") + 
				" ipsec connection to host " + remoteHost + (remoteNetwork != null ? " to remote network " + remoteNetwork : ""));
		// TODO: error checks on input parameters!
		
		// basically just create a new file and try to start the connection
		File configConn = new File("/etc/racoon/remote/" + remoteHost + ".conf");
		// but if it already exists, better not overwrite it....
		if (configConn.exists()) {
			logger.error("Unable to create IPSec connection to " + remoteHost + ": " + configConn + " already exists.");
			return false;
		}
		File configPsk = new File("/etc/racoon/psk.txt");
		// this file must already exist, we only append to it
		if (! configPsk.exists() || ! configPsk.canWrite()) {
			logger.error("Unable to create IPSec connection to " + remoteHost + ": " + configPsk + " does not exist or is not writable.");
			return false;
		}
		File configPskTmp = new File("/etc/racoon/psk.tmp");
		if (configPskTmp.exists()) {
			logger.error("Unable to create IPSec connection to " + remoteHost + ": " + configPskTmp + " already exists.");
			return false;
		}
		try {
			logger.info("Creating config file " + configConn);
			if (! configConn.createNewFile()) {
				logger.error("Unable to create IPSec connection to " + remoteHost + ": " + configConn + " could not be created.");
				return false;
			}
			logger.debug("Creating temporary file " + configPskTmp);
			if (! configPskTmp.createNewFile()) {
				logger.error("Unable to create IPSec connection to " + remoteHost + ": " + configPskTmp + " could not be created.");
				configConn.delete();
				return false;
			}

			BufferedWriter writerConn = new BufferedWriter(new FileWriter(configConn));
			
			writerConn.write("remote " + remoteHost + "\n");
			writerConn.write("{\n");
			writerConn.write("    exchange_mode main,aggressive;\n");
			writerConn.write("    doi ipsec_doi;\n");
			writerConn.write("    situation identity_only;\n");
			writerConn.write("    my_identifier address;\n");
			writerConn.write("    nonce_size 16;\n");
			writerConn.write("    lifetime time 1 hour;\n");
			writerConn.write("    initial_contact on;\n");
			writerConn.write("    proposal_check obey;\n");
			// we generate manually
			writerConn.write("    generate_policy off;\n");
			// TODO: this would be good for the Linux port, but is not supported under MacOSX
			writerConn.write("    #nat_traversal on;\n");
			writerConn.write("    proposal {\n");
			writerConn.write("        encryption_algorithm 3des;\n");
			writerConn.write("        hash_algorithm sha1;\n");
			writerConn.write("        authentication_method pre_shared_key;\n");
			writerConn.write("        dh_group modp1536;\n");
			writerConn.write("    }\n");
			writerConn.write("}\n");
			writerConn.flush();
			writerConn.close();

			BufferedWriter writerPskTmp = new BufferedWriter(new FileWriter(configPskTmp));
			// check if a PSK for this remote address is already in the file, if not, add it
			// (we basically just copy the file except the old line, if there, and append one line)
			BufferedReader readerPsk = new BufferedReader(new FileReader(configPsk));
			String line = readerPsk.readLine();
			while (line != null) {
				if (! line.startsWith(remoteHost))
					writerPskTmp.write(line + "\n");
				line = readerPsk.readLine();
			}
			readerPsk.close();
			writerPskTmp.write(remoteHost + " " + new String(Hex.encodeHex(sharedSecret)) + "\n");
			writerPskTmp.flush();
			writerPskTmp.close();
			// and move the temporary file to the one we want to change
			configPskTmp.renameTo(configPsk);
				
			// force racoon to reload its config, set the kernel policy, and try to start the connection
			try {
				// this is a hack to get correct file permissions....
				Command.executeCommand(new String[] {"chmod", "0600", configPsk.getCanonicalPath()}, null, null);
				
				Command.executeCommand(new String[] {"killall", "-HUP", "racoon"}, null, null);
				
				// this must unfortunately be done for every local ip....
				logger.info("Creating security policy entries for each of the local IP addresses");
				LinkedList allLocalAddrs = Helper.getAllLocalIps();
				while (allLocalAddrs.size() > 0) {
					String localAddr = (String) allLocalAddrs.removeFirst();
					String setkeyCmds;
					if (remoteNetwork == null) {
						setkeyCmds =
							"spdadd " + remoteHost + " " + localAddr + " any -P in ipsec esp/transport//use;\n" +
							"spdadd " + localAddr + " " + remoteHost + " any -P out ipsec esp/transport//use;\n";
					}
					else {
						setkeyCmds =
							"spdadd " + remoteNetwork + " " + localAddr + " any -P in ipsec esp/tunnel/" + remoteHost + "-" + localAddr + "/require;\n" +
							"spdadd " + localAddr + " " + remoteNetwork + " any -P out ipsec esp/tunnel/" + localAddr + "-" + remoteHost + "/require;\n";
					}
					Command.executeCommand(new String[] {"/usr/sbin/setkey", "-c"}, setkeyCmds, null);
				}
				logger.info("Established connection to " + remoteHost);
			}
			catch (ExitCodeException e) {
				logger.error("Command failed: " + e);
				// ignore it, because if one of the connections comes up, we are set
			}
		}
		catch (IOException e) {
			logger.error("Could not get list of local addresses: " + e);
			if (configConn.exists())
				configConn.delete();
			if (configPskTmp.exists())
				configPskTmp.delete();
			try {
				Command.executeCommand(new String[] {"killall", "-HUP", "racoon"}, null, null);
			} catch (Exception f) {}
			return false;
		}
		
		// since we can't explicitly start the connection with racoon, just return true here
		return true;
	}
	
	public boolean stop() {
		if (remoteHost == null) {
			logger.error("Unable to stop IPSec connection, it has not been initialized yet (don't know which host to act on)");
			return false;
		}
		
		File configConn = new File("/etc/racoon/remote/" + remoteHost + ".conf");
		if (! configConn.exists()) {
			logger.error("Unable to stop IPSec connection to " + remoteHost + ": " + configConn + " does not exists.");
			return false;
		}
		// TODO: we should also delete the PSK entry, but don't care right now

		if (! configConn.delete()) {
			logger.error("Unable to stop IPSec connection to " + remoteHost + ": " + configConn + " could not be deleted.");
			return false;
		}
		
		try {
			Command.executeCommand(new String[] {"killall", "-HUP", "racoon"}, null, null);
			// this must unfortunately be done for every local ip....
			logger.info("Deleting security policy entries for each of the local IP addresses");
			LinkedList allLocalAddrs = Helper.getAllLocalIps();
			while (allLocalAddrs.size() > 0) {
				String localAddr = (String) allLocalAddrs.removeFirst();
				String setkeyCmds;
				if (remoteNetwork == null) {
					setkeyCmds = 
						"spddelete " + remoteHost + " " + localAddr + " any -P in;\n" +
						"spddelete " + localAddr + " " + remoteHost + " any -P out;\n";
				}
				else {
					setkeyCmds =
						"spddelete " + remoteNetwork + " " + localAddr + " any -P in;\n" +
						"spddelete " + localAddr + " " + remoteNetwork + " any -P out;\n";
				}
				System.out.println(Command.executeCommand(new String[] {"/usr/sbin/setkey", "-c"}, setkeyCmds, null));
			}
			// this is a bit intrusive....
			Command.executeCommand(new String[] {"/usr/sbin/setkey", "-F"}, null, null);
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
			return false;
		}

		try {
			return getConnStatus(remoteHost) == 1;
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
	
	public void dispose() {
		if (remoteHost != null)
			stop();
	}

	/**constants for state */
	public static final int RUNNING = 1;
	public static final int STANDBY = 0;
	public static final int DEACTIVATED = -1;
	
    /** This method is taken from at.gibraltar.webadmin.modules.ipsec.Ipsec (and slightly modified).
     * 
     * possible values
     *  1 .. the connection is erouted
     *  0 .. the connection is unrouted
     * -1 .. the connection was not found
     * 
     * @return status of a single connection
     */
    protected int getConnStatus(String remoteHost) throws ExitCodeException,IOException {
        //getting current status output
		String autoStatus = Command.executeCommand(new String[] {"/usr/sbin/setkey", "-D"}, null, null);

        StringTokenizer strT = new StringTokenizer(autoStatus,"\n");
        String line = "";
        boolean foundIn = false, foundOut = false;
        while (strT.hasMoreElements() && (!foundIn || !foundOut)) {
			line = strT.nextToken();
			while (line != null && line.startsWith("\t") && strT.hasMoreElements())
				// just skip blank lines _before_ a block
				line = strT.nextToken();
			
			if (line != null && strT.hasMoreElements()) {
				String fromAddr = line.substring(0, line.indexOf(' '));
				String toAddr = line.substring(line.indexOf(' ')+1, line.length());
				// special case when there are no SA entried at all...
				if (fromAddr.equals("No") && toAddr.equals("SAD entries."))
					continue;
				
				logger.debug("Examining SA from address " + fromAddr + " to address " + toAddr);

				// the next line should be "esp mode=transport ..."
				line = strT.nextToken();
				if (! line.startsWith("\tesp mode=transport"))
					// if not, just ignore this block ...
					continue;
				// encryption and authentication algorithms (and current keys)

				line = strT.nextToken();
				if (! line.startsWith("\tE: "))
					// hmm, no encrytion algorithm yet, seems to be a candidate SA, but not an active one
					continue;
				String encAlg = line.substring(4, line.indexOf(' ', 4));

				line = strT.nextToken();
				if (! line.startsWith("\tA: "))
					continue;
				String authAlg = line.substring(4, line.indexOf(' ', 4));
				logger.debug("This SA seems to be active, using encryption algorithm " + encAlg + " and authentication algorithm " + authAlg);

				// and now the critical line, with the 4th field being the state information
				line = strT.nextToken();
				int pos = line.indexOf('=');
				pos = line.indexOf('=', pos+1);
				pos = line.indexOf('=', pos+1);
				pos = line.indexOf('=', pos+1);
				String state = line.substring(pos+1, line.length());
				logger.debug("This SA is in state " + state);
				if (state.startsWith("mature")) {
					if (fromAddr.startsWith(remoteHost)) {
						logger.debug("Found active incoming SA");
						foundIn = true;
					}
					if (toAddr.startsWith(remoteHost)) {
						logger.debug("Found active outgoing SA");
						foundOut = true;
					}
				}
			}
        }

        // TODO: not correct - to distinguish between STANDBY and DEACTIVATED, we also need to go through the SPD 
        return (foundIn && foundOut) ? RUNNING : STANDBY;
    }
}

