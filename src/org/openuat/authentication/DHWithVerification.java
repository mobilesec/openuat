/* Copyright Rene Mayrhofer
 * File created 2007-02-17
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.authentication;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;

import org.apache.log4j.Logger;
import org.openuat.authentication.exceptions.InternalApplicationException;
import org.openuat.util.HostAuthenticationServer;
import org.openuat.util.LineReaderWriter;
import org.openuat.util.RemoteConnection;

/** This is an abstract class that implements the basics of all protocols
 * based on Diffie-Hellman key exchange over streams with subsequent verification
 * of the key material to check that it is equal on both sides. This 
 * verification is necessary to prevent man-in-the-middle attacks. Derived
 * classed need to implement this specific check that the authentication key
 * provided by SimpleKeyAgreement matches by implementing the method
 * startVerification. Either this method can asynchronously start the verification
 * step, i.e. not block the caller, or the internal KeyManager object can be
 * used at any time to verify remote hosts that are in STATE_VERIFICATION.
 *  
 * After the respective key verification procedure has been performed, the
 * derived class should call either verificationSuccess or verificationFailure 
 * depending on the outcome of the check. Upon calling one of the methods, a 
 * status exchange with the remote host will be done over the communication 
 * channel to arrive at a common decision if the whole protocol succeeded. 
 * The final verdict will be signalled by emitting standard authentication 
 * events (as defined by AuthenticationProgressHandler) and by calling either 
 * the protocolSucceededHook or the protocolFailedHook function. In short, 
 * the whole authentication protocol should be used as follows:
 * 
 * 1. Construct the object.
 * Either:
 * 2a. Start the server. This may be done via the startServer method offered 
 *     by a subclass or another object that can accept incoming connections.
 *     Either way, this class's KeyManager instance must be registered to
 *     receive authentication events. 
 * 3b. Start an authentication protocol to a remote device by calling
 *     startAuthentication. This may be done explicitly or by a background
 *     process, as e.g. implemented by BluetoothOpportunisticConnector.
 * Note that, when setting concurrentVerificationSupported=false, only one
 * remote host can be in verified at the same time.
 * 4. After the key agreement phase succeeded, the abstract startVerification
 *    method is called. In this method, derived classes may asynchronously 
 *    start whatever is necessary to verify the provided shared authentication key.
 *    If not using this explicit event for immediately starting verification,
 *    derived classes can query the internal KeyManager component to verify
 *    hosts that are in STATE_VERIFICATION at opportune moments.
 * 5. When a local decision about the key verification for a specific host has 
 *    been made, call either verificationSucceess or verificationFailure.
 * 6. The local decisions will be communicated over the communication channel and if both
 *    devices signalled success, the protocolSucceededHook will be called. In any
 *    other case (both or either of the devices signalled failure on verification),
 *    the protocolFailedHook will be called.
 * Generally, events will be emitted by this class to all registered listeners.
 * 
 * @author Rene Mayrhofer
 * @version 1.3, changes to 1.2: can now deal with arbitrary servers as long as
 *               they implement the new HostAuthenticationServer interface
 *               changes to 1.1: made independent of TCP, but provide a subclass
 *               with the same old interface;
 * 				 changes to 1.0: replaced InetAddress and Socket objects passed 
 *               to events with String and RemoteConnection objects.
 */
public abstract class DHWithVerification extends AuthenticationEventSender {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger("org.openuat.authentication.DHWithVerification" /*DHOverTCPWithVerification.class*/);

	/** This message is sent via the remote channel to the remote upon authentication success. */
	private final static String Protocol_Success = "ACK ";
	/** This message is sent via the remote channel to the remote upon authentication failure. */
	private final static String Protocol_Failure = "NACK ";

	/** If set to true, the connection to the remote host will not be closed after a successful 
	 * authentication, but will be passed as a parameter to the success event. This allows re-use for
	 * additional communication. It will still be closed on authentication failures.
	 * If set to false, it will also be closed on authentication success.
	 */
	private boolean keepConnected;
	
