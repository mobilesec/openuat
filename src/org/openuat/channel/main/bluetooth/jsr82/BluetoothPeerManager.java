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
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Random;
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
import org.openuat.channel.main.bluetooth.BluetoothSupport;
import org.openuat.util.LoggingHelper;

/** This class implements a Bluetooth peer device manager that handles 
 * automatic background discovery. Users of this class should implement the
 * BluetoothPeerManager.DiscoveryEventsHandler interface and register itself
 * with addListener/removeListener to react to events.
 * 
 * Parameters for automatic background discovery can be set with
 * setSleepBetweenInquiriesTime, setAutomaticServiceDiscovery, and 
 * setAutomaticServiceDiscoveryUUID. The whole inquiry process is started by
 * calling startInquiry, which can either be a one-shot operation or be started
 * as a continuous background thread (which can be stopped with stopInquiry). 
 * Services of discovered devices can be found by either calling 
 * startServiceSearch directly or setting automaticServiceDiscovery to true.
 * 
 * There are two ways of querying the discovered information: either explicitly
 * via getPeers and getServices, or by using the arguments passed to events.
 */ 
public class BluetoothPeerManager {
	/** The default sleep time between two inquiry runs in milliseconds. */
	public final static int DEFAULT_SLEEP_TIME = 30000;
	
	/** The maximum sleep time between two inquiry runs in milliseconds.
	 * Automatic sleep time adaptation will not set it higher than this. 
	 */
	public final static int MAXIMUM_SLEEP_TIME = 120000;
	
	/** The minimum sleep time between two inquiry runs in milliseconds.
	 * Automatic sleep time adaptation will not set it lower than this. 
	 */
	public final static int MINIMUM_SLEEP_TIME = 20000;
	
	/** The maximum time we wait for a service search to finish in milliseconds.
	 * After this time, the service search request is canceled.
	 */
	public final static int TIMEOUT_SERVICE_SEARCH = 20000;
	
	/** Re-scan services every nth time that a device is discovered by the
	 * inquiry process.
	 */
	public final static int SCAN_SERVICES_FACTOR = 5;
	
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
	
	/** The sleep time between two inquiry runs in milliseconds. Note that the
	 * actual sleep time will be up to 20% randomly off this time to prevent
	 * continuous collisions between two devices doing the same. */
	private int sleepBetweenInquiries = DEFAULT_SLEEP_TIME;
	
	/** If this is set to true, then sleepBetweenInquiries will be adapted 
	 * automatically depending on the "volatility" of the Bluetooth inquiry
	 * results.
	 */
	private boolean adaptiveSleepTime = false;
	
	/** If set to true, then services will automatically be discovered for 
	 * newly found devices.
	 */
	private boolean automaticServiceDiscovery = false;
	
	/** Can be set to a specific service UUID to restrict the automatic service
	 * search.
	 */
	private UUID automaticServiceDiscoveryUUID = null;
	
	/** The events handler used for the main inquiry. */
	private DiscoveryEventsHandler eventsHandler = new DiscoveryEventsHandler(null);
	
	/** A reference to the background inquiry thread when it is running, or null if not running. */
	private Thread inquiryThread = null;
	
	/** Use for randomized sleeps. */
	private Random random = new Random();
	
	/** After discovering new devices (or for renewing their list of services),
	 * their Bluetooth addresses are added to this queue (at the end with 
	 * addElement) and removed by the inquiryThread to start service searches
	 * (from the beginning with remove(0)). Elements of the queue are of type
	 * RemoteDevice.
	 */
	private Vector serviceSearchQueue = new Vector();
	
	public BluetoothPeerManager() throws IOException {
		if (! BluetoothSupport.init()) {
			throw new IOException("Local Bluetooth stack was not initialized properly, can not construct channel objects");
		}
		agent = LocalDevice.getLocalDevice().getDiscoveryAgent();
	}
	
	/** Returns the time to sleep in between two inquiries when startInquiry is 
	 * called with continuousBackground=true.
	 * @see #sleepBetweenInquiries
	 * @return The sleep time in ms.
	 */
	public int getCurrentSleepBetweenInquiriesTime() {
		return sleepBetweenInquiries;
	}
	
	/** Sets the time to sleep in between two inquiries when startInquiry is 
	 * called with continuousBackground=true.  Note that the actual sleep time 
	 * will be up to 20% randomly off this time to prevent continuous 
	 * collisions between two devices doing the same. This may be changed even 
	 * while a backgound inquiry is running. 
	 * 
	 * When setAdaptiveSleepTime(true) is set, then this value will be changed
	 * automatically depending on the "volatility" of the Bluetooth inquiry
	 * results.
	 * 
	 * @see #sleepBetweenInquiries
	 * @see #getAdaptiveSleepTime
	 * @see #setAdaptiveSleepTime
	 * @param milliseconds The sleep time in ms.
	 */
	public void setSleepBetweenInquiriesTime(int milliseconds) {
		this.sleepBetweenInquiries = milliseconds;
	}
	
	/** Returns the value of adaptiveSleepTime. */
	public boolean getAdaptiveSleepTime() {
		return adaptiveSleepTime;
	}
	
