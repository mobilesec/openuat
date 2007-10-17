/* Copyright Rene Mayrhofer
 * File created 2007-10-17
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.authentication.accelerometer;

/** This class implements comparison of accelerometer time series based on
 * their correlation coefficient. It uses the parameters suggested in
 *     R. Marin-Perianu, M. Marin-Perianu, P. Havinga, and H. Scholten,
 *     “Movement-based group awareness with wireless sensor networks,” in
 *     Proc. Pervasive 2007. Springer-Verlag, May 2007, pp. 298–315.
 * 
 * Inputs, i.e. the time series to be compared, are acceleration magnitudes
 * sampled at 8Hz.
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class MarinPerianuComparison {
	/** Use the last 128 samples (i.e. the last 16s). */
	public static final int windowSize = 128;
	/** But compute correlation coefficients every 16 samples (i.e. every 2s). */
	public static final int stepSize = 16;
	/** With a sample rate of 8Hz. */
	public static final int sampleRate = 8;
	/** Samples are represented by just 1 Byte. */
	public static final int bitsPerSample = 8;
	
	/** For correlation coefficients > 0.5, the devices are assumed to be
	 * together, for correlation coefficients <= 0.5, they are assumed to 
	 * be separate.
	 */
	public static final float correlationCoefficientThreshold = 0.5f;
	
	// TODO: implement their incremental approach and integrate with ShakeWellBeforeUseProtocol1 and interlock:
	// run DH phase, and then in each interlock phase exchange stepSize samples 
	// (that might fit in 256 Bits, i.e. the block length when 8 Bits accuracy are sufficient)
	// then, after at least 8 such interlock exchanges (i.e. when windowSize is full),
	// start with the incremental correlation coefficient computations and do it until
	// it reaches the threshold (or a configurable number of maximum failures to abort)
	
	// incremental:
	/* sumA, sumB = sum over current sample window
	 * sum2A, sum2B = square (a[i]*a[i] sum over current sample window
	 * sumAB = cross (a[i]*b[i]) sum over current sample window
	 * initialize sumA, sumB, sum2A, sum2B, sumAB, varA, varB, cov, mA, mB with 0
	 * after each interlock exchange:
	 * last_sumA = sumA; last_sumB = sumB; last_sum2A = sum2A; last_sum2B = sum2B; last_sumAB = sumAB;
	 * recompute sumA, sumB, sum2A, sum2B, sumAB
	 * last_mA = ma; last_mB = mB;
	 * mA = mA + (sumA - last_sumA)/windowSize;
	 * mB = mB+ (sumB - last_sumB)/windowSize;
	 * varA = varA + (sum2A - last_sum2A)/windowSize - (mA*mA - last_mA*last_mA); 
	 * varB = varB + (sum2B - last_sum2B)/windowSize - (mB*mB - last_mB*last_mB);
	 * cov = voc + (sumAB - last_sumAB)/windowSize - (mA*mB - last_mA*last_mB);
	 * corrcoeff = cov/sqrt(varA*varB); 
	 */
}
