/* Copyright Rene Mayrhofer
 * File created 2006-01
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.authentication;

import org.apache.log4j.Logger;

/** This is a currently safety belt against the dongle being stuck in authentication 
 * mode or the other dongle never entering authentication mode. I.e., it is a safeguard 
 * against that specific loop ending up being an endless loop (which is not good in terms 
 * of fault tolerance). This helper class implements a "grenade timer" that will let the main
 * event loop bail out with a timeout when it is stuck for too long at the same round 
 * number. It is also useful for other purposes, even if it has been created specifically
 * for DongleProtocolHandler#handleDongleCommunication for safeguarding its main loop. 
 * 
 * There is no need to explicitly stop the thread, it will just time out and stop.
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class SafetyBeltTimer implements Runnable {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(SafetyBeltTimer.class);

	/** This signals the event loop to exit on its next possibility. */ 
	private boolean timeout = false;
	/** The time, in milliseconds, that this timer will use. */
	private int msCountdown;
	/** This is only the thread object used to execute run() in the background.
	 * It is created and immediately started in the constructor, and stops itself.
	 */
	private Thread thread;
	
	/** @param time The time, in milliseconds, that this timer will use. */
	public SafetyBeltTimer(int time) {
		msCountdown = time;
		thread = new Thread(this);
		thread.start();
	}
	
	/** Implements the timer background thread. */
	public void run() {
		logger.debug("Starting safety belt timer with " + msCountdown + "ms");
		
		while (!timeout) {
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
		
		logger.debug("Safety belt timer triggered");
	}
	
	/** Returns true when the timer has triggered and the task should terminate. */
	public boolean isTriggered() {
		if (timeout)
			logger.info("Triggered safety belt timer just got queried - task should bail out now");
		// no synchronization method here since it's only a boolean
		return timeout;
	}
	
	/** Allows to send a hearbeat signal to the timer by resetting it. 
	 * This allows
	 * to implement heartbeat like functionality where the timer can get reset
	 * whenever some progress is being made.
	 */
	public void reset() {
		// but "kill" the current timer run and reset
		thread.interrupt();
	}
}
