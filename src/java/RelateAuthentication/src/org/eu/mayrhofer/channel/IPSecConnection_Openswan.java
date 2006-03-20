package org.eu.mayrhofer.channel;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedList;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;
import org.apache.commons.codec.binary.*;

/** This is an implementation of a secure channel using the openswan/strongswan/freeswan IPSec implementation
 * for Linux. It is independent of the kernel implementation (i.e. both KLIPS and 26sec will work), but assumes
 * the IKE-daemon "pluto" to be installed with the default locations. Additionally, the directory 
 * "/etc/ipsec.d/dynamic/" is assumed to exist, the file "/etc/ipsec.conf" should contain a line
 * "include /etc/ipsec.d/dynamic/*.conf", and the file "/etc/ipsec.secrets" should contain a line
 * "include /etc/ipsec.d/dynamic/*.psk".
 * 
 * @author Rene Mayrhofer
 *
 */
class IPSecConnection_Openswan implements SecureChannel {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(IPSecConnection_Openswan.class);

	private LinkedList ignoredConns = new LinkedList();
    /**constant for connection header*/
	public static final String CONN = "conn";
    /**constant for the left subnet*/
	public static final String LEFTSUBNET = "leftsubnet";
    /**constant for the right subnet*/
	public static final String RIGHTSUBNET = "rightsubnet";
	public static final String EROUTED = "erouted";
	public static final String EROUTED_HOLD = "erouted HOLD";
    public static final String UNROUTET = "unrouted";
    public static final String IPSEC_ESTABLISHED = "IPsec SA established";

	/** To remember the remote host address that was passed in init(). 
	 * @see #init
	 */
	private String remoteHost = null;
	/** To remember the useAsDefault parameter that was passed in init(). 
	 * @see #init
	 */
	private boolean useAsDefault;
	/** To remember if the connection is supposed to be persistent (used in dispose() to decide if to stop or not). */
	private boolean persistent = false;
	/** This remembers the local address used to create the IPSec connection. It is used for stop() and isEstablished(). */
	private String localAddr = null;

	public IPSecConnection_Openswan() {
		this.ignoredConns.add("private");
		this.ignoredConns.add("block");
		this.ignoredConns.add("private-or-clear");
		this.ignoredConns.add("clear");
		this.ignoredConns.add("packetdefault");
		this.ignoredConns.add("clear-or-private");
	}
	
	protected String createConnName(String localAddr, String remoteAddr) {
		return "auto-" + localAddr.replace('.', '_') + "-" + remoteAddr.replace('.', '_');
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
		if (this.remoteHost != null) {
			logger.error("Can not initialize connection with remote '" + remoteHost + 
					"', already initialized with '" + this.remoteHost + "'");
			return false;
		}

		this.remoteHost = remoteHost;
		this.useAsDefault = useAsDefault;
		logger.info("Initialized with remote '" + this.remoteHost + "'");
		return true;
	}
	
