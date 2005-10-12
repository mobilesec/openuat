package org.eu.mayrhofer.authentication;

import java.net.*;

public interface AuthenticationProgressHandler {
    // / <summary>
    // / Upon successful authentication, the established shared key can be used
	// with the remote host.
    // / </summary>
    // / <param name="remote"></param>
    // / <param name="sharedKey"></param>
    public void AuthenticationSuccess(InetAddress remote, byte[] sharedKey);

    // / <summary>
    // / Upon authentication failure, an exception might have been thrown and a
	// message might have been created.
    // / </summary>
    // / <param name="remote"></param>
    // / <param name="e">Can be null</param>
    // / <param name="msg">Can be null</param>
    public void AuthenticationFailure(InetAddress remote, Exception e, String msg);

    public void AuthenticationProgress(InetAddress remote, int cur, int max, String msg);
}
