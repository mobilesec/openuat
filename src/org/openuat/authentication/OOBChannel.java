/* Copyright Rene Mayrhofer
 * File created 2008-06-19
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.authentication;

/* Does this make sense? It's the other way around... */
public interface OOBChannel {
	// with uni-directional communication, can only provide uni-directional human verifiability!
	public boolean isBidirectional();
	
	public boolean isConfidential();
	
	public boolean isAuthentic();
	
	public boolean isUserVerifiable();
	
	public Object getPeer();
	
	public void transmit(Object peer, byte[] message);
	
	public byte[] receive(Object peer);
}
