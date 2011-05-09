/* Copyright Rene Mayrhofer
 * File created 2006-05-09
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.channel.main;

/** This is a very simple interface to listen to incoming messages
 * for connectionless protocols, e.g. UDP.
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public interface MessageListener {
	/** This message is called for each incoming message.
	 * <b>Note:</b> Each implementation must be thread-safe, because this
	 * method can be called simultaneously from different threads. 
	 * 
	 * @param message The message itself. Depending on the protocol,
	 *                it might be out-of-order and packet delivery might
	 *                not be guaranteed at all.
	 * @param offset The offset at which the message starts in the message array.
	 * @param length The number of bytes in message that belong to the
	 *               message itself.
	 * @param sender The sender of the message, e.g. an InetAddress or
	 *               Inet6Address object for UDP packets.
	 */
	public void handleMessage(byte[] message, int offset, int length, Object sender);
}
