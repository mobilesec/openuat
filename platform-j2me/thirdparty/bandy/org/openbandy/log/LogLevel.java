/**
 *  Filename: LogLevel.java (in org.openbandy.log)
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
 * The LogLevel class defines the different log levels as integer values, as
 * strings and the colors that are used to print (i.e. draw) the log messages.
 * Additionally, it provides some auxiliary conversion methods.
 * 
 * <br>
 * <br>
 * (c) Copyright P. Bolliger 2007, ALL RIGHTS RESERVED.
 * 
 * @author Philipp Bolliger (philipp@bolliger.name)
 * @version 1.2
 */
public final class LogLevel {

	/**
	 * The log levels
	 */
	public static final int ERROR = 0;

	public static final int WARNING = 1;

	public static final int INFO = 2;

	public static final int DEBUG = 3;

	public static final int DEFAULT = INFO;

	public static final int NUMBER_OF_LEVELS = 4;

	/**
	 * Human readable string representation, note that all strings have lenght
	 * 7!
	 */
	private static final String STRING_ERROR = "Error  ";

	private static final String STRING_WARNING = "Warning";

	private static final String STRING_INFO = "Info   ";

	private static final String STRING_DEBUG = "Debug  ";

	/**
	 * Readable string assignment to the different types of log messages
	 */
	public static final String[] levels = new String[NUMBER_OF_LEVELS];
	static {
		levels[ERROR] = STRING_ERROR;
		levels[WARNING] = STRING_WARNING;
		levels[INFO] = STRING_INFO;
		levels[DEBUG] = STRING_DEBUG;
	}

	/**
	 * The message colors for the different log levels
	 */
	private static final int COLOR_ERROR = 13893632; // dark red

	private static final int COLOR_WARNING = 13143296; // dark orange

	private static final int COLOR_INFO = 0; // black

	private static final int COLOR_DEBUG = 5131854; // dark grey

	/**
	 * Color schema assignment to the different types of log messages
	 */
	public static int[] colors = new int[NUMBER_OF_LEVELS];
	static {
		colors[ERROR] = COLOR_ERROR;
		colors[WARNING] = COLOR_WARNING;
		colors[INFO] = COLOR_INFO;
		colors[DEBUG] = COLOR_DEBUG;
	}

	/**
	 * Get the int value of the log level which is represented as a string.
	 * (Ingore case)
	 * 
	 * @param logLevelString
	 *            The log level as a string, e.g. Info or Debug
	 * @return The corresponding int value (e.g. 2 for Info) if no match is
	 *         found, DEFAULT will be returned
	 */
	public static int getLevelFromString(String logLevelString) {
		if (logLevelString.equalsIgnoreCase(STRING_ERROR.trim())) {
			return ERROR;
		}
		if (logLevelString.equalsIgnoreCase(STRING_WARNING.trim())) {
			return WARNING;
		}
		if (logLevelString.equalsIgnoreCase(STRING_INFO.trim())) {
			return INFO;
		}
		if (logLevelString.equalsIgnoreCase(STRING_DEBUG.trim())) {
			return DEBUG;
		}
		return DEFAULT;
	}

	/**
	 * Check if the given int is a valid log level.
	 * 
	 * @param logLevel
	 *            The log level value to check
	 * @return True, if <code>logLevel</code> is between <code>ERROR</code>
	 *         and <code>DEBUG</code>
	 */
	public static boolean isValidLogLevel(int logLevel) {
		return ((logLevel >= ERROR) && (logLevel <= DEBUG));
	}

}
