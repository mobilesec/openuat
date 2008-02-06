/* Copyright Rene Mayrhofer
 * File created 2008-02-04
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.sensors.test;

import org.openuat.sensors.TimeSeriesAlignment;

import junit.framework.Assert;
import junit.framework.TestCase;

public class TimeSeriesAlignmentTest extends TestCase {
	private static final int NUMSAMPLES = 10;
	
	private static double[][] axis_x_2d, axis_y_2d;
	private static double[][] axis_xm_2d, axis_ym_2d;
	private static double[][] plane_xy_2d, plane_xy_rot_2d, plane_yx_2d;

	private static double[][] axis_x, axis_y, axis_z;
	private static double[][] axis_xm, axis_ym, axis_zm;
	private static double[][] plane_xy, plane_xz, plane_yz;
	private static double[][] plane_xy_rot, plane_xz_rot, plane_yz_rot;
	private static double[][] plane_yx, plane_zx, plane_zy;
	private static double[][] helix_xy, helix_xz, helix_yz;
	
	static {
		axis_x_2d = new double[NUMSAMPLES][];
		axis_y_2d = new double[NUMSAMPLES][];
		axis_xm_2d = new double[NUMSAMPLES][];
		axis_ym_2d = new double[NUMSAMPLES][];
		plane_xy_2d = new double[NUMSAMPLES][];
		plane_xy_rot_2d = new double[NUMSAMPLES][];
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

		for (int i=0; i<NUMSAMPLES; i++) {
			axis_x_2d[i] = new double[2];
			axis_y_2d[i] = new double[2];
			axis_xm_2d[i] = new double[2];
			axis_ym_2d[i] = new double[2];
			plane_xy_2d[i] = new double[2];
			plane_xy_rot_2d[i] = new double[2];
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
			plane_xy_rot_2d[i][0] = Math.sin(2*Math.PI*i/NUMSAMPLES+Math.PI/2);
			plane_xy_rot_2d[i][1] = Math.cos(2*Math.PI*i/NUMSAMPLES+Math.PI/2);
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
		}
	}

	private TimeSeriesAlignment a_axis_x_2d, a_axis_y_2d, 
	a_plane_xy_2d, a_plane_xy_rot_2d, a_plane_yx_2d,
		a_axis_x, a_axis_y, a_axis_z, a_plane_xy, a_plane_xz, a_plane_yz,
		a_plane_xy_rot, a_plane_xz_rot, a_plane_yz_rot, a_plane_yx, a_plane_zx, a_plane_zy,
		a_helix_xy, a_helix_xz, a_helix_yz; 

	@Override
	public void setUp() {
		a_axis_x_2d = new TimeSeriesAlignment(axis_x_2d); 
		a_axis_y_2d = new TimeSeriesAlignment(axis_y_2d); 
		a_plane_xy_2d = new TimeSeriesAlignment(plane_xy_2d); 
		a_plane_xy_rot_2d = new TimeSeriesAlignment(plane_xy_rot_2d); 
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
	}
	
	private void helper_testNoRotation(TimeSeriesAlignment autoAlignment) {
		TimeSeriesAlignment.Alignment a = autoAlignment.alignWith(autoAlignment);
		Assert.assertEquals("Delta alpha is not correct", 0, a.delta_alpha, 0.001);
		Assert.assertEquals("Delta beta is not correct", 0, a.delta_alpha, 0.001);
		Assert.assertEquals("Error after rotational alignment should be zero", 0, a.error, 0.001);
		Assert.assertEquals("Not all samples processed", NUMSAMPLES, a.numSamples);
	}
	
	public void testNoRotation() {
		helper_testNoRotation(a_axis_x_2d);
		helper_testNoRotation(a_axis_y_2d);
		helper_testNoRotation(a_plane_xy_2d);
		helper_testNoRotation(a_plane_xy_rot_2d);

		helper_testNoRotation(a_axis_x);
		helper_testNoRotation(a_axis_y);
		helper_testNoRotation(a_axis_z);
		helper_testNoRotation(a_plane_xy);
		helper_testNoRotation(a_plane_xz);
		helper_testNoRotation(a_plane_yz);
		helper_testNoRotation(a_helix_xy);
		helper_testNoRotation(a_helix_xz);
		helper_testNoRotation(a_helix_yz);
	}
	
	private void helper_testRotation(TimeSeriesAlignment a1, TimeSeriesAlignment a2,
			double expectedAlpha, double expectedBeta) {
		TimeSeriesAlignment.Alignment a = a1.alignWith(a2);
		Assert.assertEquals("Delta alpha is not correct", expectedAlpha, a.delta_alpha, 0.001);
		Assert.assertEquals("Delta beta is not correct", expectedBeta, a.delta_beta, 0.001);
		Assert.assertEquals("Error after rotational alignment should be zero", 0, a.error, 0.001);
		Assert.assertEquals("Not all samples processed", NUMSAMPLES, a.numSamples);
	}
	
	public void testExactCopyRotate90() {
		helper_testRotation(a_axis_x_2d, a_axis_y_2d, Math.PI/2, 0);
		helper_testRotation(a_plane_xy_2d, a_plane_xy_rot_2d, -Math.PI/2, 0);

		helper_testRotation(a_axis_x, a_axis_y, Math.PI/2, 0);
		helper_testRotation(a_axis_x, a_axis_z, 0, Math.PI/2);
		helper_testRotation(a_axis_y, a_axis_z, 0, Math.PI/2);
}

	public void testRotateNoExactMatchPossible() {
		TimeSeriesAlignment.Alignment a = a_plane_xy_2d.alignWith(a_plane_yx_2d);
		Assert.assertTrue("Error after rotational alignment should not be zero", Math.abs(0 - a.error) > 0.001);
	}
}
