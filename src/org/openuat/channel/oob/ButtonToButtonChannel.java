/* Copyright Lukas Huser
 * File created 2008-11-27
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.channel.oob;

import org.openuat.log.LogFactory;

/**
 * This channel is an <b>input channel</b>, it only implements the
 * <code>capture</code> method and doesn't support the <code>transmit</code>
 * method (it just does nothing).<br/>
 * This channel only records button presses and ignores button releases.<br/>
 * The smallest considered time unit is set to 300 milliseconds
 * (See C. Soriente, G. Tsudik: 'BEDA: Button-Enabled Device Association' for more
 * details).
 * 
 * @author Lukas Huser
 * @version 1.0
 */
public class ButtonToButtonChannel extends ButtonChannel {
	
	/**
	 * Creates a new instance of this channel.
	 * 
	 * @param impl A suitable <code>ButtonChannelImpl</code> instance
	 * to handle platform dependent method calls.
	 */
	public ButtonToButtonChannel(ButtonChannelImpl impl) {
		this.impl		= impl;
		minTimeUnit		= 300;
		inputMode		= MODE_PRESS;
		doRoundDown		= false;
		useCarry		= false;
		showFeedback	= false;
		messageHandler	= null;
		shortDescription	= "Input";
		transmitDisplayText = "";
		logger = LogFactory.getLogger(this.getClass().getName());
		
		String endl = System.getProperty("line.separator");
		if (endl == null) {
			endl = "\n";
		}
		captureDisplayText	= "Please press the button simultanously on both devices"
							+ " for a total of " + ButtonChannel.TOTAL_SIGNAL_COUNT 
							+ " times." + endl
							+ "This device is ready.";
		
	}

	/**
	 * <b>Note:</b> This method is not implemented for this channel. Don't call it
	 * from an application.
	 * @param message The data to be sent over this channel.
	 */
	// @Override
	public void transmit(byte[] message) {
		// This method is not implemented!
		logger.warn("Method transmit(byte[]): Not implemented for the ButtonToButtonChannel.");
	}

}
