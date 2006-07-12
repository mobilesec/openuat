/* Copyright Rene Mayrhofer
 * File created 2006-06-07
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.sensors;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.ListIterator;

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
public abstract class AsciiLineReaderBase {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(AsciiLineReaderBase.class);

	/** The maximum number of data lines to read from the device - depends on the sensor. */
	protected int maxNumLines;

	/** This represent the file to read from and is opened in the constructor.
	 * @see #AsciiLineReaderBase(String, int)
	 */
	protected InputStream port;
	
	/** Objects of this type are held in sinks. They represent listeners to 
	 * be notified of samples.
	 */
	private class ListenerCombination {
		int[] lines;
		SamplesSink[] sinks;
		public ListenerCombination(int[] lines, SamplesSink[] sinks) {
			this.lines = lines;
			this.sinks = sinks;
		}
		public boolean equals(Object o) {
			return o instanceof ListenerCombination &&
				((ListenerCombination) o).lines.equals(lines) &&
				((ListenerCombination) o).sinks.equals(sinks);
		}
	}
	
	/** This holds all registered sinks in form of ListenerCombination
	 * objects.
	 * @see #addSink(int[], SamplesSink[])
	 * @see #removeSink(int[], SamplesSink[])
	 */
	private LinkedList sinks;

	/** The total number of samples read until currently. Changed by emitSample.
	 * @see #emitSample(double[]) 
	 */
	private int numSamples;
	
	/** The thread that does the actual reading from the port.
	 * @see #start()
	 * @see #stop()
	 */
	private Thread samplingThread = null;
	
	/** Used to signal the sampling thread to terminate itself.
	 * @see #stop()
	 * @see RunHelper#run()
	 */
	boolean alive = true;
	
	/** Initializes the reader base object. It only saves the
	 * passed parameters and opens the InputStream to read from the specified
	 * file, and thus implicitly to check if the file exists and can be opened.
	 * 
	 * @param filename The log to read from. This may either be a normal log file
	 *                 when simulation is intended or it can be a FIFO/pipe to read
	 *                 online data.
	 * @param maxNumLines The maximum number of data lines to read from the device - 
	 *                    depends on the sensor.
	 * @throws FileNotFoundException When filename does not exist or can not be opened.
	 */
	protected AsciiLineReaderBase(String filename, int maxNumLines) throws FileNotFoundException {
		this.sinks = new LinkedList();
		this.numSamples = 0;
		this.maxNumLines = maxNumLines;
		
		logger.info("Reading from " + filename);
		
		port = new FileInputStream(new File(filename));
	}
	
	/** Initializes the reader base object. It only saves the
	 * passed parameters. This is an alternative version to
	 * @see #AsciiLineReaderBase(String, int) and should only be used
	 * in special cases. 
	 * 
	 * @param stream Specifies the InputStream to read from.
	 * @param maxNumLines The maximum number of data lines to read from the device - 
	 *                    depends on the sensor.
	 */
	protected AsciiLineReaderBase(InputStream stream, int maxNumLines) {
		this.sinks = new LinkedList();
		this.numSamples = 0;
		this.maxNumLines = maxNumLines;
		this.port = stream;
		
		logger.info("Reading from input stream");
	}
	
	/** Initializes the reader base object. It only saves the
	 * passed parameter and does <b>not</b> initialze the port
	 * member variable. Any derived class using this constructor
	 * <b>must</b> initialize port before calling any other method.
	 */
	protected AsciiLineReaderBase(int maxNumLines) {
		this.sinks = new LinkedList();
		this.numSamples = 0;
		this.maxNumLines = maxNumLines;
	}

	/** Registers a sink, which will receive all new values as they are sampled.
	 * @param sink The time series to fill. This array must have the same number of
	 *             elements as the number of lines specified to the constructor.  
	 * @param lines The set of lines on the device to read. Must be an integer
	 *              array with a minimum length of 1 and a maximum length specified to
	 *              the constructor, containing the indices of the lines to read. These 
	 *              indices are counted from 0 to maxNumLines-1. E.g. for a parallel 
	 *              port (see ParallelPortPWMReader), this corresponds to data lines 
	 *              DATA0 to DATA7. E.g. for a 3D accelerometer (see WiTiltRawReader),
	 *              this corresponds to 0=X, 1=Y, 2=Z.
	 */
	public void addSink(int[] lines, SamplesSink[] sink) throws IllegalArgumentException {
		if (lines.length < 1 || lines.length > maxNumLines)
			throw new IllegalArgumentException("Number of lines to read must be between 1 and " +
					maxNumLines);
		String tmp = "";
		for (int i=0; i<lines.length; i++) {
			if (lines[i] < 0 || lines[i] > maxNumLines-1)
				throw new IllegalArgumentException("Line index must be between 0 and " +
						(maxNumLines-1));
			tmp += lines[i] + " ";
		}
		if (sink.length != lines.length)
			throw new IllegalArgumentException("Passed TimeSeries array has " + sink.length 
					+ " elements, but sampling " + lines.length + " devices lines");
		logger.debug("Registering new listener for lines " + tmp);
		sinks.add(new ListenerCombination(lines, sink));
	}
	
	/** Removes a previously registered sink.
	 * 
	 * @param sink The time series to stop filling.
	 * @param lines The set of lines with which this sink has been registered. 
	 *              @see #addSink(int[], SamplesSink[]) 
	 * @return true if removed, false if not (i.e. if they have not been added previously).
	 */
	public boolean removeSink(int[] lines, SamplesSink[] sink) {
		return sinks.remove(new ListenerCombination(lines, sink));
	}
	
	/** Starts a new background thread to read from the file and create sample
	 * values as the lines are read.
	 */
	public void start() {
		if (samplingThread == null) {
			logger.debug("Starting sampling thread");
			samplingThread = new Thread(new RunHelper());
			samplingThread.start();
		}
	}

	/** Stops the background thread, if started previously. */
	public void stop() {
		if (samplingThread != null) {
			logger.debug("Stopping sampling thread: signalling thread to cancel and waiting;");
			alive = false;
			try {
				samplingThread.interrupt();
				samplingThread.join();
			}
			catch (InterruptedException e) {
				if (! System.getProperty("os.name").startsWith("Windows CE")) {
					logger.error("Error waiting for sampling thread to terminate: " + e.toString() + "\n" + e.getStackTrace().toString());
				}
				else {
					// J2ME CLDC doesn't have reflection support and thus no getStackTrace()....
					logger.error("Error waiting for sampling thread to terminate: " + e.toString());
				}
			}
			logger.error("Sampling thread stopped");
			samplingThread = null;
		}
	}
	
	/** Simulate sampling by reading all available lines from the spcified file. */
	public void simulateSampling() throws IOException {
		BufferedReader r = new BufferedReader(new InputStreamReader(port));
		
		String line = r.readLine();
		while (line != null) {
			parseLine(line);
			try {
				line = r.readLine();
			}
			catch (IOException e) {
				logger.debug("Ignoring exception: " + e);
				line = null;
			}
		}
	}

	/** This causes the reader to be shut down properly by calling stop() and making
	 * sure that all ressources are freed properly when this object is garbage collected.
	 * #see stop
	 */
	public void dispose() {
		stop();
		try {
			if (port != null)
				port.close();
		}
		catch (Exception e) {
			logger.error("Could not properly close input stream");
		}
	}
	
	/** Returns the maximum number of lines that can be sampled. This depends on the
	 * specific sensor implementation.
	 * @return The value of @see maxNumLines.
	 */
	public int getMaxNumLines() {
		return maxNumLines;
	}

	
	/** This is a helper class that implements the Runnable interface internally. This way, one <b>has</b> to use the
	 * start and stop methods of the outer class to start the thread, which is cleaner from an interface point of view.
	 */
	private class RunHelper implements Runnable {
		public void run() {
			BufferedReader r = new BufferedReader(new InputStreamReader(port));
			
			try {
				String line = r.readLine();
				while (alive && line != null) {
					parseLine(line);
					try {
						line = r.readLine();
					}
					catch (IOException e) {
						logger.debug("Ignoring exception: " + e);
						line = null;
					}
				}
				if (! alive)
					logger.debug("Background sampling thread terminated regularly due to request");
				else
					logger.warn("Background sampling thread received empty line! This should not happen when reading from a FIFO");
			}
			catch (Exception e) {
				logger.error("Could not read from file: " + e);
			}
			// old code that use to read from a UDP socket
			/*else {
				// no port to read from, instead read from UDP socket
				byte[] lineBuffer = new byte[65536];
				DatagramPacket packet = new DatagramPacket(lineBuffer, lineBuffer.length);
				StringBuffer buf = new StringBuffer();
				try {
					while (alive) {
						socket.receive(packet);
						// as we don't know where a packet might end, need to reconstruct the lines 
						buf.append(new String(packet.getData()));
						int nextLF = buf.indexOf("\n");
						while (nextLF != -1) {
							String line = buf.substring(0, nextLF).trim();
							System.out.println("line: '" + line + "'");
							buf.delete(0, nextLF+1);
							parseLine(line);
							nextLF = buf.indexOf("\n");
						}
					}
				}
				catch (IOException e) {
					logger.error("Aborting reading from UDP port due to: " + e);
				}
			}*/
		}
	}
	
	/** This method should be called by the parseLine method to send samples to all registered
	 * listeners.
	 * @param sample The current sample.
	 */
	protected void emitSample(double[] sample) {
		if (logger.isDebugEnabled()) 
			for (int i=0; i<maxNumLines; i++)
				logger.debug("Sample number " + numSamples +  
						", line " + i + " = " + sample[i]);
    	if (sinks != null)
    		for (ListIterator j = sinks.listIterator(); j.hasNext(); ) {
    			ListenerCombination l = (ListenerCombination) j.next();
    			for (int i=0; i<l.lines.length; i++)
    				l.sinks[i].addSample(sample[l.lines[i]], numSamples);
    		}
		numSamples++;
	}

	protected abstract void parseLine(String line);
}
