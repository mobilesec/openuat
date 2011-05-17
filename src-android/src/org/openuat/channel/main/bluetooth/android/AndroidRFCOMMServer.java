/* Copyright Michael Schöllhammer
 * Extended/cleaned up by Rene Mayrhofer
 * File created 2010-05
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.channel.main.bluetooth.android;

import java.io.IOException;
import java.util.UUID;

import org.openuat.authentication.exceptions.InternalApplicationException;
import org.openuat.channel.main.*;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

/**
 * This class represents an RFCOMM service which responds to incoming
 * authentication requests by delegating any incoming connection to the
 * HostProtocolHandler class. More specifically, for each incoming RFCOMM
 * connection, the HostProtocolHandler.startIncomingAuthenticationThread is
 * invoked with the connected RFCOMM stream connection.
 * 
 * @author Michael Schöllhammer
 * 
 */
public class AndroidRFCOMMServer extends HostServerBase {

	private BluetoothAdapter adapter;
	private BluetoothServerSocket serverSocket;
	private BluetoothSocket socket;
	public static UUID serviceUUID = UUID
			.fromString("550e8400-e29b-11d4-a716-446655440000");
	private String serviceName;
	private AndroidRFCOMMChannel androidRFCOMMChannel;

	/**
	 * Constructor
	 * 
	 * @param adapter
	 *            Bluetooth adapter
	 * @param serviceUUID
	 *            unique id for service
	 * @param serviceName
	 *            name of service
	 * @param protocolTimeoutMs
	 *            The maximum duration in milliseconds that this authentication
	 *            protocol may take before it will abort with an
	 *            AuthenticationFailed exception.
	 * @param keepConnected
	 *            indicates whether the connection should be kept after the
	 *            authentication
	 * @param useJSSE
	 *            If set to true, the JSSE API with the default JCE provider of
	 *            the JVM will be used for cryptographic operations. If set to
	 *            false, an internal copy of the Bouncycastle Lightweight API
	 *            classes will be used.
	 */
	public AndroidRFCOMMServer(BluetoothAdapter adapter, UUID serviceUUID,
			String serviceName, int protocolTimeoutMs, boolean keepConnected,
			boolean useJSSE) {
		super(keepConnected, useJSSE, protocolTimeoutMs);
		this.protocolTimeoutMs = protocolTimeoutMs;
		this.keepConnected = keepConnected;
		this.useJSSE = useJSSE;
		this.adapter = adapter;
		this.serviceName = serviceName;

		Log.i(this.getClass().toString(), "constructor");

		System.out.println(serviceUUID.toString());
	}

	/**
	 * get the server bluetooth socket from the adapter
	 */
	public void getBTServerSocket() {
		try {
			serverSocket = adapter.listenUsingRfcommWithServiceRecord(
					serviceName, serviceUUID);
		} catch (IOException e) {
			Log.e(this.getClass().toString(), "Unable to register RFCOMM socket with name '" +
					serviceName + "' and UUID " + serviceUUID, e);
		}
		Log.i(this.getClass().toString(), "getBTServerSocket");
	}

	/**
	 * starts the server
	 */
	@Override
	public void start() throws IOException {
		Log.i(this.getClass().toString(), "start");
		adapter.cancelDiscovery();

		getBTServerSocket();

		super.start();
	}

	/**
	 * This actually implements the listening for new RFCOMM channels.
	 */
	@Override
	public void run() {
		// socket = null;
		Log.i(this.getClass().toString(), "run");
		adapter.cancelDiscovery();

		Log.i(this.getClass().toString(),
				"Listening thread for RFCOMM service now running");
		try {
			while (running) {
				Log
						.i(this.getClass().toString(),
								"Listening thread for server socket waiting for connection");
				socket = serverSocket.accept();
				androidRFCOMMChannel = new AndroidRFCOMMChannel(socket);

				Log.i(this.getClass().toString(), androidRFCOMMChannel
						.getRemoteName());

				startProtocol(androidRFCOMMChannel);
			}
		} catch (IOException e) {
			Log.i(this.getClass().toString(), "Error in listening thread: " + e
					+ ". Stopping thread");
		}
		Log.i(this.getClass().toString(),
				"Listening thread for RFCOMM service now exiting");
		running = false;
	}

	/**
	 * close the server
	 */
	public void dispose() {
		try {
			cancel();
			stop();
		} catch (InternalApplicationException e) {
			Log.e(this.getClass().toString(), "Unable to close RFCOMM socket", e);
		}
		Log.i(this.getClass().toString(), "dispose");
	}

	/**
	 * close the server Bluetooth socket
	 */
	public void cancel() {
		try {
			serverSocket.close();
		} catch (IOException e) {
			Log.e(this.getClass().toString(), "Unable to close RFCOMM socket", e);
		}
		androidRFCOMMChannel.close();
		Log.i(this.getClass().toString(), "cancel");
	}
}
