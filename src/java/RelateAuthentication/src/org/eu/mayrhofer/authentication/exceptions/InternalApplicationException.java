package org.eu.mayrhofer.authentication.exceptions;

public class InternalApplicationException extends Exception {
	public InternalApplicationException(String msg) {
		super(msg);
	}
	public InternalApplicationException(String msg, Throwable t) {
		super(msg, t);
	}
}
