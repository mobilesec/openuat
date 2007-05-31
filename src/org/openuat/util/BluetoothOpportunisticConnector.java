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
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import javax.bluetooth.DataElement;
import javax.bluetooth.RemoteDevice;
import javax.bluetooth.ServiceRecord;
import javax.bluetooth.UUID;

import org.apache.log4j.Logger;
import org.openuat.authentication.AuthenticationEventSender;
import org.openuat.authentication.AuthenticationProgressHandler;
import org.openuat.authentication.HostProtocolHandler;
import org.openuat.authentication.exceptions.InternalApplicationException;
import org.openuat.util.BluetoothPeerManager;

/** This class tries to authenticate to all Bluetooth devices that are
 * discovered to support the OpenUAT authentication service. It uses the
 * BluetoothPeerManager for continous backgound inquiries and SDP service
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
 *  Clients of this class should register for AuthenticationProgressEvents and,
 *  upon AuthenticationSuccessEvent, proceed to verify the peer device.
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class BluetoothOpportunisticConnector extends AuthenticationEventSender {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(/*BluetoothOpportunisticConnector.class*/ "org.openuat.util.BluetoothOpportunisticConnector");

	/** This is the Bluetooth service UUID used for the opportunistic 
	 * authentication service.
	 */
	public static UUID serviceUUID = new UUID("c9cb7b4591ee46e392f50933394f613e", false);
	
	/** The Bluetooth service will be advertised under this friendly name. */
	public static String serviceName = "OpenUAT Opportunistic Authentication";
	
	/** The maximum number of connection attempts for a service that 
	 * advertises the serviceUUID.
	 */
	public static int maxConnectionRetries = 3;
	
	/** The sleep time before re-attempting a connection in ms. */
	public static int retryConnectionDelay = 5000;
	
	// TODO: make me configurable - maybe with setters/getters?
	public static boolean keepConnected = true;
	public static boolean useJSSE = false;
	public static String optionalParameter = null;
	
	/** The singleton instance of this class, created when getInstance() is
	 * called for the first time.
	 */
	private static BluetoothOpportunisticConnector singleton = null; 
	
	/** Our own authentication service. */
	private BluetoothRFCOMMServer service;
	
	/** The peer manager for discovering other devices and their authentication
	 * services.
	 */
	private BluetoothPeerManager manager;
	
	/** This is a queue of connections that could not be established for some
	 * reason and that should be re-tried. Keys are of type String (connection
	 * URLs), values of type Integer (the number of retries). A special case 
	 * is that the number of retries may be negative, which signifies that a
	 * connection is currently attempted.
	 */ 
	private Hashtable failedConnections = new Hashtable();
	
	/** Failed connections will be re-scheduled using this timer object. */
	private Timer connectionRetryTimer = null;
	
	protected BluetoothOpportunisticConnector() throws IOException {
		logger.debug("Creating BluetoothOpportunisticConnector instance, initializing BluetoothPeerManager");
		manager = new BluetoothPeerManager();
		manager.setAutomaticServiceDiscovery(true);
		manager.setAdaptiveSleepTime(true);
		manager.setAutomaticServiceDiscoveryUUID(serviceUUID);
		manager.addListener(new BluetoothPeerEventsHandler());
	}
	
	/** Returns the local instance of BluetoothOpportunisticConnector. 
	 * @throws IOException 
	 * @throws IOException */
	public static BluetoothOpportunisticConnector getInstance() throws IOException {
		if (singleton == null) {
			logger.debug("Creating BluetoothOpportunisticConnector singleton");
			singleton = new BluetoothOpportunisticConnector();
		}
		
		return singleton;
	}
	
	/** Starts the local authentication service and the background discovery 
	 * of remote devices and their authentication services. As soon as another
	 * authentication service is discovered, this class will try to connect to 
	 * it and run a HostProtocolHandler instance with it.
	 * @throws IOException
	 */
	public void start() throws IOException {
		logger.debug("Starting RFCOMM service and background inquiries");
		service = new BluetoothRFCOMMServer(null, serviceUUID, serviceName, keepConnected, useJSSE);
		service.addAuthenticationProgressHandler(new AuthenticationEventsHandler(true));
		service.startListening();
		manager.startInquiry(true);
	}
	
	/** Stops the local authentication service and the background inquiry. */
	public void stop() {
		logger.debug("Stopping RFCOMM service and background inquiries");
		try {
			manager.stopInquiry();
			service.stopListening();
			service = null;
		} 
		catch (InternalApplicationException e) {
			logger.error("Could not properly close RFCOMM service socket: " + e);
		}
	}
	
	/** Make sure to free resources when destroyed - particularly to remove 
	 * the SDP record again (which happens in service.stopListening.
	 */
	public void dispose() {
		stop();
	}

	/** This is a helper method to attempt a new connection. If it fails,
	 * if will be (re-)queued in failedConnections.
	 * @see #failedConnections
	 * @param connectionURL The URL to connect to.
	 */
	private boolean attemptConnection(String connectionURL) {
		logger.debug("Attempting to connect to '" + connectionURL + "'");
		BluetoothRFCOMMChannel channel;
		int numRetries = -1;

		synchronized (failedConnections) {
			if (failedConnections.contains(connectionURL)) {
				numRetries = ((Integer) failedConnections.get(connectionURL)).intValue();
				if (numRetries < 0) {
					// this is the sign that a connection attempt is already running in parallel!
					logger.warn("Connection attempt to '" + connectionURL + 
							"' is currently running, not starting a second one. This should not happen.");
					return false;
				}
			}
			else 
				// we are about to start a connection attempt, so create the lock
				failedConnections.put(connectionURL, new Integer(-1));
		}
		
		try {
			channel = new BluetoothRFCOMMChannel(connectionURL);
			HostProtocolHandler.startAuthenticationWith(channel, 
					new AuthenticationEventsHandler(false), keepConnected, optionalParameter, useJSSE);
			logger.info("Discovered remote device  " + 
					channel.getRemoteAddress() + "/'" + 
					channel.getRemoteName() + 
					"' which advertises opportunistic authentication, started key agreement");
			// if we get here, the connection itself succeeded
			synchronized (failedConnections) {
				failedConnections.remove(connectionURL);
			}
			return true;
		} catch (IOException e) {
			// check if we can (re-)schedule
			synchronized (failedConnections) {
				if (numRetries >= maxConnectionRetries) {
					// no more retries for this one
					logger.warn("Could not connect to '" + connectionURL +
							"' after " + maxConnectionRetries + " tries, aborting");
					failedConnections.remove(connectionURL);
					// maybe we can stop the timer now, if there are no more scheduled attempts
					if (failedConnections.isEmpty()) {
						logger.debug("Removed the last scheduled connection, stopping timer task");
						connectionRetryTimer.cancel();
						connectionRetryTimer = null;
					}
					return false;
				}
				else if (numRetries == -1)
					// first failed attempt, so start counter there
					failedConnections.put(connectionURL, new Integer(1));
				else
					failedConnections.put(connectionURL, new Integer(numRetries++));
			}
			logger.warn("Could not connect to remote service '" + connectionURL + 
					"', will retry in " + retryConnectionDelay + "ms");
			// if we get here, we have re-scheduled, so maybe need to start the timer
			if (connectionRetryTimer == null) {
				logger.debug("No retry timer task running, starting it now");
				connectionRetryTimer = new Timer();
				connectionRetryTimer.scheduleAtFixedRate(new ConnectionRetryTask(), 
						retryConnectionDelay, retryConnectionDelay);
			}
			return false;
		}
	}
	
	/** This is a simple helper task to re-attempt failed connections. */
	private class ConnectionRetryTask extends TimerTask {
		public void run() {
			synchronized (failedConnections) {
				if (!failedConnections.isEmpty()) {
					logger.debug("Timer task running, re-attempting " +
							failedConnections.size() + " connections");
					Enumeration urls = failedConnections.keys();
					while (urls.hasMoreElements()) {
						String url = (String) urls.nextElement();
						logger.info("Retrying connection to '" + url + "'");
						attemptConnection(url);
					}
				}
			}
		}
	}
	
	protected class BluetoothPeerEventsHandler implements BluetoothPeerManager.PeerEventsListener {
		public void inquiryCompleted(Vector newDevices) {
			// we are not interested in devices, only in services - just ignore this event
		}

		public void serviceListFound(RemoteDevice remoteDevice, Vector services) {
			if (logger.isInfoEnabled())
				logger.debug("Discovered new remote device " + remoteDevice.getBluetoothAddress() +
					"/'" + BluetoothPeerManager.resolveName(remoteDevice) + "' with " +
					services.size() + " matching authentication services");
			
			for (int i=0; i<services.size(); i++) {
				ServiceRecord sr = (ServiceRecord) services.elementAt(i); 
				// this is a sanity check
				DataElement ser_de = sr.getAttributeValue(0x100);
				String name = (String) ser_de.getValue();
				if (! name.equals(serviceName)) {
					logger.debug("Ignoring discovered service with name '" + name +
							"', expected '" + serviceName + "'");
				}
				else {
					attemptConnection(sr.getConnectionURL(ServiceRecord.NOAUTHENTICATE_NOENCRYPT, false));
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
			logger.debug("Could not create shared key with " + remote + ": " + e + "/" + msg);
			raiseAuthenticationFailureEvent(remote, e, msg);
		}

		public void AuthenticationProgress(Object sender, Object remote, int cur, int max, String msg) {
			raiseAuthenticationProgressEvent(remote, cur, max, msg);
		}

		public void AuthenticationSuccess(Object sender, Object remote, Object result) {
			logger.debug("Successfully connected to " + remote);
			raiseAuthenticationSuccessEvent(remote, result);
		}
	}
	
	///////////////////////////////////////
	// test code
//#if cfg.includeTestCode
	public static void main(String[] args) throws IOException, InterruptedException {
		BluetoothOpportunisticConnector c = BluetoothOpportunisticConnector.getInstance();
		c.start();
		System.in.read();
		c.stop();
	}
//#endif
}
