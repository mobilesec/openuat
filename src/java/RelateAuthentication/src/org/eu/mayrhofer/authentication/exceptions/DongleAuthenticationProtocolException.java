package org.eu.mayrhofer.authentication.exceptions;

/**
 * This exception is thrown when the authentication protocol with the Relate dongle fails.
 */
public class DongleAuthenticationProtocolException extends Exception {
	private static final long serialVersionUID = 3037834887141069771L;

	public DongleAuthenticationProtocolException(String msg) {
		super(msg);
	}

	public DongleAuthenticationProtocolException(String msg, Throwable t) {
		super(msg, t);
	}
}
