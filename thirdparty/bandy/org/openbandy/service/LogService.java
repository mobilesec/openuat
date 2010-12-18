/**
 *  Filename: LogService.java (in org.openbandy.service)
 *  This file is part of the OpenBandy project.
 * 
 *  OpenBandy is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  any later version.
 *
 *  OpenBandy is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with OpenBandy. If not, see <http://www.gnu.org/licenses/>.
 *
 *
 * (c) Copyright Philipp Bolliger 2007, ALL RIGHTS RESERVED.
 * 
 *  www.openbandy.org
 */

package org.openbandy.service;

import org.openbandy.log.Log;
import org.openbandy.log.LogConfiguration;
import org.openbandy.log.LogImpl;


/**
 * This is a helper class to simplify (and abstract) the usage of logging
 * methods as logging functionality by providing static methods.
 * 
 * <br>
 * <br>
 * (c) Copyright P. Bolliger 2007, ALL RIGHTS RESERVED.
 * 
 * @author Philipp Bolliger (philipp@bolliger.name)
 * @version 1.5
 */
public final class LogService extends Service {

	/* static reference to the log */
	private static Log log;

	/*
	 * try to statically assign a log of type org.openbandy.log.LogImpl
	 */
	static {
		String conf = System.getProperty("microedition.configuration");
		if (conf != null) {
			String profiles = System.getProperty("microedition.profiles");
			String platform = System.getProperty("microedition.platform");
			String encoding = System.getProperty("microedition.encoding");
			String locale = System.getProperty("microedition.locale");

			log = new LogImpl(LogConfiguration.getLogAsAppScreenConfig(true));
			debug("LogService", "Running on " + conf + " " + profiles + " " + platform + " " + encoding + " " + locale);
		}
	}

	/* ***** Getter and Setter Methods ***** */

	/**
	 * Returns a reference to the actual log if this is an instance of type
	 * <code>org.openbandy.log.LogImpl</code>.
	 * 
	 * @return Reference to the current log if it is a MicroeditionLog, null
	 *         else
	 */
	public static LogImpl getLog() {
		try {
			LogImpl microeditionLog = (LogImpl) log;
			return microeditionLog;
		}
		catch (ClassCastException cce) {
			error("LogService", "Log is not a MicroeditionLog", cce);
			return null;
		}
	}

	public static void setLog(Log log) {
		LogService.log = log;
	}

	/* ***** Logging Methods ***** */

	/**
	 * Forwards a message with log level LEVEL_ERROR to the log service
	 * 
	 * @param originName
	 *            The object reporting
	 * @param msg
	 *            The message to log
	 * @param exception
	 *            The exception that was caused with this error
	 */
	public static void error(Object originName, String msg, Throwable exception) {
		if (log != null) {
			log.error(originName, msg, exception);
		}
	}

	/**
	 * Forwards a message with log level LEVEL_ERROR to the log service
	 * 
	 * @param originName
	 *            The reporting object's class name
	 * @param msg
	 *            The message to log
	 * @param exception
	 *            The exception that was caused with this error
	 */
	public static void error(String originName, String msg, Throwable exception) {
		if (log != null) {
			log.error(originName, msg, exception);
		}
	}

	/**
	 * Forwards a message with log level LEVEL_WARNING to the log service
	 * 
	 * @param originName
	 *            The object reporting
	 * @param msg
	 *            The message to log
	 */
	public static void warn(Object originName, String msg) {
		if (log != null) {
			log.warn(originName, msg);
		}
	}

	/**
	 * Forwards a message with log level LEVEL_WARNING to the log service
	 * 
	 * @param originName
	 *            The name of the object reporting
	 * @param msg
	 *            The message to log
	 */
	public static void warn(String originName, String msg) {
		if (log != null) {
			log.warn(originName, msg);
		}
	}

	/**
	 * Forwards a message with log level LEVEL_INFO to the log service
	 * 
	 * @param originName
	 *            The object reporting
	 * @param msg
	 *            The message to log
	 */
	public static void info(Object originName, String msg) {
		if (log != null) {
			log.info(originName, msg);
		}
	}

	/**
	 * Forwards a message with log level LEVEL_INFO to the log service
	 * 
	 * @param originName
	 *            The name of the object reporting
	 * @param msg
	 *            The message to log
	 */
	public static void info(String originName, String msg) {
		if (log != null) {
			log.info(originName, msg);
		}
	}

	/**
	 * Forwards a message with log level LEVEL_DEBUG to the log service
	 * 
	 * @param originName
	 *            The object reporting
	 * @param msg
	 *            The message to log
	 */
	public static void debug(Object originName, String msg) {
		if (log != null) {
			log.debug(originName, msg);
		}
	}

	/**
	 * Forwards a message with log level LEVEL_DEBUG to the log service
	 * 
	 * @param originName
	 *            The name of the object reporting
	 * @param msg
	 *            The message to log
	 */
	public static void debug(String originName, String msg) {
		if (log != null) {
			log.debug(originName, msg);
		}
	}

	/**
	 * Set the log level.
	 * 
	 * @param level
	 *            Log level according to
	 * @link org.openbandy.log.LogLevel
	 */
	public static void setLogLevel(int level) {
		if (log != null) {
			log.setLogLevel(level);
		}
	}
	
	//added by Iulia Ion
	public static void closeLogFile(){
		//close the log file before exiting the application
		
		log.closeFile();
	}
}
