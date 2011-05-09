/* Copyright Rene Mayrhofer
 * File created 2006-05-09
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.util.test;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;

import org.openuat.authentication.test.SimpleKeyAgreementTest;
import org.openuat.channel.main.MessageListener;
import org.openuat.channel.main.ip.UDPMulticastSocket;

import junit.framework.Assert;
import junit.framework.TestCase;

public class UDPMulticastSocketTest extends TestCase {
	public UDPMulticastSocketTest(String s) {
		super(s);
	}
	
	private static final int Port = 40000;
	
	private UDPMulticastSocket s;
	
	@Override
	public void setUp() throws IOException {
		s = new UDPMulticastSocket(Port, Port, "230.0.0.1");
	}
	
	@Override
	public void tearDown() {
		s.dispose();
		s = null;
	}
	
	public void testSendMulticast() throws IOException {
		s.sendMulticast(new byte[1]);
	}
	
	public void testSendUnicast() throws UnknownHostException, IOException {
		s.sendTo(new byte[1], InetAddress.getByName("127.0.0.1"));
	}
	
	public void testSendAndReceive_Unicast_Loopback() throws UnknownHostException, IOException, InterruptedException {
		ReceiveHelper r = new ReceiveHelper();
		s.addIncomingMessageListener(r);
		s.startListening();
		// this lets the threads start
		Thread.sleep(100);
		
		byte[] msg1 = new byte[] { 1, 2, 3, 4, 5, 6, 7, 9 };
		byte[] msg2 = new byte[] { 0xa, 0xb, 0xc, 0xd, 0xe, 0xf };
		
		LinkedList addrs = getAllAddresses();
		for (Iterator iter = addrs.iterator(); iter.hasNext(); ) {
			InetAddress addr = (InetAddress) iter.next();
			s.sendTo(msg1, addr); 
			// sleep to let the packets being delivered
			Thread.sleep(100);
			Assert.assertNotNull(r.lastMessage);
			Assert.assertTrue(SimpleKeyAgreementTest.compareByteArray(msg1, r.lastMessage));
			Assert.assertEquals(addr.getHostAddress(), r.lastSender.getHostAddress());
		}
		Assert.assertEquals(addrs.size(), r.numReceived);

		for (Iterator iter = addrs.iterator(); iter.hasNext(); ) {
			InetAddress addr = (InetAddress) iter.next();
			s.sendTo(msg2, addr); 
			// sleep to let the packets being delivered
			Thread.sleep(100);
			Assert.assertNotNull(r.lastMessage);
			Assert.assertTrue(SimpleKeyAgreementTest.compareByteArray(msg2, r.lastMessage));
			Assert.assertEquals(addr.getHostAddress(), r.lastSender.getHostAddress());
		}
		Assert.assertEquals(addrs.size()*2, r.numReceived);
		
		s.stopListening();
	}
	
	// TODO: this doesn't work - we don't receive our own packets even with setLoopback(true)
	/*public void testSendAndReceive_Multicast_Loopback() throws UnknownHostException, IOException, InterruptedException {
		ReceiveHelper r = new ReceiveHelper();
		s.addIncomingMessageListener(r);
		s.startListening();
		// this lets the threads start
		Thread.sleep(100);
		
		byte[] msg1 = new byte[] { 1, 2, 3, 4, 5, 6, 7, 9 };
		byte[] msg2 = new byte[] { 0xa, 0xb, 0xc, 0xd, 0xe, 0xf };
		
		s.sendMulticast(msg1);
		Thread.sleep(100);
		Assert.assertEquals(1, r.numReceived);
		Assert.assertTrue(SimpleKeyAgreementTest.compareByteArray(msg1, r.lastMessage));

		s.sendMulticast(msg2);
		Thread.sleep(100);
		Assert.assertEquals(2, r.numReceived);
		Assert.assertTrue(SimpleKeyAgreementTest.compareByteArray(msg2, r.lastMessage));
		
		s.stopListening();
	}*/

	private LinkedList getAllAddresses() throws SocketException {
		Enumeration ifaces = NetworkInterface.getNetworkInterfaces();
		LinkedList allAddrs = new LinkedList();
		while (ifaces.hasMoreElements()) {
			NetworkInterface iface = (NetworkInterface) ifaces.nextElement();
			boolean blacklisted = false;
			for (int i=0; i<UDPMulticastSocket.Interface_Names_Blacklist.length; i++) {
				if (iface.getName().startsWith(UDPMulticastSocket.Interface_Names_Blacklist[i])) {
					blacklisted = true;
				}
			}
				
			if (!blacklisted) {
				Enumeration addrs = iface.getInetAddresses();
				while (addrs.hasMoreElements()) {
					InetAddress addr = (InetAddress) addrs.nextElement();
					if (! (addr instanceof Inet6Address)) {
						allAddrs.add(addr);
					}
					// ignore IPv6 for now
					// TODO: test IPv6 handling 
				}
			}
		}
		return allAddrs;
	}
	
	private class ReceiveHelper implements MessageListener {
		byte[] lastMessage = null;
		InetAddress lastSender = null;
		int numReceived = 0;

		public void handleMessage(byte[] message, int offset, int length, Object sender) {
			numReceived++;
			System.out.println("Got message from " + sender + ": " + length + 
					" bytes (offset " + offset + "), numReceived=" + numReceived);
			lastMessage = new byte[length];
			System.arraycopy(message, offset, lastMessage, 0, length);
			lastSender = (InetAddress) sender;
		}
	}
}
