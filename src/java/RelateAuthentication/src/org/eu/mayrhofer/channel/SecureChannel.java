/* Copyright Rene Mayrhofer
 * File created 2006-02
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.channel;

/** This interface represents a secure channel to another (usually remote) device. 
 * Specific implementations take care of constructing, terminating, and querying the
 * secure channel.
 * 
 * All implementations should take care to clean up connections if they have not been
 * set to be persistent.
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public interface SecureChannel {
	/** Initializes an instance of a secure channel. The minimal initialization is
	 * to remember the remote host description for future calls to start() and stop(),
	 * but the implementation might do additional tasks when necessary. 
	 * 
	 * <b>This method must be called before any of the others.</b>
	 *
	 * @param remoteHost The remote host to establish the channel to. This string is 
	 *                   dependent on the implementation of the secure channel.
	 * @param useAsDefault If set to true, this channel will be used as default for all
	 *                     further communication. It might not be supported by all
	 *                     implementations, set to false if in doubt.
	 * @return true if the channel could be initialized, false otherwise. It will return
	 *         false if the channel has already been initialized previously.
	 */
	public boolean init(String remoteHost, boolean useAsDefault);
	
	/** Establishes and starts a new secure channel to another host. 
	 * init() needs to be called once before this method.
	 * @see #init
	 * 
	 * @param sharedSecret The shared secret to use when establishing the channel.
	 * @param persistent If set to true, the secure channel will persist accross 
	 *                   application restarts (and might persist across reboots).
	 *                   This is dependent on the implementation, only some might
	 *                   support persistance.
	 * @return true if the channel could be started, false otherwise.
	 */ 
	public boolean start(byte[] sharedSecret, boolean persistent);
	
	/** Stop a previously established secure channel. If that channel was made
	 * persistant, then it will be deleted completely.
	 * init() needs to be called once before this method.
	 * @see #init
	 * @return true if the channel could be stopped, false otherwise.
	 */
	public boolean stop();
	
	/** Returns true if the channel has been established. 
	 * init() needs to be called once before this method.
	 * @see #init
	 */
	public boolean isEstablished();
}
