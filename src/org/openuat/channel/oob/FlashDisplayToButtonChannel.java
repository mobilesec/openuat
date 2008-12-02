/* Copyright Lukas Huser
 * File created 2008-12-02
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
 * between devices with the help of a visual signal, which is displayed
 * for a short time on screen.<br/>
 * The signal is usually very simple, for example a black square, and
 * will be displayed for 500 ms.<br/>
 * The smallest considered time unit for this channel is set to 1000 ms.
 * 
 * @author Lukas Huser
 * @version 1.0
 */
public class FlashDisplayToButtonChannel extends ButtonChannel {

	/**
	 * Creates a new instance of this channel.
	 * 
	 * @param impl A suitable <code>ButtonChannelImpl</code> instance
	 * to handle platform dependent method calls.
	 */
	public FlashDisplayToButtonChannel(ButtonChannelImpl impl) {
		this.impl = impl;
		minTimeUnit		= 1000;
		inputMode		= MODE_PRESS;
		doRoundDown		= true;
		useCarry		= true;
		messageHandler	= null;
		
		initInterval	= 2500;
		textDelay		= 5000;
		signalDuration	= 500;
		
		captureDisplayText	= "Please press the button whenever the other device "
							+ "gives a visual signal.\n"
							+ "This device is ready.";
		
		transmitDisplayText	= "This device will send visual signals. Please press"
							+ "the button on the other device.";
	}
	
	/* The first interval in ms (before the first signal is displayed). */
	private int initInterval;
	
	/* Wait some time (in ms) to let the user read the 'transmitDisplayText' first. */
	private int textDelay;
	
	/* How long will the signal be displayed (in ms)? */
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
		impl.showTransmitGui(transmitDisplayText, ButtonChannelImpl.TRANSMIT_SIGNAL);
		try {
			Thread.sleep(textDelay);
		} catch (InterruptedException e) {
			// TODO: log warning
			// logger.warn("Method transmit(byte[])", e);
		}
		
		// transmit the data (given from 'intervals')
		// note: a given interval is split into two parts:
		// * 'signalDuration' ms to show the signal
		// * 'interval' - 'signalDuration' to show a blank screen
		impl.setSignal(false);
		for (int i = 0; i < intervals.size(); i++) {
			int interval = intervals.item(i) - signalDuration;
			try {
				Thread.sleep(interval);
			} catch (InterruptedException e) {
				// TODO: log warning
				// logger.warn("Method transmit(byte[]) in transmission thread", e);
			}
			impl.setSignal(true);
			impl.repaint();
			try {
				Thread.sleep(signalDuration);
			} catch (InterruptedException e) {
				// TODO: log warning
				// logger.warn("Method transmit(byte[]) in transmission thread", e);
			}
			impl.setSignal(false);
			impl.repaint();
		}
	}

}
