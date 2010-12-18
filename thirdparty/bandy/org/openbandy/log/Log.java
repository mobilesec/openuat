/**
 *  Filename: Log.java (in org.openbandy.log)
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

package org.openbandy.log;

/**
 * The Log interface provides methods to print and/or store log messages of
 * different severity.
 * 
 * <br>
 * <br>
 * (c) Copyright P. Bolliger 2007, ALL RIGHTS RESERVED.
 * 
 * @author Philipp Bolliger (philipp@bolliger.name)
 * @version 1.0
 */
public interface Log {

	/**
	 * Logs a message with log level LEVEL_ERROR
	 * 
	 * @param origin
	 *            The object reporting
	 * @param message
	 *            The message to log
	 * @param exception
	 *            The exception that was caused with this error
	 */
	public void error(Object origin, String message, Throwable exception);

	/**
	 * Logs a message with log level LEVEL_ERROR and prints the stack trace to
	 * System.out
	 * 
	 * @param originName
	 *            The class name of the reporting object
	 * @param message
	 *            The message to log
	 * @param exception
	 *            The exception that was caused with this error
	 */
	public void error(String originName, String message, Throwable exception);

	/**
	 * Logs a message with log level LEVEL_WARNING
	 * 
	 * @param origin
	 *            The object reporting
	 * @param message
	 *            The message to log
	 */
	public void warn(Object origin, String message);

	/**
	 * Logs a message with log level LEVEL_WARNING
	 * 
	 * @param originName
	 *            The class name of the object reporting
	 * @param message
	 *            The message to log
	 */
	public void warn(String originName, String message);

	/**
	 * Logs a message with log level LEVEL_INFO
	 * 
	 * @param origin
	 *            The object reporting
	 * @param message
	 *            The message to log
	 */
	public void info(Object origin, String message);

	/**
	 * Logs a message with log level LEVEL_INFO
	 * 
	 * @param originName
	 *            The class name of the object reporting
	 * @param message
	 *            The message to log
	 */
	public void info(String originName, String message);

	/**
	 * Logs a message with log level LEVEL_DEBUG
	 * 
	 * @param origin
	 *            The object reporting
	 * @param message
	 *            The message to log
	 */
	public void debug(Object origin, String message);

	/**
	 * Logs a message with log level LEVEL_DEBUG
	 * 
	 * @param originName
	 *            The class name of the object reporting
	 * @param message
	 *            The message to log
	 */
	public void debug(String originName, String message);

	/**
	 * Set the log level
	 * 
	 * @param level
	 *            Log level according to
	 * @link org.openbandy.log.LogLevel
	 */
	public void setLogLevel(int level);
	
	//added by Iulia Ion
	public void closeFile();
}
