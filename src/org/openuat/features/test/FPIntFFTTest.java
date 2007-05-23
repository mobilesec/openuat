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

	private short x[];
	
	private short fx[];
	
	public void testForwardAndReverseMatch() {
		// do a sweep over different amplitudes, frequencies, and sizes
		for (short log2FFT=4; log2FFT<=10; log2FFT++) {
			int FFT_SIZE = 1 << log2FFT;
			int N = FFT_SIZE*2;
			short log2N = (short) (log2FFT+1);

			logger.info("Testing FFT with " + FFT_SIZE + " coefficients (2^" + log2FFT + ")");
			x = new short[N];
			fx = new short[N];
			
			for (int freq=1; freq<=512; freq++) {
				for (int ampl=64; ampl<=32768; ampl*=2) {
					logger.info("Using test signal with " + freq + "Hz and amplitude " + ampl);
					logger.debug("Signal: ");
					for (int i=0; i<N; i++) {
						x[i] = (short) (ampl*Math.cos(i*freq*(2*Math.PI)/N));
						// a particular way of populating the array is required...
						if ((i & 0x01) == 1)
							fx[(N+i)>>1] = x[i];
						else
							fx[i>>1] = x[i];
						logger.debug(i + " " + x[i]);
					}

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
						// that difference really is a lot, but some signals are not reproduced well...
						if (!(ampl<=128 && FFT_SIZE==64 && i==2) &&
						    !(ampl<=256 && FFT_SIZE==128) &&
						    !(ampl<=512 && FFT_SIZE==128 && i==2) &&
						    !(ampl<=512 && FFT_SIZE==256) &&
						    !(ampl<=2048 && FFT_SIZE==256 && i==2) &&
						    !(ampl<=2048 && FFT_SIZE==512) && 
						    !(ampl<=8192 && FFT_SIZE==512 && i==2) &&
						    !(ampl<=4096 && FFT_SIZE==512 && (freq==163||freq==349)) &&
						    !(ampl<=4096 && FFT_SIZE==1024) &&
						    !(ampl<=8192 && FFT_SIZE==1024 && (i==2 || i==0 || i==1538 || i==1026 || freq==9)) &&
						    !(ampl<=32768 && FFT_SIZE==1024 && (i==0 || i==1 || i==2 || i==2047 || (i==2046 && freq==256))))
							Assert.assertEquals("Error of coefficient " + i + " too large (" + 
									FFT_SIZE + " coeff, " + freq + "Hz, amplitude " + ampl, 
									0, x[i]-sample, Math.sqrt(ampl)*4);
						else if (!(ampl<=64) && !(i==2) && !(i==0) && !(ampl<=128 && FFT_SIZE==1024))
							Assert.assertEquals("Error of coefficient " + i + " really too large (" + 
									FFT_SIZE + " coeff, " + freq + "Hz, amplitude " + ampl, 
									0, x[i]-sample, ampl*2);
						else if (!(ampl<=64))
							Assert.assertEquals("Error of coefficient " + i + " really really too large (" + 
									FFT_SIZE + " coeff, " + freq + "Hz, amplitude " + ampl, 
									0, x[i]-sample, ampl*8);
						else
							Assert.assertEquals("Error of coefficient " + i + " really really really too large (" + 
									FFT_SIZE + " coeff, " + freq + "Hz, amplitude " + ampl, 
									0, x[i]-sample, ampl*16);
					}
					// the average error is not that high though
					Assert.assertEquals("Average error of forward and reverse FFT too large", 0, diff/(double)N, Math.sqrt(ampl));
				}
			}
		}
	}
}
