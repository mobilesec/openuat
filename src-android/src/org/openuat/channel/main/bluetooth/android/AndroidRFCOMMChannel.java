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
import java.io.InputStream;
import java.io.OutputStream;

import org.openuat.channel.main.RemoteConnection;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

/**
 * class which is used to open an RFCOMM channel to a Bluetooth device.
 * 
 * @author Michael Schöllhammer, Rene Mayrhofer
 * 
 */
public class AndroidRFCOMMChannel implements RemoteConnection {

	private BluetoothAdapter adapter;
	private BluetoothSocket socket;
	private BluetoothDevice device;

	private InputStream fromRemote = null;
	private OutputStream toRemote = null;

	/**
	 * Constructor
	 * 
	 * @param device
	 *            device to connect to
	 */
	public AndroidRFCOMMChannel(BluetoothDevice device) {
		this.device = device;

		try {
			socket = device
					.createRfcommSocketToServiceRecord(AndroidRFCOMMServer.serviceUUID);
		} catch (IOException e) {
			Log.e(this.getClass().toString(), "Unable to create Bluetooth RFCOMM socket to device " + device, e);
		}
		adapter = BluetoothAdapter.getDefaultAdapter();

		Log.i(this.getClass().toString(), "constructor: channel by device");
	}

	/**
	 * Constructor used for creating a RFCOMM channel with a socket
	 * 
	 * @param s
	 *            Bluetooth socket
	 */
	public AndroidRFCOMMChannel(BluetoothSocket s) {
		socket = s;
		adapter = BluetoothAdapter.getDefaultAdapter();

		Log.i(this.getClass().toString(), "constructor: channel by socket");
	}

	/**
	 * returns the InputStream to read from the remote side.
	 */
	@Override
	public InputStream getInputStream() throws IOException {
		return socket.getInputStream();
	}

	/**
	 * returns the OuputStream to write to the remote side.
	 */
	@Override
	public OutputStream getOutputStream() throws IOException {
		return socket.getOutputStream();
	}

	/**
	 * returns the address of the remote host
	 */
	@Override
	public Object getRemoteAddress() throws IOException {
		return socket.getRemoteDevice().getAddress();
	}

	/**
	 * returns the name of the remote host
	 */
	@Override
	public String getRemoteName() {
		return socket.getRemoteDevice().getName();
	}

	/**
	 * Opens a channel.
	 * The method is synchronized, because opening might block for some time.
	 */
	@Override
	public boolean open() throws IOException {
		Log.i(this.getClass().toString(), "open");
		adapter.cancelDiscovery();

		try {
			socket.connect();
		} catch (IOException e) {
			Log.e(this.getClass().toString(), "Unable to open socket to device " + device, e);
			return false;
		}

		fromRemote = socket.getInputStream();
		toRemote = socket.getOutputStream();
		return true;
	}

	/**
	 * Closes the channel. 
	 */
	@Override
	public void close() {
		Log.i(this.getClass().toString(), "close");
		try {
			socket.close();
		} catch (IOException e) {
			Log.i(this.getClass().toString(), e.getMessage());
		}
		try {
			if (fromRemote != null) {
				fromRemote.close();
			}
			if (toRemote != null) {
				toRemote.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		fromRemote = null;
		toRemote = null;
	}

	/**
	 * Returns true if the connection is open in both directions,
	 * false otherwise.
	 */
	@Override
	public boolean isOpen() {
		return socket != null;
	}
}