	/** If set to true, the JSSE will be used, if set to false, the Bouncycastle Lightweight API. */
	protected boolean useJSSE;

	/** The key manager instance we are using to keep track of keys and 
	 * states. We don't support concurrent key verification with different
	 * hosts at this time. This may change in the future. 
	 */
	protected KeyManager keyManager;
	
	/** This represents the server component that reacts to incoming 
	 * authentication requests.
	 */
	protected HostAuthenticationServer server;
	
	/** This may be set to distinguish multiple instances running on the same machine. */
	protected String instanceId = null;
	
	/** Construct the object by initializing basic variables.
	 * 
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
	 * @param concurrentVerificationSupported If set to false, then only one 
	 *        remote host can be in STATE_VERIFICATION at any time. This can 
	 *        be used when the sensor hardware used for key verification can 
	 *        only interact with one remote host at the same time.
	 * @param server The server implementation to use. This will be started by
	 *               startListening and stopped by stopListening. 
	 * @param instanceId This parameter may be used to distinguish different instances of
	 *                   this class running on the same machine. It will be used in logging
	 *                   and error messages. May be set to null.
	 */
	protected DHWithVerification(HostAuthenticationServer server, boolean keepConnected,
			boolean concurrentVerificationSupported, String instanceId, boolean useJSSE) {
		this.keepConnected = keepConnected;
		this.useJSSE = useJSSE;
		this.instanceId = instanceId;
		keyManager = new KeyManager(concurrentVerificationSupported, instanceId);
		// and also register ourselves at the key manager
		keyManager.addAuthenticationProgressHandler(new HostAuthenticationEventHandler());
		keyManager.addVerificationHandler(new StartVerificationHandler());
		this.server = server;
		// and don't forget to register our new key manager with the server
		server.addAuthenticationProgressHandler(keyManager.getHostAuthenticationHandler());
	}
	
	/** This simply starts the server part so that it will listen for incoming
	 * authentication requests.
	 * @throws IOException
	 */
	public void startListening() throws IOException {
		server.start();
	}
	
	/** This stops the server part. */
	public void stopListening() {
		try {
			server.stop();
		}
		catch (InternalApplicationException e) {
			// ignore this case - we are shutting down anyway, just free resources
		}
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
		// there's no connectionToRemote object in this state!
		return keyManager.isIdle();
	}
	
	/** Starts the authentication protocol in the background. Listeners should subscribe to
	 * authentication events to get notified about the progress of authentication.
	 * @param remote An already open connection to the remote host.
	 * @param param An optional parameter that should be exchanged with the host, usually describing
	 *              some parameter(s) of the subsequent verification step.
 	 * @param protocolTimeoutMs
	 * 			  The maximum duration in milliseconds that this authentication
	 * 			  protocol may take before it will abort with an AuthenticationFailed
	 * 			  exception. Set to -1 to disable the timeout.

	 * @return true if the authentication could be started, false otherwise.
	 * 
	 * @see AuthenticationEventSender#addAuthenticationProgressHandler
	 */
	public boolean startAuthentication(RemoteConnection remote, int protocolTimeoutMs, String param) 
			throws IOException/*, ConfigurationErrorException, InternalApplicationException*/ {
		if (! isIdle()) {
			logger.warn("Tried to start authentication with host " + remote.getRemoteName() + 
					" while another authentication protocol run is still active. Not starting authentication and " +
					" returning false." + 
					(instanceId != null ? " [instance " + instanceId + "]" : ""));
			return false;
		}
		
		if (!keyManager.startKeyAgreement(remote)) {
        	logger.error("Could not start remote host object, this should not happen! Aborting startAuthentication" +
					(instanceId != null ? " [instance " + instanceId + "]" : ""));
        	return false;
		}
		
		/* This is simple in the implementation, because we just need to start the
		 * host authentication here. When that suceeds, the event handler will 
		 * continue to start the dongle authentication.
		 */ 
		/* There is no need to unregister this new object, since it is only 
		 * registered with a temporary HostProtocolHandler object, which will
		 * be garbage collected when its background authentication thread
		 * finishes. */
		try {
			HostProtocolHandler.startAuthenticationWith(remote,
					keyManager.getHostAuthenticationHandler(), 
					protocolTimeoutMs, true, param, useJSSE);
		} 
		catch (IOException e) {
			// when we can't start here, be sure to reset to a clean state
			reset(remote);
			// and simply rethrow;
			throw e;
		}
		return true;
	}
	
