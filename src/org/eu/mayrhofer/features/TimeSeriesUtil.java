/* Copyright Rene Mayrhofer
 * File created 2006-10-02
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.features;

import org.apache.log4j.Logger;

/** This class contains utility functions for dealing with time series.
 *
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class TimeSeriesUtil {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(TimeSeriesUtil.class);

	/** This method equalizes the length of two time series segments by cutting
	 * the longer to the length of the shorter.
	 * 
	 * @param segment1 The first time series segment to use.
	 * @param segment2 The second time series segment to use.
	 * @return An array of two time series segments. Both arrays will have the 
	 *         same length and will contain copies of the first parts of 
	 *         segment1 and segment2 respectively. That is, return[0] will 
	 *         contain segment1[0:len] and return[1] will contain 
	 *         segment2[0:len] with len being the minimum of segment1.length
	 *         and segment2.length.
	 */
	public static double[][] cutSegmentsToEqualLength(double[] segment1, double[] segment2) {
		int len = segment1.length <= segment2.length ? segment1.length : segment2.length;
		logger.debug("Using " + len + " samples for coherence computation");

		double[][] ret = new double[2][];
		ret[0] = new double[len];
		ret[1] = new double[len];
		System.arraycopy(segment1, 0, ret[0], 0, len);
		System.arraycopy(segment2, 0, ret[1], 0, len);
		
		return ret;
	}
	
	/** Slices a time series into multiple parts pf a specified length.
	 * 
	 * @param segment The time series segment to slice.
	 * @param maxSegmentLength The number of samples that the slices should 
	 *        have. If set to -1, no slicing will happen.
	 * @param segmentSkip How many samples to skip when generating the next 
	 *        slice. Slices will overlap by (maxSegmentLength-segmentSkip)
	 *        samples.
	 * @return An array of slices generated from the time series. If segment
	 *         is <=maxSegmentLength or maxSegmentLength is <=0, then no slicing
	 *         will be done and the original time series passed in segment will
	 *         be returned as the first and only element in this array. 
	 *         Otherwise, all elements of the returned array will have the same
	 *         length, namely maxSegmentLength samples.
	 *         <b>Attention:</b>If segment can not be divided into slices 
	 *         without remainder, the remainder will be ignored and will not 
	 *         be returned.
	 */
	public static double[][] slice(double[] segment, int maxSegmentLength, int segmentSkip) {
		double[][] ret;
		
		if (maxSegmentLength > 0 && segment.length > maxSegmentLength) {
			int numSplits = (segment.length-maxSegmentLength) / (segmentSkip) + 1;
			logger.debug("Segments are longer than maximum length: " +
					segment.length + " > " + maxSegmentLength + 
					" s, splitting into " + numSplits + " segments");
			ret = new double[numSplits][];
			for (int i=0; i<numSplits; i++) {
				ret[i] = new double[maxSegmentLength];
				int off=segmentSkip*i;
				System.arraycopy(segment, off, ret[i], 0, maxSegmentLength);
			}
		}
		else {
			// simple case: just use the whole time series
			ret = new double[1][];
			ret[0] = segment;
		}
		return ret;
	}

	/** Compute the maximum index of a vector of FFT coefficients that needs to
	 * considered when using them only up to the specified cut-off frequency.
	 */
	public static int getMaxInd(int numFftPoints, int samplerate, int cutOffFrequency) {
		// only compare until the cutoff frequency
		int max_ind = (int) (((float) (numFftPoints * cutOffFrequency)) / samplerate) + 1;
		//System.out.println("Only comparing the first " + max_ind + " FFT coefficients");
		return max_ind;
	}
}
