/* Copyright Lukas Huser
 * File created 2008-11-27
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.channel.oob;

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
		useCarry		= true;
		messageHandler	= null;
		
		int eventCount = (MESSAGE_LENGTH / BITS_PER_INTERVAL) + 1;
		captureDisplayText	= "Please press the button simultanously on both devices "
							+ "for a total of " + Integer.toString(eventCount) + " times.\n"
							+ "This device is ready.";
	}

	/* (non-Javadoc)
	 * @see org.openuat.authentication.OOBChannel#transmit(byte[])
	 */
	@Override
	public void transmit(byte[] message) {
		// This method is not implemented!
		// TODO: log warning
		// logger.warn("Method transmit(byte[]): Not implemented for the ButtonToButtonChannel.");
	}

}
