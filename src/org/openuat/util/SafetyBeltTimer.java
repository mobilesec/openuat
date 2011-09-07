/* Copyright Rene Mayrhofer
 * File created 2006-01
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.util;

import java.io.IOException;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** This class implements a "grenade timer" that will let any loop bail out 
 * with a timeout when it is stuck for too long waiting for something. To use
 * it, simply construct a SafetyBeltTimer object with the number of 
 * milliseconds the next operation should take at maximum. The timer is 
 * automatically started on construction and runs in a separate thread. If 
 * there are multiple steps or rounds in some operation that are expected to
 * take this time each, then reset() can be used to set the timer back to zero
 * while still leaving it running. isTriggered() can be used to query if the
 * timer has already expired and e.g. an outer loop should exit gracefully.
 * <br>
 * There is no need to explicitly stop the thread, it will just time out and 
 * stop. Sample code for this simple use case:
 * <br>
 * <pre>
 * {
 * 		SafetyBeltTimer timer = new SafetyBeltTimer(timeoutMs, null);
 * 		while (something && ! timer.isTriggered()) {
 * 			// ... whatever might take long
 * 			if (made some progress) timer.reset();
 * 			// ... maybe some more blocking code
 * 		}
 * }
 * </pre>
 * Additionally, an InputStream can be passed to the timer on construction. 
 * When set to a valid object, its close() method will be called when the 
 * timer expires. This allows to exit even from a blocking read() that may be
 * active while the timeout occurs. Sample code to use it that way: 
 * <br>
 * <pre>
 * {
 * 		SafetyBeltTimer timer = new SafetyBeltTimer(timeoutMs, channelFromRemote);
 * 		try {
 * 			// ... whatever might take long, reading from channelFromRemote
 * 			// in this case explicitly stop the timer at the end so that it won't close channelFromRemote
 * 			timer.stop();
 * 		}
 * 		catch (IOException e) {
 * 			// there might have been a timeout
 * 		}
 * 		finally {
 * 			timer.stop();
 * 		}
 * }
 * </pre>
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class SafetyBeltTimer implements Runnable {
	/** Our logger. */
	private static Logger logger = LoggerFactory.getLogger("org.openuat.util.SafetyBeltTimer" /*SafetyBeltTimer.class*/);

	/** This signals the event loop to exit gracefully. */
	private boolean gracefulStop = false;
	/** This signals the event loop to exit on its next possibility. */ 
	private boolean timeout = false;
	/** The time, in milliseconds, that this timer will use. */
	private int msCountdown;
	/** This is only the thread object used to execute run() in the background.
	 * It is created and immediately started in the constructor, and stops itself.
	 */
	private Thread thread;
	/** If set, then this stream will be forcefully closed when the timer is
	 * triggered. This can be used for enforcing timeouts on blocking reads.
	 */ 
	private InputStream abortStream;
	
	/** 
	 * @param time The time, in milliseconds, that this timer will use. 
	 * @param abortStream If set, then this stream will be forcefully closed 
	 *                    when the timer is triggered. This can be used for 
	 *                    enforcing timeouts on blocking reads. Set to null to
	 *                    disable this functionality.
	 */
	public SafetyBeltTimer(int time, InputStream abortStream) {
		msCountdown = time;
		this.abortStream = abortStream;
		thread = new Thread(this);
		thread.start();
	}
	
	/** Implements the timer background thread. */
	public void run() {
		logger.debug("Starting safety belt timer with " + msCountdown + "ms");
		
		while (!timeout && !gracefulStop) {
			try {
				Thread.sleep(msCountdown);
				// finished the sleep, so time out now
				timeout = true;
			}
			catch (InterruptedException e) {
				// ok. this is a heartbeat, just restart the timer waiting
				// timeout will not have been set to true in that case
				logger.debug("Safety belt timer reset to " + msCountdown + "ms");
			}
		}
		/* Need to check gracefulStop too - it seems that J2ME implementations
		 * don't necessarily support thread interruption and thus timeout might
		 * be set to true even if stop() was called!. */
		if (timeout && !gracefulStop) {
			if (logger.isDebugEnabled())
				logger.debug("Safety belt timer triggered");
			if (abortStream != null) {
				logger.warn("Forcefully closing input stream to abort reads: " + abortStream);
				try {
					abortStream.close();
				} catch (IOException e) {
					logger.error("Could not forcefully close input stream: " + e);
				}
			}
		}
		else
			if (logger.isDebugEnabled())
				logger.debug("Safety belt timer exited gracefully");
	}
	
	/** Returns true when the timer has triggered and the task should terminate. */
	public boolean isTriggered() {
		if (timeout && logger.isInfoEnabled())
			logger.info("Triggered safety belt timer just got queried - task should bail out now");
		// no synchronization method here since it's only a boolean
		return timeout;
	}
	
	/** Allows to send a "heartbeat" signal to the timer by resetting it. 
	 * This allows to implement heartbeat like functionality where the timer 
	 * can get reset whenever some progress is being made.
	 */
	public void reset() {
		// but "kill" the current timer run and reset
		thread.interrupt();
	}

	/** This stops the safety belt timer gracefully without triggering it. */
	public void stop() {
		gracefulStop = true;
		reset();
	}
}
