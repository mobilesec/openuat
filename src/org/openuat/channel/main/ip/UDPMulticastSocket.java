/* Copyright Rene Mayrhofer
 * File created 2006-05-05
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.channel.main.ip;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.util.BitSet;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.openuat.channel.main.MessageListener;

/** This class offers unicast and multicast UDP communication. It binds one
 * MulticastSocket to each network interface address found in the system and sends
 * multicast packets via all of them. Received packets can be either unicast or
 * multicast, and a method to send only unicast packets is also offered.
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class UDPMulticastSocket {
	/** Our logger. */
	private static Logger logger = LoggerFactory.getLogger(UDPMulticastSocket.class.getName());

	/** A list of network interface names <b>not</b> to use for communication.
	 * All other network interfaces not matching these names are used to multicast
	 * messages.
	 */
	public final static String[] Interface_Names_Blacklist = new String[] { "vmnet", "lo" };
	
	/** Let each receive() call time out after 500ms to allow graceful shutdown of the
	 * receiving threads.
	 */
	private final static int Timeout_Receive = 500;

    /** Holds one socket for each network address in the system. With these sockets, 
     * multicast packets are sent for the respective interfaces that the sockets are bound to. 
     * These sockets are bound to a specific address, but to some random port.
     * @see #sendMulticast(byte[])
     */
	private MulticastSocket[] multicastSendSockets;
	
	/** With this socker, both unicast and multicast packets are received.
	 * This socket is bound to the receive port.
     * @see RunHelper#run()
	 */ 
	private MulticastSocket multicastReceiveSocket;
	
	/** This bit set marks those elements in multicastSockets where the underlying address
	 * (to which the socket is bound) is an IP alias. The first address that is reported
	 * for each interface (this default address is system dependent) is not marked as an alias,
	 * so that every interface will have exactly one multicastSocket that is not marked in this
	 * bit set. Sending multicast packets will only use those sockets and thus send only one
	 * multicast packet on each interface, even when some have multiple IP addresses.
	 * 
	 * It should really be possible to bind a socket to an interface, and not an address... 
	 * 
	 * @see #multicastSockets
	 */
	private BitSet addressIsAlias = new BitSet();
	
	/** This single socket is used to send unicast packets, using the system routing table.
	 * This socket is not bound at all.
	 * @see #sendTo(byte[], InetAddress) 
	 */
	private DatagramSocket unicastSendSocket;
	
	/** The port number used for sending packets. It's the one passed to the constructor. */
	private int sendPort;

	/** The port number used for receiving packets. It's the one passed to the constructor. */
	private int receivePort;
	
	/** The multicast group address to send to when sending multicast packets. */
	private InetAddress groupAddress;

	/** The list of listeners that are notified of incoming messages. */
    private LinkedList messageHandlers = new LinkedList();

    /** The thread to receive packets. It will continuously call receive() on the socket 
     * and forward all packets to all registered listeners.
     * @see #startListening()
     * @see #stopListening()
     * @see #shouldExit 
     */
    private Thread listenerThread = null;
    
    /** Set to true to signal the listener thread to exit. 
     * @see #stopListening() 
     */
    private boolean shouldExit = false;
    
    /** Creates an UDPMulticastSocket object.
     * 
     * @param port The UDP port to use for communication.
     * @param multicastGroup The multicast group to use.
     */
	public UDPMulticastSocket(int receivePort, int sendPort, String multicastGroup) throws IOException {
		logger.debug("Constructing UDPMulticastSocket with receive port " + receivePort + 
				", send port " + sendPort + ", multicast group " + multicastGroup);
		this.receivePort = receivePort;
		this.sendPort = sendPort;
		
		unicastSendSocket = new DatagramSocket();
		// receive should actually never be called on this socket, but set it so that it won't be infinite in any case
		unicastSendSocket.setSoTimeout(Timeout_Receive);

		groupAddress = InetAddress.getByName(multicastGroup);

		multicastReceiveSocket = new MulticastSocket(this.receivePort) ;
		multicastReceiveSocket.setSoTimeout(Timeout_Receive);
		boolean usingMulticast;
		if (groupAddress.isMulticastAddress()) {
			multicastReceiveSocket.joinGroup(groupAddress);
			usingMulticast = true;
		}
		else {
			logger.warn("Address " + multicastGroup + " is not a multicast address, not joining group");
			usingMulticast = false;
		}
		
		// create one multicast socket for each address
		Enumeration ifaces = NetworkInterface.getNetworkInterfaces();
		LinkedList allAddrs = new LinkedList();
		int addressIndex = 0;
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
				boolean alreadyAddedAddr = false;
				while (addrs.hasMoreElements()) {
					InetAddress addr = (InetAddress) addrs.nextElement();
					if (addr instanceof Inet6Address) {
						logger.debug("Ignoring IPv6 address " + addr + " for now");
					} else {
						logger.debug("Found address " + addr);
						allAddrs.add(addr);
						// if this is not the first address on the interface, mark it as alias
						if (alreadyAddedAddr)
							addressIsAlias.set(addressIndex);
						addressIndex++;
						alreadyAddedAddr = true;
					}
				}
			} else {
				logger.debug("Ignoring interface because it is blacklisted");
			}
		}
		
		// start the responders
		if (usingMulticast) {
			logger.debug("Using " + allAddrs.size() + " addresses, starting one multicast sending socket for each");

			multicastSendSockets = new MulticastSocket[allAddrs.size()];
			Iterator iter = allAddrs.iterator();
			for (int i=0; i<multicastSendSockets.length; i++) {
				InetAddress addr = (InetAddress) iter.next();
				// bind to a specific address, but to some random port
				multicastSendSockets[i] = new MulticastSocket(new InetSocketAddress(addr, 0));
				multicastSendSockets[i].setSoTimeout(Timeout_Receive);

				// loopback is not needed, multicast packets seem to be received anyway
				/*if (loopbackToLocalhost) {
					multicastSendSockets[i].setLoopbackMode(true);
					if (!multicastSendSockets[i].getLoopbackMode())
						logger.warn("Could not set loopback mode, own packets will not be seen by localhost");
				 }*/
			}
		}
		else {
			logger.warn("Not using multicast group to send to, therefore not binding to specific local addresses");
			
			multicastSendSockets = new MulticastSocket[1];
			multicastSendSockets[0] = new MulticastSocket();
			multicastSendSockets[0].setSoTimeout(Timeout_Receive);
		}
	}
	
	/** Sends a multicast message to the group. This will send at exactly one packet 
	 * on each interface.
	 * @param message The message to send.
	 * @see #addressIsAlias
	 */
	public void sendMulticast(byte[] message) throws IOException {
        DatagramPacket packet = new DatagramPacket(message, 0, message.length);
		packet.setAddress(groupAddress);
		packet.setPort(sendPort);
		for (int i=0; i<multicastSendSockets.length; i++) {
			if (! addressIsAlias.get(i)) {
				logger.debug("Sending packet with " + message.length + " bytes to multicast group " + 
						groupAddress + ", port " + sendPort + " on multicast socket bound to address " + 
						multicastSendSockets[i].getLocalAddress());
				multicastSendSockets[i].send(packet);
			}
			else {
				logger.debug("Not using multicast socket bound to alias address " + 
						multicastSendSockets[i].getLocalAddress());
			}
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
		packet.setPort(sendPort);
		logger.debug("Sending packet with " + message.length + " bytes to address " + 
				target + ", port " + sendPort);
		unicastSendSocket.send(packet);
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
		stopListening();
		try {
			if (groupAddress.isMulticastAddress()) {
				multicastReceiveSocket.leaveGroup(groupAddress);
			}
		}
		catch (IOException e) {
			logger.warn("Could not properly leave multicast group " + groupAddress + ": " + e);
		}
	}
	
	/** Starts one thread for each interface to listen on, if not
	 * already started.
	 * @see #multicastSockets
	 * @see #listenerThreads
	 */
	public void startListening() {
		if (listenerThread == null) {
			listenerThread = new Thread(new RunHelper());
			listenerThread.start();
		}
	}
	
	/** Signals all listening threads to stop and waits for each of them.
	 * @see #shouldExit
	 * @see #listenerThreads
	 */
	public void stopListening() {
		if (listenerThread != null) {
			shouldExit = true;
			try {
				listenerThread.join();
			}
			catch (InterruptedException e) {
				// just ignore
			}
		}
	}
	
	private class RunHelper implements Runnable {
		public void run() {
			logger.debug("Listener thread starting");
			// to allow up to the maximum UDP packet size
			byte[] buffer = new byte[65535];
			while (! shouldExit) {
	            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
	            
				try {
					multicastReceiveSocket.receive(packet);
		            logger.debug("Received packet of length " + packet.getLength() + " from " + 
		            		packet.getAddress() + " at socket bound to " + packet.getSocketAddress() +
		            		", port " + receivePort);
					
			    	if (messageHandlers != null) {
			    		for (ListIterator i = messageHandlers.listIterator(); i.hasNext(); ) {
			    			MessageListener l = (MessageListener) i.next(); 
			    			try {
			    				l.handleMessage(packet.getData(), packet.getOffset(), 
			    						packet.getLength(), packet.getAddress());
			    			}
			    			catch (Exception e) {
			    				String stackTrace = "";
			    				if (logger.isDebugEnabled()) {
			    					for (int j=0; j<e.getStackTrace().length; j++)
			    						stackTrace += e.getStackTrace()[j].toString() + "\n";
			    				}
			    				logger.error("Incoming message handler '" + l + 
			    						"' caused exception '" + e + "\n" + stackTrace + "', ignoring it here");
			    			}
			    		}
			    	}
					
				} catch (SocketTimeoutException e) {
					// just ignore
				} catch (IOException e) {
					logger.error("Could not receive from UDP socket: " + e);
				}
			}
			logger.debug("Listener thread stopping");
		}
	}
}
