/* Copyright Lukas Huser
 * File created 2009-01-12
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.log;

/**
 * This interface provides a common abstraction of a 'Logger' object. Classes
 * implementing this interface typically are thin wrappers around existing logging
 * frameworks (as log4j or microlog). By programming to this interface, a client
 * class is decoupled from a specific logging framework and, since most frameworks
 * are platform dependent (J2SE vs. J2ME), as well from a specific platform.<br/>
 * Note: an underlying logging framework is best configured in its respective
 * configuration file (e.g. log4j.properties, microlog.properties).<br/>
 * This interface provides the following log levels (from low to high severity):<br/>
 * trace, debug, info, warn, error, fatal.<br/>
 * For a usage example see {@link LogFactory}.
 * 
 * @author Lukas Huser
 * @version 1.0
 */
public interface Log {

	/**
	 * Logs a message at log level trace.
	 * 
	 * @param message The message to log.
	 */
	public void trace(Object message);
	public void finest(Object message);
	
	/**
	 * Logs a message at log level trace.
	 * 
	 * @param message The message to log.
	 * @param t The cause of the error.
	 */
	public void trace(Object message, Throwable t);
	
	/**
	 * Logs a message at log level debug.
	 * 
	 * @param message The message to log.
	 */
	public void debug(Object message);
	public void fine(Object message);
	
	/**
	 * Logs a message at log level debug.
	 * 
	 * @param message The message to log.
	 * @param t The cause of the error.
	 */
	public void debug(Object message, Throwable t);
	
	/**
	 * Logs a message at log level info.
	 * 
	 * @param message The message to log.
	 */
	public void info(Object message);	
	
	/**
	 * Logs a message at log level info.
	 * 
	 * @param message The message to log.
	 * @param t The cause of the error.
	 */
	public void info(Object message, Throwable t);
	
	/**
	 * Logs a message at log level warn.
	 * 
	 * @param message The message to log.
	 */
	public void warn(Object message);
	public void warning(Object message);
	
	/**
	 * Logs a message at log level warn.
	 * 
	 * @param message The message to log.
	 * @param t The cause of the error.
	 */
	public void warn(Object message, Throwable t);
	public void warning(Object message, Throwable t);

	/**
	 * Logs a message at log level error.
	 * 
	 * @param message The message to log.
	 */
	public void error(Object message);
	public void severe(Object message);
	
	/**
	 * Logs a message at log level error.
	 * 
	 * @param message The message to log.
	 * @param t The cause of the error.
	 */
	public void error(Object message, Throwable t);

	/**
	 * Logs a message at log level fatal.
	 * 
	 * @param message The message to log.
	 */
	public void fatal(Object message);
	
	/**
	 * Logs a message at log level fatal.
	 * 
	 * @param message The message to log.
	 * @param t The cause of the error.
	 */
	public void fatal(Object message, Throwable t);
	
	/**
	 * Is log level trace enabled?
	 * 
	 * @return <code>true</code> if log level trace is enabled.
	 */
	public boolean isTraceEnabled();
	
	/**
	 * Is log level debug enabled?
	 * 
	 * @return <code>true</code> if log level debug is enabled.
	 */
	public boolean isDebugEnabled();
	
	/**
	 * Is log level info enabled?
	 * 
	 * @return <code>true</code> if log level info is enabled.
	 */
	public boolean isInfoEnabled();
	
	/**
	 * Is log level warn enabled?
	 * 
	 * @return <code>true</code> if log level warn is enabled.
	 */
	public boolean isWarnEnabled();

	/**
	 * Is log level error enabled?
	 * 
	 * @return <code>true</code> if log level error is enabled.
	 */
	public boolean isErrorEnabled();

	/**
	 * Is log level fatal enabled?
	 * 
	 * @return <code>true</code> if log level fatal is enabled.
	 */
	public boolean isFatalEnabled();

}
