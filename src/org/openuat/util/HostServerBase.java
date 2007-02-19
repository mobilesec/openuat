/* Copyright Rene Mayrhofer
 * File created 2005-09
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.util;

import org.apache.log4j.Logger;
import org.openuat.authentication.AuthenticationEventSender;
import org.openuat.authentication.exceptions.*;

import java.io.*;

/** This is a base class for listening to connections and spawning 
 * HostProtocolHandler objects upon incoming connections. It takes care of
 * handling the background listening thread. 
 *  
 * @author Rene Mayrhofer
 * @version 1.1, changes to 1.0: this is now a base class for TCP and RFCOMM implementations
 */
public abstract class HostServerBase extends AuthenticationEventSender implements Runnable {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger("org.openuat.util.HostServerBase" /*HostServerBase.class*/);

	/** this is a private thread object instead of the whole class being derived
	   from thread to prevent other classes from fiddling with this thread */
	private Thread listenerThread = null;

	/** Used to signal the listening thread to stop itself. */
	protected boolean running = false;
	
	/** If set to true, the fully connected socket/channel that represents a connection to a client
	 * will not be closed as soon as the HostProtocolHandler is finished with it, but will be
	 * passed to the authentication success event of the respective listener for further reuse. */
	protected boolean keepConnected;

	/** If set to true, the JSSE will be used, if set to false, the Bouncycastle Lightweight API. */
	protected boolean useJSSE;
	
	/** Initializes the listener. 
	 * @param useJSSE If set to true, the JSSE API with the default JCE provider of the JVM will be used
	 *                for cryptographic operations. If set to false, an internal copy of the Bouncycastle
	 *                Lightweight API classes will be used.
	 * @see #keepConnected If set to true, the connection to the client will be kept connected after the 
	 *                           registered HostProtocolHandler has finished. This allows the socket to be
	 *                           reused for additional communication after the first authentication
	 *                           protocol has been completed.
	 */
	public HostServerBase(boolean keepConnected, boolean useJSSE) throws IOException {
		this.keepConnected = keepConnected;
		this.useJSSE = useJSSE;
	}

	/** Starts a background thread (using the run() method of this class) that will listen for incoming connections. */
	public void startListening() {
		running = true;
		logger.debug("Starting listening thread for server socket");
		listenerThread = new Thread(this);
		listenerThread.start();
		logger.debug("Started listening thread for server socket");
	}

	/** Signals the background listening thread to stop and waits for it. This method will not return until the listening
	 * thread has terminated.
	 * @throws InternalApplicationException
	 */
	public void stopListening() throws InternalApplicationException {
		logger.debug("Stopping listening thread for server socket");
		running = false;
		// this is not nice, but will throw an exception in the listener thread
		// and thus allow it to exit by itself
		try {
			listenerThread.join();
			listenerThread = null;
		} catch (InterruptedException e) {
			throw new InternalApplicationException(
					"HostServerSocket listening thread got interrupted while waiting for it to finish. This should not happen.",
					e);
		}
		logger.debug("Stopped listening thread for server socket");
	}
}
