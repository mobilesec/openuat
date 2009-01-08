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
	
	/* The internal representation of the list is just a vector */
	private Vector internalList;
	
	/* Sum of all intervals in this list */
	private int totalIntervalLength;
	
	
	/**
	 * Appends a new interval to the end of the list. The inserted
	 * interval becomes the last interval in the list.
	 * 
	 * @param interval A new interval which is appended to the list.
	 */
	public void add(int interval) {
		totalIntervalLength += interval;
		internalList.addElement(new Integer(interval));
	}
	
	/**
	 * Prepends a new interval to the beginning of the list. The inserted
	 * interval becomes the first interval in the list.
	 * 
	 * @param interval A new interval which is prepended to the list.
	 */
	public void addFirst(int interval) {
		totalIntervalLength += interval;
		internalList.insertElementAt(new Integer(interval), 0);
	}
	
	/**
	 * Returns the interval at a given index.
	 * 
	 * @param i index into the list
	 * @return interval at index <code>i</code>
	 */
	public int item(int i) {
		return ((Integer)internalList.elementAt(i)).intValue();
	}

	/**
	 * Removes an interval from this list at a given index.
	 * 
	 * @param i index into the list
	 */
	public void remove(int i) {
		totalIntervalLength -= this.item(i);
		internalList.removeElementAt(i);
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

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	// @Override
	public boolean equals(Object obj) {
		boolean result = true;
		IntervalList other = null;
		if (obj == null || this.getClass() != obj.getClass()) {
			result = false;
		}
		if (result) {
			try {
				other = (IntervalList)obj;
				result = (this.size() == other.size());
				if (result) {
					for (int i = 0; i < this.size(); i++) {
						if (this.item(i) != other.item(i)) {
							result = false;
							break;
						}
					}
				}
			} catch (ClassCastException e) {
				result = false;
			}
		}
		
		return result;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	//@Override
	public int hashCode() {
		int hash = 7; // arbitrary
		hash = 31 * hash + this.size();
		hash = 31 * hash + this.getTotalIntervalLength();
		for (int i = 0; i < this.size(); i++) {
			hash = 31 * hash + this.item(i);
		}
		return hash;
	}
	
	
}
