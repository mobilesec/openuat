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
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.ListIterator;

import org.apache.log4j.Logger;

/** This class offers unicast and multicast UDP communication. It binds one
 * MulticastSocket to each network interface address found in the system and sends
 * multicast packets via all of them. Received packets can be either unicast or
 * multicast, and a method to send only unicast packets is also offered.
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class UDPMulticastSocket {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(UDPMulticastSocket.class);

	/** A list of network interface names <b>not</b> to use for communication.
	 * All other network interfaces not matching these names are used to multicast
	 * messages.
	 */
	public final static String[] Interface_Names_Blacklist = new String[] { "vmnet", "lo" };
	
	/** Let each receive() call time out after 500ms to allow graceful shutdown of the
	 * receiving threads.
	 */
	private final static int TIMEOUT_RECEIVE = 500;

    /** Holds one socket for each network address in the system. With these sockets, 
     * both unicast and multicast packets are received, and multicast packets are sent
     * for the respective interfaces that the sockets are bound to. 
     * @see #sendMulticast(byte[])
     * @see RunHelper#run()
     */
	private MulticastSocket[] multicastSockets;
	
	/** This single socket is used to send unicast packets, using the system routing table.
	 * @see #sendTo(byte[], InetAddress) 
	 */
	private DatagramSocket unicastSocket;
	
	/** The port number used for all packets. It's the one passed to the constructor. */
	private int port;
	
	/** The multicast group address to send to when sending multicast packets. */
	private InetAddress groupAddress;

	/** The list of listeners that are notified of incoming messages. */
    private LinkedList messageHandlers = new LinkedList();

    /** One thread for each MulticastSocket to receive packets. Each thread will
     * continuously call receive() on the socket and forward all packets to all
     * registered listeners.
     * @see #startListening()
     * @see #stopListening()
     * @see #shouldExit 
     */
    private Thread[] listenerThreads = null;
    
    /** Set to true to signal the listener threads to exit. 
     * @see #stopListening() 
     */
    private boolean shouldExit = false;
    
	public UDPMulticastSocket(int port, String multicastGroup, boolean loopBackToLocalhost) throws IOException {
		this.port = port;
		
		unicastSocket = new DatagramSocket();
		// receive should actually never be called on this socket, but set it so that it won't be infinite in any case
		unicastSocket.setSoTimeout(TIMEOUT_RECEIVE);
		
		groupAddress = InetAddress.getByName(multicastGroup);

		// create one multicast socket for each address
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
				
			if (!blacklisted) {
				Enumeration addrs = iface.getInetAddresses();
				while (addrs.hasMoreElements()) {
					InetAddress addr = (InetAddress) addrs.nextElement();
					if (addr instanceof Inet6Address) {
						logger.debug("Ignoring IPv6 address " + addr + " for now");
					} else {
						logger.debug("Found address " + addr);
						allAddrs.add(addr);
					}
				}
			} else {
				logger.debug("Ignoring interface because it is blacklisted");
			}
		}
		// start the responders
		logger.debug("Using " + allAddrs.size() + " addresses, starting one multicast socket for each");
		
		multicastSockets = new MulticastSocket[allAddrs.size()];
		for (int i=0; i<multicastSockets.length; i++) {
			multicastSockets[i] = new MulticastSocket(port);
			multicastSockets[i].joinGroup(groupAddress);
	        multicastSockets[i].setSoTimeout(TIMEOUT_RECEIVE);
	        if (loopBackToLocalhost) {
	        	multicastSockets[i].setLoopbackMode(loopBackToLocalhost);
	        	if (!multicastSockets[i].getLoopbackMode())
	        		logger.warn("Could not set loopback mode, own packets will not be seen by localhost");
	        }
		}
	}
	
	/** Sends a multicast message to the group. This will send at least one packet 
	 * on each interface, and may send multiple for interfaces with multiple addresses.
	 * @param message The message to send.
	 */
	public void sendMulticast(byte[] message) throws IOException {
        DatagramPacket packet = new DatagramPacket(message, 0, message.length);
		packet.setAddress(groupAddress);
		packet.setPort(port);
		for (int i=0; i<multicastSockets.length; i++) {
			multicastSockets[i].send(packet);
		}
	}
	
	/** Send a unicast message to a specific address. This will use the system routing
	 * table to determine the appropriate interface to send the packet to.
	 * @param message The message to send.
	 * @param target The target address to send to.
	 */
	public void sendTo(byte[] message, InetAddress target) throws IOException {
        DatagramPacket packet = new DatagramPacket(message, 0, message.length);
		packet.setAddress(target);
		packet.setPort(port);
		unicastSocket.send(packet);
	}

    /** Register a listener for receiving messages. */
    public void addIncomingMessageListener(MessageListener l) {
    	if (! messageHandlers.contains(l))
    		messageHandlers.add(l);
    }

    /** De-register a listener for receiving messages. */
    public boolean removeIncomingMessageListener(MessageListener l) {
   		return messageHandlers.remove(l);
    }
    
	/** Leaves the multicast group on all sockets. */
	public void dispose() {
		for (int i=0; i<multicastSockets.length; i++) {
		}
	}
	
	/** Starts one thread for each interface to listen on, if not
	 * already started.
	 * @see #multicastSockets
	 * @see #listenerThreads
	 */
	public void startListening() {
		if (listenerThreads == null) {
			listenerThreads = new Thread[multicastSockets.length];
			for (int i=0; i<multicastSockets.length; i++) {
				listenerThreads[i] = new Thread(new RunHelper(i));
				listenerThreads[i].start();
			}
		}
	}
	
	/** Signals all listening threads to stop and waits for each of them.
	 * @see #shouldExit
	 * @see #listenerThreads
	 */
	public void stopListening() {
		if (listenerThreads != null) {
			shouldExit = true;
			for (int i=0; i<listenerThreads.length; i++) {
				try {
					listenerThreads[i].join();
				}
				catch (InterruptedException e) {
					// just ignore
				}
			}
		}
	}
	
	private class RunHelper implements Runnable {
		private int myIndex;
		
		RunHelper(int myIndex) {
			this.myIndex = myIndex;
		}
		
		public void run() {
			logger.debug("Listener thread number " + myIndex + " starting");
			// to allow up to the maximum UDP packet size
			byte[] buffer = new byte[65535];
			while (! shouldExit) {
	            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
	            logger.debug("Received packet of length " + packet.getLength() + " from " + 
	            		packet.getAddress() + " at socket bound to " + packet.getSocketAddress());
	            
				try {
					multicastSockets[myIndex].receive(packet);
					
			    	if (messageHandlers != null) {
			    		for (ListIterator i = messageHandlers.listIterator(); i.hasNext(); ) {
			    			MessageListener l = (MessageListener) i.next(); 
			    			try {
			    				l.handleMessage(packet.getData(), packet.getAddress());
			    			}
			    			catch (Exception e) {
			    				logger.error("Incoming message handler '" + l + 
			    						"' caused exception '" + e + "', ignoring it here");
			    			}
			    		}
			    	}
					
				} catch (SocketTimeoutException e) {
					// just ignore
				} catch (IOException e) {
					logger.error("Could not receive from UDP socket: " + e);
				}
			}
			logger.debug("Listener thread number " + myIndex + " stopping");
		}
	}
}
