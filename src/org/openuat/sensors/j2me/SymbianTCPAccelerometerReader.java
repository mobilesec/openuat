/* File created 2007-05-04
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.sensors.j2me;

import java.io.DataInputStream;
import java.io.IOException;

import javax.microedition.io.Connector;
import javax.microedition.io.ServerSocketConnection;
import javax.microedition.io.StreamConnection;

import org.apache.log4j.Logger;
import org.openuat.sensors.SamplesSource;

/** This class implements an accelerometer sensor reader that gets its data 
 * from a small Symbian/C++ wrapper around the Nokia sensor SDK. It opens a
 * TCP socket to which the Symbian program will connect. In the future, this
 * should be the other way around with the Symbian wrapper being a service.
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class SymbianTCPAccelerometerReader extends SamplesSource {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger("org.openuat.sensors.j2me.SymbianTCPAccelerometerReader" /*SymbianTCPAccelerometerReader.class*/);

	/** The TCP port used for connecting to the sensor wrapper. */
	private int port;
	
	private ServerSocketConnection server = null;

	private StreamConnection sensorConnector = null;
	private DataInputStream sensorDataIn = null;
	private byte[] bytes = new byte[7];
	
	/** Initializes the reader.
	 * 
	 * @param port The TCP port used for connecting to the sensor wrapper.
	 * @throws IOException when the socket can not be opened. 
	 */
	public SymbianTCPAccelerometerReader(int port) throws IOException {
		/* The accelerometer has 3 dimensions and only gives as the data at 
		 * <30Hz, thus don't sleep between reads but read as quickly as 
		 * possible (read is blocking anyway). */
		super(3, 0);
		
		this.port = port;
		server =  (ServerSocketConnection)Connector.open("socket://:" + port);
	}
	
	public void stop() {
		try {
			// properly close all resources
			if (sensorDataIn != null)
				sensorDataIn.close();
			if (sensorConnector != null)
				sensorConnector.close();
			// this should also interrupt the thread
			if (server != null)
				server.close();
		} catch (IOException e) {
			logger.error("Error closing server socket or connection to sensor source: " + e);
		}
		super.stop();
	}

	protected boolean handleSample() {
		if (sensorConnector == null) {
			logger.debug("Waiting for sensor to connect...");
			try {
				sensorConnector = server.acceptAndOpen();
				logger.info("Connection from " + sensorConnector);
				sensorDataIn = sensorConnector.openDataInputStream();
			} catch (IOException e) {
				logger.error("Unable to accept connection, aborting listening: " + e);
				return false;
			}
		}

		try {
			sensorDataIn.readFully(bytes);

			int x = bytes[0] << 8;
			x |= bytes[1] & 0xFF;
			int xxx = x-2050;
			
			int y = bytes[2] << 8;
			y |= bytes[3] & 0xFF;
			int yyy = y-2050;

			int z = bytes[4] << 8;
			z |= bytes[5] & 0xFF;
			int zzz = z-2050;
			
			emitSample(new double[] {xxx, yyy, zzz});
		} catch (IOException e) {
			logger.warn("Unable to read from socket, closing and waiting for next connection: " + e);
			try {
				// properly close all resources
				if (sensorDataIn != null)
					sensorDataIn.close();
				sensorDataIn = null;
				if (sensorConnector != null)
					sensorConnector.close();
				sensorConnector = null;
			} catch (IOException ee) {
				logger.error("Error closing server socket or connection to sensor source: " + ee);
			}
		}

		return true;
	}

}
