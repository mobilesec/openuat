/* Copyright Rene Mayrhofer
 * File created 2006-04-28
 * 
 * This implementation is based on the "cohere", "pwelch", "hanning", 
 * and "conj" functions in Octave and Octave Forge.
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.sensors;

import org.apache.log4j.Logger;

/** This class implements computation of the coherence function. It is
 * modelled after the Matlab/Octave "coher" function, but en explanation
 * can e.g. be found at http://ccrma.stanford.edu/~jos/mdft/Coherence_Function_Matlab.html
 * It uses the FFT class.
 * 
 * The coherence is estimated as the power spectrum correlation between two
 * signals split into overlapping slices. For each slice, the Fourier 
 * coefficients are computed and the magnitudes of these slices are averaged
 * to compute the power spectra.
 * 
 * <b>Note:</b> This class does not yet implement estimation of confidence
 * intervals or de-trending like the Matlab/Octave implementations do. It is
 * not necessary in the current use cases and has thus been omitted.
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class Coherence {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(Coherence.class);

	/** Compute the coherence between two signals. Overlapping windows are
	 * created with a length of exactly the number of FFT coefficients. The
	 * windows are then dot-multiplied with a von-Hann (sometimes called hanning) 
	 * window and averaged. 
	 * 
	 * @param s1 Signal 1. Both signals must have equal length.
	 * @param s2 Signal 2. Both signals must have equal length.
	 * @param windowsize The window size to use, i.e. the number of FFT coefficients to compute. Defaults to
	 *                   min(256, s1.length) if set to <= 0.
	 * @param overlap The overlap of the windows to compute. Defaults to windowsize/2 when set to <= 0.
	 * @return The coherence coefficients.
	 */
	public static double[] cohere(double[] s1, double[] s2, int windowsize, int overlap) {
		if (s1.length != s2.length) {
			logger.error("Signals have different length");
			return null;
		}
		
		// default for windowsize
		if (windowsize <= 0)
			windowsize = s1.length >= 256 ? s1.length : 256;
		// default for overlap
		if (overlap <= 0)
			overlap = windowsize / 2;
		
		logger.info("Computing coherence between two signals of length " + s1.length + 
				" with a window size/number of FFT coefficients of " + windowsize + " and " +
				overlap + " overlap");
		
		double[] hann = hann(windowsize);	
		// sanity check
		if (hann.length != windowsize) {
			logger.error("FFT window size is different from than the hanning window size, can not cope");
			return null;
		}
		// normalize the hanning window
		double hannNorm = l2Norm(hann);
		for (int i=0; i<windowsize; i++)
			hann[i] = hann[i] / hannNorm;
		if (logger.isDebugEnabled() && Math.abs(l2Norm(hann) - 1) > 0.00001)
			logger.debug("Norm of normalized hann window is not 1");
		
		// the self- and cross-correlations of the frequency power spectra of the signals
		// they are initialized to 0 by the JVM
		double[] Pxx = new double[windowsize];
		double[] Pyy = new double[windowsize];
		Complex[] Pxy = new Complex[windowsize];
		for (int i=0; i<windowsize; i++)
			Pxy[i] = new Complex(0, 0);
		
		// this calculates the average of the P** over all slices
		for (int offset=0; offset<s1.length-windowsize+1; offset+=windowsize-overlap) {
			logger.debug("Using slice at offset " + offset);

			// create the slices and mask them with hanning
			Complex[] a = new Complex[windowsize];
			for (int i=0; i<windowsize; i++)
				a[i] = new Complex(s1[offset+i] * hann[i], 0);
			Complex[] b = new Complex[windowsize];
			for (int i=0; i<windowsize; i++)
				b[i] = new Complex(s2[offset+i] * hann[i], 0);
			/* no padding is necessary because we set the hanning window to the same size 
			 * as the number of FFT coefficients anyway */
			a = FFT.fft(a);
			b = FFT.fft(b);
			// and add the slice to the power spectrum averages
			for (int i=0; i<windowsize; i++) {
				// this is just the sqare of the magnitude
				//Pxx[i] += a[i].times(a[i].conjugate()).abs();
				// quicker - saves unnecessary multiplicates (imaginary part becomes zero), copying and sqrt
				Pxx[i] += a[i].getRe() * a[i].getRe() + a[i].getIm() * a[i].getIm(); 
				Pyy[i] += b[i].getRe() * b[i].getRe() + b[i].getIm() * b[i].getIm();
				// here we need to do the full thing
				Pxy[i] = Pxy[i].plus(a[i].times(b[i].conjugate()));
			}
		}
		// no need to divide P** by the number of slices here, because these factors cancel when computing the coherence below

		// this is the coherence
		// the number of values to return - only half of the power spectrum is significant (+1 for even)
		int retSize = windowsize%2 == 1 ? (windowsize+1) / 2 : windowsize/2 + 1;
		double[] P = new double[retSize];
		for (int i=0; i<retSize; i++)
			// again: P = Pxy.*conj(Pxy)./Pxx./Pyy; gives the sqared magnitude 
			P[i] = (Pxy[i].getRe() * Pxy[i].getRe() + Pxy[i].getIm() * Pxy[i].getIm()) / (Pxx[i] * Pyy[i]);
			
		return P;
	}
	
	/** This function just generates a von-Hann window of specified size, as defined
	 * at http://www.mathworks.com/access/helpdesk/help/toolbox/signal/hann.html
	 * 
	 * @param windowsize The size of the von-Hann window to generate.
	 * @return The von-Hann window.
	 */
	public static double[] hann(int windowsize) {
		if (windowsize <= 0)
			throw new IllegalArgumentException("Window size must be > 0");
		
		if (windowsize == 1)
			return new double[] {1};
		else {
			double[] ret = new double[windowsize];
			for (int i=0; i<windowsize; i++)
				ret[i] = 0.5f - 0.5f * Math.cos(2 * Math.PI * i / windowsize);
			return ret;
		}
	}
	
	/** Helper function: calculates the L2-norm of a vector. */
	public static double l2Norm(double[] vector) {
		double ret = 0;
		for (int i=0; i<vector.length; i++)
			ret += vector[i] * vector[i];
		return Math.sqrt(ret);
	}

	/** Helper function: calculates the mean of the vector elements. */
	public static double mean(double[] vector) {
		double ret = 0;
		for (int i=0; i<vector.length; i++)
			ret += vector[i];
		return ret / vector.length;
	}
}