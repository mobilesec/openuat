/* Copyright Lukas Huser
 * File created 2008-11-18
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.channel.oob.desktop;

import org.openuat.channel.oob.ButtonChannel;
import org.openuat.channel.oob.ButtonInputHandler;
import org.openuat.log.Log;
import org.openuat.util.IntervalList;

/**
 * This class defines a broad interface which allows
 * to implement the different button channels. Each
 * platform that supports button channels should derive
 * and implement a platform specific variant of this class.<br/>
 * If a platform can't implement a specific method (e.g.
 * <code>vibrate</code>), it should do nothing (empty method body)
 * and shouldn't be called by an application. <br/>
 * For all graphical operations (displaying a signal or a
 * progress bar etc.) the usage of this class works as follows:
 * All operations just manipulate the internal state and have no
 * immediate effect on the screen. The changes become visible on screen
 * after the next call to the <code>repaint</code> method.
 * 
 * @author Lukas Huser
 * @version 1.0
 */
public abstract class ButtonChannelImpl {
	
	/**
	 * Transmission mode: plain (simply display text).
	 */
	public static final int TRANSMIT_PLAIN			= 1;
	
	/**
	 * Transmission mode: signal.
	 */
	public static final int TRANSMIT_SIGNAL 		= 2;
	
	/**
	 * Transmission mode: traffic light.
	 */
	public static final int TRANSMIT_TRAFFIC_LIGHT	= 3;
	
	/**
	 * Transmission mode: progress bar.
	 */
	public static final int TRANSMIT_BAR			= 4;
	
	/**
	 * Transmission mode: power bar.
	 */
	public static final int TRANSMIT_VERT_BARS		= 5;
	
	/**
	 * <code>transmissionMode</code> is one of the
	 * <code>TRANSMIT_*</code> constants defined in this class.
	 */
	protected int transmissionMode;
	
	/**
	 * Number of already processed (sent or received) signals/button inputs.
	 */
	protected int signalCount;
	
	/**
	 * Should the <code>signalCount</code> be displayed on screen? It's more
	 * convenient for the user, but use with care: it may leak information to
	 * an attacker (a <i>secure</i> channel should not display this information
	 * on screen).
	 */
	protected boolean showCount;
	
	/**
	 * Should the signal currently be displayed?
	 */
	protected boolean showSignal;
	
	/**
	 * Should a hint to a following signal currently be displayed?
	 */
	protected boolean prepareSignal;
	
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
	 * @param milliseconds The vibration duration.
	 */
	public abstract void vibrate(int milliseconds);
	
	/**
	 * Repaints the currently displayed gui element.
	 */
	public abstract void repaint();
	
	/**
	 * Sets the number of already processed signals.<br/>
	 * When transmitting, it represents the number of already sent
	 * signals. When capturing, it represents the number of
	 * processed button events.<br/>
	 * If <code>signalCount</code> is not within the boundaries
	 * <code>0 <= signalCount <= TOTAL_SIGNAL_COUNT</code> its
	 * value will be automatically truncated to the respective
	 * boundary.
	 * 
	 * @param signalCount The current signal count.
	 */
	public void setSignalCount(int signalCount) {
		if (signalCount < 0) {
			this.signalCount = 0;
		}
		else if (signalCount > ButtonChannel.TOTAL_SIGNAL_COUNT) {
			this.signalCount = ButtonChannel.TOTAL_SIGNAL_COUNT;
		}
		else {
			this.signalCount = signalCount;
		}
	}
	
	/**
	 * Should the <code>signalCount</code> be displayed on screen?
	 * 
	 * @param enabled Enable or disable the signal count on screen.
	 */
	public void setShowCount(boolean enabled) {
		showCount = enabled;
	}
	
	/**
	 * Should the signal currently be displayed?<br/>
	 * This method only modifies the state of the current object
	 * and has no immediate effect. The effect becomes visible
	 * after the next call to <code>repaint</code>.
	 * 
	 * @param enabled Enable or disable the signal.
	 */
	public void setSignal(boolean enabled) {
		showSignal = enabled;
	}
	
	/**
	 * Should a hint to a following signal currently be displayed?<br/>
	 * This method only modifies the state of the current object
	 * and has no immediate effect. The effect becomes visible
	 * after the next call to <code>repaint</code>.<br/>
	 * Note: When in <code>TRANSMIT_SIGNAL</code> mode: 
	 * To enable the preparatory signal, the 'real' signal
	 * must be disabled first (<code>setSignal(false)</code>).
	 * If the signal is active, it always has precedence over the
	 * preparatory signal.
	 * 
	 * @param enabled Enable or disable the preparatory signal.
	 */
	public void setPrepareSignal(boolean enabled) {
		prepareSignal = enabled;
	}
	
	/**
	 * Before transmitting in the modes <code>TRANSMIT_BAR</code>
	 * or <code>TRANSMIT_VERT_BARS</code>, an <code>IntervalList</code> 
	 * must be set.
	 * 
	 * @param list An <code>IntervalList</code>.
	 */
	public void setInterval(IntervalList list) {
		intervalList = list;
	}
	
	/**
	 * Sets the progress of the progress bar in %.
	 * Values which are too small (< 0) or too big
	 * (> 100) are set to the respective boundary.<br/>
	 * This method only modifies the state of the current object
	 * and has no immediate effect. The effect becomes visible
	 * after the next call to <code>repaint</code>.
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
