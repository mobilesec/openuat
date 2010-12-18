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

package net.sf.microlog.appender;

import java.io.DataOutputStream;
import java.io.IOException;

import javax.microedition.io.CommConnection;
import javax.microedition.io.Connector;

import net.sf.microlog.Appender;
import net.sf.microlog.Level;
import net.sf.microlog.util.PropertiesGetter;

/**
 * The <code>SerialAppender</code> uses a serial port (<code>CommConnection</code>)
 * to do the logging. The serial port could be opened via Bluetooth, IR or USB,
 * depending on the target platform. If you have access to JSR-82 it is
 * recommended that the <code>BluetoothSerialAppender</code> is used.
 * 
 * This requires MIDP 2.0.
 * 
 * @author Johan Karlsson (johan.karlsson@jayway.se)
 * @since 0.9
 */
public class SerialAppender extends AbstractAppender {

	public static final String COM_PORT_PROPERTY = "net.sf.microlog.appender.SerialAppender.comPort";

	public final static String DEFAULT_COM_PORT = "AT1";

	private final static String COMM_SCHEME = "comm:";

	private String comPort;
	private CommConnection connection;
	private DataOutputStream dataOutputStream;

	/**
	 * @param comPort
	 *            the comPort to set
	 * @throws IllegalArgumentException
	 *             if the <code>comPort</code> is null.
	 */
	public void setComPort(String comPort) throws IllegalArgumentException {
		if (comPort == null) {
			throw new IllegalArgumentException("The comPort must not be null.");
		}

		this.comPort = comPort;
	}

	/**
	 * Open the log, i.e. it opens the <code>CommConnection</code>.
	 * 
	 * @see net.sf.microlog.appender.AbstractAppender#open()
	 */
	public void open() throws IOException {

		// If no serial port is defined, use the first available port
		if (comPort == null) {
			String ports = System.getProperty("microedition.commports");
			System.out.println("Available ports: " + ports);
			int comma = ports.indexOf(',');
			if (comma > 0) {
				// Parse the first port from the available ports list.
				comPort = ports.substring(0, comma);
			} else {
				// Only one serial port available.
				comPort = ports;
			}
		}

		String connectionString = SerialAppender.COMM_SCHEME + comPort;
		connection = (CommConnection) Connector.open(connectionString);
		dataOutputStream = connection.openDataOutputStream();
		logOpen = true;
	}

	/**
	 * Clear the log. In this case we do nothing.
	 * 
	 * @see net.sf.microlog.appender.AbstractAppender#clear()
	 */
	public void clear() {
		// Do nothing
	}

	/**
	 * 
	 * @see net.sf.microlog.appender.AbstractAppender#close()
	 */
	public void close() throws IOException {

		if (dataOutputStream != null) {
			dataOutputStream.close();
		}

		if (connection != null) {
			connection.close();
		}

		logOpen = false;

	}

	/**
	 * Get the log size which is always <code>Appender.SIZE_UNDEFINED</code>.
	 * 
	 * @see net.sf.microlog.Appender#getLogSize()
	 */
	public long getLogSize() {
		return Appender.SIZE_UNDEFINED;
	}

	/**
	 * (non-Javadoc)
	 * 
	 * @see net.sf.microlog.appender.AbstractAppender#doLog(String,
	 *      long, net.sf.microlog.Level, java.lang.Object, java.lang.Throwable)
	 */
	public void doLog(String name, long time, Level level, Object message, Throwable t) {
		if (logOpen && formatter != null) {
			try {
				dataOutputStream.writeUTF(formatter.format(name, time,
						level, message, t));
				dataOutputStream.flush();
			} catch (IOException e) {
				System.err.println("Failed to write log data. " + e);
			}
		}
	}

	/**
	 * Configure the <code>SerialAppender</code>.
	 * 
	 * @see net.sf.microlog.Appender#configure(net.sf.microlog.util.PropertiesGetter)
	 */
	public void configure(PropertiesGetter properties)
			throws IllegalArgumentException {
		if (properties == null) {
			throw new IllegalArgumentException(
					"The properties must not be null.");
		}

		String newComPort = properties.getString(COM_PORT_PROPERTY);
		if (newComPort != null) {
			this.comPort = newComPort;
		}

	}

}
