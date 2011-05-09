/* Copyright Lukas Huser
 * File created 2009-02-11
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.channel.oob;

import java.util.Timer;
import java.util.TimerTask;

import org.openuat.authentication.OOBChannel;
import org.openuat.channel.oob.desktop.ButtonChannelImpl;
import org.openuat.log.LogFactory;
import org.openuat.util.IntervalList;

/**
 * This channel is a <b>transfer</b> channel. It transmits data
 * between devices by displaying an interval pattern on the screen.
 * Always two intervals together form a 'power bar': the lower interval
 * is painted gray, the upper one is colored.
 * A progress gauge keeps growing over the pattern and the user should
 * press and hold the button on the higher interval
 * of each power bar an release the button on the lower ones. <br/>
 * The smallest considered time unit for this channel is set to 600 ms.
 * 
 * @author Lukas Huser
 * @version 1.0
 */
public class PowerBarToButtonChannel extends ButtonChannel {

	/* The first interval in ms (will be painted in gray). */
	private int initInterval;
	
	/* The last interval. Is only added in case the total interval count is odd.
	 */
	private int endInterval;
	
	/* Wait some time (in ms) to let the user read the 'transmitDisplayText' first. */
	private int textDelay;
	
	/* Repeatedly repaints the gui */
	private Timer timer;
	
	/* The temporal resolution. Update the screen every 'deltaT' milliseconds. */
	private int deltaT;
	
	/* Transmission start timestamp */
	private long startTime;
	
	/**
	 * Creates a new instance of this channel.
	 * 
	 * @param impl A suitable <code>ButtonChannelImpl</code> instance
	 * to handle platform dependent method calls.
	 */
	public PowerBarToButtonChannel(ButtonChannelImpl impl) {
		this.impl = impl;
		minTimeUnit		= 600;
		inputMode		= MODE_PRESS_RELEASE;
		doRoundDown		= false;
		useCarryM		= false;
		showFeedback	= true;
		messageHandler	= null;
		shortDescription = "Power Bar";
		logger = LogFactory.getLogger("org.openuat.channel.oob.ProgressBarToButtonChannel");
		statisticsLogger = LogFactory.getLogger("statistics");
		
		initInterval	= 2000;
		endInterval		= 1000;
		textDelay		= 5000;
		deltaT			= 40;
		timer			= null; // Note: For every transmission, a new Timer instance is created.
		startTime		= 0L;
		
		String endl = System.getProperty("line.separator");
		if (endl == null) {
			endl = "\n";
		}
		captureDisplayText	= "Please press and hold the button during the green "
							+ "intervals, release it during gray intervals." + endl
							+ "This device is ready.";
		
		transmitDisplayText	= "This device will display the power bars. Please press "
							+ "the button on the other device.";
	}	
	
	/**
	 * Transmits provided data over this channel.<br/>
	 * Note: this method does not block the caller and returns
	 * immediately.
	 * 
	 * @param message The Data to be sent over this channel.
	 */
	// @Override
	public void transmit(byte[] message) {
		int intervalCount = MESSAGE_LENGTH / BITS_PER_INTERVAL;
		final IntervalList intervals = bytesToIntervals(message, minTimeUnit, BITS_PER_INTERVAL, intervalCount);
		if (statisticsLogger.isTraceEnabled()) {
			statisticsLogger.trace("[STAT] transmitted intervals: " + intervals.toString());
		}
		intervals.addFirst(initInterval);
		// add the last interval only if the interval count is odd
		if (intervals.size() % 2 != 0) {
			intervals.add(endInterval);
		}
		impl.setInterval(intervals);
		impl.setShowCount(showFeedback);
		
		// now run transmission in a separate thread
		final TimerTask task = new TimerTask() {
			public void run() {
				long duration = System.currentTimeMillis() - startTime;
				float progress = (float)(((double)duration / (double)intervals.getTotalIntervalLength()) * 100.0);
				impl.setProgress(progress);
				impl.repaint();
				if (progress > 100f) {
					timer.cancel();
					if (messageHandler != null) {
						messageHandler.handleOOBMessage(OOBChannel.BUTTON_CHANNEL, new byte[]{(byte)1});
					}
				}
			}
		};
		
		Thread t = new Thread(new Runnable() {
			public void run() {
				impl.showTransmitGui(transmitDisplayText, ButtonChannelImpl.TRANSMIT_PLAIN);
				try {
					Thread.sleep(textDelay);
				} catch (InterruptedException e) {
					logger.warn("Method transmit(byte[]): transmission thread interrupted.", e);
				}
				impl.showTransmitGui(null, ButtonChannelImpl.TRANSMIT_VERT_BARS);
				// transmit the data (given from 'intervals')
				timer = new Timer();
				startTime = System.currentTimeMillis();
				timer.scheduleAtFixedRate(task, 0, deltaT);
			}
		});
		t.start();
	}

}
