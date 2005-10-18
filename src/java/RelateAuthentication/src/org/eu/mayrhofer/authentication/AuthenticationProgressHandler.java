package org.eu.mayrhofer.authentication;

import java.net.*;

/**
 * This interface defines a listener for authentication events. 
 * @author Rene Mayrhofer
 */
public interface AuthenticationProgressHandler {
	/**
	 * Upon successful authentication, the established shared key can be used
	 * with the remote host.
	 */
	public void AuthenticationSuccess(InetAddress remoteHost, 
			byte[] sharedSessionKey, byte[] sharedAuthenticationKey);

	/**
	 Upon authentication failure, an exception might have been thrown and a
	 message might have been created.
	 @param e Reason for the failue, can be null.
	 @param msg Reaseon for the failue, can be null */
	public void AuthenticationFailure(InetAddress remote, Exception e,
			String msg);

	public void AuthenticationProgress(InetAddress remote, int cur, int max,
			String msg);
}
