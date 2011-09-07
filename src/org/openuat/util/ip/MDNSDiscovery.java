/* Copyright The Relate project team, Lancaster University
 * File created 2006-01
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.util.ip;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Enumeration;
import java.util.Timer;
import java.util.TimerTask;
import java.net.Inet6Address;
import java.net.InetAddress;

import javax.jmdns.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** This class wraps host and service discovery via Multicast DNS, using the jmDNS library
 * (a MDNS and DNS-SD implementation in pure Java) at the moment. If another MDNS responder
 * is already running on the machine (e.g. mDNSresponder under MacOSX or avahi under Linux),
 * there might be problems with starting a second instance of an MDNS responder (which is 
 * done by jmDNS). For a cleaner implementation, this class should use the operating system
 * support for MDNS/DNS-SD when available and fall back to using jmDNS when none can be found.
 * 
 * Because there can only be a single MDNS responder, this class is implemented as a singleton.
 * 
 * @author Rene Mayrhofer, modified by Carl Fischer
 * 
 * This code is licensed under terms of the GNU Lesser General Public License. 
 */
public class MDNSDiscovery {
	/** This is the DNS-SD type used to register relate devices. */ 
	public final static String DNS_SD_Type = "_relate._tcp.local.";
	/** This is the DNS-SD attribute name that is registered as DNS TXT data. */
	public final static String DNS_SD_Attribute = "deviceid";
	/** 
	 * Time to wait after receiving an add event before issuing a resolve request in millseconds 
	 * @see #ListenerHelper#serviceAdded
	 */
	public final static long QUERY_DELAY = 3000;
	
	public final static String[] Interface_Names_Blacklist = new String[] { "vmnet", "lo" };
	
	/** Holds the only instance of this class. */
	private static MDNSDiscovery instance = null;
		
	/** Our logger. */
	private static Logger logger = LoggerFactory.getLogger(MDNSDiscovery.class.getName());
	
	/** This holds one instance of the jMDNS responder for each interface that this host has. */
	private JmDNS[] jmdns = null; 
	
	/** Get the only instance of the MDNSDiscovery. 
	 * 
	 * @return The MDNSDiscovery singleton.
	 */
	public static MDNSDiscovery getMDNSDiscovery() throws IOException {
		if (instance == null) {
			logger.debug("Creating singleton instance of MDNSDiscovery");
			instance = new MDNSDiscovery();
		}
		return instance;
	}
	
	/** Constructs the MDNSDiscovery class by initializing the jmDNS responder.
	 * 
	 *  TODO: Use operating system MDNS support when available. 
	 */
	public MDNSDiscovery() throws IOException {
		super();

		// only use all network interfaces when not under PocketPC
		if (! System.getProperty("os.name").startsWith("Windows CE")) {
			// bind to all interfaces known to the host
			Enumeration ifaces = java.net.NetworkInterface.getNetworkInterfaces();
			LinkedList allAddrs = new LinkedList();
			while (ifaces.hasMoreElements()) {
				java.net.NetworkInterface iface = (java.net.NetworkInterface) ifaces.nextElement();
				logger.debug("Found local interface " + iface.getName());
				// check if that interface name is blacklisted
				boolean blacklisted = false;
				for (int i=0; i<Interface_Names_Blacklist.length; i++) {
					if (iface.getName().startsWith(Interface_Names_Blacklist[i])) {
						blacklisted = true;
					}
				}
				
				// TODO: actually, we should bind a JmDNS instance to each interface, not to each address!
				// fix JmDNS to work out all addresses to a given interface and then change it here
				if (!blacklisted) {
					Enumeration addrs = iface.getInetAddresses();
					while (addrs.hasMoreElements()) {
						InetAddress addr = (InetAddress) addrs.nextElement();
						if (addr instanceof Inet6Address) {
							logger.debug("Ignoring IPv6 address " + addr + " for now");
						} else {
							logger.debug("Found address " + addr);
							allAddrs.add(addr);
							// only use the first one for now.... - see above TODO
							break;
						}
					}
				} else {
					logger.debug("Ignoring interface because it is blacklisted");
				}
			}
			// start the responders
			logger.debug("Using " + allAddrs.size() + " addresses, starting one jmDNS responder for each");
			jmdns = new JmDNS[allAddrs.size()];
			for (int i=0; allAddrs.size() > 0; i++) {
				InetAddress addr = (InetAddress) allAddrs.removeFirst();
				logger.debug("Starting on " + addr);
				jmdns[i] = new JmDNS(addr);
			}
			logger.debug("jmDNS responders started");
			//addresses = new HashMap();
			// and register our listener on all resolvers
			for (int i = 0; i<jmdns.length; i++) {
				jmdns[i].addServiceListener(DNS_SD_Type, new ListenerHelper());
			}
		}
		else {
			logger.warn("Detected Windows CE, only using primary network interface for mDNS. This might be wrong.");
			jmdns = new JmDNS[1];
			jmdns[1] = new JmDNS();
			jmdns[1].addServiceListener(DNS_SD_Type, new ListenerHelper());
		}
	}
	
