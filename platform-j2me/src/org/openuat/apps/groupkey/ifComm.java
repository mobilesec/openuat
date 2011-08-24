/* Copyright Christoph Egger, Lukas Wallentin
 * File created 2008
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package org.openuat.apps.groupkey;


/** This interface 
 * 
 * @author Christoph Egger, Lukas Wallentin
 * Extends Runnable interface, as implementations MUST run as threads
 */
public interface ifComm extends Runnable {
	/** Calls the handleStringEvent function of the calling thread	
	 * <p>
	 * Triggers sending of a message
	 * @param _folder folder name where the message shall be placed
	 * @param _msg message to send
	 * @param _guest calling application thread
	 */
	public abstract void sendMsg(String _folder, String _msg, ifListener _guest);
	/** Calls the handleStringEvent function. of the calling thread
	 * Triggers the reception of all message from a specific folder.
	 * <p>
	 * implementation of ifComm
	 * 
	 * @param _folder folder name where the desired messages are
	 * @param _guest calling application thread
	 * @see ifComm
	 */
	public abstract void getMsg(String _folder, ifListener _guest);
	/** Calls the handleStringEvent function of the calling thread
	 * Triggers the reception of all messages which were sent starting at a specific time until now from a specific folder. 
	 * <p>
	 * implementation of ifComm
	 * 
	 * @param _folder folder name where the desired messages are
	 * @param _time starting time, oldest wanted message
	 * @param _guest calling application thread
	 * @see ifComm
	 */
	public abstract void getMsgSince(String _folder, int _time, ifListener _guest);
}
