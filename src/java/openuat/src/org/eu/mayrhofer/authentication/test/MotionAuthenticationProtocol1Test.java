/* Copyright Rene Mayrhofer
 * File created 2006-09-03
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.authentication.test;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.zip.GZIPInputStream;

import junit.framework.Assert;
import junit.framework.TestCase;

import org.eu.mayrhofer.authentication.accelerometer.MotionAuthenticationProtocol1;
import org.eu.mayrhofer.sensors.AsciiLineReaderBase;
import org.eu.mayrhofer.sensors.ParallelPortPWMReader;
import org.eu.mayrhofer.sensors.TimeSeriesAggregator;

public class MotionAuthenticationProtocol1Test extends TestCase {

	private static final int samplerate = 128; // Hz
	private static final int windowsize = samplerate / 2; // 1/2 second
	private static final int minsegmentsize = windowsize; // 1/2 second
	private static final double varthreshold = 750;
	
	private TimeSeriesAggregator aggr_a, aggr_b;
	private Protocol1Hooks prot1_a, prot1_b;
	
	public void setUp() throws IOException {
		aggr_a = new TimeSeriesAggregator(3, windowsize, minsegmentsize);
		aggr_b = new TimeSeriesAggregator(3, windowsize, minsegmentsize);
		aggr_a.setOffset(0);
		aggr_a.setMultiplicator(1 / 128f);
		aggr_a.setSubtractTotalMean(true);
		aggr_a.setActiveVarianceThreshold(varthreshold);
		aggr_b.setOffset(0);
		aggr_b.setMultiplicator(1 / 128f);
		aggr_b.setSubtractTotalMean(true);
		aggr_b.setActiveVarianceThreshold(varthreshold);

		/*
		 * 2: construct the two prototol instances: two different variants, each
		 * with two sides
		 */
		prot1_a = new Protocol1Hooks();
		prot1_b = new Protocol1Hooks();

		/* 3: register the protocols with the respective sides */
		aggr_a.addNextStageSegmentsSink(prot1_a);
		aggr_b.addNextStageSegmentsSink(prot1_b);

		/*
		 * 4: authenticate for protocol variant 1 (variant 2 doesn't need this
		 * step)
		 */
		prot1_a.setContinuousChecking(true);
		prot1_b.setContinuousChecking(true);
		prot1_a.startServer();
		prot1_b.startAuthentication("localhost");
	}
	
	public void tearDown() {
	}
	
	public void testSoftRealtime() throws IOException {
		// just read from the file
		FileInputStream in = new FileInputStream("tests/motionauth/negative/1.gz");
		AsciiLineReaderBase reader1 = new ParallelPortPWMReader(new GZIPInputStream(in), samplerate);

		reader1.addSink(new int[] { 0, 1, 2 }, aggr_a.getInitialSinks());
		reader1.addSink(new int[] { 4, 5, 6 }, aggr_b.getInitialSinks());
		
		long startTime = System.currentTimeMillis();
		reader1.start();
		boolean end = false;
		while (!end && System.currentTimeMillis() - startTime < 10000) {
			if (prot1_a.numSucceeded > 0 && prot1_b.numSucceeded > 0 )
				end = true;
			if (prot1_a.numFailed > 0 && prot1_b.numFailed > 0 )
				end = true;
		}
		reader1.stop();
		Assert.assertTrue("Protocol did not finish within time limit", end);
	}

	private class Protocol1Hooks extends MotionAuthenticationProtocol1 {
		public int numProgress = 0, numSucceeded = 0, numFailed = 0;
		
		protected Protocol1Hooks() {
			super(false);
		}
		
		protected void protocolSucceededHook(InetAddress remote, 
				Object optionalRemoteId, String optionalParameterFromRemote, 
				byte[] sharedSessionKey, Socket toRemote) {
			numSucceeded++;
		}		

		protected void protocolFailedHook(InetAddress remote, Object optionalRemoteId, 
				Exception e, String message) {
			numFailed++;
		}
		
		protected void protocolProgressHook(InetAddress remote, 
				Object optionalRemoteId, int cur, int max, String message) {
			numProgress++;
		}		
	}
}
