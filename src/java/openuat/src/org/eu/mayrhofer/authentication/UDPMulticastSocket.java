/* Copyright Rene Mayrhofer
 * File created 2006-05-05
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.authentication;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.LinkedList;

import org.apache.log4j.Logger;

public class UDPMulticastSocket {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(UDPMulticastSocket.class);

	public final static String[] Interface_Names_Blacklist = new String[] { "vmnet", "lo" };

	public UDPMulticastSocket() throws SocketException {
		Enumeration ifaces = NetworkInterface.getNetworkInterfaces();
		LinkedList allAddrs = new LinkedList();
		while (ifaces.hasMoreElements()) {
			NetworkInterface iface = (NetworkInterface) ifaces.nextElement();
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
		logger.debug("Using " + allAddrs.size() + " addresses, starting one multicast socket for each");
		
	}
}
