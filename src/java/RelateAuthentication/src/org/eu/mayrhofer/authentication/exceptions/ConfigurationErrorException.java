package org.eu.mayrhofer.authentication.exceptions;

/**
 * This exception is thrown whenever a configuration error has been detected, e.g .when specified hardware or software components are not available or configuration values are contradictory. 
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
