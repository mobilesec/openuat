/* Copyright Rene Mayrhofer
 * File created 2006-09-01
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import javax.bluetooth.RemoteDevice;
import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;

import org.apache.commons.codec.binary.Hex;
import org.apache.log4j.Logger;

/** This is a very simple class that uses the JSR82 API to open an RFCOMM channel
 * to a Bluetooth device. 
 *  
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class BluetoothRFCOMMChannel implements RemoteConnection {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger("org.openuat.util.BluetoothRFCOMMChannel" /*BluetoothRFCOMMChannel.class*/);
	
	/** The remote device address string, as passed to the constructor. */
	private String remoteDeviceAddress;
	/** The remote RFCOMM channel number (SDP number), as passed to the 
	 * constructor.
	 */
	private int remoteChannelNumber;
	/** This service URL gets constructed from remoteDeviceAddress and remoteChannelNumber
	 * in the constructor and is used in @see #open;
	 */
	private String serviceURL = null;
	
	/** After a call to @see #open, this will hold the connection object.
	 */
	private StreamConnection connection = null;
	/** Initialized by @see #open.
	 */
	private InputStream fromRemote = null;
	/** Initialized by @see #open.
	 */
	private OutputStream toRemote = null;
	
	/** Construct a Bluetooth RFCOMM channel object with a specific remote endpoint.
	 * This does not yet open the channel, @see open needs to be called for that.
	 * @param connectionURL The complete Bluetooth service URL, as returned e.g.
	 *                      by a service search.
	 * @throws IOException When the local Bluetooth stack was not initialized properly.
	 */
	public BluetoothRFCOMMChannel(String connectionURL) throws IOException {
		if (! BluetoothSupport.init()) {
			throw new IOException("Local Bluetooth stack was not initialized properly, can not construct channel objects");
		}

		// remember the parameter
		serviceURL = connectionURL;
		/* and try to parse the address and channel number parameters, so that
		 * getRemoteAddress will work even before opening the connection */
		if (serviceURL.startsWith("btspp://") &&
				serviceURL.indexOf(':', 8) == 20 &&
				serviceURL.indexOf(';') >= 22) {
			this.remoteDeviceAddress = serviceURL.substring(8, 20);
			this.remoteChannelNumber = Integer.parseInt(serviceURL.substring(21, serviceURL.indexOf(';')));
			if (logger.isDebugEnabled())
				logger.debug("Parsed remote device address '" + remoteDeviceAddress + 
					"' and channel " + remoteChannelNumber + " from URL '" +
					serviceURL + "'");
		}
		else
			logger.warn("Could not parse URL '" + serviceURL + 
					"', getRemoteAddress and getRemoteName will not work until a connection is established");
	}
	
	/** Construct a Bluetooth RFCOMM channel object with a specific remote endpoint.
	 * This does not yet open the channel, @see open needs to be called for that.
	 * 
	 * Note: This constructor, along with a dummy remoteChannelNumber (i.e. -1), 
	 * can be used as a reference object to compare against, e.g. when querying
	 * KeyManager for a key with a remote Bluetooth device.
	 * 
	 * @param remoteDeviceAddress The Bluetooth MAC address to connect to, in format
	 *                            "AABBCCDDEEFF".
	 * @param remoteChannelNumber The SDP RFCOMM channel number to connect to, usually between
	 *                            1 and 10.
	 * @throws IOException When the local Bluetooth stack was not initialized properly.
	 */
	public BluetoothRFCOMMChannel(String remoteDeviceAddress, int remoteChannelNumber) throws IOException {
		this("btspp://" + remoteDeviceAddress + ":" + remoteChannelNumber + 
			";authenticate=false;master=true;encrypt=false");
		
		// just remember the parameters
		this.remoteDeviceAddress = remoteDeviceAddress;
		this.remoteChannelNumber = remoteChannelNumber;
		logger.debug("Created RFCOMM channel object to remote device '" + this.remoteDeviceAddress +
				"' to SDP port "+ this.remoteChannelNumber);
	}

	/** Construct a Bluetooth RFCOMM channel object from a StreamConnection 
	 * that is already connected (this is used for server side). The channel
	 * can not be re-openend by calling open() after close() has been called.
	 * @throws IOException On Bluetooth errors.
	 */
	BluetoothRFCOMMChannel(StreamConnection connection) throws IOException {
		// also get the remote device information
		RemoteDevice remote = RemoteDevice.getRemoteDevice(connection);
		this.remoteDeviceAddress = remote.getBluetoothAddress();
		this.remoteChannelNumber = -1;
		logger.debug("Opening streams in already connected RFCOMM channel");
		
		this.connection = connection;
		fromRemote = connection.openInputStream();
		toRemote = connection.openOutputStream();
	}
	
	/** Opens a channel to the endpoint given to the constructor.
	 * @throws IOException On Bluetooth errors.
	 * @throws IOException When the channel has already been opened.
	 */
	// TODO: activate me again when J2ME polish can deal with Java5 sources!
	//@SuppressWarnings("static-access") // we really want the javax...Connector, and not the avetanebt!
	public boolean open() throws IOException {
		if (connection != null) {
			throw new IOException("Channel has already been opened");
		}
		if (serviceURL == null) {
			throw new IOException("Channel can not be opened, URL has not been set");
		}
		logger.debug("Opening RFCOMM channel to remote device '" + remoteDeviceAddress + 
				"' with port " + remoteChannelNumber + " with URL '" + serviceURL + "'");
		
		connection = (StreamConnection) Connector.open(serviceURL);
		fromRemote = connection.openInputStream();
		toRemote = connection.openOutputStream();
		return true;
	}
	
	/** Closes the channel to the endpoint given to the constructor. It may be
	 * re-opened with another call to @see #open.
	 * It is also an mplementation of RemoteConnection.close.
	 * @see RemoteConnection.close
	 * @throws IOException On Bluetooth errors.
	 * @throws IOException When the channel has not yet been opened.
	 */
	public void close() {
		if (connection == null || toRemote == null || fromRemote == null) {
			logger.error("RFCOMM channel has not been openend properly or been close already, can not close");
			return;
		}
		logger.debug("Closing RFCOMM channel to remote device '" + remoteDeviceAddress + 
				"' with port " + remoteChannelNumber);
		
    	try {
    		fromRemote.close();
    		toRemote.close();
    		connection.close();
		}
		catch (IOException e) {
   			// need to ignore here, nothing we can do about it...
   			logger.error("Unable to close streams cleanly", e);
		}
		fromRemote = null;
		toRemote = null;
		connection = null;
	}
	
	/** Returns the InputStream object for reading from the remote Bluetooth device.
	 * It is also an mplementation of RemoteConnection.getInputStream.
	 * @see RemoteConnection.getInputStream
	 * @return The InputStream object openend in @see #open.
	 * @throws IOException When the channel has not yet been opened.
	 */
	public InputStream getInputStream() throws IOException {
		if (connection == null || fromRemote == null) {
			throw new IOException("RFCOMM channel has not been opened properly");
		}
		
		// maybe apply decorator
		if (logger.isTraceEnabled())
			return new DebugInputStream(fromRemote, "org.openuat.util.BluetoothRFCOMMChannel_IN");
			
		return fromRemote;
	}

	/** Returns the OutputStream object for writing to the remote Bluetooth device.
	 * It is also an mplementation of RemoteConnection.getOutputStream.
	 * @see RemoteConnection.getOutputStream
	 * @return The OutputStream object openend in @see #open.
	 * @throws IOException When the channel has not yet been opened.
	 */
	public OutputStream getOutputStream() throws IOException {
		if (connection == null || toRemote == null) {
			throw new IOException("RFCOMM channel has not been opened properly");
		}
		
		// maybe apply decorator
		if (logger.isTraceEnabled())
			return new DebugOutputStream(toRemote, "org.openuat.util.BluetoothRFCOMMChannel_OUT");
			
		return toRemote;
	}

	/** Implementation of RemoteConnection.getRemoteAddress.
	 * @see RemoteConnection.getRemoteAddress
	 * @return A RemoteDevice object representing the Bluetooth device.
	 */
	public Object getRemoteAddress() throws IOException {
		if (connection != null)
			// connection already open, get the RemoteDevice object from it
			return RemoteDevice.getRemoteDevice(connection);
		else if (remoteDeviceAddress != null)
			// No connection open, need to work with remoteDeviceAddress here.
			/* But the JSR82 API makes the RemoteDevice(String) constructor 
			   protected, so can't use it. The best option is to simply return
			   a string object. */
			// TODO: do something about this! return something sensible!
			// no, don't do it! some callers may depend on this being a RemoteDevice object
			//return remoteDeviceAddress;
			return null;
		else
			return null;
	}
	
	/** This returns the remote device address as a string, as e.g. parsed
	 * by the constructor taking a connection URL. 
	 */
	public String getRemoteAddressString() {
		return remoteDeviceAddress;
	}

	/** Implementation of RemoteConnection.getRemoteName.
	 * @see RemoteConnection.getRemoteName
	 */
	public String getRemoteName() {
		try {
			if (connection != null)
				return BluetoothPeerManager.resolveName((RemoteDevice) getRemoteAddress());
			else
				return remoteDeviceAddress;
		} catch (IOException e) {
			// can't resolve - that's bad
			return null;
		}
	}

	/** This implementation of equals either compares either the connection
	 * objects (if set) or serviceURL.
	 */ 
	// TODO: activate me again when J2ME polish can deal with Java5 sources!
	//@Override
	public boolean equals(Object other) {
		if (other == null || !(other instanceof BluetoothRFCOMMChannel)) {
			if (logger.isDebugEnabled())
				logger.debug("equals called with object of wrong type");
			return false;
		}
		BluetoothRFCOMMChannel o = (BluetoothRFCOMMChannel) other;
		
		// already connected? if yes, this has precedence
		if (connection != null && o.connection != null) {
			if (logger.isDebugEnabled())
				logger.debug("Both connection objects set, comparing connection=" + 
						connection + ", o.connection=" + connection);
			return connection.equals(o.connection);
		}
		
		// not connected, compare serviceURL (because this will be used in open()
		// however, only compare serviceURL if both have a valid remote channel number
		if (serviceURL != null && o.serviceURL != null && 
				remoteChannelNumber != -1 && o.remoteChannelNumber != -1) {
			logger.debug("Both serviceURL objects set, comparing serviceURL=" + 
					serviceURL + ", o.serviceURL=" + serviceURL);
			return serviceURL.equals(o.serviceURL);
		}
		
		// ok, neither connected, nor do we have full serviceURLs, only compare device addresses (if available)
		if (remoteDeviceAddress != null && o.remoteDeviceAddress != null) {
			logger.debug("Both remoteDeviceAddress objects set, comparing remoteDeviceAddress=" +
					remoteDeviceAddress + ", o.remoteDeviceAddress=" + o.remoteDeviceAddress);
			return remoteDeviceAddress.equals(o.remoteDeviceAddress);
		}
		
		// don't know...
		logger.error("Trying to compare objects where neither both are connected nor both have serviceURL or remoteDeviceAddress set. For what I know, they are different.");
		return false;
	}
	
	public String toString() {
		if (connection != null)
			try {
				return "BluetoothRFCOMMChannel connected to " + getRemoteAddress();
			} catch (IOException e) {
				return "BluetoothRFCOMMChannel connected, but unable to resolve address: " + e;
			}
		else if (serviceURL != null && remoteChannelNumber != -1)
			return "BluetoothRFCOMMChannel with URL " + serviceURL;
		else if (remoteDeviceAddress != null)
			return "BluetoothRFCOMMChannel with remote device " + remoteDeviceAddress;
		else
			return "BluetoothRFCOMMChannel with invalid/unknown endpoint";
	}

	   /**
	    * Shows information about the remote device (name, device class, BT address ..etc..)
	    */
	   /*public void getRemoteDevInfos() {
	   	 RemoteDevice rd = null;
	   	 String name = "unknown";
	   	 int rssi = 0;
		 int lq = 0;
	   	 try {
	     	rd = RemoteDevice.getRemoteDevice(streamCon);
			name = rd.getFriendlyName(false);
			rssi = Rssi.getRssi(rd.getBTAddress());
			lq = LinkQuality.getLinkQuality(rd.getBTAddress());
		} catch (Exception e) {
			showInfo (e.getMessage(), "Error");
			return;
		}
	     showInfo("Remote Device Address, Name, Rssi und Quality\n" + rd.getBluetoothAddress() + " " + name + " " + rssi + " " + lq,"Info");
	   }*/

	   /**
	    * Turns on/off the encryption of an existing ACL link
	    */
	   /*public void encryptLink() {
	     try {
	       RemoteDevice dev=((BTConnection)streamCon).getRemoteDevice();
	       dev.encrypt(streamCon, !m_encrypt.isEnabled());
	     }catch(Exception ex) {
	       showError(ex.getMessage());
	     }
	   }*/

	   /**
	    * Authenticates the remote device connected with the local device
	    */
	   /*public void authenticateLink() {
	     try {
	       RemoteDevice dev=((BTConnection)streamCon).getRemoteDevice();
	             
	       JOptionPane.showMessageDialog(this, "Authentification " + (dev.authenticate() ? "successfull" : "Non successfull"));
	     }catch(Exception ex) {
	       showError(ex.getMessage());
	     }
	   }*/

	   /**
	    * Switches the state of the local device between Master and Slave
	 * @throws IOException 
	 * @throws NumberFormatException 
	    */
	   /*public void switchMaster() {
	     try {
	       throw new Exception("not yet implemented!");
	     }catch(Exception ex) {
	       showError(ex.getMessage());
	     }

	   }*/

	
