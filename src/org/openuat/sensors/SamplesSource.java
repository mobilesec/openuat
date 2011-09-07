/* File created 2007-05-04
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.sensors;

import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** This is a base class for emitting samples to a list of registers 
 * SamplesSink objects. It imlements handling the listeners and the background
 * thread for doing the sampling.
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public abstract class SamplesSource {
	/** Our logger. */
	private static Logger logger = LoggerFactory.getLogger("org.openuat.sensors.SamplesSource" /*SamplesSource.class*/);

	/** The maximum number of data lines to read from the device - depends on the sensor. */
	protected int maxNumLines;

	/** Objects of this type are held in sinks. They represent listeners to 
	 * be notified of samples.
	 */
	private class ListenerCombination {
		int[] lines;
		SamplesSink[] doubleSinks;
		SamplesSink_Int[] intSinks;
		public ListenerCombination(int[] lines, SamplesSink[] doubleSinks, SamplesSink_Int[] intSinks) {
			this.lines = lines;
			this.doubleSinks = doubleSinks;
			this.intSinks = intSinks;
		}
		// TODO: activate me again when J2ME polish can deal with Java5 sources!
		//@Override
		public boolean equals(Object o) {
			return o instanceof ListenerCombination &&
				((ListenerCombination) o).lines.equals(lines) &&
				(doubleSinks != null && ((ListenerCombination) o).doubleSinks.equals(doubleSinks)) &&
				(intSinks != null && ((ListenerCombination) o).intSinks.equals(intSinks));
		}
	}
	
	/** This holds all registered sinks in form of ListenerCombination
	 * objects.
	 * @see #addSink(int[], SamplesSink[])
	 * @see #removeSink(int[], SamplesSink[])
	 * @see #addSink(int[], SamplesSink_Int[])
	 * @see #removeSink(int[], SamplesSink_Int[])
	 */
	private Vector listeners;
	
	/** This holds all registered vector sinks.
	 * @see #addSink(VectorSamplesSink)
	 * @see #removeSink(VectorSamplesSink)
	 */
	private Vector vectorListeners;

	/** The total number of samples read until currently. Changed by emitSample.
	 * @see #emitSample(double[]) 
	 * @see #emitSample(int[]) 
	 */
	private int numSamples;
	
	/** The thread that does the actual reading from the port.
	 * @see #start()
	 * @see #stop()
	 */
	private Thread samplingThread = null;
	
	/** The time to sleep between two reads from the file in milliseconds.
	 * @see RunHelper#run()
	 * @see #simulateSampling()
	 */
	private int sleepBetweenReads = 0;
	
	/** Used to signal the sampling thread to terminate itself.
	 * @see #stop()
	 * @see RunHelper#run()
	 */
	boolean alive = true;

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
	 */
	protected SamplesSource(int maxNumLines, int sleepBetweenReads) {
		this.listeners = new Vector();
		this.vectorListeners = new Vector();
		this.numSamples = 0;
		this.maxNumLines = maxNumLines;
		this.sleepBetweenReads = sleepBetweenReads;

		logger.info("Initializing for " + maxNumLines + 
				" sampling lines, sleeping for " + this.sleepBetweenReads + 
				" ms between reads");
	}
	
	/** This is just a helper doing some checks and debug printing, called by
	 * the public addSink implementations.
	 */
	private void addSinkHelper(int[] lines, ListenerCombination listener) throws IllegalArgumentException {
		if (lines.length < 1 || lines.length > maxNumLines)
			throw new IllegalArgumentException("Number of lines to read must be between 1 and " +
					maxNumLines);
		StringBuffer tmp = new StringBuffer();
		for (int i=0; i<lines.length; i++) {
			if (lines[i] < 0 || lines[i] > maxNumLines-1)
				throw new IllegalArgumentException("Line index must be between 0 and " +
						(maxNumLines-1));
			if (logger.isDebugEnabled()) {
				tmp.append(lines[i]);
				tmp.append(' ');
			}
		}
		if (logger.isDebugEnabled())
			logger.debug("Registering new listener for lines " + tmp.toString());
		listeners.addElement(listener);
	}

	/** Registers a sink, which will receive all new values as they are sampled.
	 * @param doubleSinks The time series to fill. This array must have the same number of
	 *             elements as the number of lines specified to the constructor.  
	 * @param lines The set of lines on the device to read. Must be an integer
	 *              array with a minimum length of 1 and a maximum length specified to
	 *              the constructor, containing the indices of the lines to read. These 
	 *              indices are counted from 0 to maxNumLines-1. E.g. for a parallel 
	 *              port (see ParallelPortPWMReader), this corresponds to data lines 
	 *              DATA0 to DATA7. E.g. for a 3D accelerometer (see WiTiltRawReader),
	 *              this corresponds to 0=X, 1=Y, 2=Z.
	 */
	public void addSink(int[] lines, SamplesSink[] doubleSinks) throws IllegalArgumentException {
		if (doubleSinks.length != lines.length)
			throw new IllegalArgumentException("Passed TimeSeries array has " + doubleSinks.length 
					+ " elements, but sampling " + lines.length + " devices lines");
		addSinkHelper(lines, new ListenerCombination(lines, doubleSinks, null));
	}

	/** Registers a sink, which will receive all new values as they are sampled. 
	 * This is the integer sinks variant.
	 * @param intSinks The time series to fill. This array must have the same number of
	 *             elements as the number of lines specified to the constructor.  
	 * @param lines The set of lines on the device to read. Must be an integer
	 *              array with a minimum length of 1 and a maximum length specified to
	 *              the constructor, containing the indices of the lines to read. These 
	 *              indices are counted from 0 to maxNumLines-1. E.g. for a parallel 
	 *              port (see ParallelPortPWMReader), this corresponds to data lines 
	 *              DATA0 to DATA7. E.g. for a 3D accelerometer (see WiTiltRawReader),
	 *              this corresponds to 0=X, 1=Y, 2=Z.
	 */
	public void addSink(int[] lines, SamplesSink_Int[] intSinks) throws IllegalArgumentException {
		if (intSinks.length != lines.length)
			throw new IllegalArgumentException("Passed TimeSeries array has " + intSinks.length 
					+ " elements, but sampling " + lines.length + " devices lines");
		addSinkHelper(lines, new ListenerCombination(lines, null, intSinks));
	}

	/** Registers a sink, which will receive all new vectors as they are sampled.
	 * @param vectorSinks These sinks will be notified with complete samples.  
	 */
	public void addSink(VectorSamplesSink vectorSink) throws IllegalArgumentException {
		if (vectorListeners != null && !vectorListeners.contains(vectorSink))
			vectorListeners.addElement(vectorSink);
	}

	/** Removes a previously registered sink.
	 * 
	 * @param doubleSinks The time series to stop filling.
	 * @param lines The set of lines with which this sink has been registered. 
	 *              @see #addSink(int[], SamplesSink[]) 
	 * @return true if removed, false if not (i.e. if they have not been added previously).
	 */
	public boolean removeSink(int[] lines, SamplesSink[] doubleSinks) {
		return listeners.removeElement(new ListenerCombination(lines, doubleSinks, null));
	}

	/** Removes a previously registered sink.
	 * This is the integer sinks variant.
	 * 
	 * @param intSinks The time series to stop filling.
	 * @param lines The set of lines with which this sink has been registered. 
	 *              @see #addSink(int[], SamplesSink[]) 
	 * @return true if removed, false if not (i.e. if they have not been added previously).
	 */
	public boolean removeSink_Int(int[] lines, SamplesSink_Int[] intSinks) {
		return listeners.removeElement(new ListenerCombination(lines, null, intSinks));
	}

	/** Removes a previously registered sink.
	 * 
	 * @param vectorSinks The time series to stop notifying.
	 * @return true if removed, false if not (i.e. if it has not been added previously).
	 */
	public boolean removeSink(VectorSamplesSink vectorSink) {
		return vectorListeners.removeElement(vectorSink);
	}
	
	/** Starts a new background thread to read from the file and create sample
	 * values as the lines are read.
	 */
	public void start() {
		if (samplingThread == null) {
			if (logger.isDebugEnabled())
				logger.debug("Starting sampling thread");
			samplingThread = new Thread(new RunHelper());
			samplingThread.start();
		}
	}

	/** Stops the background thread, if started previously. */
	public void stop() {
		if (samplingThread != null) {
			if (logger.isDebugEnabled())
				logger.debug("Stopping sampling thread: signalling thread to cancel and waiting;");
			alive = false;
			try {
				samplingThread.interrupt();
				samplingThread.join();
			}
			catch (InterruptedException e) {
//#if cfg.haveReflectionSupport
				if (! System.getProperty("os.name").startsWith("Windows CE")) {
					logger.error("Error waiting for sampling thread to terminate: " + e.toString() + "\n" + e.getStackTrace().toString());
				}
				else
//#endif
				{
					// J2ME CLDC doesn't have reflection support and thus no getStackTrace()....
					logger.error("Error waiting for sampling thread to terminate: " + e.toString());
				}
			}
			logger.error("Sampling thread stopped");
			samplingThread = null;
		}
	}

	/** This causes the reader to be shut down properly by calling stop().
	 * #see stop
	 */
	public void dispose() {
		stop();
	}
	
	/** Returns the maximum number of lines that can be sampled. This depends on the
	 * specific sensor implementation.
	 * @return The value of @see maxNumLines.
	 */
	public int getMaxNumLines() {
		return maxNumLines;
	}

	/** Simulate sampling by reading all available lines from the spcified file. */
	public void simulateSampling() {
		while (handleSample()) {
			try {
				if (sleepBetweenReads > 0)
					Thread.sleep(sleepBetweenReads);
			} catch (InterruptedException e) {
				// just don't care when being interrupted, it just makes the waiting time shorter when stopping
			}
		}
	}
	
	/** This method should be called by the parseLine method to send samples to all registered
	 * listeners. Note: When integer sinks have been registered, the double values will be truncated
	 * for sending to these listeners!
	 * @param sample The current sample.
	 */
	protected void emitSample(double[] sample) {
		if (logger.isDebugEnabled()) 
			for (int i=0; i<maxNumLines; i++)
				logger.debug("Double sample number " + numSamples +  
						", line " + i + " = " + sample[i]);
    	if (listeners != null)
    		for (int j=0; j<listeners.size(); j++) {
    			ListenerCombination l = (ListenerCombination) listeners.elementAt(j);
				if (l.doubleSinks != null)
					for (int i=0; i<l.lines.length; i++)
    					l.doubleSinks[i].addSample(sample[l.lines[i]], numSamples);
   				if (l.intSinks != null)
					for (int i=0; i<l.lines.length; i++)
    					l.intSinks[i].addSample((int) sample[l.lines[i]], numSamples);
    		}
    	
    	if (vectorListeners != null)
    		for (int j=0; j<vectorListeners.size(); j++) {
    			VectorSamplesSink l = (VectorSamplesSink) vectorListeners.elementAt(j);
    			if (l != null)
    				l.addSample(sample, numSamples);
    		}
		numSamples++;
	}

	/** This method should be called by the parseLine method to send samples to all registered
	 * listeners. 
	 * @param sample The current sample.
	 */
	protected void emitSample(int[] sample) {
		if (logger.isDebugEnabled()) 
			for (int i=0; i<maxNumLines; i++)
				logger.debug("Integer sample number " + numSamples +  
						", line " + i + " = " + sample[i]);
    	if (listeners != null)
    		for (int j=0; j<listeners.size(); j++) {
    			ListenerCombination l = (ListenerCombination) listeners.elementAt(j);
				if (l.doubleSinks != null)
					for (int i=0; i<l.lines.length; i++)
    					l.doubleSinks[i].addSample(sample[l.lines[i]], numSamples);
   				if (l.intSinks != null)
					for (int i=0; i<l.lines.length; i++)
    					l.intSinks[i].addSample(sample[l.lines[i]], numSamples);
    		}
		numSamples++;
	}
	
	/** This is a helper class that implements the Runnable interface internally. This way, one <b>has</b> to use the
	 * start and stop methods of the outer class to start the thread, which is cleaner from an interface point of view.
	 */
	private class RunHelper implements Runnable {
		public void run() {
			while (alive && handleSample()) {
				try {
					if (sleepBetweenReads > 0)
						Thread.sleep(sleepBetweenReads);
				} catch (InterruptedException e) {
					// just don't care when being interrupted, it just makes the waiting time shorter when stopping
				}
			}
			if (! alive)
				if (logger.isDebugEnabled())
					logger.debug("Background sampling thread terminated regularly due to request");
			else
				logger.warn("Background sampling thread received no more samples, ending now");
			// old code that used to read from a UDP socket
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
	
	/** This method is called whenever a sample should be read from the 
	 * respective source, and it should in turn call emitSample to send the
	 * new sample to all registered listeners.
	 * @return true if more samples are available, false otherwise.
	 */
	protected abstract boolean handleSample();

	/** Each time samples source must be able to provide the appropriate 
	 * parameters for normalizing its values to the [-1;1] range.
	 */
	public abstract TimeSeries.Parameters getParameters();
	public abstract TimeSeries_Int.Parameters getParameters_Int();
}
