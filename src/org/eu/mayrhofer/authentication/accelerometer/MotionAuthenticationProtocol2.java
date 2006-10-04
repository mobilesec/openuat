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
import org.eu.mayrhofer.features.QuantizedFFTCoefficients;
import org.eu.mayrhofer.features.TimeSeriesUtil;
import org.eu.mayrhofer.sensors.ParallelPortPWMReader;
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
	
	private int sampleRate; // Hz
	private int fftPoints;
	private int numQuantLevels;
	private int numCandidates;
	private int cutOffFrequency; // in Hz
	private int windowOverlap; 
	
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
	public MotionAuthenticationProtocol2(int sampleRate, int fftPoints, int numQuantLevels, int numCandidates,
			int cutOffFrequency, int windowOverlap, float matchThreshold,
			int minMatchingParts, boolean useJSSE) throws IOException {
		this(sampleRate, fftPoints, numQuantLevels, numCandidates, cutOffFrequency, windowOverlap, 
				matchThreshold, minMatchingParts, useJSSE, UdpPort, UdpPort, MulticastGroup, null);
	}
	
	/** Initializes the object, only setting useJSSE at the moment.
	 * 
	 * @param sampleRate A good value is @see MotionAuthenticationParameters.samplerate
	 * @param fftPoints A good value is @see MotionAuthenticationParameters.fftMatchesWindowSize
	 * @param numQuantLevels A good value is @see MotionAuthenticationParameters.fftMatchesQuantizationLevels
	 * @param numCandidates A good value is @see MotionAuthenticationParameters.fftMatchesCandidatesPerRound
	 * @param cutOffFrequency A good value is @see MotionAuthenticationParameters.fftMatchesCutOffFrequenecy
	 * @param windowOverlap A good value is @see MotionAuthenticationParameters.fftMatchesWindowOverlap
	 * @param matchThreshold A good value is @see MotionAuthenticationParameters.fftMatchesThreshold
	 * @param minMatchingParts
	 * @param useJSSE If set to true, the JSSE API with the default JCE provider of the JVM will be used
	 *                for cryptographic operations. If set to false, an internal copy of the Bouncycastle
	 *                Lightweight API classes will be used.
	 * @param udpRecvPort The UDP port number to listen for packets at.
	 * @param udpSendPort The UDP port to send packets to.
	 * @param sendAddress The (multicast or unicast) IP address to send UDP packets to.
	 * @throws IOException 
	 */
	public MotionAuthenticationProtocol2(int sampleRate, int fftPoints, int numQuantLevels, int numCandidates,
			int cutOffFrequency, int windowOverlap, float matchThreshold,
			int minMatchingParts, boolean useJSSE, 
			int udpRecvPort, int udpSendPort, String sendAddress, String instanceId) throws IOException {
		// TODO: set minimum entropy
		super(udpRecvPort, udpSendPort, sendAddress, instanceId, true, false, LocalCandidateHistorySize, MatchingCandidatesHistorySize, MaximumMatchingCandidatesAge, 
				matchThreshold, 0, (1-matchThreshold)*2/3, MinimumNumberOfRoundsForAction, useJSSE);
		this.sampleRate = sampleRate;
		this.fftPoints = fftPoints;
		this.numQuantLevels = numQuantLevels;
		this.numCandidates = numCandidates;
		this.cutOffFrequency = cutOffFrequency;
		this.windowOverlap = windowOverlap;
		
	}

	/** The implementation of SamplesSink.addSegment. It will be called for all 
	 * (aggregated) samples that occur during an active phase.
	 * 
	 * This implementation immediately computes the sliding FFT windows, quantizes
	 * the coefficients, and sends out candidate key parts. 
	 */
	public void addSample(double sample, int numSample) {
		if (curSegment == null) {
			logger.warn("Received sample while not in active state, ignoring it" +
					(instanceId != null ? " [" + instanceId + "]" : ""));
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
			
			// only compare until the cutoff frequency
			int max_ind = TimeSeriesUtil.getMaxInd(fftPoints, sampleRate, cutOffFrequency); 

			// compute the type 4 match: pairwise sums of exponentially quantized FFT-coefficients
			// TODO: this is now type 1, switch to type 4!
			int[][] cand = QuantizedFFTCoefficients.computeFFTCoefficientsCandidates(segment,
					0, fftPoints, max_ind, numQuantLevels, numCandidates, false, false);
			
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
				logger.error("Could not add candidates: " + e +
						(instanceId != null ? " [" + instanceId + "]" : ""));
			} catch (IOException e) {
				logger.error("Could not add candidates: " + e +
						(instanceId != null ? " [" + instanceId + "]" : ""));
			}
			
			numWindows++;
			logger.info("Finished adding window " + numWindows + " as new candidates, now shifting" +
					(instanceId != null ? " [" + instanceId + "]" : ""));

			// and remove the overlap from the front
			for (int i=0; i<windowOverlap; i++)
				curSegment.removeFirst();
		}
	}
	
	public void segmentStart(int numSample) {
		if (curSegment != null) {
			logger.warn("Received segment start event while still in active phase, ignoring" +
					(instanceId != null ? " [" + instanceId + "]" : ""));
			return;
		}
		
		curSegment = new LinkedList();
		numWindows = 0;
	}
	
	public void segmentEnd(int numSample) {
		if (curSegment == null) {
			logger.warn("Received segment end event while no in active phase, ignoring" +
					(instanceId != null ? " [" + instanceId + "]" : ""));
			return;
		}
		
		// TODO: don't discard, do something with it?
		curSegment = null;
		
		logger.info("Active segment ending now, after extracting " + numWindows + " windows" +
				(instanceId != null ? " [" + instanceId + "]" : ""));
	}

	protected void protocolSucceededHook(String remote, byte[] sharedSessionKey, float matchingRoundsFraction) {
		logger.info("CKP succeeded with remote " + remote + " with " + matchingRoundsFraction + 
				" matching rounds, shared key is now " + sharedSessionKey.toString() +
				(instanceId != null ? " [" + instanceId + "]" : ""));
	}

	protected void protocolFailedHook(String remote, float matchingRoundsFraction, Exception e, String message) {
		logger.error("CKP failed with remote " + remote + " with " + matchingRoundsFraction + 
				" matching rounds: " + message + "/" + e +
				(instanceId != null ? " [" + instanceId + "]" : ""));
	}

	protected void protocolProgressHook(String remote, int cur, int max, String message) {
		logger.debug("CKP progress with remote " + remote + ": " + cur + " out of " + max + ": " + message +
				(instanceId != null ? " [" + instanceId + "]" : ""));
	}


	/////////////////// testing code begins here ///////////////
	public static void main(String[] args) throws IOException {
		int minmatchingparts = 8;
		
		ParallelPortPWMReader r = new ParallelPortPWMReader(args[0], MotionAuthenticationParameters.samplerate);
		TimeSeriesAggregator aggr_a = new TimeSeriesAggregator(3, MotionAuthenticationParameters.activityDetectionWindowSize, MotionAuthenticationParameters.activityMinimumSegmentSize, -1);
		TimeSeriesAggregator aggr_b = new TimeSeriesAggregator(3, MotionAuthenticationParameters.activityDetectionWindowSize, MotionAuthenticationParameters.activityMinimumSegmentSize, -1);
		r.addSink(new int[] {0, 1, 2}, aggr_a.getInitialSinks());
		r.addSink(new int[] {4, 5, 6}, aggr_b.getInitialSinks());
		aggr_a.setOffset(0);
		aggr_a.setMultiplicator(1/128f);
		aggr_a.setSubtractTotalMean(true);
		aggr_a.setActiveVarianceThreshold(MotionAuthenticationParameters.activityVarianceThreshold);
		aggr_b.setOffset(0);
		aggr_b.setMultiplicator(1/128f);
		aggr_b.setSubtractTotalMean(true);
		aggr_b.setActiveVarianceThreshold(MotionAuthenticationParameters.activityVarianceThreshold);
		
		MotionAuthenticationProtocol2 ma1 = new MotionAuthenticationProtocol2(MotionAuthenticationParameters.samplerate, MotionAuthenticationParameters.fftMatchesWindowSize,
				MotionAuthenticationParameters.fftMatchesQuantizationLevels, MotionAuthenticationParameters.fftMatchesCandidatesPerRound,
				MotionAuthenticationParameters.fftMatchesCutOffFrequenecy, MotionAuthenticationParameters.fftMatchesWindowOverlap,
				MotionAuthenticationParameters.fftMatchesThreshold,
				minmatchingparts, true); 
		MotionAuthenticationProtocol2 ma2 = new MotionAuthenticationProtocol2(MotionAuthenticationParameters.samplerate, MotionAuthenticationParameters.fftMatchesWindowSize,
				MotionAuthenticationParameters.fftMatchesQuantizationLevels, MotionAuthenticationParameters.fftMatchesCandidatesPerRound,
				MotionAuthenticationParameters.fftMatchesCutOffFrequenecy, MotionAuthenticationParameters.fftMatchesWindowOverlap,
				MotionAuthenticationParameters.fftMatchesThreshold,
				minmatchingparts, true); 
		aggr_a.addNextStageSamplesSink(ma1);
		aggr_b.addNextStageSamplesSink(ma2);
		
		r.simulateSampling();
	}
}
