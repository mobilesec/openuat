package org.openuat.util;

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

