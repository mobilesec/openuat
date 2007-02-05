/* Copyright Rene Mayrhofer
 * File created 2007-01-25
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.util;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Vector;

import javax.bluetooth.BluetoothStateException;
import javax.bluetooth.DeviceClass;
import javax.bluetooth.DiscoveryAgent;
import javax.bluetooth.DiscoveryListener;
import javax.bluetooth.LocalDevice;
import javax.bluetooth.RemoteDevice;
import javax.bluetooth.ServiceRecord;
import javax.bluetooth.UUID;

import org.apache.log4j.Logger;

/** This class implements a Bluetooth peer device manager that handles 
 * automatic background discovery.
 */ 
public class BluetoothPeerManager {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(/*BluetoothPeerManager.class*/ "org.openuat.util.BluetoothPeerManager");
	
	/** The agent used for actual discovery, initialized in the constructor. */
	private DiscoveryAgent agent;
	
	// TODO: use an associative Map instead so that we can easily search for devices of a specific class
	private Vector foundDevices = new Vector();
	
	/** Stores the already discovered services. The key is the RemoteDevice,
	 * value a Vector of ServiceRecord elements.
	 */ 
	private Hashtable foundServices = new Hashtable();
	
	private Vector listeners = new Vector();
	
	public BluetoothPeerManager() throws IOException {
		if (! BluetoothSupport.init()) {
			throw new IOException("Local Bluetooth stack was not initialized properly, can not construct channel objects");
		}
		agent = LocalDevice.getLocalDevice().getDiscoveryAgent();
	}
	
	// TODO: use the arguments...
	public boolean startInquiry(boolean continuousBackground, boolean automaticServiceDiscovery) {
			try {
				agent.startInquiry(DiscoveryAgent.GIAC, new DiscoveryEventsHandler(null));
				return true;
			} catch (BluetoothStateException e) {
				logger.error("Could not initiate inquiry: " + e);
				e.printStackTrace();
				return false;
			}
	}
	
	// TODO: this interface needs to be changed
	public boolean startServiceSearch(int[] attributes, UUID[] uuids, RemoteDevice device) {
		try {
			agent.searchServices(attributes, uuids, device, new DiscoveryEventsHandler(device));
			return true;
		} catch (BluetoothStateException e) {
			logger.error("Could not initiate inquiry: " + e);
			e.printStackTrace();
			return false;
		} catch (IllegalArgumentException e) {
			logger.error("Could not initiate inquiry: " + e);
			e.printStackTrace();
			return false;
		}
	}
	
	// TODO: optionally restrict to device classes
	public RemoteDevice[] getPeers() {
		synchronized(foundDevices) {
			RemoteDevice[] ret = new RemoteDevice[foundDevices.size()];
			for (int i=0; i<foundDevices.size(); i++)
				ret[i] = (RemoteDevice) foundDevices.elementAt(i);
			return ret;
		}
	}
	
	/** @return true if the listener was added, false if it was already found in the list. */
	public boolean addListener(PeerEventsListener listener) {
		if (listener == null)
			return false;
		
		if (! listeners.contains(listener)) {
			listeners.addElement(listener);
			return true;
		}
		return false;
	}
	
	public boolean removeListener(PeerEventsListener listener) {
		return listeners.remove(listener);
	}
	
	public interface PeerEventsListener {
		// listeners should then query...
		public void newDevicesFound();
		
		public void serviceListFound(RemoteDevice remoteDevice, Vector services);
	}

	
	private class DiscoveryEventsHandler implements DiscoveryListener {
		// TODO: do we really need to remember this? what about transaction IDs? 
		private RemoteDevice currentRemoteDevice = null;
		
		/** @param forDevice	Set to null for device discovery and to the 
		 *                      remote device object for service discovery.
		 */
		public DiscoveryEventsHandler(RemoteDevice forDevice) {
			currentRemoteDevice = forDevice;
		}

		public void deviceDiscovered(RemoteDevice remoteDevice, DeviceClass deviceClass) {
			try {
				logger.info("Found remote device with MAC " + remoteDevice.getBluetoothAddress() +
						" named '" + remoteDevice.getFriendlyName(true) + "' of class " + 
						deviceClass.getMajorDeviceClass() + "." + deviceClass.getMinorDeviceClass());
			} catch (IOException e) {
				// just ignore
			}
			synchronized(foundDevices) {
				foundDevices.addElement(remoteDevice);
			}
		}