	/** Sets the value of adaptiveSleepTime.
	 * 
	 * @param value true if the sleep time between two subsequent inquiry runs
	 *              should be adapted automatically depending on the "volatility"
	 *              of the Bluetooth inquiry results.
	 */
	public void setAdaptiveSleepTime(boolean value) {
		adaptiveSleepTime = value;
	}
	
	/** Returns the state of automatic service discovery.
	 * @see #automaticServiceDiscovery
	 * @return true if services are discovered automatically for newly found
	 *         devices.
	 */
	public boolean getAutomaticServiceDiscovery() {
		return automaticServiceDiscovery;
	}
	
	/** Sets the state of automatic service discovery. This may be changed even 
	 * while a background inquiry is running.
	 * @see #automaticServiceDiscovery
	 * @param automaticServiceDiscovery Set to true if services should be 
	 *                                  discovered automatically for newly found
	 *                                  devices.
	 */
	public void setAutomaticServiceDiscovery(boolean automaticServiceDiscovery) {
		this.automaticServiceDiscovery = automaticServiceDiscovery;
	}
	
	/** Returns the UUID used for automatically discovering only specific
	 * service UUID.
	 * @see #automaticServiceDiscoveryUUID
	 * @return The service UUID used for automatic service discovery.
	 */
	public UUID getAutomaticServiceDiscoveryUUID() {
		return automaticServiceDiscoveryUUID;
	}

	/** Sets the UUID used for automatically discovering only specific
	 * service UUID. This may be changed even while a backgound inquiry is 
	 * running.
	 * @see #automaticServiceDiscoveryUUID
	 * @param uuid The service UUID used for automatic service discovery. Set
	 *             to null to not restrict the discovery but search for all
	 *             available services.
	 */
	public void setAutomaticServiceDiscoveryUUID(UUID uuid) {
		automaticServiceDiscoveryUUID = uuid;
	}

	/** Starts a Bluetooth inquiry.
	 * @param continuousBackground If set to true, this start a continuous
	 *                             inquiry in the background, with the time
	 *                             set by setSleepBetweenInquiriesTime between
	 *                             two inquiries. If set to false, it just
	 *                             starts a one-shot inquiry.
	 * @return true if the inquiry could be started, false otherwise (either
	 *         when another inquiry is already active or due to a Bluetooth
	 *         error).
	 */
	public boolean startInquiry(boolean continuousBackground) {
		if (inquiryThread != null) {
			logger.warn("Background inquiry already running, can not start another instance.");
			return false;
		}
		if (! continuousBackground) {
			// OK, one-shot inquiry
			return startInquiry();
		}
		else {
			inquiryThread = new Thread(new InquiryThread() );
			inquiryThread.start();
			return true;
		}
	}

	/** Stops a background inquiry.
	 * @param force If set to true, then any inquiry or service search that 
	 *              may be running in the background will be canceled. If
	 *              set to false, they will be left running (and 
	 *              waitForBackgroundSearchToFinish can be used to wait for 
	 *              them to finish).
	 * @return true it stopped successfully, false if no background inquiry
	 *         was running.
	 */
	public boolean stopInquiry(boolean force) {
		if (inquiryThread != null) {
			Thread tmp = inquiryThread;
			inquiryThread = null;
			tmp.interrupt();
			try {
				tmp.join();
			} catch (InterruptedException e) {
				// stopping anyway, don't care when interrupted
			}
			
			if (force) {
				// if running, try to cancel an active inquiry
				if (eventsHandler.isInquiryRunning()) {
					logger.info("Inquiry currently running, aborting now (probably because we want to start another Bluetooth task)");
					agent.cancelInquiry(eventsHandler);
				}
			
				// and also cancel any service searches that might still be running
				RemoteDevice[] devs = getPeers();
				for (int i=0; i<devs.length; i++) {
					RemoteDeviceDetail dev = getDeviceDetail(devs[i]);
					if (dev.serviceSearchTransId > -1) {
						logger.info("Service search on device " + 
								devs[i].getBluetoothAddress() +
								"currently running, aborting now (probably because we want to start another Bluetooth task)");
						agent.cancelServiceSearch(dev.serviceSearchTransId);
					}
				}
			}
			
			return true;
		}
		else
			return false;
	}
	
	/** Returns true if the inquiry thread is currently running. */
	public boolean isInquiryActive() {
		return inquiryThread != null;
	}
	
	/** Wait for any background inquiry or service search that may be running
	 * to finish. It is advisable to call stopInquiry() before this method...
	 * @param timeoutMs The maximum amount of time to wait in milliseconds.
	 * @return true if, within timeoutMs milliseconds, no background task is 
	 *         running any more, false otherwise (i.e. if something is still
	 *         running).
	 * @throws InterruptedException
	 */ 
	public boolean waitForBackgroundSearchToFinish(int timeoutMs) throws InterruptedException {
		long startWait = System.currentTimeMillis();
		while (eventsHandler.isInquiryRunning() && 
			   System.currentTimeMillis()-startWait <= timeoutMs) {
			logger.trace("Waiting for background inquiry to finish...");
			Thread.sleep(200);
		}
		if (eventsHandler.isInquiryRunning()) {
			logger.info("Timeout while waiting for background inquiry to finish: still running after " +
					timeoutMs + "ms, aborting wait");
			return false;
		}
		logger.info("Background inquiry finished");
		
		synchronized (foundDevices) {
			for (Enumeration devices = foundDevices.keys(); devices.hasMoreElements(); ) {
				if (!waitForServiceSearch((RemoteDevice) devices.nextElement(), 
						timeoutMs, startWait))
					return false;
			}
		}
		return true;
	}
	
