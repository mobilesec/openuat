/* Copyright Rene Mayrhofer
 * File created 2006-05-05
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.authentication;

import java.net.DatagramSocket;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.LinkedList;

import org.apache.log4j.Logger;

public class UDPMulticastSocket {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(UDPMulticastSocket.class);

	//public final static String[] Interface_Names_Blacklist = new String[] { "vmnet", "lo" };
	
	private MulticastSocket socket;

	public UDPMulticastSocket(int port, boolean simulate) throws SocketException {
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
	
	public class MulticastCommunicationExample {
		 
	    static final String MULTICAST_GROUP = "224.59.10.1";
	 
	    static final int PORT = 2000;
	 
	    static final String EXIT_COMMAND = "QUIT";
	 
	    static final InetAddress GROUP_ADDRESS;
	    static {
	        try {
	            GROUP_ADDRESS = InetAddress.getByName(MULTICAST_GROUP);
	        } catch (UnknownHostException e) {
	            throw new RuntimeException(e);
	        }
	    }
	 
	    /**
	     * @param args
	     */
	    public static void main(String[] args) {
	 
	        Participant clientA = new Participant("A");
	        Participant clientB = new Participant("B");
	        Participant clientC = new Participant("C");	
	        Participant clientD = new Participant("D");
	        
	        final ExecutorService executorService = Executors.newFixedThreadPool(5);
	        executorService.execute(clientA);
	        executorService.execute(clientB);
	        executorService.execute(clientC);
	        executorService.execute(clientD);
	 
	        Timer pingTimer = new Timer(true);
	        pingTimer.schedule(new TimerTask() {
	            MulticastSocket multicastSocket;
	            {
	                try {
	                    multicastSocket = new MulticastSocket(PORT);
	                    multicastSocket.joinGroup(GROUP_ADDRESS);
	                    multicastSocket.setSoTimeout(30000);
	                } catch (SocketException e) {
	                    e.printStackTrace();
	                } catch (IOException e) {
	                    e.printStackTrace();
	                }
	            }
	 
	            public void run() {
	                try {
	                    sendMessageToGroup("timer ping: "
	                            + System.currentTimeMillis(), multicastSocket);
	                } catch (Exception e) {
	                    e.printStackTrace();
	                }
	            }
	        }, 1000L, 10000L);
	        
	        Timer shutdownTimer = new Timer(true);
	        shutdownTimer.schedule(new TimerTask() {
	            MulticastSocket multicastSocket;
	            {
	                try {
	                    multicastSocket = new MulticastSocket(PORT);
	                    multicastSocket.joinGroup(GROUP_ADDRESS);
	                    multicastSocket.setSoTimeout(30000);
	                } catch (SocketException e) {
	                    e.printStackTrace();
	                } catch (IOException e) {
	                    e.printStackTrace();
	                }
	            }
	 
	            public void run() {
	                try {
	                    sendMessageToGroup(EXIT_COMMAND, multicastSocket);
	                    executorService.shutdown();
	                } catch (Exception e) {
	                    e.printStackTrace();
	                }
	            }
	        }, 30000L);
	 
	    }
	 
	    static class Participant implements Runnable {
	 
	        MulticastSocket multicastSocket;
	 
	        String id;
	        
	        boolean quit;
	 
	        public Participant(String id) {
	            this.id = id;
	            try {
	                multicastSocket = new MulticastSocket(PORT);
	                multicastSocket.joinGroup(GROUP_ADDRESS);
	                multicastSocket.setSoTimeout(30000);
	            } catch (SocketException e) {
	                e.printStackTrace();
	            } catch (IOException e) {
	                e.printStackTrace();
	            }
	        }
	 
	        public void run() {
	            try {
	                sendMessageToGroup(this.id + " joins group", multicastSocket);
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
	                multicastSocket.leaveGroup(GROUP_ADDRESS);
	            } catch (Exception e) {
	                e.printStackTrace();
	            }
	        }
	    }
	 
	    public static void sendMessageToGroup(String message,
	            MulticastSocket multicastSocket) throws Exception {
	        byte[] data = message.getBytes("UTF-8");
	        DatagramPacket packet = new DatagramPacket(data, 0, data.length);
	        packet.setAddress(GROUP_ADDRESS);
	        packet.setPort(PORT);
	        multicastSocket.send(packet);
	    }
	} 
	
	
	public class MulticastSender 
	{
		public static void main( String[] argv ) 
		{
			try 
			{
				// get the InetAddress of the MCAST group 
				InetAddress ia = InetAddress.getByName( argv[0] );

				// get the port that the MCAST group members will be listening on
				int recvPort = Integer.parseInt( argv[1] );

				// create a datagram with a suitable message
				String str = "Hello from: "+InetAddress.getLocalHost();
				byte[] data = str.getBytes();
				DatagramPacket dp = new DatagramPacket(data, data.length, ia, recvPort);

				// create a multicast socket bound to any local port
				MulticastSocket ms = new MulticastSocket();

				//Join the multicast group
				ms.joinGroup(ia); 

				// send the message with a Time-To-Live (TTL)=1
				ms.send(dp, (byte)1); 

				// tidy up - leave the group and close the socket
				ms.leaveGroup(ia);
				ms.close();
			} 
			catch (IOException e) {}
		}
	}

	
	public class MulticastReceiver
	{
		public static void main( String[] argv ) 
		{
			try 
			{
				// get the InetAddress of the MCAST group 
				InetAddress ia = InetAddress.getByName( argv[0] );

				// get the port that we will be listening on
				int port = Integer.parseInt( argv[1] );

				// create a multicast socket on the specified local port number
				MulticastSocket ms = new MulticastSocket( port );

				// create an empty datagram packet
				DatagramPacket dp = new DatagramPacket(new byte[128], 128);

				//Join a multicast group and wait for some action
				ms.joinGroup(ia); 
				System.out.println( "waiting for a packet from "+ia+"...");
				ms.receive(dp);

				// print out what we received and quit
				System.out.println( new String(dp.getData() ));

				ms.leaveGroup(ia);
				ms.close();
			} 
			catch (IOException e) {}
		}
	}


}
