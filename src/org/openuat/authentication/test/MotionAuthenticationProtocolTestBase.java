/* Copyright Rene Mayrhofer
 * File created 2006-09-03
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.authentication.test;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.StringTokenizer;
import java.util.zip.GZIPInputStream;

import junit.framework.Assert;
import junit.framework.TestCase;

import org.openuat.authentication.accelerometer.MotionAuthenticationParameters;
import org.openuat.sensors.AsciiLineReaderBase;
import org.openuat.sensors.ParallelPortPWMReader;
import org.openuat.sensors.TimeSeriesAggregator;

public class MotionAuthenticationProtocolTestBase extends TestCase {

	// only allow to take this much more time than the data set is long --> "soft-realtime"
	protected static final int MAX_PROTOCOL_LATENCY_SECONDS = 2;
	
	protected TimeSeriesAggregator aggr_a, aggr_b;
	
	protected int numSucceeded, numFailed, numProgress;
	
	protected boolean classIsReadyForTests = false;
	
	public void setUp() throws IOException {
		aggr_a = new TimeSeriesAggregator(3, MotionAuthenticationParameters.activityDetectionWindowSize, MotionAuthenticationParameters.activityMinimumSegmentSize, -1);
		aggr_b = new TimeSeriesAggregator(3, MotionAuthenticationParameters.activityDetectionWindowSize, MotionAuthenticationParameters.activityMinimumSegmentSize, -1);
		aggr_a.setOffset(0);
		aggr_a.setMultiplicator(1 / 128f);
		aggr_a.setSubtractTotalMean(true);
		aggr_a.setActiveVarianceThreshold(MotionAuthenticationParameters.activityVarianceThreshold);
		aggr_b.setOffset(0);
		aggr_b.setMultiplicator(1 / 128f);
		aggr_b.setSubtractTotalMean(true);
		aggr_b.setActiveVarianceThreshold(MotionAuthenticationParameters.activityVarianceThreshold);
		
		numSucceeded = 0;
		numFailed = 0;
		numProgress = 0;
		
		classIsReadyForTests = false;
	}
	
	public void tearDown() {
		classIsReadyForTests = false;
	}
	
	protected void runCase(String filename) throws IOException, InterruptedException {
		if (classIsReadyForTests) {
			int dataSetLength = determineDataSetLength(filename);
			System.out.println("Data set is " + dataSetLength + " seconds long");
			int timeout = (dataSetLength + MAX_PROTOCOL_LATENCY_SECONDS) * 1000;
			// just read from the file
			FileInputStream in = new FileInputStream(filename);
			AsciiLineReaderBase reader1 = new ParallelPortPWMReader(new GZIPInputStream(in), MotionAuthenticationParameters.samplerate);

			reader1.addSink(new int[] { 0, 1, 2 }, aggr_a.getInitialSinks());
			reader1.addSink(new int[] { 4, 5, 6 }, aggr_b.getInitialSinks());
		
			long startTime = System.currentTimeMillis();
			reader1.start();
			boolean end = false;
			while (!end && System.currentTimeMillis() - startTime < timeout) {
				if (numSucceeded > 0 && numSucceeded > 0 )
					end = true;
				if (numFailed > 0 && numFailed > 0 )
					end = true;
				Thread.sleep(500);
			}
			reader1.stop();
			in.close();
			System.gc();
			Assert.assertTrue("Protocol did not finish within time limit", end);
		}
	}
	
	public void testSoftRealtime() throws IOException, InterruptedException {
		runCase("tests/motionauth/negative/1.gz");
	}
	
	/** This helper function returns the length of the data set in seconds. */ 
	private int determineDataSetLength(String filename) throws FileNotFoundException, IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(filename))));
		int firstTimestamp = -1, lastTimestamp = -1;
		String line = in.readLine();
		while (line != null) {
			StringTokenizer st = new StringTokenizer(line, " .", false);
			int timestampSecs = Integer.parseInt(st.nextToken());
			if (firstTimestamp == -1)
				firstTimestamp = timestampSecs;
			if (timestampSecs > lastTimestamp)
				lastTimestamp = timestampSecs;
			line = in.readLine();
		}
		return lastTimestamp - firstTimestamp;
	}
}
