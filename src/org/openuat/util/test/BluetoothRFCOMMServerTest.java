/* Copyright Rene Mayrhofer
 * File created 2007-02-18
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.util.test;

import java.io.IOException;

import javax.bluetooth.LocalDevice;
import javax.bluetooth.UUID;

import junit.framework.Assert;

import org.openuat.channel.main.HostServerBase;
import org.openuat.channel.main.bluetooth.BluetoothSupport;
import org.openuat.channel.main.bluetooth.jsr82.BluetoothRFCOMMChannel;
import org.openuat.channel.main.bluetooth.jsr82.BluetoothRFCOMMServer;

public class BluetoothRFCOMMServerTest extends BluetoothEmulatorBase {
	private static final int CHANNEL = 9;
	
	private static final UUID SERVICE_UUID = new UUID(0x2108); /*UUID("3f6d7392984445c18a0256801765e2f0", false);*/
	
	private static final String SERVICE_NAME = "JUnit Test Service";
	
	private HostServerBase server = null;

	private BluetoothRFCOMMChannel client = null;

	private boolean haveBTSupport;

	public BluetoothRFCOMMServerTest(String s) {
		super(s);
	}

	@Override
	public void setUp() throws Exception {
		try {
			haveBTSupport = BluetoothSupport.init();
		} catch (UnsatisfiedLinkError e) {
			System.err.println("Unable to load native Bluetooth support: " + e.toString());
			System.err.println("Skipping Bluetooth tests");
			return;
		}

		// NOTE: with the BlueCove emulator, channels can't be explicitly registered?
		server = new BluetoothRFCOMMServer(null, /*new Integer(CHANNEL),*/ 
				SERVICE_UUID, SERVICE_NAME, 
				10000, false, true);
		super.setUp();
		// TODO: enable again when the BlueCove emulator works from within ant
		// it does work when calling junit on the commandline
		//server.start();
	}

	@Override
	public void tearDown() throws Exception {
		if (client != null)
			client.close();
		if (server != null)
			server.stop();
		// make the thread exit
		synchronized (server) {
			HostServerBase tmp = server;
			server = null;
			tmp.notify();
		}
		super.tearDown();
	}

	public void testCreateConnection() throws IOException {
		if (!haveBTSupport) return;

		// TODO: enable when we find out why it fails
/*		client = new BluetoothRFCOMMChannel(LocalDevice.getLocalDevice().getBluetoothAddress(), CHANNEL);
		Assert.assertNotNull("Can't connect to server channel", client.getInputStream());
		Assert.assertNotNull("Can't connect to server channel", client.getOutputStream());
*/	}

	@Override
	protected Runnable getServerThread() {
		// return a very simple implementation that will keep the emulator running
		// as long as our server is active
		return new Runnable() {
			public void run() {
				while (server != null) {
					try {
						synchronized (server) { server.wait(1000); }
					} catch (InterruptedException e) {
						// just ignore, non-fatal
					}
				}
			}
		};
	}
}
