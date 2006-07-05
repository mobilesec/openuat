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
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.ListIterator;

import org.apache.log4j.Logger;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

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
	 * It may be null, in which case lines will be read from @see #socket instead.
	 * @see #AsciiLineReaderBase(String, int)
	 */
	protected InputStream port;
	
	/** This represents the UDP socket to read lines from. It is only used if
	 * @see #port is null.
	 * @see #AsciiLineReaderBase(DatagramSocket, int)
	 */
	protected DatagramSocket socket;
	
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
	 * @param socket Specifies an UDP socket to read the ASCII lines from.
	 *               It is assumed that another process (or host) will push
	 *               the lines into this socket, with one line per UDP packet.
	 * @param maxNumLines The maximum number of data lines to read from the device - 
	 *                    depends on the sensor.
	 */
	protected AsciiLineReaderBase(DatagramSocket socket, int maxNumLines) {
		this.sinks = new LinkedList();
		this.numSamples = 0;
		this.maxNumLines = maxNumLines;
		this.socket = socket;
		
		logger.info("Reading from datagram socket bound to port " + socket.getLocalPort());
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
		if (port != null) {
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
		else
			logger.error("Simulation not supported when not reading from local port");
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

	
	/** This is a helper class that implements the Runnable interface internally. This way, one <b>has</b> to use the
	 * start and stop methods of the outer class to start the thread, which is cleaner from an interface point of view.
	 */
	private class RunHelper implements Runnable {
		public void run() {
			if (port != null) {
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
			}
			else {
				// no port to read from, instead read from UDP socket
				byte[] lineBuffer = new byte[256];
				DatagramPacket packet = new DatagramPacket(lineBuffer, lineBuffer.length);
				try {
					while (alive) {
						socket.receive(packet);
						String line = new String(packet.getData());
						parseLine(line);
					}
				}
				catch (IOException e) {
					logger.error("Aborting reading from UDP port due to: " + e);
				}
			}
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




	/////////////////////////// test code begins here //////////////////////////////
	static class XYSink implements SamplesSink {
		XYSeries series = new XYSeries("Line", false);
		int num=0;
		ArrayList segment = null;
		XYSeries firstActiveSegment = null;
	
		public void addSample(double s, int index) {
			if (index != num++)
				logger.error("Sample index invalid");
			series.add(index, s);
			if (segment != null)
				segment.add(new Double(s));
		}
		public void segmentStart(int index) {
			logger.debug("Receiving segment starting from index " + index);
			
			if (firstActiveSegment == null)
				segment = new ArrayList();
		}
		public void segmentEnd(int index) {
			logger.debug("Receiving segment ending at index " + index);

			if (segment != null) {
				firstActiveSegment = new XYSeries("Segment", false);
				for (int i=0; i<segment.size(); i++)
					firstActiveSegment.add(i, ((Double) segment.get(i)).doubleValue());
			}
		}
	}

	static class SegmentSink implements SegmentsSink {
		static double[][] segs = new double[2][];
		static {
			segs[0] = null;
			segs[1] = null;
		}

		private int index;
		public SegmentSink(int index) {
			this.index = index;
		}
		public void addSegment(double[] segment, int startIndex) {
			logger.info("Received segment of size " + segment.length + " starting at index " + startIndex);

			if (segs[index] == null)
				segs[index] = segment;
			else
				logger.warn("Already received segment " + index + ", this is a second significant one!");
		}
	}

	// just a helper function for comparing
	private static boolean compareQuantizedVectors(int[][] cand1, int[][] cand2, int max_ind) {
		for (int i=0; i<cand1.length; i++) {
			for (int j=0; j<cand2.length; j++) {
				boolean equal = true;
				for (int k=0; k<max_ind && equal; k++) {
					if (cand1[i][k] != cand2[j][k])
						equal = false;
				}
				if (equal) {
					//System.out.println("Match at i=" + i + ", j=" + j);
					return true;
				}
			}
		}
		return false;
	}
	
	private static boolean[] quantizeAndCompare(double[] vector1, double[] vector2, 
			int numQuantLevels, int numCandidates, int max_ind) {
		boolean[] ret = new boolean[2];
		double max1 = Quantizer.max(vector1);
		double max2 = Quantizer.max(vector2);
		int cand1[][] = Quantizer.generateCandidates(vector1, 0, max1, numQuantLevels, false, numCandidates, false);
		int cand2[][] = Quantizer.generateCandidates(vector2, 0, max2, numQuantLevels, false, numCandidates, false);
		ret[0] = compareQuantizedVectors(cand1, cand2, max_ind);
		cand1 = Quantizer.generateCandidates(vector1, 0, max1, numQuantLevels, true, numCandidates, false);
		cand2 = Quantizer.generateCandidates(vector2, 0, max2, numQuantLevels, true, numCandidates, false);
		ret[1] = compareQuantizedVectors(cand1, cand2, max_ind);
		return ret;
	}
	
	protected static void mainRunner(String runClassName, String[] args) throws IOException {
		String filename = args[0];
		
		boolean graph = false;
		boolean paramSearch = false;
		if (args.length > 1 && args[1].equals("dographs"))
			graph = true;
		if (args.length > 1 && args[1].equals("paramsearch"))
			paramSearch = true;
		
		/////// test 1: just plot all time series
		if (graph) {
			AsciiLineReaderBase r = null;
			if (runClassName.equals("ParallelPortPWMReader"))
				r = new ParallelPortPWMReader(filename, 100);
			else if (runClassName.equals("WiTiltRawReader"))
				r = new WiTiltRawReader(filename);
			else {
				System.err.println("Unknown derived class name!");
				System.exit(200);
			}
			
			TimeSeries[] t = new TimeSeries[r.maxNumLines];
			XYSink[] s = new XYSink[r.maxNumLines];
			int[] linesToAdd = new int[r.maxNumLines];
			for (int i=0; i<r.maxNumLines; i++) {
				t[i] = new TimeSeries(50);
				t[i].setOffset(0);
				t[i].setMultiplicator(1/128f);
				t[i].setSubtractTotalMean(true);
				t[i].setActiveVarianceThreshold(350);
				s[i] = new XYSink();
				t[i].addNextStageSink(s[i]);
				
				linesToAdd[i] = i;
			}
			r.addSink(linesToAdd, t);
			r.simulateSampling();
		
			for (int i=0; i<r.maxNumLines; i++) {
				XYDataset data = new XYSeriesCollection(s[i].series);
				JFreeChart chart = ChartFactory.createXYLineChart("Line " + i, "Number [100Hz]", 
						"Sample", data, PlotOrientation.VERTICAL, true, true, false);
				ChartUtilities.saveChartAsJPEG(new File("/tmp/line" + i + ".jpg"), chart, 500, 300);

				XYDataset segData = new XYSeriesCollection(s[i].firstActiveSegment);
				JFreeChart segChart = ChartFactory.createXYLineChart("Segment at line " + i, "Number [100Hz]", 
						"Sample", segData, PlotOrientation.VERTICAL, true, true, false);
				ChartUtilities.saveChartAsJPEG(new File("/tmp/seg" + i + ".jpg"), segChart, 500, 300);
			}
		}
		
		/////// test 2: plot the 2 extracted segments from the first and the second device
		int[] samplerates = new int[] {128, 256, 512}; // {64, 128, 256, 512}; // different sample rates
		double[] windowsizeFactors = new double[] {1}; // {1 , 1/2f, 1/4f};  // 1 second, 1/2 second or 1/4 second for active detection
		double varthresholdMin = 350; // 50;
		double varthresholdMax = 350; // 1000;
		double varthresholdStep = 50; // 10;
		int[] quantLevels = new int[] {2, 3, 4, 5, 6, 8, 10, 12, 14, 16, 20};
		int numCandidatesMin = 2;
		int numCandidatesMax = 8;
		int numCandidatesStep = 2;
		int cutOffFrequencyMin = 5;
		int cutOffFrequencyMax = 20;
		int cutOffFrequencyStep = 5;
		double[] windowOverlapFactors = new double[] {0, 1/8f, 1/4f, 1/3f, 1/2f, 2/3f, 3/4f, 7/8f}; // or just 1/2? 
		
		// this is ugly.....
		for (int i1=0; i1<samplerates.length; i1++) {
			int samplerate = samplerates[i1];
			// these are the defaults when not searching for parameters
			if (!paramSearch) {
				samplerate = 128; // Hz
				i1=samplerates.length; // makes the loop exit after this run
			}

			System.out.println("Sampling input data from " + filename + " with " + samplerate + " Hz");
			// can not sample now, because the time series aggregator needs all samples again...

			for (int i2=0; i2<windowsizeFactors.length; i2++) {
				int windowsize = (int) (samplerate*windowsizeFactors[i2]);
				// this is not yet searched, but restrict the minimum significant segment size to 1/2s
				int minsegmentsize = windowsize;
				// these are the defaults when not searching for parameters
				if (!paramSearch) {
					windowsize = samplerate/2; // 1/2 second
					minsegmentsize = windowsize; // 1/2 second
					i2=windowsizeFactors.length; // makes the loop exit after this run
				}
				
				for (double varthreshold=varthresholdMin; varthreshold<=varthresholdMax; 
						varthreshold+=(paramSearch ? varthresholdStep : varthresholdMax)) {
					// these are the defaults when not searching for parameters
					if (!paramSearch) {
						varthreshold = 350;
					}
					
					System.out.println("Searching for first significant segments with windowsize=" + windowsize + 
							", minsegmentsize=" + minsegmentsize + ", varthreshold=" + varthreshold);
					AsciiLineReaderBase r2 = null;
					if (runClassName.equals("ParallelPortPWMReader"))
						r2 = new ParallelPortPWMReader(filename, samplerate);
					else {
						System.err.println("Unknown derived class name or not supported for WiTilt right now!");
						System.exit(200);
					}

					TimeSeriesAggregator aggr_a = new TimeSeriesAggregator(3, windowsize, minsegmentsize);
					TimeSeriesAggregator aggr_b = new TimeSeriesAggregator(3, windowsize, minsegmentsize);
					r2.addSink(new int[] {0, 1, 2}, aggr_a.getInitialSinks());
					r2.addSink(new int[] {4, 5, 6}, aggr_b.getInitialSinks());
					aggr_a.addNextStageSegmentsSink(new SegmentSink(0));
					aggr_b.addNextStageSegmentsSink(new SegmentSink(1));
					aggr_a.setOffset(0);
					aggr_a.setMultiplicator(1/128f);
					aggr_a.setSubtractTotalMean(true);
					aggr_a.setActiveVarianceThreshold(varthreshold);
					aggr_b.setOffset(0);
					aggr_b.setMultiplicator(1/128f);
					aggr_b.setSubtractTotalMean(true);
					aggr_b.setActiveVarianceThreshold(varthreshold);
					r2.simulateSampling();

					if (SegmentSink.segs[0] != null && SegmentSink.segs[1] != null) {
						if (graph) {
							XYSeries seg1 = new XYSeries("Segment 1", false);
							for (int i=0; i<SegmentSink.segs[0].length; i++)
								seg1.add(i, SegmentSink.segs[0][i]);
							XYDataset dat1 = new XYSeriesCollection(seg1);
							JFreeChart chart1 = ChartFactory.createXYLineChart("Aggregated samples", "Number [100Hz]", 
									"Sample", dat1, PlotOrientation.VERTICAL, true, true, false);
							ChartUtilities.saveChartAsJPEG(new File("/tmp/aggrA.jpg"), chart1, 500, 300);

							XYSeries seg2 = new XYSeries("Segment 2", false);
							for (int i=0; i<SegmentSink.segs[1].length; i++)
								seg2.add(i, SegmentSink.segs[1][i]);
							XYDataset dat2 = new XYSeriesCollection(seg2);
							JFreeChart chart2 = ChartFactory.createXYLineChart("Aggregated samples", "Number [100Hz]", 
									"Sample", dat2, PlotOrientation.VERTICAL, true, true, false);
							ChartUtilities.saveChartAsJPEG(new File("/tmp/aggrB.jpg"), chart2, 500, 300);
						}

						/////// test 3: calculate and plot the coherence between the segments from test 2
						// make sure they have similar length
						int len = SegmentSink.segs[0].length <= SegmentSink.segs[1].length ? SegmentSink.segs[0].length : SegmentSink.segs[1].length;
						System.out.println("Using " + len + " samples for coherence computation");
						double[] s1 = new double[len];
						double[] s2 = new double[len];
						for (int i=0; i<len; i++) {
							s1[i] = SegmentSink.segs[0][i];
							s2[i] = SegmentSink.segs[1][i];
						}
						
						// disable coherence computation for now, we are searching for variant 2 parameters
						/*double[] coherence = Coherence.cohere(s1, s2, windowsize, 0);
						if (coherence != null) {
							if (graph) {
								XYSeries c = new XYSeries("Coefficients", false);
								for (int i=0; i<coherence.length; i++)
									c.add(i, coherence[i]);
								XYDataset c1 = new XYSeriesCollection(c);
								JFreeChart c2 = ChartFactory.createXYLineChart("Coherence", "", 
										"Sample", c1, PlotOrientation.VERTICAL, true, true, false);
								ChartUtilities.saveChartAsJPEG(new File("/tmp/coherence.jpg"), c2, 500, 300);
							}
		
							double coherenceMean = Coherence.mean(coherence);
							System.out.println("Coherence mean: " + coherenceMean + 
									" samplerate=" + samplerate + ", windowsize=" + windowsize + 
									", minsegmentsize=" + minsegmentsize + ", varthreshold=" + varthreshold);
						}*/

						/////// test 4: calculate and compare the quantized FFT power spectra coefficients of the segments from test 2
						for (int i3=0; i3<windowOverlapFactors.length; i3++) {
							int fftpoints = samplerate;
							int windowOverlap = (int) (fftpoints*windowOverlapFactors[i3]);
							// these are the defaults when not searching for parameters
							if (!paramSearch) {
								windowOverlap = fftpoints/2;
								i3=windowOverlapFactors.length;
							}

							for (int i4=0; i4<quantLevels.length; i4++) {
//							for (int numQuantLevels=numQuantLevelsMin; numQuantLevels<=numQuantLevelsMax; 
// //									numQuantLevels=(paramSearch ? numQuantLevels*numQuantLevelsStepMultiplicator : numQuantLevelsMax)) {
//									numQuantLevels+=(paramSearch ? numQuantLevelsStep : numQuantLevelsMax)) {
								int numQuantLevels = quantLevels[i4];
								// these are the defaults when not searching for parameters
								if (!paramSearch) {
									numQuantLevels = 8;
								}
								for (int numCandidates=numCandidatesMin; numCandidates<=numCandidatesMax; 
									numCandidates+=(paramSearch ? numCandidatesStep : numCandidatesMax)) {
									// these are the defaults when not searching for parameters
									if (!paramSearch) {
										numCandidates = 6;
									}

									for (int cutOffFrequency=cutOffFrequencyMin; cutOffFrequency<=cutOffFrequencyMax; 
										cutOffFrequency+=(paramSearch ? cutOffFrequencyStep : cutOffFrequencyMax)) {
										// these are the defaults when not searching for parameters
										if (!paramSearch) {
											cutOffFrequency = 15; // Hz
										}

										// only compare until the cutoff frequency
										int max_ind = (int) (((float) (fftpoints * cutOffFrequency)) / samplerate) + 1;
										//System.out.println("Only comparing the first " + max_ind + " FFT coefficients");
										
										int numMatchesVariantA=0;
										int numMatchesVariantB=0;
										int numMatchesVariantC=0;
										int numMatchesVariantD=0;
										int numWindows=0;

										for (int offset=0; offset<s1.length-fftpoints+1; offset+=fftpoints-windowOverlap) {
											double[] allCoeff1 = FFT.fftPowerSpectrum(s1, offset, fftpoints);
											double[] allCoeff2 = FFT.fftPowerSpectrum(s2, offset, fftpoints);
											
											// for better performance, only use the first max_ind coefficients since the others will not be compared anyway
											double[] fftCoeff1 = new double[max_ind];
											double[] fftCoeff2 = new double[max_ind];
											System.arraycopy(allCoeff1, 0, fftCoeff1, 0, max_ind);
											System.arraycopy(allCoeff2, 0, fftCoeff2, 0, max_ind);
											
											// HACK HACK HACK: set DC components to 0
											fftCoeff1[0] = 0;
											fftCoeff2[0] = 0;
											
											// variants a and c: compare the quantized FFT coefficients directly
											boolean[] matches = quantizeAndCompare(fftCoeff1, fftCoeff2, 
													numQuantLevels, numCandidates, max_ind);
											if (matches[0])
												numMatchesVariantA++;
											if (matches[1])
												numMatchesVariantC++;
											
											// variants b and d: compare the quantized pairwise neighboring sums of the coefficients
											double sums1[] = new double[max_ind];
											double sums2[] = new double[max_ind];
											for (int k=0; k<max_ind; k++) {
												sums1[k] = allCoeff1[k] + allCoeff1[k+1];
												sums2[k] = allCoeff2[k] + allCoeff2[k+1];
												//System.out.println("k=" + k + ": sum1=" + sums1[k] + " sum2=" + sums2[k]);
											}
											matches = quantizeAndCompare(sums1, sums2,
													numQuantLevels, numCandidates, max_ind);
											if (matches[0])
												numMatchesVariantB++;
											if (matches[1])
												numMatchesVariantD++;
											
											numWindows++;
										}
										System.out.println("Match A: " + (float) numMatchesVariantA / numWindows + " (" + numMatchesVariantA + " out of " + numWindows + ")" + 
												" Match B: " + (float) numMatchesVariantB / numWindows + " (" + numMatchesVariantB + " out of " + numWindows + ")" +
												" Match C: " + (float) numMatchesVariantC / numWindows + " (" + numMatchesVariantC + " out of " + numWindows + ")" +
												" Match D: " + (float) numMatchesVariantD / numWindows + " (" + numMatchesVariantD + " out of " + numWindows + ")" +
												" samplerate=" + samplerate + ", windowsize=" + windowsize + 
												", minsegmentsize=" + minsegmentsize + ", varthreshold=" + varthreshold +
												", windowoverlap=" + windowOverlap + ", numquantlevels=" + numQuantLevels +
												", numcandidates=" + numCandidates + ", cutofffrequ=" + cutOffFrequency +
												" (" + max_ind + " FFT coefficients)");
									}
								}
							}
						}
					}
					else 
						System.err.println("Did not get 2 significant active segments");

				}
			}
		}
	}
}
