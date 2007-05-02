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
	public static UUID serviceUUID = new UUID("c9cb7b45-91ee-46e3-92f5-0933394f613e", false);
	
	/** The Bluetooth service will be advertised under this friendly name. */
	public static String serviceName = "OpenUAT Opportunistic Authentication";
	
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
	
	protected BluetoothOpportunisticConnector() throws IOException {
		logger.debug("Creating BluetoothOpportunisticConnector instance, initializing BluetoothPeerManager");
		manager = new BluetoothPeerManager();
		manager.setAutomaticServiceDiscovery(true);
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
	
	/** Returns the time to sleep in between two inquiries. 
	 * @see BluetoothPeerManager#sleepBetweenInquiries
	 * @return The sleep time in ms.
	 */
	public int getSleepBetweenInquiriesTime() {
		return manager.getSleepBetweenInquiriesTime();
	}

	/** Sets the time to sleep in between two inquiries. 
	 * Note that the actual sleep time 
	 * will be up to 20% randomly off this time to prevent continuous 
	 * collisions between two devices doing the same. This may be changed even 
	 * while a backgound inquiry is running. 
	 * @see BluetoothPeerManager#sleepBetweenInquiries
	 * @param milliseconds The sleep time in ms.
	 */
	public void setSleepBetweenInquiriesTime(int milliseconds) {
		manager.setSleepBetweenInquiriesTime(milliseconds);
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
					BluetoothRFCOMMChannel channel;
					try {
						channel = new BluetoothRFCOMMChannel(
								sr.getConnectionURL(ServiceRecord.NOAUTHENTICATE_NOENCRYPT, false));
						HostProtocolHandler.startAuthenticationWith(channel, 
								new AuthenticationEventsHandler(false), keepConnected, optionalParameter, useJSSE);
						logger.info("Started authentication attempt with remote device " + 
								remoteDevice.getBluetoothAddress() + "/'" + 
								BluetoothPeerManager.resolveName(remoteDevice) + "'");
					} catch (IOException e) {
						logger.warn("Could not connect to remote service, will retry in " +
								"XXXXX" + " ms");
						// TODO: schedule for retry
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
			raiseAuthenticationFailureEvent(remote, e, msg);
		}

		public void AuthenticationProgress(Object sender, Object remote, int cur, int max, String msg) {
			raiseAuthenticationProgressEvent(remote, cur, max, msg);
		}

		public void AuthenticationSuccess(Object sender, Object remote, Object result) {
			raiseAuthenticationSuccessEvent(remote, result);
		}
	}
}
