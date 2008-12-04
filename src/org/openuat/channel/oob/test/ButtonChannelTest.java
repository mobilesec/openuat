/* Copyright Lukas Huser
 * File created 2008-12-03
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.channel.oob.test;

import java.util.Arrays;

import org.openuat.channel.oob.ButtonChannel;
import org.openuat.util.IntervalList;

import junit.framework.TestCase;

/**
 * Test case for {@link org.openuat.channel.oob.ButtonChannel}.
 * 
 * @author Lukas Huser
 * @version 1.0
 */
public class ButtonChannelTest extends TestCase {

	/*
	 * Helper class that allows to access the protected methods
	 * 'intervalsToBytes' and 'bytesToIntervals'.
	 */
	private class AccessibleButtonChannel extends ButtonChannel {
		
		public AccessibleButtonChannel() {
			// a real subclass should initialize members here
		}

		public void transmit(byte[] message) {
			// not implemented
		}

		public IntervalList bytesToIntervals(byte[] bytes, int minInterval, int bitsPerInterval, int intervalCount) {
			return super.bytesToIntervals(bytes, minInterval, bitsPerInterval, intervalCount);
		}

		public byte[] intervalsToBytes(IntervalList intervals, int minInterval, int bitsPerInterval, boolean roundDown, boolean useCarry) {
			return super.intervalsToBytes(intervals, minInterval, bitsPerInterval, roundDown, useCarry);
		}
	}
	
	/* An instance of the class under test */
	private AccessibleButtonChannel channel;
	
	/* (non-Javadoc)
	 * @see junit.framework.TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		super.setUp();
		channel = new AccessibleButtonChannel();
	}

	/* (non-Javadoc)
	 * @see junit.framework.TestCase#tearDown()
	 */
	protected void tearDown() throws Exception {
		super.tearDown();
	}

	/**
	 * Test method for {@link org.openuat.channel.oob.ButtonChannel#intervalsToBytes(org.openuat.util.IntervalList, int, int, boolean, boolean)}.
	 * <br/>
	 * Simulate button to button channel, with clean intervals (no rounding required)
	 */
	public void testIntervalsToBytes1() {
		int minInterval 	= 300;
		int bitsPerInterval = 3;
		boolean roundDown	= false;
		boolean useCarry	= true;
		
		IntervalList intervals = new IntervalList();
		intervals.add(300);		// 001
		intervals.add(1800);	// 110
		intervals.add(900);		// 011
		intervals.add(2400);	// 000 
		intervals.add(3000);	// 010
		intervals.add(1200);	// 100
		intervals.add(3600);	// 100
		intervals.add(4500);	// 111
		// expected bitstring: 1111 0010 0010 0000 1111 0001
		
		byte[] expected = {(byte)0xf1, (byte)0x20, (byte)0xf2};
		//System.out.println(Arrays.toString(expected));
		byte[] result = channel.intervalsToBytes(intervals, minInterval, bitsPerInterval, roundDown, useCarry);
		//System.out.println(Arrays.toString(result));
		
		assertTrue("Arrays are equal", Arrays.equals(expected, result));
	}
	
	/**
	 * Test method for {@link org.openuat.channel.oob.ButtonChannel#intervalsToBytes(org.openuat.util.IntervalList, int, int, boolean, boolean)}.
	 * <br/>
	 * Simulate button to button channel, intervals must be rounded
	 */
	public void testIntervalsToBytes2() {
		int minInterval 	= 300;
		int bitsPerInterval = 3;
		boolean roundDown	= false;
		boolean useCarry	= true;
		
		IntervalList intervals = new IntervalList();
		intervals.add(330);		// 001 carry:	+30
		intervals.add(1805);	// 110			+35
		intervals.add(860);		// 011			-5
		intervals.add(2495);	// 000 			+90
		intervals.add(3059);	// 010			+149
		intervals.add(950);		// 100			-101
		intervals.add(3609);	// 100			-92
		intervals.add(4442);	// 111			-150
		// expected bitstring: 1111 0010 0010 0000 1111 0001
		
		byte[] expected = {(byte)0xf1, (byte)0x20, (byte)0xf2};
		
		byte[] result = channel.intervalsToBytes(intervals, minInterval, bitsPerInterval, roundDown, useCarry);
		
		assertTrue("Arrays are equal", Arrays.equals(expected, result));
	}
	