	/** This method registers a local device id for querying via MDNS/DNS-SD.
	 * @param localRelateId The local relate id to register.
	 */
	public void registerLocalDeviceId(int localRelateId) throws IOException {
		// just make sure that there's no minus in front....
		int positiveId = localRelateId;
		if (positiveId < 0)
			positiveId += 0x100;
		
		logger.debug("Registering local relate id " + localRelateId + " for MDNS/DNS-SD lookup");
		
		// TODO: register a meaningful port number
		int MY_PORT=8888;
		for (int i=0; i<jmdns.length; i++) {
			jmdns[i].registerService(new ServiceInfo(DNS_SD_Type, Integer.toString(positiveId), 
					MY_PORT, 0, 0, DNS_SD_Attribute + "=" + Integer.toString(positiveId)));
		}
	}
	
	/** (Try to) parse the device id from the service name. */
	private Integer parseService(String name) {
		try {
			int val = Integer.parseInt(name);
			if (val > 127)
				val -= 0x100;
			// just a sanity check
			if (val < -128 || val > 127) {
				logger.error("Device id is out of range");
				return null;
			}
			return new Integer(val);
		} catch (NumberFormatException e) {
			// ok, not parseable, probably no entry created by the class
			logger.error("Device id is not an integer number");
			return null;
		}
	}
	
	/** Resolves a remote device id to an IP address of the host to which the device is
	 * connected. As it performs the query synchronously, it can take a few seconds to
	 * complete.
	 * 
	 * @param remoteRelateId The remote relate id to get the address for.
	 * @return The IP address in some form (either host name, IPv4 or IPv6 address) usable
	 *         by InetAddress or null if no mapping could be found, i.e. if the address
	 *         could not be reolved (yet). 
	 */
	public String resolveAddress(int remoteRelateId) {
		// just make sure that there's no minus in front....
		int positiveId = remoteRelateId;
		if (positiveId < 0)
			positiveId += 0x100;
		
		logger.debug("Trying to resolve relate id " + positiveId);
		
		// this performs a query right now - so it will take some time
		// TODO: this just uses the first instance, which might not necessarily be the best - return all found addresses
		ServiceInfo service = null;
		for (int i=0; i<jmdns.length && service == null; i++) {
			service = jmdns[i].getServiceInfo(DNS_SD_Type, Integer.toString(positiveId));
			try {
				if (service != null) {
					logger.debug("Found service for relate id " + positiveId + " on interface " + 
							jmdns[i].getInterface() + ": maps to " + service.getHostAddress());
					// found the id, no need to continue
					break;
				}
			} catch (IOException e) {
				// just ignore, it's only debugging output anyways ....
			}
		}
		
		if (service == null) {
			logger.info("Unable to find address for relate id " + positiveId);
			return null;
		}
		logger.info("Mapped relate id " + positiveId + " to address " + service.getHostAddress());
		return service.getHostAddress();
	}
	
	
	/** This helper class implements the service listener, which will get notified of any
	 * MDNS replies that the responder receives.
	 */
	private class ListenerHelper implements ServiceListener {
		//TODO not urgent but some of this is duplicate code and could be cleaned up a bit
		//possibly make the main class implement ServiceListener rather than use a helper class
		//FIXME check that this is thread safe, are 2 methods of the same listener ever run concurrently ?
		int deviceId;
		boolean isRelateType;
		ServiceInfo info;
		