	/** Resets the object to its idle state. After calling reset, incoming as well as outgoing 
	 * authentication protocol runs are again possible. 
	 */
	protected void reset(RemoteConnection remote) {
		if (remote == null) {
			logger.error("Can not reset nonexistant remote object, this should not happen!");
			return;
		}
		if (!keyManager.reset(remote))
			logger.error("Could not reset remote host object, this should not happen!" +
					(instanceId != null ? " [instance " + instanceId + "]" : ""));
		
		// also allow derived classes to do more specific resets
		resetHook(remote);

		// and finally reset the state
		logger.debug("Reset object to idle set" + 
				(instanceId != null ? " [instance " + instanceId + "]" : ""));
	}
	
	/** Small helper function to raise an authentication failure event and set state as well as wipe sharedKey.
	 * 
	 * @param remote The remote device with which the authentication failed.
	 * @param optionalVerificationId If the key verification step yielded any
	 *        ID or reference that can be referred to, this should be set
	 *        by the derived class.
	 * @param e If not null, the exception describing the failure.
	 * @param message If not null, the message describing the failure.
	 */ 
	protected void authenticationFailed(RemoteConnection remote, Object optionalVerificationId, 
			Exception e, String message) {
		if (remote == null) {
			logger.error("Can not fail nonexistant remote object, this should not happen!");
			return;
		}
		if (!keyManager.fail(remote))
			logger.error("Could not fail remote host object, this should not happen!" +
					(instanceId != null ? " [instance " + instanceId + "]" : ""));
		raiseAuthenticationFailureEvent(remote, e, message);
		
		// also allow derived classes to do special failure handling
		protocolFailedHook(remote, optionalVerificationId, e, message);
		
		// no need to keep the connection around in any case - close it properly
		remote.close();
		
		reset(remote);
	}
	
	/** Small helper to send to the remote and wait for the remote message 
	 * (after we sent our own - hint against dead locks) before continuing with 
	 * either success of failure handling.
	 */
	private String remoteStatusExchange(RemoteConnection remote, Object optionalVerificationId, 
			String reportToRemote) {
		try {
    		// this enables auto-flush
    		OutputStreamWriter toRemote = new OutputStreamWriter(remote.getOutputStream());
	    	logger.debug("Sending status to remote: '" + reportToRemote + "'" + 
					(instanceId != null ? " [instance " + instanceId + "]" : ""));
    		toRemote.write(reportToRemote + "\n");
    		toRemote.flush();
    		logger.debug("Status sent, waiting for status from remote" + 
					(instanceId != null ? " [instance " + instanceId + "]" : ""));
			/* do not use a BufferedReader here because that would potentially mess up
			 * the stream for other users of the socket (by consuming too many bytes)
			 */
    		InputStream fromRemote = remote.getInputStream();
    		String remoteStatus = LineReaderWriter.readLine(fromRemote);
	    	logger.debug("Received remote status: '" + remoteStatus + "'" + 
					(instanceId != null ? " [instance " + instanceId + "]" : ""));
    		if (remoteStatus.length() == 0) {
    			logger.error("Could not get status message from remote host" + 
    					(instanceId != null ? " [instance " + instanceId + "]" : ""));
    			authenticationFailed(remote, optionalVerificationId, null, 
    					"Could not get status message from remote host" + 
    					(instanceId != null ? " [instance " + instanceId + "]" : ""));
    			return null;
    		}
    		else
    			return remoteStatus;
        }
        catch (IOException e) {
        	logger.error("Could not report success/failure to remote host or get status message from remote host: " + e + 
					(instanceId != null ? " [instance " + instanceId + "]" : ""));
        	authenticationFailed(remote, optionalVerificationId, e, 
        			"Could not report success/failure to remote host or get status message from remote host" + 
					(instanceId != null ? " [instance " + instanceId + "]" : ""));
        	return null;
        }
	}
	
