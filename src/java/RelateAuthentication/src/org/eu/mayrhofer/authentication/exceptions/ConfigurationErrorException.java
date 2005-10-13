package org.eu.mayrhofer.authentication.exceptions;

public class ConfigurationErrorException extends Exception {
	/**
	 * 
	 */
	private static final long serialVersionUID = -4058896188595367369L;
	public ConfigurationErrorException(String msg) {
		super(msg);
	}
	public ConfigurationErrorException(String msg, Throwable t) {
		super(msg, t);
	}
}
