/* Copyright Rene Mayrhofer
 * File created 2006-05-03
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.authentication;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import org.apache.commons.codec.binary.Hex;
import org.apache.log4j.Logger;
import org.eu.mayrhofer.authentication.exceptions.InternalApplicationException;
import org.eu.mayrhofer.authentication.relate.DongleProtocolHandler;

/** This is an abstract class that implements the basics of all protocols
 * based on Diffie-Hellman key exchange over TCP with subsequent verification
 * of the key material to check that it is equal on both sides. This 
 * verification is necessary to prevent man-in-the-middle attacks. Derived
 * classed need to implement this specific check that the authentication key
 * provided by SimpleKeyAgreement matches by implementing the method
 * startVerification. This method should asynchronously start the verification
 * step, i.e. not block the caller, and should then either call 
 * verificationSuccess or verificationFailure depending on the outcome of
 * the check. Upon calling one of the methods, a status exchange with the remote
 * host will be done over the TCP channel to arrive at a common decision if the
 * whole protocol succeeded. The final verdict will be signalled by emitting
 * standard authentication events (as defined by AuthenticationProgressHandler) and
 * by calling either the protocolSucceededHook or the protocolFailedHook
 * function. In short, the whole authentication protocol should be used as follows:
 * 
 * 1. Construct the object.
 * Either:
 * 2a. Start the TCP server.
 * 3b. Start an authentication protocol to a remote device by calling
 *     startAuthentication.
 * (It is possible to start a server and then initiate a protocol run, but
 * only one protocol run can be active at a time.)
 * 4. After the key agreement phase succeeded, the abstract startVerification
 *    method is called. In this method, derived classes should asynchronously 
 *    start whatever is necessary to verify the provided shared authentication key.
 * 5. When a local decision about the key verification has been made, call either
 *    verificationSucceess or verificationFailure.
 * 6. The local decisions will be communicated over the TCP channel and if both
 *    devices signalled success, the protocolSucceededHook will be called. In any
 *    other case (both or either of the devices signalled failure on verification),
 *    the protocolFailedHook will be called.
 * Generally, events will be emitted by this class to all registered listeners.
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public abstract class DHOverTCPWithVerification extends AuthenticationEventSender {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(DHOverTCPWithVerification.class);

	/** The TCP port to listen on and to connect to the remote server. */
	private int tcpPort;
	
	/** Possible value of state, indicates that the authentication has not been started yet. 
	 * @see #state 
	 */
	private final static int STATE_IDLE = 1;
	/** Possible value of state, indicates that the host authentication is running.
	 * @see #state 
	 */
	private final static int STATE_HOST_AUTH_RUNNING = 2;
	/** Possible value of state, indicates that the key verification is running
	 * (and the host authentication has thus implicitly been completed successfully).
	 * @see #state 
	 */
	private final static int STATE_VERIFICATION_RUNNING = 3;
	/** Possible value of state, indicates that the whole authentication protocol has 
	 * been completed successfully.
	 * @see #state 
	 */
	private final static int STATE_SUCCEEDED = 4;
	/** Possible value of state, indicates that the whole authentication protocol has failed.
	 * @see #state 
	 */
	private final static int STATE_FAILED = 5;
	
	/** The current state of the authentication, one of STATE_IDLE,
	 * STATE_HOST_AUTH_RUNNING, STATE_VERIFICATION_RUNNING, STATE_SUCCEEDED, STATE_FAILED.
	 * @see #STATE_IDLE
	 * @see #STATE_HOST_AUTH_RUNNING
	 * @see #STATE_VERIFICATION_RUNNING
	 * @see #STATE_SUCCEEDED
	 * @see #STATE_FAILED
	 */
	private int state = STATE_IDLE;
	
	/** This message is sent via the TCP channel to the remote upon authentication success. */
	private final static String Protocol_Success = "ACK ";
	/** This message is sent via the TCP channel to the remote upon authentication failure. */
	private final static String Protocol_Failure = "NACK ";

	/** If set to true, the TCP socket to the remote host will not be closed after a successful 
	 * authentication, but will be passed as a parameter to the success event. This allows re-use for
	 * additional communication. It will still be closed on authentication failures.
	 * If set to false, it will also be closed on authentication success.
	 * @see #socketToRemote
	 */
	private boolean keepSocketConnected;
	
	/** If set to true, the JSSE will be used, if set to false, the Bouncycastle Lightweight API. */
	protected boolean useJSSE;

	/** If the state is STATE_DONGLE_AUTH_RUNNING or STATE_SUCCEEDED, this contains
	 * the secret key shared with the other device.
	 */
	private byte[] sharedKey = null;

	/** If the state is STATE_DONGLE_AUTH_RUNNING, this contains a socket that is still
	 * connected to the remote side and which is used for transmitting success or failure
	 * messages from the dongle authentication protocol (i.e. the second stage).
	 * It is set by HostAuthenticationEventHandler.AuthenticationSuccess.
	 * @see HostAuthenticationEventHandler#AuthenticationSuccess
	 */
	private Socket socketToRemote = null;

	/** This is only a helper member for keeping the HostServerSocket object that is created by
	 * startServer, so that it can be freed by stopServer.
	 * @see #startServer
	 * @see #stopServer
	 */
	private HostServerSocket serverSocket = null;
	
	/** This may be set to distinguish multiple instances running on the same machine. */
	private String instanceId = null;
	
	/** Construct the object by initializing basic variables.
	 * 
	 * @param tcpPort The TCP port to use for listening and for connecting to remote hosts.
	 * @param useJSSE If set to true, the JSSE API with the default JCE provider of the JVM will be used
	 *                for cryptographic operations. If set to false, an internal copy of the Bouncycastle
	 *                Lightweight API classes will be used.
	 * @param keepSocketConnected
	 *            If set to true, the opened client socket soc is passed to the
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
	protected DHOverTCPWithVerification(int tcpPort, boolean keepSocketConnected,
			String instanceId, boolean useJSSE) {
		this.tcpPort = tcpPort;
		this.keepSocketConnected = keepSocketConnected;
		this.useJSSE = useJSSE;
		this.instanceId = instanceId;
	}

	/** This method returns true if this object is idle or if it is currently running the authentication protocol
	 * with a remote host. Callers should check that it is idle before calling startAuthentication, because only
	 * one protocol can be run at a time (this might change in the future at least for the host authentication 
	 * phase, but right now, we only accept new authentication runs to be started when idle).
	 * 
	 * @return true when the object is idle, i.e. when a new authentication can be started.
	 */
	public boolean isIdle() {
		// no synchronization mechanism here because it's only a boolean
		return state == STATE_IDLE;
	}
	
	/** This method returns true if key verification is currently running.
	 * 
	 * @return true when the object is in key verification state, false otherwise.
	 */
	public boolean isVerifying() {
		// dt.
		return state == STATE_VERIFICATION_RUNNING;
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
		if (! isIdle()) {
			logger.warn("Tried to start authentication with host " + remoteHost + 
					" while another authentication protocol run is still active. Not starting authentication and " +
					" returning false." + 
					(instanceId != null ? " [instance " + instanceId + "]" : ""));
			return false;
		}
		
		state = STATE_HOST_AUTH_RUNNING;
		
		/* This is simple in the implementation, because we just need to start the
		 * host authentication here. When that suceeds, the event handler will 
		 * continue to start the dongle authentication.
		 */ 
		/* There is no need to unregister this new object, since it is only 
		 * registered with a temporary HostProtocolHandler object, which will
		 * be garbage collected when its background authentication thread
		 * finishes. */
		try {
			HostProtocolHandler.startAuthenticationWith(remoteHost, tcpPort, 
					new HostAuthenticationEventHandler(), 
					true, param, useJSSE);
		} 
		catch (UnknownHostException e) {
			// when we can't start here, be sure to reset to a clean state
			reset();
			// and simply rethrow;
			throw e;
		}
		catch (IOException e) {
			// dt.
			reset();
			throw e;
		}
		return true;
	}

	/** Resets the object to its idle state. After calling reset, incoming as well as outgoing 
	 * authentication protocol runs are again possible. 
	 */
	private void reset() {
		socketToRemote = null;
		
		// also allow derived classes to do more specific resets
		resetHook();

		// and finally reset the state
		state = STATE_IDLE;
		logger.debug("Reset object to idle set" + 
				(instanceId != null ? " [instance " + instanceId + "]" : ""));
	}
	
	/** Small helper function to raise an authentication failure event and set state as well as wipe sharedKey.
	 * 
	 * @param remote The remote device (either InetAddress or Integer for the host address or relate id) with which the authentication failed.
	 * @param e If not null, the exception describing the failure.
	 * @param message If not null, the message describing the failure.
	 */ 
	private void authenticationFailed(InetAddress remote, 
			Object optionalRemoteId, Exception e, String message) {
		state = STATE_FAILED;
		// be sure to wipe the shared key if it has already been set
		if (sharedKey != null) {
			for (int i=0; i<sharedKey.length; i++)
				sharedKey[i] = 0;
			sharedKey = null;
		}
		raiseAuthenticationFailureEvent(remote, e, message);
		
		// also allow derived classes to do special failure handling
		protocolFailedHook(remote, optionalRemoteId, e, message);
		
		// no need to keep the socket around in any case - close it properly
		closeSocket();
		
		reset();
	}

	/** Small helper to send to the remote and wait for the remote message 
	 * (after we sent our own - hint against dead locks) before continuing with 
	 * either success of failure handling.
	 */
	private String remoteStatusExchange(String reportToRemote, 
			Object optionalRemoteId) {
		try {
    		// this enables auto-flush
    		PrintWriter toRemote = new PrintWriter(socketToRemote.getOutputStream(), true);
	    	logger.debug("Sending status to remote: '" + reportToRemote + "'" + 
					(instanceId != null ? " [instance " + instanceId + "]" : ""));
    		toRemote.println(reportToRemote);
    		toRemote.flush();
    		logger.debug("Status sent, waiting for status from remote" + 
					(instanceId != null ? " [instance " + instanceId + "]" : ""));
			/* do not use a BufferedReader here because that would potentially mess up
			 * the stream for other users of the socket (by consuming too many bytes)
			 */
    		InputStream fromRemote = socketToRemote.getInputStream();
    		String remoteStatus = "";
    		int ch = fromRemote.read();
    		while (ch != -1 && ch != '\n') {
    			// TODO: check if this is enough to deal with line ending problems
    			if (ch != '\r')
    				remoteStatus += (char) ch;
   				ch = fromRemote.read();
    		}
	    	logger.debug("Received remote status: '" + remoteStatus + "', exited with " + 
	    			(ch == -1 ? "end of stream" : "new line") +
					(instanceId != null ? " [instance " + instanceId + "]" : ""));
    		if (remoteStatus.length() == 0) {
    			logger.error("Could not get status message from remote host" + 
    					(instanceId != null ? " [instance " + instanceId + "]" : ""));
    			authenticationFailed(socketToRemote.getInetAddress(), optionalRemoteId,
    					null, "Could not get status message from remote host" + 
    					(instanceId != null ? " [instance " + instanceId + "]" : ""));
    			return null;
    		}
    		else
    			return remoteStatus;
        }
        catch (IOException e) {
        	logger.error("Could not report success to remote host or get status message from remote host: " + e + 
					(instanceId != null ? " [instance " + instanceId + "]" : ""));
        	authenticationFailed(socketToRemote.getInetAddress(), optionalRemoteId, 
        			e, "Could not report success to remote host or get status message from remote host" + 
					(instanceId != null ? " [instance " + instanceId + "]" : ""));
        	return null;
        }
	}
	
	/** Just a small helper to ignore an IOException when closing the socket (we are finished anyway). */
	private void closeSocket() {
		try {
			if (socketToRemote != null)
				socketToRemote.close();
			else
				logger.error("socketToRemote is null, but shouldn't be" + 
						(instanceId != null ? " [instance " + instanceId + "]" : ""));
		}
		catch (IOException e) {
			logger.error("Could not close socket to remote host properly, but ignoring it: " + e + 
					(instanceId != null ? " [instance " + instanceId + "]" : ""));
		}
	}

	/** This method should be called by derived classes after key verification has
	 * been started with the startVerification method. Calling this method signals a
	 * local success of the key verification, but the whoel authentication protocol can
	 * still fail when the other host signals a failure. After calling this method, either
	 * the protocolSucceededHook or the protocolFailedHook will be called.
	 * 
	 * @param optionalRemoteId An optional remote ID that will be forwarded to the hook 
	 *                         functions. It can be used to identify the remote device.
	 * @param optionalParameterToRemote An optional parameter that should be sent to the
	 *                                  remote device alongside the success status message.
	 *                                  This will also be forwarded to the hook functions.
	 */
	protected void verificationSuccess(Object optionalRemoteId, String optionalParameterToRemote) {
    	String remoteStatus = remoteStatusExchange(Protocol_Success + 
    			(optionalParameterToRemote != null ? optionalParameterToRemote : ""), 
    			optionalRemoteId);
		
    	if (remoteStatus != null && remoteStatus.length() > 0) {
    		if (remoteStatus.startsWith(Protocol_Success)) {
    			logger.info("Received success status from remote host" + 
    					(instanceId != null ? " [instance " + instanceId + "]" : ""));

		        state = STATE_SUCCEEDED;
		        /* for sending the success events, first figure out both aspects of the remote host
		           (i.e. the IP address and the optional remote ID) */
		        InetAddress remoteAddrPart = socketToRemote.getInetAddress();
		        Object[] remoteParam = new Object[] {remoteAddrPart, optionalRemoteId};
		        if (!keepSocketConnected)
			        /* our result object is here the secret key that is shared (host authentication) 
			           and now spatially authenticated (dongle authentication) */
		        	raiseAuthenticationSuccessEvent(remoteParam, sharedKey);
		        else
		        	/* It has been requested that the socket be kept open, so pass it over
		        	 * in addition to the shared secret key.
		        	 * As we need to pass two parameters in this case, again use an array...
		        	 */
		        	raiseAuthenticationSuccessEvent(remoteParam, new Object[] {sharedKey, socketToRemote});

		        // this string can be null if no optional parameter has been received from the remote host
		        String optionalParameterFromRemote = remoteStatus.substring(Protocol_Success.length());
		        // and also call the hook to allow the derived classes to react too
		        protocolSucceededHook(remoteAddrPart, optionalRemoteId, optionalParameterFromRemote, sharedKey);
		        		
				// if the socket is not going to be re-used, don't forget to close it properly
				if (!keepSocketConnected)
					closeSocket();

				// and finally reset (in failure cases, the authenticationFailed helper will call reset)
		        reset();
    		}
    		else if (remoteStatus.startsWith(Protocol_Failure)) {
    			logger.error("Received failure status from remote host although local dongle authentication was successful. Authentication protocol failed" + 
    					(instanceId != null ? " [instance " + instanceId + "]" : ""));
    			authenticationFailed(socketToRemote.getInetAddress(), optionalRemoteId, null, "Received authentication failure status from remote host" + 
    					(instanceId != null ? " [instance " + instanceId + "]" : ""));
        	}
    		else {
    			logger.error("Unkown status from remote host! Ignoring it (was '" + remoteStatus + "')" + 
    					(instanceId != null ? " [instance " + instanceId + "]" : ""));
    			authenticationFailed(socketToRemote.getInetAddress(), optionalRemoteId, null, "Unkown status from remote host (was '" + remoteStatus + "')" + 
    					(instanceId != null ? " [instance " + instanceId + "]" : ""));
        	}
    	} // if remoteStatus == null, just ignore here because the helper already fired the failure event
	}
	
	/** This method should be called by derived classes after key verification has
	 * been started with the startVerification method. Calling this method signals a
	 * local failure of the key verification, so the whole authentication protocol must
	 * fail (it can only succeed when both hosts signal success). After calling this 
	 * method, the protocolFailedHook will be called.
	 * 
	 * @param optionalRemoteId An optional remote ID that will be forwarded to the hook 
	 *                         function. It can be used to identify the remote device.
	 * @param optionalParameterToRemote An optional parameter that should be sent to the
	 *                                  remote device alongside the success status message.
	 *                                  This will also be forwarded to the hook function.
	 */
	protected void verificationFailure(Object optionalRemoteId, String optionalParameterToRemote,
			Exception e, String msg) {
		String remoteStatus = remoteStatusExchange(Protocol_Failure + 
    			(optionalParameterToRemote != null ? optionalParameterToRemote : ""), 
    			optionalRemoteId);
        
    	if (remoteStatus != null) {
    		if (remoteStatus.startsWith(Protocol_Success)) {
    			logger.info("Received success status from remote host after reporting local failure" + 
    					(instanceId != null ? " [instance " + instanceId + "]" : ""));
    		} else if (remoteStatus.startsWith(Protocol_Failure)) { 
    			logger.info("Received failure status from remote host to match local failure."
    					+ "Good that we agreed." + 
    					(instanceId != null ? " [instance " + instanceId + "]" : ""));
    		} else {
    			logger.error("Unkown status from remote host! Ignoring it (was '" + remoteStatus + "')" + 
    					(instanceId != null ? " [instance " + instanceId + "]" : ""));
    		}

    		authenticationFailed(socketToRemote.getInetAddress(), optionalRemoteId, e, msg);
    	} // if remoteStatus == null, just ignore here because the helper already fired the failure event
	}
	
	/** This is a helper function to start the "server" part of the authentication protocol.
	 * It constructs a HostServerSocket object and sets up this object as a listener.
	 * @see #serverSocket
	 */
	public void startServer() throws IOException {
		if (serverSocket == null) {
			serverSocket = new HostServerSocket(tcpPort, true, useJSSE);
			HostAuthenticationEventHandler hostServerHandler = new HostAuthenticationEventHandler();
    		serverSocket.addAuthenticationProgressHandler(hostServerHandler);
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
			}
			serverSocket = null;
		}
		else
			logger.error("Could not stop authentication server because none is running." + 
					(instanceId != null ? " [instance " + instanceId + "]" : ""));
	}
	

	/** A helper class for handling the events from HostProtocolHandler.
	 * Its main purpose is to react to the AuthenticationSuccess event of
	 * HostAuthenticationProtocol (i.e. stage 1), record its results and
	 * fire off the key verification (i.e. stage 2).
	 * Additionally, it will forward failure and progress events.  
	 */
	private class HostAuthenticationEventHandler implements AuthenticationProgressHandler {
	    public void AuthenticationSuccess(Object sender, Object remote, Object result)
	    {
	    	if (isIdle()) {
	    		logger.debug("Received host authentication event from " + sender + " in idle state - assuming to be the server." + 
						(instanceId != null ? " [instance " + instanceId + "]" : ""));
	    		state = STATE_HOST_AUTH_RUNNING;
	    	}
	    	if (state != STATE_HOST_AUTH_RUNNING) {
	    		logger.error("Received host authentication success event with remote host " + remote + 
	    				" while not expecting one! This event will be ignored." + 
						(instanceId != null ? " [instance " + instanceId + "]" : ""));
	    		return;
	    	}
			
	    	InetAddress remoteHost = (InetAddress) remote;
	        logger.info("Received host authentication success event with " + remoteHost + 
					(instanceId != null ? " [instance " + instanceId + "]" : ""));
	        Object[] res = (Object[]) result;
	        // remember the secret key shared with the other device
	        sharedKey = (byte[]) res[0];
	        // and extract the shared authentication key for phase 2
	        byte[] authKey = (byte[]) res[1];
	        logger.debug("Shared session key is now '" + new String(Hex.encodeHex(sharedKey)) + 
	        		"' with length " + sharedKey.length + 
	        		", shared authentication key is now '" + new String(Hex.encodeHex(authKey)) + 
	        		"' with length " + authKey.length + 
					(instanceId != null ? " [instance " + instanceId + "]" : ""));
	        // then extraxt the optional parameter
	        String param = (String) res[2];
	        /* TODO: this could need some error handling, but at the moment we depend on it being set
	         * (it should be, since we set keepSocketConnected=true for HostProtocolHandler)
	         */
	        socketToRemote = (Socket) res[3];

	        // finally change state and fire off the key verification
        	state = STATE_VERIFICATION_RUNNING;
	        startVerification(authKey, remoteHost, param, socketToRemote);
	    }

	    public void AuthenticationFailure(Object sender, Object remote, Exception e, String msg)
	    {
	    	if (isIdle()) {
	    		logger.debug("Received host authentication event from " + sender + " in idle state - assuming to be the server." + 
						(instanceId != null ? " [instance " + instanceId + "]" : ""));
	    		state = STATE_HOST_AUTH_RUNNING;
	    	}
	    	InetAddress remoteHost = (InetAddress) remote;
	    	if (state != STATE_HOST_AUTH_RUNNING) {
	    		logger.error("Received host authentication failure event with remote host " + remoteHost + 
	    				" while not expecting one! This event will be ignored." + 
						(instanceId != null ? " [instance " + instanceId + "]" : ""));
	    		return;
	    	}
			
	        logger.info("Received host authentication failure with " + remote + 
					(instanceId != null ? " [instance " + instanceId + "]" : ""));
	        if (e != null)
	            logger.info("Exception: " + e);
	        if (msg != null)
	            logger.info("Message: " + msg);
	        // this will also call the derived classes hook
	        authenticationFailed(remoteHost, null, e, msg);
	    }

	    public void AuthenticationProgress(Object sender, Object remote, int cur, int max, String msg)
	    {
	    	if (isIdle()) {
	    		logger.debug("Received host authentication event from " + sender + " in idle state - assuming to be the server." + 
						(instanceId != null ? " [instance " + instanceId + "]" : ""));
	    		state = STATE_HOST_AUTH_RUNNING;
	    	}
	    	if (state != STATE_HOST_AUTH_RUNNING) {
	    		logger.error("Received host authentication progress event with remote host " + remote + 
	    				" while not expecting one! This event will be ignored." + 
						(instanceId != null ? " [instance " + instanceId + "]" : ""));
	    		return;
	    	}
			
	        logger.debug("Received host authentication progress event with " + remote + " " + cur + " out of " + max + ": " + msg + 
					(instanceId != null ? " [instance " + instanceId + "]" : ""));
	        // this is not optional because we don't know the number of rounds to use yet
	        raiseAuthenticationProgressEvent(remote, cur, 
	        		HostProtocolHandler.AuthenticationStages + DongleProtocolHandler.AuthenticationStages,
	        		msg);
	        // also call the hook of derived classes
	        protocolProgressHook((InetAddress) remote, null, cur, max, msg);
	    }
	}

	
	/** This method must be implemented by derived classes. It shoudl start the
	 * verification of the shared key to make sure that the other host shared the
	 * same and thus rule out man-in-the-middle attacks during the Diffie-Hellman
	 * key agreement.
	 * 
	 * @param sharedAuthenticationKey This key should be verified to be equal on
	 *                                both sides.
	 * @param remote The remote host with which the key exchange succeeded.
	 * @param param Optional parameters sent by the remote host during the key agreement
	 *              phase.
	 * @param socketToRemote This socket is still open and can be used to communicate
	 *                       with the remote host for verifying the authentication
	 *                       key. When it is used, care <b>must</b> be taken not to
	 *                       consume any bytes from the remote end that are not expected
	 *                       during verification, because the same channel will be
	 *                       used for exchanging status information about the success
	 *                       or failure of the whole authentication protocol.
	 */
	protected abstract void startVerification(byte[] sharedAuthenticationKey, 
			InetAddress remote, String param, Socket socketToRemote);
	
	/** This hook will be called when the object is reset to its "idle" state,
	 * i.e. so that subsequent authentications can be performed. Derived classes
	 * should implement it to react to being reset. A reset of the object will
	 * occur after both failure and after success of the whole authentication
	 * protocol.
	 */
	protected abstract void resetHook();
	
	/** This hook will be called when the final verdict is that the whole 
	 * authentication protocol succeeded, i.e. both hosts signalled success on
	 * key verification.
	 * @param remote The remote host with which the key exchange succeeded.
	 * @param optionalRemoteId An optional remote ID, exactly as it has been passed
	 *                         to verificationSuccess. May be null. 
	 * @param optionalParameterFromRemote If the remote device reported an additional
	 *                                    parameter with its success message, it will
	 *                                    be put into this parameter. May be null.
	 * @param sharedSessionKey The shared session key (which is different from the
	 *                         shared authentication key used for verification) that
	 *                         can now be used for subsequent secure communication.
	 */
	protected abstract void protocolSucceededHook(InetAddress remote, 
			Object optionalRemoteId, String optionalParameterFromRemote, 
			byte[] sharedSessionKey);
	
	/** This hook will be called when the whole authentication protocol has
	 * failed. Derived classes should implement it to react to this failure.
	 * @param remote The remote host with which the key exchange succeeded.
	 * @param optionalRemoteId An optional remote ID, exactly as it has been passed
	 *                         to verificationSuccess or verificationFailure. May be null. 
	 * @param e If not null, the exception describing the failure.
	 * @param message If not null, the message describing the failure.
	 */
	protected abstract void protocolFailedHook(InetAddress remote, 
			Object optionalRemoteId, Exception e, String message);

	/** This hook will be called when the whole authentication protocol has
	 * made some progress. Derived classes should implement it to react to 
	 * this progress.
	 * @param remote The remote host with which the key exchange succeeded.
	 * @param optionalRemoteId An optional remote ID, exactly as it has been passed
	 *                         to verificationSuccess or verificationFailure. May be null. 
	 * @param cur @see AuthenticationProgressHandler#AuthenticationProgress
	 * @param max @see AuthenticationProgressHandler#AuthenticationProgress
	 * @param message @see AuthenticationProgressHandler#AuthenticationProgress
	 */
	protected abstract void protocolProgressHook(InetAddress remote, 
			Object optionalRemoteId, int cur, int max, String message);
}
