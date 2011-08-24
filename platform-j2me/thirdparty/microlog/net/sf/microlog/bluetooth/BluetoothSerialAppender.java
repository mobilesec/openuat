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

import java.io.DataOutputStream;
import java.io.IOException;

import javax.bluetooth.BluetoothStateException;
import javax.bluetooth.DiscoveryAgent;
import javax.bluetooth.LocalDevice;
import javax.bluetooth.ServiceRecord;
import javax.bluetooth.UUID;
import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;

import net.sf.microlog.Appender;
import net.sf.microlog.Level;
import net.sf.microlog.appender.AbstractAppender;
import net.sf.microlog.util.MicrologConstants;

/**
 * The <code>BluetoothSerialAppender</code> log using a Bluetooth serial
 * connection (btspp).
 * 
 * @author Johan Karlsson (johan.karlsson@jayway.se)
 * @since 0.6
 */
public class BluetoothSerialAppender extends AbstractAppender {

	private StreamConnection connection;
	private DataOutputStream dataOutputStream;

	/**
	 * Clear the log. This has not affect for this appender.
	 */
	public void clear() {
		// Do nothing
	}

	/**
	 * Close the log.
	 */
	public synchronized void close() throws IOException {

		if (dataOutputStream != null) {
			try {
				dataOutputStream
						.writeUTF(MicrologConstants.STOP_LOGGING_COMMAND_STRING);
				dataOutputStream.flush();
				dataOutputStream.close();
			} catch (IOException e) {
				System.err
						.println("Failed to terminate the dataOutputStream in a controlled way."
								+ e);
			}
		}

		if (connection != null) {
			try {
				connection.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		logOpen = false;
	}

	public synchronized void doLog(String name, long time, Level level,
			Object message, Throwable t) {
		if (logOpen && formatter != null) {
			try {
				dataOutputStream.writeUTF(formatter.format("", time,
						level, message, t));
				dataOutputStream.flush();
			} catch (IOException e) {
				System.err.println("Unable to log to the output stream. " + e);
			}
		}
	}

	/**
	 * Open the log, i.e. open the Bluetooth connection to the log server.
	 */
	public synchronized void open() throws IOException {

		try {
			// Retrieve the connection string to connect to
			// the server
			LocalDevice local = LocalDevice.getLocalDevice();
			DiscoveryAgent agent = local.getDiscoveryAgent();

			String connectionString = agent.selectService(new UUID(
					MicrologConstants.DEFAULT_BT_UUID_STRING, false),
					ServiceRecord.NOAUTHENTICATE_NOENCRYPT, false);

			if (connectionString != null) {
				try {
					connection = (StreamConnection) Connector
							.open(connectionString);
					dataOutputStream = connection.openDataOutputStream();

					logOpen = true;
				} catch (IOException e) {
					System.err
							.println("Failed to connect to the Bluetooth log server with connection string "
									+ connectionString + " " + e);
				}
			} else {
				System.err.println("Did not find any Microlog service.");
			}
		} catch (BluetoothStateException e) {
			System.err
					.println("Failed to connect to the Bluetooth log server. "
							+ e);
		}
	}

	/**
	 * Get the size of the. Always returns <code>Appender.SIZE_UNDEFINED</code>.
	 */
	public long getLogSize() {
		return Appender.SIZE_UNDEFINED;
	}
}
