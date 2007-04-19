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

/** This interface represents an already opened, stream-oriented connection to 
 * a remote host, e.g. via TCP or via Bluetooth L2CAP/RFCOMM. The specific 
 * connection implementations should implement it with internal or anonymous
 * classes so that authentication protocols can re-use connections.
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public interface RemoteConnection {
	/** Returns the InputStream to read from the remote side. */
	InputStream getInputStream() throws IOException;
	
	/** Returns the OuputStream to write to the remote side. */
	OutputStream getOutputStream() throws IOException;
	
	/** Returns the name of the remote host in appropriate representation, 
	 * or null if it could not be resolved. */
	String getRemoteName();
	
	/** Returns the address of the remote host as appropriate object. */
	Object getRemoteAddress() throws IOException;
	
	/** Closes the underlying connection cleanly and frees any resources
	 * held by it.
	 */
	void close();
}
