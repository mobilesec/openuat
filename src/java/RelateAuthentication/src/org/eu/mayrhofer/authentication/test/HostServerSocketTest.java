/* Copyright Rene Mayrhofer
 * File created 2005-09
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.authentication.test;

import org.eu.mayrhofer.authentication.*;
import org.eu.mayrhofer.authentication.exceptions.*;

import java.net.*;
import java.io.*;

import junit.framework.*;

public class HostServerSocketTest extends TestCase {
	public static final int PORT = 23456;

	private HostServerSocket server = null;

	private Socket client = null;

	public HostServerSocketTest(String s) {
		super(s);
	}

	public void setUp() throws IOException {
		server = new HostServerSocket(PORT, false, true);
		server.startListening();
	}

	public void tearDown() throws IOException, InternalApplicationException {
		if (client != null)
			client.close();
		if (server != null)
			server.stopListening();
	}

	public void testCreateConnection() throws UnknownHostException, IOException {
		client = new Socket("localhost", PORT);
		Assert.assertTrue("Can't connect to server socket", client.isConnected());
	}
}
