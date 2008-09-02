/* Copyright Rene Mayrhofer
 * File created 2007-05-04 
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.sensors.j2me;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;

import javax.microedition.io.Connector;
import javax.microedition.io.ServerSocketConnection;
import javax.microedition.io.StreamConnection;

import org.apache.log4j.Logger;
import org.openuat.sensors.SamplesSource;
import org.openuat.sensors.TimeSeries;
import org.openuat.sensors.TimeSeries_Int;

/** This class implements an accelerometer sensor reader that gets its data 
 * from a small Python wrapper around the Nokia sensor SDK. It connect to
 * the python server via a TCP socket.
 * 
 * @author Rene Mayrhofer
 * @version 1.1, changes to 1.0: Not using the Symbian wrapper any more but 
 *               now the Python wrapper implementation.
 */
public class NokiaAccelerometerReader extends SamplesSource {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger("org.openuat.sensors.j2me.NokiaAccelerometerReader" /*SymbianTCPAccelerometerReader.class*/);
	
	private final static int Port = 12008;
	
	/** This is only approximate, we can't control the sample rate on the device. */
	public final static int SAMPLERATE = 30;
	
	public final static int VALUE_RANGE = 512;

	/** When the connection to the Sensor API wrapper has been opened
	 * successfully, this contains the data connection object.
	 */
	private StreamConnection dataConnector = null;
	/** When the connection to the Symbian Sensor API wrapper has been opened
	 * successfully, this contains the data input stream object.
	 * <br>
	 * Note: Whenever changing the connection object dataConnecter, this
	 * one <b>must</b> be changed as well (e.g. opening or closing).
	 * handleSample will read from it.
	 * 
	 * @see #handleSample
	 */
	private DataInputStream sensorDataIn = null;

	/** This is a buffer for reading from sensorDataIn that is kept as a 
	 * member variable instead of locally in the method for performance 
	 * reasons.
	 * 
	 * @see #handleSample
	 */ 
	private int[] bytes = new int[3];
	
	/** Initializes the reader.
	 */
	public NokiaAccelerometerReader() {
		/* The accelerometer has 3 dimensions and only gives as the data at 
		 * <30Hz, thus don't sleep between reads but read as quickly as 
		 * possible (read is blocking anyway). */
		super(3, 0);
	}
	
	/** This overrides the SamplesSource.start implementation, because we need
	 * to open the outgoing control connection to get the incoming data 
	 * connection.
	 */
	//@SuppressWarnings("static-access") // we really want the javax...Connector, and not the avetanebt!
	//@Override
	public void start() {
		if (sensorDataIn != null || dataConnector != null) {
			logger.warn("Connection seems to be already open: sensorDataIn="
					+ sensorDataIn + ", dataConnector=" + dataConnector +
					", not starting again");
			return;
		}
		try {
			// connect to the sensor wrapper server
            dataConnector = (StreamConnection) Connector.open("socket://127.0.0.1:" + Port);
			sensorDataIn = dataConnector.openDataInputStream();
			
			// start the background thread for reading
			super.start();
		} catch (IOException e) {
			logger.error("Unable to connect to Symbian sensor API wrapper, can not continue");
			return;
		}
	}
	
	/** This overrides the SamplesSource.stop implementation to also properly
	 * close all resources the may be in use (the sockets).
	 */
	//@Override
	public void stop() {
		logger.warn("11111111111");
		try {
			// properly close all resources
			logger.warn("22222222");
			if (sensorDataIn != null) {
				sensorDataIn.close();
				sensorDataIn = null;
			}
			logger.warn("444444444444");
			if (dataConnector != null) {
				dataConnector.close();
				dataConnector = null;
			}
		} catch (IOException e) {
			logger.error("Error closing server socket or connection to sensor source: " + e);
		}
		logger.warn("66666666666");
		super.stop();
	}

	/** Implementation of SamplesSource.handleSample. When the connection has 
	 * not yet been established by the Symbian Sensor API wrapper, then this
	 * method will block until an incoming connection has been established.
	 * Then, and on all further calls, it will read the samples from 
	 * sensorDataIn and call emitSample to send to listeners.
	 */
	//@Override
	protected boolean handleSample() {
		if (dataConnector == null) {
			logger.error("Not connected to sensor wrapper");
			return false;
		}

		try {
			StringBuffer sb = new StringBuffer();
			int x = 0;
			while ((char) x != '*') {
				x = sensorDataIn.read();
				if (x == -1) {
					logger.error("Symbian sensor wrapper terminated connection, aborting reading");
					return false;
				}
                sb.append(x);
			}

			String s = sb.toString();
            sb.delete(0,sb.length());
            String xS = s.substring(0,s.indexOf(","));
            s=s.substring(s.indexOf(",")+1);
                
            String yS = s.substring(0,s.indexOf(","));
            s=s.substring(s.indexOf(",")+1);                    
                
            String zS = s;
                
            bytes[0] = Integer.parseInt(xS);
            bytes[1] = Integer.parseInt(yS);
            bytes[2] = Integer.parseInt(zS);

			emitSample(bytes);
		} catch (IOException e) {
			logger.error("Unable to read from socket: " + e);
			return false;
		}

		return true;
	}

	/** Provides appropriate parameters for interpreting the values to 
	 * normalize to the [-1;1] range.
	 */
	//@Override
	public TimeSeries.Parameters getParameters() {
		// no floating point support...
		return null;
		/*return new TimeSeries.Parameters() {
			public float getMultiplicator() {
				return 2f/VALUE_RANGE;
			}

			public float getOffset() {
				return -1f;
			}
		};*/
	}
	/** Instead of to [-1;1], these integer parameters map to [-1024;1024],
	 * i.e. MAXIMUM_RANGE in TimeSeries_Int. */
	public TimeSeries_Int.Parameters getParameters_Int() {
		return new TimeSeries_Int.Parameters() {
			public int getMultiplicator() {
				/* We would set this to TimeSeries_Int.MAXIMUM_VALUE (1024), 
				 * but this appears to lead to integer range overflows. 
				 * Therefore already divide by VALUE_RANGE (which would be set
				 * as divisor below) to avoid this.
				 */ 
				return 2;
			}

			public int getDivisor() {
				/* We would set this to VALUE_RANGE, but see above for the 
				 * reason why we return 1 here.
				 */ 
				return 1;
			}

			public int getOffset() {
				return 0;
			}
		};
	}
}
