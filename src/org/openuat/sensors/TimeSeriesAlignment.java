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
	
	public Alignment alignWith(double[][] otherSide) {
		if (otherSide == null || otherSide.length < 1)
			throw new IllegalArgumentException("Need at least 1 sample");
		if (otherSide[0].length != firstStageSeries_Int.length)
			throw new IllegalArgumentException("Number of dimensions must match for both sides");

		// this is naive optimisation
		Alignment al = new Alignment();
		for (int i=0; i<index && i<otherSide.length; i++) {
			al.delta_alpha += alpha[i] - otherSide[i][1];
			if (firstStageSeries_Int.length == 3)
				al.delta_beta += beta[i] - otherSide[i][2];
			al.numSamples++;
		}
		al.delta_alpha /= al.numSamples;
		al.delta_beta /= al.numSamples;
		
		// calculate error for alpha, beta, and length (magnitude)
		for (int i=0; i<al.numSamples; i++)
			al.error += (alpha[i]-otherSide[i][1]-al.delta_alpha)*
			            (alpha[i]-otherSide[i][1]-al.delta_alpha) +
			            (beta[i]-otherSide[i][2]-al.delta_beta)*
			            (beta[i]-otherSide[i][2]-al.delta_beta) +
			            (l[i]-otherSide[i][0])*(l[i]-otherSide[i][0]);
		
		return al;
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
