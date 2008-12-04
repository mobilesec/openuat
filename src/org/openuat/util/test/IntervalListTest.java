/* Copyright Lukas Huser
 * File created 2008-12-03
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.util.test;

import org.openuat.util.IntervalList;

import junit.framework.TestCase;

/**
 * Test case for {@link org.openuat.util.IntervalList}.
 * 
 * @author Lukas Huser
 * @version 1.0
 */
public class IntervalListTest extends TestCase {

	/* An instance of the class under test */
	private IntervalList list;
	
	/* (non-Javadoc)
	 * @see junit.framework.TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		super.setUp();
		list = new IntervalList();
	}

	/* (non-Javadoc)
	 * @see junit.framework.TestCase#tearDown()
	 */
	protected void tearDown() throws Exception {
		super.tearDown();
	}

	/**
	 * Test method for {@link org.openuat.util.IntervalList#add(int)}.
	 */
	public void testAdd() {
		list.add(10);
		assertEquals("First element", 1, list.size());
		list.add(20);
		assertEquals("Second element", 2, list.size());
	}

	/**
	 * Test method for {@link org.openuat.util.IntervalList#addFirst(int)}.
	 */
	public void testAddFirst() {
		list.addFirst(10);
		assertEquals("First element", 1, list.size());
		list.addFirst(20);
		assertEquals("Second element", 2, list.size());
	}

	/**
	 * Test method for {@link org.openuat.util.IntervalList#item(int)}.
	 */
	public void testItem() {
		int expected  = 10;
		list.add(expected);
		assertEquals(expected, list.item(0));
	}

	/**
	 * Test method for {@link org.openuat.util.IntervalList#remove(int)}.
	 */
	public void testRemove() {
		list.add(10);
		list.add(20);
		list.remove(0);
		assertEquals(1, list.size());
	}

	/**
	 * Test method for {@link org.openuat.util.IntervalList#size()}.
	 */
	public void testSize() {
		list.add(10);
		assertEquals("First element", 1, list.size());
		list.addFirst(20);
		assertEquals("Second element", 2, list.size());
	}

	/**
	 * Test method for {@link org.openuat.util.IntervalList#getTotalIntervalLength()}.
	 */
	public void testGetTotalIntervalLength() {
		list.add(10);
		assertEquals("Add first element", 10, list.getTotalIntervalLength());
		list.addFirst(20);
		assertEquals("Add second element", 30, list.getTotalIntervalLength());
		list.remove(0);
		assertEquals("Remove second element", 10, list.getTotalIntervalLength());
	}
	
	/**
	 * Test method for {@link org.openuat.util.IntervalList#equals(Object)}.
	 */
	public void testEquals() {
		list.add(10);
		list.add(20);
		IntervalList other = new IntervalList();
		other.addFirst(20);
		other.addFirst(10);
		assertTrue(list.equals(other));
	}
}
