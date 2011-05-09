/* Copyright Rene Mayrhofer
 * File created 2007-08-09
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.channel.main;

/** This interface describes custom protocol command handlers that react
 * to specific commands with which they are registered.
 */
public interface ProtocolCommandHandler {
	/** Delegates handling of a sub-protocol.
	 * 
	 * @param firstLine The first line of the sub-protocol. This by 
	 *                  definition starts with the command (word) for 
	 *                  which this protocol handler has been registered.
	 * @param remote The channel to use for communication. 
	 * @return true if the sub-protocol finished successfully, false 
	 *         otherwise.
	 */
	boolean handleProtocol(String firstLine, RemoteConnection remote);
}
