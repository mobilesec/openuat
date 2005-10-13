package org.eu.mayrhofer.authentication.exceptions;

public class KeyAgreementProtocolException extends Exception {
	/**
	 * 
	 */
	private static final long serialVersionUID = 457589531544533482L;
	public KeyAgreementProtocolException(String msg) {
		super(msg);
	}
	public KeyAgreementProtocolException(String msg, Throwable t) {
		super(msg, t);
	}
}
