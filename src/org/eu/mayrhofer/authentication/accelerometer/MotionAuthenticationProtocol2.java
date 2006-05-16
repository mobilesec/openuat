/* Copyright Rene Mayrhofer
 * File created 2006-05-09
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.authentication.accelerometer;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;

import org.apache.log4j.Logger;
import org.eu.mayrhofer.authentication.CKPOverUDP;
import org.eu.mayrhofer.authentication.exceptions.InternalApplicationException;
import org.eu.mayrhofer.sensors.FFT;
import org.eu.mayrhofer.sensors.ParallelPortPWMReader;
import org.eu.mayrhofer.sensors.Quantizer;
import org.eu.mayrhofer.sensors.SamplesSink;
import org.eu.mayrhofer.sensors.TimeSeriesAggregator;

/** This is the first variant of the motion authentication protocol. It 
 * broadcasts candidate keys over UDP and creates shared keys with the
 * candidate key protocol.
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class MotionAuthenticationProtocol2 extends CKPOverUDP implements SamplesSink  {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(MotionAuthenticationProtocol2.class);

	/** The TCP port we use for this protocol. */
	public static final int UdpPort = 54322;
	
	public static final String MulticastGroup = "228.10.10.1";
	
	private int fftPoints = 128;
	private int numQuantLevels = 8;
	private int numCandidates = 6;
	private int cutOffFrequency = 15; // Hz
	private int windowOverlap = fftPoints/2; 
	
	private LinkedList curSegment = null;

	/** Initializes the object, only setting useJSSE at the moment.
	 * 
	 * @param minMatchingParts
	 * @param useJSSE If set to true, the JSSE API with the default JCE provider of the JVM will be used
	 *                for cryptographic operations. If set to false, an internal copy of the Bouncycastle
	 *                Lightweight API classes will be used.
	 * @throws IOException 
	 */
	public MotionAuthenticationProtocol2(int sampleRate, int minMatchingParts, boolean useJSSE) throws IOException {
		// TODO: set minimum entropy
		super(UdpPort, UdpPort, MulticastGroup, null, true, false, minMatchingParts, 0, useJSSE);
	}

	/** The implementation of SegmentsSink.addSegment. It will be called whenever
	 * a significant active segment has been sampled completely, i.e. when the
	 * source has become quiescent again. 
	 * 
	 * This implementation immediately computes the sliding FFT windows, quantizes
	 * the coefficients, and sends out candidate key parts. 
	 * @throws IOException 
	 * @throws InternalApplicationException 
	 */
	public void addSample(double sample, int numSample) {
		if (curSegment == null) {
			logger.warn("Received sample while not in active state, ignoring it");
			return;
		}
		
		curSegment.add(new Double(sample));
		
		if (curSegment.size() == fftPoints) {
			// ok, got a full segment to work on
			
			// convert to array
			double[] segment = new double[fftPoints];
			Iterator iter = curSegment.iterator();
			for (int i=0; i<fftPoints; i++)
				segment[i] = ((Double) iter.next()).doubleValue();
			
			// TODO: this is actually the other way around....
			int sampleRate = fftPoints;
			// only compare until the cutoff frequency
			int max_ind = (int) (((float) (fftPoints * cutOffFrequency)) / sampleRate) + 1;
			System.out.println("Only comparing the first " + max_ind + " FFT coefficients");
			
			double[] fftCoeff1 = FFT.fftPowerSpectrum(segment, 0, fftPoints);
			// HACK HACK HACK: set DC components to 0
			fftCoeff1[0] = 0;
			int[][] cand = Quantizer.generateCandidates(fftCoeff1, 0, Quantizer.max(fftCoeff1), numQuantLevels, numCandidates, false);
			// and transform to byte array - we certainly use less than 256 quantization stages, so just byte-cast
			byte[][] candBytes = new byte[numCandidates][];
			for (int i=0; i<numCandidates; i++) {
				candBytes[i] = new byte[cand[i].length];
				for (int j=0; j<cand[i].length; j++)
					candBytes[i][j] = (byte) cand[i][j];
			}
			// TODO: estimate entropy
			try {
				addCandidates(candBytes, 0);
			} catch (InternalApplicationException e) {
				logger.error("Could not add candidates: " + e);
			} catch (IOException e) {
				logger.error("Could not add candidates: " + e);
			}
			
			// and remove the overlap from the front
			for (int i=0; i<windowOverlap; i++)
				curSegment.removeFirst();
		}
	}
	
	public void segmentStart(int numSample) {
		if (curSegment != null) {
			logger.warn("Received segment start event while still in active phase, ignoring");
			return;
		}
		
		curSegment = new LinkedList();
	}
	
	public void segmentEnd(int numSample) {
		if (curSegment == null) {
			logger.warn("Received segment end event while no in active phase, ignoring");
			return;
		}
		
		// TODO: don't discard, do something with it?
		curSegment = null;
	}

	protected void protocolSucceededHook(String remote, byte[] sharedSessionKey) {
		
	}

	protected void protocolFailedHook(String remote, Exception e, String message) {
		
	}

	protected void protocolProgressHook(String remote, int cur, int max, String message) {
		
	}


	/////////////////// testing code begins here ///////////////
	public static void main(String[] args) throws IOException {
		int minmatchingparts = 8;
		
		int samplerate = 128; // Hz
		int windowsize = samplerate/2; // 1/2 second
		int minsegmentsize = windowsize; // 1/2 second
		double varthreshold = 350;
		ParallelPortPWMReader r = new ParallelPortPWMReader(args[0], samplerate);
		TimeSeriesAggregator aggr_a = new TimeSeriesAggregator(3, windowsize, minsegmentsize);
		TimeSeriesAggregator aggr_b = new TimeSeriesAggregator(3, windowsize, minsegmentsize);
		r.addSink(new int[] {0, 1, 2}, aggr_a.getInitialSinks());
		r.addSink(new int[] {4, 5, 6}, aggr_b.getInitialSinks());
		aggr_a.setOffset(0);
		aggr_a.setMultiplicator(1/128f);
		aggr_a.setSubtractTotalMean(true);
		aggr_a.setActiveVarianceThreshold(varthreshold);
		aggr_b.setOffset(0);
		aggr_b.setMultiplicator(1/128f);
		aggr_b.setSubtractTotalMean(true);
		aggr_b.setActiveVarianceThreshold(varthreshold);
		
		MotionAuthenticationProtocol2 ma1 = new MotionAuthenticationProtocol2(samplerate, minmatchingparts, true); 
		MotionAuthenticationProtocol2 ma2 = new MotionAuthenticationProtocol2(samplerate, minmatchingparts, true); 
		aggr_a.addNextStageSamplesSink(ma1);
		aggr_b.addNextStageSamplesSink(ma2);
		
		r.simulateSampling();
	}
}
