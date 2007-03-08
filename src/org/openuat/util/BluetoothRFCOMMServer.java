/* Copyright Rene Mayrhofer
 * File created 2007-01-25
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.util;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ListIterator;

import javax.bluetooth.DiscoveryAgent;
import javax.bluetooth.LocalDevice;
import javax.bluetooth.ServiceRecord;
import javax.bluetooth.UUID;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.StreamConnectionNotifier;

import org.apache.log4j.Logger;
import org.openuat.authentication.AuthenticationProgressHandler;
import org.openuat.authentication.HostProtocolHandler;
import org.openuat.authentication.exceptions.InternalApplicationException;

import de.avetana.bluetooth.connection.Connector;

/** This class represents an RFCOMM service which responds to incoming authentication requests by delegating any incoming
 * connection to the HostProtocolHandler class. More specifically, for each incoming RFCOMM connection, the 
 * HostProtocolHandler.startIncomingAuthenticationThread is invoked with the connected RFCOMM stream connection.
 * 
 * Listening is done in a background thread using blocking accept() calls. After constructing a BluetoothRFCOMMServer object for
 * a specific channel, startListening() needs to be called to start accepting incoming connection. Authentication and encryption
 * as well as authorization on Bluetooth level are deactivated.
 *  
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class BluetoothRFCOMMServer extends HostServerBase {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger("org.openuat.util.BluetoothRFCOMMServer" /*BluetoothRFCOMMServer.class*/);
	
	/** This notifier is used to accept new RFCOMM connection. */
	private StreamConnectionNotifier listener = null;
	
	private String serviceURL;
	
    // We use a pseudo-singleton pattern here: for each port, only one instance can exist. This map holds the known instances.
	//private static HashMap instances;

	/** Initializes the listener by creating the RFCOMM service.
	 * @param channel The RFCOMM channel to use.
	 * @param serviceUUID The Bluetooth service UUID to register.
	 * @param serviceName The name to announce via SDP.
	 * @param useJSSE If set to true, the JSSE API with the default JCE provider of the JVM will be used
	 *                for cryptographic operations. If set to false, an internal copy of the Bouncycastle
	 *                Lightweight API classes will be used.
	 * @see #keepConnected If set to true, the connection to the client will be kept connected after the 
	 *                           registered HostProtocolHandler has finished. This allows the socket to be
	 *                           reused for additional communication after the first authentication
	 *                           protocol has been completed.
	 */
	public BluetoothRFCOMMServer(/*int channel, */UUID serviceUUID, String serviceName, boolean keepConnected, boolean useJSSE) throws IOException {
		super(keepConnected, useJSSE);

		if (! BluetoothSupport.init()) {
			throw new IOException("Local Bluetooth stack was not initialized properly, can not construct channel objects");
		}

		// construct the Bluetooth service URL
		serviceURL = "btspp://localhost:" + serviceUUID /*+ ":" + channel*/ + ";name=" + 
			serviceName + ";authenticate=false;encrypt=false;authorize=false;master=false";

		// and create the RFCOMM service
		this.listener = (StreamConnectionNotifier) Connector.open(serviceURL);
		
		// we can query and modify our local service description
		ServiceRecord service = LocalDevice.getLocalDevice().getRecord(listener);
		logger.info("Registered local service with URL " + service.getConnectionURL(ServiceRecord.NOAUTHENTICATE_NOENCRYPT, false));
	}
	
	/** Need to override the stopListening method to properly close the RFCOMM service notifier. 
	 * @throws InternalApplicationException */
	public void stopListening() throws InternalApplicationException {
		if (listener != null) {
			try {
				// this causes the service record to be removed from the SDDB 
				listener.close();
				listener = null;
			} catch (IOException e) {
				throw new InternalApplicationException(
						"Could not close listening socket cleanly as a signal to the listener thread. This should not happen.",
						e);
			}
		}
		super.stopListening();
	}
	
	/** This actually implements the listening for new RFCOMM channels. */
	public void run() {
		logger.debug("Listening thread for RFCOMM service now running");
		try {
			while (running) {
				System.out.println("111111111111111111");
				//System.out.println("Listening thread for server socket waiting for connection");
				StreamConnection connection = listener.acceptAndOpen();
				System.out.println("2222222222222222222");
				BluetoothRFCOMMChannel channel = new BluetoothRFCOMMChannel(connection);
				logger.info("Accepted incoming connection from " + channel.getRemoteAddress() + "/'" + 
						channel.getRemoteName() + "'");
				
/*				HostProtocolHandler h = new HostProtocolHandler(channel, keepConnected, useJSSE);
				// before starting the background thread, register all our own listeners with this new event sender
    			for (ListIterator i = eventsHandlers.listIterator(); i.hasNext(); )
    				h.addAuthenticationProgressHandler((AuthenticationProgressHandler) i.next());
    			h.startIncomingAuthenticationThread();*/
				
				DataInputStream in = connection.openDataInputStream();
				try {
					in.read();
					// when we get to here, i.e. when the next exception does _not_ catch, then most probably the next acceptAndConnect will no longer work. this sucks
				} catch (IOException e) {//ignore: connection closed by other side
					System.out.println("3333333333333333333333333");
					e.printStackTrace();
				}
				try {
					in.close();
				} catch (IOException e) {//ignore
					System.out.println("5555555555555555555555555555");
					e.printStackTrace();
				}
				try {
					connection.close();
				} catch (IOException e) {//ignore
					System.out.println("444444444444444444444444444");
					e.printStackTrace();
				}
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					// TODO Automatisch erstellter Catch-Block
					e.printStackTrace();
				}
			}
		} catch (IOException e) {
			logger.warn("Error in listening thread: " + e + ". Stopping thread");
		}
		logger.debug("Listening thread for RFCOMM service now exiting");
		running = false;
	}
	
	/** Override the dispose method to make sure that we remove the service record from the local SDB. */
	public void dispose() {
		try {
			stopListening();
		} catch (InternalApplicationException e) {
			logger.error("Could not properly dispose object: unable to close listener: " + e);
		}
	}
	
	////////////////////// test code begins here ////////////////
	public static void main(String[] args) throws NullPointerException, IllegalArgumentException, IOException, InternalApplicationException {
		BluetoothRFCOMMServer s = new BluetoothRFCOMMServer(new UUID("1089a94a47044480adc9576fd41a04b2", false), "Test Service", false, false);
		// for the test, make sure to be discoverable
		LocalDevice.getLocalDevice().setDiscoverable(DiscoveryAgent.GIAC);
		s.startListening();
		System.out.println("Service now listening, press any key to exit");
		System.in.read();
		s.stopListening();
		System.out.println("Application exiting now");
		System.exit(0);
	}
}