	/** Start to search for the list of services on a remote device. This 
	 * should not be done when automaticServiceDiscovery=true for performance
	 * reasons (but does not hurt if it is called). Calling this method empties
	 * the services list for the given device before the new search to get rid
	 * of potentially stale (changed) service entries.
	 * @param device The remote device to get the service list from.
	 * @param specificService The UUID of a specific service to search for. If
	 *                        set to null, arbitrary services are returned.
	 *                        The UUID 0x0100 (for L2CAP support) is always 
	 *                        included in the search, because we only support
	 *                        L2CAP connections anyway (no SCO).
	 * @return true if the service search could be started, false otherwise
	 *         (most probably due to Bluetooth state issue like an already
	 *         running inquiry or too many concurrent service searches).
	 */
	public boolean startServiceSearch(RemoteDevice device, UUID specificService) {
		// TODO: make me configurable?
		/* query for the following attributes:
		 *     - 0x0000: ServiceRecordHandle
		 *     - 0x0001: ServiceClassIDList
		 *     - 0x0002: ServiceRecordState
		 *     - 0x0003: ServiceID
		 *     - 0x0004: ProtocolDescriptorList
		 *     - 0x0100: service name
		 */
		// attribute list {0x100,0x0003} will fetch the name of the services
		int[] attributes = new int[] {0x0000, 0x0001, 0x0002, 0x0003, 0x004, 0x0100};
		UUID[] uuids;
		if (specificService == null) {
			uuids = new UUID[1];
			uuids[0] = new UUID(0x0100); // always search for services that support L2CAP (no SCO)
			// another option would be to search for service UUID 0x0003 (RFCOMM)
		}
		else {
			uuids = new UUID[1];
			// TODO: check if it has any advantage to include this! maybe it doesn't
			//uuids[0] = new UUID(0x0100);
			// TODO: if we include the first, increase index to 1
			uuids[0] = specificService;
		}
		RemoteDeviceDetail dev = getDeviceDetail(device);
		// no need to report the error here, the helper method does that
		if (dev == null) return false;
		synchronized (dev) {
			// safety check
			if (dev.serviceSearchTransId != -1) {
				logger.warn("Service search for remote device " + 
						device.getBluetoothAddress() + 
						" already in progress, not starting a second time");
				return false;
			}
			
			// when requested to start a new search, we certainly aren't finished...
			dev.serviceSearchFinished = false;
			dev.services.removeAllElements();

			try {
				logger.info("Starting service search on " + device.getBluetoothAddress());
				dev.serviceSearchTransId = agent.searchServices(attributes, uuids, device, new DiscoveryEventsHandler(device));
				if (dev.serviceSearchTransId < 0)
					logger.warn("Started service search for remote device " +
							device.getBluetoothAddress() +
							" but returned transaction id <0 (" +
							dev.serviceSearchTransId + "). This should not happen!");
				// serviceSearchTransId changed, notify
				synchronized (dev.notifier) {
					dev.notifier.notifyAll();
				}
				return true;
			} catch (BluetoothStateException e) {
				logger.warn("Could not initiate service search for remote device " +
						device.getBluetoothAddress() + ": " + e);
				LoggingHelper.debugWithException(logger, 
						"BluetoothStateException while starting service search for " +
						device.getBluetoothAddress(), e);
				return false;
			} catch (IllegalArgumentException e) {
				logger.warn("Could not initiate service search for remote device " +
						device.getBluetoothAddress() + ": " + e);
				LoggingHelper.debugWithException(logger, 
						"IllegalArgumentException while starting service search for " +
						device.getBluetoothAddress(), e);
				return false;
			}
		}
	}
	
	/** Returns the list of devices that have been discovered until now.
	 * @return The list of devices. If no devices have been discovered yet, the
	 *         returned array will be valid, but empty. 
	 */
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
	
