/*
 * Copyright 2008 The Microlog project @sourceforge.net
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.sf.microlog.bluetooth;

import java.io.DataInputStream;
import java.io.IOException;

import javax.bluetooth.BluetoothStateException;
import javax.bluetooth.DiscoveryAgent;
import javax.bluetooth.LocalDevice;
import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.StreamConnectionNotifier;

import net.sf.microlog.util.MicrologConstants;

/**
 * The <code>BluetoothSerialServerThread</code> is used for receiving data
 * from a <code>BluetoothSerialAppender</code>. It is intended to be used for
 * servers implemented both in Java & in Java ME (CLDC).
 * 
 * @author Johan Karlsson (johan.karlsson@jayway.se)
 * @since 0.9
 */
public class BluetoothSerialServerThread extends Thread {

	private BluetoothMessageReceiver messageReceiver;

	/**
	 * Create a <code>BluetoothSerialServerThread</code> object.
	 */
	public BluetoothSerialServerThread() {
		super("BluetoothSerialServerThread");
	}

	/**
	 * Set the <code>BluetoothMessageReceiver</code> that is notified every
	 * time a message is received from the client.
	 * 
	 * @param messageReceiver
	 *            the messageReceiver to be notified.
	 */
	public void setMessageReceiver(BluetoothMessageReceiver messageReceiver) {
		this.messageReceiver = messageReceiver;
	}

	/**
	 * Implementation of the <code>Runnable</code> interface.
	 */
	public void run() {

		System.out.println("BluetoothSerialServerThread started.");

		StreamConnectionNotifier notifier = null;
		StreamConnection connection = null;
		DataInputStream dataInputStream = null;

		try {
			// Make the local device discoverable for the
			// client to locate
			LocalDevice local = LocalDevice.getLocalDevice();
			if (!local.setDiscoverable(DiscoveryAgent.GIAC)) {
				System.out.println("Failed to change to the "
						+ "discoverable mode");
				return;
			}

			notifier = (StreamConnectionNotifier) Connector
					.open("btspp://localhost:"
							+ MicrologConstants.DEFAULT_BT_UUID_STRING);

			connection = notifier.acceptAndOpen();
			dataInputStream = connection.openDataInputStream();
			
			System.out.println("Start to read the input from the client.");
			boolean stopReading = false;
			String message = dataInputStream.readUTF();
			
			while (!stopReading && message != null) {
				System.out.println(message);
				if (dataInputStream != null) {
					message = dataInputStream.readUTF();

					if (message != null && message.compareTo("[STOP]") == 0) {
						stopReading = true;
					}

					if (message != null && messageReceiver != null) {
						messageReceiver.messageReceived(message);
					}
				}
			}

		} catch (BluetoothStateException e) {
			System.err.println("Failed to init the Bluetooth connection. " + e);
		} catch (IOException e) {
			System.err
					.println("Failed data from the client. It is probably disconnected. "
							+ e);
		} finally {
			System.out.println("We are finally closing the logging.");
			try {
				if (dataInputStream != null) {
					dataInputStream.close();
				}

				if (connection != null) {
					connection.close();
				}

				if (notifier != null) {
					notifier.close();
				}
			} catch (IOException e) {
				System.err.println("Failed to close: " + e);
			}

		}
	}

}
