package org.eu.mayrhofer.authentication.exceptions;

/**
 * This exception is thrown when the key agreement protocol is not followed, e.g. by calling methods in the wrong order or passing invalid arguments.
 */
public class KeyAgreementProtocolException extends Exception {
	private static final long serialVersionUID = 457589531544533482L;
	public KeyAgreementProtocolException(String msg) {
		super(msg);
	}
	public KeyAgreementProtocolException(String msg, Throwable t) {
		super(msg, t);
	}
}
