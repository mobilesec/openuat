/* Copyright Rene Mayrhofer
 * File created 2006-05-01
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.sensors;

import java.util.Vector;

import org.apache.log4j.Logger;

/** This class implements an aggregation of multiple time series into one. To do so,
 * it calculates the magnitude of the multi-dimensional vector for each sample and uses
 * this magnitude as the new (single-dimensional) sample value.
 * 
 * Forwarding this single-dimensional time series to the next step can be done either as
 * single samples during active periods or as complete segments from the start of an 
 * active period to its end. For the former, this class can send to SamplesSink objects,
 * for the latter, it can send to SegmentsSink objects.
 * 
 * It keeps the time series of the first step, i.e. the pre-processing that does a linear
 * transformation to [-1;1] and detects active/quiescent segments, internally. Using the
 * getSinks() method, these internally managed TimeSeries objects can be registered with
 * the samples source.
 * 
 * After an active segment ends, this class emits the aggregated segment.
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class TimeSeriesAggregator {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger("org.openuat.sensors.TimeSeriesAggregator" /*TimeSeriesAggregator.class*/);

	/** This is a helper class to listen to second stage samples and for active/quiescent
	 * events of the TimeSeries.
	 * @author Rene Mayrhofer
	 */
	private class TimeSeriesSinks extends DeviceStateListener {
		private TimeSeriesSinks(int numLines) {
			super(numLines);
		}
		
		/** When the first dimension becomes active, this method starts a new active,
		 * aggregated segment.
		 */
		protected void toActive(int lineIndex, int numSample) {
			/* one of the time series just became active when no one was before 
			 * --> start of active segment, start aggregating
			 */
			aggregatedSeries = new Vector();

			// also forward this event to the sample listeners
			if (samplesSinks != null) {
				logger.debug("Forwarding segment start event to " + samplesSinks.size() + " registered sinks");
				for (int i=0; i<samplesSinks.size(); i++) {
					SamplesSink s = (SamplesSink) samplesSinks.elementAt(i);
					s.segmentStart(numSample);
				}			
			}
			if (samplesSinks_Int != null) {
				for (int i=0; i<samplesSinks_Int.size(); i++) {
					SamplesSink_Int s = (SamplesSink_Int) samplesSinks_Int.elementAt(i);
					s.segmentStart(numSample);
				}			
			}
		}
		
		/** When the last dimension becomes quiescent, this method stops the active,
		 * aggregated segment. It will also send the complete segment to registered listeners. 
		 */
		protected void toQuiescent(int lineIndex, int numSample) {
			// +2 because first and last samples are added - or is it? not quite sure...
			if (curSampleIndex-windowSize+1 != numSample && curSampleIndex-windowSize+2 != numSample)
				logger.warn("Unexpected index of segment end, got " + numSample 
						+ ", expected either " + (curSampleIndex-windowSize+1)
						+ " or " +  + (curSampleIndex-windowSize+2));
			
			/* the last time series just became quiescent when at least one was active before 
			 * --> end of active segment, forward the complete segment
			 */
			if (aggregatedSeries != null && aggregatedSeries.size() > windowSize) {
				if (aggregatedSeries.size()-windowSize >= minSegmentSize) {
					double[] segment = new double[aggregatedSeries.size()-windowSize];
					for (int i=0; i<aggregatedSeries.size()-windowSize; i++)
						segment[i] = ((Double) aggregatedSeries.elementAt(i)).doubleValue();
					if (segmentsSinks != null) {
						logger.debug("Forwarding segment to " + segmentsSinks.size() + " registered sinks");
						for (int i=0; i<samplesSinks.size(); i++) {
							SegmentsSink s = (SegmentsSink) segmentsSinks.elementAt(i);
							s.addSegment(segment, curSampleIndex-aggregatedSeries.size());
						}			
						logger.debug("Finished forwarding segment to sinks");
					}
				}
				else
					logger.info("Active segment with " + aggregatedSeries.size() +
							" samples is too short, not forwarding");
			}
			else if (aggregatedSeries != null)
				logger.info("Detected segment that is smaller than the window size (" +
						aggregatedSeries.size() + " <= " + windowSize + ", ignoring");
			else
				logger.error("toQuiescent called, but aggregated time series not initialized. This should not happen!");

			aggregatedSeries = null;

			if (aggregatedSeries_Int != null && aggregatedSeries_Int.size() > windowSize) {
				if (aggregatedSeries_Int.size()-windowSize >= minSegmentSize) {
					int[] segment = new int[aggregatedSeries_Int.size()-windowSize];
					for (int i=0; i<aggregatedSeries_Int.size()-windowSize; i++)
						segment[i] = ((Integer) aggregatedSeries_Int.elementAt(i)).intValue();
					if (segmentsSinks_Int != null) {
						logger.debug("Forwarding segment to " + segmentsSinks_Int.size() + " registered sinks");
						for (int i=0; i<samplesSinks_Int.size(); i++) {
							SegmentsSink_Int s = (SegmentsSink_Int) segmentsSinks_Int.elementAt(i);
							s.addSegment(segment, curSampleIndex-aggregatedSeries_Int.size());
						}			
						logger.debug("Finished forwarding segment to sinks");
					}
				}
				else
					logger.info("Active segment with " + aggregatedSeries_Int.size() +
							" samples is too short, not forwarding");
			}
			else if (aggregatedSeries_Int != null)
				logger.info("Detected segment that is smaller than the window size (" +
						aggregatedSeries_Int.size() + " <= " + windowSize + ", ignoring");
			else
				logger.error("toQuiescent called, but aggregated time series not initialized. This should not happen!");

			aggregatedSeries_Int = null;

			// also forward this event to the sample listeners
			if (samplesSinks != null) {
				logger.debug("Forwarding segment end event to " + samplesSinks.size() + " registered sinks");
				for (int i=0; i<samplesSinks.size(); i++) {
					SamplesSink s = (SamplesSink) samplesSinks.elementAt(i);
					s.segmentEnd(numSample);
				}
			}
			if (samplesSinks_Int != null) {
				logger.debug("Forwarding segment end event to " + samplesSinks_Int.size() + " registered sinks");
				for (int i=0; i<samplesSinks_Int.size(); i++) {
					SamplesSink_Int s = (SamplesSink_Int) samplesSinks_Int.elementAt(i);
					s.segmentEnd(numSample);
				}			
			}
		}
		
		/** Buffers the current sample until all dimensions have been received, and
		 * aggregates to a single dimension as soon as it is complete.
		 * @see TimeSeriesAggregator#curSample for buffering the current sample dimensions
		 * @see TimeSeriesAggregator#aggregatedSeries for the aggregated values written by this method
		 * @see TimeSeriesAggregator#isCurSampleComplete() for checking if the current sample is complete
		 * @see TimeSeriesAggregator#curSampleReceived is reset when all sample dimensions are complete and have been aggregated
		 */
		protected void sampleAdded(int lineIndex, double sample, int numSample) {
			// TODO: maybe also check that all numSample values match for the current sample? would be a good sanity check
			curSample[lineIndex] = sample;
			curSampleReceived[lineIndex] = true;
			// if currently active, aggregatedSeries will be set
			if (aggregatedSeries != null && isCurSampleComplete()) {
				// this time step is now complete, so immediately aggregate
				curSampleIndex = numSample;
				double magnitude = 0;
				for (int i=0; i<curSample.length; i++) {
					magnitude += curSample[i] * curSample[i];
					curSampleReceived[i] = false;
				}
				magnitude = Math.sqrt(magnitude);
				aggregatedSeries.addElement(new Double(magnitude));

				/* this is inside an active segment, so also forward the samples 
				 * immediately to all registered listeners
				 */
				if (samplesSinks != null) {
					logger.debug("Forwarding single sample to " + samplesSinks.size() + " registered sinks");
					for (int i=0; i<samplesSinks.size(); i++) {
						SamplesSink s = (SamplesSink) samplesSinks.elementAt(i);
						s.addSample(magnitude, numSample);
					}
				}
				
				/* and also check if the maximum segment size has been reached */
				// need to subtract windowSize, because the segment will be shortened in toQuiescent
				if (maxSegmentSize != -1 && aggregatedSeries.size()-windowSize == maxSegmentSize) {
					logger.debug("Active segment with " + aggregatedSeries.size() +
							" samples has reached maximum segment size, forwarding now");
					// the first parameter is ignored by this toQuiescent implementation
					toQuiescent(-1, numSample);
				}
			}
		}
		protected void sampleAdded(int lineIndex, int sample, int numSample) {
			// TODO: maybe also check that all numSample values match for the current sample? would be a good sanity check
			curSample_Int[lineIndex] = sample;
			curSampleReceived[lineIndex] = true;
			// if currently active, aggregatedSeries will be set
			if (aggregatedSeries_Int != null && isCurSampleComplete()) {
				// this time step is now complete, so immediately aggregate
				curSampleIndex = numSample;
				int magnitude = 0;
				for (int i=0; i<curSample_Int.length; i++) {
					magnitude += curSample_Int[i] * curSample_Int[i];
					curSampleReceived[i] = false;
				}
				magnitude = (int) Math.sqrt(magnitude);
				aggregatedSeries_Int.addElement(new Integer(magnitude));

				/* this is inside an active segment, so also forward the samples 
				 * immediately to all registered listeners
				 */
				if (samplesSinks_Int != null) {
					logger.debug("Forwarding single sample to " + samplesSinks_Int.size() + " registered sinks");
					for (int i=0; i<samplesSinks_Int.size(); i++) {
						SamplesSink_Int s = (SamplesSink_Int) samplesSinks_Int.elementAt(i);
						s.addSample(magnitude, numSample);
					}
				}
				
				/* and also check if the maximum segment size has been reached */
				// need to subtract windowSize, because the segment will be shortened in toQuiescent
				if (maxSegmentSize != -1 && aggregatedSeries_Int.size()-windowSize == maxSegmentSize) {
					logger.debug("Active segment with " + aggregatedSeries_Int.size() +
							" samples has reached maximum segment size, forwarding now");
					// the first parameter is ignored by this toQuiescent implementation
					toQuiescent(-1, numSample);
				}
			}
		}
	}
	
	/** These are the time series for the first stage. The objects are responsible for
	 * an initial linear transform of the sample values to [-1;1] and for detecting 
	 * active/quiescent segments within the single dimensions.
	 */
	private TimeSeries[] firstStageSeries;
	/** And the integer version. */
	private TimeSeries_Int[] firstStageSeries_Int;
	/** Holds the TimeSeriesSink objects that are registered as sinks with the 
	 * firstStageSeries objects.
	 */
	private TimeSeriesSinks firstStageHandlers;
	/** This is just a buffer to keep the current sample dimension until all 
	 * dimensions have been received and can thus be aggregated into a new value
	 * appended to aggregatedSeries.
	 */
	private double[] curSample;
	/** And the integer version. */
	private int[] curSample_Int;
	/** Used to mark the sample dimensions that have already been received. It is 
	 * managed solely by TimeSeries#addSample  
	 */
	private boolean[] curSampleReceived;
	/** Holds the index of the last complete sample that has been received. This
	 * is the index received in the addSample method.
	 */
	private int curSampleIndex = 0;
	/** This holds the current, aggregated segment when currently in active state. If
	 * in quiescent state, it is set to null;
	 */
	private Vector aggregatedSeries = null;
	private Vector aggregatedSeries_Int = null;
	
	/** This window size as passed to the constructor.
	 * @see #TimeSeriesAggregator(int, int, int, int)
	 */
	private int windowSize;
	
	/** The minimum segment size to use, as passed to the constructor.
	 * @see #TimeSeriesAggregator(int, int, int, int)
	 */
	private int minSegmentSize;
	
	/** The maximum segment size to use, as passed to the constructor.
	 * @see #TimeSeriesAggregator(int, int, int, int)
	 */
	private int maxSegmentSize;
	
	/** Holds all registered sinks that should receive active, aggregated, completed segments.
	 * Elements in this list are of type SegmentsSink.
	 */
	private Vector segmentsSinks;
	private Vector segmentsSinks_Int;
	
	/** Holds all registered sinks that should receive active, aggregated samples.
	 * Elements in this list are of type SamplesSink. 
	 */
	private Vector samplesSinks;
	private Vector samplesSinks_Int;
	
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
	public TimeSeriesAggregator(int numSeries, int windowSize, int minSegmentSize, int maxSegmentSize) {
		if (numSeries <= 0) {
			throw new IllegalArgumentException("Number of time series must be > 0");
		}
		if (windowSize <= 0) {
			throw new IllegalArgumentException("Window size must be > 0");
		}
		if (minSegmentSize <= 0 /*|| minSegmentSize > windowSize*/) {
			throw new IllegalArgumentException("Minimum segment size must be > 0"/* and <= windowSize"*/);
		}
		if (maxSegmentSize != -1 && (maxSegmentSize <= 0 || maxSegmentSize < minSegmentSize)) {
			throw new IllegalArgumentException("Maximum segment size must be > 0 and >= minimum segment size");
		}
		
		this.windowSize = windowSize;
		this.minSegmentSize = minSegmentSize;
		this.maxSegmentSize = maxSegmentSize;
		
		curSample = new double[numSeries];
		curSample_Int = new int[numSeries];
		curSampleReceived = new boolean[numSeries];
		firstStageSeries = new TimeSeries[numSeries];
		firstStageHandlers = new TimeSeriesSinks(numSeries);
		for (int i=0; i<numSeries; i++) {
			firstStageSeries[i] = new TimeSeries(windowSize);
			firstStageSeries[i].addNextStageSink(firstStageHandlers.getSinks()[i]);
		}
		segmentsSinks = new Vector();
		samplesSinks = new Vector();
	}

	/** Resets the time series to the state as created when freshly constructing it. */
	public void reset() {
		for (int i=0; i<firstStageSeries.length; i++) {
			firstStageSeries[i].reset();
		}
		curSampleIndex = 0;
		firstStageHandlers.reset();
	}
	
	/** A helper function to check if the current sample has been received completely.
	 * 
	 * @return True when all dimensions of the current sample have been received, i.e. when
	 *         all of curSampleReceived are true, false otherwise.
	 */
	private boolean isCurSampleComplete() {
		for (int i=0; i<curSampleReceived.length; i++)
			if (! curSampleReceived[i])
				return false;
		return true;
	}
	
	/** Returns the first stage sink objects that can be registered with the samples source.
	 * 
	 * @return The sink objects to be registered.
	 */
	public SamplesSink[] getInitialSinks() {
		return firstStageSeries;
	}
	
	/** Sets the offset for all internally kept time series.
	 * @see TimeSeries#setOffset(double)
	 */
	public void setOffset(double offset) {
		for (int i=0; i<firstStageSeries.length; i++)
			firstStageSeries[i].setOffset(offset);
	}
	public void setOffset(int offset) {
		for (int i=0; i<firstStageSeries_Int.length; i++)
			firstStageSeries_Int[i].setOffset(offset);
	}

	/** Sets the multiplicator for all internally kept time series.
	 * @see TimeSeries#setMultiplicator(double)
	 */
	public void setMultiplicator(double multiplicator) {
		for (int i=0; i<firstStageSeries.length; i++)
			firstStageSeries[i].setMultiplicator(multiplicator);
	}
	public void setMultiplicator(int multiplicator) {
		for (int i=0; i<firstStageSeries_Int.length; i++)
			firstStageSeries_Int[i].setMultiplicator(multiplicator);
	}

	/** Sets the subtractWindowMean for all internally kept time series.
	 * @see TimeSeries#setSubtractWindowMean(boolean)
	 */
	public void setSubtractWindowMean(boolean subtractWindowMean) {
		for (int i=0; i<firstStageSeries.length; i++)
			firstStageSeries[i].setSubtractWindowMean(subtractWindowMean);
	}

	/** Sets the subtractTotalMean for all internally kept time series.
	 * @see TimeSeries#setSubtractTotalMean(boolean)
	 */
	public void setSubtractTotalMean(boolean subtractTotalMean) {
		for (int i=0; i<firstStageSeries.length; i++)
			firstStageSeries[i].setSubtractTotalMean(subtractTotalMean);
	}

	/** Sets the activeVarianceThreshold for all internally kept time series.
	 * @see TimeSeries#setActiveVarianceThreshold(double)
	 */
	public void setActiveVarianceThreshold(double activeVarianceThreshold) {
		for (int i=0; i<firstStageSeries.length; i++)
			firstStageSeries[i].setActiveVarianceThreshold(activeVarianceThreshold);
	}
	public void setActiveVarianceThreshold(int activeVarianceThreshold) {
		for (int i=0; i<firstStageSeries_Int.length; i++)
			firstStageSeries_Int[i].setActiveVarianceThreshold(activeVarianceThreshold);
	}

	/** Registers a sink which will receive all active segments when they are complete.
	 * 
	 * @param sink The sink to push new aggregated segments to.
	 */
	public void addNextStageSegmentsSink(SegmentsSink sink) {
		segmentsSinks.addElement(sink);
	}
	public void addNextStageSegmentsSink_Int(SegmentsSink_Int sink) {
		segmentsSinks_Int.addElement(sink);
	}

	/** Removes a previously registered sink.
	 * 
	 * @param sink The sink to stop pushing segments to.
	 * @return true if removed, false if not (i.e. if it has not been added previously).
	 */
	public boolean removeNextStageSegmentsSink(SegmentsSink sink) {
		return segmentsSinks.removeElement(sink);
	}
	public boolean removeNextStageSegmentsSink(SegmentsSink_Int sink) {
		return segmentsSinks_Int.removeElement(sink);
	}

	/** Registers a sink which will receive all samples within active segments
	 * immediately after they have been aggregated into one dimension.
	 * 
	 * @param sink The sink to push new aggregated segments to.
	 */
	public void addNextStageSamplesSink(SamplesSink sink) {
		samplesSinks.addElement(sink);
	}
	public void addNextStageSamplesSink(SamplesSink_Int sink) {
		samplesSinks_Int.addElement(sink);
	}

	/** Removes a previously registered sink.
	 * 
	 * @param sink The sink to stop pushing segments to.
	 * @return true if removed, false if not (i.e. if it has not been added previously).
	 */
	public boolean removeNextStageSamplesSink(SamplesSink sink) {
		return samplesSinks.removeElement(sink);
	}
	public boolean removeNextStageSamplesSink(SamplesSink_Int sink) {
		return samplesSinks_Int.removeElement(sink);
	}
}