	/** Creates a new connection entry for openswan/strongswan/freeswan and tries to
	 * start that connection.
	 * 
	 * @param sharedSecret The PSK to use - this byte array will be HEX-encoded to form a textual representation.
	 * @param persistent Supported. If set to true, the connection will be set to auto=start, if set to false,
	 *                   it will be set to auto=add.
	 */
	public boolean start(byte[] sharedSecret, boolean persistent) {
		if (remoteHost == null) {
			logger.error("Can not start connection, remoteHost not yet set");
			return false;
		}

		this.persistent = persistent;
		
		logger.debug("Trying to create " + (persistent ? "persistent" : "temporary") + 
				" ipsec connection to host " + remoteHost + (useAsDefault ? " as default route" : ""));
		// TODO: error checks on input parameters!
		
		// basically just create a new file and try to start the connection
		File configConn = new File("/etc/ipsec.d/dynamic/" + remoteHost + ".conf");
		// but if it already exists, better not overwrite it....
		if (configConn.exists()) {
			logger.error("Unable to create IPSec connection to " + remoteHost + ": " + configConn + " already exists.");
			return false;
		}
		File configPsk = new File("/etc/ipsec.d/dynamic/" + remoteHost + ".psk");
		// but if it already exists, better not overwrite it....
		if (configConn.exists()) {
			logger.error("Unable to create IPSec connection to " + remoteHost + ": " + configPsk + " already exists.");
			return false;
		}
		try {
			logger.info("Creating config files " + configConn + " and " + configPsk);
			if (! configConn.createNewFile()) {
				logger.error("Unable to create IPSec connection to " + remoteHost + ": " + configConn + " could not be created.");
				return false;
			}
			BufferedWriter writerConn = new BufferedWriter(new FileWriter(configConn));
			if (! configPsk.createNewFile()) {
				logger.error("Unable to create IPSec connection to " + remoteHost + ": " + configConn + " could not be created.");
				configConn.delete();
				return false;
			}
			BufferedWriter writerPsk = new BufferedWriter(new FileWriter(configPsk));
			
			// hmpf, this is a bad hack for openswan, because it can't deal with %defaultroute in the .secrets file - damn it
			logger.info("Creating one connection description for each of the local IP addresses");
			LinkedList allLocalAddrs = Helper.getAllLocalIps();
			while (allLocalAddrs.size() > 0) {
				// for each local address, create one configuration block
				String localAddr = (String) allLocalAddrs.removeFirst();
				logger.debug("Using local address " + localAddr);
				
				writerConn.write("conn " + createConnName(localAddr, remoteHost) + "\n");
				writerConn.write("    left=" + localAddr + "\n");
				// this is necessary so that the certificate ID isn't used for the ipsec.secrets lookup
				writerConn.write("    leftcert=\n");
				writerConn.write("    authby=secret\n");
				writerConn.write("    right=" + remoteHost + "\n");
				writerConn.write("    auto=" + (persistent ? "start" : "add") + "\n");
				if (!useAsDefault) {
					writerConn.write("    type=transport\n");
				}
				else {
					writerConn.write("    type=tunnel\n");
					writerConn.write("    rightsubnet=0.0.0.0/0");
				}
				writerConn.flush();

				writerPsk.write(localAddr + " " + remoteHost + " : PSK \"" + new String(Hex.encodeHex(sharedSecret)) + "\"\n");
				writerPsk.flush();
				
				// reload the secrets and try to start the connection
				try {
					Command.executeCommand(new String[] {"/usr/sbin/ipsec", "secrets"}, null, null);
					Command.executeCommand(new String[] {"/usr/sbin/ipsec", "auto", "--add", createConnName(localAddr, remoteHost)}, null, null);
					try {
						Command.executeCommand(new String[] {"/usr/sbin/ipsec", "auto", "--asynchronous", "--up", createConnName(localAddr, remoteHost)}, null, null);
					}
					catch (ExitCodeException e) {
						logger.debug("Trying to take ipsec up resulted in error code different from 0:" + e);
					}
					this.localAddr = localAddr;
					writerConn.close();
					writerPsk.close();
					logger.info("Established connection from " + localAddr + " to " + remoteHost);
					return isEstablished();
				}
				catch (ExitCodeException e) {
					logger.error("Command failed: " + e);
					// ignore it, because if one of the connections comes up, we are set
					try {
						Command.executeCommand(new String[] {"/usr/sbin/ipsec", "auto", "--delete", createConnName(localAddr, remoteHost)}, null, null);
					} catch (ExitCodeException f) {}
				}
			}
			writerConn.close();
			writerPsk.close();
			// none of the connections came up
			logger.error("None of the connections could be established, cleaning up");
			configConn.delete();
			configPsk.delete();
			try {
				Command.executeCommand(new String[] {"/usr/sbin/ipsec", "secrets"}, null, null);
			} catch (ExitCodeException f) {}
			return false;
		}
		catch (IOException e) {
			logger.error("Could not execute command, handle files, or get list of local addresses: " + e);
			if (configConn.exists())
				configConn.delete();
			if (configConn.exists())
				configPsk.delete();
			try {
				Command.executeCommand(new String[] {"/usr/sbin/ipsec", "secrets"}, null, null);
			} catch (Exception f) {}
			return false;
		}
	}
	
