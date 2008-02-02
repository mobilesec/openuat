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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.StringTokenizer;
import java.util.zip.GZIPInputStream;

import junit.framework.Assert;
import junit.framework.TestCase;

import org.openuat.authentication.accelerometer.ShakeWellBeforeUseParameters;
import org.openuat.sensors.AsciiLineReaderBase;
import org.openuat.sensors.ParallelPortPWMReader;
import org.openuat.sensors.TimeSeriesAggregator;

public class ShakeWellBeforeUseProtocolTestBase extends TestCase {

	// only allow to take this much more time than the data set is long --> "soft-realtime"
	protected static final int MAX_PROTOCOL_LATENCY_SECONDS = 5;
	
	protected TimeSeriesAggregator aggr_a, aggr_b;
	
	protected int numSucceeded, numFailed, numProgress;
	
	protected boolean classIsReadyForTests = false;
	
	@Override
	public void setUp() throws IOException {
		aggr_a = new TimeSeriesAggregator(3, ShakeWellBeforeUseParameters.activityDetectionWindowSize, ShakeWellBeforeUseParameters.activityMinimumSegmentSize, -1);
		aggr_b = new TimeSeriesAggregator(3, ShakeWellBeforeUseParameters.activityDetectionWindowSize, ShakeWellBeforeUseParameters.activityMinimumSegmentSize, -1);
		aggr_a.setOffset(0);
		aggr_a.setMultiplicator(1 / 128f);
		aggr_a.setSubtractTotalMean(true);
		aggr_a.setActiveVarianceThreshold(ShakeWellBeforeUseParameters.activityVarianceThreshold);
		aggr_b.setOffset(0);
		aggr_b.setMultiplicator(1 / 128f);
		aggr_b.setSubtractTotalMean(true);
		aggr_b.setActiveVarianceThreshold(ShakeWellBeforeUseParameters.activityVarianceThreshold);
		
		numSucceeded = 0;
		numFailed = 0;
		numProgress = 0;
		
		classIsReadyForTests = false;
	}
	
	@Override
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
			AsciiLineReaderBase reader1 = new ParallelPortPWMReader(new GZIPInputStream(in), ShakeWellBeforeUseParameters.samplerate);

			reader1.addSink(new int[] { 0, 1, 2 }, aggr_a.getInitialSinks());
			reader1.addSink(new int[] { 4, 5, 6 }, aggr_b.getInitialSinks());
		
			long startTime = System.currentTimeMillis();
			reader1.start();
			boolean end = false;
			while (!end && System.currentTimeMillis() - startTime < timeout) {
				if (numSucceeded > 1 || numFailed > 1 || (numSucceeded > 0 && numFailed > 0))
					end = true;
				Thread.sleep(500);
				
				System.out.println(numSucceeded + " " + numFailed);
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

	public void testAuthenticationSuccess() throws IOException, InterruptedException {
		if (!classIsReadyForTests) 
			return;
			
		String[] testFiles = getTestFiles("tests/motionauth/positive/");
		for (int i=0; i<testFiles.length; i++) {
			numSucceeded = numFailed = numProgress = 0;
			runCase("tests/motionauth/positive/" + testFiles[i]);
			
			Assert.assertEquals("Test file " + testFiles.length + " should have succeeded on both sides, but didn't",
					2, numSucceeded);
			Assert.assertEquals("Test file " + testFiles.length + " should not have failed on either side, but did",
					0, numFailed);
		}
	}

	public void testAuthenticationFailure() throws IOException, InterruptedException {
		if (!classIsReadyForTests) 
			return;

		String[] testFiles = getTestFiles("tests/motionauth/negative/");
		for (int i=0; i<testFiles.length; i++) {
			numSucceeded = numFailed = numProgress = 0;
			runCase("tests/motionauth/negative/" + testFiles[i]);
			
			Assert.assertEquals("Test file " + testFiles.length + " should not have succeeded on either sides, but did",
					0, numSucceeded);
			Assert.assertEquals("Test file " + testFiles.length + " should have failed on both side, but didn't",
					2, numFailed);
		}
	}

	/** This is a small helper to get all *.gz files from a directory. */
	private String[] getTestFiles(String directory) {
		File dir = new File(directory);
		String[] testFiles = dir.list(new FilenameFilter() { 
			public boolean accept(File dir, String name) {
				return name.endsWith(".gz");
			}
		});
		return testFiles;
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
