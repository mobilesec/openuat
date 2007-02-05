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
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import javax.bluetooth.BluetoothStateException;
import javax.bluetooth.DataElement;
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
	/** The default sleep time between two inquiry runs in milliseconds. */
	public final static int DEFAULT_SLEEP_TIME = 20000;
	
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(/*BluetoothPeerManager.class*/ "org.openuat.util.BluetoothPeerManager");
	
	/** The agent used for actual discovery, initialized in the constructor. */
	private DiscoveryAgent agent;
	
	/** Stores the already discovered services and, implicitly, devices. The key is the RemoteDevice,
	 * value a RemoteDeviceDetail object.
	 */ 
	private Hashtable foundDevices = new Hashtable();
	
	/** The list of registered listeners. */
	private Vector listeners = new Vector();
	
	/** The sleep time between two inquiry runs in milliseconds. */
	private int sleepBetweenInquiries = DEFAULT_SLEEP_TIME;
	
	/** The events handler used for the main inquiry. */
	private DiscoveryEventsHandler eventsHandler = new DiscoveryEventsHandler(null);
	
	/** A reference to the background inquiry thread when it is running, or null if not running. */
	private Thread inquiryThread = null;
	
	public BluetoothPeerManager() throws IOException {
		if (! BluetoothSupport.init()) {
			throw new IOException("Local Bluetooth stack was not initialized properly, can not construct channel objects");
		}
		agent = LocalDevice.getLocalDevice().getDiscoveryAgent();
	}
	
	/** Returns the time to sleep in between two retries when startInquiry is 
	 * called with continuousBackground=true.
	 * @return The sleep time in ms.
	 */
	public int getSleepBetweenInquiriesTime() {
		return sleepBetweenInquiries;
	}
	
	/** Sets the time to sleep in between two retries when startInquiry is 
	 * called with continuousBackground=true.
	 * @param milliseconds The sleep time in ms.
	 */
	public void setSleepBetweenInquiriesTime(int milliseconds) {
		this.sleepBetweenInquiries = milliseconds;
	}
	
	// TODO: use the arguments...
	public boolean startInquiry(boolean continuousBackground, boolean automaticServiceDiscovery) {
			if (! continuousBackground) {
				// OK, one-shot inquiry
				return startInquiry();
			}
			else {
				if (inquiryThread != null) {
					logger.warn("Background inquiry already running, can not start another instance.");
					return false;
				}
					
				inquiryThread = new Thread(new Runnable() { public void run() {
					while (inquiryThread != null) {
						if (!startInquiry())
							return;
						try {
							// before starting the next inquire run, wait for this one to finish
							synchronized (eventsHandler) {
								while (eventsHandler.isInquiryRunning())
									eventsHandler.wait();
							}
							// and sleep
							Thread.sleep(sleepBetweenInquiries);
						} catch (InterruptedException e) { }
					}
				}});
				inquiryThread.start();
				return true;
			}
	}
	
	public boolean stopInquiry() {
		if (inquiryThread != null) {
			Thread tmp = inquiryThread;
			inquiryThread = null;
			tmp.interrupt();
			try {
				tmp.join();
			} catch (InterruptedException e) { }
			return true;
		}
		else
			return false;
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
			int i = 0;
			for (Enumeration devices = foundDevices.keys(); devices.hasMoreElements(); )
				ret[i++] = (RemoteDevice) devices.nextElement();
			return ret;
		}
	}
	
	/** Add a new event listener. 
	 * @return true if the listener was added, false if it was already found in the list. */
	public boolean addListener(PeerEventsListener listener) {
		if (listener == null)
			return false;
		
		if (! listeners.contains(listener)) {
			listeners.addElement(listener);
			return true;
		}
		return false;
	}
	
	/** Remove a registered event listener. 
	 * @return true if the listener was removed, false if it was not found in the list. */
	public boolean removeListener(PeerEventsListener listener) {
		return listeners.remove(listener);
	}
	
	/** Users of BluetoothPeerManager should most probably implement this
	 * interface and register themselves with addListener. They will then
	 * receive events about discovered devices and services.
	 * @see BluetoothPeerManager#addListener
	 */
	public interface PeerEventsListener {
		/** This method is called when the inquiry has been completed (either
		 * started as a one-shot process or periodically by the background
		 * inquiry. Implementations may then e.g. use 
		 * @see BluetoothPeerManager.getPeers to get a list of devices 
		 * discovered so far.
		 * @param newDevices This vector contains the list of new devices that 
		 *                   have been discovered since the last inquiry 
		 *                   completed. If no new devices have been found 
		 *                   during this inquiry, the vector is empty. Elements
		 *                   of the vector are of type RemoteDevice.
		 */
		public void inquiryCompleted(Vector newDevices);
		
		/** This method is called when the search for the services of a
		 * specific remote device has completed.
		 * @param remoteDevice The remote device for which new services have
		 *                     been found.
		 * @param services The list of services of the remote device. Elements
		 *                 of the vector are of type ServiceRecord.
		 */
		public void serviceListFound(RemoteDevice remoteDevice, Vector services);
	}

	/** This is only a small structure for keeping together the things we
	 * remember about a discovered remote device. 
	 */
	private class RemoteDeviceDetail {
		public boolean newlyDiscovered = true;
		public long lastSeen = System.currentTimeMillis();
		public Vector services = new Vector();
	}
	
	private class DiscoveryEventsHandler implements DiscoveryListener {
		/** true if inquiry has finished, false otherwise. */
		private boolean isFinished = true;

		/** The device we are searching services on, or null if we are searching for devices. */
		private RemoteDevice currentRemoteDevice = null;
		
		/** @param forDevice	Set to null for device discovery and to the 
		 *                      remote device object for service discovery.
		 */
		public DiscoveryEventsHandler(RemoteDevice forDevice) {
			currentRemoteDevice = forDevice;
		}
		
		/** Resets the object to its "active" state, meaning that inquiry is currently running. */
		public synchronized void reset() {
			isFinished = false;
		}
		
		/** @return true is inquiry is still running, false if it has finished. */
		public boolean isInquiryRunning() {
			return !isFinished;
		}

		public void deviceDiscovered(RemoteDevice remoteDevice, DeviceClass deviceClass) {
			if (currentRemoteDevice != null) {
				logger.error("Remote device set, but discovered devices. This should not happen, ignoring devices!");
				return;
			}

			try {
				logger.info("Found remote device with MAC " + remoteDevice.getBluetoothAddress() +
						" named '" + remoteDevice.getFriendlyName(true) + "' of class " + 
						deviceClass.getMajorDeviceClass() + "." + deviceClass.getMinorDeviceClass());
			} catch (IOException e) {
				// just ignore
			}
			synchronized(foundDevices) {
				// remember the device by adding an empty service list with its key (if not already found before)
				if (!foundDevices.containsKey(remoteDevice)) {
					foundDevices.put(remoteDevice, new RemoteDeviceDetail());
					// an event containing this newly found device will be sent when discovery is complete
				}
			}
		}

		public void inquiryCompleted(int param) {
			if (currentRemoteDevice != null) {
				logger.error("Remote device set, but discovered devices. This should not happen, ignoring devices!");
				return;
			}

			switch (param) {
			case DiscoveryListener.INQUIRY_COMPLETED: //Inquiry completed normally
				// find out which of the devices are new
				Vector newDevices = new Vector();
				synchronized (foundDevices) {
					for (Enumeration devices = foundDevices.keys(); devices.hasMoreElements(); ) {
						RemoteDevice device = (RemoteDevice) devices.nextElement();
						RemoteDeviceDetail entry = (RemoteDeviceDetail) foundDevices.get(device);
						if (entry.newlyDiscovered) {
							entry.newlyDiscovered = false;
							newDevices.addElement(device);
						}
					}
				}
				
				for (int i=0; i<listeners.size(); i++)
					((PeerEventsListener) listeners.elementAt(i)).inquiryCompleted(newDevices);
				break;
			case DiscoveryListener.INQUIRY_ERROR: // Error during inquiry
				logger.error("Inqury error");
				break;
			case DiscoveryListener.INQUIRY_TERMINATED: // Inquiry terminated by agent.cancelInquiry()
				logger.error("Inqury Canceled");
				break;
			}
			synchronized (this) {
				// in any instance, inquiry is no longer active
				isFinished = true;
				// signal the waiting background thread (if there is one)
				this.notify();
			}
		}


		public void servicesDiscovered(int transID, ServiceRecord[] serviceRecord) {
			logger.info("Discovered " + serviceRecord.length + " services for remote device " +
					currentRemoteDevice + " with transaction id " + transID);
			if (currentRemoteDevice == null) {
				logger.error("Remote device not set, but discovered services. This should not happen, ignoring services!");
				return;
			}
			
			synchronized(foundDevices) {
				if (! foundDevices.containsKey(currentRemoteDevice)) {
					logger.error("Internal error: Remote device set and discovered services, but no device entry. This should not happen, ignoring services!");
					return;
				}
				Vector services = ((RemoteDeviceDetail) foundDevices.get(currentRemoteDevice)).services; 
					
				for (int x = 0; x < serviceRecord.length; x++)
					services.addElement(serviceRecord[x]);
			}
		}

		public void serviceSearchCompleted(int transID, int respCode) {
			if (currentRemoteDevice == null) {
				logger.error("Remote device not set, but discovered services. This should not happen, ignoring services!");
				return;
			}

			switch (respCode) {
			case DiscoveryListener.SERVICE_SEARCH_COMPLETED:
				Vector services;
				synchronized(foundDevices) {
					if (! foundDevices.containsKey(currentRemoteDevice)) {
						logger.error("Internal error: Remote device set and discovered services, but no device entry. This should not happen, ignoring services!");
						return;
					}
					services = ((RemoteDeviceDetail) foundDevices.get(currentRemoteDevice)).services; 
				}

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
	}
	
	/** This is only a small helper function for actually starting an inquiry. 
	 * It is idempotent and takes care of resetting the eventsHandler object.
	 * @return true if it was started successfully, false otherwise (either 
	 *         because another inquiry is already running or because of a
	 *         Bluetooth exception.
	 */
	private boolean startInquiry() {
		try {
			if (eventsHandler.isInquiryRunning()) {
				logger.error("Inquiry is already running, can not start a second run in parallel");
				return false;
			}
			eventsHandler.reset();
			agent.startInquiry(DiscoveryAgent.GIAC, eventsHandler);
			return true;
		} catch (BluetoothStateException e) {
			logger.error("Could not initiate inquiry: " + e);
			e.printStackTrace();
			return false;
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
	
	////////////// testing code //////////////////
	private static class TempEventsHandler implements PeerEventsListener {
		BluetoothPeerManager man;
		
		public void inquiryCompleted(Vector newDevices) {
			System.out.println("Inquiry completed, new devices: ");
			for (int i=0; i<newDevices.size(); i++)
				try {
					System.out.println("    " + ((RemoteDevice) newDevices.elementAt(i)).getFriendlyName(false));
				} catch (IOException e) {
					e.printStackTrace();
				}
			
			System.out.println("List of all devices discovered so far: ");
			RemoteDevice[] allDevices = man.getPeers();
			for (int i=0; i<allDevices.length; i++) {
				try {
					System.out.println("    " + allDevices[i].getFriendlyName(false));
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		public void serviceListFound(RemoteDevice remoteDevice, Vector services) {
			for (int x = 0; x < services.size(); x++) {
				try {
					DataElement ser_de = ((ServiceRecord) services.elementAt(x)).getAttributeValue(0x100);
					String name = (String) ser_de.getValue();
					System.out.println("Found service for devices " + remoteDevice.getFriendlyName(false) + ": " + name);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	public static void main (String[] args) throws IOException {
		TempEventsHandler l = new TempEventsHandler();
		BluetoothPeerManager m = new BluetoothPeerManager();
		l.man = m;
		m.addListener(l);
		System.out.println("Starting inquiry");
		m.startInquiry(true, true);
		System.out.println("Press any key to stop background inquiry");
		System.in.read();
		m.stopInquiry();
	}
}
