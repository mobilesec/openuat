/* Copyright Rene Mayrhofer
 * File created 2006-05-05
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.authentication;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.LinkedList;

import org.apache.log4j.Logger;

public class UDPMulticastSocket {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(UDPMulticastSocket.class);

    static final String MULTICAST_GROUP = "224.59.10.1";
	 
    static final int PORT = 2000;

    private InetAddress GROUP_ADDRESS;
    
	//public final static String[] Interface_Names_Blacklist = new String[] { "vmnet", "lo" };
	
	private DatagramSocket unicastSocket;
	private MulticastSocket multicastSocket;

	public UDPMulticastSocket(int port, boolean loopBackToLocalhost) throws IOException {
		GROUP_ADDRESS = InetAddress.getByName(MULTICAST_GROUP);
		multicastSocket = new MulticastSocket(PORT);
		multicastSocket.joinGroup(GROUP_ADDRESS);
        multicastSocket.setSoTimeout(30000);

	    /*public static void sendMessageToGroup(String message,
	            MulticastSocket multicastSocket) throws Exception {
	        byte[] data = message.getBytes("UTF-8");
	        DatagramPacket packet = new DatagramPacket(data, 0, data.length);
	        packet.setAddress(GROUP_ADDRESS);
	        packet.setPort(PORT);
	        multicastSocket.send(packet);
	    }*/

        /*sendMessageToGroup(this.id + " joins group", multicastSocket);
        byte[] buffer = new byte[2048];
        while (!quit) {
            DatagramPacket packet = new DatagramPacket(buffer,
                    buffer.length);
            multicastSocket.receive(packet);

            byte[] payLoad = new byte[packet.getLength()];
            System.arraycopy(packet.getData(), 0, payLoad, 0,
                    payLoad.length);
            String receivedMessage = new String(payLoad, "UTF-8");
            if (receivedMessage.equals(EXIT_COMMAND)) {
                quit = true;
            }
            System.out.println(this.id + " received "
                    + packet.getLength() + "bytes: " + receivedMessage);
        }
        System.out.println(this.id + " leaves the group");
        multicastSocket.leaveGroup(GROUP_ADDRESS);*/
        
		/*Enumeration ifaces = NetworkInterface.getNetworkInterfaces();
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
		logger.debug("Using " + allAddrs.size() + " addresses, starting one multicast socket for each");*/
		
	}
}
