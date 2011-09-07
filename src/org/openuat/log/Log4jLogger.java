/* Copyright Lukas Huser
 * File created 2009-01-12
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.log;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.Priority;

/**
 * This class is a thin wrapper around a logger. Configuration of the
 * log4j framework should be done through its properties file (log4j.properties).
 * However, you can get access to the native logger through the
 * {@link #getNativeLogger} method.
 * 
 * @see Log
 * @author Lukas Huser
 * @version 1.0
 */
public class Log4jLogger implements Log {
	
	/* The internal logger */
	private Logger logger;
	
	/**
	 * Creates a new instance which will wrap a new named logger.
	 * 
	 * @param name The name of the new logger.
	 */
	public Log4jLogger(String name) {
		this.logger = Logger.getLogger(name);
	}
	
	/**
	 * Creates a new instance which wraps an existing logger.
	 * 
	 * @param logger The wrapped logger.
	 */
	public Log4jLogger(Logger logger) {
		this.logger = logger;
	}
	
	/**
	 * Returns the native logger.
	 * 
	 * @return The native logger.
	 */
	public Logger getNativeLogger() {
		return logger;
	}
	
	// @Override
	public void debug(Object message) {
		logger.debug(message);
	}

	// @Override
	public void debug(Object message, Throwable t) {
		logger.debug(message, t);
	}

	// @Override
	public void error(Object message) {
		logger.error(message);
	}

	// @Override
	public void error(Object message, Throwable t) {
		logger.error(message, t);
	}

	// @Override
	public void fatal(Object message) {
		logger.fatal(message);
	}

	// @Override
	public void fatal(Object message, Throwable t) {
		logger.fatal(message, t);
	}

	// @Override
	public void info(Object message) {
		logger.info(message);
	}

	// @Override
	public void info(Object message, Throwable t) {
		logger.info(message, t);
	}

	// @Override
	public boolean isDebugEnabled() {
		return logger.isDebugEnabled();
	}

	// @Override
	public boolean isErrorEnabled() {
		return logger.isEnabledFor(Priority.ERROR);
	}

	// @Override
	public boolean isFatalEnabled() {
		return logger.isEnabledFor(Level.FATAL);
	}

	// @Override
	public boolean isInfoEnabled() {
		return logger.isInfoEnabled();
	}

	// @Override
	public boolean isTraceEnabled() {
		return logger.isTraceEnabled();
	}

	// @Override
	public boolean isWarnEnabled() {
		return logger.isEnabledFor(Level.WARN);
	}

	// @Override
	public void trace(Object message) {
		logger.trace(message);
	}

	// @Override
	public void trace(Object message, Throwable t) {
		logger.trace(message, t);
	}

	// @Override
	public void warn(Object message) {
		logger.warn(message);
	}

	// @Override
	public void warn(Object message, Throwable t) {
		logger.warn(message, t);
	}

	@Override
	public void finest(Object message) {
		logger.trace(message);
	}

	@Override
	public void fine(Object message) {
		logger.debug(message);
	}

	@Override
	public void warning(Object message) {
		logger.warn(message);
	}

	@Override
	public void warning(Object message, Throwable t) {
		logger.warn(message, t);
	}

	@Override
	public void severe(Object message) {
		logger.error(message);
	}

	@Override
	public void severe(Object message, Throwable t) {
		logger.error(message, t);
	}
}
