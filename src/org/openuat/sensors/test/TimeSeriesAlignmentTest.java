/* Copyright Rene Mayrhofer
 * File created 2008-02-04
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.sensors.test;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;

import org.openuat.authentication.accelerometer.ShakeWellBeforeUseParameters;
import org.openuat.sensors.AsciiLineReaderBase;
import org.openuat.sensors.ParallelPortPWMReader;
import org.openuat.sensors.TimeSeriesAlignment;

import junit.framework.Assert;
import junit.framework.TestCase;

public class TimeSeriesAlignmentTest extends TestCase {
	private static final int NUMSAMPLES = 10;
	
	private static final double POSIIVE_SAMPLES_ALIGNMENT_ERROR_THRESHOLD = 4000;
	private static final double NEGATIVE_SAMPLES_ALIGNMENT_ERROR_THRESHOLD = 10000;
	
	private static double[][] axis_x_2d, axis_y_2d;
	private static double[][] axis_xm_2d, axis_ym_2d;
	private static double[][] plane_xy_2d, plane_xy_rot90_2d, plane_xy_rot315_2d, plane_yx_2d;

	private static double[][] axis_x, axis_y, axis_z;
	private static double[][] axis_xm, axis_ym, axis_zm;
	private static double[][] plane_xy, plane_xz, plane_yz;
	private static double[][] plane_xy_rot, plane_xz_rot, plane_yz_rot;
	private static double[][] plane_yx, plane_zx, plane_zy;
	private static double[][] helix_xy, helix_xz, helix_yz;
	
	private static double[][] offcenterplane_xy, offcenterplane_xz, offcenterplane_yz;
	
	static {
		axis_x_2d = new double[NUMSAMPLES][];
		axis_y_2d = new double[NUMSAMPLES][];
		axis_xm_2d = new double[NUMSAMPLES][];
		axis_ym_2d = new double[NUMSAMPLES][];
		plane_xy_2d = new double[NUMSAMPLES][];
		plane_xy_rot90_2d = new double[NUMSAMPLES][];
		plane_xy_rot315_2d = new double[NUMSAMPLES][];
		plane_yx_2d = new double[NUMSAMPLES][];

		axis_x = new double[NUMSAMPLES][];
		axis_y = new double[NUMSAMPLES][];
		axis_z = new double[NUMSAMPLES][];
		axis_xm = new double[NUMSAMPLES][];
		axis_ym = new double[NUMSAMPLES][];
		axis_zm = new double[NUMSAMPLES][];
		plane_xy = new double[NUMSAMPLES][];
		plane_xz = new double[NUMSAMPLES][];
		plane_yz = new double[NUMSAMPLES][];
		plane_xy_rot = new double[NUMSAMPLES][];
		plane_xz_rot = new double[NUMSAMPLES][];
		plane_yz_rot = new double[NUMSAMPLES][];
		plane_yx = new double[NUMSAMPLES][];
		plane_zx = new double[NUMSAMPLES][];
		plane_zy = new double[NUMSAMPLES][];
		helix_xy = new double[NUMSAMPLES][];
		helix_xz = new double[NUMSAMPLES][];
		helix_yz = new double[NUMSAMPLES][];

		offcenterplane_xy = new double[NUMSAMPLES][];
		offcenterplane_xz = new double[NUMSAMPLES][];
		offcenterplane_yz = new double[NUMSAMPLES][];
		// TODO: 45° rotated offcenterplane
		
		for (int i=0; i<NUMSAMPLES; i++) {
			axis_x_2d[i] = new double[2];
			axis_y_2d[i] = new double[2];
			axis_xm_2d[i] = new double[2];
			axis_ym_2d[i] = new double[2];
			plane_xy_2d[i] = new double[2];
			plane_xy_rot90_2d[i] = new double[2];
			plane_xy_rot315_2d[i] = new double[2];
			plane_yx_2d[i] = new double[2];

			axis_x[i] = new double[3];
			axis_y[i] = new double[3];
			axis_z[i] = new double[3];
			axis_xm[i] = new double[3];
			axis_ym[i] = new double[3];
			axis_zm[i] = new double[3];
			plane_xy[i] = new double[3];
			plane_xz[i] = new double[3];
			plane_yz[i] = new double[3];
			plane_xy_rot[i] = new double[3];
			plane_xz_rot[i] = new double[3];
			plane_yz_rot[i] = new double[3];
			plane_yx[i] = new double[3];
			plane_zx[i] = new double[3];
			plane_zy[i] = new double[3];
			helix_xy[i] = new double[3];
			helix_xz[i] = new double[3];
			helix_yz[i] = new double[3];

			offcenterplane_xy[i] = new double[3];
			offcenterplane_xz[i] = new double[3];
			offcenterplane_yz[i] = new double[3];

			axis_x_2d[i][0] = Math.sin(2*Math.PI*i/NUMSAMPLES);
			axis_x_2d[i][1] = 0;
			axis_y_2d[i][0] = 0;
			axis_y_2d[i][1] = Math.sin(2*Math.PI*i/NUMSAMPLES);
			axis_xm_2d[i][0] = -Math.sin(2*Math.PI*i/NUMSAMPLES);
			axis_xm_2d[i][1] = 0;
			axis_ym_2d[i][0] = 0;
			axis_ym_2d[i][1] = -Math.sin(2*Math.PI*i/NUMSAMPLES);
			plane_xy_2d[i][0] = Math.sin(2*Math.PI*i/NUMSAMPLES);
			plane_xy_2d[i][1] = Math.cos(2*Math.PI*i/NUMSAMPLES);
			plane_xy_rot90_2d[i][0] = Math.sin(2*Math.PI*i/NUMSAMPLES+Math.PI/2);
			plane_xy_rot90_2d[i][1] = Math.cos(2*Math.PI*i/NUMSAMPLES+Math.PI/2);
			plane_xy_rot315_2d[i][0] = Math.sin(2*Math.PI*i/NUMSAMPLES+Math.PI*7/8);
			plane_xy_rot315_2d[i][1] = Math.cos(2*Math.PI*i/NUMSAMPLES+Math.PI*7/8);
			plane_yx_2d[i][0] = Math.cos(2*Math.PI*i/NUMSAMPLES);
			plane_yx_2d[i][1] = Math.sin(2*Math.PI*i/NUMSAMPLES);
			
			axis_x[i][0] = Math.sin(2*Math.PI*i/NUMSAMPLES);
			axis_x[i][1] = 0;
			axis_x[i][2] = 0;
			axis_y[i][0] = 0;
			axis_y[i][1] = Math.sin(2*Math.PI*i/NUMSAMPLES);
			axis_y[i][2] = 0;
			axis_z[i][0] = 0;
			axis_z[i][1] = 0;
			axis_z[i][2] = Math.sin(2*Math.PI*i/NUMSAMPLES);
			axis_xm[i][0] = -Math.sin(2*Math.PI*i/NUMSAMPLES);
			axis_xm[i][1] = 0;
			axis_xm[i][2] = 0;
			axis_ym[i][0] = 0;
			axis_ym[i][1] = -Math.sin(2*Math.PI*i/NUMSAMPLES);
			axis_ym[i][2] = 0;
			axis_zm[i][0] = 0;
			axis_zm[i][1] = 0;
			axis_z[i][2] = -Math.sin(2*Math.PI*i/NUMSAMPLES);
			plane_xy[i][0] = Math.sin(2*Math.PI*i/NUMSAMPLES);
			plane_xy[i][1] = Math.cos(2*Math.PI*i/NUMSAMPLES);
			plane_xy[i][2] = 0;
			plane_xz[i][0] = Math.sin(2*Math.PI*i/NUMSAMPLES);
			plane_xz[i][1] = 0;
			plane_xz[i][2] = Math.cos(2*Math.PI*i/NUMSAMPLES);
			plane_yz[i][0] = 0;
			plane_yz[i][1] = Math.sin(2*Math.PI*i/NUMSAMPLES);
			plane_yz[i][2] = Math.cos(2*Math.PI*i/NUMSAMPLES);

			plane_xy_rot[i][0] = Math.sin(2*Math.PI*i/NUMSAMPLES+Math.PI/2);
			plane_xy_rot[i][1] = Math.cos(2*Math.PI*i/NUMSAMPLES+Math.PI/2);
			plane_xy_rot[i][2] = 0;
			plane_xz_rot[i][0] = Math.sin(2*Math.PI*i/NUMSAMPLES+Math.PI/2);
			plane_xz_rot[i][1] = 0;
			plane_xz_rot[i][2] = Math.cos(2*Math.PI*i/NUMSAMPLES+Math.PI/2);
			plane_yz_rot[i][0] = 0;
			plane_yz_rot[i][1] = Math.sin(2*Math.PI*i/NUMSAMPLES+Math.PI/2);
			plane_yz_rot[i][2] = Math.cos(2*Math.PI*i/NUMSAMPLES+Math.PI/2);
			plane_yx[i][0] = Math.cos(2*Math.PI*i/NUMSAMPLES);
			plane_yx[i][1] = Math.sin(2*Math.PI*i/NUMSAMPLES);
			plane_yx[i][2] = 0;
			plane_zx[i][0] = Math.cos(2*Math.PI*i/NUMSAMPLES);
			plane_zx[i][1] = 0;
			plane_zx[i][2] = Math.sin(2*Math.PI*i/NUMSAMPLES);
			plane_zy[i][0] = 0;
			plane_zy[i][1] = Math.cos(2*Math.PI*i/NUMSAMPLES);
			plane_zy[i][2] = Math.sin(2*Math.PI*i/NUMSAMPLES);

			helix_xy[i][0] = Math.sin(2*Math.PI*i/NUMSAMPLES);
			helix_xy[i][1] = Math.cos(2*Math.PI*i/NUMSAMPLES);
			helix_xy[i][2] = ((double) i)/NUMSAMPLES;
			helix_xz[i][0] = Math.sin(2*Math.PI*i/NUMSAMPLES);
			helix_xz[i][1] = ((double) i)/NUMSAMPLES;
			helix_xz[i][2] = Math.cos(2*Math.PI*i/NUMSAMPLES);
			helix_yz[i][0] = ((double) i)/NUMSAMPLES;
			helix_yz[i][1] = Math.sin(2*Math.PI*i/NUMSAMPLES);
			helix_yz[i][2] = Math.cos(2*Math.PI*i/NUMSAMPLES);

			offcenterplane_xy[i][0] = Math.sin(2*Math.PI*i/NUMSAMPLES);
			offcenterplane_xy[i][1] = Math.cos(2*Math.PI*i/NUMSAMPLES);
			offcenterplane_xy[i][2] = 1;
			offcenterplane_xz[i][0] = Math.sin(2*Math.PI*i/NUMSAMPLES);
			offcenterplane_xz[i][1] = 1;
			offcenterplane_xz[i][2] = Math.cos(2*Math.PI*i/NUMSAMPLES);
			offcenterplane_yz[i][0] = 1;
			offcenterplane_yz[i][1] = Math.sin(2*Math.PI*i/NUMSAMPLES);
			offcenterplane_yz[i][2] = Math.cos(2*Math.PI*i/NUMSAMPLES);
		}
	}

	private TimeSeriesAlignment a_axis_x_2d, a_axis_y_2d, 
	a_plane_xy_2d, a_plane_xy_rot90_2d, a_plane_xy_rot315_2d, a_plane_yx_2d,
		a_axis_x, a_axis_y, a_axis_z, a_plane_xy, a_plane_xz, a_plane_yz,
		a_plane_xy_rot, a_plane_xz_rot, a_plane_yz_rot, a_plane_yx, a_plane_zx, a_plane_zy,
		a_helix_xy, a_helix_xz, a_helix_yz,
		a_offcenterplane_xy, a_offcenterplane_xz, a_offcenterplane_yz,
		sample1, sample2;

	@Override
	public void setUp() {
		a_axis_x_2d = new TimeSeriesAlignment(axis_x_2d); 
		a_axis_y_2d = new TimeSeriesAlignment(axis_y_2d); 
		a_plane_xy_2d = new TimeSeriesAlignment(plane_xy_2d); 
		a_plane_xy_rot90_2d = new TimeSeriesAlignment(plane_xy_rot90_2d); 
		a_plane_xy_rot315_2d = new TimeSeriesAlignment(plane_xy_rot315_2d); 
		a_plane_yx_2d = new TimeSeriesAlignment(plane_yx_2d); 

		a_axis_x = new TimeSeriesAlignment(axis_x); 
		a_axis_y = new TimeSeriesAlignment(axis_y); 
		a_axis_z = new TimeSeriesAlignment(axis_z); 
		a_plane_xy = new TimeSeriesAlignment(plane_xy); 
		a_plane_xz = new TimeSeriesAlignment(plane_xz); 
		a_plane_yz = new TimeSeriesAlignment(plane_yz); 
		a_plane_xy_rot = new TimeSeriesAlignment(plane_xy_rot); 
		a_plane_xz_rot = new TimeSeriesAlignment(plane_xz_rot); 
		a_plane_yz_rot = new TimeSeriesAlignment(plane_yz_rot); 
		a_plane_yx = new TimeSeriesAlignment(plane_yx); 
		a_plane_zx = new TimeSeriesAlignment(plane_zx); 
		a_plane_zy = new TimeSeriesAlignment(plane_zy); 
		a_helix_xy = new TimeSeriesAlignment(helix_xy); 
		a_helix_xz = new TimeSeriesAlignment(helix_xz); 
		a_helix_yz = new TimeSeriesAlignment(helix_yz); 
		a_offcenterplane_xy = new TimeSeriesAlignment(offcenterplane_xy); 
		a_offcenterplane_xz = new TimeSeriesAlignment(offcenterplane_xz); 
		a_offcenterplane_yz = new TimeSeriesAlignment(offcenterplane_yz); 

		sample1 = new TimeSeriesAlignment(3, ShakeWellBeforeUseParameters.activityDetectionWindowSize, ShakeWellBeforeUseParameters.activityMinimumSegmentSize, ShakeWellBeforeUseParameters.activityMinimumSegmentSize*3);
		sample2 = new TimeSeriesAlignment(3, ShakeWellBeforeUseParameters.activityDetectionWindowSize, ShakeWellBeforeUseParameters.activityMinimumSegmentSize, ShakeWellBeforeUseParameters.activityMinimumSegmentSize*3);
		sample1.setOffset(0);
		sample1.setMultiplicator(1 / 128f);
		sample1.setSubtractTotalMean(true);
		sample1.setActiveVarianceThreshold(ShakeWellBeforeUseParameters.activityVarianceThreshold);
		sample2.setOffset(0);
		sample2.setMultiplicator(1 / 128f);
		sample2.setSubtractTotalMean(true);
		sample2.setActiveVarianceThreshold(ShakeWellBeforeUseParameters.activityVarianceThreshold);
	}
	
	@Override
	public void tearDown() {
		sample1.reset();
		sample2.reset();
	}
	
	private void helper_testNoRotation(TimeSeriesAlignment self, double[][] self2) {
		TimeSeriesAlignment.Alignment a = self.alignWith(self2);
		Assert.assertEquals("Delta alpha is not correct", 0, a.delta_theta, 0.001);
		Assert.assertEquals("Delta beta is not correct", 0, a.delta_theta, 0.001);
		Assert.assertEquals("Error after rotational alignment should be zero", 0, a.error, 0.001);
		Assert.assertEquals("Not all samples processed", NUMSAMPLES, a.numSamples);
	}
	
	public void testNoRotation() {
		helper_testNoRotation(a_axis_x_2d, axis_x_2d);
		helper_testNoRotation(a_axis_y_2d, axis_y_2d);
		helper_testNoRotation(a_plane_xy_2d, plane_xy_2d);
		helper_testNoRotation(a_plane_xy_rot90_2d, plane_xy_rot90_2d);
		helper_testNoRotation(a_plane_xy_rot315_2d, plane_xy_rot315_2d);

		helper_testNoRotation(a_axis_x, axis_x);
		helper_testNoRotation(a_axis_y, axis_y);
		helper_testNoRotation(a_axis_z, axis_z);
		helper_testNoRotation(a_plane_xy, plane_xy);
		helper_testNoRotation(a_plane_xz, plane_xz);
		helper_testNoRotation(a_plane_yz, plane_yz);
		helper_testNoRotation(a_helix_xy, helix_xy);
		helper_testNoRotation(a_helix_xz, helix_xz);
		helper_testNoRotation(a_helix_yz, helix_yz);
	}
	
	private void helper_testRotation(TimeSeriesAlignment a1, double[][] a2,
			double expectedTheta, double expectedPhi, 
			int expectedQuadRotPsi, int expectedQuadRotTheta, int expectedQuadRotPhi) {
		TimeSeriesAlignment.Alignment a = a1.alignWith(a2);
		Assert.assertEquals("Delta theta is not correct", expectedTheta, a.delta_theta, 0.001);
		Assert.assertEquals("Delta phi is not correct", expectedPhi, a.delta_phi, 0.001);
		Assert.assertEquals("Quadrot psi is not correct", expectedQuadRotPsi, a.quadrotPsi);
		Assert.assertEquals("Quadrot theta is not correct", expectedQuadRotTheta, a.quadrotTheta);
		Assert.assertEquals("Quadrot phi is not correct", expectedQuadRotPhi, a.quadrotPhi);
		Assert.assertEquals("Error after rotational alignment should be zero", 0, a.error, 0.001);
		Assert.assertEquals("Not all samples processed", NUMSAMPLES, a.numSamples);
	}
	
	public void testExactCopyRotate90() {
		helper_testRotation(a_axis_x_2d, axis_y_2d, Math.PI/2, 0, 0, 0, 0);
		helper_testRotation(a_axis_y_2d, axis_x_2d, -Math.PI/2, 0, 0, 0, 0);
		helper_testRotation(a_plane_xy_2d, plane_xy_rot90_2d, -Math.PI/2, 0, 0, 0, 0);
		helper_testRotation(a_plane_xy_rot90_2d, plane_xy_2d, Math.PI/2, 0, 0, 0, 0);

		helper_testRotation(a_axis_x, axis_y, Math.PI/2, 0, 0, 0, 0);
		helper_testRotation(a_axis_y, axis_x, -Math.PI/2, 0, 0, 0, 0);
		helper_testRotation(a_axis_x, axis_z, 0, Math.PI/2, 0, 0, 0);
		helper_testRotation(a_axis_z, axis_x, 0, -Math.PI/2, 0, 0, 0);
		helper_testRotation(a_axis_y, axis_z, -Math.PI/2, Math.PI/2, 0, 0, 0);
		helper_testRotation(a_axis_z, axis_y, Math.PI/2, -Math.PI/2, 0, 0, 0);

		helper_testRotation(a_plane_xy, plane_xz, Math.PI/2, 0, 0, 1, 0);
		helper_testRotation(a_plane_xy, plane_yz, Math.PI/2, 0, 1, 1, 0);
		helper_testRotation(a_plane_xz, plane_yz, Math.PI/2, 0, 0, 0, 0);
		helper_testRotation(a_offcenterplane_xy, offcenterplane_xz, Math.PI/2, 0, 0, 1, 0);
		helper_testRotation(a_offcenterplane_xy, offcenterplane_yz, Math.PI/2, 0, 1, 1, 0);
		helper_testRotation(a_offcenterplane_xz, offcenterplane_yz, 0, 0, 1, 0, 0);
}

	public void testExactCopyRotateOther() {
		helper_testRotation(a_plane_xy_2d, plane_xy_rot315_2d, -Math.PI*7/8, 0, 0, 0, 0);
		helper_testRotation(a_plane_xy_rot315_2d, plane_xy_2d, Math.PI*7/8, 0, 0, 0, 0);
		helper_testRotation(a_plane_xy_rot90_2d, plane_xy_rot315_2d, -Math.PI*3/8, 0, 0, 0, 0);
		helper_testRotation(a_plane_xy_rot315_2d, plane_xy_rot90_2d, Math.PI*3/8, 0, 0, 0, 0);
	}

	public void testRotateNoExactMatchPossible() {
		TimeSeriesAlignment.Alignment a = a_plane_xy_2d.alignWith(plane_yx_2d);
		Assert.assertTrue("Error after rotational alignment should not be zero", Math.abs(0 - a.error) > 0.001);
	}

	protected double runCase(String filename) throws IOException, InterruptedException {
		int dataSetLength = DataFilesHelper.determineDataSetLength(filename);
		System.out.println("Data set is " + dataSetLength + " seconds long");
		// just read from the file
		FileInputStream in = new FileInputStream(filename);
		AsciiLineReaderBase reader1 = new ParallelPortPWMReader(new GZIPInputStream(in), ShakeWellBeforeUseParameters.samplerate);

		reader1.addSink(new int[] { 0, 1, 2 }, sample1.getInitialSinks());
		reader1.addSink(new int[] { 4, 5, 6 }, sample2.getInitialSinks());
	
		reader1.simulateSampling();
		in.close();
		System.gc();
		
		return sample1.alignWith(sample2.getCartesian()).error;
	}

	public void testAuthenticationSuccess() throws IOException, InterruptedException {
		String[] testFiles = DataFilesHelper.getTestFiles("tests/motionauth/positive/");
		for (int i=0; i<testFiles.length; i++) {
			double error = runCase("tests/motionauth/positive/" + testFiles[i]);
			Assert.assertTrue("Error (" + error + ") should have been lower than threshold for file " + 
					"tests/motionauth/positive/" + testFiles[i] + 
					", but wasn't", (error < POSIIVE_SAMPLES_ALIGNMENT_ERROR_THRESHOLD));
			System.out.println("----- TEST SUCCESSFUL: tests/motionauth/positive/" + testFiles[i]);
		}
	}

	/* TODO: This doesn't work with the current alignment code! Need to fix.
	public void testAuthenticationFailure() throws IOException, InterruptedException {
		String[] testFiles = DataFilesHelper.getTestFiles("tests/motionauth/negative/");
		for (int i=0; i<testFiles.length; i++) {
			double error = runCase("tests/motionauth/negative/" + testFiles[i]);
			Assert.assertTrue("Error (" + error + ") should have been higher than threshold for file " + 
					"tests/motionauth/negative/" + testFiles[i] + 
					", but wasn't", (error > NEGATIVE_SAMPLES_ALIGNMENT_ERROR_THRESHOLD));
			System.out.println("----- TEST SUCCESSFUL: tests/motionauth/negative/" + testFiles[i]);
		}
	} */
}
