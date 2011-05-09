/* Copyright Rene Mayrhofer
 * File created 2005-09
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.util.test;

import org.openuat.authentication.exceptions.*;
import org.openuat.channel.main.HostServerBase;
import org.openuat.channel.main.ip.TCPPortServer;

import java.net.*;
import java.io.*;

import junit.framework.*;

public class TCPPortServerTest extends TestCase {
	public static final int PORT = 23456;

	private HostServerBase server = null;

	private Socket client = null;

	public TCPPortServerTest(String s) {
		super(s);
	}

	@Override
	public void setUp() throws IOException {
		server = new TCPPortServer(PORT, 10000, false, true);
		server.start();
	}

	@Override
	public void tearDown() throws IOException, InternalApplicationException {
		if (client != null)
			client.close();
		if (server != null)
			server.stop();
	}

	public void testCreateConnection() throws UnknownHostException, IOException {
		client = new Socket("localhost", PORT);
		Assert.assertTrue("Can't connect to server socket", client.isConnected());
	}
}
