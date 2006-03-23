/* Copyright Rene Mayrhofer
 * File created 2005-09
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.authentication.exceptions;

/**
 * This exception is thrown when the key agreement protocol is not followed, 
 * e.g. by calling methods in the wrong order or passing invalid arguments.
 * 
 * @author Rene Mayrhofer
 * @version 1.0
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
