/* Copyright Lukas Huser
 * File created 2008-10-20
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.channel.oob;

import org.openuat.authentication.OOBChannel;
import org.openuat.authentication.OOBMessageHandler;
import org.openuat.util.IntervalList;

/**
 * This class is a common ancestor for all button related
 * oob channels. It provides much of the functionality which
 * is needed in the <code>capture</code> and <code>transmit</code>
 * methods but does not depend on a specific underlying platform.<br/>
 * All platform dependent calls are delegated to an appropriate instance
 * of a subclass of <code>ButtonChannelImpl</code>.
 *
 * @see ButtonChannelImpl
 * 
 * @author Lukas Huser
 * @version 1.0
 */
public abstract class ButtonChannel implements OOBChannel, ButtonInputHandler {
	
	
	
	/**
	 * Length of an oob message that can be sent
	 * over a button channel: 24 bit = 3 byte.<br/>
	 * This value should be a multiple of 
	 * <code>BITS_PER_INTERVAL</code>.<br/>
	 * Note: a high value results in low usability.
	 */
	public static final int MESSAGE_LENGTH		= 24;
	
	/**
	 * From each interval between two button events,
	 * 3 bits of random data can be extracted.<br/>
	 * (See C. Soriente, G. Tsudik:
	 * 'BEDA: Button-Enabled Device Association')
	 */
	public static final int BITS_PER_INTERVAL	= 3;
	
	/**
	 * Input mode: Press. Button presses represent a
	 * button event, button releases are ignored.
	 */
	public static final int MODE_PRESS			= 1;
	
	/**
	 * Input mode: Press/Release. Button presses and
	 * releases both represent a button event.
	 */
	public static final int MODE_PRESS_RELEASE	= 2;
	
	
	
	/**
	 * Delegate platform dependent method calls
	 * to a <code>ButtonChannelImpl</code> instance.
	 */
	protected ButtonChannelImpl impl;
	
	/**
	 * The smallest considered time unit in ms.<br/>
	 * Needed to cope with reaction delays by the user.
	 */
	protected int minTimeUnit;
	
	/**
	 * The current input mode. Can be either
	 * <code>MODE_PRESS</code> or <code>MODE_PRESS_RELEASE</code>.
	 */
	protected int inputMode;
	
	/**
	 * Used for {@link #intervalsToBytes}.<br/>
	 * <code>true</code>: round down<br/>
	 * <code>false</code>: round nearest
	 */
	protected boolean doRoundDown;
	
	/**
	 * Used for {@link #intervalsToBytes}.
	 */
	protected boolean useCarry;
	
	/**
	 * oob message handler
	 * @see #setOOBMessageHandler
	 */
	protected OOBMessageHandler messageHandler;

	
	/**
	 * Some text that is displayed while waiting for user input
	 * (button events). The field should be initialized by
	 * subclasses of this class, such that the text instructs or
	 * informs the user, according to the respective channel.
	 */
	protected String captureDisplayText;
	
	/**
	 * Some text that is displayed before (and, if the channels
	 * doesn't use the display itself to transmit, while) transmitting
	 * data. The text should be instructive 
	 */
	protected String transmitDisplayText;
	
	/*
	 * How many button events (presses/releases) are still to process?
	 */
	private int buttonEventsLeft;
	
	/*
	 * The transmitted and captured oob message as a list of intervals.
	 */
	private IntervalList oobInput;
	
	/*
	 * Helps to keep track of currently captured input.
	 */
	private long timestamp;

	/**
	 * Receives out of band inputs. Listens to key
	 * press/release events and extracts the transmitted
	 * message from the captured time intervals.
	 * 
	 */
	// @Override
	public void capture() {
		buttonEventsLeft = (MESSAGE_LENGTH / BITS_PER_INTERVAL) + 1;
		timestamp = 0L;
		oobInput = new IntervalList();
		impl.showCaptureGui(captureDisplayText, this);
	}	
	
	
	/**
	 * Registers a handler for oob messages. The handler
	 * will be invoked after an oob message has been received
	 * by the <code>capture</code> method.
	 * @param handler oob message handler
	 */
	// @Override
	public void setOOBMessageHandler(OOBMessageHandler handler) {
		this.messageHandler = handler;
	}


	// @Override
	public void buttonPressed() {
		if (buttonEventsLeft > 0) {
			if (timestamp == 0) { // first event: set timestamp
				timestamp = System.currentTimeMillis();
			}
			else { // following events: compute measured interval
				long temp = System.currentTimeMillis();
				oobInput.add((int)(temp - timestamp));
				timestamp = temp;
			}
			buttonEventsLeft--;
			if (buttonEventsLeft <= 0) {
				// massage has been transmitted, pass it on
				if (messageHandler != null) {
					byte[] message = intervalsToBytes(oobInput, minTimeUnit, BITS_PER_INTERVAL, doRoundDown, useCarry);
					messageHandler.handleOOBMessage(OOBChannel.BUTTON_CHANNEL, message);
				}
				else {
					// TODO: log warning
					// logger.warn("Method buttonPressed(): Message received, but 'messageHandler' is null");
				}
			}
		}
	}

