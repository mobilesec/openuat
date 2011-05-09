/* Copyright Christoph Egger,Lukas Wallentin
 * File created 2008-11-12
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.apps.groupkey;

import java.util.Hashtable;
/**
 * 
 * @author Christoph Egger, Lukas Wallentin
 * Extends Runnable interface, as implementations MUST run as threads
 */
public interface ifPair extends ifListener {
	/**
	 * Returns Hashtable with keys
	 */
	public Hashtable listKeys();

	/**
	 * sets The name of the current device, must be done at the beginning
	 * @param _name Name of the current device
	 */
	public void setName( String _name);
	
	/**
	 * Returns a single key or null if there is no key with that pair
	 * @param _bob Name of the pair device
	 */
	public abstract byte[] getKey(String _bob);
	
	/**
	 * Set Key function
	 * @param _remote Name of the pair device
	 * @param _key shared key between the current and the pair device
	 */
	public abstract void setKeyPair(String _remote, byte[] _key);
	
	/**
	 * returns a string with debugging information
	 * @return String with debugging infomation
	 */
	public String getLog();	
	
	/**
	 * resets the Log
	 */
	public void resetLog();		
}

