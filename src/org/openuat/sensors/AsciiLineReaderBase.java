/* File created 2006-06-07
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.sensors;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.log4j.Logger;

/** This is a base class for reading from sensors that are represented by
 * simple files and output ASCII lines, with one line for each sample. It
 * handles registering of sinks and sending sample events to them as well 
 * as managing a background thread for sampling the values.
 * 
 * The parseLine method must be implemented by derived classes, which is
 * expected to call the emitSample method to send out the samples to all
 * registered sinks.
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public abstract class AsciiLineReaderBase extends SamplesSource {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger("org.openuat.sensors.AsciiLineReaderBase" /*AsciiLineReaderBase.class*/);
	
	/** This represent the stream to read from and is opened in the constructor.
	 */
	protected InputStream port = null;
	
	/** This is always initialized with port as a backend. When reopening, this
	 * must also be re-initialized, as handleSample will read from it.
	 */
	protected BufferedReader reader = null;

	/** Set to true when the InputStream should be reopened from the file 
	 * before every read. If set to true, reopenStreamFrom <b>must</b> be set.
	 * @see #reopenStreamFrom
	 * @see RunHelper#run()
	 * @see #simulateSampling()
	 */
	private boolean reopenBeforeRead = false;

	/** The file to reopen the stream from. This is only used when 
	 * reopenBeforeRead is set to true, but in this case it must be
	 * initialized.
	 * @see #reopenBeforeRead
	 * @see RunHelper#run()
	 * @see #simulateSampling()
	 */
	protected File reopenStreamFrom = null;
	
	/** Initializes the reader base object. It only saves the
	 * passed parameters, but the member variable @see {@link #port} needs to
	 * be initialized separately before starting and sampling.
	 * 
	 * @param filename The log to read from. This may either be a normal log file
	 *                 when simulation is intended or it can be a FIFO/pipe to read
	 *                 online data.
	 * @param maxNumLines The maximum number of data lines to read from the device - 
	 *                    depends on the sensor.
	 * @param sleepBetweenReads The number of milliseconds to sleep between two 
	 *                          reads from filename. Set to 0 to do blocking reads
	 *                          (i.e. as fast as the file can give something back).
	 * @param reopenBeforeRead Set to true if the input stream should be re-opened
	 *                        before reading each new line (which might be equivalent
	 *                        with reading each new sample). This can be useful with
	 *                        pseudo-files, e.g. under Linux with the /sys filesystem.
	 *                        When set to true, the derived class <b>must</b> set the
	 *                        reopenStreamFrom member.
	 *                        Set to false if you don't know what this is.
	 */
	protected AsciiLineReaderBase(int maxNumLines, int sleepBetweenReads, boolean reopenBeforeRead) {
		super(maxNumLines, sleepBetweenReads);
		this.reopenBeforeRead = reopenBeforeRead;

		logger.info("Initializing for " + maxNumLines + 
				" sampling lines, sleeping for " + sleepBetweenReads + 
				" ms between reads" +
				(this.reopenBeforeRead ? " and reopening before each read": ""));
}
	
	/** Initializes the reader base object. It only saves the
	 * passed parameters and opens the InputStream to read from the specified
	 * file, and thus implicitly to check if the file exists and can be opened.
	 * 
	 * @param filename The log to read from. This may either be a normal log file
	 *                 when simulation is intended or it can be a FIFO/pipe to read
	 *                 online data.
	 * @param maxNumLines The maximum number of data lines to read from the device - 
	 *                    depends on the sensor.
	 * @param sleepBetweenReads The number of milliseconds to sleep between two 
	 *                          reads from filename. Set to 0 to do blocking reads
	 *                          (i.e. as fast as the file can give something back).
	 * @param reopenBeforeRead Set to true if the input stream should be re-opened
	 *                        before reading each new line (which might be equivalent
	 *                        with reading each new sample). This can be useful with
	 *                        pseudo-files, e.g. under Linux with the /sys filesystem.
	 *                        When set to true, the derived class <b>must</b> set the
	 *                        reopenStreamFrom member.
	 *                        Set to false if you don't know what this is.
	 * @throws FileNotFoundException When filename does not exist or can not be opened.
	 */
	protected AsciiLineReaderBase(String filename, int maxNumLines, int sleepBetweenReads, boolean reopenBeforeRead) throws FileNotFoundException {
		this(maxNumLines, sleepBetweenReads, reopenBeforeRead);
		
		logger.info("Reading from " + filename);
		
		port = new FileInputStream(new File(filename));
		reader = new BufferedReader(new InputStreamReader(port));
	}
	
	/** Initializes the reader base object. It only saves the
	 * passed parameters. This is an alternative version to
	 * @see #AsciiLineReaderBase(String, int, int, boolean) and should only be used
	 * in special cases. 
	 * 
	 * @param stream Specifies the InputStream to read from.
	 * @param maxNumLines The maximum number of data lines to read from the device - 
	 *                    depends on the sensor.
	 */
	protected AsciiLineReaderBase(InputStream stream, int maxNumLines) {
		this(maxNumLines, 0, false);
		
		logger.info("Reading from input stream");
		port = stream;
		reader = new BufferedReader(new InputStreamReader(port));
	}
	
	/** Initializes the reader base object. It only saves the
	 * passed parameter and does <b>not</b> initialze the port
	 * member variable. Any derived class using this constructor
	 * <b>must</b> initialize port before calling any other method.
	 */
	protected AsciiLineReaderBase(int maxNumLines) {
		this(maxNumLines, 0, false);
	}
	
	/** Implementation of SamplesSource.handleSample. */
	protected boolean handleSample() {
		try {
			String line = reader.readLine();
			if (line == null) 
				// no more lines to read, thus no more samples left
				return false;
			parseLine(line);

			try {
				if (reopenBeforeRead) {
					port = new FileInputStream(reopenStreamFrom);
					reader = new BufferedReader(new InputStreamReader(port));
				}
			}
			catch (IOException e) {
				if (logger.isDebugEnabled())
					logger.debug("Ignoring exception, but ending reading: " + e);
				return false;
			}
			return true;
		}
		catch (IOException e) {
			logger.error("Could not read from file: " + e);
			return false;
		}
	}
	
	/** This makes sure that all ressources are freed properly when this object 
	 * is garbage collected.
	 */
	public void dispose() {
		super.dispose();
		try {
			if (port != null)
				port.close();
		}
		catch (Exception e) {
			logger.error("Could not properly close input stream");
		}
	}

	/** This method must be implemented to parse each line of sensor data, and
	 * call emitSample after successful parsing to send samples to all 
	 * registered listeners.
	 * @param line The line that should be parsed.
	 */
	protected abstract void parseLine(String line);
}
