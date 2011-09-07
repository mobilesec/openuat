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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * @version 1.1, changes to 1.0: now based on the new base class TimeSeriesBundle
 */
public class TimeSeriesAggregator extends TimeSeriesBundle {
	/** Our logger. */
	private static Logger logger = LoggerFactory.getLogger("org.openuat.sensors.TimeSeriesAggregator" /*TimeSeriesAggregator.class*/);
	
	/** This holds the current, aggregated segment when currently in active state. If
	 * in quiescent state, it is set to null;
	 */
//#if cfg.haveFloatSupport
	private Vector aggregatedSeries = null;
//#endif
	private int[] aggregatedSeries_Int = null;
	/** A negative index means that we are not in an active segment and that
	 * nothing will be written into the (statically allocated) 
	 * aggregatedSeries_Int array.
	 */
	private int aggregatedSeriesIndex_Int = -1;
	
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
		super(numSeries, windowSize, minSegmentSize, maxSegmentSize);
		
		aggregatedSeries_Int = new int[maxSegmentSize+windowSize];
	}

	protected void toActiveFirstLine(int numSample) {
//#if cfg.haveFloatSupport
		aggregatedSeries = new Vector();
//#endif
		// start aggregating into the array
		aggregatedSeriesIndex_Int = 0;
	}

	protected void toQuiescentLastLine(int numSample) {
	  if (logger.isDebugEnabled() && (
//#if cfg.haveFloatSupport
			(aggregatedSeries != null && aggregatedSeries.size() > windowSize) ||
//#endif					
			(aggregatedSeries_Int != null && aggregatedSeriesIndex_Int >= 0 && aggregatedSeriesIndex_Int > windowSize))) {
				logger.debug("Unexpected index of segment end, got " + numSample 
						+ ", expected either " + curSampleIndex
						+ " or " +  + (curSampleIndex+1));
	  }
			
//#if cfg.haveFloatSupport
		if (aggregatedSeries != null && aggregatedSeries.size() > windowSize) {
			if (aggregatedSeries.size()-windowSize >= minSegmentSize) {
				double[] segment = new double[aggregatedSeries.size()-windowSize];
				for (int i=0; i<aggregatedSeries.size()-windowSize; i++)
					segment[i] = ((Double) aggregatedSeries.elementAt(i)).doubleValue();
				if (segmentsSinks != null) {
					logger.debug("Forwarding segment to " + segmentsSinks.size() + " registered sinks");
					for (int i=0; i<segmentsSinks.size(); i++) {
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
			logger.info("toQuiescent called, but aggregated time series not initialized (it was probably forwarded due to reaching the maximum size), ignoring");

		aggregatedSeries = null;
//#endif
		
		if (aggregatedSeries_Int != null && aggregatedSeriesIndex_Int >= 0 && aggregatedSeriesIndex_Int > windowSize) {
			if (aggregatedSeriesIndex_Int-windowSize >= minSegmentSize) {
				int[] segment = new int[aggregatedSeriesIndex_Int-windowSize];
				System.arraycopy(aggregatedSeries_Int, 0, segment, 0, aggregatedSeriesIndex_Int-windowSize);
				if (segmentsSinks_Int != null) {
					logger.debug("Forwarding segment to " + segmentsSinks_Int.size() + " registered sinks");
					for (int i=0; i<segmentsSinks_Int.size(); i++) {
						SegmentsSink_Int s = (SegmentsSink_Int) segmentsSinks_Int.elementAt(i);
						s.addSegment(segment, curSampleIndex-aggregatedSeriesIndex_Int);
					}			
					logger.debug("Finished forwarding segment to sinks");
				}
			}
			else
				logger.info("Active segment with " + aggregatedSeriesIndex_Int +
						" samples is too short, not forwarding");
		}
		else if (aggregatedSeries_Int != null && aggregatedSeriesIndex_Int >= 0)
			logger.info("Detected segment that is smaller than the window size (" +
					aggregatedSeriesIndex_Int + " <= " + windowSize + ", ignoring");
		else
			logger.info("toQuiescent called, but aggregated time series not initialized (it was probably forwarded due to reaching the maximum size), ignoring");

		// stop aggregating
		aggregatedSeriesIndex_Int = -1;
	}

//#if cfg.haveFloatSupport
	protected void sampleAddedLine(int lineIndex, double sample, int numSample) {
		if (aggregatedSeries != null) {
			double magnitude = 0;
			for (int i=0; i<curSample.length; i++)
				magnitude += curSample[i] * curSample[i];
			magnitude = Math.sqrt(magnitude);
			aggregatedSeries.addElement(new Double(magnitude));

			/* this is inside an active segment, so also forward the aggregated samples 
			 * immediately to all registered listeners
			 */
			if (samplesSinks != null) {
				logger.debug("Forwarding single sample to " + samplesSinks.size() + " registered sinks");
				for (int i=0; i<samplesSinks.size(); i++) {
					SamplesSink s = (SamplesSink) samplesSinks.elementAt(i);
					s.addSample(magnitude, numSample);
				}
			}
		}
	}
//#endif

	protected void sampleAddedLine(int lineIndex, int sample, int numSample) {
		if (aggregatedSeries_Int != null & aggregatedSeriesIndex_Int >= 0) {
			int magnitude = 0;
			for (int i=0; i<curSample_Int.length; i++)
				magnitude += curSample_Int[i] * curSample_Int[i];
			// TODO: verify if this is doing the correct thing!
			//magnitude = (int) Math.sqrt(magnitude);
			if (aggregatedSeriesIndex_Int < maxSegmentSize+windowSize)
				aggregatedSeries_Int[aggregatedSeriesIndex_Int++] = magnitude;
			else
				logger.warn("Want to write more active samples than segment size. This should not happen!");

			/* this is inside an active segment, so also forward the aggregated samples 
			 * immediately to all registered listeners
			 */
			if (samplesSinks_Int != null) {
				logger.debug("Forwarding single sample to " + samplesSinks_Int.size() + " registered sinks");
				for (int i=0; i<samplesSinks_Int.size(); i++) {
					SamplesSink_Int s = (SamplesSink_Int) samplesSinks_Int.elementAt(i);
					s.addSample(magnitude, numSample);
				}
			}
		}
	}
}
