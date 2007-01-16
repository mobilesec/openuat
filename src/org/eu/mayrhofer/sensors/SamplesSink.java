/* Copyright Rene Mayrhofer
 * File created 2006-05-01
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.sensors;

/** This interface represents a sink for sample values, and only defines
 * three methods: to add new samples, to react to a segment becoming
 * "active" (by some definition) and to react to a segment becoming "inactive".
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public interface SamplesSink {
	/** Adds a new sample to the sink.
	 * 
	 * @param sample The new sample value to add.
	 * @param index The index of this sample. All samples are required
	 *              to be equally spaced.
	 */
	public void addSample(double sample, int index);

	/** Should be called when it is detected that an active segment
	 * starts, i.e. when it is detected that the source has become
	 * active by some definition.
	 * 
	 * @param index The index at which the active segment starts.
	 */ 
	public void segmentStart(int index);

	/** Should be called when it is detected that an active segment
	 * end, i.e. when it is detected that the source has become
	 * quiescent by some definition.
	 * 
	 * @param index The index at which the active segment ends.
	 */ 
	public void segmentEnd(int index);
}
