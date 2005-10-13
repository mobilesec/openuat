package org.eu.mayrhofer.authentication.exceptions;

public class InternalApplicationException extends Exception {
	/**
	 * 
	 */
	private static final long serialVersionUID = -6216935455895338914L;
	public InternalApplicationException(String msg) {
		super(msg);
	}
	public InternalApplicationException(String msg, Throwable t) {
		super(msg, t);
	}
}
