/* Copyright Rene Mayrhofer
 * File created 2008-01-31
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.sensors;

import org.apache.log4j.Logger;

public class TimeSeriesAlignment extends TimeSeriesBundle {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger("org.openuat.sensors.TimeSeriesAlignment" /*TimeSeriesAlignment.class*/);

	private double[] l = null, alpha = null, beta = null;
	
	private int index = -1;
	
	/** Constructs all internal buffers and the time series.
	 * 
	 * @param numSeries The number of time series to use, i.e. the dimensionality
	 *                  of the input space.
	 * @param windowSize The time window size to use for detecting active/quiescent
	 *                   segments. This is specified as the number of samples in the
	 *                   window.
	 * @param minSegmentSize The minimum size of an active segment to be regarded
	 *                       significant enough to be sent to listeners.
	 * @param maxSegmentSize If set to something other than -1, specifies the
	 *                       maximum size of an active segments. If an active segment
	 *                       is longer than this number of samples, it will be sent
	 *                       to the listeners as soon as it reaches this length. The
	 *                       remainders of longer segments will be discarded. Set to
	 *                       -1 to disable this functionality, otherwise must be
	 *                       >=minSegmentSize.
	 */
	public TimeSeriesAlignment(int numSeries, int windowSize, int minSegmentSize, int maxSegmentSize) {
		super(numSeries, windowSize, minSegmentSize, maxSegmentSize);
		
		if (numSeries != 2 && numSeries != 3)
			throw new IllegalArgumentException("Number of dimensions must be 2 or 3");
		
		l = new double[windowSize+maxSegmentSize];
		alpha = new double[windowSize+maxSegmentSize];
		if (numSeries == 3)
			beta = new double[windowSize+maxSegmentSize];
	}
	
	public TimeSeriesAlignment(double[][] mySide) {
		this(mySide[0].length, 1, 1, mySide.length);
		
		if (mySide.length < 1)
			throw new IllegalArgumentException("Need at least 1 sample");
		if (mySide[0].length != 2 && mySide[0].length != 3)
			throw new IllegalArgumentException("Number of dimensions must be 2 or 3");

		index=0;
		for (int i=0; i<mySide.length; i++)
			newSample(mySide[i]);
	}
	
	public class Alignment {
		public double delta_alpha=0, delta_beta=0, error=0;
		public int numSamples=0;
	}
	
	public Alignment alignWith(TimeSeriesAlignment otherSide) {
		if (otherSide == null || otherSide.l.length < 1)
			throw new IllegalArgumentException("Need at least 1 sample");
		if (otherSide.firstStageSeries_Int.length != firstStageSeries_Int.length)
			throw new IllegalArgumentException("Number of dimensions must match for both sides");

		// this is naive optimisation - our own sample is the reference, the other rotated wrt. it
		Alignment al = new Alignment();
		int numRelevantAlpha=0, numRelevantBeta=0;
		for (int i=0; i<index && i<otherSide.index; i++) {
			al.delta_alpha += angleWithinPI(otherSide.alpha[i] - alpha[i]);
			/* only count as relevant when both alphas are != 0, because atan2 returns 0
			 * for (0,0), which really has no angle at all */
			if (alpha[i] != 0 || otherSide.alpha[i] != 0)
				numRelevantAlpha++;
			if (firstStageSeries_Int.length == 3) {
				al.delta_beta += angleWithinPI(otherSide.beta[i] - beta[i]);
				// same here
				if (beta[i] != 0 || otherSide.beta[i] != 0)
					numRelevantBeta++;
			}
			al.numSamples++;
		}
		if (numRelevantAlpha > 0)
			al.delta_alpha /= numRelevantAlpha;
		else if (al.delta_alpha != 0)
			logger.warn("Delta alpha is not equal zero, although we didn't have a single relevant pair to process. This should not happen!");
		if (numRelevantBeta > 0)
			al.delta_beta /= numRelevantBeta;
		else if (al.delta_beta != 0)
			logger.warn("Delta beta is not equal zero, although we didn't have a single relevant pair to process. This should not happen!");
		
		// calculate error for alpha, beta, and length (magnitude)
		for (int i=0; i<index && i<otherSide.index; i++) {
			if (firstStageSeries_Int.length == 3)
				al.error += (otherSide.alpha[i]-alpha[i]-al.delta_alpha)*
			            (otherSide.alpha[1]-alpha[i]-al.delta_alpha) +
			            (otherSide.beta[i]-beta[i]-al.delta_beta)*
			            (otherSide.beta[i]-beta[i]-al.delta_beta) +
			            (l[i]-otherSide.l[i])*(l[i]-otherSide.l[i]);
			else
				al.error += (otherSide.alpha[i]-alpha[i]-al.delta_alpha)*
						(otherSide.alpha[1]-alpha[i]-al.delta_alpha) +
						(l[i]-otherSide.l[i])*(l[i]-otherSide.l[i]);
		}
		
		return al;
	}
	
	private double angleWithinPI(double angle) {
		if (angle <= -Math.PI)
			angle += 2*Math.PI;
		if (angle <= -Math.PI || angle > Math.PI)
			logger.warn("Unexpected angle: " + angle);
		return angle;
	}
	
	public double[][] rotate(double[][] series) {
		// TODO: implement rotation
		return null;
	}
	
	protected void toActiveFirstLine(int numSample) {
		index = 0;
	}

	protected void toQuiescentLastLine(int numSample) {
		// TODO: implement forwarding
	}

//#if cfg.haveFloatSupport
	protected void sampleAddedLine(int lineIndex, double sample, int numSample) {
		newSample(curSample);
	}
//#endif

	protected void sampleAddedLine(int lineIndex, int sample, int numSample) {
	}
	
	private void newSample(double[] coord) {
		if (index >= maxSegmentSize+windowSize) {
			logger.warn("Want to write more active samples than segment size, aborting. This should not happen!");
			return;
		}

		if (coord.length == 2) {
			l[index] = Math.sqrt(coord[0]*coord[0] + coord[1]*coord[1]);
			alpha[index] = Math.atan2(coord[1], coord[0]);
			index++;
		}
		else if (coord.length == 3) {
			l[index] = Math.sqrt(coord[0]*coord[0] + coord[1]*coord[1] + coord[2]*coord[2]);
			alpha[index] = Math.atan2(coord[1], coord[0]);
			beta[index] = Math.atan2(coord[2], Math.sqrt(coord[0]*coord[0] + coord[1]*coord[1]));
			index++;
		}
		else
			throw new IllegalArgumentException("Number of dimensions must be 2 or 3");
	}
}
