/* Copyright Rene Mayrhofer
 * File created 2007-06-07
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.features.test;

import junit.framework.Assert;
import junit.framework.TestCase;

import org.openuat.features.TimeSeriesUtil;

public class TimeSeriesUtilTest extends TestCase {
	public void testEncodeDecode() {
		// test vector
		double[] a = new double[128];
		for (int i=0; i<a.length; i++)
			a[i] = Math.sin(i*2*Math.PI/a.length);
		
		byte[] b = TimeSeriesUtil.encodeVector(a);
		Assert.assertNotNull("encoding failed", b);

		double[] a2 = TimeSeriesUtil.decodeVector(b);
		Assert.assertNotNull("decoding failed", a2);
		Assert.assertEquals("decoded vector has different length", a.length, a2.length);
		
		for (int i=0; i<a.length; i++)
			Assert.assertEquals("element " + i + " does not match", a[i], a2[i], 0.00001);
	}
}
