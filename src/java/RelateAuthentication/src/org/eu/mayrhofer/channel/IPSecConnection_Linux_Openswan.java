package org.eu.mayrhofer.channel;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedList;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;
import org.apache.commons.codec.*;
import org.apache.commons.codec.binary.*;

public class IPSecConnection_Linux_Openswan implements SecureChannel {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(IPSecConnection_Linux_Openswan.class);

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
	
	private String remoteHost;

	public IPSecConnection_Linux_Openswan() {
		this.ignoredConns.add("private");
		this.ignoredConns.add("block");
		this.ignoredConns.add("private-or-clear");
		this.ignoredConns.add("clear");
		this.ignoredConns.add("packetdefault");
		this.ignoredConns.add("clear-or-private");
	}
	
	/** Creates a new connection entry for openswan/strongswan/freeswan and tries to
	 * start that connection.
	 * 
	 * @param remoteHost The IP address or host name of the remote host.
	 * @param sharedSecret The PSK to use - this byte array will be BASE64-encoded to form a textual representation.
	 * @param persistent Supported. If set to true, the connection will be set to auto=start, if set to false,
	 *                   it will be set to auto=add.
	 */
	public boolean start(String remoteHost, byte[] sharedSecret, boolean persistent) {
		this.remoteHost = remoteHost;
		
		logger.debug("Trying to create " + (persistent ? "persistent" : "temporary") + " ipsec connection to host " + remoteHost);
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
			if (! configConn.createNewFile()) {
				logger.error("Unable to create IPSec connection to " + remoteHost + ": " + configConn + " could not be created.");
				return false;
			}
			BufferedWriter writerConn = new BufferedWriter(new FileWriter(configConn));
			writerConn.write("conn " + remoteHost + "\n");
			writerConn.write("    left=%defaultroute\n");
			writerConn.write("    authby=secret\n");
			writerConn.write("    type=transport\n");
			writerConn.write("    right=" + remoteHost);
			writerConn.write("    auto=" + (persistent ? "start" : "add"));

			if (! configPsk.createNewFile()) {
				logger.error("Unable to create IPSec connection to " + remoteHost + ": " + configConn + " could not be created.");
				return false;
			}
			BufferedWriter writerPsk = new BufferedWriter(new FileWriter(configPsk));
			writerPsk.write("%any " + remoteHost + new String(Hex.encodeHex(sharedSecret)));
		
			// reload the secrets and start the connection
			Command.executeCommand(new String[] {"/usr/sbin/ipsec", "secrets"}, null);
			Command.executeCommand(new String[] {"/usr/sbin/ipsec", "auto", "--add", remoteHost}, null);
			Command.executeCommand(new String[] {"/usr/sbin/ipsec", "auto", "--start", remoteHost}, null);
		}
		catch (ExitCodeException e) {
			logger.error("Could not execute command: " + e);
			return false;
		}
		catch (IOException e) {
			logger.error("Could not execute command: " + e);
			return false;
		}
		
		return isEstablished();
	}
	
	public boolean stop() {
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
			Command.executeCommand(new String[] {"/usr/sbin/ipsec", "auto", "--delete", remoteHost}, null);
			if (! configConn.delete()) {
				logger.error("Unable to stop IPSec connection to " + remoteHost + ": " + configConn + " could not be deleted.");
				return false;
			}
			if (! configPsk.delete()) {
				logger.error("Unable to stop IPSec connection to " + remoteHost + ": " + configConn + " could not be deleted.");
				return false;
			}
			Command.executeCommand(new String[] {"/usr/sbin/ipsec", "secrets"}, null);
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
    private int getConnStatus(String label) throws ExitCodeException,IOException {
    	//todo: change command
		int retVal = -1;
        //getting current status output
		String[] command = {"/usr/sbin/ipsec","auto","--status"};
		String autoStatus = Command.executeCommand(new String[] {"/usr/sbin/ipsec", "auto", "--status"}, null);

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
    
    
    ////////////////////// testing code begins here /////////////////////
    public static void main(String[] args) throws Exception {
    		byte[] key = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
    	
    		System.out.print("Starting connection to " + args[0] + ": ");
    		IPSecConnection_Linux_Openswan c = new IPSecConnection_Linux_Openswan();
    		System.out.println(c.start(args[0], key, false));

    		Thread.sleep(10000);
    		
    		System.out.print("Connection to " + args[0] + "is now " + c.isEstablished());

    		Thread.sleep(10000);
    		
    		System.out.print("Stopping connection to " + args[0] + ": ");
    		System.out.println(c.stop());

    		Thread.sleep(10000);
    		
    		System.out.print("Connection to " + args[0] + "is now " + c.isEstablished());
    }
}