	/** Returns the list of services that have been discovered for a device.
	 * @param device The device for which the services should be returned. 
	 *               This device must have been discovered before, i.e. it must
	 *               be in the list of devices returned by getPeers.
	 * @return The list of services found for the given device. When the device
	 *         is valid but not services have been found, the returned array 
	 *         will be valid but empty. When the device has not been discovered
	 *         yet or service discovery for it is still in progress, null is
	 *         returned.
	 */
	public ServiceRecord[] getServices(RemoteDevice device) {
		Vector services;
		RemoteDeviceDetail dev = getDeviceDetail(device);
		// no need to report the error here, the helper method does that
		if (dev == null) return null;
		synchronized (dev) {
			if (!dev.serviceSearchFinished) {
				logger.warn("Service search for remote device " + device.getBluetoothAddress() +
						" has not finished yet, don't have a service list.");
				return null;
			}
			
			services = dev.services;
		}
		synchronized(services) {
			ServiceRecord[] ret = new ServiceRecord[services.size()];
			for (int i=0; i<services.size(); i++)
				ret[i] = (ServiceRecord) services.elementAt(i);
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
		return listeners.removeElement(listener);
	}
	
	/** Users of BluetoothPeerManager should most probably implement this
	 * interface and register themselves with addListener. They will then
	 * receive events about discovered devices and services.
	 * @see BluetoothPeerManager#addListener
	 */
	public interface PeerEventsListener {
		public final static int SEARCH_COMPLETE = 0;
		public final static int DEVICE_NOT_REACHABLE = 1;
		public final static int SEARCH_FAILED = 2;
		public final static int SEARCH_ABORTED = 3;
		
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
		 *                     been searched for.
		 * @param services The list of services of the remote device. Elements
		 *                 of the vector are of type ServiceRecord. If no 
		 *                 services were found, then this vector is empty and
		 *                 the parameter errorReason is set. However, note that
		 *                 this vector may also be empty when no error occurred
		 *                 but when the other device simply offers no services
		 *                 that were searched for.
		 * @param errorReason 0 indicates successful completion of service 
		 * 					  search (either returning a list of matching 
		 * 					  services or an empty list if there are none),
		 * 					  1 indicates the remote device was not reachable,
		 *                    2 that the service search ended with an error 
		 *                    from the Bluetooth stack, and
		 *                    3 that the search was canceled prematurely by
		 *                    another thread.
		 */
		public void serviceSearchCompleted(RemoteDevice remoteDevice, 
				Vector services, int errorReason);
	}
	
	private class InquiryThread implements Runnable {
		public void run() {
			while (inquiryThread != null) {
				logger.debug("Main inquiry thread: starting next inquiry");
				if (!startInquiry())
					return;
				try {
					// before starting the next inquiry run, wait for this one to finish
					synchronized (eventsHandler) {
						while (eventsHandler.isInquiryRunning())
							eventsHandler.wait();
					}
					// if necessary, go through the service scan queue
					synchronized (serviceSearchQueue) {
						for (int i=0; i<serviceSearchQueue.size(); i++) {
							RemoteDevice device = (RemoteDevice) serviceSearchQueue.elementAt(i);
							logger.debug("Main inquiry thread: processing service search queue request for " + 
									device.getBluetoothAddress());
							startServiceSearch(device, automaticServiceDiscoveryUUID);
							// wait for the previous service scan to finish before starting the next
							long startWait = System.currentTimeMillis();
							RemoteDeviceDetail dev = getDeviceDetail(device);
							// if already shutting down, don't wait but cancel immediately
							if (!waitForServiceSearch(device, 
									(inquiryThread != null ? TIMEOUT_SERVICE_SEARCH : -1), 
									startWait)) {
								logger.info("Service search on " + device.getBluetoothAddress() +
										" timed out, aborting it");
								if (dev.serviceSearchTransId > -1)
									agent.cancelServiceSearch(dev.serviceSearchTransId);
								else
									logger.warn("Tried to cancel service search on " + 
											device.getBluetoothAddress() + 
											", but no transaction ID known, can not cancel");
							}
						}
						serviceSearchQueue.removeAllElements();
						logger.debug("Main inquiry thread: service search queue processed, emptying now");
					}
					// and sleep (but only if not exiting)
					if (inquiryThread != null)
						Thread.sleep((sleepBetweenInquiries*8 + random.nextInt(sleepBetweenInquiries*4))/10);
				} catch (InterruptedException e) { 
					// just ignore when we are being interrupted - will only shorten the wait time but is non-critical
				}
			}
		}		
	}

	/** This is only a small structure for keeping together the things we
	 * remember about a discovered remote device. 
	 */
	private class RemoteDeviceDetail {
		boolean newlyDiscovered = true;
		long lastSeen = System.currentTimeMillis();
		/* Every nth time the respective device is found in inquiry, a service
		 * search is triggered. It is also triggered when set to 0 and thus 
		 * this field is slightly abused as a "service search error" flag that
		 * is reset to 0 when a service search aborts with an error. 
		 */
		int numNoServiceScans = 0;
		Vector services = new Vector();
		// this should only be false _while a service search is actively running_
		boolean serviceSearchFinished = false;
		int serviceSearchTransId = -1; // -1 when not in progress 
		
		Object notifier = new Object(); // only used for notifying a thread that's waiting for any change in the object
	}
	
	/** This is an internal helper class for reacting to Bluetooth events. */
	private class DiscoveryEventsHandler implements DiscoveryListener {
		/** true if inquiry has finished, false otherwise. */
		private boolean isFinished = true;

		/** The device we are searching services on, or null if we are searching for devices. */
		private RemoteDevice currentRemoteDevice = null;
		
		/** @param forDevice	Set to null for device discovery and to the 
		 *                      remote device object for service discovery.
		 */
		public DiscoveryEventsHandler(RemoteDevice forDevice) {
			this.currentRemoteDevice = forDevice;
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

			logger.debug("Found remote device with MAC " + remoteDevice.getBluetoothAddress() +
					" named '" + resolveName(remoteDevice) + "' of class " + 
					deviceClass.getMajorDeviceClass() + "." + deviceClass.getMinorDeviceClass());
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
				logger.error("Remote device not set, but discovered devices. This should not happen, ignoring devices!");
				return;
			}

			switch (param) {
			case DiscoveryListener.INQUIRY_COMPLETED: 
				// find out which of the devices are new
				Vector newDevices = new Vector();
				synchronized (foundDevices) {
					for (Enumeration devices = foundDevices.keys(); devices.hasMoreElements(); ) {
						RemoteDevice device = (RemoteDevice) devices.nextElement();
						RemoteDeviceDetail entry = (RemoteDeviceDetail) foundDevices.get(device);
						synchronized (entry) {
							// if this device has been discovered in the current inquiry run, report it
							if (entry.newlyDiscovered) {
								entry.newlyDiscovered = false;
								newDevices.addElement(device);
							}
							// and start service discovery if requested the first and every nth time
							// NOTE: service searches are started consecutively by processing the search queue
							if (automaticServiceDiscovery) {
								if (entry.numNoServiceScans >= SCAN_SERVICES_FACTOR || entry.numNoServiceScans == 0 ||
										// but also start it if the last search finished with an error
										!entry.serviceSearchFinished) {
									entry.numNoServiceScans = 1;
									// add to the service search queue so that it will be scanned
									synchronized (serviceSearchQueue) {
										serviceSearchQueue.addElement(device);
									}
								}
								else
									entry.numNoServiceScans++;
							}
							synchronized (entry.notifier) {
								entry.notifier.notifyAll();
							}
						}
					}
				}
				
				if (newDevices.size() == 0) {
					if (logger.isDebugEnabled())
						logger.debug("Discovery completed, found " + newDevices.size() + 
								" new devices, forwarding to " + listeners.size() + " listeners");
				}
				else {
					if (logger.isInfoEnabled())
						logger.info("Discovery completed, found " + newDevices.size() + 
								" new devices, forwarding to " + listeners.size() + " listeners");
				}
				for (int i=0; i<listeners.size(); i++)
					((PeerEventsListener) listeners.elementAt(i)).inquiryCompleted(newDevices);
				
				// also adapt the sleep time depending on how many new devices were found
				/* Don't care about synchronization, it's only an integer 
				 * variable and we can't really mess it up too badly here.
				 */
				if (adaptiveSleepTime) {
					if (newDevices.size() > 0) 
						// found new devices, decrease time quickly
						sleepBetweenInquiries = sleepBetweenInquiries / (newDevices.size() + 1);
					else
						// no new devices, slowly increase
						sleepBetweenInquiries += sleepBetweenInquiries/2; 
					if (sleepBetweenInquiries < MINIMUM_SLEEP_TIME)
						sleepBetweenInquiries = MINIMUM_SLEEP_TIME;
					if (sleepBetweenInquiries > MAXIMUM_SLEEP_TIME)
						sleepBetweenInquiries = MAXIMUM_SLEEP_TIME;
					logger.debug("Inquiry found " + newDevices.size() + 
							" new, adapting sleep time to " + sleepBetweenInquiries + "ms");
				}
				
				break;
			case DiscoveryListener.INQUIRY_ERROR:
				logger.error("Inquiry error");
				break;
			case DiscoveryListener.INQUIRY_TERMINATED: // inquiry terminated by agent.cancelInquiry()
				logger.error("Inquiry cancelled");
				break;
			}
			synchronized (this) {
				// in any instance, inquiry is no longer active
				/* This will also wake up the inquiryThread (if running), and
				 * thus trigger going through the service search queue.
				 */ 
				isFinished = true;
				// signal the waiting background thread (if there is one)
				this.notify();
			}
		}


		public void servicesDiscovered(int transID, ServiceRecord[] serviceRecord) {
			if (logger.isInfoEnabled())
				logger.info("Discovered " + serviceRecord.length + " services for remote device " +
					currentRemoteDevice.getBluetoothAddress() + 
					" with transaction id " + transID);
			if (currentRemoteDevice == null) {
				logger.error("Remote device not set, but discovered services. This should not happen, ignoring services!");
				return;
			}

			Vector services;
			RemoteDeviceDetail dev = getDeviceDetail(currentRemoteDevice);
			// no need to report the error here, the helper method does that
			if (dev == null) return;
			synchronized (dev) {
				// sanity check
				if (dev.serviceSearchFinished) {
					logger.warn("Service search for remote device " + currentRemoteDevice +
							" has already finished, can not append to services list");
					return;
				}
				// and another sanity check
				if (dev.serviceSearchTransId != transID) {
					logger.warn("Discovered services with transaction id " + 
							transID + ", while starting the search returned id " +
							dev.serviceSearchTransId + ", not appending to services list");
					return;
				}
				
				services = dev.services;
			}
			synchronized (services) {	
				for (int i=0; i<serviceRecord.length; i++) {
					if (logger.isDebugEnabled())
						logger.debug("Service " + i + ": " + 
							serviceRecord[i].getConnectionURL(ServiceRecord.NOAUTHENTICATE_NOENCRYPT, false));
					dev.services.addElement(serviceRecord[i]);
				}
				if (logger.isDebugEnabled())
					logger.debug("Total number of services discovered for " + 
						currentRemoteDevice + " is now " + services.size());
				// something in dev changed, notify
				synchronized (dev.notifier) {
					dev.notifier.notifyAll();
				}
			}
		}

		public void serviceSearchCompleted(int transID, int respCode) {
			if (currentRemoteDevice == null) {
				logger.error("Remote device not set, but discovered services. This should not happen, ignoring services!");
				return;
			}

			if (logger.isInfoEnabled())
				logger.info("Service search completed for " + currentRemoteDevice.getBluetoothAddress() + 
					" with transID " + transID + " and respCode " + respCode);
			
			RemoteDeviceDetail dev = getDeviceDetail(currentRemoteDevice);
			// no need to report the error here, the helper method does that
			if (dev == null) return;
			
			synchronized (dev) {
				// sanity check
				if (dev.serviceSearchFinished) {
					logger.warn("Service search for remote device " + currentRemoteDevice.getBluetoothAddress() +
							" has already finished, can not finish twice, ignoring");
					return;
				}
				// and another sanity check
				if (dev.serviceSearchTransId != transID) {
					logger.warn("Finished service discovery with transaction id " + 
							transID + ", while starting the search returned id " +
							dev.serviceSearchTransId + ", ignoring");
					return;
				}
				dev.serviceSearchTransId = -1;
			}

			switch (respCode) {
			case DiscoveryListener.SERVICE_SEARCH_COMPLETED:
				Vector services;
				synchronized (dev) {
					dev.serviceSearchFinished = true;
					services = dev.services;
					// finished with that service, wake up when we are waiting for it
					dev.notifyAll();
				}
				// changed
				synchronized (dev.notifier) {
					dev.notifier.notifyAll();
				}

				synchronized (services) { 
					if (logger.isInfoEnabled())
						logger.info("Service scan for " + currentRemoteDevice.getBluetoothAddress() +
								" with transaction " + transID + " completed, found " + services.size() + 
							" services, forwarding to " + listeners.size() + " listeners");
					if (logger.isDebugEnabled()) {
						for (int i=0; i<services.size(); i++) {
							ServiceRecord sr = (ServiceRecord) services.elementAt(i);
							logger.debug("Service " + i + ": " + sr.getConnectionURL(ServiceRecord.NOAUTHENTICATE_NOENCRYPT, false));
						}
					}
					for (int i=0; i<listeners.size(); i++)
						((PeerEventsListener) listeners.elementAt(i)).serviceSearchCompleted(
								currentRemoteDevice, services, PeerEventsListener.SEARCH_COMPLETE);
				}
				
				break;
			case DiscoveryListener.SERVICE_SEARCH_DEVICE_NOT_REACHABLE:
				logger.info("Device not reachable while trying to perform service discovery on current device " +
						currentRemoteDevice.getBluetoothAddress() + 
						". Maybe the other device is running an inquiry or has an open connection?");
				synchronized (dev) {
					// the actual service search transaction has finished, but with an error
					dev.serviceSearchFinished = true;
					dev.numNoServiceScans = 0;
					dev.notifyAll();
				}
				// changed
				synchronized (dev.notifier) {
					dev.notifier.notifyAll();
				}
				for (int i=0; i<listeners.size(); i++)
					((PeerEventsListener) listeners.elementAt(i)).serviceSearchCompleted(
							currentRemoteDevice, null, PeerEventsListener.DEVICE_NOT_REACHABLE);
				break;
			case DiscoveryListener.SERVICE_SEARCH_ERROR:
				logger.info("Service search error reported by Bluetooth stack for current device " +
						currentRemoteDevice.getBluetoothAddress() + 
						". Most probably the other device has no matching service or it might be busy. This is ok.");
				synchronized (dev) {
					// the actual service search transaction has finished, but with an error
					dev.serviceSearchFinished = true;
					dev.numNoServiceScans = 0;
					dev.notifyAll();
				}
				synchronized (dev.notifier) {
					dev.notifier.notifyAll();
				}
				for (int i=0; i<listeners.size(); i++)
					((PeerEventsListener) listeners.elementAt(i)).serviceSearchCompleted(
							currentRemoteDevice, null, PeerEventsListener.SEARCH_FAILED);
				break;
			case DiscoveryListener.SERVICE_SEARCH_NO_RECORDS:
				logger.info("No matching records returned for service search on current device " +
						currentRemoteDevice.getBluetoothAddress());
				synchronized (dev) {
					// in this case, service search was actually finished correctly (even if we didn't get any records)
					dev.serviceSearchFinished = true;
					dev.notifyAll();
				}
				synchronized (dev.notifier) {
					dev.notifier.notifyAll();
				}
				for (int i=0; i<listeners.size(); i++)
					((PeerEventsListener) listeners.elementAt(i)).serviceSearchCompleted(
							currentRemoteDevice, null, PeerEventsListener.SEARCH_COMPLETE);
				break;
			case DiscoveryListener.SERVICE_SEARCH_TERMINATED:
				logger.info("Service search cancelled by stack on current device " +
						currentRemoteDevice.getBluetoothAddress());
				synchronized (dev) {
					// the actual service search transaction has finished, but with an error
					dev.serviceSearchFinished = true;
					dev.numNoServiceScans = 0;
					dev.notifyAll();
				}
				synchronized (dev.notifier) {
					dev.notifier.notifyAll();
				}
				for (int i=0; i<listeners.size(); i++)
					((PeerEventsListener) listeners.elementAt(i)).serviceSearchCompleted(
							currentRemoteDevice, null, PeerEventsListener.SEARCH_ABORTED);
				break;
			}
			if (!dev.serviceSearchFinished)
				logger.warn("serviceSearchCompleted handler exiting, but serviceSearchFinished still not true for " +
						currentRemoteDevice.getBluetoothAddress() + 
						". This should not happen! Please investigate right now!");
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
			LoggingHelper.debugWithException(logger, 
					"BluetoothStateException while starting inquiry", e);
			return false;
		}
	}
	
