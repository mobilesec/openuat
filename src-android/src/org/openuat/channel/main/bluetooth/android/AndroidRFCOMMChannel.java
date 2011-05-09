package org.openuat.channel.main.bluetooth.android;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Vector;

import org.openuat.channel.main.RemoteConnection;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

/**
 * class which is used to open an RFCOMM channel to a Bluetooth device.
 * 
 * @author Michael Schöllhammer
 * 
 */
public class AndroidRFCOMMChannel implements RemoteConnection {

	private BluetoothAdapter adapter;
	private BluetoothSocket socket;
	private BluetoothDevice device;
	private static Vector openChannels;
	private String remoteDeviceAddress;

	private InputStream fromRemote = null;
	private OutputStream toRemote = null;

	/**
	 * Constructor
	 * 
	 * @param device
	 *            device to connect to
	 */
	public AndroidRFCOMMChannel(BluetoothDevice device) {
		openChannels = new Vector();
		this.device = device;

		try {
			socket = device
					.createRfcommSocketToServiceRecord(AndroidRFCOMMServer.serviceUUID);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		remoteDeviceAddress = device.getAddress();
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
		openChannels = new Vector();
		device = socket.getRemoteDevice();
		remoteDeviceAddress = device.getAddress();
		adapter = BluetoothAdapter.getDefaultAdapter();

		Log.i(this.getClass().toString(), "constructor: channel by socket");
	}

	/**
	 * returns the InputStream to read from the remote side.
	 */
	@Override
	public InputStream getInputStream() throws IOException {
		// TODO Auto-generated method stub
		return socket.getInputStream();
	}

	/**
	 * returns the OuputStream to write to the remote side.
	 */
	@Override
	public OutputStream getOutputStream() throws IOException {
		// TODO Auto-generated method stub
		return socket.getOutputStream();
	}

	/**
	 * returns the address of the remote host
	 */
	@Override
	public Object getRemoteAddress() throws IOException {
		// TODO Auto-generated method stub
		return socket.getRemoteDevice().getAddress();
	}

	/**
	 * returns the name of the remote host
	 */
	@Override
	public String getRemoteName() {
		// TODO Auto-generated method stub
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
			// TODO Auto-generated catch block
			e.printStackTrace();
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
		// TODO Auto-generated method stub
		Log.i(this.getClass().toString(), "close");
		try {
			socket.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
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
		// TODO Auto-generated method stub
		return socket != null;
	}

}
