/* Copyright Rene Mayrhofer
 * File created 2007-02-18
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.util;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

import org.apache.log4j.Logger;
import org.openuat.authentication.exceptions.InternalApplicationException;

/** This class represents a listener on a TCP port which responds to incoming authentication requests by delegating any incoming
 * connection to the HostProtocolHandler class. More specifically, for each incoming TCP connection, the 
 * HostProtocolHandler.startIncomingAuthenticationThread is invoked with the connected TCP socket.
 * 
 * Listening is done in a background thread using blocking accept() calls. After constructing a HostServerSocket object for
 * a specific port, startListening() needs to be called to start accepting incoming connection.
 *  
 * @author Rene Mayrhofer
 * @version 1.2, changes to 1.1: startListening is now called start
 *               changes to 1.0: The TCP server socket is now opened in startListening instead of the constructor.
 */
public class TCPPortServer extends HostServerBase {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(TCPPortServer.class);

	/** This is the (bound but unconnected) TCP socket for listening for incoming connections. */
	private ServerSocket listener;
	
	/** The TCP port to listen on - used by startListening and set by the constructor. */
	private int port;

    // We use a pseudo-singleton pattern here: for each port, only one instance can exist. This map holds the known instances.
	//private static HashMap instances;

	/** Initializes the listener by creating the TCP server socket.
	 * @param port The TCP port to bind to. 
	 * @param useJSSE If set to true, the JSSE API with the default JCE provider of the JVM will be used
	 *                for cryptographic operations. If set to false, an internal copy of the Bouncycastle
	 *                Lightweight API classes will be used.
	 * @see #keepConnected If set to true, the connection to the client will be kept connected after the 
	 *                           registered HostProtocolHandler has finished. This allows the socket to be
	 *                           reused for additional communication after the first authentication
	 *                           protocol has been completed.
	 * @param protocolTimeoutMs
	 * 			  The maximum duration in milliseconds that this authentication
	 * 			  protocol may take before it will abort with an AuthenticationFailed
	 * 			  exception. Set to -1 to disable the timeout.
	 */
	public TCPPortServer(int port, int protocolTimeoutMs, boolean keepConnected, boolean useJSSE) {
		super(keepConnected, useJSSE, protocolTimeoutMs);
		this.port = port;
	}

	/** Need to override the startListening method to open the TCP server socket. 
	 * @throws IOException */
	// TODO: activate me again when J2ME polish can deal with Java5 sources!
	//@Override
	public void start() throws IOException {
		if (listener == null) {
			this.listener = new ServerSocket(port);
			super.start();
		}
		else
			logger.error("Could not start TCP server because one is already running.");
	}

	/** Need to override the stopListening method to properly close the TCP server socket. 
	 * @throws InternalApplicationException */
	// TODO: activate me again when J2ME polish can deal with Java5 sources!
	//@Override
	public void stop() throws InternalApplicationException {
		try {
			listener.close();
			listener = null;
		} catch (IOException e) {
			throw new InternalApplicationException(
					"Could not close listening socket cleanly as a signal to the listener thread. This should not happen.",
					e);
		}
		super.stop();
	}

	/** Does the actual listening for incoming connections by calling the blocking accept() on the listening socket in a loop.
	 * For each incoming connection, a new HostProtocolHandler object is created and its startIncomingAuthenticationThread is
	 * used to start a thread that handles the new connection.
	 */
	public void run() {
		logger.debug("Listening thread for server socket now running port " + listener.getLocalPort());
		try {
			while (running) {
				Socket s = listener.accept();
				startProtocol(new RemoteTCPConnection(s));
			}
		} catch(SocketException e) {
			// Only ignore the SocketException when we have been signalled to stop. Otherwise it's a real error. 
			if (running)
				logger.error("Error in listening thread: " + e);
			else
				logger.debug("Listening socket was forcibly closed, exiting listening thread now.");
		} catch (IOException e) {
			logger.error("Error in listening thread: " + e);
		}
	}
}
