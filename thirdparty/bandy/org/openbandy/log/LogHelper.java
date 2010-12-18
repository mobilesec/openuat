/**
 *  Filename: LogHelper.java (in org.openbandy.log)
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

import org.openbandy.util.TimestampUtil;


/**
 * The LogHelper provides two static methods to create log messages and
 * determine the log level respectively.
 * 
 * The log level is thus included as a number according to <cod>LogLevel</code>
 * and added at the beginning of the log message String.
 * 
 * <br>
 * <br>
 * (c) Copyright P. Bolliger 2007, ALL RIGHTS RESERVED.
 * 
 * @author Philipp Bolliger (philipp@bolliger.name)
 * @version 1.0
 */
public class LogHelper {

	/**
	 * Create the log message string from the given parameters according to the
	 * format:
	 * <code>level(1)|timestamp(17)| |class name (if given)|: |log message</code>
	 * 
	 * @param level
	 *            The log level that should be applied
	 * @param message
	 *            The message to log
	 * @param originClassName
	 *            The short class name (i.e. without package indication) of the
	 *            class that logs
	 * @param exception
	 *            The exception that was caused with this error, or null if it
	 *            is not an error log
	 * @return The compiled log message
	 */
	public static String createLogMessage(int level, Object message, String originClassName, Throwable exception) {
		/* start the log message by indicating the log level */
		String logMessage = "" + level;

		/* add timestamp */
		logMessage = logMessage + TimestampUtil.getActualFormatedDate();

		/* add class name if available */
		if (originClassName != "") {
			logMessage = logMessage + " " + originClassName + ": ";
		}

		/* set message as empty string if not provided */
		if (message == null) {
			message = "";
		}

		/* add the actual message */
		logMessage += message.toString();

		/* if given, add the class name of the exception */
		if (exception != null) {
			logMessage += " (" + exception.getClass().getName() + ")";
		}

		return logMessage;
	}

	/**
	 * This method extracts the log level from the log message, if
	 * <code>logMessage</code> is formatted as:
	 * 
	 * <code>level(1)|timestamp(17)| |class name (if given)|: |log message</code>
	 * 
	 * @param logMessage
	 *            The log message, starting with an indicator of the log level
	 * @return The log level as encoded if valid, or
	 *         <code>LogLevel.DEFAULT</code> else
	 */
	public static int getLogLevel(String logMessage) {
		int logLevel = LogLevel.DEFAULT;
		try {
			logLevel = Integer.parseInt(logMessage.substring(0, 1));
			if (!LogLevel.isValidLogLevel(logLevel)) {
				logLevel = LogLevel.DEFAULT;
			}
		}
		catch (NumberFormatException e) {
			/* do nothing */
		}
		return logLevel;
	}

}
