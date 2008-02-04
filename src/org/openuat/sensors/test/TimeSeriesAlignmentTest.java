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
	private static double[][] rotate90_1, rotate90_2, rotate90_3;
	
	static {
		rotate90_1 = new double[10][];
		rotate90_2 = new double[10][];
		rotate90_3 = new double[10][];
		for (int i=0; i<10; i++) {
			rotate90_1[i] = new double[3];
			rotate90_2[i] = new double[3];
			rotate90_3[i] = new double[3];
			
			rotate90_1[i][0] = Math.sin(Math.PI*i/5);
			rotate90_1[i][1] = Math.cos(Math.PI*i/5);
			rotate90_1[i][2] = 0;

			rotate90_2[i][0] = rotate90_1[i][1]; 
			rotate90_2[i][1] = rotate90_1[i][2]; 
			rotate90_2[i][2] = rotate90_1[i][0]; 

			rotate90_3[i][0] = rotate90_1[i][2]; 
			rotate90_3[i][1] = rotate90_1[i][0]; 
			rotate90_3[i][2] = rotate90_1[i][1]; 
		}
	}

	private TimeSeriesAlignment rotate90_a1, rotate90_a2, rotate90_a3; 

	@Override
	public void setUp() {
		rotate90_a1 = new TimeSeriesAlignment(rotate90_1); 
		rotate90_a2 = new TimeSeriesAlignment(rotate90_2); 
		rotate90_a3 = new TimeSeriesAlignment(rotate90_3); 
	}
	
	public void testNoRotation() {
		TimeSeriesAlignment.Alignment a = rotate90_a1.alignWith(rotate90_1);
		Assert.assertEquals("Delta alpha is not correct", 0, a.delta_alpha, 0.001);
		Assert.assertEquals("Delta beta is not correct", 0, a.delta_alpha, 0.001);
		Assert.assertEquals("Error after rotational alignment should be zero", 0, a.error, 0.001);
		Assert.assertEquals("Not all samples processed", rotate90_1.length, a.numSamples);

		a = rotate90_a2.alignWith(rotate90_2);
		Assert.assertEquals("Delta alpha is not correct", 0, a.delta_alpha, 0.001);
		Assert.assertEquals("Delta beta is not correct", 0, a.delta_alpha, 0.001);
		Assert.assertEquals("Error after rotational alignment should be zero", 0, a.error, 0.001);
		Assert.assertEquals("Not all samples processed", rotate90_1.length, a.numSamples);

		a = rotate90_a3.alignWith(rotate90_3);
		Assert.assertEquals("Delta alpha is not correct", 0, a.delta_alpha, 0.001);
		Assert.assertEquals("Delta beta is not correct", 0, a.delta_alpha, 0.001);
		Assert.assertEquals("Error after rotational alignment should be zero", 0, a.error, 0.001);
		Assert.assertEquals("Not all samples processed", rotate90_1.length, a.numSamples);
	}
	
	public void testRotate90() {
		TimeSeriesAlignment.Alignment a = rotate90_a1.alignWith(rotate90_2);
		Assert.assertEquals("Delta alpha is not correct", Math.PI/2, a.delta_alpha, 0.001);
		Assert.assertEquals("Delta beta is not correct", 0, a.delta_alpha, 0.001);
		Assert.assertEquals("Error after rotational alignment should be zero", 0, a.error, 0.001);
		Assert.assertEquals("Not all samples processed", rotate90_1.length, a.numSamples);

		a = rotate90_a1.alignWith(rotate90_3);
		Assert.assertEquals("Delta alpha is not correct", Math.PI/2, a.delta_alpha, 0.001);
		Assert.assertEquals("Delta beta is not correct", 0, a.delta_alpha, 0.001);
		Assert.assertEquals("Error after rotational alignment should be zero", 0, a.error, 0.001);
		Assert.assertEquals("Not all samples processed", rotate90_1.length, a.numSamples);

		a = rotate90_a2.alignWith(rotate90_1);
		Assert.assertEquals("Delta alpha is not correct", -Math.PI/2, a.delta_alpha, 0.001);
		Assert.assertEquals("Delta beta is not correct", 0, a.delta_alpha, 0.001);
		Assert.assertEquals("Error after rotational alignment should be zero", 0, a.error, 0.001);
		Assert.assertEquals("Not all samples processed", rotate90_1.length, a.numSamples);

		a = rotate90_a2.alignWith(rotate90_3);
		Assert.assertEquals("Delta alpha is not correct", Math.PI/2, a.delta_alpha, 0.001);
		Assert.assertEquals("Delta beta is not correct", 0, a.delta_alpha, 0.001);
		Assert.assertEquals("Error after rotational alignment should be zero", 0, a.error, 0.001);
		Assert.assertEquals("Not all samples processed", rotate90_1.length, a.numSamples);

		a = rotate90_a3.alignWith(rotate90_1);
		Assert.assertEquals("Delta alpha is not correct", Math.PI/2, a.delta_alpha, 0.001);
		Assert.assertEquals("Delta beta is not correct", 0, a.delta_alpha, 0.001);
		Assert.assertEquals("Error after rotational alignment should be zero", 0, a.error, 0.001);
		Assert.assertEquals("Not all samples processed", rotate90_1.length, a.numSamples);

		a = rotate90_a3.alignWith(rotate90_2);
		Assert.assertEquals("Delta alpha is not correct", Math.PI/2, a.delta_alpha, 0.001);
		Assert.assertEquals("Delta beta is not correct", 0, a.delta_alpha, 0.001);
		Assert.assertEquals("Error after rotational alignment should be zero", 0, a.error, 0.001);
		Assert.assertEquals("Not all samples processed", rotate90_1.length, a.numSamples);
	}
}
