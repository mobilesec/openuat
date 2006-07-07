/* Copyright Rene Mayrhofer
 * File created 2006-04-27
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.sensors;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;

/** This class implements a reader for the data format generated by the small
 * Linux native code (a C command line program) to sample pulse-width modulated
 * sensors (e.g. accelerometers) using a standard parallel port. 
 * 
 * Because the output format reports a new value whenever a change on the 
 * parallel port occurs, this class implements sampling with a defined sample
 * rate to generate equidistant sample points.
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class ParallelPortPWMReader extends AsciiLineReaderBase {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(ParallelPortPWMReader.class);
	
	/** The length of s sample period, i.e. the sample width, in µsec. */
	private int sampleWidth;
	
	/** The current samples, i.e. all sample values that fall into the current sample period. */
	private ArrayList[] curSample;
	/** The time when the last sample was issued, in usec. */
	private long lastSampleAt;
	/** Remember the last sample values, in case there is a sample period with no samples from
	 * the sensors. In this case the last values are just repeated.
	 */
	private double[] lastSampleValues;
	
	/** Initializes the parallel port PWM log reader. It only saves the
	 * passed parameters and opens the InputStream to read from the specified
	 * file, and thus implicitly to check if the file exists and can be opened.
	 * 
	 * @param filename The log to read from. This may either be a normal log file
	 *                 when simulation is intended or it can be a FIFO/pipe to read
	 *                 online data.
	 * @param samplerate The sample rate in Hz.
	 * @throws FileNotFoundException When filename does not exist or can not be opened.
	 */
	public ParallelPortPWMReader(String filename, int samplerate) throws FileNotFoundException {
		// the maximum number of data lines to read from the port - obviously 8
		super(filename, 8); 

		this.sampleWidth = 1000000 / samplerate;
		this.curSample = new ArrayList[maxNumLines];
		for (int i=0; i<maxNumLines; i++)
			curSample[i] = new ArrayList();
		this.lastSampleValues = new double[maxNumLines];
		
		this.lastSampleAt = 0;
		
		logger.info("Reading from " + filename +
				" with sample rate " + samplerate + " Hz (sample width " + sampleWidth + " us)");
	}

	/** Initializes the parallel port PWM log reader. It only saves the
	 * passed parameters. This is an alternative to @see #ParallelPortPWMReader(String, int)
	 * and should only be used in special cases.
	 * 
	 * @param stream Specifies the InputStream to read from.
	 * @param samplerate The sample rate in Hz.
	 */
	public ParallelPortPWMReader(InputStream stream, int samplerate) {
		// the maximum number of data lines to read from the port - obviously 8
		super(stream, 8); 

		this.sampleWidth = 1000000 / samplerate;
		this.curSample = new ArrayList[maxNumLines];
		for (int i=0; i<maxNumLines; i++)
			curSample[i] = new ArrayList();
		this.lastSampleValues = new double[maxNumLines];
		
		this.lastSampleAt = 0;
		
		logger.info("Reading from input stream with sample rate " + samplerate + 
				" Hz (sample width " + sampleWidth + " us)");
	}
	
	/** A helper function to parse single line of the format produced by 
	 * parport-pulsewidth. This method creates the samples and emits events.
	 * @param line The line to parse.
	 */
	protected void parseLine(String line) {
		StringTokenizer st = new StringTokenizer(line, " .", false);
		// first two columns (with a '.' in between) are the timestamp (sec and usec)
		long timestamp = 0;
		try {
			int timestampSecs = Integer.parseInt(st.nextToken()); 
			int timestampUSecs = Integer.parseInt(st.nextToken());
			timestamp = (long)timestampSecs*1000000 + timestampUSecs;
		}
		catch (NoSuchElementException e) {
			logger.warn("Short line with incomplete timestamp, ignoring line");
			return;
		}
		catch (NumberFormatException e) {
			logger.warn("Unable to decode timestamp, ignoring line");
			return;
		}
		logger.debug("Reading at timestamp " + timestamp + " us");
		// sanity check
		if (timestamp <= lastSampleAt) {
			logger.error("Reading from the past (read " + timestamp + ", last sample at " + 
					lastSampleAt + ")! Aborting parsing");
			return;
		}
		// another sanity check
		if (timestamp >= lastSampleAt + sampleWidth * 1000 &&
			timestamp >= System.currentTimeMillis() * 1000) {
			logger.error("Reading from the future (and jumping forwards by more than 1000 samples, read " +
					timestamp + ", last sample at " + lastSampleAt + ", current time " + 
					System.currentTimeMillis() + ")! Aborting parsing");
		}
		
		// special case: first sample
		if (lastSampleAt == 0) {
			logger.debug("First sample starting at " + timestamp + " us");
			lastSampleAt = timestamp;
		}
		
		// check if this sample creates a new sample period
		if (timestamp > lastSampleAt + sampleWidth) {
			logger.debug("Current reading creates new sample");
			
			// get the average over the last period's samples (if there are any, if not, just use the last period's samples)
			if (curSample[0].size() > 0) {
				logger.debug("Averaging over " + curSample[0].size() + " values for the last sample");
				for (int i=0; i<maxNumLines; i++) {
					lastSampleValues[i] = 0;
					for (int j=0; j<curSample[i].size(); j++)
						lastSampleValues[i] += ((Integer) curSample[i].get(j)).intValue();
					lastSampleValues[i] /= curSample[i].size();
					// prepare for the next (i.e. the currently starting) sample period
					curSample[i].clear();
				}
			}
			
			while (timestamp > lastSampleAt + sampleWidth) {
				lastSampleAt += sampleWidth;
				// and put into all sinks
				logger.debug("Emitting sample for timestamp " + lastSampleAt);
				emitSample(lastSampleValues);
			}
		}
		
		// only continue to extract values when there are more tokens in the line
		if (st.hasMoreElements()) {
			// then the 8 data lines
			int[] allLines = new int[8];
			int elem=0;
			while (elem<8 && st.hasMoreElements()) {
				try {
					allLines[elem] = Integer.parseInt(st.nextToken());
				}
				catch (NumberFormatException e) {
					logger.warn("Unable to decode sample value, ignoring line");
					return;
				}
				elem++;
			}
			if (elem<8) {
				logger.warn("Short line, only got " + elem + " line values instead of " + 8 + ", ignoring line");
			}
			else {
				// extract the lines we want and remember the values
				for (int i=0; i<maxNumLines; i++) {
					int val = allLines[i]; 
					logger.debug("Read value " + val + " on line " + i);
					curSample[i].add(new Integer(val));
				}
			}
		}
		else
			logger.debug("This is an empty reading containing only a timestamp");
	}


	/////////////////////////// test code begins here //////////////////////////////
	public static void main(String[] args) throws IOException {
		mainRunner("ParallelPortPWMReader", args);
	}
}
