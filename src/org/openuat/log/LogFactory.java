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
 * The public interface of this class provides the two static methods
 * <code>init</code> and <code>getLogger</code>, which conveniently
 * allow to receive a suitable logger for the current system. It is
 * important that <code>init</code> is called <b>before</b> the first
 * call to <code>getLogger</code>, else it will return a default empty
 * logger that just does nothing.<br/>
 * Usage and code example:<br/>
 * Initialize the <code>LogFactory</code> in the applications main class,
 * e.g. in the class inheriting from <code>MIDlet</code> (J2ME) or the class
 * containing the <code>main</code> method (J2SE):
 * <pre>
 * LogFactory.init(new Log4jFactory());
 * </pre> 
 * or
 * <pre>
 * LogFactory.init(new MicrologFactory());
 * </pre>
 * Then you can obtain a <code>Log</code> instance in any other class with:
 * <pre>
 * Log logger = LogFactory.getLogger("my.named.logger");
 * </pre>
 * 
 * @see Log
 * @author Lukas Huser
 * @version 1.0
 */
public abstract class LogFactory {
	
	/* Internal reference to the currently used LogFactory instance. */
	private static LogFactory instance;
	
	/**
	 * Initializes the <code>LogFactory</code>.<br/>
	 * This method should be called only once, at application startup,
	 * and before any calls to <code>getLogger</code>.
	 * 
	 * @param FactoryInstance a new <code>LogFactory</code> instance.
	 * It is <i>always</i> an instance of a subclass of <code>LogFactory</code>,
	 * since <code>LogFactory</code> itself is abstract.
	 */
	public static void init(LogFactory FactoryInstance) {
		instance = FactoryInstance;
	}
	
	/**
	 * Requests a logger from the underlying logging framework and
	 * returns it, wrapped in a <code>Log</code> instance.
	 * 
	 * @param name The name of the requested logger.
	 * @return The requested named logger or the default empty logger if
	 * <code>LogFactory</code> has not been initialized.
	 */
	public static Log getLogger(String name) {
		return instance == null ? new EmptyLogger() : instance.newLogger(name);
	}
	
	/**
	 * Creates a new wrapper around a logger from the underlying logging framework.
	 * 
	 * @param name The name of the new logger.
	 * @return The 
	 */
	protected abstract Log newLogger(String name);
	
	/*
	 * This class is an empty logger, it implements all methods of the
	 * Log interface and leaves them empty. Its main purpose is robustness:
	 * It is only used in the case where LogFactory.init was not called, but
	 * a class which uses the LogFactory interface is used somewhere. The
	 * getLogger method will then return a new EmptyLogger instance instead of null,
	 * such that existing code will not fail with a NullPointerException.
	 */
	private static class EmptyLogger implements Log {
		
		/**
		 * Creates a new empty logger instance.
		 */
		public EmptyLogger() {
			// Do nothing
		}
		
		// @Override
		public void debug(Object message, Throwable t) {
			// Do nothing
		}
		
		// @Override
		public void debug(Object message) {
			// Do nothing
		}

		// @Override
		public void error(Object message, Throwable t) {
			// Do nothing
		}

		// @Override
		public void error(Object message) {
			// Do nothing
		}

		// @Override
		public void fatal(Object message, Throwable t) {
			// Do nothing
		}

		// @Override
		public void fatal(Object message) {
			// Do nothing
		}

		// @Override
		public void info(Object message, Throwable t) {
			// Do nothing
		}

		// @Override
		public void info(Object message) {
			// Do nothing
		}

		// @Override
		public boolean isDebugEnabled() {
			return false;
		}

		// @Override
		public boolean isErrorEnabled() {
			return false;
		}

		// @Override
		public boolean isFatalEnabled() {
			return false;
		}

		// @Override
		public boolean isInfoEnabled() {
			return false;
		}

		// @Override
		public boolean isTraceEnabled() {
			return false;
		}

		// @Override
		public boolean isWarnEnabled() {
			return false;
		}

		// @Override
		public void trace(Object message, Throwable t) {
			// Do nothing
		}

		// @Override
		public void trace(Object message) {
			// Do nothing
		}

		// @Override
		public void warn(Object message, Throwable t) {
			// do nothing
		}

		// @Override
		public void warn(Object message) {
			// Do nothing
		}
	}
	
}
