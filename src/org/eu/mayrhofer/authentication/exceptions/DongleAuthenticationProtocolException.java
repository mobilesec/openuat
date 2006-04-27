/* Copyright Rene Mayrhofer
 * File created 2005-09
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.authentication.exceptions;

/**
 * This exception is thrown when the authentication protocol with the Relate dongle fails.
 * 
 * @author Rene Mayrhofer
 * @version 1.0
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
