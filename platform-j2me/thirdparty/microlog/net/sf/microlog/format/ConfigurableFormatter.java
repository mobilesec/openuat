/*
 * Copyright 2008 The Microlog project @sourceforge.net
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.sf.microlog.format;

import java.util.Date;

import net.sf.microlog.Formatter;
import net.sf.microlog.Level;
import net.sf.microlog.util.PropertiesGetter;

/**
 * A formatter that could be configured how the formatting shall be. It is not
 * possible to set the order of the output.
 * 
 * @author Johan Karlsson (johan.karlsson@jayway.se)
 */
public class ConfigurableFormatter implements Formatter {

	public static final int NO_TIME = 0;

	public static final int DATE_TO_STRING = 1;

	public static final int TIME_IN_MILLIS = 2;

	public static final String TIME_FORMAT_VALUE_DATE = "date";
	public static final String TIME_FORMAT_VALUE_MILLIS = "millis";

	public static final String LEVEL_STRING = "microlog.formatter.ConfigurableFormatter.level";
	public static final String TIME_STRING = "microlog.formatter.ConfigurableFormatter.time";
	public static final String DELIMETER_STRING = "microlog.formatter.ConfigurableFormatter.delimiter";

	private static final int INITIAL_BUFFER_SIZE = 256;

	private final StringBuffer buffer = new StringBuffer(INITIAL_BUFFER_SIZE);

	private int timeFormat = NO_TIME;

	private boolean printLevel = true;

	private boolean printMessage = true;

	private String delimiter = ":";

	/**
	 * @see net.sf.microlog.Formatter#format(String, long,
	 *      net.sf.microlog.Level, java.lang.Object, java.lang.Throwable)
	 */
	public String format(String name, long time, Level level,
			Object message, Throwable throwable) {
		if (buffer.length() > 0) {
			buffer.delete(0, buffer.length());
		}

		if (timeFormat == DATE_TO_STRING) {
			buffer.append((new Date()).toString());
		} else if (timeFormat == TIME_IN_MILLIS) {
			buffer.append(time);
		}

		if (printLevel && level != null) {
			buffer.append(delimiter);
			buffer.append('[');
			buffer.append(level);
			buffer.append(']');
		}

		if (printMessage && message != null) {
			buffer.append(delimiter);
			buffer.append(message);
		}

		if (throwable != null) {
			buffer.append(delimiter);
			buffer.append(throwable);
		}

		return buffer.toString();
	}

	/**
	 * 
	 * @see net.sf.microlog.Formatter#configure(net.sf.microlog.util.PropertiesGetter)
	 */
	public void configure(PropertiesGetter properties) {

		String timeProperty = properties.getString(TIME_STRING);
		if (timeProperty != null) {
			if (timeProperty.compareTo(TIME_FORMAT_VALUE_DATE) == 0) {
				setTimeFormat(DATE_TO_STRING);
			} else if (timeProperty.compareTo(TIME_FORMAT_VALUE_MILLIS) == 0) {
				setTimeFormat(TIME_IN_MILLIS);
			} else {
				setTimeFormat(NO_TIME);
			}
		}

		String levelProperty = properties.getString(LEVEL_STRING);
		if (levelProperty != null) {
			setPrintLevel(toBoolean(levelProperty));
		}

		String delimeterProperty = properties.getString(DELIMETER_STRING);
		if (delimeterProperty != null) {
			setDelimiter(delimeterProperty);
		}
	}

	/**
	 * @return Returns the printLevel.
	 */
	public final boolean isPrintLevel() {
		return printLevel;
	}

	/**
	 * @param printLevel
	 *            The printLevel to set.
	 */
	public final void setPrintLevel(boolean printLevel) {
		this.printLevel = printLevel;
	}

	/**
	 * @return Returns the timeFormat.
	 */
	public final int getTimeFormat() {
		return timeFormat;
	}

	/**
	 * @param timeFormat
	 *            The timeFormat to set.
	 */
	public final void setTimeFormat(int timeFormat) {
		this.timeFormat = timeFormat;
	}

	/**
	 * @return Returns the printMessage.
	 */
	public final boolean isPrintMessage() {
		return printMessage;
	}

	/**
	 * @param printMessage
	 *            The printMessage to set.
	 */
	public final void setPrintMessage(boolean printMessage) {
		this.printMessage = printMessage;
	}

	/**
	 * @return Returns the delimiter.
	 */
	public final String getDelimeter() {
		return delimiter;
	}

	/**
	 * Set the delimiter.
	 * 
	 * @param delimiter
	 *            The delimiter to set.
	 */
	public final void setDelimiter(String delimiter)
			throws IllegalArgumentException {
		if (delimiter == null) {
			throw new IllegalArgumentException(
					"The delimiter must not be null.");
		}

		this.delimiter = delimiter;
	}

	/**
	 * Converts the supplied value to a boolean.
	 * 
	 * @param value
	 *            the value.
	 * @return a boolean.
	 */
	private boolean toBoolean(Object value) {
		String valueString = value.toString();
		boolean toBoolean = false;

		if ((valueString.compareTo("true") == 0)
				|| (valueString.compareTo("on") == 0)) {
			toBoolean = true;
		} else if ((valueString.compareTo("false") == 0)
				|| (valueString.compareTo("off") == 0)) {
			toBoolean = false;
		}

		return toBoolean;
	}
}
