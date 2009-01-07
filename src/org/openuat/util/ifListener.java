package org.openuat.util;
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
