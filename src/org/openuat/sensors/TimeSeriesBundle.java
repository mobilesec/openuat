/* Copyright Rene Mayrhofer
 * File created 2008-01-31
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.sensors;

import java.util.Vector;

import org.apache.log4j.Logger;

/** This class implements a collection of multiple time series that belong together,
 * for example multiple dimensions from a single sensor.
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
public abstract class TimeSeriesBundle {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger("org.openuat.sensors.TimeSeriesBundle" /*TimeSeriesBundle.class*/);

	/** This is a helper class to listen to second stage samples and for active/quiescent
	 * events of the TimeSeries.
	 * @author Rene Mayrhofer
	 */
	private class TimeSeriesSinks extends DeviceStateListener {
		private TimeSeriesSinks(int numLines) {
			super(numLines);
		}
		
		/** When the first dimension becomes active, this method starts a new active,
		 * aggregated segment (called by DeviceStateListener when the first line
		 * becomes active).
		 */
		//@Override
		protected void toActive(int lineIndex, int numSample) {
			/* one of the time series just became active when no one was before 
			 * --> start of active segment, start processing
			 */
			curActiveSegmentLength = 0;

			// this is a hook for setting up some resources for further processing
			toActiveFirstLine(numSample);
			
			// also forward this event to the sample listeners
//#if cfg.haveFloatSupport
			if (samplesSinks != null) {
				logger.debug("Forwarding segment start event to " + samplesSinks.size() + " registered sinks");
				for (int i=0; i<samplesSinks.size(); i++) {
					SamplesSink s = (SamplesSink) samplesSinks.elementAt(i);
					s.segmentStart(numSample);
				}			
			}
//#endif			
			if (samplesSinks_Int != null) {
				for (int i=0; i<samplesSinks_Int.size(); i++) {
					SamplesSink_Int s = (SamplesSink_Int) samplesSinks_Int.elementAt(i);
					s.segmentStart(numSample);
				}			
			}
		}
		
		/** When the last dimension becomes quiescent, this method stops the active,
		 * aggregated segment. It will also send the complete segment to registered listeners
		 * (called by DeviceStateListener when the last line becomes quiescent). 
		 */
		//@Override
		protected void toQuiescent(int lineIndex, int numSample) {
			// +1 because first and last samples are added
			if (curSampleIndex != numSample && curSampleIndex+1 != numSample && 
			    curSampleIndex-windowSize != numSample && curSampleIndex-windowSize+1 != numSample)
				logger.warn("Unexpected index of segment end, got " + numSample 
						+ ", expected either " + curSampleIndex
						+ " or " +  + (curSampleIndex+1));

			// this is a hook for final processing/forwarding and to stop processing
			toQuiescentLastLine(numSample);

			/* the last time series just became quiescent when at least one was active before 
			 * --> end of active segment, forward the complete segment
			 */
			curActiveSegmentLength = -1;

			// also forward this event to the sample listeners - but only if it was a genuine event and not called from addSample!
			if (lineIndex != -1) {
//#if cfg.haveFloatSupport
				if (samplesSinks != null) {
					logger.debug("Forwarding segment end event to " + samplesSinks.size() + " registered sinks");
					for (int i=0; i<samplesSinks.size(); i++) {
						SamplesSink s = (SamplesSink) samplesSinks.elementAt(i);
						s.segmentEnd(numSample);
					}
				}
//#endif			
				if (samplesSinks_Int != null) {
					logger.debug("Forwarding segment end event to " + samplesSinks_Int.size() + " registered sinks");
					for (int i=0; i<samplesSinks_Int.size(); i++) {
						SamplesSink_Int s = (SamplesSink_Int) samplesSinks_Int.elementAt(i);
						s.segmentEnd(numSample);
					}
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
		//@Override
		protected void sampleAdded(int lineIndex, double sample, int numSample) {
//#if cfg.haveFloatSupport
			// TODO: maybe also check that all numSample values match for the current sample? would be a good sanity check
			curSample[lineIndex] = sample;
			curSampleReceived[lineIndex] = true;
			if (curActiveSegmentLength >= 0 && isCurSampleComplete()) {
				// this time step is now complete, so immediately process
				curSampleIndex = numSample;
				curActiveSegmentLength++;
				
				sampleAddedLine(lineIndex, sample, numSample);
				
				/* and also check if the maximum segment size has been reached */
				// need to subtract windowSize, because the segment will be shortened in toQuiescent
				if (maxSegmentSize != -1 && curActiveSegmentLength-windowSize == maxSegmentSize) {
					logger.debug("Active segment with " + curActiveSegmentLength +
							" samples has reached maximum segment size, forwarding now");
					// the first parameter is ignored by this toQuiescent implementation
					toQuiescent(-1, numSample);
				}
			}
//#endif			
		}
		//@Override
		protected void sampleAdded(int lineIndex, int sample, int numSample) {
			// TODO: maybe also check that all numSample values match for the current sample? would be a good sanity check
			curSample_Int[lineIndex] = sample;
			curSampleReceived[lineIndex] = true;
			if (curActiveSegmentLength >= 0 && isCurSampleComplete()) {
				// this time step is now complete, so immediately aggregate
				curSampleIndex = numSample;
				curActiveSegmentLength++;
				
				sampleAddedLine(lineIndex, sample, numSample);
				
				/* and also check if the maximum segment size has been reached */
				// need to subtract windowSize, because the segment will be shortened in toQuiescent
				if (maxSegmentSize != -1 && curActiveSegmentLength-windowSize == maxSegmentSize) {
					logger.debug("Active segment with " + curActiveSegmentLength +
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
//#if cfg.haveFloatSupport
	protected TimeSeries[] firstStageSeries;
//#endif
	/** And the integer version. */
	protected TimeSeries_Int[] firstStageSeries_Int;
	/** Holds the TimeSeriesSink objects that are registered as sinks with the 
	 * firstStageSeries objects.
	 */
	protected TimeSeriesSinks firstStageHandlers;
	/** This is just a buffer to keep the current sample dimension until all 
	 * dimensions have been received and can thus be aggregated into a new value
	 * appended to aggregatedSeries.
	 */
//#if cfg.haveFloatSupport
	protected double[] curSample;
//#endif
	/** And the integer version. */
	protected int[] curSample_Int;
	/** Used to mark the sample dimensions that have already been received. It is 
	 * managed solely by TimeSeries#addSample  
	 */
	protected boolean[] curSampleReceived;
	/** Holds the index of the last complete sample that has been received. This
	 * is the index received in the addSample method.
	 */
	protected int curSampleIndex = 0;
	
	/** This window size as passed to the constructor.
	 * @see #TimeSeriesAggregator(int, int, int, int)
	 */
	protected int windowSize;
	
	/** The minimum segment size to use, as passed to the constructor.
	 * @see #TimeSeriesAggregator(int, int, int, int)
	 */
	protected int minSegmentSize;
	
	/** The maximum segment size to use, as passed to the constructor.
	 * @see #TimeSeriesAggregator(int, int, int, int)
	 */
	protected int maxSegmentSize;
	
	/** Holds all registered sinks that should receive active, aggregated, completed segments.
	 * Elements in this list are of type SegmentsSink.
	 */
//#if cfg.haveFloatSupport
	protected Vector segmentsSinks;
//#endif
	protected Vector segmentsSinks_Int;
	
	/** Holds all registered sinks that should receive active, aggregated samples.
	 * Elements in this list are of type SamplesSink. 
	 */
//#if cfg.haveFloatSupport
	protected Vector samplesSinks;
//#endif
	protected Vector samplesSinks_Int;
	
	/** Holds the number of sample in the current active segment or -1 if no line is
	 * active at the moment.
	 */
	protected int curActiveSegmentLength = -1; 
	
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
	protected TimeSeriesBundle(int numSeries, int windowSize, int minSegmentSize, int maxSegmentSize) {
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
		
		curSampleReceived = new boolean[numSeries];
		firstStageHandlers = new TimeSeriesSinks(numSeries);
//#if cfg.haveFloatSupport
		curSample = new double[numSeries];
		firstStageSeries = new TimeSeries[numSeries];
//#endif
		curSample_Int = new int[numSeries];
		firstStageSeries_Int = new TimeSeries_Int[numSeries];
		for (int i=0; i<numSeries; i++) {
//#if cfg.haveFloatSupport
			firstStageSeries[i] = new TimeSeries(windowSize);
			firstStageSeries[i].addNextStageSink(firstStageHandlers.getSinks()[i]);
//#endif
			firstStageSeries_Int[i] = new TimeSeries_Int(windowSize);
			firstStageSeries_Int[i].addNextStageSink(firstStageHandlers.getSinks_Int()[i]);
		}
//#if cfg.haveFloatSupport
		segmentsSinks = new Vector();
		samplesSinks = new Vector();
//#endif
		segmentsSinks_Int = new Vector();
		samplesSinks_Int = new Vector();
	}

	/** Resets the time series to the state as created when freshly constructing it. */
	public void reset() {
		// note: we use curSampleReceived.length instead of firstStageSeries.length because the latter may not exist...
		for (int i=0; i<curSampleReceived.length; i++) {
//#if cfg.haveFloatSupport
			firstStageSeries[i].reset();
//#endif
			firstStageSeries_Int[i].reset();
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
//#if cfg.haveFloatSupport
	public SamplesSink[] getInitialSinks() {
		return firstStageSeries;
	}
//#endif
	
	/** Returns the first stage sink objects that can be registered with the samples source.
	 * 
	 * @return The sink objects to be registered.
	 */
	public SamplesSink_Int[] getInitialSinks_Int() {
		return firstStageSeries_Int;
	}
	
	/** Sets the offset for all internally kept time series.
	 * @see TimeSeries#setOffset(double)
	 */
//#if cfg.haveFloatSupport
	public void setOffset(double offset) {
		for (int i=0; i<firstStageSeries.length; i++)
			firstStageSeries[i].setOffset(offset);
	}
//#endif
	public void setOffset(int offset) {
		for (int i=0; i<firstStageSeries_Int.length; i++)
			firstStageSeries_Int[i].setOffset(offset);
	}

	/** Sets the multiplicator for all internally kept time series.
	 * @see TimeSeries#setMultiplicator(double)
	 */
//#if cfg.haveFloatSupport
	public void setMultiplicator(double multiplicator) {
		for (int i=0; i<firstStageSeries.length; i++)
			firstStageSeries[i].setMultiplicator(multiplicator);
	}
//#endif
	public void setMultiplicator(int multiplicator) {
		for (int i=0; i<firstStageSeries_Int.length; i++)
			firstStageSeries_Int[i].setMultiplicator(multiplicator);
	}

	/** Sets the subtractWindowMean for all internally kept time series.
	 * @see TimeSeries#setSubtractWindowMean(boolean)
	 */
	public void setSubtractWindowMean(boolean subtractWindowMean) {
		logger.warn("Switching window mean subtraction on for all individual dimensions. This is probably not what you want!");
		for (int i=0; i<curSampleReceived.length; i++) {
//#if cfg.haveFloatSupport
			firstStageSeries[i].setSubtractWindowMean(subtractWindowMean);
//#endif
			firstStageSeries_Int[i].setSubtractWindowMean(subtractWindowMean);
		}
	}

	/** Sets the subtractTotalMean for all internally kept time series.
	 * @see TimeSeries#setSubtractTotalMean(boolean)
	 */
	public void setSubtractTotalMean(boolean subtractTotalMean) {
		logger.warn("Switching total mean subtraction on for all individual dimensions. This is probably not what you want!");
		for (int i=0; i<curSampleReceived.length; i++) {
//#if cfg.haveFloatSupport
			firstStageSeries[i].setSubtractTotalMean(subtractTotalMean);
//#endif
			firstStageSeries_Int[i].setSubtractTotalMean(subtractTotalMean);
		}
	}

	/** Sets the activeVarianceThreshold for all internally kept time series.
	 * @see TimeSeries#setActiveVarianceThreshold(double)
	 */
//#if cfg.haveFloatSupport
	public void setActiveVarianceThreshold(double activeVarianceThreshold) {
		for (int i=0; i<firstStageSeries.length; i++)
			firstStageSeries[i].setActiveVarianceThreshold(activeVarianceThreshold);
	}
//#endif
	public void setActiveVarianceThreshold(int activeVarianceThreshold) {
		for (int i=0; i<firstStageSeries_Int.length; i++)
			firstStageSeries_Int[i].setActiveVarianceThreshold(activeVarianceThreshold);
	}

	/** Sets the parameters for all internally kept time series.
	 * @see TimeSeries#setParameters
	 */
//#if cfg.haveFloatSupport
	public void setParameters(TimeSeries.Parameters pars) {
		for (int i=0; i<firstStageSeries.length; i++)
			firstStageSeries[i].setParameters(pars);
	}
//#endif
	public void setParameters(TimeSeries_Int.Parameters pars) {
		for (int i=0; i<firstStageSeries_Int.length; i++)
			firstStageSeries_Int[i].setParameters(pars);
	}

	/** Registers a sink which will receive all active segments when they are complete.
	 * 
	 * @param sink The sink to push new aggregated segments to.
	 */
//#if cfg.haveFloatSupport
	public void addNextStageSegmentsSink(SegmentsSink sink) {
		segmentsSinks.addElement(sink);
		logger.debug("Registered next stage float segments sink " + sink);
	}
//#endif
	public void addNextStageSegmentsSink_Int(SegmentsSink_Int sink) {
		segmentsSinks_Int.addElement(sink);
		logger.debug("Registered next stage integer segments sink " + sink);
	}

	/** Removes a previously registered sink.
	 * 
	 * @param sink The sink to stop pushing segments to.
	 * @return true if removed, false if not (i.e. if it has not been added previously).
	 */
//#if cfg.haveFloatSupport
	public boolean removeNextStageSegmentsSink(SegmentsSink sink) {
		return segmentsSinks.removeElement(sink);
	}
//#endif
	public boolean removeNextStageSegmentsSink(SegmentsSink_Int sink) {
		return segmentsSinks_Int.removeElement(sink);
	}

	/** Registers a sink which will receive all samples within active segments
	 * immediately after they have been aggregated into one dimension.
	 * 
	 * @param sink The sink to push new aggregated segments to.
	 */
//#if cfg.haveFloatSupport
	public void addNextStageSamplesSink(SamplesSink sink) {
		samplesSinks.addElement(sink);
	}
//#endif
	public void addNextStageSamplesSink(SamplesSink_Int sink) {
		samplesSinks_Int.addElement(sink);
	}

	/** Removes a previously registered sink.
	 * 
	 * @param sink The sink to stop pushing segments to.
	 * @return true if removed, false if not (i.e. if it has not been added previously).
	 */
//#if cfg.haveFloatSupport
	public boolean removeNextStageSamplesSink(SamplesSink sink) {
		return samplesSinks.removeElement(sink);
	}
//#endif
	public boolean removeNextStageSamplesSink(SamplesSink_Int sink) {
		return samplesSinks_Int.removeElement(sink);
	}

	
	// this is a hook for setting up some resources for further processing
	protected abstract void toActiveFirstLine(int numSample);

	// this is a hook for final processing/forwarding and to stop processing
	protected abstract void toQuiescentLastLine(int numSample);

//#if cfg.haveFloatSupport
	protected abstract void sampleAddedLine(int lineIndex, double sample, int numSample);
//#endif

	protected abstract void sampleAddedLine(int lineIndex, int sample, int numSample);
}