//#if cfg.includeTestCode
	///////////////////////////////////////// test code begins here //////////////////////
	protected static class TempHandler implements 
			org.openuat.authentication.AuthenticationProgressHandler,
			org.openuat.authentication.KeyManager.VerificationHandler {
		private boolean performMirrorAttack, requestSensorStream;
		
		TempHandler(boolean attack, boolean requestStream) {
			this.performMirrorAttack = attack;
			this.requestSensorStream = requestStream;
		}
		
		public void AuthenticationFailure(Object sender, Object remote, Exception e, String msg) {
			System.out.println("DH with " + remote + " failed: " + e + "/" + msg);
			System.exit(1);
		}

		public void AuthenticationProgress(Object sender, Object remote, int cur, int max, String msg) {
			System.out.println("DH with " + remote + " progress: " + cur + "/" + max + ": " + msg);
		}

		public void AuthenticationSuccess(Object sender, Object remote, Object result) {
			System.out.println("DH with " + remote + " SUCCESS");

	        Object[] res = (Object[]) result;
	        // remember the secret key shared with the other device
	        byte[] sharedKey = (byte[]) res[0];
	        // and extract the shared authentication key for phase 2
	        byte[] authKey = (byte[]) res[1];
	        logger.debug("Shared session key is now '" + new String(Hex.encodeHex(sharedKey)) + 
	        		"' with length " + sharedKey.length + 
	        		", shared authentication key is now '" + new String(Hex.encodeHex(authKey)) + 
	        		"' with length " + authKey.length);
	        // then extraxt the optional parameter
	        //String param = (String) res[2];
	        RemoteConnection connectionToRemote = (RemoteConnection) res[3];
	        
	        inVerificationPhase(connectionToRemote);
		}

		public void startVerification(byte[] sharedAuthenticationKey, String optionalParam, RemoteConnection toRemote) {
			inVerificationPhase(toRemote);
		}
		
		private void inVerificationPhase(RemoteConnection connectionToRemote) {
	        InputStream i;
	        OutputStream o;
			try {
				i = connectionToRemote.getInputStream();
				o = connectionToRemote.getOutputStream();
				OutputStreamWriter ow = new OutputStreamWriter(o);
				
				if (requestSensorStream) {
					ow.write("DEBG_Stream\n");
					ow.flush();
				}
				
		        int tmp = i.read();
				while (tmp != -1) {
					System.out.print((char) tmp);
					if (performMirrorAttack)
						o.write(tmp);
				  	tmp = i.read();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}

			System.exit(0);
		}
	}
	
	  public static void main(String[] args) throws IOException, NumberFormatException, InterruptedException {
		  BluetoothRFCOMMChannel c;
		  if (args[0].equals("URL"))
			  c = new BluetoothRFCOMMChannel(args[1]);
		  else
			  c = new BluetoothRFCOMMChannel(args[0], Integer.parseInt(args[1]));
		  c.open();
		  
		  if (args.length > 2 && args[2].equals("DH")) {
			  boolean attack = false, requestStream = false;
			  if (args.length > 3 && args[3].equals("mirror"))
				  attack = true;
			  if (args.length > 3 && args[3].equals("stream"))
				  requestStream = true;
			  // this is our test client, keep connected, and use JSSE (interoperability tests...)
			  org.openuat.authentication.HostProtocolHandler.startAuthenticationWith(
					 c, new TempHandler(attack, requestStream), true, null, true);
			  System.out.println("Waiting for protocol to run in the background");
			  while (true) Thread.sleep(500);
		  }
		  else {
			  InputStream i = c.getInputStream();
			  int tmp = i.read();
			  while (tmp != -1) {
				  System.out.print((char) tmp);
				  tmp = i.read();
			  }
		  }
	  }
//#endif	  
}

