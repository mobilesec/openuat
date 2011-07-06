/* Copyright Rene Mayrhofer
 * File created 2007-02-18
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.channel.main.bluetooth.jsr82;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import javax.bluetooth.DataElement;
import javax.bluetooth.LocalDevice;
import javax.bluetooth.RemoteDevice;
import javax.bluetooth.ServiceRecord;
import javax.bluetooth.UUID;

import java.util.logging.Logger;
import org.openuat.authentication.AuthenticationEventSender;
import org.openuat.authentication.AuthenticationProgressHandler;
import org.openuat.authentication.HostProtocolHandler;
import org.openuat.authentication.KeyManager;
import org.openuat.authentication.exceptions.InternalApplicationException;
import org.openuat.channel.main.HostAuthenticationServer;
import org.openuat.channel.main.ProtocolCommandHandler;
import org.openuat.channel.main.RemoteConnection;
import org.openuat.channel.main.bluetooth.jsr82.BluetoothPeerManager;

/** This class tries to authenticate to all Bluetooth devices that are
 * discovered to support the OpenUAT authentication service. It uses the
 * BluetoothPeerManager for continuous background inquiries and SDP service
 * browsing, BluetoothRFCOMMServer for providing the authentication service
 * itself, and HostProtocolHandler with BluetoothRFCOMMChannel to 
 * opportunistically connect to all discovered authentication services. 
 * 
 * As this class currently uses a hard-coded UUID for the Bluetooth 
 * authentication service, it is a singleton, i.e. there can be only
 * one instance on each host.
 * 
 * In future revisions, other protocols should probably be supported as well
 * in addition to Diffie-Hellman key agreement that is currently used 
 * implicitly via HostProtocolHandler.
 * 
 * Clients of this class should register for AuthenticationProgressEvents and,
 * upon AuthenticationSuccessEvent, proceed to verify the peer device. 
 * KeyManager would be a good option to register as a listener.
 * 
 * <b>Note</b>: Due to a circular dependency, for best operation (read: in the
 * normal case when you don't know exactly why it should be otherwise), a 
 * KeyManager object needs to be registered twice with the 
 * BluetoothOpportunisticConnector singleton. First, it needs to be registered
 * as an AuthenticationProgressHandler by calling the method 
 * addAuthenticationProgressHandler(keyManager.getHostAuthenticationHandler()).
 * This enabled the KeyManager to react to HostAuthentication events and 
 * therefore manage the keys that are agreed to by (incoming or outgoing)
 * HostProtocolHandler runs. Second, the same KeyManager objects needs to be
 * set by a call to setKeyManager(keyManager), so that outgoing connection
 * attempts will be prevented when the respective remote device already has a
 * known state (i.e. a key agreement is running or has already finished).
 * Both calls should be made before starting the RFCOMM service and the 
 * background inquiry with start().
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class BluetoothOpportunisticConnector extends AuthenticationEventSender
		implements HostAuthenticationServer {
	/** Our logger. */
	private static Logger logger = Logger.getLogger(/*BluetoothOpportunisticConnector.class*/ "org.openuat.util.BluetoothOpportunisticConnector");

	/** This is the Bluetooth service UUID used for the opportunistic 
	 * authentication service.
	 */
	public final static UUID serviceUUID = new UUID("c9cb7b4591ee46e392f50933394f613e", false);
	
	/** The Bluetooth service will be advertised under this friendly name. */
	public final static String serviceName = "OpenUAT Opportunistic Authentication";
	
	/** The maximum number of connection attempts for a service that 
	 * advertises the serviceUUID.
	 */
	public final static int maxConnectionRetries = 3;
	
	/** The sleep time before re-attempting a connection in ms. */
	public final static int retryConnectionDelay = 20000;
	
	/** The maximum duration, in ms, that we allow a key agreement to take. */
	public final static int maximumKeyAgreementRuntime = 15000;
	
	public final static boolean useJSSE = false;

	/** true if RFCOMM channels should be left open after successful key
	 * agreement, false if they should be closed.
	 */
	private static boolean keepConnected = false;
	
	/** The singleton instance of this class, created when getInstance() is
	 * called for the first time.
	 */
	private static BluetoothOpportunisticConnector singleton = null; 
	
	/** Our own authentication service. */
	private BluetoothRFCOMMServer service;

	/** The optional parameter to send in the key agreement protocol: this will
	 * be the URL that the local RFCOMM server is reachable at, i.e. the URL at
	 * which the remote can "call back".
	 * It is set in start() and used when starting a HostProtocolHandler in
	 * attemptConnection().
	 */
	private String optionalParameter = null;

	/** The peer manager for discovering other devices and their authentication
	 * services.
	 */
	private BluetoothPeerManager manager;
	
	/** This reference to the key manager, if set, is used only for making 
	 * sure that we don't start another key agreement when a key with a remote 
	 * device is already known.
	 */
	private KeyManager keyManager;
	
	/** This is a queue of connections that should be established (either for
	 * the first time or re-tried after failure). Keys are of type String 
	 * (connection URLs), values of type Integer (the number of retries). A 
	 * special case is that the number of retries may be negative, which 
	 * signifies that a connection is currently attempted.
	 */ 
	private Hashtable connectionsQueue = new Hashtable();
	
	/** Failed connections will be re-scheduled using this timer object. */
	private Timer connectionRetryTimer = null;
	
	/** Initializes the object with defaults, but does not start any server 
	 * or inquiry processes yet.
	 * @throws IOException If the Bluetooth support could not be initialized.
	 */
	protected BluetoothOpportunisticConnector() throws IOException {
		logger.finer("Creating BluetoothOpportunisticConnector instance, initializing BluetoothPeerManager");
		manager = new BluetoothPeerManager();
		manager.setAutomaticServiceDiscovery(true);
		manager.setAdaptiveSleepTime(true);
		manager.setAutomaticServiceDiscoveryUUID(serviceUUID);
		manager.addListener(new BluetoothPeerEventsHandler());
		// TODO: activate timeout again when it is stable on J2ME!
		service = new BluetoothRFCOMMServer(null, serviceUUID, serviceName, -1, /*maximumKeyAgreementRuntime,*/ keepConnected, useJSSE);
		service.addAuthenticationProgressHandler(new AuthenticationEventsHandler(true));
	}
	
	/** Returns the local instance of BluetoothOpportunisticConnector.
	 * @throws IOException */
	public static BluetoothOpportunisticConnector getInstance() throws IOException {
		if (singleton == null) {
			logger.finer("Creating BluetoothOpportunisticConnector singleton");
			singleton = new BluetoothOpportunisticConnector();
		}
		
		return singleton;
	}

	/** Returns the URL under which the Bluetooth service is reachable for 
	 * incoming authentication requests. This URL may be used by clients to
	 * connect (when e.g. transmitted over some out-of-band means).
	 */
	public String getRegisteredServiceURL() {
		return service.getRegisteredServiceURL();
	}

	/** Gets the current value of keepConnected.
	 * 
	 * @return true if RFCOMM channels will be left open after successful key
	 * 		   agreement, false if they will be closed.
	 */
    public static boolean getKeepConnected() {
    	return keepConnected;
    }

    /** Sets the current value of keepConnected.
     * 
     * @param keepConnected true if RFCOMM channels should be left open after successful key
	 * 		   agreement, false if they should be closed.
     */
    public static void setKeepConnected(boolean keepConnected) {
    	BluetoothOpportunisticConnector.keepConnected = keepConnected;
    }

	/** @see HostProtocolHandler#addProtocolCommandHandler */
    public void addProtocolCommandHandler(String command, ProtocolCommandHandler handler) {
    	service.addProtocolCommandHandler(command, handler);
    }

	/** @see HostProtocolHandler#removeProtocolCommandHandler */
    public boolean removeProtocolCommandHandler(String command) {
    	return service.removeProtocolCommandHandler(command);
    }

    /** @see HostProtocolHandler#setProtocolCommandHandlers*/
    public boolean setProtocolCommandHandler(Hashtable handlers) {
    	return service.setProtocolCommandHandler(handlers);
    }
    
    /** Sets the keyManager object that should be used for preventing to start
     * new connections when hosts are already known (by the keyManager).
     * @param keyManager This object will be queried for known keys before 
     *        attempting a new connection.
     *        
     * TODO: This interface is a bit ugly and should probably be refactored.
     *       But the problem is the circular dependency DHWithVerification
     *       -> HostServerBase (the latter needs to be given to the former's
     *       constructor) and BluetoothOpportunisticConnector -> KeyManager
     *       (where the former implements the HostServerBase interface, but
     *       needs access to the keyManager object contained within the e.g.
     *       DHWithVerification).
     */
    public void setKeyManager(KeyManager keyManager) {
    	this.keyManager = keyManager;
    }
    
	/** Starts the local authentication service and the background discovery 
	 * of remote devices and their authentication services. As soon as another
	 * authentication service is discovered, this class will try to connect to 
	 * it and run a HostProtocolHandler instance with it.
	 * @throws IOException
	 */
	public void start() throws IOException {
		if (!service.isRunning()) {
			logger.finer("Starting RFCOMM service and background inquiries");
			service.start();
			optionalParameter = service.getRegisteredServiceURL();
			manager.startInquiry(true);
		}
		else
			logger.severe("Can not start Bluetooth service, because one is already running");
	}
	
	/** Stops the local authentication service and the background inquiry. */
	public void stop() {
		if (service.isRunning()) {
			logger.finer("Stopping RFCOMM service and background inquiries");
			try {
				manager.stopInquiry(true);
				service.stop();
			} 
			catch (InternalApplicationException e) {
				logger.severe("Could not properly close RFCOMM service socket: " + e);
			}
		}
		else
			logger.severe("Can not stop Bluetooth service, because none is running");
	}
	
	/** Make sure to free resources when destroyed - particularly to remove 
	 * the SDP record again (which happens in service.stopListening.
	 */
	public void dispose() {
		stop();
		BluetoothRFCOMMChannel.shutdownAllChannels();
	}

	/** This is a helper method to attempt a new connection. If it fails,
	 * if will be (re-)queued in failedConnections.
	 * @see #failedConnections
	 * @param connectionURL The URL to connect to.
	 */
	private boolean attemptConnection(String connectionURL) {
		// TODO: debug
		logger.warning("Attempting to connect to '" + connectionURL + "'");
		BluetoothRFCOMMChannel channel;
		int numRetries = -1;

		synchronized (connectionsQueue) {
			if (connectionsQueue.containsKey(connectionURL)) {
				numRetries = ((Integer) connectionsQueue.get(connectionURL)).intValue();
				if (numRetries < 0) {
					// this is the sign that a connection attempt is already running in parallel!
					logger.warning("Connection attempt to '" + connectionURL + 
							"' is currently running, not starting a second one. This should not happen.");
					return false;
				}
			}
			else {
				logger.warning("attemptConnection called with '" + connectionURL +
						"' which was not found in connectionsQueue. This should not happen!");
				// we are about to start a connection attempt, so create the lock
				connectionsQueue.put(connectionURL, new Integer(-1));
			}
		}

		String localAddress, remoteAddress;
		try {
			channel = new BluetoothRFCOMMChannel(connectionURL);
			localAddress = LocalDevice.getLocalDevice().getBluetoothAddress();
			remoteAddress = (String) channel.getRemoteAddress();
		} catch (IOException e) {
			logger.severe("Can't initialize Bluetooth subsystem and/or get local address", e);
			return false;
		}

		// sanity check
		if (localAddress.equals(remoteAddress)) {
			logger.severe("Can't talk to myself - not connecting to Bluetooth address '" + 
					remoteAddress + "'");
			return false;
		}
		
		// don't start an outgoing key agreement if we already have a key with that host
		if (keyManager != null) {
			try {
				int stateWithRemote = keyManager.getState(new BluetoothRFCOMMChannel(remoteAddress, -1));
				if (stateWithRemote != KeyManager.STATE_NONEXISTANT &&
						stateWithRemote != KeyManager.STATE_IDLE) {
					logger.info("Already in state " + stateWithRemote + " with remote '" +
							remoteAddress + "', not running another key agreement, aborting");
					synchronized (connectionsQueue) {
						connectionsQueue.remove(connectionURL);
					}
					return false;
				}
			} catch (IOException e) {
				logger.severe("Can't initialize Bluetooth subsystem - this should not happen when we get to here!", e);
				return false;
			}
		}
		else
			logger.warning("Can not check for existing state with remote, as we don't have a valid KeyManager");

		/* This is a small but hopefully effective hack to prevent two devices 
		 * from trying to contact each other at the same time: the higher MAC 
		 * address backs off for the first try, and will only try if not contacted
		 * in the mean time.
		 */
		if (numRetries == 0) {
			if (localAddress.compareTo(remoteAddress) > 0) {
				if (logger.isInfoEnabled())
					// TODO: debug
					logger.warning("My Bluetooth address '" + localAddress +
						"' is higher than the remote address to connect to '" + 
						remoteAddress + "', backing off and waiting for remote to connect");
				// but this counts as a failed attempt, or we would never do it...
				connectionsQueue.put(connectionURL, new Integer(++numRetries));
				return false;
			}
		}
		
		// before trying to connect, need to stop background inquiry and wait for it to finish
		boolean wasRunning = manager.isInquiryActive();
		if (wasRunning) {
			if (!manager.stopInquiry(false))
				// TODO: info
				logger.warning("Unable to stop background inquiry, connection attempt may fail");
		}
		try {
			if (!manager.waitForBackgroundSearchToFinish(retryConnectionDelay))
				// TODO: info
				logger.warning("Unable to wait for background search to finish, connection attempt may fail");
		}
		catch (InterruptedException e) {
			// just ignore, doesn't matter
		}
		
		boolean success;
		try {
			if (logger.isLoggable(Level.FINER))
				// TODO: debug
				logger.warning("Attempting to connect to '" + connectionURL + "' with " +
					numRetries + " failures so far");
			channel.open();
			if (logger.isLoggable(Level.FINER))
				// TODO: debug
				logger.warning("Connection to '" + connectionURL + "' established, starting key agreement");
			HostProtocolHandler.startAuthenticationWith(channel, 
					new AuthenticationEventsHandler(false), maximumKeyAgreementRuntime,
					keepConnected, optionalParameter, useJSSE);
			// TODO: info
			logger.warning("Discovered remote device  " + 
					channel.getRemoteAddress() + "/'" + 
					channel.getRemoteName() + 
					"' which advertises opportunistic authentication, started key agreement");
			// if we get here, the connection itself succeeded
			synchronized (connectionsQueue) {
				connectionsQueue.remove(connectionURL);
			}
			success = true;
		} catch (IOException e) {
			// check if we can (re-)schedule
			synchronized (connectionsQueue) {
				if (numRetries >= maxConnectionRetries) {
					// no more retries for this one
					logger.warning("Could not connect to '" + connectionURL +
							"' after " + maxConnectionRetries + " tries, aborting");
					connectionsQueue.remove(connectionURL);
					// maybe we can stop the timer now, if there are no more scheduled attempts
					stopTimer();
				}
				else {
					if (numRetries == -1) {
						// first failed attempt, so start counter there
						connectionsQueue.put(connectionURL, new Integer(1));
						numRetries = 1;
					}
					else
						connectionsQueue.put(connectionURL, new Integer(++numRetries));
					// TODO: info
					logger.warning("Could not connect to remote service '" + connectionURL + 
							"' after " + (numRetries-1) + " previously failed attempts, will retry in " + 
							retryConnectionDelay + "ms");
					// if we get here, we have re-scheduled, so maybe need to start the timer
					startTimer(false);
				}
			}
			success = false;
		}
		finally {
			// don't forget to activate background inquiry again
			if (wasRunning)
				manager.startInquiry(true);
		}
		return success;
	}
	
	/** Small helper function for starting a regular timer if it isn't running. */
	private void startTimer(boolean now) {
		if (connectionRetryTimer == null) {
			logger.finer("No retry timer task running, starting it now");
			connectionRetryTimer = new Timer();
			connectionRetryTimer.schedule(new ConnectionRetryTask(), now ? 10 : retryConnectionDelay);
		}
	}
	
	/** Small helper function for stopping the timer if it is running. */
	private void stopTimer() {
		if (connectionRetryTimer == null) {
			logger.severe("Can not stop timer when no one is running. Ignoring, but this should not happen!");
			return;
		}
		if (connectionsQueue.isEmpty()) {
			logger.finer("Removed the last scheduled connection, stopping timer task");
			connectionRetryTimer.cancel();
			connectionRetryTimer = null;
		}
	}
	
	/** This is a simple helper task to re-attempt failed connections. */
	private class ConnectionRetryTask extends TimerTask {
		//@Override
		public void run() {
			synchronized (connectionsQueue) {
				if (!connectionsQueue.isEmpty()) {
					logger.finer("Timer task running, (re-)attempting " +
							connectionsQueue.size() + " connections");
					Enumeration urls = connectionsQueue.keys();
					while (urls.hasMoreElements()) {
						String url = (String) urls.nextElement();
						logger.finer("(Re-)trying connection to '" + url + "'");
						attemptConnection(url);
					}
				}

				// if there are still some connections left, reschedule
				if (!connectionsQueue.isEmpty())
					connectionRetryTimer.schedule(new ConnectionRetryTask(), retryConnectionDelay);
				// otherwise, we stop...
				else
					connectionRetryTimer = null;
			}
		}
	}
	
	protected class BluetoothPeerEventsHandler implements BluetoothPeerManager.PeerEventsListener {
		public void inquiryCompleted(Vector newDevices) {
			// we are not interested in devices, only in services - just ignore this event
		}

		public void serviceSearchCompleted(RemoteDevice remoteDevice, Vector services, int errorReason) {
			if (logger.isInfoEnabled())
				// TODO: debug
				logger.warning("Discovered new remote device " + remoteDevice.getBluetoothAddress() +
					"/'" + BluetoothPeerManager.resolveName(remoteDevice) + "' with " +
					services.size() + " matching authentication services");
			
			// only act on success
			if (errorReason == 0) {
				for (int i=0; i<services.size(); i++) {
					ServiceRecord sr = (ServiceRecord) services.elementAt(i); 
					// this is a sanity check
					DataElement ser_de = sr.getAttributeValue(0x100);
					String name = (String) ser_de.getValue();
					if (! name.equals(serviceName)) {
						logger.info("Ignoring discovered service with name '" + name +
								"', expected '" + serviceName + "'");
					}
					else {
						// ok, service known, schedule for connection
						synchronized (connectionsQueue) {
							connectionsQueue.put(sr.getConnectionURL(ServiceRecord.NOAUTHENTICATE_NOENCRYPT, false), 
									new Integer(0)); // with 0 failed attempts so far
						}
						startTimer(true);
					}
				}
			}
		}
	}
	
	protected class AuthenticationEventsHandler implements AuthenticationProgressHandler {
		public boolean incoming;
		
		protected AuthenticationEventsHandler(boolean incoming) {
			this.incoming = incoming;
		}
		
		public void AuthenticationFailure(Object sender, Object remote, Exception e, String msg) {
			logger.finer("Could not create shared key with " + remote + ": " + e + "/" + msg);
			raiseAuthenticationFailureEvent(remote, e, msg);
		}

		public void AuthenticationProgress(Object sender, Object remote, int cur, int max, String msg) {
			logger.finer("Authentication progress with " + remote + ": " + cur + "/" + max + ": " + msg);
			raiseAuthenticationProgressEvent(remote, cur, max, msg);
		}

		public boolean AuthenticationStarted(Object sender, Object remote) {
			logger.finer("Authentication started with " + remote);
			return raiseAuthenticationStartedEvent(remote);
		}
		
		public void AuthenticationSuccess(Object sender, Object remote, Object result) {
			logger.finer("Successfully agreed to key with " + remote);
			raiseAuthenticationSuccessEvent(remote, result);
		}
	}
	
	///////////////////////////////////////
	// test code
