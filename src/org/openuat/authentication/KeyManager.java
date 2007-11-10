/* Copyright Rene Mayrhofer
 * File created 2007-05-31
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.authentication;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import org.apache.commons.codec.binary.Hex;
import org.apache.log4j.Logger;
import org.openuat.util.RemoteConnection;

/** This class manages shared secret keys. It assumes that up to two keys are
 * shared with a remote host, an authentication key and a session key for 
 * further secure communication. Remote hosts are identified by their 
 * RemoteConnection objects, which must implement proper equals() methods.
 * Note that RemoteConnection objects are used primarily for identification
 * purposes, but also for communication with the remote host. A caller may not
 * assume that the RemoteConnection reference can be used to communicate at 
 * all times - the respective channel may have been closed.
 * 
 * A remote host can, as far as this class is concerned, be in one of the
 * following states:
 * <ul>
 * <li>STATE_NONEXISTANT: This remote host is simply unknown and has not yet
 * been involved in any key agreement with this KeyManager object.
 * <li>STATE_IDLE: The host is known, but key agreement has not yet been 
 * started. No keys are known at this time.
 * <li>STATE_KEY_AGREEMENT: The host is known, and a key agreement with it is
 * currently running. No keys are known at this time.
 * <li>STATE_VERIFICATION: The host is known and two keys have already
 * been agreed to. The keys are unauthenticated and can not yet be used for
 * secure communication.
 * <li>STATE_SUCCEEDED: An authenticated secret shared (session) key is known 
 * for this host and can be used for secure communication. The authentication
 * key has already been wiped from memory. 
 * <li>STATE_FAILED: The host is known, but either key agreement or 
 * verification failed. No keys are known at this time, as they have already
 * been wiped from memory.
 * </ul>
 * 
 * When host references are removed or any error happens, key material is
 * actively wiped from memory to prevent leaking secret keys.
 * 
 * This class also extends AuthenticationEventSender and will forward all
 * AuthenticationFailure and AuthenticationProgress events to all registered 
 * listeners. AuthenticationSuccess events are not forwarded, but instead the
 * startVerification method of all registered KeyManagerEventHandler objects
 * will be called after extracting the keys from the AuthenticationSuccess
 * event.
 *
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class KeyManager extends AuthenticationEventSender {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger("org.openuat.authentication.KeyManager" /*KeyManager.class*/);

	/** Possible state, indicates that nothing is known about this host. 
	 */
	public final static int STATE_NONEXISTANT = 0;
	/** Possible state, indicates that the authentication has not been started yet. 
	 */
	public final static int STATE_IDLE = 1;
	/** Possible state, indicates that the host authentication is running.
	 */
	public final static int STATE_KEY_AGREEMENT = 2;
	/** Possible state, indicates that the key verification is running
	 * (and the key agreement has thus implicitly been completed successfully).
	 */
	public final static int STATE_VERIFICATION = 3;
	/** Possible state, indicates that the whole authentication protocol has 
	 * been completed successfully.
	 */
	public final static int STATE_SUCCEEDED = 4;
	/** Possible state, indicates that the whole authentication protocol has failed.
	 */
	public final static int STATE_FAILED = 5;
	
	/** This interface must be implemented by classes used for key 
	 * verification.
	 */
	public interface VerificationHandler {
		void startVerification(byte[] sharedAuthenticationKey, String optionalParam, RemoteConnection toRemote);
	}
	
	private static class State {
		/** The current state of the authentication, one of STATE_NONEXISTANT, STATE_IDLE,
		 * STATE_KEY_AGREEMENT, STATE_VERIFICATION, STATE_SUCCEEDED, STATE_FAILED. The
		 * default is STATE_IDLE, because there will be no State object when we know nothing
		 * about a host.
		 * @see #STATE_NONEXISTANT
		 * @see #STATE_IDLE
		 * @see #STATE_KEY_AGREEMENT
		 * @see #STATE_VERIFICATION
		 * @see #STATE_SUCCEEDED
		 * @see #STATE_FAILED
		 */
		int state = STATE_IDLE;
		
		/** If the state is STATE_VERIFICATION, this contains
		 * the secret authentication key shared with the other device.
		 */
		byte[] authenticationKey = null;

		/** If the state is STATE_VERIFICATION or STATE_SUCCEEDED, this contains
		 * the secret session key shared with the other device. In
		 * STATE_VERIFICATION, it is still unauthenticated and thus not yet usable
		 * for secure communication.
		 */
		byte[] sessionKey = null;
		
		/** This may hold an optional parameter transmitted by the other host 
		 * during key agreement. It only remembers what may be transmitted in
		 * the AuthenticationSuccess event.
		 */
		String optionalParam = null;
		
		/** This may hold an additional reference that key verification 
		 * protocols need to keep track of their state.
		 */
		Object optionalRemoteReference = null;
		
		void wipeSessionKey() {
			logger.info("Wiping session key material for host in state " + state);
			if (sessionKey != null) {
				for (int i=0; i<sessionKey.length; i++)
					sessionKey[i] = 0;
				sessionKey = null;
			}
			System.gc();
		}
		
		void wipeAuthenticationKey() {
			logger.info("Wiping authentication key material for host in state " + state);
			if (authenticationKey != null) {
				for (int i=0; i<authenticationKey.length; i++)
					authenticationKey[i] = 0;
				authenticationKey = null;
			}
			System.gc();
		}
		
		/** Wipes both authenticationKey and sessionKey, if set. */
		public void wipe() {
			wipeAuthenticationKey();
			wipeSessionKey();
			state = STATE_IDLE;
		}
	}
	
	/** A helper class for handling the events from HostProtocolHandler.
	 * Its main purpose is to react to the AuthenticationSuccess event of
	 * HostAuthenticationProtocol (i.e. key agreement). It remembers the
	 * data passed to the AuthenticationSuccess event and wipes all key
	 * material in case of an AuthenticationFailure event.
	 * It also records AuthenticationStarted events so as to mark a remote 
	 * host as being active and therefore prevent multiple concurrent key
	 * agreements with the same host (e.g. one incoming, one outgoing). 
	 */
	private class HostAuthenticationEventHandler implements AuthenticationProgressHandler {
		/** A small helper function to retreive the state for the remote or 
		 * create one if it's not there.
		 */
		private State retreiveState(Object sender, Object remote, boolean allowImplicitTransition) {
	    	if (! (remote instanceof RemoteConnection)) {
	    		logger.error("Received host authentication event from " + sender + " with remote object of unknown type '" + 
	    				remote + "', ignoring" +
		        		(instanceId != null ? " [instance " + instanceId + "]" : ""));
	    		return null;
	    	}
	    	RemoteConnection host = (RemoteConnection) remote;
	    	State remoteState;
	    	if (! hosts.containsKey(host)) {
	    		logger.debug("Received host authentication event from " + sender + " in nonexistant state - assuming to be the server." + 
		        		(instanceId != null ? " [instance " + instanceId + "]" : ""));
	    		remoteState = new State();
	    		remoteState.state = STATE_IDLE;
	    		if (hosts.put(host, remoteState) != null)
	    			logger.warn("Got old object while trying to insert the first one, this should not happen!");
	    	}
	    	else
	    		remoteState = (State) hosts.get(host);
	    	
	    	if (remoteState.state == STATE_IDLE && allowImplicitTransition) {
	    		logger.debug("Received host authentication started event from " + sender + " in idle state - assuming to be the server." + 
		        		(instanceId != null ? " [instance " + instanceId + "]" : ""));
	    		remoteState.state = STATE_KEY_AGREEMENT;
	    	}
	    	if (remoteState.state != STATE_KEY_AGREEMENT) {
	    		logger.error("Received some host authentication event with remote host " + remote + 
	    				" while not expecting one (currently in state " + remoteState.state + 
	    				")! This event will be ignored." +
		        		(instanceId != null ? " [instance " + instanceId + "]" : ""));
	    		return null;
	    	}
	    	return remoteState;
		}
		
		/** Helper function to wipe key material and set state. */
		private void failed(State remoteState) {
			remoteState.wipe();
			remoteState.state = STATE_FAILED;
		}
		
		/** This implementation of AuthenticationProgressHandler.AuthenticationSuccess
		 * remembers all the passed parameters.
		 */
	    public void AuthenticationSuccess(Object sender, Object remote, Object result) {
	        logger.info("Received host authentication success event with " + remote +
	        		(instanceId != null ? " [instance " + instanceId + "]" : ""));

	        State remoteState = retreiveState(sender, remote, false);
	    	if (remoteState == null) return;

	    	// first of all remember all the parameters for later use
	        Object[] res = (Object[]) result;
	        // remember the secret key shared with the other device
	        remoteState.sessionKey = (byte[]) res[0];
	        // and extract the shared authentication key for phase 2
	        remoteState.authenticationKey = (byte[]) res[1];
	        logger.debug("Host " + remote + ": shared session key is now '" + 
	        		new String(Hex.encodeHex(remoteState.sessionKey)) + 
	        		"' with length " + remoteState.sessionKey.length + 
	        		", shared authentication key is now '" + 
	        		new String(Hex.encodeHex(remoteState.authenticationKey)) + 
	        		"' with length " + remoteState.authenticationKey.length +
					(instanceId != null ? " [instance " + instanceId + "]" : ""));
	        // then extraxt the optional parameter
	        remoteState.optionalParam = (String) res[2];
	        // this is mostly a sanity check - but it's unnessesary, we can have a key and use another channel for verification, after all
	        /*if (res.length < 4 || res[3] == null || 
	        		!(res[3] instanceof RemoteConnection) ||
	        		res[3] != remote || !res[3].equals(remote)) {
	        	logger.error("Did not receive a proper remote connection object in authentication success event, can not re-use connection for authentication. Aborting and wiping keys." +
						(instanceId != null ? " [instance " + instanceId + "]" : ""));
	        	if (logger.isDebugEnabled()) {
	        		logger.debug("res.length=" + res.length);
	        		if (res.length >= 4)
	        			logger.debug("res[3]=" + res[3] + 
	        					", res[3] is RemoteConnection=" + (res[3] instanceof RemoteConnection) +
	        					", res[3]==remote=" + (res[3] != remote) + ", res[3].equals(remote)=" + res[3].equals(remote));
	        	}
	        	failed(remoteState);
	        	return;
	        }*/

	    	// check that we don't have two verification runs in parallel if not supported
	    	if (!concurrentVerificationSupported) {
	    		Enumeration e = hosts.keys();
	    		while (e.hasMoreElements()) {
	    			RemoteConnection k = (RemoteConnection) e.nextElement();
	    			State s = (State) hosts.get(k);
	    			if (s.state == STATE_VERIFICATION) {
	    				logger.error("Key verification already running with remote host '" + 
	    						k.getRemoteName() + "' and concurrent verification runs not supported, ignoring event" +
	    						(instanceId != null ? " [instance " + instanceId + "]" : ""));
	    				return;
	    			}
	    		}
	    	}
	        
	        // finally change state and fire off the key verification
        	remoteState.state = STATE_VERIFICATION;
        	for (int i=0; i<verificationHandlers.size(); i++)
        		((VerificationHandler) verificationHandlers.elementAt(i)).startVerification(
        				remoteState.authenticationKey, remoteState.optionalParam, (RemoteConnection) remote);
	    }
		
	    public void AuthenticationFailure(Object sender, Object remote, Exception e, String msg) {
	        logger.info("Received host authentication failure with " + remote +
	        		(instanceId != null ? " [instance " + instanceId + "]" : ""));

	        State remoteState = retreiveState(sender, remote, false);
	    	if (remoteState == null) return;

	    	failed(remoteState);
			
	        if (e != null)
	            logger.info("Exception: " + e);
	        if (msg != null)
	            logger.info("Message: " + msg);
	        // forward this event onwards, as an abort is global for the respective host
	        raiseAuthenticationFailureEvent(remote, e, msg);
	    }

	    public void AuthenticationProgress(Object sender, Object remote, int cur, int max, String msg) {
	        logger.debug("Received host authentication progress event with " + remote + " " + cur + " out of " + max + ": " + msg + 
	        		(instanceId != null ? " [instance " + instanceId + "]" : ""));

	        State remoteState = retreiveState(sender, remote, false);
	    	if (remoteState == null) return;
			
	        // forward the progress event onwards
	        raiseAuthenticationProgressEvent(remote, cur, max, msg);
	    }

		public boolean AuthenticationStarted(Object sender, Object remote) {
	        logger.debug("Received host authentication started event with " + remote + 
	        		(instanceId != null ? " [instance " + instanceId + "]" : ""));

	        // this basically makes sure that a state object is created for this protocol run
	        // and only in this event do we allow an implicit transition from IDLE to KEY_AGREEMENT
	        State remoteState = retreiveState(sender, remote, true);
	        // and if it wasn't IDLE (or nonexistant) before, veto right here
	    	if (remoteState == null) return false;
			
	        // forward the started event onwards
	        return raiseAuthenticationStartedEvent(remote);
		}
	}
	
	/** All known hosts and key states. Keys are of type RemoteConnection,
	 * values of type State.
	 */
	private Hashtable hosts;
	
	/** All registered handlers of the startVerification event. */
	private Vector verificationHandlers;
	
	/** This may be set to distinguish multiple instances running on the same machine. */
	protected String instanceId = null;
	
	/** If set to false, then only one remote host can be in STATE_VERIFICATION
	 * at any time. This can be used when the sensor hardware used for 
	 * key verification can only interact with one remote host at the same 
	 * time.
	 */
	private boolean concurrentVerificationSupported;

	/** Initializes the key manager.
	 * @param concurrentVerificationSupported If set to false, then only one 
	 *        remote host can be in STATE_VERIFICATION at any time. This can 
	 *        be used when the sensor hardware used for key verification can 
	 *        only interact with one remote host at the same time. 
	 * @param instanceId This parameter may be used to distinguish different instances of
	 *                   this class running on the same machine. It will be used in logging
	 *                   and error messages. May be set to null.
	 */	
	public KeyManager(boolean concurrentVerificationSupported, String instanceId) {
		this.concurrentVerificationSupported = concurrentVerificationSupported;
		this.instanceId = instanceId;
		this.hosts = new Hashtable();
		this.verificationHandlers = new Vector();
	}
	
    /** Register a listener for verification events. */
	public void addVerificationHandler(VerificationHandler listener) {
		if (!verificationHandlers.contains(listener))
			verificationHandlers.addElement(listener);
	}
	
    /** De-register a listener for verification events. */
	public boolean removeVerificationHandler(VerificationHandler listener) {
		return verificationHandlers.removeElement(listener);
	}
	
	/** Returns the current state of a remote host. */
	public int getState(RemoteConnection host) {
		if (! hosts.containsKey(host)) {
			logger.info("getState called for unknown host '" + 
					host.getRemoteName() + "', return nonexistant state" +
	        		(instanceId != null ? " [instance " + instanceId + "]" : ""));
			return STATE_NONEXISTANT;
		}
		else {
			State s = (State) hosts.get(host);
			if (s == null) {
				logger.error("State object for host '" + host.getRemoteName() + 
						"' is null. Internal error, this should not happen!" +
		        		(instanceId != null ? " [instance " + instanceId + "]" : ""));
				return STATE_NONEXISTANT;
			}
			else
				return s.state;
		}
	}
	
	/** Simply returns the value of concurrentVerificationSupported passed to 
	 * the constructor.
	 */
	public boolean isConcurrentVerificationSupported() {
		return concurrentVerificationSupported;
	}
	
	/** Returns an implementation of AuthenticationProgressHandler that should
	 * be registered with a HostProtocolHandler so that KeyManager will 
	 * receive the keys that are agreed to.
	 */
	public AuthenticationProgressHandler getHostAuthenticationHandler() {
		return new HostAuthenticationEventHandler();
	}
	
	/** This method returns true if and only if no remote host is currently in
	 * states STATE_KEY_AGREEMENT, STATE_VERIFICATION, or STATE_FAILED. It can be used to 
	 * determine if start() can be called when concurrent verification is not
	 * supported. STATE_FAILED does not count as idle because this needs 
	 * attention (and most probably resetting the failed host).
	 * @see #concurrentVerificationSupported
	 */
	public boolean isIdle() {
		Enumeration e = hosts.keys();
		while (e.hasMoreElements()) {
			State s = (State) hosts.get(e.nextElement());
			if (s.state == STATE_KEY_AGREEMENT || s.state == STATE_VERIFICATION || s.state == STATE_FAILED)
				return false;
		}
		return true;
	}
	
	/** This method returns all host reference for hosts that are in the
	 * requested state.
	 * @param state One of STATE_NONEXISTANT, STATE_IDLE, STATE_KEY_AGREEMENT, 
	 *              STATE_VERIFICATION, STATE_SUCCEEDED, STATE_FAILED.
	 * @return An array of all host reference that are currently in this 
	 *         state. It may be empty (length == 0), but not null.
	 * @throws IllegalArgumentException if state < STATE_IDLE or state > STATE_FAILED.
	 */
	public RemoteConnection[] getHostsInState(int state) {
		if (state < STATE_IDLE || state > STATE_FAILED) {
			throw new IllegalArgumentException("state " + state + " is out of range" +
	        		(instanceId != null ? " [instance " + instanceId + "]" : ""));
		}
		
		Vector tmp = new Vector();
		Enumeration e = hosts.keys();
		while (e.hasMoreElements()) {
			RemoteConnection k = (RemoteConnection) e.nextElement();
			State s = (State) hosts.get(k);
			if (s.state == state)
				tmp.addElement(k);
		}
		RemoteConnection[] ret = new RemoteConnection[tmp.size()];
		for (int i=0; i<tmp.size(); i++)
			ret[i] = (RemoteConnection) tmp.elementAt(i);
		return ret;
	}
	
	/** Sets a remote host to STATE_SUCCEEDED. This is only possible if it has
	 * been in STATE_VERIFICATION at the time this method is called. When this
	 * check is passed, the method also wipes the authentication key, since 
	 * after verification this is no longer needed (and should not be re-used). 
	 * @param host The host to succeed.
	 * @return true if successful, false if the host is nonexistant or not in
	 *              STATE_VERIFICATION.
	 */ 
	public boolean succeed(RemoteConnection host) {
		if (! hosts.containsKey(host)) {
			logger.warn("Can not succeed host '" + 
					host.getRemoteName() + "' in nonexistant state" +
	        		(instanceId != null ? " [instance " + instanceId + "]" : ""));
			return false;
		}
		
		State s = (State) hosts.get(host);
		if (s.state == STATE_VERIFICATION) {
			s.state = STATE_SUCCEEDED;
			// but wipe the authentication key, we no longer need it
			s.wipeAuthenticationKey();
			return true;
		}
		else {
			logger.warn("Can not succeed host '" + 
					host.getRemoteName() + "' that is not in verification state (state is " + s.state +
	        		(instanceId != null ? " [instance " + instanceId + "]" : ""));
			return false;
		}
	}

	/** Sets a remote host to STATE_KEY_AGREEMENT. This is only possible if it has
	 * been in STATE_IDLE at the time this method is called. The method should 
	 * be called upon outgoing connections, while the state will be automatically
	 * updated to STATE_KEY_AGREEMENT for incoming connections. When the host 
	 * reference is unknown when the method is called, it will be created.
	 * @param host The host to reset.
	 * @return true if successful, false if the host is nonexistant or not in
	 *              STATE_IDLE.
	 */
	public boolean startKeyAgreement(RemoteConnection host) {
		State remoteState;
		if (! hosts.containsKey(host)) {
			logger.info("Host '" + host.getRemoteName() + "' is nonexistant when trying to start, creating its state object" +
	        		(instanceId != null ? " [instance " + instanceId + "]" : ""));
    		remoteState = new State();
    		remoteState.state = STATE_IDLE;
    		if (hosts.put(host, remoteState) != null)
    			logger.warn("Got old object while trying to insert the first one, this should not happen!");
		}
		else
			remoteState = (State) hosts.get(host);

		if (remoteState.state == STATE_IDLE) {
			remoteState.state = STATE_KEY_AGREEMENT;
			return true;
		}
		else {
			logger.warn("Can not start host '" + 
					host.getRemoteName() + "' that is not in idle state (state is " + remoteState.state +
	        		(instanceId != null ? " [instance " + instanceId + "]" : ""));
			return false;
		}
	}

	/** Sets a remote host to STATE_FAILED. The method also wipes all key
	 * material that may be held for this host.
	 * @param host The host to fail.
	 * @return true if successful, false if the host is nonexistant.
	 */
	public boolean fail(RemoteConnection host) {
		if (! hosts.containsKey(host)) {
			logger.warn("Can not fail host '" + 
					host.getRemoteName() + "' in nonexistant state" +
	        		(instanceId != null ? " [instance " + instanceId + "]" : ""));
			return false;
		}
		
		State s = (State) hosts.get(host);
		// wipe all key material upon any failure - this is a safe fallback
		s.wipe();
		// but set state correctly, because wipe() will set to idle
		s.state = STATE_FAILED;
		return true;
	}
	
	/** Sets a remote host to STATE_IDLE. The method also wipes all key
	 * material that may be held for this host.
	 * @param host The host to reset.
	 * @return true if successful, false if the host is nonexistant.
	 */
	public boolean reset(RemoteConnection host) {
		if (! hosts.containsKey(host)) {
			logger.warn("Can not reset host '" + 
					host.getRemoteName() + "' in nonexistant state" +
	        		(instanceId != null ? " [instance " + instanceId + "]" : ""));
			return false;
		}

		State s = (State) hosts.get(host);
		// this also sets STATE_IDLE
		s.wipe();
		return true;
	}

	/** Returns the authentication key for a remote host. An authentication key
	 * will only exist if the host is in STATE_VERIFICATION.
	 * @param host The remote host to retrieve the authentication key for.
	 * @return The key if set or null if the host is nonexistant or not in
	 *         STATE_VERIFICATION.
	 */ 
	public byte[] getAuthenticationKey(RemoteConnection host) {
		if (! hosts.containsKey(host)) {
			logger.warn("Can not retrieve authentication key for host '" + 
					host.getRemoteName() + "' in nonexistant state" +
	        		(instanceId != null ? " [instance " + instanceId + "]" : ""));
			return null;
		}
		
		State s = (State) hosts.get(host);
		if (s.state == STATE_VERIFICATION) {
			if (s.authenticationKey == null)
				logger.warn("Host '" + host.getRemoteName() + 
						"' is in verification state, but has no authentication key. This should not happen" +
		        		(instanceId != null ? " [instance " + instanceId + "]" : ""));
			return s.authenticationKey;
		}
		else {
			logger.warn("Can not retrieve authentication key for host '" + 
					host.getRemoteName() + "' that is not in verification state (state is " + s.state +
	        		(instanceId != null ? " [instance " + instanceId + "]" : ""));
			return null;
		}
	}
	
	/** Returns the session key for a remote host. A session key
	 * will only exist if the host is in STATE_SUCCEEDED.
	 * @param host The remote host to retrieve the session key for.
	 * @return The key if set or null if the host is nonexistant or not in
	 *         STATE_SUCCEEDED.
	 */ 
	public byte[] getSessionKey(RemoteConnection host) {
		if (! hosts.containsKey(host)) {
			logger.warn("Can not retrieve session key for host '" + 
					host.getRemoteName() + "' in nonexistant state" +
	        		(instanceId != null ? " [instance " + instanceId + "]" : ""));
			return null;
		}
		
		State s = (State) hosts.get(host);
		if (s.state == STATE_SUCCEEDED) {
			if (s.sessionKey == null)
				logger.warn("Host '" + host.getRemoteName() + 
						"' is in succeeded state, but has no session key. This should not happen" +
		        		(instanceId != null ? " [instance " + instanceId + "]" : ""));
			return s.sessionKey;
		}
		else {
			logger.warn("Can not retrieve session key for host '" + 
					host.getRemoteName() + "' that is not in succeeded state (state is " + s.state +
	        		(instanceId != null ? " [instance " + instanceId + "]" : ""));
			return null;
		}
	}
	
	/** Returns the optional remote reference for a remote host.
	 * This may hold an additional reference that key verification 
	 * protocols need to keep track of their state.
	 * @param host The remote host to retrieve the optional remote reference.
	 * @return The reference if set or null if the host is nonexistant.
	 */ 
	public Object getOptionalRemoteReference(RemoteConnection host) {
		if (! hosts.containsKey(host)) {
			logger.warn("Can not retrieve optional remote reference for host '" + 
					host.getRemoteName() + "' in nonexistant state" +
	        		(instanceId != null ? " [instance " + instanceId + "]" : ""));
			return null;
		}
		
		State s = (State) hosts.get(host);
		return s.optionalRemoteReference;
	}
	
	/** Returns the optional remote reference for a remote host.
	 * This may hold an additional reference that key verification 
	 * protocols need to keep track of their state.
	 * @param host The remote host to retrieve the optional remote reference.
	 * @return The reference if set or null if the host is nonexistant.
	 */ 
	public void setOptionalRemoteReference(RemoteConnection host, Object optionalRemoteReference) {
		if (! hosts.containsKey(host)) {
			logger.warn("Can not set optional remote reference for host '" + 
					host.getRemoteName() + "' in nonexistant state" +
	        		(instanceId != null ? " [instance " + instanceId + "]" : ""));
			return;
		}
		
		State s = (State) hosts.get(host);
		s.optionalRemoteReference = optionalRemoteReference; 
	}

	/** Wipes all key material by calling wipe(). */
	public void dispose() {
		wipe();
	}
	
	/** Wipes all key material by calling wipe() on each State object. */
	public void wipe() {
		logger.info("Wiping all key material" +
        		(instanceId != null ? " [instance " + instanceId + "]" : ""));
		Enumeration e = hosts.keys();
		while (e.hasMoreElements()) {
			State s = (State) hosts.get(e.nextElement());
			if (s != null)
				s.wipe();
		}
	}
}