	/** This is only a small helper function of retrieving the 
	 * RemoteDeviceDetail object for an already discovered device.
	 * @return The RemoteDeviceDetail object if found or null if not found.
	 */ 
	private RemoteDeviceDetail getDeviceDetail(RemoteDevice device) {
		RemoteDeviceDetail dev;
		synchronized(foundDevices) {
			if (! foundDevices.containsKey(device)) {
				logger.error("Remote device " + device.getBluetoothAddress() + 
					" has not been discovered before, don't have a service list yet. This is not yet supported!");
				return null;
			}
			dev = (RemoteDeviceDetail) foundDevices.get(device);
			// sanity check
			if (dev == null) {
				logger.error("Internal error: Remote device " + device.getBluetoothAddress() + 
				" has been discovered before, service list has not been set correctly. This should not happen!");
			return null;
			}
		}
		return dev;
	}
	
	/** This is only a small helper to wait for a service search at a device
	 * to finish.
	 * @return true if no search is running, false if wait was not successful
	 *         (something is still running).
	 * @throws InterruptedException 
	 */
	private boolean waitForServiceSearch(RemoteDevice device,
			int timeoutMs, long startWait) throws InterruptedException {
		RemoteDeviceDetail dev = getDeviceDetail(device);
		synchronized (dev.notifier) {
			while (!dev.serviceSearchFinished && 
					System.currentTimeMillis()-startWait <= timeoutMs) {
				if (logger.isTraceEnabled())
					logger.trace("Waiting for service search to finish...");
				dev.notifier.wait(500);
			}
			if (!dev.serviceSearchFinished) {
				logger.info("Timeout while waiting for service search for " +
						device.getBluetoothAddress() + 
						" to finish: still running after " +
						timeoutMs + "ms, aborting wait");
				return false;
			}
			else
				return true;
		}
	}
	
