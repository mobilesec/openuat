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

import java.util.Vector;

import net.sf.microlog.Formatter;
import net.sf.microlog.Level;
import net.sf.microlog.format.command.CategoryFormatCommand;
import net.sf.microlog.format.command.FormatCommandInterface;
import net.sf.microlog.format.command.MessageFormatCommand;
import net.sf.microlog.format.command.NoFormatCommand;
import net.sf.microlog.format.command.PriorityFormatCommand;
import net.sf.microlog.format.command.ThreadFormatCommand;
import net.sf.microlog.format.command.ThrowableFormatCommand;
import net.sf.microlog.format.command.TimeFormatCommand;
import net.sf.microlog.util.PropertiesGetter;

/**
 * This class is the equivalent to the <code>PatternLayout</code> found in
 * Log4j. As far as possible all conversions should be available.
 * 
 * 
 * @author Johan Karlsson (johan.karlsson@jayway.se)
 * @since 0.6
 */
public class PatternFormatter implements Formatter {

	/**
	 * This is the default pattern that is used for the
	 * <code>PatternFormatter</code>.
	 * 
	 * Developer notice: If you change the pattern, please test that the pattern
	 * works as expected. Otherwise it is possible that the default (no
	 * argument) constructor do not work as expected.
	 */
	public static final String DEFAULT_CONVERSION_PATTERN = "%r [%P] %m %T";

	public static final String PATTERN_STRING = "microlog.formatter.PatternFormatter.pattern";
	
	public static final char CATEGORY_CONVERSION_CHAR = 'c';
	public static final char MESSAGE_CONVERSION_CHAR = 'm';
	public static final char PRIORITY_CONVERSION_CHAR = 'P';
	public static final char RELATIVE_TIME_CONVERSION_CHAR = 'r';
	public static final char THREAD_CONVERSION_CHAR = 't';
	public static final char THROWABLE_CONVERSION_CHAR = 'T';
	public static final char PERCENT_CONVERSION_CHAR = '%';

	private String pattern = DEFAULT_CONVERSION_PATTERN;
	private FormatCommandInterface[] commandArray;

	private boolean patternParsed = false;

	/**
	 * Create a <code>PatternFormatter</code> with the default pattern.
	 */
	public PatternFormatter() {
		patternParsed = false;
	}

	/**
	 * Format the input parameters.
	 * 
	 * @see net.sf.microlog.Formatter#format(String, long,
	 *      net.sf.microlog.Level, java.lang.Object, java.lang.Throwable)
	 */
	public String format(String name, long time, Level level, Object message, Throwable t) {

		if (!patternParsed && pattern != null) {
			parsePattern(pattern);
		}

		StringBuffer formattedStringBuffer = new StringBuffer(64);
		if (commandArray != null) {
			int length = commandArray.length;

			for (int index = 0; index < length; index++) {
				FormatCommandInterface currentConverter = commandArray[index];
				if (currentConverter != null) {
					formattedStringBuffer.append(currentConverter.execute(name,
							time, level, message, t));
				}
			}
		}

		return formattedStringBuffer.toString();
	}

	/**
	 * @see net.sf.microlog.Formatter#configure(net.sf.microlog.util.PropertiesGetter)
	 */
	public void configure(PropertiesGetter properties) {
		String pattern = properties.getString(PATTERN_STRING);
		if (pattern != null) {
			this.setPattern(pattern);
		}
	}

	/**
	 * Get the pattern that is when formatting.
	 * 
	 * @return the pattern
	 */
	public String getPattern() {
		return pattern;
	}

	/**
	 * Set the pattern that is when formatting.
	 * 
	 * @param pattern
	 *            the pattern to set
	 * @throws IllegalArgumentException
	 *             if the pattern is null.
	 */
	public void setPattern(String pattern) throws IllegalArgumentException {
		if (pattern == null) {
			throw new IllegalArgumentException("The pattern must not be null.");
		}
		
		this.pattern = pattern;
		parsePattern(this.pattern);
	}

	/**
	 * Parse the pattern.
	 * 
	 * This creates a command array that is executed when formatting the log
	 * message.
	 */
	private void parsePattern(String pattern) {

		int currentIndex = 0;
		int patternLength = pattern.length();
		Vector converterVector = new Vector(20);

		while (currentIndex < patternLength) {
			char currentChar = pattern.charAt(currentIndex);

			if (currentChar == '%') {

				currentIndex++;
				currentChar = pattern.charAt(currentIndex);

				switch (currentChar) {
				case CATEGORY_CONVERSION_CHAR:
					System.out.println("Creating Category format command");
					converterVector.addElement(new CategoryFormatCommand());
					break;
				case MESSAGE_CONVERSION_CHAR:
					converterVector.addElement(new MessageFormatCommand());
					break;

				case PRIORITY_CONVERSION_CHAR:
					converterVector.addElement(new PriorityFormatCommand());
					break;

				case RELATIVE_TIME_CONVERSION_CHAR:
					converterVector.addElement(new TimeFormatCommand());
					break;

				case THREAD_CONVERSION_CHAR:
					converterVector.addElement(new ThreadFormatCommand());
					break;

				case THROWABLE_CONVERSION_CHAR:
					converterVector.addElement(new ThrowableFormatCommand());
					break;

				case PERCENT_CONVERSION_CHAR:
					NoFormatCommand noFormatCommand = new NoFormatCommand();
					noFormatCommand.init("%");
					converterVector.addElement(noFormatCommand);
					break;

				default:
					System.err.println("Unrecognized conversion character "
							+ currentChar);
					break;
				}

				currentIndex++;

			} else {

				int percentIndex = pattern.indexOf("%", currentIndex);
				String noFormatString = "";
				if (percentIndex != -1) {
					noFormatString = pattern.substring(currentIndex,
							percentIndex);
				} else {
					noFormatString = pattern.substring(currentIndex,
							patternLength);
				}

				NoFormatCommand noFormatCommand = new NoFormatCommand();
				noFormatCommand.init(noFormatString);
				converterVector.addElement(noFormatCommand);

				currentIndex = currentIndex + noFormatString.length();
			}

		}

		commandArray = new FormatCommandInterface[converterVector.size()];
		converterVector.copyInto(commandArray);

		patternParsed = true;
	}

}
