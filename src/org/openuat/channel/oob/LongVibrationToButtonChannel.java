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
import org.openuat.log.LogFactory;
import org.openuat.util.IntervalList;

/**
 * This channel is a <b>transfer</b> channel. It transmits data
 * between devices by giving a vibration signal to the user. The user
 * should press and hold the button while the device vibrates, and 
 * release the button when it doesn't vibrate.<br/>
 * The smallest considered time unit for this channel is set to 1000 ms.
 * 
 * @author Lukas Huser
 * @version 1.0
 */
public class LongVibrationToButtonChannel extends ButtonChannel {
	
	/* The first interval in ms (before the first signal will be sent). */
	private int initInterval;
	
	/* The last interval in ms. This is only needed if the number of intervals
	 * (which actually contain the data) is even, and hence the number of button
	 * events is odd. This situation leads to an empty (non-vibrating) last
	 * interval and an additional vibration signal is needed to make it clear
	 * when this interval end. */
	private int endInterval;
	
	/* When in TRANSMIT_SIGNAL mode: Wait some time to let the user read the text. */
	private int textDelay;
	
	/* How long is the (visual) preparatory signal (in ms)? */
	private int prepSignalDuration;
	
	/* Is the prepare signal enabled? */
	private boolean isPrepareEnabled;
	
	/**
	 * Creates a new instance of this channel.<br/>
	 * This constructor is equivalent to
	 * <code>LongVibrationToButtonChannel(impl, false)</code>.
	 * 
	 * @param impl A suitable <code>ButtonChannelImpl</code> instance
	 * to handle platform dependent method calls.
	 */
	public LongVibrationToButtonChannel(ButtonChannelImpl impl) {
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
	public LongVibrationToButtonChannel(ButtonChannelImpl impl, boolean usePrepareSignal) {
		this.impl = impl;
		isPrepareEnabled = usePrepareSignal;
		
		minTimeUnit		= 1000;
		inputMode		= MODE_PRESS_RELEASE;
		doRoundDown		= false;
		useCarry		= true;
		showFeedback	= true;
		messageHandler	= null;
		shortDescription = "Long Vibration";
		logger = LogFactory.getLogger("org.openuat.channel.oob.LongVibrationToButtonChannel");
		statisticsLogger = LogFactory.getLogger("statistics");
		
		initInterval		= isPrepareEnabled ? 2500 : 6500;
		endInterval			= 600;
		textDelay			= 5000;
		prepSignalDuration	= 500;
		
		String endl = System.getProperty("line.separator");
		if (endl == null) {
			endl = "\n";
		}
		captureDisplayText	= "Please press and hold the button while the other device "
							+ "vibrates, release it, when it doesn't." + endl
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
		if (intervalCount % 2 == 0) {
			intervals.add(endInterval);
		}
		
		// now run the transmission in a separate thread
		Thread t = new Thread(new Runnable() {
			public void run() {
				int signalCount = 0;
				impl.setSignalCount(signalCount);
				impl.setShowCount(showFeedback);
				impl.showTransmitGui(transmitDisplayText, ButtonChannelImpl.TRANSMIT_PLAIN);

				/* 
				 * transmit the data (given from 'intervals')
				 * note: the first interval is always an empty (non-vibrating)
				 * interval ('initInterval') to give the user some time to prepare.
				 * It follows that all vibrating intervals have odd indices in the interval list.
				 */
				if (!isPrepareEnabled) {
					for (int i = 0; i < intervals.size(); i++) {
						int interval = intervals.item(i);
						if (i % 2 == 1) {
							impl.vibrate(interval);
						}
						try {
							Thread.sleep(interval);
							signalCount++;
							impl.setSignalCount(signalCount);
							impl.repaint();
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
							int interval = intervals.item(i);
							if (i % 2 == 1) {
								impl.vibrate(interval);
							}
							interval -= prepSignalDuration;
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
