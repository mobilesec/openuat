/* Copyright Rene Mayrhofer
 * File created 2006-05-02
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

import java.util.logging.Level;
import java.util.logging.Logger;

/** This class implements a simple linear quantizer to transform double-valued
 * signals into small-ranged integer-valued ones. 
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class Quantizer {
	/** Our logger. */
	private static Logger logger = Logger.getLogger("org.openuat.features.Quantizer" /*Quantizer.class*/);

	/** Quantifies a signal according to the parameters. Every value of the
	 * input vector is quantized independently.
	 * 
	 * @param vector The input signal to quantize.
	 * @param lower The lower end of the range in which the input signal should be quantized.
	 *              All values in vector less than this lower end will simply be quantized to
	 *              the minimum quantization level, i.e. to 0. 
	 * @param upper The upper end of the range in which the input signal should be quantized.
	 *              All values in vector greater than this upper end will simply be quantized to
	 *              the maximum quantization level, i.e. to numLevels-1
	 * @param numLevels The number of levels to distinguish. All output values will be in the
	 *                  (integer) range [0;numLevels-1] with the exception of "error" values
	 *                  when using error zones, which will be set to -1.
	 * @param exponentialLevels If set to true, the quantization levels will be created with
	 *                          exponentially growing ranges. If set to false, all quantization
	 *                          levels will have an equal range, i.e. with equidistant points
	 *                          to distinguish levels.
	 * @param offset A value between -0.5 and +0.5 to specify an offset in the quantization. 
	 *               It can be used to change the start of the quantization levels slightly 
	 *               to account for small differences in the original data that would lead to 
	 *               mismatch in the quantized data. This is necessary since small errors in the
	 *               sensor values can easily cause quantization erros.
	 *               Set to 0 if not needed.
	 * @param errorZone If set to true, an error zone will be created around each quantum value 
	 *                  and values outside those error zones will be marked in the output by being 
	 *                  set to -1. The error zone are <b>not</b> adapted to exponential ranges
	 *                  is exponentialLevels is set to true.
	 *                  If set to false, no error zones will be used.
	 * @return The quantized values. This array will have the same number of elements as the input.
	 */
	public static int[] quantize(double[] vector, double lower, double upper, int numLevels, 
			boolean exponentialLevels, double offset, boolean errorZone) {
		if (lower >= upper)
			throw new IllegalArgumentException("lower must be < upper");
		if (numLevels < 2)
			throw new IllegalArgumentException("Need at least 2 quantization levels");
		if (offset < -0.5 || offset > 0.5)
			throw new IllegalArgumentException("Invalid offset value, must be between -0.5 and 0.5");

		// first generate the quantization intervals
		double errorMargin;
		if (errorZone)
			// when we use an error zone, the valid intervals are smaller
			errorMargin = (upper-lower) / numLevels / 4;
		else 
			errorMargin = 0;
		// holds both upper and lower ends of the intervals
		double intervals[][] = new double[2][];
		intervals[0] = new double[numLevels];
		intervals[1] = new double[numLevels];
		if (logger.isLoggable(Level.FINER))
			logger.finer("Creating " + (exponentialLevels ? "exponential" : "linear") + 
				" intervals with lower=" + lower + ", upper=" + upper + 
				", numLevels=" + numLevels + ", offset=" + offset + ", errorMargin=" + errorMargin);
		int totalQuantLevelsExp = 1, curQuantLevelsExp = 1, sumQuantLevelsExp = 0;
		if (exponentialLevels) {
			for (int i=0; i<numLevels; i++) totalQuantLevelsExp*=2;
			totalQuantLevelsExp--;
			if (logger.isLoggable(Level.FINER))
				logger.finer("Using " + totalQuantLevelsExp + " quants");
		}
		for (int i=0; i<numLevels; i++) {
			if (! exponentialLevels) {
				intervals[0][i] = (i+offset) * (upper-lower)/numLevels + lower + errorMargin;
				intervals[1][i] = (i+1+offset) * (upper-lower)/numLevels + lower - errorMargin;
			}
			else {
				intervals[0][i] = (sumQuantLevelsExp+offset*curQuantLevelsExp) * (upper-lower)/totalQuantLevelsExp + lower + errorMargin;
				intervals[1][i] = (sumQuantLevelsExp+(offset+1)*curQuantLevelsExp) * (upper-lower)/totalQuantLevelsExp + lower - errorMargin;
				sumQuantLevelsExp+=curQuantLevelsExp;
				curQuantLevelsExp*=2;
			}
			if (logger.isLoggable(Level.FINER))
				logger.finer("Using interval " + i + " from " + intervals[0][i] + " to " + intervals[1][i]);
		}
		// but (when using no error zones), set first and last intervals to be open
		intervals[0][0] = Double.NEGATIVE_INFINITY;
		intervals[1][numLevels-1] = Double.POSITIVE_INFINITY;

		// and quantize each value
		int[] quantized = new int[vector.length];
		for (int i=0; i<vector.length; i++) {
			quantized[i] = -1;
			for (int j=0; j<numLevels; j++) {
				if (vector[i] >= intervals[0][j] && vector[i] <= intervals[1][j]) {
					quantized[i] = j;
					if (logger.isLoggable(Level.FINER))
						logger.finer(i + ": " + vector[i] + " is in " + j + ": [" + 
							intervals[0][j] + ";" + intervals[1][j] + "], setting to " + quantized[i]);
				}
			}
		}
		
		return quantized;
	}
	
	/** Helper function to return the maximum value in a vector. */
	public static double max(double[] vector) {
		double max = Double.NEGATIVE_INFINITY;
		for (int i=0; i<vector.length; i++)
			if (vector[i] > max)
				max = vector[i];
		return max;
	}
	
	/** Generates multiple quantization candidates with different offset values.
	 * 
	 * @param vector @see #quantize
	 * @param lower @see #quantize
	 * @param upper @see #quantize
	 * @param numLevels @see #quantize
	 * @param exponentialLevels @see #quantize
	 * @param numCandidates The number of candidates to create. That is, 
	 *                      numCandidates offset values will be used from 0 to 0.5. It
	 *                      must be at least 2 to make sense.
	 * @param errorZone @see #quantize
	 * @return An array of candidates, where the first dimension represents the
	 *         different candidates (thus, the number of elements in the first
	 *         dimension is numCandidates) and the second dimension represents the
	 *         vector values (thus, the number of elements in the second dimension
	 *         is vector.length).
	 */
	public static int[][] generateCandidates(double[] vector, double lower, double upper, int numLevels, 
			boolean exponentialLevels, int numCandidates, boolean errorZone) {
		if (numCandidates < 2)
			throw new IllegalArgumentException("numCandidates must >= 2");
		
		if (logger.isLoggable(Level.FINER))
			logger.finer("Generating " + numCandidates + " quantization candidates for lower=" 
				+ lower + ", upper=" + upper + ", numLevels=" + numLevels + 
				(errorZone ? " with" : " without") + " error zones");
		
		int[][] candidates = new int[numCandidates][];
		for (int i=0; i<numCandidates; i++) {
			// with numCandidates different values, there's (numCandidates-1) additions in between...
			double offset = i * 0.5 / (numCandidates-1);
			candidates[i] = quantize(vector, lower, upper, numLevels, exponentialLevels, offset, errorZone);
		}
		return candidates;
	}
}
