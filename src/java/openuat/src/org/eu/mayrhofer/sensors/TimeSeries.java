/* Copyright Rene Mayrhofer
 * File created 2006-04-28
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.sensors;

import org.apache.log4j.Logger;

import org.jfree.data.xy.*;

/** This class represents a possibly multi-dimensional time series of a single
 * sensor. It computes simply statistical values, can distinguish active from
 * passive segments, and offers some convenience methods.
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class TimeSeries {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(TimeSeries.class);

	/** This is the internal circular buffer used to hold the values inside the time window. */
	private float[] circularBuffer;
	/** The current position inside the circular buffer. This marks the position where the next sample will be written to. */
	private int index = 0;
	/** This is a boolean because once filled, we never expect it to be contain less elements again. So using a boolean, the checking is faster.*/
	private boolean full = false;
	
	/** Keeps a running total sum over all samples added to this time series so far (not only the current time window). */
	private float totalSum = 0;
	/** Keeps a running total sum over all squared samples added to this time series so far (not only the current time window). */
	private float totalSum2 = 0;
	/** The number of samples added to this time series so far (not only the current time window). */
	private int totalNum;
	
	/** Keeps a running sum over all samples pf the current time window. */
	private float windowSum = 0;
	/** Keeps a running sum over all squared samples pf the current time window. */
	private float windowSum2 = 0;

	/** This offset is added to all sample values before passing them on to the next stage.
	 * @see #setOffset(float)
	 * @see #getOffset() 
	 */
	private float offset = 0;
	/** All sample values are multiplied with this factor before passing them on to the next stage.
	 * @see #setOffset(float)
	 * @see #getOffset() 
	 */
	private float multiplicator = 1.0f;
	/** If set to true, the window mean will be subtracted before passing a sample on to the next stage.
	 * @see #setSubtractWindowMean(boolean)
	 * @see #getSubtractWindowMean()
	 */
	private boolean subtractWindowMean = false;
	/** If set to true, the window mean will be subtracted before passing a sample on to the next stage.
	 * @see #setSubtractTotalMean(boolean)
	 * @see #getSubtractTotalMean()
	 */
	private boolean subtractTotalMean = false;
	
	/** Initializes the time series circular buffer with the specified window size.
	 * 
	 * @param windowSize Specifies the number of past samples kept in memory and used for
	 *                   computing the window mean and variance.
	 */
	public TimeSeries(int windowSize) {
		circularBuffer = new float[windowSize];
	}
	
	/////// test
	XYSeries series = new XYSeries("Line", false);
	/////// test

	/** Adds a new sample to the time series in-memory buffer, updates statistics and
	 * may forward to the next stage.
	 * 
	 * @param sample The sample value to add.
	 */
	public void addSample(float sample) {
		// if circular buffer is already full, remove oldest (i.e. update statistics
		if (full) {
			windowSum -= circularBuffer[index];
			windowSum2 -= circularBuffer[index] * circularBuffer[index];
		}
		
		// add sample to internal buffer and update statistics
		circularBuffer[index] = sample;
		index++;
		if (index == circularBuffer.length) {
			full = true;
			index = 0;
		}
		
		windowSum += sample;
		windowSum2 += sample * sample;
		totalSum += sample;
		totalSum2 += sample * sample;
		totalNum++;
		
		// Now that this time series buffer has been updated, notify next stage.
		// But optionally pre-process our values before forwarding them.
		float nextStageSample = sample;
		// first subtract mean (because that is in the same value range as the "raw" values)
		if (subtractWindowMean)
			nextStageSample -= getWindowMean();
		else if (subtractTotalMean)
			nextStageSample -= getTotalMean();
		// and then apply optional linear transformation
		nextStageSample = nextStageSample * multiplicator + offset;
		logger.debug("Pushing value " + nextStageSample + " to next stage");
		
		// test
		series.add(totalNum-1, nextStageSample);
	}
	
	/** Helper method for computing the arithmetical average, i.e. the mean. */
	private float getMean(float sum, int num) {
		if (num > 0)
			return sum / num;
		else
			return 0;
	}
	
	/** Helper method for computing the variance. */
	private float getVariance(float sum, float sum2, int num) {
		if (num > 1)
			return (sum2 - 2*sum*sum/num + num*sum*sum) / (num -1);
		else
			return 0;
	}
	
	/** Returns the mean over all values added to this time series since its construction. */
	public float getTotalMean() {
		return getMean(totalSum, totalNum);
	}
	
	/** Returns the variance over all values added to this time series since its construction. */
	public float getTotalVariance() {
		return getVariance(totalSum, totalSum2, totalNum);
	}

	/** Returns the mean over all values in the time series buffer, i.e. the last window size samples. */
	public float getWindowMean() {
		return getMean(windowSum, full ? circularBuffer.length : index);
	}
	
	/** Returns the variance over all values in the time series buffer, i.e. the last window size samples. */
	public float getWindowVariance() {
		return getVariance(windowSum, windowSum2, full ? circularBuffer.length : index);
	}
	
	/** Gets the current value of offset.
	 * @see #offset
	 * @return The current value of offset.
	 */
	public float getOffset() {
		return offset;
	}
	
	/** Sets the current value of offset.
	 * @see #offset
	 * @param offset The current value of offset.
	 */
	public void setOffset(float offset) {
		this.offset = offset;
	}
	
	/** Gets the current value of multiplicator.
	 * @see #multiplicator
	 * @return The current value of multiplicator.
	 */
	public float getMultiplicator() {
		return multiplicator;
	}
	
	/** Sets the current value of multiplicator.
	 * @see #multiplicator
	 * @param multiplicator The current value of multiplicator.
	 */
	public void setMultiplicator(float multiplicator) {
		this.multiplicator = multiplicator;
	}
	
	/** Gets the current value of subtractWindowMean.
	 * @see #subtractWindowMean
	 * @return The current value of subtractWindowMean.
	 */
	public boolean getSubtractWindowMean() {
		return subtractWindowMean;
	}
	
	/** Sets the current value of subtractWindowMean.
	 * @see #subtractWindowMean
	 * @param subtractWindowMean The current value of subtractWindowMean.
	 */
	public void setSubtractWindowMean(boolean subtractWindowMean) {
		this.subtractWindowMean = subtractWindowMean;
	}

	/** Gets the current value of subtractTotalMean.
	 * @see #subtractTotalMean
	 * @return The current value of subtractTotalMean.
	 */
	public boolean getSubtractTotalMean() {
		return subtractTotalMean;
	}
	
	/** Sets the current value of subtractTotalMean.
	 * @see #subtractTotalMean
	 * @param subtractTotalMean The current value of subtractTotalMean.
	 */
	public void setSubtractTotalMean(boolean subtractTotalMean) {
		this.subtractTotalMean = subtractTotalMean;
	}
	
	// TODO: detect active and passive segments automatically and fire off events on transitions when requested
	// TODO: provide default parameter values, but allow to override them
}