	/** This is a helper function for resolving a remote device name. If the
	 * name can not be queried or is empty, then the Bluetooth (MAC) address 
	 * will be returned instead. This method takes care of the Bluetooth
	 * exception that can occur when trying to resolve the name and will always
	 * return a valid string object.
	 * @param device The remote device to resolve the name for.
	 */
	public static String resolveName(RemoteDevice device) {
		if (device == null) {
			logger.warn("Can not resolve name for null device");
			return null;
		}
		
		String name;
		try {
			name = device.getFriendlyName(false);

		} catch (IOException ioe) {
			name = device.getBluetoothAddress();
		}
		if (name.length() == 0)
			name = device.getBluetoothAddress();
		return name;
	}
	
	/** This is a helper function to return a remote service given a UUID and 
	 * Bluetooth MAC address. It blocks during inquiry and service search and 
	 * can thus take easily up to 30 seconds to return!
	 * 
	 * @param remoteAddress The Bluetooth MAC address of the remote device.
	 * @param serviceUuid The UUID of the service to search for.
	 * @param authenticateEncryptMode See ServiceRecord, use e.g. 
	 * 			ServiceRecord.NOAUTHENTICATE_NOENCRYPT
	 * @param timeoutMs The maximum amount of time to wait in milliseconds.
	 * @return The complete URL to the service if it was found (that is, a 
	 *          service with the specified UUID at the specified device) or
	 *          null otherwise.
	 * @throws IOException
	 */
	public static String getRemoteServiceURL(String remoteAddress, 
			UUID serviceUuid, int authenticateEncryptMode, int timeoutMs) throws IOException {
		BluetoothPeerManager serviceSearch = new BluetoothPeerManager();
		
		// for that, need to do an inquiry to get the RemoteDevice objects - JSR82 really isn't that nice
		/*serviceSearch.startInquiry(false);
		try {
			serviceSearch.waitForBackgroundSearchToFinish(15000);
		} catch (InterruptedException e) {
			// don't care - if we haven't found the device yet, simply return null
		}
		serviceSearch.stopInquiry(true);
		try {
			serviceSearch.waitForBackgroundSearchToFinish(10000);
		} catch (InterruptedException e) {
			// don't care - if we haven't found the device yet, simply return null
		}
		
		RemoteDevice[] devs = serviceSearch.getPeers();
		RemoteDevice dev = null;
		for (int i=0; i<devs.length && devs == null; i++) {
			if (devs[i].getBluetoothAddress().equals(remoteAddress)) {
				logger.info("Found remote device " + remoteAddress + 
						" during inquiry, now browsing for RFCOMM channel");
				dev = devs[i];
			}
		}
		if (dev == null) {
			logger.warn("Didn't find device " + remoteAddress + 
					" during inquiry, can not browse for services");
			return null;
		}*/
		
		/* UPDATE: use a small hack to directly construct a RemoteDevice 
		 * object from a known Bluetooth MAC address - this is far quicker
		 * then doing a inquiry beforehand!
		 */
		RemoteDevice dev = new BluetoothRFCOMMChannel.RDevice(remoteAddress);
		// but need to add it to list of found device so that service search can deal with it
		serviceSearch.foundDevices.put(dev, serviceSearch.new RemoteDeviceDetail());
		
		serviceSearch.startServiceSearch(dev, serviceUuid);
		try {
			// blocks return of the servicesearch!!
			//serviceSearch.waitForBackgroundSearchToFinish(timeoutMs);
			serviceSearch.waitForServiceSearch(dev, timeoutMs, System.currentTimeMillis());
		} catch (InterruptedException e) {
			// don't care - if we haven't found the service yet, simply return null
		}
		ServiceRecord[] services = serviceSearch.getServices(dev);
		logger.info("Got " + services.length + " services for remote " + remoteAddress);
		if (services.length > 1) {
			logger.error("Unexpected number (" + services.length + 
					") of remote services with the specific UUID (expected only 1 - it should be unique!)");
			return null;
		}
		else if (services.length == 1) {
			// found
			return services[0].getConnectionURL(authenticateEncryptMode, false);
		} 
		else {
			// no matching service found
			logger.warn("No matching service found on " + remoteAddress + 
				" with UUID " + serviceUuid.toString());
			return null;
		}
	}
	
