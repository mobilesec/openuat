/* Copyright Rene Mayrhofer
 * File created 2005-09
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.authentication;

import org.eu.mayrhofer.authentication.exceptions.*;
import java.net.*;
import java.util.ListIterator;
import java.io.*;

/** This class represents a listener on a TCP port which responds to incoming authentication requests by delegating any incoming
 * connection to the HostProtocolHandler class. More specifically, for each incoming TCP connection, the 
 * HostProtocolHandler.startIncomingAuthenticationThread is invoked with the connected TCP socket.
 * 
 * Listening is done in a background thread using blocking accept() calls. After constructing a HostServerSocket object for
 * a specific port, startListening() needs to be called to start accepting incoming connection.
 *  
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class HostServerSocket extends AuthenticationEventSender implements Runnable {
	/** This is the (bound but unconnected) TCP socket for listening for incoming connections. */
	private ServerSocket listener;

    // We use a pseudo-singleton pattern here: for each port, only one instance can exist. This map holds the known instances.
	//private static HashMap instances;

	/** this is a private thread object instead of the whole class being derived
	   from thread to prevent other classes from fiddling with this thread */
	private Thread listenerThread = null;

	/** Used to signal the listening thread to stop itself. */
	private boolean running = false;
	
	/** If set to true, the fully connected socket that represents a connection to a client
	 * will not be closed as soon as the HostProtocolHandler is finished with it, but will be
	 * passed to the authentication success event of the respective listener for further reuse. */
	private boolean keepSocketConnected;

	/** If set to true, the JSSE will be used, if set to false, the Bouncycastle Lightweight API. */
	private boolean useJSSE;
	
	/** Initialized the listener socket by binding it to the specified port. 
	 * @param useJSSE If set to true, the JSSE API with the default JCE provider of the JVM will be used
	 *                for cryptographic operations. If set to false, an internal copy of the Bouncycastle
	 *                Lightweight API classes will be used.
	 * @see #keepSocketConnected If set to true, the socket to the client will be kept connected after the 
	 *                           registered HostProtocolHandler has finished. This allows the socket to be
	 *                           reused for additional communication after the first authentication
	 *                           protocol has been completed.
	 */
	public HostServerSocket(int port, boolean keepSocketConnected, boolean useJSSE) throws IOException {
		this.listener = new ServerSocket(port);
		this.keepSocketConnected = keepSocketConnected;
		this.useJSSE = useJSSE;
	}

	/** Starts a background thread (using the run() method of this class) that will listen for incoming connections. */
	public void startListening() {
		running = true;
		//System.out.println("Starting listening thread for server socket");
		listenerThread = new Thread(this);
		listenerThread.start();
		//System.out.println("Started listening thread for server socket");
	}

	/** Signals the background listening thread to stop and waits for it. This method will not return until the listening
	 * thread has terminated.
	 * @throws InternalApplicationException
	 */
	public void stopListening() throws InternalApplicationException {
		//System.out.println("Stopping listening thread for server socket");
		running = false;
		// this is not nice, but will throw an exception in the listener thread
		// and thus allow it to exit by itself
		try {
			listener.close();
			listenerThread.join();
			listenerThread = null;
		} catch (InterruptedException e) {
			throw new InternalApplicationException(
					"HostServerSocket listening thread got interrupted while waiting for it to finish. This should not happen.",
					e);
		} catch (IOException e) {
			throw new InternalApplicationException(
					"Could not close listening socket cleanly as a signal to the listener thread. This should not happen.",
					e);
		}
		//System.out.println("Stopped listening thread for server socket");
	}

	/** Does the actual listening for incoming connections by calling the blocking accept() on the listening socket in a loop.
	 * For each incoming connection, a new HostProtocolHandler object is created and its startIncomingAuthenticationThread is
	 * used to start a thread that handles the new connection.
	 */
	public void run() {
		//System.out.println("Listening thread for server socket now running");
		try {
			while (running) {
				//System.out.println("Listening thread for server socket waiting for connection");
				Socket s = listener.accept();
				HostProtocolHandler h = new HostProtocolHandler(s, keepSocketConnected, useJSSE);
				// before starting the background thread, register all our own listeners with this new event sender
	    			for (ListIterator i = eventsHandlers.listIterator(); i.hasNext(); )
	    				h.addAuthenticationProgressHandler((AuthenticationProgressHandler) i.next());
	    			h.startIncomingAuthenticationThread();
			}
		} catch(SocketException e) {
			// Only ignore the SocketException when we have been signalled to stop. Otherwise it's a real error. 
			if (running)
				System.out.println("Error in listening thread: " + e);
			/*else
				System.out.println("Listening socket was forcibly closed, exiting listening thread now.");*/
		} catch (IOException e) {
			System.out.println("Error in listening thread: " + e);
		}
	}
}