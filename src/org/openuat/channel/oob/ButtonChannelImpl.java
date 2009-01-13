/* Copyright Lukas Huser
 * File created 2008-11-18
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.channel.oob;

import org.openuat.log.Log;
import org.openuat.util.IntervalList;

/**
 * This class defines a broad interface which allows
 * to implement the different button channels. Each
 * platform that supports button channels should derive
 * and implement a platform specific variant of this class.<br/>
 * If a platform can't implement a specific method (e.g.
 * <code>vibrate</code>), it should do nothing (empty method body)
 * and shouldn't be called by an application.
 * 
 * @author Lukas Huser
 * @version 1.0
 */
public abstract class ButtonChannelImpl {
	
	/**
	 * Transmission mode: plain (simply display text).
	 */
	public static final int TRANSMIT_PLAIN	= 1;
	
	/**
	 * Transmission mode: signal.
	 */
	public static final int TRANSMIT_SIGNAL = 2;
	
	/**
	 * Transmission mode: progress bar.
	 */
	public static final int TRANSMIT_BAR	= 3;
	
	/**
	 * <code>transmissionMode</code> is one of:<br/>
	 * <code>TRANSMIT_PLAIN</code>, <code>TRANSMIT_SIGNAL</code>,
	 * <code>TRANSMIT_BAR</code>
	 */
	protected int transmissionMode;
	
	/**
	 * Should the signal currently be displayed?
	 */
	protected boolean showSignal;
	
	/**
	 * An <code>IntervalList</code> used to draw
	 * the progress bar.
	 */
	protected IntervalList intervalList;
	
	/**
	 * Progress of the progress bar in %.
	 */
	protected float progress;
	
	/**
	 * Logger instance. It will be used by this class and its subclasses as well.
	 */
	protected Log logger;

	/**
	 * Starts the capturing process by launching a gui element that listens
	 * to button inputs. <code>text</code> should be an instructive
	 * and/or informative text that helps the user to perform his task.
	 * 
	 * @param text Is displayed on screen while capturing.
	 * @param inputHandler Button inputs are delegated to <code>inputHandler</code>.
	 */
	public abstract void showCaptureGui(String text, ButtonInputHandler inputHandler);
	
	/**
	 * Starts the transmitting process.
	 * 
	 * @param text Is displayed on screen before transmitting starts.
	 * @param type Defines the transmission type.
	 */
	public abstract void showTransmitGui(String text, int type);
	
	/**
	 * Vibrates for <code>milliseconds</code> ms.<br/>
	 * This method returns immediately and does not
	 * block the caller.
	 * 
	 * @param milliseconds
	 */
	public abstract void vibrate(int milliseconds);
	
	/**
	 * Repaints the currently displayed gui element.
	 */
	public abstract void repaint();
	
	/**
	 * Should the signal currently be displayed?
	 * 
	 * @param show
	 */
	public void setSignal(boolean show) {
		showSignal = show;
	}
	
	/**
	 * Before transmitting with the help of a progress
	 * bar, an <code>IntervalList</code> must be set.
	 * 
	 * @param list 
	 */
	public void setInterval(IntervalList list) {
		intervalList = list;
	}
	
	/**
	 * Sets the progress of the progress bar in %.
	 * Values which are too small (< 0) or too big
	 * (> 100) are set to the respective boundary.
	 * 
	 * @param progress Progress of the progress bar in %.
	 */
	public void setProgress(float progress) {
		if (progress < 0f){
			this.progress = 0f;
		}
		else if (progress > 100f) {
			this.progress = 100f;
		}
		else {
			this.progress = progress;
		}
	}
	
}
