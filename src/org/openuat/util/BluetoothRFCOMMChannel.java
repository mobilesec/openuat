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

import javax.bluetooth.RemoteDevice;
import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;

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
	}
	
	/** Construct a Bluetooth RFCOMM channel object with a specific remote endpoint.
	 * This does not yet open the channel, @see open needs to be called for that.
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
	
/*
btl2cap://hostname:[PSM | UUID];parameters

   
  
The URL format for an RFCOMMStreamConnection:
btspp://hostname:[CN | UUID];parameters

   
  
Where:
btl2cap is the URL scheme for an L2CAPConnection.
btspp is the URL scheme for an RFCOMM StreamConnection.
hostname is either localhost to set up a server connection, or the Bluetooth address to create a client connection.
PSMis the Protocol/Service Multiplexer value, used by a clientconnecting to a server. This is similar in concept to a TCP/IP port.
CN is the Channel Number value, used by a client connecting to a server – also similar in concept to a TCP/IP port.
UUID is the UUID value used when setting up a service on a server.
parameters include name to describe the service name, and the security parameters authenticate, authorize, and encrypt. 
*/
/*
btspp://hostname:[CN | UUID];authenticate=true;authorize=true;encrypt=true

   
  
Where:
authenticate verifies the identity of a connecting device.
authorize verifies whether access is granted by a connecting (identified) device.
encrypt specifies that the connection must be encrypted.
You've already seen that a client wishing to connect to a service can retrieve the service's connection URL by calling the method ServiceRecord.getConnectionURL(). One of this method's arguments, requiredSecurity, specifies whether the returned connection URL should include the optional authenticate and encrypt security parameters. The valid values for requiredSecurity are: 
ServiceRecord.NOAUTHENTICATE_NOENCRYPTindicates authenticate=false; encrypt=false.
ServiceRecord.AUTHENTICATE_NOENCRYPT indicates authenticate=true; encrypt=false.
ServiceRecord.AUTHENTICATE_ENCRYPTindicates authenticate=true; encrypt=true. 
*/
	
	/*
...
// (assuming we have the service record)
// use record to retrieve a connection URL
String url =
    record.getConnectionURL(
        record.NOAUTHENTICATE_NOENCRYPT, false);
// open a connection to the server
StreamConnection connection =
    (StreamConnection) Connector.open(url);
// Send/receive data
try {
    byte buffer[] = new byte[100];
    String msg = "hello there, server";
    InputStream is = connection.openInputStream();
    OutputStream os = connection.openOutputStream();
    // send data to the server
    os.write(msg.getBytes);
    // read data from the server
    is.read(buffer);
    connection.close();
} catch(IOException e) {
  e.printStackTrace();
}
...	 */
	
	/** Opens a channel to the endpoint given to the constructor.
	 * @throws IOException On Bluetooth errors.
	 * @throws IOException When the channel has already been opened.
	 */
	public void open() throws IOException {
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
			logger.error("RFCOMM channel has not yet been openend properly, can not close");
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
			throw new IOException("RFCOMM channel has not yet been opened properly");
		}
		
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
			throw new IOException("RFCOMM channel has not yet been opened properly");
		}
		
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
		else
			// no connection open, need to work with remoteDeviceAddress here
			return new RemoteDevice(remoteDeviceAddress);
	}

	/** Implementation of RemoteConnection.getRemoteName.
	 * @see RemoteConnection.getRemoteName
	 */
	public String getRemoteName() {
		try {
			return BluetoothPeerManager.resolveName((RemoteDevice) getRemoteAddress());
		} catch (IOException e) {
			// can't resolve - that's bad
			return null;
		}
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

	
/*
 * StreamConnection con =(StreamConnection)Connector.open(url);
 * 
 * String connectionURL = serviceRecord.getConnectionURL(0, false);
StreamConnection con =(StreamConnection)Connector.open(connectionURL);

btspp://0001234567AB:3
 */
	
	  public static void main(String[] args) throws IOException, NumberFormatException {
		  BluetoothRFCOMMChannel c = new BluetoothRFCOMMChannel(args[0], Integer.parseInt(args[1]));
		  c.open();
		  InputStream i = c.getInputStream();
		  int tmp = i.read();
		  while (tmp != -1) {
			  System.out.print((char) tmp);
			  tmp = i.read();
		  }
	  }
}

