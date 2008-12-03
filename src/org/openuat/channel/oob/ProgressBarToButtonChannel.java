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
 * between devices by displaying a interval pattern on the screen and
 * a progress bar that keeps growing over the pattern. The user should
 * press and hold the button on the first colored interval, release it on
 * the next interval etc. thus triggering on each interval border a button
 * event (either press or release).<br/>
 * The smallest considered time unit for this channel is set to 500 ms.
 * 
 * @author Lukas Huser
 * @version 1.0
 */
public class ProgressBarToButtonChannel extends ButtonChannel {

	/**
	 * Creates a new instance of this channel.
	 * 
	 * @param impl A suitable <code>ButtonChannelImpl</code> instance
	 * to handle platform dependent method calls.
	 */
	public ProgressBarToButtonChannel(ButtonChannelImpl impl) {
		this.impl = impl;
		minTimeUnit		= 500;
		inputMode		= MODE_PRESS_RELEASE;
		doRoundDown		= false;
		useCarry		= true;
		messageHandler	= null;
		
		initInterval	= 2000;
		textDelay		= 5000;
		deltaT			= 20;
		
		String endl = System.getProperty("line.separator");
		captureDisplayText	= "Please press and hold the button during the ligth "
							+ "intervals, release it on dark intervals." + endl
							+ "This device is ready.";
		
		transmitDisplayText	= "This device will display the progress bar. Please press"
							+ "the button on the other device.";
	}
	
	/* The first interval in ms (will be painted in gray). */
	private int initInterval;
	
	/* Wait some time (in ms) to let the user read the 'transmitDisplayText' first. */
	private int textDelay;
	
	/* The temporal resolution. Update the screen every 'deltaT' milliseconds. */
	private int deltaT;
	
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
		impl.showTransmitGui(transmitDisplayText, ButtonChannelImpl.TRANSMIT_BAR);
		try {
			Thread.sleep(textDelay);
		} catch (InterruptedException e) {
			// TODO: log warning
			// logger.warn("Method transmit(byte[])", e);
		}
		
		// transmit the data (given from 'intervals')
		// note: 
		int progress = 0;
		long start = System.currentTimeMillis();
		long duration = 0;
		while (progress <= 100) {
			duration = System.currentTimeMillis() - start;
			progress = (int)(((double)duration / (double)intervals.getTotalIntervalLength()) * 100);
			impl.setProgress(progress);
			impl.repaint();
			try {
				Thread.sleep(deltaT);
			} catch (InterruptedException e) {
				// TODO: log warning
				// logger.warn("Method transmit(byte[])", e);
			}
		}
	}

}
