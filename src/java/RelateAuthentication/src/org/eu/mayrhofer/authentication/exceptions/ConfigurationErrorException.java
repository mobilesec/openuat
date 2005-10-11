package org.eu.mayrhofer.authentication.exceptions;

public class ConfigurationErrorException extends Exception {
	public ConfigurationErrorException(String msg) {
		super(msg);
	}
	public ConfigurationErrorException(String msg, Throwable t) {
		super(msg, t);
	}
}
