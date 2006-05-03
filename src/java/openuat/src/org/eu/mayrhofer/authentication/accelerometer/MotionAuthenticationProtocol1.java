/* Copyright Rene Mayrhofer
 * File created 2006-05-03
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.authentication.accelerometer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

import org.apache.log4j.Logger;
import org.eu.mayrhofer.authentication.DHOverTCPWithVerification;
import org.eu.mayrhofer.sensors.Coherence;
import org.eu.mayrhofer.sensors.ParallelPortPWMReader;
import org.eu.mayrhofer.sensors.SegmentsSink;
import org.eu.mayrhofer.sensors.TimeSeriesAggregator;

/** This is the first variant of the motion authentication protocol.
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class MotionAuthenticationProtocol1 extends DHOverTCPWithVerification {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(MotionAuthenticationProtocol1.class);

	public static final int TcpPort = 54322;
	
	private double[] localSegment = null;
	
	private double[] remoteSegment = null;
	
	private double coherenceThreshold = 0.25;
	
	private Thread interlockRunner = null;
	
	public MotionAuthenticationProtocol1(boolean useJSSE) {
		super(TcpPort, false, useJSSE);
	}
	
	/** Called by the base class when the object is reset to idle state. */
	protected void resetHook() {
		// idle again --> no segments to compare
		localSegment = null;
		remoteSegment = null;
	}
	
	/** Called by the base class when the whole authentication protocol succeeded. */
	protected void protocolSucceededHook(InetAddress remote, 
			Object optionalRemoteId, String optionalParameterFromRemote, 
			byte[] sharedSessionKey) {
		// nothing special to do, events have already been emitted by the base class
	}
	
	/** Called by the base class when the whole authentication protocol failed. */
	protected void protocolFailedHook(InetAddress remote, Object optionalRemoteId, 
			Exception e, String message) {
		// nothing special to do, events have already been emitted by the base class
	}
	
	/** Called by the base class when the whole authentication protocol shows progress. */
	protected void protocolProgressHook(InetAddress remote, 
			Object optionalRemoteId, int cur, int max, String message) {
		// nothing special to do, events have already been emitted by the base class
	}
	
	/** Called by the base class when shared keys have been established and should be verified now.
	 * In this implementation, verification is done listening for significant motion segments and
	 * exchanging them via interlock. 
	 */
	protected void startVerification(byte[] sharedAuthenticationKey, 
			InetAddress remote, String param, Socket socketToRemote) {
		interlockRunner = new Thread(new AsyncInterlockHelper());
		interlockRunner.start();
	}

	private boolean checkCoherence() {
		if (localSegment == null || remoteSegment == null) {
			throw new RuntimeException("Did not yet receive both segments, skipping comparing for now");
		}
		
		int len = localSegment.length <= remoteSegment.length ? localSegment.length : remoteSegment.length;
		System.out.println("Using " + len + " samples for coherence computation");
		double[] s1 = new double[len];
		double[] s2 = new double[len];
		for (int i=0; i<len; i++) {
			s1[i] = localSegment[i];
			s2[i] = remoteSegment[i];
		}
		double[] coherence = Coherence.cohere(s1, s2, 128, 0);
		
		double coherenceMean = Coherence.mean(coherence);
		System.out.println("Coherence mean: " + coherenceMean);
		
		return coherenceMean > coherenceThreshold;
	}
	
	private class ActiveSegmentsListener implements SegmentsSink {
		/** The implementation of SegmentsSink.addSegment. It will be called whenever
		 * a significant active segment has been sampled completely, i.e. when the
		 * source has become quiescent again.
		 */
		public void addSegment(double[] segment, int startIndex) {
			logger.info("Received segment of size " + segment.length + " starting at index " + startIndex);
			synchronized (localSegment) {
				localSegment = segment;
				localSegment.notify();
			}
		}
	}
	
	private class AsyncInterlockHelper implements Runnable {
		public void run() {
			//while (remoteSegment == null) {
				// first wait for the local segment to be received to start the interlock protocol
			synchronized (localSegment) {
				while (localSegment == null) {
					try {
						localSegment.wait();
					}
					catch (InterruptedException e) {}
				}
			}

			if (remoteSegment != null) {
				// ok, remote segment already received, can compare
				System.out.println("COHERENCE MATCH: " + checkCoherence());
			}
			//}
		}
	}
	
	
	/////////////////// testing code begins here ///////////////
	public void main(String[] args) throws IOException {
		int samplerate = 128; // Hz
		int windowsize = samplerate/2; // 1/2 second
		int minsegmentsize = windowsize; // 1/2 second
		double varthreshold = 350;
		ParallelPortPWMReader r2_a = new ParallelPortPWMReader(args[0], new int[] {0, 1, 2}, samplerate);
		ParallelPortPWMReader r2_b = new ParallelPortPWMReader(args[0], new int[] {4, 5, 6}, samplerate);
		TimeSeriesAggregator aggr_a = new TimeSeriesAggregator(3, windowsize, minsegmentsize);
		TimeSeriesAggregator aggr_b = new TimeSeriesAggregator(3, windowsize, minsegmentsize);
		r2_a.addSink(aggr_a.getInitialSinks());
		r2_b.addSink(aggr_b.getInitialSinks());
		aggr_a.addNextStageSink(new MotionAuthenticationProtocol1(true).new ActiveSegmentsListener());
		aggr_b.addNextStageSink(new MotionAuthenticationProtocol1(true).new ActiveSegmentsListener());
		aggr_a.setOffset(0);
		aggr_a.setMultiplicator(1/128f);
		aggr_a.setSubtractTotalMean(true);
		aggr_a.setActiveVarianceThreshold(varthreshold);
		aggr_b.setOffset(0);
		aggr_b.setMultiplicator(1/128f);
		aggr_b.setSubtractTotalMean(true);
		aggr_b.setActiveVarianceThreshold(varthreshold);
		r2_a.simulateSampling();
		r2_b.simulateSampling();
	}
}
