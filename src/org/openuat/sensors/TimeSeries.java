/* Copyright Rene Mayrhofer
 * File created 2006-04-28
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.sensors;

import java.util.Vector;

import org.apache.log4j.Logger;

/** This class represents a possibly multi-dimensional time series of a single
 * sensor. It computes simply statistical values, can distinguish active from
 * passive segments, and offers some convenience methods.
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class TimeSeries implements SamplesSink {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger("org.openuat.sensors.TimeSeries" /*TimeSeries.class*/);

	/** This is the internal circular buffer used to hold the values inside the time window. */
	private double[] circularBuffer;
	/** The current position inside the circular buffer. This marks the position where the next sample will be written to. */
	private int index = 0;
	/** This is a boolean because once filled, we never expect it to be contain less elements again. So using a boolean, the checking is faster.*/
	private boolean full = false;
	
	/** Keeps a running total sum over all samples added to this time series so far (not only the current time window). */
	private double totalSum = 0;
	/** Keeps a running total sum over all squared samples added to this time series so far (not only the current time window). */
	private double totalSum2 = 0;
	/** The number of samples added to this time series so far (not only the current time window). */
	private int totalNum = 0;
	
	/** Keeps a running sum over all samples pf the current time window. */
	private double windowSum = 0;
	/** Keeps a running sum over all squared samples pf the current time window. */
	private double windowSum2 = 0;

	/** This offset is added to all sample values before passing them on to the next stage.
	 * @see #setOffset(double)
	 * @see #getOffset() 
	 */
	private double offset = 0;
	/** All sample values are multiplied with this factor before passing them on to the next stage.
	 * @see #setOffset(double)
	 * @see #getOffset() 
	 */
	private double multiplicator = 1.0f;
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
	
	/** Holds the "listeners" for samples in the next stage, i.e. after filtering by
	 * this object.
	 * @see #addNextStageSink(SamplesSink)
	 * @see #removeSink(SamplesSink)
	 */
	private Vector nextStageSinks = new Vector();
	
	/** The variance threshold to detect active segments. Only if this is set > 0, 
	 * detection of active segments (and thus calculation of the variance) will be
	 * done.
	 */ 
	private double activeVarianceThreshold = 0;
	
	/** true if the current segment has previously been detected to be active, falls if
	 * previously detected to be quiescent.
	 */
	private boolean isActive = false;
	
	/** Initializes the time series circular buffer with the specified window size.
	 * 
	 * @param windowSize Specifies the number of past samples kept in memory and used for
	 *                   computing the window mean and variance.
	 */
	public TimeSeries(int windowSize) {
		if (windowSize <= 0) {
			throw new IllegalArgumentException("Window size must by > 0");
		}

		circularBuffer = new double[windowSize];
	}
	
	/** Resets the time series to the state as created when freshly constructing it. 
	 * However, it does not reset the parameters offset, multiplicator, subtractWindowMean,
	 * and subtractTotalMean. */
	public void reset() {
		index = 0;
		full = false;
		totalSum = 0;
		totalSum2 = 0;
		totalNum = 0;
		windowSum = 0;
		windowSum2 = 0;
	}
	
	/** Adds a new sample to the time series in-memory buffer, updates statistics and
	 * may forward to the next stage.
	 * 
	 * @param sample The sample value to add.
	 * @param sampleNum The number of the sample to add. As this class keeps internal count
	 *              of how many samples have already been added, this is used for checking
	 *              that all samples are received and no duplicates happen. index is assumed
	 *              to start at 0.
	 */
	public void addSample(double sample, int sampleNum) {
		if (sampleNum != totalNum) {
			logger.warn("Sample index " + sampleNum + " does not correspond to number of samples already received "
					+ "(" + totalNum + ")");
		}
		
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
		double nextStageSample = sample;
		// first subtract mean (because that is in the same value range as the "raw" values)
		if (subtractWindowMean)
			nextStageSample -= getWindowMean();
		else if (subtractTotalMean)
			nextStageSample -= getTotalMean();
		// and then apply optional linear transformation
		nextStageSample = nextStageSample * multiplicator + offset;
		if (logger.isTraceEnabled())
			logger.trace("Pushing value " + nextStageSample + " to next stage");
    	if (nextStageSinks != null)
    		for (int i=0; i<nextStageSinks.size(); i++) {
    			SamplesSink s = (SamplesSink) nextStageSinks.elementAt(i);
    			s.addSample(nextStageSample, sampleNum);
    		}
		
		// detect active segments
    	if (logger.isDebugEnabled())
    		logger.debug("Checking for activity: window variance is " + getWindowVariance() + 
    				", threshold is " + activeVarianceThreshold);
    	if (activeVarianceThreshold > 0 && getWindowVariance() >= activeVarianceThreshold
    			&& !isActive) {
    		logger.debug("Detected transition to active at index " + sampleNum);
    		isActive = true;
        	if (nextStageSinks != null)
        		for (int i=0; i<nextStageSinks.size(); i++) {
        			SamplesSink s = (SamplesSink) nextStageSinks.elementAt(i);
        			// we define the active segment to start at the end of an active window
        			s.segmentStart(sampleNum);
        		}
    	}
    	if (activeVarianceThreshold > 0 && getWindowVariance() < activeVarianceThreshold
    			&& isActive) {
    		logger.debug("Detected transition to quiescent at index " + (sampleNum-circularBuffer.length+1));
    		isActive = false;
        	if (nextStageSinks != null)
        		for (int i=0; i<nextStageSinks.size(); i++) {
        			SamplesSink s = (SamplesSink) nextStageSinks.elementAt(i);
        			// and to end at the beginning of a quiescent window
        			s.segmentEnd(sampleNum-circularBuffer.length+1);
        		}
    	}
	}
	
	/** Dummy implementation of SamplesSink.segmentStart. Does nothing. */
	public void segmentStart(int indexNotUsed) {
		logger.warn("segmentStart method of TimeSeries called. This should not happen");
	}

	/** Dummy implementation of SamplesSink.segmentEnd. Does nothing. */
	public void segmentEnd(int indexNotUsed) {
		logger.warn("segmentEnd method of TimeSeries called. This should not happen");
	}
	
	/** Registers a sink, which will receive all new values as they are sampled.
	 * 
	 * @param sink The sink to push new pre-processed samples to.
	 */
	public void addNextStageSink(SamplesSink sink) {
		nextStageSinks.addElement(sink);
	}

	/** Removes a previously registered sink.
	 * 
	 * @param sink The sink to stop pushing samples to.
	 * @return true if removed, false if not (i.e. if it has not been added previously).
	 */
	public boolean removeSink(SamplesSink sink) {
		return nextStageSinks.removeElement(sink);
	}
	
	/** Helper method for computing the arithmetical average, i.e. the mean. */
	private static double getMean(double sum, int num) {
		if (num > 0)
			return sum / num;
		else
			return 0;
	}
	
	/** Helper method for computing the variance. */
	private static double getVariance(double sum, double sum2, int num) {
		if (num > 1) {
			//return (sum2 - 2*sum*sum/num + sum*sum/num) / (num -1);
			return (sum2 - sum*sum/num) / (num -1);
		}
		else
			return 0;
	}
	
	/** Returns the mean over all values added to this time series since its construction. */
	public double getTotalMean() {
		return getMean(totalSum, totalNum);
	}
	
	/** Returns the variance over all values added to this time series since its construction. */
	public double getTotalVariance() {
		return getVariance(totalSum, totalSum2, totalNum);
	}

	/** Returns the mean over all values in the time series buffer, i.e. the last window size samples. */
	public double getWindowMean() {
		return getMean(windowSum, full ? circularBuffer.length : index);
	}
	
	/** Returns the variance over all values in the time series buffer, i.e. the last window size samples. */
	public double getWindowVariance() {
		return getVariance(windowSum, windowSum2, full ? circularBuffer.length : index); 
	}
	
	/** Returns all samples currently contained in the time window. */
	public double[] getSamplesInWindow() {
		// TODO: this can be optimized with 2 System.ArrayCopy calls
		int startInd = full ? index : 0;
		int num = full ? circularBuffer.length : index;
		double[] ret = new double[num];
		
		for (int i=0; i<num; i++)
			ret[i] = circularBuffer[(startInd+i)%circularBuffer.length];
		
		return ret;
	}
	
	/** Gets the current value of offset.
	 * @see #offset
	 * @return The current value of offset.
	 */
	public double getOffset() {
		return offset;
	}
	
	/** Sets the current value of offset.
	 * @see #offset
	 * @param offset The current value of offset.
	 */
	public void setOffset(double offset) {
		this.offset = offset;
	}
	
	/** Gets the current value of multiplicator.
	 * @see #multiplicator
	 * @return The current value of multiplicator.
	 */
	public double getMultiplicator() {
		return multiplicator;
	}
	
	/** Sets the current value of multiplicator.
	 * @see #multiplicator
	 * @param multiplicator The current value of multiplicator.
	 */
	public void setMultiplicator(double multiplicator) {
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
		if (subtractTotalMean == true && subtractWindowMean == true) {
			logger.error("Can not set both subtractWindowMean and subtractTotalMean");
			return;
		}
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
		if (subtractTotalMean == true && subtractWindowMean == true) {
			logger.error("Can not set both subtractWindowMean and subtractTotalMean");
			return;
		}
		this.subtractTotalMean = subtractTotalMean;
	}

	/** Gets the current value of activeVarianceThreshold.
	 * @see #activeVarianceThreshold
	 * @return The current value of activeVarianceThreshold.
	 */
	public double getActiveVarianceThreshold() {
		return activeVarianceThreshold;
	}

	/** Sets the current value of activeVarianceThreshold.
	 * @see #activeVarianceThreshold
	 * @param activeVarianceThreshold The current value of activeVarianceThreshold.
	 */
	public void setActiveVarianceThreshold(double activeVarianceThreshold) {
		this.activeVarianceThreshold = activeVarianceThreshold;
	}
	
	// TODO: provide default parameter values, but allow to override them
}
