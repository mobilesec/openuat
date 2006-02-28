package org.eu.mayrhofer.channel;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.LinkedList;

import org.apache.log4j.Logger;

/** This class implements various helper methods that don' really fit elsewhere. */
public class Helper {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(Helper.class);

	public final static String[] Interface_Names_Blacklist = new String[] { "vmnet", "lo" };
    
	public static LinkedList getAllLocalIps() throws SocketException {
		Enumeration ifaces = NetworkInterface.getNetworkInterfaces();
		LinkedList allAddrs = new LinkedList();
		while (ifaces.hasMoreElements()) {
			NetworkInterface iface = (NetworkInterface) ifaces.nextElement();
			logger.debug("Found local interface " + iface.getName());
			// check if that interface name is blacklisted
			boolean blacklisted = false;
			for (int i=0; i<Interface_Names_Blacklist.length; i++)
				if (iface.getName().startsWith(Interface_Names_Blacklist[i]))
					blacklisted = true;
			
			if (!blacklisted) {
				Enumeration addrs = iface.getInetAddresses();
				while (addrs.hasMoreElements()) {
					InetAddress addr = (InetAddress) addrs.nextElement();
					if (addr instanceof Inet6Address)
						logger.debug("Ignoring IPv6 address " + addr + " for now");
					else {
						logger.debug("Found address " + addr);
						allAddrs.add(addr.getHostAddress());
					}
				}
			}
			else
				logger.debug("Ignoring interface because it is blacklisted");
		}
		
		return allAddrs;
	}

}
