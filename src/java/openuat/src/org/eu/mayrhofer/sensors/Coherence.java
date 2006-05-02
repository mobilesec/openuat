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

/**
## usage: [Cxy, w] = cohere(x, y, ...)
##
## Estimate coherence between two signals.
## This is simply Cxy = |Pxy|^2/(PxxPxy).
##
## See pwelch for an explanation of the available parameters.

 elseif nargout==1
    Cxy=pwelch('cohere',varargin{:});
    varargout{1} = Cxy;

/**
## Estimate power spectrum of a stationary signal. This chops the signal
## into overlapping slices, windows each slice and applies a Fourier
## transform to determine the frequency components at that slice. The
## magnitudes of these slices are then averaged to produce the estimate Pxx.
## The confidence interval around the estimate is returned in Pci.
##
## x: vector of samples
## n: size of fourier transform window, or [] for default=256
## Fs: sample rate, or [] for default=2 Hz
## window: shape of the fourier transform window, or [] for default=hanning(n)
##    Note: window length can be specified instead, in which case
##    window=hanning(length)
## overlap: overlap with previous window, or [] for default=length(window)/2
## ci: confidence interval, or [] for default=0.95
##    ci must be between 0 and 1; if ci is not passed, or if it is
##    passed as 0, then no confidence intervals will be computed.
## range: 'whole',  or [] for default='half'
##    show all frequencies, or just half of the frequencies
## units: 'squared', or [] for default='db'
##    show results as magnitude squared or as log magnitude squared
## trend: 'mean', 'linear', or [] for default='none'
##    remove trends from the data slices before computing spectral estimates

 * 
 * @author rene
 *
 */
public class Coherence {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(Coherence.class);

	/** Compute the coherence between two signals. 
	 * 
	 * @param s1 Signal 1. Both signals must have equal length.
	 * @param s2 Signal 2. Both signals must have equal length.
	 * @param windowsize The window size to use, i.e. the number of FFT coefficients to compute. Defaults to
	 *                   min(256, s1.length) if set to <= 0.
	 * @param overlap The overlap of the windows to compute. Defaults to windowsize/2 when set to <= 0.
	 * @return The coherence coefficients.
	 */
	public static double[] cohere(double[] s1, double[] s2, int windowsize, int overlap
			/*double confidenceInterval*/) {
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
		// default confidence interval - not needed just for the coherence
		/*if (confidenceInterval <= 0)
			confidenceInterval = 0.95f;
if isempty(trend), trend=-1; endif
		*/
		
		logger.info("Computing coherence between two signals of length " + s1.length + 
				" with a window size/number of FFT coefficients of " + windowsize + " and " +
				overlap + " overlap");
		
		double[] hann = hanning(windowsize);	
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
			// optional: detrend - not used here right now
			//   if trend>=0, a=detrend(a,trend); endif
			//   if trend>=0, b=detrend(b,trend); endif

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
			
			/*
  ## the factors of N cancel when computing cohere and tfe
  if !isempty(Pxx), Pxx = Pxx / N; endif
  if !isempty(Pxy), Pxy = Pxy / N; endif
  if !isempty(Pyy), Pyy = Pyy / N; endif
			 */
		}

		// this is the coherence
		// the number of values to return - only half of the power spectrum is significant (+1 for even)
		int retSize = windowsize%2 == 1 ? (windowsize+1) / 2 : windowsize/2 + 1;
		double[] P = new double[retSize];
		for (int i=0; i<retSize; i++)
			// again: P = Pxy.*conj(Pxy)./Pxx./Pyy; gives the sqared magnitude 
			P[i] = (Pxy[i].getRe() * Pxy[i].getRe() + Pxy[i].getIm() * Pxy[i].getIm()) / (Pxx[i] * Pyy[i]);
			
		return P;
	}
	
	/** This function just generates a hanning window of specified size.
	 * 
	 * @param windowsize The size of the hanning window to generate.
	 * @return The hanning window.
	 */
	public static double[] hanning(int windowsize) {
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
	
	/** Calculates the L2-norm of a vector. */
	public static double l2Norm(double[] vector) {
		double ret = 0;
		for (int i=0; i<vector.length; i++)
			ret += vector[i] * vector[i];
		return Math.sqrt(ret);
	}

	/** Calculates the mean of the vector elements. */
	public static double mean(double[] vector) {
		double ret = 0;
		for (int i=0; i<vector.length; i++)
			ret += vector[i];
		return ret / vector.length;
	}
}
