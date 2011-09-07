/* Copyright Rene Mayrhofer
 * File created 2007-02-17
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.channel.main.ip;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.openuat.channel.main.RemoteConnection;
import org.openuat.util.DebugInputStream;
import org.openuat.util.DebugOutputStream;

/** This is a private implementation of RemoteConnection for TCP. */
public class RemoteTCPConnection implements RemoteConnection {
	/** Our logger. */
	private static Logger logger = LoggerFactory.getLogger("org.openuat.util.RemoteTCPConnection" /*BluetoothRFCOMMChannel.class*/);

	/** Just a reference to the Socket object wrapped by this class. */
	private Socket socket = null;
	
	/** Only stores the Socket reference s in socket. */
	public RemoteTCPConnection(Socket s) {
		socket = s;
	}
	
	/** Implementation of RemoteConnection.getInputStream.
	 * @see RemoteConnection.getInputStream
	 */
	public InputStream getInputStream() throws IOException {
		// maybe apply decorator
		if (logger.isLoggable(Level.FINEST))
			return new DebugInputStream(socket.getInputStream(), "org.openuat.util.RemoteTCPConnection_IN");
			
		return socket.getInputStream();
	}

	/** Implementation of RemoteConnection.getOutputStream.
	 * @see RemoteConnection.getOutputStream
	 */
	public OutputStream getOutputStream() throws IOException {
		// maybe apply decorator
		if (logger.isLoggable(Level.FINEST))
			return new DebugOutputStream(socket.getOutputStream(), "org.openuat.util.RemoteTCPConnection_OUT");
			
		return socket.getOutputStream();
	}

	/** Implementation of RemoteConnection.getRemoteAddress.
	 * @see RemoteConnection.getRemoteAddress
	 */
	public Object getRemoteAddress() throws IOException {
		return socket.getInetAddress();
	}

	/** Implementation of RemoteConnection.getRemoteName.
	 * @see RemoteConnection.getRemoteName
	 */
	public String getRemoteName() {
		return socket.getInetAddress().getCanonicalHostName();
	}
	
	/** Implementation of RemoteConnection.open, will always return false in
	 * this implementation.
	 * @see RemoteConnection.open
	 */
	public boolean open() throws IOException {
		return false;
	}

	/** Implementation of RemoteConnection.close.
	 * @see RemoteConnection.close
	 */
	public void close() {
    	try {
    		if (socket != null && socket.isConnected())
    		{
    			if (! socket.isInputShutdown() && !socket.isClosed())
    				socket.shutdownInput();
    			if (! socket.isOutputShutdown() && !socket.isClosed())
					socket.shutdownOutput();
    			socket.close();
    		}
		}
		catch (IOException e) {
   			// need to ignore here, nothing we can do about it...
   			logger.log(Level.SEVERE, "Unable to close streams cleanly", e);
		}
	}

	/** Implementation of RemoteConnection.isOpen. */
	public boolean isOpen() {
		return socket != null && socket.isConnected();
	}

	/** Returns the underlying reference to the Socket object. This method 
	 * should generally not be used if it can be avoided!
	 */
	public Socket getSocketReference() {
		return socket;
	}
	
	/** Implementation of equals, comparing the target IP address and target 
	 * port. Local port are ignored in this comparison, as we refer to 
	 * Remote_TCP_Connections. 
	 */
	// TODO: activate me again when J2ME polish can deal with Java5 sources!
	//@Override
	public boolean equals(Object other) {
		if (other == null || !(other instanceof RemoteTCPConnection)) {
			if (logger.isLoggable(Level.FINER))
				logger.finer("equals called with object of wrong type");
			return false;
		}
		RemoteTCPConnection o = (RemoteTCPConnection) other;
		boolean ret = o.socket != null && socket != null &&
			o.socket.getInetAddress().equals(socket.getInetAddress()) &&
			o.socket.getPort() == socket.getPort(); 
		if (logger.isLoggable(Level.FINER))
			logger.finer("socket=" + socket + ", o.socket=" + o.socket +
					", socket.InetAddress=" + socket.getInetAddress() + 
					", o.socket.InetAddress=" + o.socket.getInetAddress() + 
					", socket.port=" + socket.getPort() + ", o.socket.port=" + o.socket.getPort() +
					", RET=" + ret);
		return ret; 
	}
	
	public String toString() {
		if (socket.isConnected()) {
			try {
				return "RemoteTCPConnection connected to address " + getRemoteAddress() + 
					" with name " + getRemoteName() + " at port " + socket.getPort();
			}
			catch (IOException e) {
				return "RemoteTCPConnection connected to unretrievable address";
			}
		}
		else
			return "RemoteTCPConnection, unconnected";
	}
}