		private void parseEvent(ServiceEvent e) {
			isRelateType = false;
			//check that this is a relate service type
			//we are just registered for relate type services but check anyway
			if (e.getType().equals(DNS_SD_Type)) {
				isRelateType = true;
				deviceId = parseService(e.getName()).intValue();
				info = e.getInfo();
				if(info != null) {
					InetAddress ipAddress = info.getInetAddress();
					String hostname = info.getServer();
				} else {
					logger.warn("No info about service.");
				}
				//TODO should we use the other available info ? username for instance ?
				try {
					MDNSDiscovery.getMDNSDiscovery();
				} catch(IOException ex) {
					//doesn't matter, the source for the event will just be null
					logger.error("Unable to access mdns resolver.");
				}
				
			}
		}
		
		public void serviceAdded(ServiceEvent e){
			logger.debug("ADD event:\n" + e);
			parseEvent(e);
			if(isRelateType && info != null) {
			    //set current time since we don't have any other timestamp to work with
/*				Service service = new Service(ipAddress, hostname, deviceId, System.currentTimeMillis());
				services.put(new Integer(deviceId), service);
				fireEvent(new RelateConnectionEvent(jmdns, service));*/
			} else if (isRelateType && info == null) {
				//HACK the first add event doesn't always seem to contain any service info
				//if info is null, try ignoring the event and setting a timertask to do a resolve query in a few seconds
				Timer t = new Timer();
				t.schedule(new TimerTask() {
					public void run() {
						try {
							MDNSDiscovery.getMDNSDiscovery().resolveAddress(deviceId);
						} catch(IOException ex) {
							logger.warn("Couldn't get MDNSDiscovery.");
						}
					}
				}, QUERY_DELAY);
			}
		}
		
		public void serviceRemoved(ServiceEvent e)
		{
			logger.debug("REMOVE event:\n" + e);
			parseEvent(e);
			if (isRelateType) {
			    //set current time since we don't have any other timestamp to work with
/*			    Service service = new Service(ipAddress, hostname, deviceId, System.currentTimeMillis());
				services.remove(new Integer(deviceId));
				fireEvent(new RelateDisconnectionEvent(jmdns, service));*/
			}
		}
		
		public void serviceResolved(ServiceEvent e)
		{
			//let's treat this just like an add event
			//is that ok ???
			logger.debug("RESOLVE event:\n" + e);
			parseEvent(e);
			if (isRelateType) {
			    //set current time since we don't have any other timestamp to work with
/*				Service service = new Service(ipAddress, hostname, deviceId, System.currentTimeMillis());
				if(service == null) {
				    logger.warn("service creation failed");
				    return;
				}
				services.put(new Integer(deviceId), service);
				fireEvent(new RelateConnectionEvent(jmdns, service));*/
			}
		}
	}
	
	
	////////////////////////// Test code begins here //////////////////////////
	//TODO Junit
	public static void main(String[] args) throws IOException, InterruptedException {
		MDNSDiscovery d = MDNSDiscovery.getMDNSDiscovery();
		System.out.println("Started responder and registered listener");
		Thread.sleep(5000);
		d.registerLocalDeviceId(3);
		System.out.println("Registered local device");
		System.out.println("Waiting for events");
		Thread.sleep(5000);
		System.out.println("Proceeding to resolve addresses");
		for (int i= 0; i<5; i++) {
			String s = d.resolveAddress(i);
			System.out.println("Device id " + i + " maps to " + s);
		}
		d.jmdns = null;
		System.out.println("Finished.");
		System.out.println("Waiting for other events...");
		while(true)
			Thread.sleep(10000);
	}
}