//#if cfg.includeTestCode
	private static class MotionVerificationCommandMirror implements ProtocolCommandHandler {
		public boolean handleProtocol(String firstLine, RemoteConnection remote) {
			// sanity check
			if (! firstLine.startsWith(org.openuat.authentication.accelerometer.ShakeWellBeforeUseProtocol1.MotionVerificationCommand)) {
				logger.severe("MotionVerificationCommandHandler invoked with a protocol line that does not start with the expected command '" + 
						org.openuat.authentication.accelerometer.ShakeWellBeforeUseProtocol1.MotionVerificationCommand + "'. This should not happen!");
				return false;
			}
			System.out.println("Motion verification handler entered with remote " + remote + ", now mirroring everything back");
			
			BluetoothRFCOMMChannel.TempHandler h = new BluetoothRFCOMMChannel.TempHandler(true, false);
			h.inVerificationPhase(remote, false);

			System.out.println("Motion verification handler entered with remote " + remote + " exiting");

			return true;
		}
	}
	
	public static void main(String[] args) throws IOException {
		BluetoothOpportunisticConnector c = BluetoothOpportunisticConnector.getInstance();
		// yes, we support concurrent verification here for this test
		KeyManager km = new KeyManager(true, "the one and only");
		c.addAuthenticationProgressHandler(km.getHostAuthenticationHandler());
		c.setKeyManager(km);
		if (args.length > 0 && args[0].equals("mirror")) {
			System.out.println("Switching to mirror attack mode, registering handler");
			// don't do this anymore - we now have 2 split phases
			//km.addVerificationHandler(new BluetoothRFCOMMChannel.TempHandler(true, false));
			c.addProtocolCommandHandler(org.openuat.authentication.accelerometer.ShakeWellBeforeUseProtocol1.MotionVerificationCommand, 
					new MotionVerificationCommandMirror());
		}
		c.start();
		System.in.read();
		c.stop();
		// proper shutdown
		BluetoothRFCOMMChannel.shutdownAllChannels();
	}
//#endif
}
