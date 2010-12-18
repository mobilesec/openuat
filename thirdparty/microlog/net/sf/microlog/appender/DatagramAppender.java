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

import java.io.IOException;

import javax.microedition.io.Connector;
import javax.microedition.io.Datagram;
import javax.microedition.io.DatagramConnection;

import net.sf.microlog.Level;
import net.sf.microlog.util.PropertiesGetter;

/**
 * An <code>Appender</code> that logs via UDP (Datagram) to a remote host.
 * Each logging sent to the host at the time of logging (no buffer). The class
 * uses a <code>Datagram</code> that is re-used and filled with new data each
 * time a message is sent.
 * 
 * This class requires MIDP 2.0 or better.
 * 
 * Note: This was from the beginning called GPRSAppender, which was contributed
 * by Marius de Beer.
 * 
 * @author Johan Karlsson (johan.karlsson@jayway.se)
 * @author Marius de Beer
 * @since 0.6
 */
public class DatagramAppender extends AbstractAppender {

	/**
	 * The property to set for the host to connect to.
	 */
	public static final String HOST_PROPERTY_STRING = "microlog.appender.DatagramAppender.host";

	/**
	 * The default host to connect to for logging.
	 */
	public static final String DEFAULT_HOST = "127.0.0.1";

	/**
	 * This is the default datagram size.
	 */
	public static final int DEFAULT_DATAGRAM_SIZE = 128;

	/**
	 * The default port to be used for logging.
	 */
	public static final int DEFAULT_PORT = 1023;

	private String host = DEFAULT_HOST;

	private int port = DEFAULT_PORT;

	private String encoding = "ASCII";

	private int datagramSize = DEFAULT_DATAGRAM_SIZE;

	protected DatagramConnection connection;

	/**
	 * This is one datagram that is used over and over again.
	 */
	private Datagram datagram;

	/**
	 * Create a DatagramAppender.
	 */
	public DatagramAppender() {
		super();
	}

	/**
	 * @see net.sf.microlog.appender.AbstractAppender#open()
	 */
	public void open() throws IOException {

		StringBuffer connectionStringBuffer = new StringBuffer(32);
		connectionStringBuffer.append("datagram://");
		connectionStringBuffer.append(host);
		connectionStringBuffer.append(':');
		connectionStringBuffer.append(port);
		connection = (DatagramConnection) Connector.open(connectionStringBuffer
				.toString());
		logOpen = true;

	}

	/**
	 * Do the logging.
	 * @param level
	 *            the level to use for the logging.
	 * @param message
	 *            the message to log.
	 * @param t
	 *            the exception to log.
	 */
	public void doLog(String name, long time, Level level, Object message, Throwable t) {
		if (logOpen && formatter != null) {
			String logMessage = formatter.format(name, time, level, message, t);
			sendMessage(logMessage);
		}
	}

	/**
	 * Send the message to the defined host.
	 * 
	 * @param message
	 *            the message to send.
	 */
	protected void sendMessage(String message) {
		try {
			if (datagram == null) {
				datagram = connection.newDatagram(datagramSize);
			}

			datagram.setData(message.getBytes(encoding), 0, message.length());
			connection.send(datagram);
		} catch (IOException e) {
			System.err.println("Could not send the Datagram: " + e);
		}
	}

	/**
	 * No Effect
	 * 
	 * @see net.sf.microlog.appender.AbstractAppender#clear()
	 */
	public void clear() {
	}

	/**
	 * @see net.sf.microlog.appender.AbstractAppender#close()
	 * @throws IOException
	 *             if the close operation failed.
	 */
	public void close() throws IOException {
		if (connection != null) {
			connection.close();
		}
		logOpen = false;
	}

	/**
	 * Get the size of the log. The size is the number of items logged.
	 * 
	 * @return the size of the log.
	 */
	public long getLogSize() {
		return SIZE_UNDEFINED;
	}

	/**
	 * Configure the DatagramAppender.
	 * <p>
	 * The host address can be passed with the property
	 * <code>microlog.appender.DatagramAppender.host</code>.
	 * 
	 * @param properties
	 *            the Properties to configure with
	 */
	public void configure(PropertiesGetter properties) {
		// Set the host address from Properties
		host = properties.getString(HOST_PROPERTY_STRING);
		if (host == null) {
			host = DEFAULT_HOST;
		}
	}

	/**
	 * Get the host to connect to.
	 * 
	 * @return the host to connect to.
	 */
	public String getHost() {
		return host;
	}

	/**
	 * Set the host to connect to. Note that this is the host without the port
	 * number.
	 * 
	 * @param host
	 *            the host to set
	 * @throws IllegalArgumentException
	 *             if the <code>host</code> is null.
	 * 
	 */
	public void setHost(String host) throws IllegalArgumentException {
		if (host == null) {
			throw new IllegalArgumentException("The host must not be null.");
		}

		this.host = host;
	}

	/**
	 * Get the port that is used for the connection.
	 * 
	 * @return the port
	 */
	public int getPort() {
		return port;
	}

	/**
	 * Set the port that is used for the connection.
	 * 
	 * @param port
	 *            the port to set
	 */
	public void setPort(int port) {
		this.port = port;
	}

	/**
	 * Set the encoding to be used when creating the byte array data that is
	 * sent in the <code>Datagram</code>.
	 * 
	 * @param encoding
	 *            the encoding to set
	 * @throws IllegalArgumentException
	 *             if the encoding is null or the length of the encoding
	 *             <code>String</code> is shorter than 1.
	 */
	public void setEncoding(String encoding) throws IllegalArgumentException {
		if (encoding == null || (encoding != null && encoding.length() < 1)) {
			throw new IllegalArgumentException(
					"The encoding must not be null and the length greater than 1");
		}

		this.encoding = encoding;
	}

	/**
	 * Set the size of the datagram that is created.
	 * 
	 * @param datagramSize
	 *            the datagramSize to set
	 * @throws IllegalArgumentException
	 *             if the length is less than 1
	 */
	public void setDatagramSize(int datagramSize)
			throws IllegalArgumentException {
		if (datagramSize < 1) {
			throw new IllegalArgumentException(
					"The datagram size must be greater than 1");
		}

		this.datagramSize = datagramSize;
	}

	/**
	 * Set the <code>DatagramConnection</code> to be used. The connection must
	 * be open. If the log is open this call is ignored.
	 * 
	 * This should only be used for testing purposes.
	 * 
	 * @param connection
	 *            the connection to set
	 */
	void setConnection(DatagramConnection connection) {
		if (!logOpen) {
			this.connection = connection;
		}
	}
}
