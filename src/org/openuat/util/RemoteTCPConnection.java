/* Copyright Rene Mayrhofer
 * File created 2007-02-17
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
import java.net.Socket;

/** This is a private implementation of RemoteConnection for TCP. */
public class RemoteTCPConnection implements RemoteConnection {
	/** Just a reference to the Socket object wrapped by this class. */
	private Socket socket;
	
	/** Only stores the Socket reference s in socket. */
	public RemoteTCPConnection(Socket s) {
		socket = s;
	}
	
	/** Implementation of RemoteConnection.getInputStream.
	 * @see RemoteConnection.getInputStream
	 */
	public InputStream getInputStream() throws IOException {
		return socket.getInputStream();
	}

	/** Implementation of RemoteConnection.getOutputStream.
	 * @see RemoteConnection.getOutputStream
	 */
	public OutputStream getOutputStream() throws IOException {
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
			throw new RuntimeException("Unable to close socket cleanly", e);
		}
	}
	
	/** Returns the underlying reference to the Socket object. This method 
	 * should generally not be used if it can be avoided!
	 */
	public Socket getSocketReference() {
		return socket;
	}
}
