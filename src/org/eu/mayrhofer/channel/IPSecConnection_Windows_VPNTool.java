package org.eu.mayrhofer.channel;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedList;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;
import org.apache.commons.codec.binary.*;

/** This is an implementation of a secure channel using Windows 2000/XP and the VPNtool
 * command line tool, which in turn invokes ipsecpol/ipseccmd command line tools. 
 * 
 * @author Rene Mayrhofer
 *
 */
public class IPSecConnection_Windows_VPNTool implements SecureChannel {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(IPSecConnection_Windows_VPNTool.class);

	/** To remember the remote host address that was passed in start(). */
	private String remoteHost = null;
	private String tempPath;

	protected String createConnName(String localAddr, String remoteAddr) {
		return "auto-" + localAddr.replace('.', '_') + "-" + remoteAddr.replace('.', '_');
	}
	
	public IPSecConnection_Windows_VPNTool() {
		tempPath=System.getenv("TEMP") + "\\";
		logger.debug("Temporary path used is '" + tempPath + "'");
	}
	
	/** Creates a new connection entry for Windows XP by creating a config file for and calling
	 * ipsec.exe.
	 * 
	 * @param remoteHost The IP address or host name of the remote host.
	 * @param sharedSecret The PSK to use - this byte array will be HEX-encoded to form a textual representation.
	 * @param persistent Unsupported. It connections will always be persistent until removed again.
	 */
	public boolean start(String remoteHost, byte[] sharedSecret, boolean persistent) {
		this.remoteHost = remoteHost;
		
		logger.debug("Trying to create " + (persistent ? "persistent" : "temporary") + " ipsec connection to host " + remoteHost);
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
				writerConn.write("    type=transport\n");
				writerConn.write("    pfs=yes\n");
				writerConn.write("    network=lan\n");
				writerConn.write("    right=" + remoteHost + "\n");
				// need to use start here unconditionally!
				writerConn.write("    auto=" + "start" /*(persistent ? "start" : "add")*/ + "\n");
				writerConn.write("    presharedkey=" + new String(Hex.encodeHex(sharedSecret)) + "\n");
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
			logger.error("Unable to stop IPSec connection, it has not been started yet");
			return false;
		}
		
		File configConn = new File(tempPath + "ipsec.conf");
		if (! configConn.exists()) {
			logger.error("Unable to stop IPSec connection to " + remoteHost + ": " + configConn + " does not exists.");
			return false;
		}

		try {
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

    
    ////////////////////// testing code begins here /////////////////////
    public static void main(String[] args) throws Exception {
    		byte[] key = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
    	
    		System.out.print("Starting connection to " + args[0] + ": ");
    		IPSecConnection_Windows_VPNTool c = new IPSecConnection_Windows_VPNTool();
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

