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
	
	/** For correlation coefficients > 0.5, the devices are assumed to be
	 * together, for correlation coefficients <= 0.5, they are assumed to 
	 * be separate.
	 */
	public static final float correlationCoefficientThreshold = 0.5f;
}
