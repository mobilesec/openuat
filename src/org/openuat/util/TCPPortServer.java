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
import org.openuat.authentication.AuthenticationProgressHandler;
import org.openuat.authentication.HostProtocolHandler;
import org.openuat.authentication.exceptions.InternalApplicationException;

/** This class represents a listener on a TCP port which responds to incoming authentication requests by delegating any incoming
 * connection to the HostProtocolHandler class. More specifically, for each incoming TCP connection, the 
 * HostProtocolHandler.startIncomingAuthenticationThread is invoked with the connected TCP socket.
 * 
 * Listening is done in a background thread using blocking accept() calls. After constructing a HostServerSocket object for
 * a specific port, startListening() needs to be called to start accepting incoming connection.
 *  
 * @author Rene Mayrhofer
 * @version 1.1, changes to 1.0: The TCP server socket is now opened in startListening instead of the constructor.
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
	 */
	public TCPPortServer(int port, boolean keepConnected, boolean useJSSE) {
		super(keepConnected, useJSSE);
		this.port = port;
	}

	/** Need to override the startListening method to open the TCP server socket. 
	 * @throws IOException */
	public void startListening() throws IOException {
		this.listener = new ServerSocket(port);
		super.startListening();
	}

	/** Need to override the stopListening method to properly close the TCP server socket. 
	 * @throws InternalApplicationException */
	public void stopListening() throws InternalApplicationException {
		try {
			listener.close();
		} catch (IOException e) {
			throw new InternalApplicationException(
					"Could not close listening socket cleanly as a signal to the listener thread. This should not happen.",
					e);
		}
		super.stopListening();
	}

	/** Does the actual listening for incoming connections by calling the blocking accept() on the listening socket in a loop.
	 * For each incoming connection, a new HostProtocolHandler object is created and its startIncomingAuthenticationThread is
	 * used to start a thread that handles the new connection.
	 */
	public void run() {
		logger.debug("Listening thread for server socket now running port " + listener.getLocalPort());
		try {
			while (running) {
				//System.out.println("Listening thread for server socket waiting for connection");
				Socket s = listener.accept();
				
				HostProtocolHandler h = new HostProtocolHandler(new RemoteTCPConnection(s), keepConnected, useJSSE);
				// before starting the background thread, register all our own listeners with this new event sender
    			for (int i=0; i<eventsHandlers.size(); i++)
    				h.addAuthenticationProgressHandler((AuthenticationProgressHandler) eventsHandlers.elementAt(i));
    			// call the protocol asynchronously
    			h.startIncomingAuthenticationThread(true);
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
