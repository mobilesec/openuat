/* Copyright Rene Mayrhofer
 * File created 2006-04-28
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.sensors;

/** This class represents a possibly multi-dimensional time series of a single
 * sensor. It computes simply statistical values, can distinguish active from
 * passive segments, and offers some convenience methods.
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class TimeSeries {
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
	
	private float offset = 0;
	private float multiplicator = 1.0f;
	private boolean subtractWindowMean = false;
	private boolean subtractTotalMean = false;
	
	public TimeSeries(int windowSize) {
		circularBuffer = new float[windowSize];
	}
	
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
	}
	
	private float getMean(float sum, int num) {
		if (num > 0)
			return sum / num;
		else
			return 0;
	}
	
	private float getVariance(float sum, float sum2, int num) {
		if (num > 1)
			return (sum2 - 2*sum*sum/num + num*sum*sum) / (num -1);
		else
			return 0;
	}
	
	public float getTotalMean() {
		return getMean(totalSum, totalNum);
	}
	
	public float getTotalVariance() {
		return getVariance(totalSum, totalSum2, totalNum);
	}

	public float getWindowMean() {
		return getMean(windowSum, full ? circularBuffer.length : index);
	}
	
	public float getWindowVariance() {
		return getVariance(windowSum, windowSum2, full ? circularBuffer.length : index);
	}

	// TODO: detect active and passive segments automatically and fire off events on transitions when requested
	// TODO: provide default parameter values, but allow to override them
}
