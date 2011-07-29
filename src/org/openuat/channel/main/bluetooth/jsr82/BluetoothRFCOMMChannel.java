/* Copyright Rene Mayrhofer
 * File created 2006-09-01
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.channel.main.bluetooth.jsr82;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Enumeration;
import java.util.Vector;

import javax.bluetooth.RemoteDevice;
import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;

import org.apache.commons.codec.binary.Hex;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.openuat.channel.main.RemoteConnection;
import org.openuat.channel.main.bluetooth.BluetoothSupport;
import org.openuat.util.DebugInputStream;
import org.openuat.util.DebugOutputStream;

/** This is a very simple class that uses the JSR82 API to open an RFCOMM channel
 * to a Bluetooth device. 
 *  
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class BluetoothRFCOMMChannel implements RemoteConnection {
	/** The RemoteDevice constructor taking a Bluetooth MAC address is, for
	 * some weird reason, protected. Therefore, we need a small wrapper class
	 * just to be able to construct RemoteDevice objects from address strings.
	 */
	public static class RDevice extends RemoteDevice {
		public RDevice(String remoteAddress) {
			super(remoteAddress);
		}
	}
	
	/** Our logger. */
	private static Logger logger = Logger.getLogger("org.openuat.util.BluetoothRFCOMMChannel" /*BluetoothRFCOMMChannel.class*/);
	
	/** This is used to hold the list of all concurrently open (or trying to
	 * be opened) RFCOMM channels. open() will add channels to this list,
	 * close() will remove them, elements are of type BluetoothRFCOMMChannel.
	 * This is mainly there for cleanup purposes, to close all channels when
	 * exiting the application (and thus e.g. getting rid of hanging MIDlet
	 * states).
	 */
	private static Vector openChannels = new Vector();
	
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
	
	/** Returns all BluetoothRFCOMMChannel objects whose channel is currently
	 * open or is being tried to be opened.
	 * @return The array may be of length 0 when no channels are open, but 
	 *         will be != null.
	 */
	public static BluetoothRFCOMMChannel[] getOpenChannels() {
		BluetoothRFCOMMChannel[] ret = new BluetoothRFCOMMChannel[openChannels.size()];
		for (int i=0; i<openChannels.size(); i++)
			ret[i] = (BluetoothRFCOMMChannel) openChannels.elementAt(i);
		logger.info("Returning " + ret.length + " open BluetoothRFCOMMChannel objects");
		return ret;
	}

	/** This method can be used to speed up shutdown of the overall application
	 * by closing all RFCOMM channels that are still open at this time. 
	 */
	public static void shutdownAllChannels() {
		Enumeration e = openChannels.elements();
		while (e.hasMoreElements()) {
			BluetoothRFCOMMChannel c = (BluetoothRFCOMMChannel) e.nextElement();
			c.close();
		}
		
		if (openChannels.size() > 0)
			logger.severe("Unable to close all Bluetooth RFCOMM channels, some are left open. This should not happen!");
	}
	
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
				(serviceURL.indexOf(';') >= 22 || 
				 (serviceURL.length() <= 23 && serviceURL.indexOf(';') == -1))) {
			this.remoteDeviceAddress = serviceURL.substring(8, 20);
			int end = serviceURL.indexOf(';') > 0 ? serviceURL.indexOf(';') : serviceURL.length(); 
			this.remoteChannelNumber = Integer.parseInt(serviceURL.substring(21, end));
			if (logger.isLoggable(Level.FINER))
				logger.finer("Parsed remote device address '" + remoteDeviceAddress + 
					"' and channel " + remoteChannelNumber + " from URL '" +
					serviceURL + "'");
		}
		else
			logger.warning("Could not parse URL '" + serviceURL + 
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
		this(constructURL(remoteDeviceAddress, remoteChannelNumber));
		
		// just remember the parameters
		this.remoteDeviceAddress = remoteDeviceAddress;
		this.remoteChannelNumber = remoteChannelNumber;
		logger.finer("Created RFCOMM channel object to remote device '" + this.remoteDeviceAddress +
				"' to SDP port "+ this.remoteChannelNumber);
	}

	/** Construct a Bluetooth RFCOMM channel object from a StreamConnection 
	 * that is already connected (this is used for server side). The channel
	 * can not be re-openend by calling open() after close() has been called.
	 * @param connection The connection to the remote host that must already be
	 *                   open, and will also be used to query the remote address.
	 * @throws IOException On Bluetooth errors.
	 */
	BluetoothRFCOMMChannel(StreamConnection connection) throws IOException {
		this(connection, -1);
	}
	
	/** Construct a Bluetooth RFCOMM channel object from a StreamConnection 
	 * that is already connected (this is used for server side). In addition,
	 * a remote channel number to be used for "calling back" on this channel is
	 * specified (it can't be queried from the already open connection). The channel
	 * can be re-openend by calling open() after close() has been called.
	 * @param connection The connection to the remote host that must already be
	 *                   open, and will also be used to query the remote address.
	 * @param remoteChannelNumberForCallback The remote channel number to use 
	 *                                       when re-opening the channel after
	 *                                       closing it. This probably means to
	 *                                       reverse the original direction of
	 *                                       the channel (it's incoming when
	 *                                       this constructor is used, but calling
	 *                                       open will make it outgoing).
	 * @throws IOException On Bluetooth errors.
	 */
	BluetoothRFCOMMChannel(StreamConnection connection, int remoteChannelNumberForCallback) throws IOException {
		// also get the remote device information
		RemoteDevice remote = RemoteDevice.getRemoteDevice(connection);
		this.remoteDeviceAddress = remote.getBluetoothAddress();
		this.remoteChannelNumber = remoteChannelNumberForCallback;
		logger.finer("Opening streams in already connected RFCOMM channel");
		
		this.connection = connection;
		
		try {
		    fromRemote = connection.openInputStream();
		    toRemote = connection.openOutputStream();
		    // as this channel is already open, need to keep track of it
		    synchronized (openChannels) {
			openChannels.addElement(this);
		    }
		}
		catch (IOException e) {
			logger.info("Could not open streams on open connection to '" + 
				remoteDeviceAddress);
			throw e;
		}
	}

	/** Just a small helper function to construct the correct URL string. */
	private static String constructURL(String remoteDeviceAddress, int remoteChannelNumber) {
//		return "btspp://" + remoteDeviceAddress + ":" + remoteChannelNumber + 
//		";authenticate=false;master=true;encrypt=false";
		return "btspp://" + remoteDeviceAddress + ":" + remoteChannelNumber + 
		";authenticate=false;master=false;encrypt=false";
	}
	
	/** Opens a channel to the endpoint given to the constructor.
	 * The method is synchronized, because opening might block for some time
	 * and we don't want to do it twice for the same host.
	 * @throws IOException On Bluetooth errors.
	 * @throws IOException When the channel has already been opened.
	 */
	//@SuppressWarnings("static-access") // we really want the javax...Connector, and not the avetanebt!
	public synchronized boolean open() throws IOException {
		if (connection != null) {
			throw new IOException("Channel has already been opened");
		}
		if (serviceURL == null) {
			throw new IOException("Channel can not be opened, URL has not been set");
		}
		logger.finer("Opening RFCOMM channel to remote device '" + remoteDeviceAddress + 
				"' with port " + remoteChannelNumber + " with URL '" + serviceURL + "'");

		// before blocking, add to our list
		synchronized (openChannels) {
			// sanity check
			if (openChannels.contains(this)) {
				openChannels.removeElement(this);
//				logger.warning("This BluetoothRFCOMMChannel object to " + 
//						remoteDeviceAddress + 
//						" does not seem to have an open connection, but is already in openChannels. This should not happen, aborting connection attempt!");
//				return false;
			}
			openChannels.addElement(this);
		}
		try {
			logger.info("Trying to reopen connection");
			// this can take some time...
			connection = (StreamConnection) Connector.open(serviceURL);
			fromRemote = connection.openInputStream();
			toRemote = connection.openOutputStream();
		}
		catch (IOException e) {
			logger.info("Could not establish connection to '" + serviceURL +
					"', removing from list of open connections again");
			synchronized (openChannels) {
				openChannels.removeElement(this);
			}
			throw e;
		}
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
		if (connection == null) {
			logger.finer("RFCOMM channel has not been opened properly or been closed already, can not close");
			return;
		}
		logger.finer("Closing RFCOMM channel to remote device '" + remoteDeviceAddress + 
				"' with port " + remoteChannelNumber);
		
    	try {
    		if (fromRemote != null)
    			fromRemote.close();
    		if (toRemote != null)
    			toRemote.close();
    		connection.close();
		}
		catch (IOException e) {
   			// need to ignore here, nothing we can do about it...
   			logger.log(Level.SEVERE, "Unable to close streams cleanly", e);
		}
		finally {
			// remove from the list of open channels again
			synchronized (openChannels) {
				if (!openChannels.contains(this)) {
					logger.severe("This BluetoothRFCOMMChannel object to " + 
							remoteDeviceAddress + 
							" seems to have an open connection, but is not in openChannels. This should not happen!");
				}
				else
					openChannels.removeElement(this);
			}
		}
		fromRemote = null;
		toRemote = null;
		connection = null;
	}
	
	/** Implementation of RemoteConnection.isOpen. */
	public boolean isOpen() {
		if (connection == null || fromRemote == null || toRemote == null) {
			logger.finer(this + " is not open because connection, fromRemote, or toRemote are null: "+connection+"-"+fromRemote+"-"+toRemote);
			return false;
		}
		try {
			fromRemote.available();
		}
		catch (IOException e) {
			logger.finer(this + " is not open because fromRemote.available threw an exception: " + e);
			return false;
		}
		
		try {
			toRemote.flush();
		}
		catch (IOException e) {
			logger.finer(this + " is not open because toRemote.flush threw an exception: " + e);
			return false;
		}
		
		/* and another test that seems to do the trick at least on J2ME on 
		 * Symbian: this returns address "000000000000" when the remote 
		 * side has closed the channel.
		 */
		try {
			String reportedAddr = RemoteDevice.getRemoteDevice(connection).getBluetoothAddress();
			if (reportedAddr.equals("000000000000")) {
				logger.severe(this + " is not open because getBluetoothAddress() from connection returns the 0-address");
				return false;
			}
			if (!reportedAddr.equals(remoteDeviceAddress)) {
				logger.severe("The reported getBluetoothAddress() from connection (" + 
						reportedAddr + ") differs from the one this BluetoothRFCOMMChannel was constructed with (" +
						remoteDeviceAddress + "). This is not fatal but should not happen in any case. Please investigate.");
			}
			return true;
		} catch (IOException e) {
			logger.finer(this + " is not open because getRemoteDevice(connection) threw an exception: " + e);
			return false;
		}
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
		if (logger.isLoggable(Level.FINEST))
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
		if (logger.isLoggable(Level.FINEST))
			return new DebugOutputStream(toRemote, "org.openuat.util.BluetoothRFCOMMChannel_OUT");
			
		return toRemote;
	}

	/** Implementation of RemoteConnection.getRemoteAddress.
	 * @see RemoteConnection.getRemoteAddress
	 * @return A String object with the remote Bluetooth device address. It
	 *         would be nicer to return a RemoteDevice object in every case, 
	 *         but we can't due to the RemoteDevice(String) constructor being
	 *         protected in the JSR81 API. Go complain to its authors.
	 */
	public Object getRemoteAddress() /*throws IOException*/ {
		/* Actually, why would we ever want to use it? Every constructor sets
		 * remoteDeviceAddress anyway. Scratch that here, just return it!
		 */
		/*if (isOpen() && System.getProperty( "microedition.platform") == null)
			// connection already open, get the RemoteDevice object from it
			return RemoteDevice.getRemoteDevice(connection).getBluetoothAddress();
		else if (remoteDeviceAddress != null)*/
			// No connection open, need to work with remoteDeviceAddress here.
			/* But the JSR82 API makes the RemoteDevice(String) constructor 
			   protected, so can't use it. The best option is to simply return
			   a string object. */
			return remoteDeviceAddress;
		/*else
			return null;*/
	}
	
	/** Implementation of RemoteConnection.getRemoteName.
	 * @see RemoteConnection.getRemoteName
	 */
	public String getRemoteName() {
		try {
			if (isOpen()) { 
				String name = BluetoothPeerManager.resolveName(RemoteDevice.getRemoteDevice(connection));
				/* Special handling here, mostly for J2ME: when the other side
				 * has already closed the connection, then getBluetoothAddress()
				 * on RemoteDevice - used as fallback in resolveName - will 
				 * return this special "null-address" string. Catch and replace
				 * by the known address instead.
				 */
				if (name.equals("000000000000"))
					name = remoteDeviceAddress;
				return name;
			}
			else
				return remoteDeviceAddress;
		} catch (IOException e) {
			// can't resolve - that's bad
			return null;
		}
	}

	/** Returns the remote channel number. */
	public int getRemoteChannelNumber() {
		return remoteChannelNumber;
	}
	 
	/** Sets the remote channel number that will be used in the next open() call. */
	public void setRemoteChannelNumber(int remoteChannelNumber) {
		this.remoteChannelNumber = remoteChannelNumber;
		serviceURL = constructURL(remoteDeviceAddress, remoteChannelNumber); 
	}
	
	/** This implementation of equals either compares either the connection
	 * objects (if set) or serviceURL.
	 */ 
	//@Override
	public boolean equals(Object other) {
		if (other == null || !(other instanceof BluetoothRFCOMMChannel)) {
			if (logger.isLoggable(Level.FINER))
				logger.finer("equals called with object of wrong type");
			return false;
		}
		BluetoothRFCOMMChannel o = (BluetoothRFCOMMChannel) other;
		
		// already connected? if yes, this has precedence
		if (connection != null && o.connection != null) {
			if (logger.isLoggable(Level.FINER))
				logger.finer("Both connection objects set, comparing connection=" + 
						connection + ", o.connection=" + connection);
			return connection.equals(o.connection);
		}
		
		// not connected, compare serviceURL (because this will be used in open()
		// however, only compare serviceURL if both have a valid remote channel number
		// also ignore the parameters in the serviceURL
		if (serviceURL != null && o.serviceURL != null && 
				remoteChannelNumber != -1 && o.remoteChannelNumber != -1) {
			logger.finer("Both serviceURL objects set, comparing serviceURL=" + 
					serviceURL + ", o.serviceURL=" + serviceURL);
			String s1 = serviceURL.indexOf(';') > 0 ? serviceURL.substring(0, serviceURL.indexOf(';')) : serviceURL;
			String s2 = o.serviceURL.indexOf(';') > 0 ? o.serviceURL.substring(0, o.serviceURL.indexOf(';')) : o.serviceURL;
			return s1.equals(s2);
		}
		
		// ok, neither connected, nor do we have full serviceURLs, only compare device addresses (if available)
		if (remoteDeviceAddress != null && o.remoteDeviceAddress != null) {
			logger.finer("Both remoteDeviceAddress objects set, comparing remoteDeviceAddress=" +
					remoteDeviceAddress + ", o.remoteDeviceAddress=" + o.remoteDeviceAddress);
			return remoteDeviceAddress.equals(o.remoteDeviceAddress);
		}
		
		// don't know...
		logger.severe("Trying to compare objects where neither both are connected nor both have serviceURL or remoteDeviceAddress set. For what I know, they are different.");
		return false;
	}

	/** Override hashCode so as to provide the same integer when two objects 
	 * are equal as defined by the overrode equals implementation. In this 
	 * case, simply return the hashcode of the Bluetooth MAC address (as 
	 * String), or 0 if no address is known.
	 */
	//@Override
	public int hashCode() {
		/*String remoteAddr = null;
		try {
			remoteAddr = (String) getRemoteAddress();
		}
		catch (IOException e) {
			logger.severe("Couldn't query remote address of connection while trying to generate hash code, will return 0");
		}
		
		if (remoteAddr == null)
			// no address known - let the hashtable call equals if necessary...
			return 0;
		else
			return remoteAddr.hashCode();*/
		
		return remoteDeviceAddress.hashCode();
	}

	public String toString() {
		if (connection != null)
			//try {
				return "BluetoothRFCOMMChannel connected to " + getRemoteAddress();
			/*} catch (IOException e) {
				return "BluetoothRFCOMMChannel connected, but unable to resolve address: " + e;
			}*/
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

		public boolean AuthenticationStarted(Object sender, Object remote) {
			System.out.println("DH with " + remote + " started");
			return true;
		}

		public void AuthenticationSuccess(Object sender, Object remote, Object result) {
			System.out.println("DH with " + remote + " SUCCESS");

	        Object[] res = (Object[]) result;
	        // remember the secret key shared with the other device
	        byte[] sharedKey = (byte[]) res[0];
	        // and extract the shared authentication key for phase 2
	        byte[] authKey = (byte[]) res[1];
	        System.out.println("Shared session key is now '" + new String(Hex.encodeHex(sharedKey)) + 
	        		"' with length " + sharedKey.length + 
	        		", shared authentication key is now '" + new String(Hex.encodeHex(authKey)) + 
	        		"' with length " + authKey.length);
	        // then extraxt the optional parameter
	        //String param = (String) res[2];
	        RemoteConnection connectionToRemote = (RemoteConnection) res[3];
	        if (connectionToRemote != null) {
	        	System.out.println("Connection to remote is still open, mirroring on it");
	        	inVerificationPhase(connectionToRemote, true);
	        }
		}

		public void startVerification(byte[] sharedAuthenticationKey, String optionalParam, RemoteConnection toRemote) {
			if (((BluetoothRFCOMMChannel) toRemote).isOpen()) {
	        	System.out.println("Called for verification and connection to remote is still open, mirroring on it");
				inVerificationPhase(toRemote, true);
			}
		}
		
		void inVerificationPhase(RemoteConnection connectionToRemote, boolean exitAfterClosing) {
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

			if (exitAfterClosing)
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
					 c, new TempHandler(attack, requestStream), 20000, true, null, true);
			  System.out.println("Waiting for protocol to run in the background");
			  while (true) Thread.sleep(500);
		  }
		  else {
			  InputStream i = c.getInputStream();
			  if (args.length > 2 && args[2].equals("stream")) {
				  OutputStream o = c.getOutputStream();
				  OutputStreamWriter ow = new OutputStreamWriter(o);
				  ow.write("DEBG_Stream\n");
				  ow.flush();
			  }
				  
			  int tmp = i.read();
			  while (tmp != -1) {
				  System.out.print((char) tmp);
				  tmp = i.read();
			  }
		  }
	  }
//#endif	  
}

