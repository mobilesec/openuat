/* Copyright Rene Mayrhofer
 * File created 2007-06-04
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.util;

import java.io.IOException;

import org.openuat.authentication.AuthenticationProgressHandler;
import org.openuat.authentication.exceptions.InternalApplicationException;

/** This interface represents the minimum requirement for server parts that
 * can listen to incoming authentication requests. It abstracts from the
 * underlying channel.
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public interface HostAuthenticationServer {
	/** Starts the server part. This must be idempotent, i.e. not start a 
	 * second instance if it is already running.
	 */
	public void start() throws IOException;
	
	/** Stops the server part. */
	public void stop() throws InternalApplicationException;
	
	/** Registers an event handler for listening to incoming authentication
	 * protocol events.
	 * @param handler The handler to register.
	 */
	public void addAuthenticationProgressHandler(AuthenticationProgressHandler handler);
	
	/** Removes an event handler from listening to incoming authentication
	 * protocol events.
	 * @param handler The handler to remove.
	 * @return true if the handler was removed, false otherwise (if this 
	 *         handler object was not registered).
	 */
	public boolean removeAuthenticationProgressHandler(AuthenticationProgressHandler handler);
}