	/** This method should be called by derived classes after key verification has
	 * been started with the startVerification method. Calling this method signals a
	 * local success of the key verification, but the whole authentication protocol can
	 * still fail when the other host signals a failure. After calling this method, either
	 * the protocolSucceededHook or the protocolFailedHook will be called.
	 *
	 * @param remote The remote host which has been successfully verified.
	 * @param optionalVerificationId If the key verification step yielded any
	 *        ID or reference that can be referred to, this should be set
	 *        by the derived class.
	 * @param optionalParameterToRemote An optional parameter that should be sent to the
	 *                                  remote device alongside the success status message.
	 *                                  This will also be forwarded to the hook functions.
	 * @param
	 */
	protected void verificationSuccess(RemoteConnection remote, Object optionalVerificationId, 
			String optionalParameterToRemote) {
    	String remoteStatus = remoteStatusExchange(remote, optionalVerificationId,
    			Protocol_Success + (optionalParameterToRemote != null ? optionalParameterToRemote : ""));
		
    	if (remoteStatus != null && remoteStatus.length() > 0) {
    		if (remoteStatus.startsWith(Protocol_Success)) {
    			logger.info("Received success status from remote host" + 
    					(instanceId != null ? " [instance " + instanceId + "]" : ""));

    			// mark host as succeeded
		        if (!keyManager.succeed(remote)) {
		        	logger.error("Could not succeed remote host object, this should not happen! Aborting verificationSuccess" +
							(instanceId != null ? " [instance " + instanceId + "]" : ""));
		        	return;
		        }
		        /* for sending the success events, first figure out both aspects of the remote host
		           (i.e. the remote host identifier and the optional remote reference) */
		        Object[] remoteParam = new Object[] {remote, keyManager.getOptionalRemoteReference(remote)};

		        // this string can be null if no optional parameter has been received from the remote host
		        String optionalParameterFromRemote = remoteStatus.substring(Protocol_Success.length());
		        
		        byte[] sessionKey = keyManager.getSessionKey(remote);
		        if (sessionKey == null) {
		        	logger.error("Could not retreive session key for remote host '" + remote.getRemoteName() +
		        			", this should not happen! Aborting verificationSuccess" +
							(instanceId != null ? " [instance " + instanceId + "]" : ""));
		        	return;
		        }

		        // first also call the hook to allow the derived classes to react too
		        protocolSucceededHook(remote, optionalVerificationId, optionalParameterFromRemote, sessionKey);

		        if (!keepConnected) {
			        /* our result object is here the secret key that is shared (host authentication) 
			           and now spatially authenticated (dongle authentication) */
		        	raiseAuthenticationSuccessEvent(remoteParam, sessionKey);
		        }
		        else {
		        	/* It has been requested that the socket be kept open, so pass it over
		        	 * in addition to the shared secret key.
		        	 * As we need to pass two parameters in this case, again use an array...
		        	 */
		        	raiseAuthenticationSuccessEvent(remoteParam, new Object[] {sessionKey, remote});
		        }
		        		
				// if the socket is not going to be re-used, don't forget to close it properly
				if (!keepConnected) {
					logger.info("Closing channel that has been used for key verification");
					remote.close();
				}

				// and finally reset (in failure cases, the authenticationFailed helper will call reset)
		        keyManager.reset(remote);
    		}
    		else if (remoteStatus.startsWith(Protocol_Failure)) {
    			logger.error("Received failure status from remote host although local dongle authentication was successful. Authentication protocol failed" + 
    					(instanceId != null ? " [instance " + instanceId + "]" : ""));
    			authenticationFailed(remote, optionalVerificationId, null, 
    					"Received authentication failure status from remote host" + 
    					(instanceId != null ? " [instance " + instanceId + "]" : ""));
        	}
    		else {
    			logger.error("Unkown status from remote host! Ignoring it (was '" + remoteStatus + "')" + 
    					(instanceId != null ? " [instance " + instanceId + "]" : ""));
    			authenticationFailed(remote, optionalVerificationId, null, 
    					"Unkown status from remote host (was '" + remoteStatus + "')" + 
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
	 * @param remote The remote host which could not be verified.
	 * @param optionalVerificationId If the key verification step yielded any
	 *        ID or reference that can be referred to, this should be set
	 *        by the derived class.
	 * @param optionalParameterToRemote An optional parameter that should be sent to the
	 *                                  remote device alongside the success status message.
	 *                                  This will also be forwarded to the hook function.
	 */
	protected void verificationFailure(RemoteConnection remote, Object optionalVerificationId, 
			String optionalParameterToRemote, Exception e, String msg) {
		String remoteStatus = remoteStatusExchange(remote, optionalVerificationId, 
				Protocol_Failure + (optionalParameterToRemote != null ? optionalParameterToRemote : ""));
        
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

    		authenticationFailed(remote, optionalVerificationId, e, msg);
    	} // if remoteStatus == null, just ignore here because the helper already fired the failure event
	}

	/** A helper class for handling the events from KeyManager to forward 
	 * failure and progress events.  
	 */
	protected class HostAuthenticationEventHandler implements AuthenticationProgressHandler {
	    public void AuthenticationSuccess(Object sender, Object remote, Object result) {
	    	// key manager has taken care of this event, can ignore here
	    }

	    public void AuthenticationFailure(Object sender, Object remote, Exception e, String msg) {
	        logger.info("Received host authentication failure with " + remote + 
					(instanceId != null ? " [instance " + instanceId + "]" : ""));
	        // this will also call the derived classes hook and raise an event to listeners
	        authenticationFailed(((RemoteConnection) remote), null, e, msg);
	    }

	    public void AuthenticationProgress(Object sender, Object remote, int cur, int max, String msg) {
	        logger.debug("Received host authentication progress event with " + remote + " " + cur + " out of " + max + ": " + msg + 
					(instanceId != null ? " [instance " + instanceId + "]" : ""));
	        // this is not optional because we don't know the number of rounds to use yet
	        raiseAuthenticationProgressEvent(remote, cur, 
	        		HostProtocolHandler.AuthenticationStages /*+ DongleProtocolHandler.AuthenticationStages*/,
	        		msg);
	        // also call the hook of derived classes
	        protocolProgressHook(((RemoteConnection) remote), cur, max, msg);
	    }

		public boolean AuthenticationStarted(Object sender, Object remote) {
	        logger.debug("Received host authentication started event with " + remote + 
					(instanceId != null ? " [instance " + instanceId + "]" : ""));
	        boolean ret = raiseAuthenticationStartedEvent(remote);
	        // also call the hook of derived classes
	        protocolStartedHook(((RemoteConnection) remote));
	        return ret;
		}
	}

	/** This helper class is only there for visibility purposes: it calls the
	 * outer class startVerificationAsync method with the arguments passed to
	 * its startVerification method. In theory, the outer class could implement
	 * KeyManager.VerificationHandler itself, but then it would need to be
	 * public. We don't want that.
	 */
	protected class StartVerificationHandler implements KeyManager.VerificationHandler {
		public void startVerification(byte[] sharedAuthenticationKey, String optionalParam, RemoteConnection toRemote) {
	        logger.info("Starting asynchronous verification triggered by host authentication success event with " + toRemote + 
					(instanceId != null ? " [instance " + instanceId + "]" : ""));
	        startVerificationAsync(sharedAuthenticationKey, optionalParam, toRemote);
		}
	}

	
	/** This method must be implemented by derived classes. It shoudl start the
	 * verification of the shared key to make sure that the other host shared the
	 * same and thus rule out man-in-the-middle attacks during the Diffie-Hellman
	 * key agreement.
	 * 
	 * @param sharedAuthenticationKey This key should be verified to be equal on
	 *                                both sides.
	 * @param optionalParam Optional parameters sent by the remote host during the key agreement
	 *              phase.
	 * @param socketToRemote This socket is still open and can be used to communicate
	 *                       with the remote host for verifying the authentication
	 *                       key. When it is used, care <b>must</b> be taken not to
	 *                       consume any bytes from the remote end that are not expected
	 *                       during verification, because the same channel will be
	 *                       used for exchanging status information about the success
	 *                       or failure of the whole authentication protocol.
	 */
	protected abstract void startVerificationAsync(byte[] sharedAuthenticationKey, 
			String optionalParam, RemoteConnection toRemote);
	
	/** This hook will be called when the object is reset to its "idle" state,
	 * i.e. so that subsequent authentications can be performed. Derived classes
	 * should implement it to react to being reset. A reset of the object will
	 * occur after both failure and after success of the whole authentication
	 * protocol.
	 */
	protected abstract void resetHook(RemoteConnection remote);
	
	/** This hook will be called when the final verdict is that the whole 
	 * authentication protocol succeeded, i.e. both hosts signalled success on
	 * key verification.
	 * @param remote The remote host with which the key exchange succeeded. If 
	 *               it has not been requested that the connection should stay 
	 *               open (keepConnected==true), then this will be closed 
	 *               immediately after the hook method returns. 
	 * @param optionalVerificationId If the key verification step yielded any
	 *        ID or reference that can be referred to, then this will be set.
	 *        It is directly forwarded through from the call to 
	 *        verificationSuccess.
	 * @param optionalParameterFromRemote If the remote device reported an additional
	 *                                    parameter with its success message, it will
	 *                                    be put into this parameter. May be null.
	 * @param sharedSessionKey The shared session key (which is different from the
	 *                         shared authentication key used for verification) that
	 *                         can now be used for subsequent secure communication.
	 */
	protected abstract void protocolSucceededHook(RemoteConnection remote,
			 Object optionalVerificationId,	String optionalParameterFromRemote,	byte[] sharedSessionKey);
	
	/** This hook will be called when the whole authentication protocol has
	 * failed. Derived classes should implement it to react to this failure.
	 * @param remote The remote host with which the key exchange failed. If 
	 *               it has not been requested that the connection should stay 
	 *               open (keepConnected==true), then this will be closed 
	 *               immediately after the hook method returns. 
	 * @param optionalVerificationId If the key verification step yielded any
	 *        ID or reference that can be referred to, then this will be set.
	 *        It is directly forwarded through from the call to 
	 *        verificationSuccess or verificationFailed. If the protocol
	 *        already failed during key agreement (or the derived class did
	 *        not set the parameter), then this will be null.
	 * @param e If not null, the exception describing the failure.
	 * @param message If not null, the message describing the failure.
	 */
	protected abstract void protocolFailedHook(RemoteConnection remote,
			 Object optionalVerificationId,	Exception e, String message);

	/** This hook will be called when the whole authentication protocol has
	 * made some progress. Derived classes should implement it to react to 
	 * this progress.
	 * @param remote The remote host with which the key exchange progressed.
	 * @param cur @see AuthenticationProgressHandler#AuthenticationProgress
	 * @param max @see AuthenticationProgressHandler#AuthenticationProgress
	 * @param message @see AuthenticationProgressHandler#AuthenticationProgress
	 */
	protected abstract void protocolProgressHook(RemoteConnection remote,
			 int cur, int max, String message);

	/** This hook will be called when the whole authentication protocol has
	 * been started. Derived classes should implement it to react to 
	 * this progress.
	 * @param remote The remote host with which the key exchange started.
	 */
	protected abstract void protocolStartedHook(RemoteConnection remote);
}