	/*
	 * String connectionURL = servRecord[i].getConnectionURL(0, false);
	 */
	
	 	 /* Retrieve PREKNOWN devices and add them to our Vector */
/*		 RemoteDevice[] devices = agent.retrieveDevices(DiscoveryAgent.PREKNOWN);*/
	/*
	 * repeating the in the above code each time will obviously extend the time
	 *  devices = agent.retrieveDevices(DiscoveryAgent.PREKNOWN);
 devices = agent.retrieveDevices(DiscoveryAgent.GIAC);
 devices = agent.retrieveDevices(DiscoveryAgent.CACHED); 
	 */
	
	/* In the case of a Serial Port service record, this string might look like "btspp://0050CD00321B:3;authenticate=true;encrypt=false;master=true", where "0050CD00321B" is the Bluetooth address of the device that provided this ServiceRecord, "3" is the RFCOMM server channel mentioned in this ServiceRecord, and there are three optional parameters related to security and master/slave roles.  */
	
	////////////// testing code //////////////////
//#if cfg.includeTestCode
	private static class TempEventsHandler implements PeerEventsListener {
		BluetoothPeerManager man;
		
		public void inquiryCompleted(Vector newDevices) {
			System.out.println("Inquiry completed, new devices: ");
			for (int i=0; i<newDevices.size(); i++)
				System.out.println("    " + resolveName((RemoteDevice) newDevices.elementAt(i)));
			
			System.out.println("- List of all devices discovered so far: ");
			RemoteDevice[] allDevices = man.getPeers();
			for (int i=0; i<allDevices.length; i++) {
				System.out.println("    " + resolveName(allDevices[i]));
			}
		}

