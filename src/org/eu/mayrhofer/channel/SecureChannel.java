package org.eu.mayrhofer.channel;

/** This interface represents a secure channel to another (usually remote) device. 
 * Specific implementations take care of constructing, terminating, and querying the
 * secure channel.
 * 
 * All implementations should take care to clean up connections if they have not been
 * set to be persistent.
 * 
 * @author Rene Mayrhofer
 *
 */
public interface SecureChannel {
	/** Establishes and starts a new secure channel to another host.
	 * 
	 * @param remoteHost The remote host to establish the channel to. This string is 
	 *                   dependent on the implementation of the secure channel.
	 * @param sharedSecret The shared secret to use when establishing the channel.
	 * @param persistent If set to true, the secure channel will persist accross 
	 *                   application restarts (and might persist across reboots).
	 *                   This is dependent on the implementation, only some might
	 *                   support persistance.
	 * @return true if the channel could be started, false otherwise.
	 */ 
	public boolean start(String remoteHost, byte[] sharedSecret, boolean persistent);
	
	/** Stop a previously established secure channel. If that channel was made
	 * persistant, then it will be deleted completely.
	 * @return true if the channel could be stopped, false otherwise.
	 */
	public boolean stop();
	
	/** Returns true if the channel has been established. */ 
	public boolean isEstablished();
}