	// @Override
	public void buttonReleased() {
		if (inputMode == MODE_PRESS_RELEASE) {
			this.buttonPressed();
		}
	}
	
	
	/**
	 * Converts an <code>IntervalList</code> to a <code>byte[]</code>.
	 * <br/>
	 * <code>bitsPerInterval</code> should not exceed 31, it is truncated to 31 if it's larger.
	 * The result is at most 64 bit = 8 byte long.
	 * If <code>intervals.size() * max(bitsPerInterval, 31)</code> exceeds 64 bit, only
	 * the first 64 bits will be returned.
	 * If the returned number of bits is not a multiple of 8, the remaining
	 * bits in the last byte are set to zero.
	 * 
	 * @param intervals The list of intervals to be converted.
	 * @param minInterval Smallest considered interval or time unit in ms.
	 * @param bitsPerInterval How many bits per interval should be extracted?
	 * @param roundFloor Rounding mode: If <code>true</code>, each interval will be
	 * rounded down to the next multiple of <code>minInterval</code>. If <code>false</code>,
	 * each interval will be rounded to the nearest multiple of <code>minInterval</code>.
	 * @param useCarry Should rounding losses be added to the next interval?
	 * @return Returns the message converted to a <code>byte[]</code>.
	 * 
	 * @see IntervalList
	 */
	protected byte[] intervalsToBytes(IntervalList intervals, int minInterval, int bitsPerInterval, boolean roundDown, boolean useCarry) {
		bitsPerInterval = Math.min(bitsPerInterval, 31);
		int bytes = Math.min(((intervals.size() * bitsPerInterval) + 7) / 8, 8);
		byte[] result = new byte[bytes];
		int range = 1 << bitsPerInterval; // computes range = pow(2, bitsPerInterval)
		long tempResult = 0L;
		
		// extract bits
		int interval = 0;
		int carry = 0;
		int value = 0;
		int shiftIndex = 0;
		long mask = 0L;
		for (int i = 0; i < intervals.size(); i++) {
			interval = intervals.item(i);
			// add carry
			if (useCarry) {
				interval = interval + carry;
			}
			// rounding mode
			if (!roundDown) {
				interval = interval +  minInterval / 2;
			}
			// round down to next multiple of minInterval
			interval = interval - interval % minInterval;
			// compute new carry
			if (useCarry) {
				carry = intervals.item(i) + carry - interval;
			}
			
			// extract value from interval
			interval = interval / minInterval;
			value = interval % range; 
			mask = (long)value << shiftIndex;
			tempResult = tempResult | mask;
			
			shiftIndex += bitsPerInterval;
		}
		
		// fill result
		shiftIndex = 0;
		for (int i = 0; i < bytes; i++) {
			result[i] = (byte)(tempResult >>> shiftIndex);
			shiftIndex += 8;
		}
		
		return result;
	}

	/**
	 * Converts a <code>byte[]</code> to an <code>IntervalList</code>, which is 
	 * suitable to transmit data over a button channel.
	 * <br/>
	 * The length of <code>bytes</code> should not exceed 8 bytes, only the first 8 bytes
	 * will be considered.
	 * <code>bitsPerInterval</code> should not exceed 31, it is truncated to 31 if it's larger.
	 * 
	 * @param bytes The input to be converted to an <code>IntervalList</code>.
	 * @param minInterval Minimum length of a generated interval.
	 * @param bitsPerInterval How many bits are encoded in a generated interval?
	 * @param intervalCount How many intervals will be generated? This parameter must
	 * be consistent with the length of <code>bytes</code> and <code>bitsPerInterval</code>.
	 * It is useful, if the message doesn't align with 1 byte, this is, if there are
	 * 'unused' bits in the last byte.
	 * @return Returns the message converted to an <code>IntervalList</code>.
	 * 
	 * @see IntervalList
	 */
	protected IntervalList bytesToIntervals(byte[] bytes, int minInterval, int bitsPerInterval, int intervalCount) {
		IntervalList result = new IntervalList();
		bitsPerInterval = Math.min(bitsPerInterval, 31);
		int range = 1 << bitsPerInterval; // compute range = pow(2, bitsPerInterval)
		int byteCount = Math.min(bytes.length, 8);
		long bits = 0L;
		
		// put 'bytes' into a long value
		long temp = 0L;
		int shiftIndex = 0;
		for (int i = 0; i < byteCount; i++) {
			temp = (long)bytes[i] & 0xffL;
			temp = temp << shiftIndex;
			bits = bits | temp;
			shiftIndex += 8;
		}
		
		// read bits
		int mask = 0xffffffff >>> (32 - bitsPerInterval);
		shiftIndex = 0;
		int value = 0;
		int interval = 0;
		for (int i = 0; i < intervalCount; i++) {
			value = (int)(bits >>> shiftIndex) & mask;
			if (value == 0) {
				value = range;
			}
			interval = value * minInterval;
			result.add(interval);
			shiftIndex += bitsPerInterval;
		}
		
		return result;
	}
	
}
