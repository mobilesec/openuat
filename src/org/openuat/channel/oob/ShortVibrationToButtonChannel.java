/* Copyright Lukas Huser
 * File created 2008-12-03
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
 * between devices by giving a vibration signal to the user. The user
 * should press the button once for each signal. The signal
 * lasts for 500 ms.<br/>
 * The smallest considered time unit for this channel is set to 1000 ms. 
 * 
 * @author Lukas Huser
 * @version 1.0
 */
public class ShortVibrationToButtonChannel extends ButtonChannel {
	
	/* The first interval in ms (before the first signal will be sent). */
	private int initInterval;
	
	/* When in TRANSMIT_SIGNAL mode: Wait some time to let the user read the text. */
	private int textDelay;
	
	/* How long is the signal/vibration (in ms)? */
	private int signalDuration;
	
	/* How long is the (visual) preparatory signal (in ms)? */
	private int prepSignalDuration;
	
	/* Is the prepare signal enabled? */
	private boolean isPrepareEnabled;
	
	/**
	 * Creates a new instance of this channel.<br/>
	 * This constructor is equivalent to
	 * <code>ShortVibrationToButtonChannel(impl, false)</code>.
	 * 
	 * @param impl A suitable <code>ButtonChannelImpl</code> instance
	 * to handle platform dependent method calls.
	 */
	public ShortVibrationToButtonChannel(ButtonChannelImpl impl) {
		this(impl, false);
	}
	
	/**
	 * Creates a new instance of this channel.
	 * 
	 * @param impl A suitable <code>ButtonChannelImpl</code> instance
	 * to handle platform dependent method calls.
	 * @param usePrepareSignal Should a preparatory signal be sent
	 * before a real signal is emitted?
	 */
	public ShortVibrationToButtonChannel(ButtonChannelImpl impl, boolean usePrepareSignal) {
		this.impl = impl;
		isPrepareEnabled = usePrepareSignal;
		
		minTimeUnit		= 1000;
		inputMode		= MODE_PRESS;
		doRoundDown		= false;
		useCarryM		= true;
		showFeedback	= true;
		messageHandler	= null;
		shortDescription = "Short Vibration";
		logger = LogFactory.getLogger("org.openuat.channel.oob.ShortVibrationToButtonChannel");
		statisticsLogger = LogFactory.getLogger("statistics");
		
		initInterval = isPrepareEnabled ? 2500 : 6500;
		textDelay			= 5000;
		signalDuration		= 500;
		prepSignalDuration	= 500;
		
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
		
		Thread t = new Thread(new Runnable() {
			public void run() {
				int signalCount = 0;
				impl.setSignalCount(signalCount);
				impl.setShowCount(showFeedback);
				// start transmission
				impl.showTransmitGui(transmitDisplayText, ButtonChannelImpl.TRANSMIT_PLAIN);
				
				/* transmit the data (given from 'intervals')
				 * If !isPrepareEnabled: a given interval is split into two parts:
				 * - vibrate for 'signalDuration' ms
				 * - wait for 'interval' - 'signalDuration' ms
				 * 
				 * If isPrepareEnabled: a given interval is split into three parts:
				 * - vibrate for 'signalDuration' ms
				 * - wait for 'interval' - 'signalDuration' - 'prepSignalDuration' ms
				 * - display the preparatory signal for 'prepSignalDuration' ms
				 */
				if (!isPrepareEnabled) {
					for (int i = 0; i < intervals.size(); i++) {
						int interval = intervals.item(i) - signalDuration;
						try {
							Thread.sleep(interval);
							signalCount++;
							impl.setSignalCount(signalCount);
							impl.repaint();
							impl.vibrate(signalDuration);
							Thread.sleep(signalDuration);
						} catch (InterruptedException e) {
							logger.warn("Method transmit(byte[]): transmission thread interrupted.", e);
						}
					}
				}
				else { // isPrepareEnabled
					try {
						Thread.sleep(textDelay);
						impl.showTransmitGui(transmitDisplayText, ButtonChannelImpl.TRANSMIT_SIGNAL);
						for (int i = 0; i < intervals.size(); i++) {
							int interval = intervals.item(i) - signalDuration - prepSignalDuration;
							Thread.sleep(interval);
							impl.setSignal(false);
							impl.setPrepareSignal(true);
							impl.repaint();
							Thread.sleep(prepSignalDuration);
							impl.setSignal(false);
							impl.setPrepareSignal(false);
							signalCount++;
							impl.setSignalCount(signalCount);
							impl.repaint();
							impl.vibrate(signalDuration);
							Thread.sleep(signalDuration);
						}
					} catch (InterruptedException e) {
						logger.warn("Method transmit(byte[]): transmission thread interrupted.", e);
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
