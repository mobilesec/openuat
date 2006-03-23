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
 * This exception is thrown whenever a configuration error has been detected, e.g .when 
 * specified hardware or software components are not available or configuration values 
 * are contradictory.
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class ConfigurationErrorException extends Exception {
	private static final long serialVersionUID = -4058896188595367369L;
	public ConfigurationErrorException(String msg) {
		super(msg);
	}
	public ConfigurationErrorException(String msg, Throwable t) {
		super(msg, t);
	}
}