	/**
	 * Test method for {@link org.openuat.channel.oob.ButtonChannel#intervalsToBytes(org.openuat.util.IntervalList, int, int, boolean, boolean)}.
	 * <br/>
	 * Simulate a transfer channel, intervals must be rounded.
	 */
	public void testIntervalsToBytes3() {
		int minInterval 	= 1000;
		int bitsPerInterval = 3;
		boolean roundDown	= true;
		boolean useCarry	= true;
		
		IntervalList intervals = new IntervalList();
		intervals.add(1857);	// 001 carry: 	+857
		intervals.add(3490);	// 100			+347
		intervals.add(6300);	// 110			+647
		intervals.add(2230);	// 010			+877
		intervals.add(8122);	// 000			+999
		intervals.add(4900);	// 101			+899
		intervals.add(1101);	// 010			+0
		intervals.add(1045);	// 001			+45
		// expected bitstring: 0010 1010 1000 0101 1010 0001
		
		byte[] expected = {(byte)0xa1, (byte)0x85, (byte)0x2a};
		
		byte[] result = channel.intervalsToBytes(intervals, minInterval, bitsPerInterval, roundDown, useCarry);
		
		assertTrue("Arrays are equal", Arrays.equals(expected, result));
	}
	
	/**
	 * Test method for {@link org.openuat.channel.oob.ButtonChannel#intervalsToBytes(org.openuat.util.IntervalList, int, int, boolean, boolean)}.
	 * <br/>
	 * Simulate a transfer channel, intervals must be rounded.
	 */
	public void testIntervalsToBytes4() {
		int minInterval 	= 500;
		int bitsPerInterval = 3;
		boolean roundDown	= true;
		boolean useCarry	= true;
		
		IntervalList intervals = new IntervalList();
		intervals.add(1857);	// 011 carry: 	+357
		intervals.add(3490);	// 111			+347
		intervals.add(620);		// 001			+467
		intervals.add(2230);	// 101			+197
		intervals.add(3122);	// 110			+319
		intervals.add(4681);	// 010			+0
		intervals.add(954);		// 001			+454
		intervals.add(1045);	// 010			+499
		// expected bitstring: 0100 0101 0110 1010 0111 1011
		
		byte[] expected = {(byte)0x7b, (byte)0x6a, (byte)0x45};
		
		byte[] result = channel.intervalsToBytes(intervals, minInterval, bitsPerInterval, roundDown, useCarry);
		
		assertTrue("Arrays are equal", Arrays.equals(expected, result));
	}

	/**
	 * Test method for {@link org.openuat.channel.oob.ButtonChannel#bytesToIntervals(byte[], int, int, int)}.
	 * <br/>
	 * Simulate a transfer channel.
	 */
	public void testBytesToIntervals1() {
		int minInterval = 1000;
		int bitsPerInterval = 3;
		int intervalCount = 8;
		
		// input bitstring: 0000 1000 1111 1110 1100 1010
		byte[] bytes = {(byte)0xca, (byte)0xfe, (byte)0x08};
		
		// 3 bit chunks: 000 010 001 111 111 011 001 010
		IntervalList expected = new IntervalList();
		expected.add(2000);
		expected.add(1000);
		expected.add(3000);
		expected.add(7000);
		expected.add(7000);
		expected.add(1000);
		expected.add(2000);
		expected.add(8000);
		
		IntervalList result = channel.bytesToIntervals(bytes, minInterval, bitsPerInterval, intervalCount);
		
		assertEquals(expected, result);
	}
	
	/**
	 * Test method for {@link org.openuat.channel.oob.ButtonChannel#bytesToIntervals(byte[], int, int, int)}.
	 * <br/>
	 * Simulate an ideal transfer channel (no delays, no rounding required).
	 */
	public void testIdealChannel() {
		int minInterval = 1000;
		int bitsPerInterval = 3;
		int intervalCount = 8;
		boolean useCarry = true;
		boolean roundDown = true;
		
		// input bitstring: 0000 1000 1111 1110 1100 1010
		byte[] bytes = {(byte)0xca, (byte)0xfe, (byte)0x08};
		
		IntervalList tempList = channel.bytesToIntervals(bytes, minInterval, bitsPerInterval, intervalCount);
		
		byte[] transformedBytes = channel.intervalsToBytes(tempList, minInterval, bitsPerInterval, roundDown, useCarry);
		
		assertTrue("Arrays are equal", Arrays.equals(bytes, transformedBytes));
		
		
		IntervalList intervals = new IntervalList();
		intervals.add(2000);
		intervals.add(1000);
		intervals.add(3000);
		intervals.add(7000);
		intervals.add(7000);
		intervals.add(1000);
		intervals.add(2000);
		intervals.add(8000);
		
		byte[] tempBytes = channel.intervalsToBytes(intervals, minInterval, bitsPerInterval, roundDown, useCarry);
		
		IntervalList transformedList = channel.bytesToIntervals(tempBytes, minInterval, bitsPerInterval, intervalCount);
		
		assertEquals("Lists are equal", intervals, transformedList);
	}

}
