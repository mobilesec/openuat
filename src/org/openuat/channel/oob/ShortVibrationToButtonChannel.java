/* Copyright Lukas Huser
 * File created 2008-12-03
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.channel.oob;

import org.openuat.util.IntervalList;

/**
 * This channel is a <b>transfer</b> channel. It transmits data
 * between devices by giving a vibration signal to the user. The user
 * should press the button once for each signal. The signal
 * lasts for 500 ms.<br/>
 * The smallest considered time unit for this channel is set to 1000 ms. 
 * 
 * @author Lukas Huser
 * @version 1.0
 */
public class ShortVibrationToButtonChannel extends ButtonChannel {

	/**
	 * Creates a new instance of this channel.
	 * 
	 * @param impl A suitable <code>ButtonChannelImpl</code> instance
	 * to handle platform dependent method calls.
	 */
	public ShortVibrationToButtonChannel(ButtonChannelImpl impl) {
		this.impl = impl;
		minTimeUnit		= 1000;
		inputMode		= MODE_PRESS;
		doRoundDown		= true;
		useCarry		= true;
		messageHandler	= null;
		
		initInterval	= 6500;
		signalDuration	= 500;
		
		String endl = System.getProperty("line.separator");
		if (endl == null) {
			endl = "\n";
		}
		captureDisplayText	= "Please press the button whenever the other device "
							+ "gives a vibration signal." + endl
							+ "This device is ready.";
		
		transmitDisplayText	= "This device will send vibration signals. Please press "
							+ "the button on the other device.";
	}
	
	/* The first interval in ms (before the first signal will be sent). */
	private int initInterval;
	
	/* How long is the signal/vibration (in ms)? */
	private int signalDuration;
	
	/**
	 * Transmits provided data over this channel.<br/>
	 * Note: this method blocks the caller and will return when
	 * the transmission process has finished.
	 * 
	 * @param message The Data to be sent over this channel.
	 */
	// @Override
	public void transmit(byte[] message) {
		int intervalCount = MESSAGE_LENGTH / BITS_PER_INTERVAL;
		final IntervalList intervals = bytesToIntervals(message, minTimeUnit, BITS_PER_INTERVAL, intervalCount);
		intervals.addFirst(initInterval);
		
		// start transmission
		impl.showTransmitGui(transmitDisplayText, ButtonChannelImpl.TRANSMIT_PLAIN);
		
		// transmit the data (given from 'intervals')
		// note: a given interval is split into two parts:
		// * vibrate for 'signalDuration' ms
		// * wait for 'interval' - 'signalDuration' ms
		for (int i = 0; i < intervals.size(); i++) {
			int interval = intervals.item(i) - signalDuration;
			try {
				Thread.sleep(interval);
			} catch (InterruptedException e) {
				// TODO: log warning
				// logger.warn("Method transmit(byte[]) in transmission thread", e);
			}
			impl.vibrate(signalDuration);
			try {
				Thread.sleep(signalDuration);
			} catch (InterruptedException e) {
				// TODO: log warning
				// logger.warn("Method transmit(byte[]) in transmission thread", e);
			}
		}

	}

}
