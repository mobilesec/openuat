/* Copyright Lukas Huser
 * File created 2009-02-05
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.channel.oob;

import org.openuat.authentication.OOBChannel;
import org.openuat.channel.oob.ButtonChannelImpl;
import org.openuat.log.LogFactory;
import org.openuat.util.IntervalList;

/**
 * This channel is a <b>transfer</b> channel. It transmits data
 * between devices with the help of visual signals, which are represented
 * as a traffic light. There are three states: red - do nothing,
 * yellow - prepare, green - act (press button on other device)<br/>
 * The yellow signal will be displayed for 350 ms and the green signal
 * will be displayed for 500 ms.<br/>
 * The smallest considered time unit for this channel is set to 1000 ms.
 * 
 * @author Lukas Huser
 * @version 1.0
 */
public class TrafficLightToButtonChannel extends ButtonChannel {

	/* The first interval in ms (before the first signal is displayed). */
	private int initInterval;
	
	/* Wait some time (in ms) to let the user read the 'transmitDisplayText' first. */
	private int textDelay;
	
	/* How long will the signal (green light) be displayed (in ms)? */
	private int signalDuration;
	
	/* How long will the preparatory signal (yellow light) be displayed (in ms)? */
	private int prepSignalDuration;
	
	/**
	 * Creates a new instance of this channel.
	 * 
	 * @param impl A suitable <code>ButtonChannelImpl</code> instance
	 * to handle platform dependent method calls.
	 */
	public TrafficLightToButtonChannel(ButtonChannelImpl impl) {
		this.impl = impl;
		
		minTimeUnit		= 1000;
		inputMode		= MODE_PRESS;
		doRoundDown		= false;
		useCarryM		= true;
		showFeedback	= true;
		messageHandler	= null;
		shortDescription = "Traffic Light";
		logger = LogFactory.getLogger("org.openuat.channel.oob.TrafficLightToButtonChannel");
		statisticsLogger = LogFactory.getLogger("statistics");
		
		initInterval		= 2500;
		textDelay			= 5000;
		signalDuration		= 500;
		prepSignalDuration	= 350;
		
		String endl = System.getProperty("line.separator");
		if (endl == null) {
			endl = "\n";
		}
		captureDisplayText	= "Please press the button whenever the traffic "
							+ "ligth on the other device turns green."+ endl
							+ "This device is ready.";
		
		transmitDisplayText	= "This device will show the traffic light. Please press "
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
		
		// now run the transmission in a separate thread
		Thread t = new Thread(new Runnable() {
			public void run() {
				int signalCount = 0;
				impl.setSignalCount(signalCount);
				impl.setShowCount(showFeedback);
				impl.showTransmitGui(transmitDisplayText, ButtonChannelImpl.TRANSMIT_PLAIN);
				try {
					Thread.sleep(textDelay);
				} catch (InterruptedException e) {
					logger.warn("Method transmit(byte[]): transmission thread interrupted.", e);
				}
				
				impl.showTransmitGui(null, ButtonChannelImpl.TRANSMIT_TRAFFIC_LIGHT);
				/* transmit the data (given from 'intervals')
				 * a given interval is split into three parts:
				 * - 'signalDuration ms to show the green light
				 * - 'interval' - 'signalDuration' - 'prepSignalDuration' to show the red light
				 * - 'prepSignalDuration' to show the yellow light
				 */
				impl.setSignal(false);
				for (int i = 0; i < intervals.size(); i++) {
					int interval = intervals.item(i) - signalDuration - prepSignalDuration;
					try {
						Thread.sleep(interval);
						impl.setSignal(false);
						impl.setPrepareSignal(true);
						impl.repaint();
						Thread.sleep(prepSignalDuration);
						impl.setSignal(true);
						impl.setPrepareSignal(false);
						signalCount++;
						impl.setSignalCount(signalCount);
						impl.repaint();
						Thread.sleep(signalDuration);
						impl.setSignal(false);
						impl.setPrepareSignal(false);
						impl.repaint();
					} catch (InterruptedException e1) {
						logger.warn("Method transmit(byte[]): transmission thread interrupted", e1);
					}
				}
				if (messageHandler != null) {
					messageHandler.handleOOBMessage(OOBChannel.BUTTON_CHANNEL, new byte[]{(byte)1});
				}
			}
		});
		t.start();
	}

}
