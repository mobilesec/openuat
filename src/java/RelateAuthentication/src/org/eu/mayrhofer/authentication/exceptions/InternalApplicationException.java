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
 * This exception is thrown when internal conditions do not hold or unexpected errors occur.
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class InternalApplicationException extends Exception {
	private static final long serialVersionUID = -6216935455895338914L;
	public InternalApplicationException(String msg) {
		super(msg);
	}
	public InternalApplicationException(String msg, Throwable t) {
		super(msg, t);
	}
}
