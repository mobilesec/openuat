/* Copyright Lukas Huser
 * File created 2008-10-20
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.channel.oob;

/**
 * This interface defines methods to react to button inputs
 * independent of the underlying implementation.<br/>
 * At this level of abstraction it is just one button that is
 * needed (<b>the</b> Button). Which button this is (or even
 * several buttons) depends on the given device and should be
 * decided in the corresponding platform dependent class.
 * 
 * @author Lukas Huser
 * @version 1.0
 */
public interface ButtonInputHandler {

	/**
	 * Handles button press events.
	 */
	public void buttonPressed();
	
	/**
	 * Handles button release events.
	 */
	public void buttonReleased();
}
