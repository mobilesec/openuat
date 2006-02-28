package org.eu.mayrhofer.channel;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;
import org.apache.commons.codec.*;
import org.apache.commons.codec.binary.*;

/** This is an implementation of a secure channel using the racoon IPSec implementation, which is
 * used by MacOSX, NetBSD, FreeBSD, and can be used under Linux and OpenBSD. The file "/etc/racoon/racoon.conf"
 * should include a line "include "/etc/racoon/remote/*.conf" ;"
 * 
 * @author Rene Mayrhofer
 *
 */
public class IPSecConnection_Racoon implements SecureChannel {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(IPSecConnection_Racoon.class);

	/** To remember the remote host address that was passed in start(). */
	private String remoteHost = null;

	public IPSecConnection_Racoon() {
	}
	
	/** Creates a new connection entry for racoon, sets the kernel security policies (in SPD), and tries to
	 * start that connection.
	 * 
	 * @param remoteHost The IP address or host name of the remote host.
	 * @param sharedSecret The PSK to use - this byte array will be HEX-encoded to form a textual representation.
	 * @param persistent Not supported right now. The security policies (in SPD) will only be temporary right now and
	 *                   will not persist a reboot.
	 */
	public boolean start(String remoteHost, byte[] sharedSecret, boolean persistent) {
		this.remoteHost = remoteHost;
		
		logger.debug("Trying to create " + (persistent ? "persistent" : "temporary") + " ipsec connection to host " + remoteHost);
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
		if (! configConn.exists() || ! configConn.canWrite()) {
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
				return false;
			}

			BufferedWriter writerConn = new BufferedWriter(new FileWriter(configConn));
			
			writerConn.write("remote " + remoteHost + "\n");
			writerConn.write("{\n");
			writerConn.write("    exchange_mode aggressive,main;");
			writerConn.write("    doi ipsec_doi;");
			writerConn.write("    situation identity_only;");
			writerConn.write("    my_identifier address;");
			writerConn.write("    nonce_size 16;");
			writerConn.write("    lifetime time 1 hour;");
			writerConn.write("    initial_contact on;");
			writerConn.write("    support_mip6 on;");
			writerConn.write("    proposal_check obey;");
			// TODO: this would be good for the Linux port, but is not supported under MacOSX
			writerConn.write("    #nat_traversal on;");
			writerConn.write("    proposal {");
			writerConn.write("        encryption_algorithm 3des;");
			writerConn.write("        hash_algorithm sha1;");
			writerConn.write("        authentication_method pre_shared_key;");
			writerConn.write("        dh_group 2;");
			writerConn.write("    }");
			writerConn.write("}\n");
			writerConn.flush();
			writerConn.close();

			BufferedWriter writerPskTmp = new BufferedWriter(new FileWriter(configConn));
			// check if a PSK for this remote address is already in the file, if not, add it
			// (we basically just copy the file except the old line, if there, and append one line)
			BufferedReader readerPsk = new BufferedReader(new FileReader(configPsk));
			String line = readerPsk.readLine();
			while (line != null) {
				if (! line.startsWith(remoteHost))
					writerPskTmp.write(line);
			}
			readerPsk.close();
			writerPskTmp.write(remoteHost + " " + new String(Hex.encodeHex(sharedSecret)) + "\n");
			writerPskTmp.flush();
			writerPskTmp.close();
			// and move the temporary file to the one we want to change
			//configPskTmp.renameTo(configPsk);
				
			// force racoon to reload its config, set the kernel policy, and try to start the connection
			try {
				Command.executeCommand(new String[] {"killall", "-HUP", "racoon"}, null);
				String setkeyCmds = 
					"spdadd " + remoteHost + " 0.0.0.0 any -P in ipsec esp/transport//use;\n" +
					"spdadd 0.0.0.0 " + remoteHost + " any -P out ipsec esp/transport//use;\n";
				Command.executeCommand(new String[] {"/usr/sbin/setkey", "-c"}, setkeyCmds);
				logger.info("Established connection from to " + remoteHost);
			}
			catch (ExitCodeException e) {
				logger.error("Command failed: " + e);
				// ignore it, because if one of the connections comes up, we are set
			}
		}
		catch (IOException e) {
			logger.error("Could not execute command: " + e);
			return false;
		}
		
		return isEstablished();
	}
	
	public boolean stop() {
		if (remoteHost == null) {
			logger.error("Unable to stop IPSec connection, it has not been started yet");
			return false;
		}
		
		File configConn = new File("/etc/remote/" + remoteHost + ".conf");
		if (! configConn.exists()) {
			logger.error("Unable to stop IPSec connection to " + remoteHost + ": " + configConn + " does not exists.");
			return false;
		}
		// TODO: we should also delete the PSK entry, but don't care right now

		try {
			if (! configConn.delete()) {
				logger.error("Unable to stop IPSec connection to " + remoteHost + ": " + configConn + " could not be deleted.");
				return false;
			}
			Command.executeCommand(new String[] {"killall", "-HUP", "racoon"}, null);
			Command.executeCommand(new String[] {"/usr/sbin/setkey", "-F"}, null);
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
		String autoStatus = Command.executeCommand(new String[] {"/usr/sbin/setkey", "-D"}, null);

        StringTokenizer strT = new StringTokenizer(autoStatus,"\n");
        String temp = "";
        boolean found = false;
        while (strT.hasMoreElements()) {
			temp = strT.nextToken();
            /*if (!found && temp.startsWith("000 \"" + label.trim() + "\"")) {
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
            }*/
        }
        
        // one more time because we could recognice that some connections might be running althoug
        // they are in "unrouted" state in the first block
		/*strT = new StringTokenizer(autoStatus,"\n");
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
		}   */     
        return retVal;
    }
    
    
    ////////////////////// testing code begins here /////////////////////
    public static void main(String[] args) throws Exception {
    		byte[] key = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
    	
    		System.out.print("Starting connection to " + args[0] + ": ");
    		IPSecConnection_Racoon c = new IPSecConnection_Racoon();
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

