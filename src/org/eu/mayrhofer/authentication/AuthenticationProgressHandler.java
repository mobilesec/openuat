package org.eu.mayrhofer.authentication;

/**
 * This interface defines a listener for authentication events. 
 * @author Rene Mayrhofer
 */
public interface AuthenticationProgressHandler {
	/**
	 * Upon successful authentication, the established shared key can be used
	 * with the remote host. The type of the remoteHost object depends on the sender
	 * of the event, e.g. an InetAddress object for HostProtocolHandler generated
	 * events, but an Integer for DongleProtocolHandler generated events (encapsulating
	 * the remote relate id).
	 * 
	 * @param remoteHost The remote end with which the authentication is performed. 
	 * Depends on the sender of the event.
	 * 
	 * @param result The result, if any, of the successful authentication. This can
	 * e.g. be a shared key or a set of keys or can even be null if the authentication
	 * event is enough to signal successful authentication.
	 */
	public void AuthenticationSuccess(Object remoteHost, Object result);

	/**
	 Upon authentication failure, an exception might have been thrown and a
	 message might have been created.
	 @param e Reason for the failue, can be null.
	 @param msg Reaseon for the failue, can be null */
	public void AuthenticationFailure(Object remote, Exception e,
			String msg);

	/** This event is raised during the authentication protocol to indicate progress.
	 * 
	 * @param remote The remote end with which the authentication is performed.
	 * @param cur The current stage in the authentication.
	 * @param max The maximum number of stages.
	 * @param msg If not null, a message describing the last successful stage.
	 */
	public void AuthenticationProgress(Object remote, int cur, int max,
			String msg);
}
