/* Copyright Rene Mayrhofer
 * File created 2007-01-25
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.channel.main.bluetooth.jsr82;

import java.io.IOException;

import javax.bluetooth.BluetoothStateException;
import javax.bluetooth.DataElement;
import javax.bluetooth.DiscoveryAgent;
import javax.bluetooth.LocalDevice;
import javax.bluetooth.ServiceRecord;
import javax.bluetooth.UUID;
import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.StreamConnectionNotifier;

import org.apache.log4j.Logger;
import org.openuat.authentication.exceptions.InternalApplicationException;
import org.openuat.channel.main.HostServerBase;
import org.openuat.channel.main.bluetooth.BluetoothSupport;

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
	
	private String registeredURL;
	
    // We use a pseudo-singleton pattern here: for each port, only one instance can exist. This map holds the known instances.
	//private static HashMap instances;

	/** Initializes the listener by creating the RFCOMM service.
	 * @param channel The RFCOMM channel to use, or unspecified (dynamically
	 *                use a free channel) when null.
	 * @param serviceUUID The Bluetooth service UUID to register.
	 * @param serviceName The name to announce via SDP.
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
	public BluetoothRFCOMMServer(Integer channel, UUID serviceUUID, String serviceName, 
			int protocolTimeoutMs, boolean keepConnected, boolean useJSSE) throws IOException {
		super(keepConnected, useJSSE, protocolTimeoutMs);

		if (! BluetoothSupport.init()) {
			throw new IOException("Local Bluetooth stack was not initialized properly, can not construct channel objects");
		}

		// construct the Bluetooth service URL
		serviceURL = "btspp://localhost:" + serviceUUID.toString() + 
			(channel != null ? ":" + channel.intValue() : "") + ";name=" + serviceName + 
			";authenticate=false;encrypt=false;authorize=false;master=false";
		// the service itself will be created later when calling startListening
	}

	/** Need to override the startListening method to register the SDP service. 
	 * @throws InternalApplicationException */
	//@Override
	//@SuppressWarnings("static-access") // we really want the javax...Connector, and not the avetanebt!
	public void start() throws IOException {
		if (listener == null) {
			// create the RFCOMM service
			try {
				this.listener = (StreamConnectionNotifier) Connector.open(serviceURL);
			} catch (IOException e) {
				logger.error("Unable to register SDP service with URL '" + serviceURL + "', aborting startListening: " + e);
				throw e;
			}
			try {
				// make sure we are discoverable
				LocalDevice local = LocalDevice.getLocalDevice();
	            if (!local.setDiscoverable(DiscoveryAgent.GIAC)) {
	            	logger.warn("Failed to change to discoverable mode");
	            }
				
				// we can query and modify our local service description
				ServiceRecord service;
				service = local.getRecord(listener);
				DataElement e = new DataElement(DataElement.DATSEQ);
				e.addElement(new DataElement(DataElement.UUID,new UUID(0x1002)));// PublicBrowseRoot value (0x1002)
				service.setAttributeValue(0x0005,e);// BrowseGroupList (attribute ID 0x1005) 
				local.updateRecord(service);
				registeredURL = service.getConnectionURL(ServiceRecord.NOAUTHENTICATE_NOENCRYPT, false);
				logger.info("Registered local service with URL " + registeredURL);
			} catch (BluetoothStateException e) {
				logger.error("Unable to query registered SDP record, aborting startListening: " + e);
				throw e;
			}
		}
		super.start();
	}

	/** Need to override the stopListening method to properly close the RFCOMM service notifier. 
	 * @throws InternalApplicationException */
	//@Override
	public void stop() throws InternalApplicationException {
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
		super.stop();
	}
	
	/** After startListening finished successfully, this will return the URL 
	 * that can be used by RFCOMM clients to connect to this service.
	 */
	public String getRegisteredServiceURL() {
		return registeredURL;
	}
	
	/** This actually implements the listening for new RFCOMM channels. */
	public void run() {
		logger.debug("Listening thread for RFCOMM service now running");
		try {
			while (running) {
				//System.out.println("Listening thread for server socket waiting for connection");
				StreamConnection connection = listener.acceptAndOpen();
				BluetoothRFCOMMChannel channel = new BluetoothRFCOMMChannel(connection);
				if (logger.isInfoEnabled())
					logger.info("Accepted incoming connection from " + channel.getRemoteAddress() + "/'" + 
							channel.getRemoteName() + "'");
				
				
				//do mambo jumbo
				
//				InputStream in = channel.getInputStream();
//				OutputStream out = channel.getOutputStream();
//				try{
////				Thread.sleep(10000);
//				
//					String line ;
//					line = LineReaderWriter.readLine(in, 2000);
//					logger.info("read: "+line);
//					Thread.sleep(2000);
//					LineReaderWriter.println(out, "MSG2");
//					
//					line = LineReaderWriter.readLine(in,4000);
//					logger.info("read: "+line);
//					Thread.sleep(4000);
//					LineReaderWriter.println(out, "MSG4");
//					
//					line = LineReaderWriter.readLine(in, 6000);
//					logger.info("read: "+line);
//					Thread.sleep(6000);
//					LineReaderWriter.println(out, "MSG6");
//					
//					line = LineReaderWriter.readLine(in, 8000);
//					logger.info("read: "+line);
//					Thread.sleep(8000);
//					LineReaderWriter.println(out, "MSG8");
//					
//					
//					line = LineReaderWriter.readLine(in, 10000);
//					logger.info("read: "+line);
//					Thread.sleep(10000);
//					LineReaderWriter.println(out, "MSG10");
//					
//					line = LineReaderWriter.readLine(in, 12000);
//					logger.info("read: "+line);
//					Thread.sleep(12000);
//					LineReaderWriter.println(out, "MSG12");
//					
//					line = LineReaderWriter.readLine(in, 14000);
//					logger.info("read: "+line);
//					Thread.sleep(14000);
//					LineReaderWriter.println(out, "MSG14");
//					
//				}catch(Exception e){
//					logger.error(e);
//				}
				//runBEDA input
				
				
				startProtocol(channel);
    			
    			/* It turns out that we need to add a sleep before starting the 
    			 * next acceptAndOpen after finishing the previous connection, or
    			 * it will simply stop accepting new connections after some time. 
    			 * Maybe also an avetana weirdness.
    			 */ 
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					// ignore this silently
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
			stop();
		} catch (InternalApplicationException e) {
			logger.error("Could not properly dispose object: unable to close listener: " + e);
		}
	}
	
//#if cfg.includeTestCode
	////////////////////// test code begins here ////////////////
	public static void main(String[] args) throws NullPointerException, IllegalArgumentException, IOException, InternalApplicationException {
		BluetoothRFCOMMServer s = new BluetoothRFCOMMServer(null, new UUID("1089a94a47044480adc9576fd41a04b2", false), "Test Service", 
				10000, false, false);
		// for the test, make sure to be discoverable
		LocalDevice.getLocalDevice().setDiscoverable(DiscoveryAgent.GIAC);
		s.start();
		System.out.println("Service now listening, press any key to exit");
		System.in.read();
		s.stop();
		System.out.println("Application exiting now");
		System.exit(0);
	}
//#endif
}
