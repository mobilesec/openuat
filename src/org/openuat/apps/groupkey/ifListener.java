/* Copyright Christoph Egger, Martijn Sack, Lukas Wallentin, Andreas Weiner
 * File created 2008-11-12
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.apps.groupkey;
/**
 * 
 * @author Christoph Egger, Martijn Sack, Lukas Wallentin, Andreas Weiner, 
 * Extends Runnable interface, as implementations MUST run as threads
 */
public interface ifListener extends Runnable{
	/**
	 * Wakes up the sleeping Thread as it got a String-message
	 * @param _data received data
	 * @param _success tells if there was success or not
	 */
	public abstract void handleStringEvent(String _data, boolean _success);	
}
