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

import org.openuat.authentication.exceptions.InternalApplicationException;
import org.openuat.util.BluetoothRFCOMMChannel;
import org.openuat.util.BluetoothRFCOMMServer;
import org.openuat.util.HostServerBase;

import junit.framework.TestCase;

public class BluetoothRFCOMMServerTest extends TestCase {
	private static final int CHANNEL = 9;
	
	private static final UUID SERVICE_UUID = new UUID("3f6d7392984445c18a0256801765e2f0", false);
	
	private static final String SERVICE_NAME = "JUnit Test Service";
	
	private HostServerBase server = null;

	private BluetoothRFCOMMChannel client = null;

	public BluetoothRFCOMMServerTest(String s) {
		super(s);
	}

	@Override
	public void setUp() throws IOException {
		server = new BluetoothRFCOMMServer(new Integer(CHANNEL), SERVICE_UUID, SERVICE_NAME, 
				10000, false, true);
		server.start();
	}

	@Override
	public void tearDown() throws IOException, InternalApplicationException {
		if (client != null)
			client.close();
		if (server != null)
			server.stop();
	}

	public void testCreateConnection() throws IOException {
		// TODO: loopback connections seem not to work, hmm - need to find some was to test
		client = new BluetoothRFCOMMChannel(LocalDevice.getLocalDevice().getBluetoothAddress(), CHANNEL);
/*		Assert.assertNotNull("Can't connect to server channel", client.getInputStream());
		Assert.assertNotNull("Can't connect to server channel", client.getOutputStream());*/
	}
}
