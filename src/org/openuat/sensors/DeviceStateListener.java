/* Copyright Rene Mayrhofer
 * File created 2006-07-13
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.sensors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** This is a small helper class for checking the status of a device
 * with multiple lines/time series based on the events fired by its SampleSource objects.
 * A device is defined to be active when at least one of its lines is active.
 * 
 * It calls the abstract functions toActive and toPassive when the device state
 * changes, and forwards all samples via calls to addSample.
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public abstract class DeviceStateListener {
	/** Our logger. */
	private static Logger logger = LoggerFactory.getLogger("org.openuat.sensors.DeviceStateListener" /*DeviceStateListener.class*/);

	/** Used to remember which of the lines are currently active. */
	private boolean[] lineActive;

	/** The listener objects, one for each line/time series. */
	private TimeSeriesEventListener[] lineListeners;
	
	/** Constructs the device listener objects.
	 * 
	 * @param numLines The number of lines/time series this device has.
	 */
	public DeviceStateListener(int numLines) {
		this.lineActive = new boolean[numLines];
		this.lineListeners = new TimeSeriesEventListener[numLines];
		for (int i=0; i<numLines; i++) {
			lineListeners[i] = new TimeSeriesEventListener(i);
			lineActive[i] = false;
		}
	}
	
	/** Returns an array of SampleSink implementations that can be registered
	 * with the respective source of sample events.
	 * @return An array of SampleSink objects with length numLines as passed to
	 *         the constructor.
	 */
	public SamplesSink[] getSinks() {
		return lineListeners;
	}

	/** Returns an array of SampleSink_Int implementations that can be registered
	 * with the respective source of sample events.
	 * @return An array of SampleSink objects with length numLines as passed to
	 *         the constructor.
	 */
	public SamplesSink_Int[] getSinks_Int() {
		return lineListeners;
	}

	/** Resets the time series to the state as created when freshly constructing it (i.e. quiescent). */
	public void reset() {
		for (int i=0; i<lineActive.length; i++)
			lineActive[i] = false;
	}
	
	/** This method will be called when the device was quiescent and became active with
	 * the last sample.
	 * @param changedLineIndex The line/time series index that caused the change from
	 *                         quiescent to active, i.e. the first line/time series index
	 *                         that became active.
	 * @param numSample The number of the sample within the time series that caused the 
	 *                  state change.
	 */
	protected abstract void toActive(int changedLineIndex, int numSample);
	
	/** This method will be called when the device was active and became quiescent with
	 * the last sample.
	 * @param changedLineIndex The line/time series index that caused the change from
	 *                         active to quiescent, i.e. the last line/time series index
	 *                         that became quiescent.
	 * @param numSample The number of the sample within the time series that caused the 
	 *                  state change.
	 */
	protected abstract void toQuiescent(int changedLineIndex, int numSample);
	
	/** This method will be called when a new sample is received.
	 * 
	 * @param lineIndex The line/time series that received the sample.
	 * @param sample The sample value.
	 * @param numSample The number of the sample within the time series.
	 */
	protected abstract void sampleAdded(int lineIndex, double sample, int numSample);

	/** This method will be called when a new sample is received.
	 * 
	 * This uses integer samples and is called when DeviceStateListener is
	 * used as a SamplesSink_Int.
	 * 
	 * @param lineIndex The line/time series that received the sample.
	 * @param sample The sample value.
	 * @param numSample The number of the sample within the time series.
	 */
	protected abstract void sampleAdded(int lineIndex, int sample, int numSample);

	/** A helper function to check if the whole, multi-dimensional segment is active.
	 * 
	 * @return True when any of the dimensions is active, i.e. when at least one of 
	 *         firstStagesActive is true, false otherwise.
	 */
	private boolean isActive() {
		for (int i=0; i<lineActive.length; i++)
			if (lineActive[i])
				return true;
		return false;
	}

	/** This is a helper class for listening to the sample events. */
	private class TimeSeriesEventListener implements SamplesSink, SamplesSink_Int {
		/** The line index in @see #lineActive that this object is responsible for. */
		int lineIndex;
	
		/** Constructs the helper class object. */
		private TimeSeriesEventListener(int lineIndex) {
			this.lineIndex = lineIndex;
		}
	
		/** Empty implementation of the SamplesSink method. Does nothing. */
		public void addSample(double sample, int index) {
			sampleAdded(lineIndex, sample, index);
		}

		/** Empty implementation of the SamplesSink_Int method. Does nothing. */
		public void addSample(int sample, int index) {
			sampleAdded(lineIndex, sample, index);
		}

		/** When a segment start has been detected, mark the respective device as active. */
		public void segmentStart(int numSample) {
			boolean previouslyActive = isActive();
			lineActive[lineIndex] = true;
			if (!previouslyActive && isActive()) {
				logger.finer("Time series " + lineIndex + " is first to become active at index " + numSample);
				toActive(lineIndex, numSample);
			}
		}

		/** When all lines have become inactive again, mark the respective device as quiescent. */
		public void segmentEnd(int numSample) {
			boolean previouslyActive = isActive();
			lineActive[lineIndex] = false;
			if (previouslyActive && !isActive()) {
				logger.finer("Time series " + lineIndex + " is last to become quiescent");
				toQuiescent(lineIndex, numSample);
			}
		}
	}
}