		public void inquiryCompleted(int param) {
			switch (param) {
			case DiscoveryListener.INQUIRY_COMPLETED: //Inquiry completed normally
				
				for (int i=0; i<listeners.size(); i++)
					((PeerEventsListener) listeners.elementAt(i)).newDevicesFound();
				break;
			case DiscoveryListener.INQUIRY_ERROR: // Error during inquiry
				logger.error("Inqury error");
				break;
			case DiscoveryListener.INQUIRY_TERMINATED: // Inquiry terminated by agent.cancelInquiry()
				logger.error("Inqury Canceled");
				break;
			}
		}

		public void servicesDiscovered(int transID, ServiceRecord[] serviceRecord) {
			logger.info("Discovered " + serviceRecord.length + " services for remote device " +
					currentRemoteDevice + " with transaction id " + transID);
			
			Vector services = getServicesList();
			if (services == null)
				return;
			
			synchronized(foundServices) {
				for (int x = 0; x < serviceRecord.length; x++)
					services.addElement(serviceRecord[x]);
			}
		}

		public void serviceSearchCompleted(int transID, int respCode) {
			switch (respCode) {
			case DiscoveryListener.SERVICE_SEARCH_COMPLETED:
				Vector services = getServicesList();
				if (services == null)
					return;

				for (int i=0; i<listeners.size(); i++)
					((PeerEventsListener) listeners.elementAt(i)).serviceListFound(currentRemoteDevice, services);
				
				break;
			case DiscoveryListener.SERVICE_SEARCH_DEVICE_NOT_REACHABLE:
				logger.error("Device not Reachable");
				break;
			case DiscoveryListener.SERVICE_SEARCH_ERROR:
				logger.error("Service serch error");
				break;
			case DiscoveryListener.SERVICE_SEARCH_NO_RECORDS:
				logger.error("No records returned");
				break;
			case DiscoveryListener.SERVICE_SEARCH_TERMINATED:
				logger.error("Inqury Cancled");
				break;
			}
		}
		
		private Vector getServicesList() {
			if (currentRemoteDevice == null) {
				logger.error("Remote device not set, but discovered services. This should not happen, ignoring services!");
				return null;
			}
			Vector services = null;; 
			synchronized(foundServices) {
				if (foundServices.containsKey(currentRemoteDevice))
					services = (Vector) foundServices.get(currentRemoteDevice); 
				else {
					services = new Vector();
					foundServices.put(currentRemoteDevice, services);
				}
			}
			return services;
		}
	}
	
	/*
 LocalDevice localdevice = LocalDevice.getLocalDevice(); 
DiscoveryAgent discoveryagent = localdevice.getDiscoveryAgent();
 */

	/*
	 * String connectionURL = servRecord[i].getConnectionURL(0, false);
	 */
	
	/*public void startApp() {
		 startServer();
		 }
		 private void startServer() { 
		 if (mServer !=null)
		 return;
		 //start the server and receiver
		 mServer = new Thread(this);
		 mServer.start(); 
		 }
		 
		 
		 Procedure run() {
		 if (deviceVector == null) deviceVector = new Vector();
		 if (agent == null) agent = local.getDiscoveryAgent();
		 
		 /* Retrieve PREKNOWN devices and add them to our Vector */
		 
/*		 RemoteDevice[] devices = agent.retrieveDevices(DiscoveryAgent.PREKNOWN);*/
		 /* 
		 * Synchronize on vector to obtain object lock before loop.
		 * Else, object lock will be obtained every iteration.
		 */
/*		 synchronized(deviceVector) {
		 
		 for (int i = devices.length-1;i >=0;i--) {
		 deviceVector.addElement(devices[i]);
		 
		 try {
		 name = devices[i].getFriendlyName(false);
		 
		 }catch (IOException ioe) {
		 name = devices[i].getBluetoothAddress();
		 }
		 if (name.equals("")) name = devices[i].getBluetoothAddress();
		 knownDevices.insert(0,name,null);
		 }
		 } //End synchronized
		 }
		 }*/
	
	/*
	 * repeating the in the above code each time will obviously extend the time
	 *  devices = agent.retrieveDevices(DiscoveryAgent.PREKNOWN);
 devices = agent.retrieveDevices(DiscoveryAgent.GIAC);
 devices = agent.retrieveDevices(DiscoveryAgent.CACHED); 
	 */
	
	/* In the case of a Serial Port service record, this string might look like "btspp://0050CD00321B:3;authenticate=true;encrypt=false;master=true", where "0050CD00321B" is the Bluetooth address of the device that provided this ServiceRecord, "3" is the RFCOMM server channel mentioned in this ServiceRecord, and there are three optional parameters related to security and master/slave roles.  */
}
