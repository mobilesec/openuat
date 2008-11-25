/* Copyright Lukas Huser
 * File created 2008-11-18
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.channel.oob;





/**
 * This is an AWT specific implementation of
 * the {@link ButtonChannelImpl} class.
 * 
 * @author Lukas Huser
 * @version 1.0
 */
public class AWTButtonChannelImpl extends ButtonChannelImpl {

	/**
	 * Creates a new instance.
	 */
	public AWTButtonChannelImpl() {
		// TODO: initialize
	}

	/* (non-Javadoc)
	 * @see org.openuat.channel.oob.ButtonChannelImpl#repaint()
	 */
	@Override
	public void repaint() {
		// TODO Auto-generated method stub
		
	}

	/* (non-Javadoc)
	 * @see org.openuat.channel.oob.ButtonChannelImpl#showCaptureGui(java.lang.String, org.openuat.channel.oob.ButtonInputHandler)
	 */
	@Override
	public void showCaptureGui(String text, ButtonInputHandler inputHandler) {
		// TODO Auto-generated method stub
		
	}

	/* (non-Javadoc)
	 * @see org.openuat.channel.oob.ButtonChannelImpl#showTransmitGui(java.lang.String, int)
	 */
	@Override
	public void showTransmitGui(String text, int type) {
		// TODO Auto-generated method stub
		
	}

	/* (non-Javadoc)
	 * @see org.openuat.channel.oob.ButtonChannelImpl#vibrate(int)
	 */
	@Override
	public void vibrate(int milliseconds) {
		// can't be implemented on this platform
		// TODO Logger.warn("method vibrate is not implemented on AWT");
		
	}
	


}
