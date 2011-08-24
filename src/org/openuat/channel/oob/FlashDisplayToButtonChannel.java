/* Copyright Lukas Huser
 * File created 2008-12-02
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
	
	/* The first interval in ms (before the first signal is displayed). */
	private int initInterval;
	
	/* Wait some time (in ms) to let the user read the 'transmitDisplayText' first. */
	private int textDelay;
	
	/* How long will the signal be displayed (in ms)? */
	private int signalDuration;
	
	/* How long will the preparatory signal be displayed (in ms)? */
	private int prepSignalDuration;
	
	/* Is the prepare signal enabled? */
	private boolean isPrepareEnabled;
	
	/**
	 * Creates a new instance of this channel.<br/>
	 * This constructor is equivalent to
	 * <code>FlashDisplayToButtonChannel(impl, true)</code>.
	 * 
	 * @param impl A suitable <code>ButtonChannelImpl</code> instance
	 * to handle platform dependent method calls.
	 */
	public FlashDisplayToButtonChannel(ButtonChannelImpl impl) {
		this (impl, true);
	}
	
	/**
	 * Creates a new instance of this channel.
	 * 
	 * @param impl A suitable <code>ButtonChannelImpl</code> instance
	 * to handle platform dependent method calls.
	 * @param usePrepareSignal Should a preparatory signal be sent
	 * before a real signal is emitted?
	 */
	public FlashDisplayToButtonChannel(ButtonChannelImpl impl, boolean usePrepareSignal) {
		this.impl = impl;
		isPrepareEnabled = usePrepareSignal;
		
		minTimeUnit		= 1000;
		inputMode		= MODE_PRESS;
		doRoundDown		= false;
		useCarryM		= true;
		showFeedback	= true;
		messageHandler	= null;
		shortDescription = "Flash Display";
		logger = LogFactory.getLogger("org.openuat.channel.oob.FlashDisplayToButtonChannel");
		statisticsLogger = LogFactory.getLogger("statistics");
		
		initInterval		= 2500;
		textDelay			= 5000;
		signalDuration		= 500;
		prepSignalDuration	= 500;
		
		String endl = System.getProperty("line.separator");
		if (endl == null) {
			endl = "\n";
		}
		captureDisplayText	= "Please press the button whenever the other device "
							+ "gives a visual signal." + endl
							+ "This device is ready.";
		
		transmitDisplayText	= "This device will send visual signals. Please press "
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
				impl.showTransmitGui(null, ButtonChannelImpl.TRANSMIT_SIGNAL);
				/* transmit the data (given from 'intervals')
				 * if !isPrepareEnabled: a given interval is split into two parts:
				 * - 'signalDuration' ms to show the signal
				 * - 'interval' - 'signalDuration' to show a blank screen
				 * 
				 * if isPrepareEnabled: a given interval is split into three parts:
				 * - 'signalDuration ms to show the signal
				 * - 'interval' - 'signalDuration' - 'prepSignalDuration' to show a blan screen
				 * - 'prepSignalDuration' to show the preparatory signal
				 */
				impl.setSignal(false);
				for (int i = 0; i < intervals.size(); i++) {
					int interval = intervals.item(i) - signalDuration;
					if (isPrepareEnabled) {
						interval -= prepSignalDuration;
					}
					try {
						Thread.sleep(interval);
						if (isPrepareEnabled) {
							impl.setSignal(false);
							impl.setPrepareSignal(true);
							impl.repaint();
							Thread.sleep(prepSignalDuration);
						}
						impl.setSignal(true);
						impl.setPrepareSignal(false);
						signalCount++;
						impl.setSignalCount(signalCount);
						impl.repaint();
						Thread.sleep(signalDuration);
						impl.setSignal(false);
						impl.setPrepareSignal(false);
						impl.repaint();
					} catch (InterruptedException e) {
						logger.warn("Method transmit(byte[]): transmission thread interrupted", e);
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