		public void serviceSearchCompleted(RemoteDevice remoteDevice, Vector services, int errorReason) {
			if (errorReason == PeerEventsListener.SEARCH_COMPLETE) {
				System.out.println("Service search completed successfully for device " + resolveName(remoteDevice));
				for (int x = 0; x < services.size(); x++) {
					ServiceRecord service = (ServiceRecord) services.elementAt(x);
					DataElement ser_de = service.getAttributeValue(0x100);
					String name, url;
					if (ser_de != null)
						name = (String) ser_de.getValue();
					else
						name = "<unable to resolve name>";
					url = service.getConnectionURL(ServiceRecord.NOAUTHENTICATE_NOENCRYPT, false);
					System.out.println("* Found service for device " + 
							resolveName(remoteDevice) + ": " + name + "; " + url);
				}
			}
			else {
				System.out.println("Did not find any services for device + " + resolveName(remoteDevice)
						+ " with reason " + errorReason);
			}
		}
	}
	
	public static void main (String[] args) throws IOException {
		TempEventsHandler l = new TempEventsHandler();
		BluetoothPeerManager m = new BluetoothPeerManager();
		l.man = m;
		m.addListener(l);
		System.out.println("Starting inquiry");
		m.setAutomaticServiceDiscovery(true);
		m.startInquiry(true);
		System.out.println("Press any key to stop background inquiry");
		System.in.read();
		m.stopInquiry(true);
	}
//#endif
}
