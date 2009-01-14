/* Copyright Lukas Huser
 * File created 2009-01-14
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.log.j2me;


import org.openbandy.log.LogImpl;
import org.openbandy.log.LogLevel;
import org.openbandy.service.LogService;
import org.openuat.log.Log;

/**
 * This class is a thin wrapper around the openbandy <code>LogService</code>.
 * You can get access to the native <code>logImpl</code> instance through the
 * {@link #getNativeLogger()} method.
 * 
 * @see Log
 * @author Lukas Huser
 * @version 1.0
 */
public class BandyLogger implements Log {
	
	/* The name of the logger. It's the originating class */
	private String name;
	/**
	 * Creates a new instance which will wrap bandys <code>LogService</code>.
	 * 
	 * @param name The name of the logger. 
	 */
	public BandyLogger(String name) {
		this.name = name.substring(name.lastIndexOf('.') + 1);
	}
	
	/**
	 * Returns the native bandy <code>LogImpl</code> instance.
	 * 
	 * @return The native bandy <code>LogImpl</code> instance.
	 */
	public LogImpl getNativeLogger() {
		return LogService.getLog();
	}
	
	// @Override
	public void debug(Object message) {
		LogService.debug(name, message.toString());
	}

	// @Override
	public void debug(Object message, Throwable t) {
		LogService.debug(name, message.toString() + "  " +  t.toString());
	}

	// @Override
	public void error(Object message) {
		LogService.error(name, message.toString(), null);
	}

	// @Override
	public void error(Object message, Throwable t) {
		LogService.error(name, message.toString(), t);
	}

	// @Override
	public void fatal(Object message) {
		// map fatal to error
		this.error(message);
	}

	// @Override
	public void fatal(Object message, Throwable t) {
		// map fatal to error
		this.error(message, t);
	}

	// @Override
	public void info(Object message) {
		LogService.info(name, message.toString());
	}

	// @Override
	public void info(Object message, Throwable t) {
		LogService.info(name, message.toString() + "  " +  t.toString());
	}

	// @Override
	public boolean isDebugEnabled() {
		return LogService.getLog().config.getLogLevel() <= LogLevel.DEBUG;
	}

	// @Override
	public boolean isErrorEnabled() {
		return LogService.getLog().config.getLogLevel() <= LogLevel.ERROR;
	}

	// @Override
	public boolean isFatalEnabled() {
		// map fatal to error
		return this.isErrorEnabled();
	}

	// @Override
	public boolean isInfoEnabled() {
		return LogService.getLog().config.getLogLevel() <= LogLevel.INFO;
	}

	// @Override
	public boolean isTraceEnabled() {
		// map trace to debug
		return this.isDebugEnabled();
	}

	// @Override
	public boolean isWarnEnabled() {
		return LogService.getLog().config.getLogLevel() <= LogLevel.WARNING;
	}

	// @Override
	public void trace(Object message) {
		// map trace to debug
		this.debug(message);
	}

	// @Override
	public void trace(Object message, Throwable t) {
		// map trace to debug
		this.debug(message, t);
	}

	// @Override
	public void warn(Object message) {
		LogService.warn(name, message.toString());
	}

	// @Override
	public void warn(Object message, Throwable t) {
		LogService.warn(name, message.toString() + "  " + t.toString());
	}
}
