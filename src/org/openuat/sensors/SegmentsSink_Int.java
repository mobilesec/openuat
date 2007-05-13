/* Copyright Rene Mayrhofer
 * File created 2007-05-12
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.sensors;

/** This interface represents a sink for whole time series segments.
 * 
 * In contrast to the SegmentsSink interface, this one uses int values for 
 * samples, and is thus better suited for resource limited scenarios like J2ME.
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public interface SegmentsSink_Int {
	/** Adds a new segment to the sink.
	 * 
	 * @param sample The new time series segment to add.
	 * @param index The start index of this segment. All samples are required
	 *              to be equally spaced.
	 */
	public void addSegment(int[] segment, int startIndex);
}
