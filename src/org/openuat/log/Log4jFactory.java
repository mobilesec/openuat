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
 * This is a <code>LogFactory</code> that is able to create loggers.<br/>
 * For a usage example, see {@link LogFactory}.
 * 
 * @author Lukas Huser
 * @version 1.0
 */
public class Log4jFactory extends LogFactory {

	/**
	 * Creates a new factory instance.
	 */
	public Log4jFactory() {
		// empty constructor
	}
	
	//@Override
	protected Log newLogger(String name) {
		return new Log4jLogger(name);
	}

}
