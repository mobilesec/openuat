/* Copyright Rene Mayrhofer
 * File created 2007-05-12
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.features.test;

import org.apache.log4j.Logger;
import org.openuat.features.FPIntFFT;

import junit.framework.Assert;
import junit.framework.TestCase;

public class FPIntFFTTest extends TestCase {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger("org.openuat.features.test.FPIntFFTTest" /*FPIntFFTTest.class*/);

	private final static int FFT_SIZE = 128;
	private final static short log2FFT = 7;
	private final static int N = (2 * FFT_SIZE);
	private final static short log2N = (log2FFT + 1);
	private final static int FREQUENCY = 5;
	private final static int AMPLITUDE = 12288;
	
	private short x[];
	
	private short fx[];
	
	public void setUp() {
		x = new short[N];
		fx = new short[N];
		logger.debug("Signal: ");
		for (int i=0; i<N; i++) {
			x[i] = (short) (AMPLITUDE*Math.cos(i*FREQUENCY*(2*Math.PI)/N));
			// a particular way of populating the array is required...
			if ((i & 0x01) == 1)
				fx[(N+i)>>1] = x[i];
			else
				fx[i>>1] = x[i];
			logger.debug(i + " " + x[i]);
		}
	}
	
	public void testForwardAndReverseMatch() {
		FPIntFFT.fix_fftr(fx, log2N, false);
		
		if (logger.isDebugEnabled()) {
			logger.debug("Spectrum: ");
			for (int i=0; i<N/2; i++)
				logger.debug(i + " " + fx[i]);
		}
		
		int scale = FPIntFFT.fix_fftr(fx, log2N, true);
		logger.debug("scale=" + scale);
		logger.debug("Regenerated signal : ");
		int diff = 0;
		for (int i=0; i<N; i++) {
			int sample;
			if ((i & 0x01) == 1)
				sample = fx[(N+i)>>1] << scale;
			else
				sample = fx[i>>1] << scale;
			logger.debug(i + " " + sample);
			diff += Math.abs(x[i]-sample);
		}
		Assert.assertEquals("Error of forward and reverse FFT too large", 0, diff/(double)N, 0.0001d);
	}
}
