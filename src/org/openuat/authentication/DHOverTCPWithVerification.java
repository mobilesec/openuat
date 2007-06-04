/* Copyright Rene Mayrhofer
 * File created 2006-05-03
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.authentication;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

import org.apache.log4j.Logger;
import org.openuat.authentication.exceptions.InternalApplicationException;
import org.openuat.authentication.relate.DongleProtocolHandler;
import org.openuat.util.HostServerBase;
import org.openuat.util.RemoteTCPConnection;
import org.openuat.util.TCPPortServer;

/** This is an implementation of DHWithVerification specifically for TCP. It
 * is an extremely thin wrapper and may get removed soon.
 * 
 * @author Rene Mayrhofer
 * @version 1.2, changes to 1.1: made independent of TCP, but provide a subclass
 *               with the same old interface
 * 				 changes to 1.0: replaced InetAddress and Socket objects passed 
 *               to events with String and RemoteConnection objects.
 */
public abstract class DHOverTCPWithVerification extends DHWithVerification {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger("org.openuat.authentication.DHOverTCPWithVerification" /*DHOverTCPWithVerification.class*/);

	/** This is only a helper member for keeping the HostServerSocket object that is created by
	 * startServer, so that it can be freed by stopServer.
	 * @see #startServer
	 * @see #stopServer
	 */
	protected HostServerBase serverSocket = null;
	
	/** The TCP port to listen on and to connect to the remote server. */
	private int tcpPort;
	
	/** Construct the object by initializing basic variables.
	 * 
	 * @param tcpPort The TCP port to use for listening and for connecting to remote hosts.
	 * @param useJSSE If set to true, the JSSE API with the default JCE provider of the JVM will be used
	 *                for cryptographic operations. If set to false, an internal copy of the Bouncycastle
	 *                Lightweight API classes will be used.
	 * @param keepConnected
	 *            If set to true, the opened client connection is passed to the
	 *            authentication success event (in the results parameter) for 
	 *            further re-use of the connection (e.g. passing additional 
	 *            information about further protocol steps). If set to false, the
	 *            socket will be closed when this protocol is done with it. The socket
	 *            will always be closed on authentication failures.
	 *            If in doubt, set to false;
	 * @param instanceId This parameter may be used to distinguish differenc instances of
	 *                   this class running on the same machine. It will be used in logging
	 *                   and error messages. May be set to null.
	 */
	protected DHOverTCPWithVerification(int tcpPort, boolean keepConnected,
			String instanceId, boolean useJSSE) {
		super(keepConnected, false, instanceId, useJSSE);
		this.tcpPort = tcpPort;
	}

	/** Starts the authentication protocol in the background. Listeners should subscribe to
	 * authentication events to get notified about the progress of authentication.
	 * @param remoteHost The hostname/IP address of the remote device to send an authentication request to.
	 * @param param An optional parameter that should be exchanged with the host, usually describing
	 *              some parameter(s) of the subsequent verification step.
	 * 
	 * @return true if the authentication could be started, false otherwise.
	 * 
	 * @see AuthenticationEventSender#addAuthenticationProgressHandler
	 * @see DongleProtocolHandler#handleDongleCommunication
	 */
	protected boolean startAuthentication(String remoteHost, String param) 
			throws UnknownHostException, IOException/*, ConfigurationErrorException, InternalApplicationException*/ {
		Socket socket = new Socket(remoteHost, tcpPort);
		this.startAuthentication(new RemoteTCPConnection(socket), param);
		return true;
	}

	
	/** This is a helper function to start the "server" part of the authentication protocol.
	 * It constructs a HostServerSocket object and sets up this object as a listener.
	 * @see #serverSocket
	 */
	public void startServer() throws IOException {
		if (serverSocket == null) {
			serverSocket = new TCPPortServer(tcpPort, true, useJSSE);
    		serverSocket.addAuthenticationProgressHandler(keyManager.getHostAuthenticationHandler());
    		serverSocket.startListening();
		}
		else
			logger.error("Could not start authentication server because one is already running." + 
					(instanceId != null ? " [instance " + instanceId + "]" : ""));
	}

	/** This is a helper function to stop the "server" part of the authentication protocol.
	 * @see #serverSocket
	 */
	public void stopServer() {
		if (serverSocket != null) {
			try {
				serverSocket.stopListening();
			}
			catch (InternalApplicationException e) {
				// ignore this case - we are shutting down anyway, just free resources
			}
			serverSocket = null;
		}
		else
			logger.error("Could not stop authentication server because none is running." + 
					(instanceId != null ? " [instance " + instanceId + "]" : ""));
	}
}
