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

/** This is the second variant of the motion authentication protocol. It 
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
	
	// TODO: make me settable
	private int fftPoints = 128;
	private int numQuantLevels = 8;
	private int numCandidates = 8;
	private int cutOffFrequency = 15; // Hz
	private int windowOverlap = fftPoints/2; 
	private static final float matchThreshold = 0.35f;
	
	public final static int MinimumNumberOfRoundsForAction = 5;
	
	/** Remember our own (locally generated) last 30 candidate key parts for detecting
	 * possible matches.
	 */ 
	public final static int LocalCandidateHistorySize = 5 * 8;
	
	/** For each remote host, remember the last 20 matching key parts to have enough 
	 * material for generating candidate keys.
	 */
	public final static int MatchingCandidatesHistorySize = 10 * MinimumNumberOfRoundsForAction * 8;
	
	/** Keep the match history for each remote host for 5 minutes - should really be enough. */
	public final static int MaximumMatchingCandidatesAge = 300;

	/** This keeps the current segment while it is still being collected. It is only set after a
	 * call to segmentStart and before the next call to segmentEnd.
	 */
	private LinkedList curSegment = null;
	
	/** Used to keep track of the number of windows that have been collected. */
	int numWindows = 0;

	/** Initializes the object, only setting useJSSE at the moment. This constructor sets
	 * default values for udpSendPort, udpReceivePort and multicastGroup.
	 * 
	 * @param minMatchingParts
	 * @param useJSSE If set to true, the JSSE API with the default JCE provider of the JVM will be used
	 *                for cryptographic operations. If set to false, an internal copy of the Bouncycastle
	 *                Lightweight API classes will be used.
	 * @throws IOException 
	 */
	public MotionAuthenticationProtocol2(int minMatchingParts, boolean useJSSE) throws IOException {
		// TODO: set minimum entropy
		super(UdpPort, UdpPort, MulticastGroup, null, true, false, LocalCandidateHistorySize, MatchingCandidatesHistorySize, MaximumMatchingCandidatesAge, 
				matchThreshold, 0, matchThreshold/2, MinimumNumberOfRoundsForAction, useJSSE);
	}
	
	/** Initializes the object, only setting useJSSE at the moment.
	 * 
	 * @param minMatchingParts
	 * @param useJSSE If set to true, the JSSE API with the default JCE provider of the JVM will be used
	 *                for cryptographic operations. If set to false, an internal copy of the Bouncycastle
	 *                Lightweight API classes will be used.
	 * @param udpRecvPort The UDP port number to listen for packets at.
	 * @param udpSendPort The UDP port to send packets to.
	 * @param sendAddress The (multicast or unicast) IP address to send UDP packets to.
	 * @throws IOException 
	 */
	public MotionAuthenticationProtocol2(int minMatchingParts, boolean useJSSE, 
			int udpRecvPort, int udpSendPort, String sendAddress, String instanceId) throws IOException {
		// TODO: set minimum entropy
		super(udpRecvPort, udpSendPort, sendAddress, instanceId, true, false, LocalCandidateHistorySize, MatchingCandidatesHistorySize, MaximumMatchingCandidatesAge, 
				matchThreshold, 0, matchThreshold/2, MinimumNumberOfRoundsForAction, useJSSE);
	}

	/** The implementation of SamplesSink.addSegment. It will be called for all 
	 * (aggregated) samples that occur during an active phase.
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
			// ok, got a full window to work on
			
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
			// and take only the relevant coefficients
			double[] fftCoeff2 = new double[max_ind];
			System.arraycopy(fftCoeff1, 0, fftCoeff2, 0, max_ind);
			
			int[][] cand = Quantizer.generateCandidates(fftCoeff2, 0, Quantizer.max(fftCoeff2), numQuantLevels, false, numCandidates, false);
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
			
			numWindows++;
			logger.info("Finished adding window " + numWindows + " as new candidates, now shifting");
			
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
		numWindows = 0;
	}
	
	public void segmentEnd(int numSample) {
		if (curSegment == null) {
			logger.warn("Received segment end event while no in active phase, ignoring");
			return;
		}
		
		// TODO: don't discard, do something with it?
		curSegment = null;
		
		logger.info("Active segment ending now, after extracting " + numWindows + " windows");
	}

	protected void protocolSucceededHook(String remote, byte[] sharedSessionKey, float matchingRoundsFraction) {
		logger.info("CKP succeeded with remote " + remote + " with " + matchingRoundsFraction + 
				" matching rounds, shared key is now " + sharedSessionKey.toString());
	}

	protected void protocolFailedHook(String remote, float matchingRoundsFraction, Exception e, String message) {
		logger.error("CKP failed with remote " + remote + " with " + matchingRoundsFraction + 
				" matching rounds: " + message + "/" + e);
	}

	protected void protocolProgressHook(String remote, int cur, int max, String message) {
		logger.debug("CKP progress with remote " + remote + ": " + cur + " out of " + max + ": " + message);
	}


	/////////////////// testing code begins here ///////////////
	public static void main(String[] args) throws IOException {
		int minmatchingparts = 8;
		
		int samplerate = 128; // Hz
		int windowsize = samplerate; // 1 second
		int minsegmentsize = windowsize; // 1 second
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
		
		MotionAuthenticationProtocol2 ma1 = new MotionAuthenticationProtocol2(minmatchingparts, true); 
		MotionAuthenticationProtocol2 ma2 = new MotionAuthenticationProtocol2(minmatchingparts, true); 
		aggr_a.addNextStageSamplesSink(ma1);
		aggr_b.addNextStageSamplesSink(ma2);
		
		r.simulateSampling();
	}
}
