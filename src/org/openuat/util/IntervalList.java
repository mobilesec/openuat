/* Copyright Lukas Huser
 * File created 2008-10-24
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.util;

import java.util.Vector;

/**
 * This class represents a list of time intervals
 * (in milliseconds).
 * 
 * @author Lukas Huser
 * @version 1.0
 *
 */
public class IntervalList {
	
	/**
	 * Creates an empty <code>IntervalList</code>.
	 */
	public IntervalList() {
		internalList = new Vector();
		totalIntervalLength = 0;
	}
	
	// the internal list is just a vector
	private Vector internalList;
	
	// sum of all intervals in this list
	private int totalIntervalLength;
	
	
	/**
	 * Adds a new interval to this list.
	 * 
	 * @param interval A new interval which is added to the list.
	 */
	public void add(int interval) {
		totalIntervalLength += interval;
		internalList.add(new Integer(interval));
	}
	
	/**
	 * Returns the interval at a given index.
	 * 
	 * @param i index into the list
	 * @return interval at index <code>i</code>
	 */
	public int item(int i) {
		return ((Integer)internalList.get(i)).intValue();
	}

	/**
	 * Removes an interval from this list at a given index.
	 * 
	 * @param i index into the list
	 */
	public void remove(int i) {
		totalIntervalLength -= this.item(i);
		internalList.remove(i);
	}
	
	/**
	 * Returns the number intervals in this list.
	 * 
	 * @return number of intervals
	 */
	public int size() {
		return internalList.size();
	}
	
	/**
	 * Returns the total length (sum) of all intervals in this list.
	 * 
	 * @return total interval length
	 */
	public int getTotalIntervalLength() {
		return totalIntervalLength;
	}
}
