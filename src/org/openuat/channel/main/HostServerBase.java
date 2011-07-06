/* Copyright Rene Mayrhofer
 * File created 2005-09
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.channel.main;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Vector;

import java.util.logging.Logger;
import org.openuat.authentication.AuthenticationEventSender;
import org.openuat.authentication.HostProtocolHandler;
import org.openuat.authentication.SimpleKeyAgreement;
import org.openuat.authentication.exceptions.*;

/** This is a base class for listening to connections and spawning 
 * HostProtocolHandler objects upon incoming connections. It takes care of
 * handling the background listening thread. 
 *  
 * @author Rene Mayrhofer
 * @version 1.1, changes to 1.0: this is now a base class for TCP and RFCOMM implementations, and startListening can now throw an IOException
 */
public abstract class HostServerBase extends AuthenticationEventSender 
		implements HostAuthenticationServer, Runnable {
	/** Our logger. */
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
	
	/** If =! 1, specifies a timeout for the (incoming) protocol runs started in the background. */
	protected int protocolTimeoutMs;
	
    /** If this is set, then we have some form of user input that has been
     * created _before_ starting a specific protocol instance and is assumed to be
     * secret. This <code>Vector<byte[]></code> may contain multiple entries,
     * in this case each entry is assumed to be a 'candidate secret'.<br/>
     * Note: all entries have to be of the same length!
     */
    protected Vector presharedShortSecrets = null;

    /** If this is set, then we have received a (long) pre-authentication
     * message from the client and will use it to verify its public key.
     * This must be set before the client connects to us (and thus before
     * the HostProtocolHandler instance gets created) to have any effect.
     */
    protected byte[] preAuthenticationMessageFromClient = null;
    
    /** If set, this will be passed on to the constructed HostProtocolHandler
     * objects.
     */
    protected SimpleKeyAgreement permanentKeyAgreementInstance = null;

	/** This only keeps the command handlers so that they can be pre-registered
	 * and then be passed onto HostProtocolHandler objects when they are 
	 * instantiated.
	 */
    protected Hashtable protocolCommandHandlers = null;

	/** Initializes the listener. 
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
	public HostServerBase(boolean keepConnected, boolean useJSSE, int protocolTimeoutMs) {
		this.keepConnected = keepConnected;
		this.useJSSE = useJSSE;
		this.protocolTimeoutMs = protocolTimeoutMs;
	}

	/** @see HostProtocolHandler#addProtocolCommandHandler */
    public void addProtocolCommandHandler(String command, ProtocolCommandHandler handler) {
    	if (protocolCommandHandlers == null)
    		protocolCommandHandlers = new Hashtable();
    	protocolCommandHandlers.put(command, handler);
    }

	/** @see HostProtocolHandler#removeProtocolCommandHandler */
    public boolean removeProtocolCommandHandler(String command) {
    	if (protocolCommandHandlers == null)
    		return false;
    	boolean removed = (protocolCommandHandlers.remove(command) != null);
    	if (protocolCommandHandlers.size() == 0)
    		protocolCommandHandlers = null;
    	return removed;
    }
    
    /** @see HostProtocolHandler#setProtocolCommandHandlers*/
    public boolean setProtocolCommandHandler(Hashtable handlers) {
    	if (protocolCommandHandlers != null)
    		return false;
    	protocolCommandHandlers = handlers;
    	return true;
    }

    /** Sets a preshared short secret as entered by the user. This <b>must</b>
     * remain secret until the protocol finished and <b>must not</b> be re-used 
     * again for another protocol run!
     */
    public void setPresharedShortSecret(byte[] presharedShortSecret) {
    	if (presharedShortSecret == null) {
    		this.presharedShortSecrets = null;
    	}
    	else {
    		this.presharedShortSecrets = new Vector();
    		this.presharedShortSecrets.addElement(presharedShortSecret);
    	}
    }
    
    /** Sets a list of preshared short secrets as entered by the user. 
     * Each entry represents a 'candidate secret'. This <b>must</b>
     * remain secret until the protocol finished and <b>must not</b> be re-used 
     * again for another protocol run!
     */
    public void setPresharedShortSecrets(Vector presharedShortSecrets) {
    	this.presharedShortSecrets = presharedShortSecrets;
    }
    
    /** Returns the user-input preshared short secret.
    * May be null when not used. 
    */
    public Vector getPresharedShortSecrets() {
    	return presharedShortSecrets;
    }

    /** Sets a pre-authentication message received from the client and
     * committing it to its public key that it will use when starting the
     * proper protocol run.
     * This must be set before the client connects to us (and thus before
     * the HostProtocolHandler instance gets created) to have any effect.
     */
    public void setPreAuthenticationMessageFromClient(byte[] publicKeyCommitment) {
    	this.preAuthenticationMessageFromClient = publicKeyCommitment;
    }
    
    /** Returns the pre-authentication message received from the client.
    * May be null when not used. 
    */
    public byte[] getPreAuthenticationMessageFromClient() {
    	return preAuthenticationMessageFromClient;
    }
    
    /** Sets the permanent key agreement instance to use for all subsequent 
     * HostProtocolHandler invocations. This should be used with care and
     * only if ephemeral Diffie-Hellman keys can not be supported. The only
     * real use case at the time of this writing is pre-authentication with
     * long public key commitments when those need to be static (e.g. printed
     * on the case of a device).
     */
    public void setPermanentKeyAgreementInstance(SimpleKeyAgreement keyAgreement) {
    	this.permanentKeyAgreementInstance = keyAgreement;
    }
    
    /** Returns the permanent key agreement instance. 
     * May be null when not used. 
     */
    public SimpleKeyAgreement getPermanentKeyAgreementInstance() {
    	return permanentKeyAgreementInstance;
    }
    
    public byte[] getPermanentPreAuthenticationMessage() {
    	/* This is a bad hack, but at least we only need the commitment
    	 * implementation in HostProtocolHandler. Just construct a temporary
    	 * object for the sake of getting the commitment from the key
    	 * agreement instance.
    	 */
    	if (permanentKeyAgreementInstance == null) {
    		logger.warning("Can not derive permanent pre-authentication commitment when no permanent key agreement instance has been set");
    		return null;
    	}
    	return new HostProtocolHandler(null, 
				presharedShortSecrets, permanentKeyAgreementInstance, 
				protocolTimeoutMs, keepConnected, useJSSE).getPreAuthenticationMessage();
    }

	/** Starts a background thread (using the run() method of this class) that will listen for incoming connections. */
	//@SuppressWarnings("unused") // the exception may be thrown in sub-classes that override startListening
	public void start() throws IOException {
		if (!running) {
			running = true;
			logger.finer("Starting listening thread for server socket");
			listenerThread = new Thread(this);
			listenerThread.start();
			logger.finer("Started listening thread for server socket");
		}
	}

	/** Signals the background listening thread to stop and waits for it. This method will not return until the listening
	 * thread has terminated.
	 */
	public void stop() throws InternalApplicationException {
		if (listenerThread != null) {
			logger.finer("Stopping listening thread for server socket");
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
			logger.finer("Stopped listening thread for server socket");
		}
	}
	
	/** Returns true if the server is running, false otherwise. */
	public boolean isRunning() {
		return running;
	}

	/** This is a small helper function that derived classes should call after
	 * accepting an incoming connection. It fires off a HostProtocolHandler in
	 * the background and registers all listeners beforehand.
	 * @param remote The (already opened) remote connection to use.
	 */
	protected void startProtocol(RemoteConnection remote) {
		HostProtocolHandler h = new HostProtocolHandler(remote, 
				presharedShortSecrets, permanentKeyAgreementInstance, 
				protocolTimeoutMs, keepConnected, useJSSE);
		// before starting the background thread, register all our own listeners with this new event sender
		h.setAuthenticationProgressHandlers(eventsHandlers);
		h.setProtocolCommandHandlers(protocolCommandHandlers);
		h.setPreAuthenticationMessage(preAuthenticationMessageFromClient);
		// call the protocol asynchronously
		logger.finer("Accepted incoming channel, now starting host protocol");
		h.startIncomingAuthenticationThread(true);
	}
}
