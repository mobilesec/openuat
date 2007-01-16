/* Copyright Rene Mayrhofer
 * File created 2006-05-01
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.sensors.test;

import org.eu.mayrhofer.sensors.*;

import junit.framework.*;

public class TimeSeriesTest extends TestCase {
	private TimeSeries s;
	
	public void setUp() {
		s = new TimeSeries(10);
		s.addSample(15f, 0);
		for (int i=0; i<10; i++)
			s.addSample(i, i+1);
		for (int i=0; i<10; i++)
			s.addSample(9-i, i+11);
	}
	
	public void testCalculateTotalMean() {
		Assert.assertEquals("Total mean calculated online does not match", (10*9+15)/21d, s.getTotalMean(), 0.0001d);
	}

	public void testCalculateWindowMean() {
		Assert.assertEquals("Window mean calculated online does not match", (10*9/2)/10d, s.getWindowMean(), 0.0001d);
	}

	/*public void testCalculateTotalVariance() {
		Assert.assertEquals("Total variance calculated online does not match", (float) 9*8, s.getTotalVariance(), 0.0001f);
	}*/

	public void testCalculateWindowVariance() {
		double[] arr = s.getSamplesInWindow();
		double mean = 0;
		for (int i=0; i<arr.length; i++) {
			mean += arr[i];
		}
		mean /= arr.length;
		double var2 = 0;
		double sum = 0, sum2 = 0;
		for (int i=0; i<arr.length; i++) {
			double val = arr[i];
			var2 += (val - mean) * (val - mean);
			sum += val;
			sum2 += val * val;
		}
		var2 /= arr.length-1;
		
		Assert.assertEquals("Window variance calculated online does not match", var2, s.getWindowVariance(), 0.0001d);
	}
}