	public boolean stop() {
		if (remoteHost == null) {
			logger.error("Unable to stop IPSec connection, it has not been initialized yet (don't know which remote host to work on)");
			return false;
		}
		
		File configConn = new File("/etc/ipsec.d/dynamic/" + remoteHost + ".conf");
		if (! configConn.exists()) {
			logger.error("Unable to stop IPSec connection to " + remoteHost + ": " + configConn + " does not exists.");
			return false;
		}
		File configPsk = new File("/etc/ipsec.d/dynamic/" + remoteHost + ".psk");
		if (! configPsk.exists()) {
			logger.error("Unable to stop IPSec connection to " + remoteHost + ": " + configPsk + " does not exists.");
			return false;
		}

		try {
			if (localAddr != null)
				Command.executeCommand(new String[] {"/usr/sbin/ipsec", "auto", "--delete", createConnName(localAddr, remoteHost)}, null, null);
			else
				logger.info("Skipping to take the connection down, it does not seem to have been started.");
		}
		catch (ExitCodeException e) {
			logger.error("Could not execute command: " + e);
			// ignore it here and go on to delete the files
		}
		catch (IOException e) {
			logger.error("Could not execute command: " + e);
			// dt.
		}

		if (! configConn.delete()) {
			logger.error("Unable to stop IPSec connection to " + remoteHost + ": " + configConn + " could not be deleted.");
			return false;
		}
		if (! configPsk.delete()) {
			logger.error("Unable to stop IPSec connection to " + remoteHost + ": " + configPsk + " could not be deleted.");
			return false;
		}
		
		try {
			Command.executeCommand(new String[] {"/usr/sbin/ipsec", "secrets"}, null, null);
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
		if (remoteHost == null || localAddr == null) {
			return false;
		}

		try {
			return getConnStatus(createConnName(localAddr, remoteHost)) == 1;
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
		if (remoteHost != null && !persistent)
			stop();
	}

	/**constants for state */
	public static final int RUNNING = 1;
	public static final int STANDBY = 0;
	public static final int DEACTIVATED = -1;
	
    /** This method is taken from at.gibraltar.webadmin.modules.ipsec.Ipsec (and slightly modified).
     * 
     * returns the status of the connection named like "label"
     * 
     * possible values
     *  1 .. the connection is erouted
     *  0 .. the connection is unrouted
     * -1 .. the connection was not found
     * 
     * @param label of the connection
     * @return status of a single connection
     */
    protected int getConnStatus(String label) throws ExitCodeException,IOException {
    	//todo: change command
		int retVal = -1;
        //getting current status output
		String autoStatus = Command.executeCommand(new String[] {"/usr/sbin/ipsec", "auto", "--status"}, null, null);

        StringTokenizer strT = new StringTokenizer(autoStatus,"\n");
        String temp = "";
        boolean found = false;
        while (strT.hasMoreElements()) {
			temp = strT.nextToken();
            if (!found && temp.startsWith("000 \"" + label.trim() + "\"")) {
            	// 18.7.2005 - we have also to look for the keyword "erouted HOLD"
            	if (temp.indexOf(UNROUTET)!=-1 || temp.indexOf(EROUTED_HOLD)!=-1) {
                    found = true;
                    retVal = STANDBY;
                } else if (temp.indexOf(EROUTED)!=-1) {
					found = true;
                    retVal = RUNNING;
                }  else {
                    retVal = DEACTIVATED;
                }
            }
        }
        
        // one more time because we could recognice that some connections might be running althoug
        // they are in "unrouted" state in the first block
		strT = new StringTokenizer(autoStatus,"\n");
		temp = "";
		found = false;
		while (strT.hasMoreElements()) {
			temp = strT.nextToken();
			if (!found && temp.startsWith("000 #")) {
				if (temp.indexOf(label)!=-1 && temp.indexOf(IPSEC_ESTABLISHED)!=-1) {
					found = true;
					retVal = RUNNING;
				}
			}
		}        
        return retVal;
    }
}
