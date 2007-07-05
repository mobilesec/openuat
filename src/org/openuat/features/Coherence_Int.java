/* Copyright Rene Mayrhofer
 * File created 2007-06-06
 * 
 * This implementation is based on the "cohere", "pwelch", "hanning", 
 * and "conj" functions in Octave and Octave Forge.
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.features;

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
 * This class implements coherence with integers.
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class Coherence_Int {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger("org.openuat.features.Coherence" /*Coherence.class*/);
	
	/** This is a small helper function to compute how many slices will be used. */
	public static int getNumSlices(int signalLength, int windowsize, int overlap) {
		if (windowsize-overlap <= 0)
			throw new IllegalArgumentException("windowsize-overlap <= 0, can not create slices");
		if (signalLength < 2*windowsize - overlap)
			throw new IllegalArgumentException("Signals are too short to compute coherence. Need at least 2 slices: " +
					(2*windowsize - overlap) + " samples necessary for window size " + windowsize +
					" with overlap " + overlap + ", but got only " + signalLength);
		
		return (signalLength-windowsize) / (windowsize-overlap) + 1;
	}

	/** Compute the coherence between two signals. Overlapping windows are
	 * created with a length of exactly the number of FFT coefficients. The
	 * windows are then dot-multiplied with a von-Hann (sometimes called hanning) 
	 * window and averaged. 
	 * 
	 * @param s1 Signal 1. Both signals must have equal length.
	 * @param s2 Signal 2. Both signals must have equal length.
	 * @param windowsize The window size to use, i.e. the number of FFT coefficients to compute. Defaults to
	 *                   min(256, s1.length) if set to <= 0.
	 * @param overlap The overlap of the windows to compute. Defaults to windowsize/2 when set to < 0.
	 * @return The coherence coefficients.
	 */
	public static double[] cohere(double[] s1, double[] s2, int windowsize, int overlap) {
		if (s1.length != s2.length)
			throw new IllegalArgumentException("Signals have different length");
		
		// default for windowsize
		if (windowsize <= 0)
			windowsize = s1.length >= 256 ? s1.length : 256;
		// default for overlap
		if (overlap < 0)
			overlap = windowsize / 2;

		if (s1.length < 2*windowsize - overlap) {
			logger.error("Signals are too short to compute coherence. Need at least 2 slices: " +
					(2*windowsize - overlap) + " samples necessary for window size " + windowsize +
					" with overlap " + overlap + ", but got only " + s1.length);
			return null;
		}

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
		int slices=0;
		for (int offset=0; offset<s1.length-windowsize+1; offset+=windowsize-overlap) {
			logger.debug("Using slice " + slices + " at offset " + offset);
			slices++;

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
		// sanity check
		if (slices != getNumSlices(s1.length, windowsize, overlap))
			throw new RuntimeException("Error: did not compute as many slices as expected. This should not happen.");

		/* No need to divide P** by the number of slices here, it would cancel itself out below.
		   Also no need to divide P** by the norm of the hanning window, it would also cancel itself out
		   (the second one should be suggested by Bendat & Piersol Sec 11.5.2, according to
		   http://mail.python.org/pipermail/python-list/2003-January/142831.html ). */ 

		// this is the coherence
		// the number of values to return - only half of the power spectrum is significant (+1 for even)
		int retSize = windowsize%2 == 1 ? (windowsize+1) / 2 : windowsize/2 + 1;
		double[] P = new double[retSize];
		for (int i=0; i<retSize; i++) {
			// again: P = Pxy.*conj(Pxy)./Pxx./Pyy; gives the sqared magnitude 
			P[i] = (Pxy[i].getRe() * Pxy[i].getRe() + Pxy[i].getIm() * Pxy[i].getIm()) / (Pxx[i] * Pyy[i]);
		}
			
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

	/** Helper function: calculates the mean of the vector elements, up to a maximum index 
	 * (or the whole vector if max_ind is set to -1 or larger than the vector length). */
	public static double mean(double[] vector, int max_ind) {
		double ret = 0;
		int len = (max_ind == -1 || max_ind > vector.length) ? vector.length : max_ind;
		for (int i=0; i<len; i++)
			ret += vector[i];
		return ret / len;
	}
	
	/** This is a helper function that computes the average over the coherence 
	 * values, weighted by the number of slices that were used to compute the
	 * coherence. This weighting is necessary.
	 * 
	 * @param s1 Signal 1. Both signals must have equal length.
	 * @param s2 Signal 2. Both signals must have equal length.
	 * @param windowsize The window size to use, i.e. the number of FFT coefficients to compute. Defaults to
	 *                   min(256, s1.length) if set to <= 0.
	 * @param overlap The overlap of the windows to compute. Defaults to windowsize/2 when set to <= 0.
	 * @param max_ind The maximum index of the FFT coefficient vectors to compare to. This can be used
	 *                to only compare up to a specifiv frequency, and ignore higher frequencies. Set to
	 *                -1 to use all coefficients.
	 * @return The coherence coefficients.
	 */
	public static double weightedCoherenceMean(double[] s1, double[] s2, int windowsize, int overlap, int max_ind) {
		return mean(cohere(s1, s2, windowsize, overlap), max_ind) * Math.sqrt(getNumSlices(s1.length, windowsize, overlap));
	}
}
