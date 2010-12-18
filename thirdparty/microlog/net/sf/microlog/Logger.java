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
package net.sf.microlog;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import net.sf.microlog.util.PropertiesGetter;
import net.sf.microlog.util.StopWatch;

/**
 * The <code>Logger</code> class is used for logging.
 * 
 * This is similiar to the Log4j <code>Logger</code> class. All the logging
 * methods are the same, but there is only one instance of the
 * <code>Logger</code> class, i.e. there are no named loggers.
 * 
 * @author Johan Karlsson (johan.karlsson@jayway.se)
 * @author Darius Katz
 * @author Karsten Ohme
 * @since 0.1
 */
public final class Logger {
	
	public static final String NAME_STRING = "microlog.name";

	public static final String LOG_LEVEL_STRING = "microlog.level";

	public static final String APPENDER_STRING = "microlog.appender";

	public static final String FORMATTER_STRING = "microlog.formatter";

	public static final String PROPERTY_DELIMETER = ";";

	private String name;

	private Level logLevel = Level.DEBUG;

	private static final StopWatch stopWatch = new StopWatch();

	private final static Vector appenderList = new Vector(4);

	private static boolean firstLogEvent = true;

	private final static Hashtable loggerHashtable = new Hashtable(47);

	/**
	 * Create a <code>Logger</code> object. This is made private to prevent
	 * the user from creating a <code>Logger</code> object through a
	 * constructor.
	 */
	private Logger() {
		name = "";
	}

	/**
	 * Create a logger with the specified <code>name</code>.
	 * 
	 * @param name
	 *            the name of the logger.
	 */
	private Logger(String name) {
		this.name = name;
	}

	/**
	 * Get the Logger instance.
	 * 
	 * @return the Logger.
	 */
	synchronized public static Logger getLogger() {
		Logger logger = (Logger) loggerHashtable.get("");

		if (logger == null) {
			logger = new Logger();
			loggerHashtable.put("", logger);
		}

		return logger;
	}

	/**
	 * Get the logger with the specified class <code>name</code>. This is the
	 * same as <code>getLogger(clazz.getName())</code>.
	 * 
	 * @param clazz
	 *            the class which name shall be used as the name for the logger.
	 * @return the logger instance.
	 */
	synchronized public static Logger getLogger(Class clazz)
			throws IllegalArgumentException {
		if (clazz == null) {
			throw new IllegalArgumentException("The clazz must not be null");
		}
		return getLogger(clazz.getName());
	}

	/**
	 * Get the logger with the specified <code>name</code>.
	 * 
	 * @param name
	 *            the name of the <code>Logger</code> object that shall be
	 *            fetched.
	 * @return the logger instance.
	 */
	synchronized public static Logger getLogger(String name)
			throws IllegalArgumentException {
		if (name == null) {
			throw new IllegalArgumentException("The name must not be null");
		}

		Logger logger = (Logger) loggerHashtable.get(name);

		if (logger == null) {
			logger = new Logger(name);
			loggerHashtable.put(name, logger);
		}

		return logger;
	}

	/**
	 * Get the log level.
	 * 
	 * @return the logLevel.
	 */
	public Level getLogLevel() {
		return logLevel;
	}

	/**
	 * Set the log level.
	 * 
	 * @param logLevel
	 *            The logLevel to set.
	 * @throws IllegalArgumentException
	 *             if the <code>logLevel</code> is null.
	 */
	public void setLogLevel(Level logLevel) throws IllegalArgumentException {
		if (logLevel == null) {
			throw new IllegalArgumentException("The level must not be null.");
		}
		this.logLevel = logLevel;
	}

	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param name
	 *            the name to set
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Clear the log.
	 * 
	 * The call is forwarded to the appender. For some appenders this is
	 * ignored. The time is also reset.
	 */
	public void clearLog() {
		int nofAppenders = Logger.appenderList.size();
		for (int index = 0; index < nofAppenders; index++) {
			Appender appender = (Appender) appenderList.elementAt(index);
			appender.clear();
		}
		Logger.firstLogEvent = true;
	}

	/**
	 * Close the log. From this point on, no logging is done.
	 * 
	 * @throws IOException
	 *             if the <code>Logger</code> failed to close.
	 */
	public void close() throws IOException {
		int nofAppenders = appenderList.size();
		for (int index = 0; index < nofAppenders; index++) {
			Appender appender = (Appender) appenderList.elementAt(index);
			appender.close();
		}
		stopWatch.stop();
		Logger.firstLogEvent = true;
	}

	/**
	 * Open the log. The logging is now turned on.
	 */
	private void open() throws IOException {

		int nofAppenders = appenderList.size();
		for (int index = 0; index < nofAppenders; index++) {
			Appender appender = (Appender) appenderList.elementAt(index);
			appender.open();
		}

	}

	/**
	 * Add the specified appender to the output appenders.
	 * 
	 * @param appender
	 *            the <code>Appender</code> to add.
	 * @throws IllegalArgumentException
	 *             if the <code>appender</code> is <code>null</code>.
	 */
	public void addAppender(Appender appender) throws IllegalArgumentException {
		if (appender == null) {
			throw new IllegalArgumentException(
					"Appender not allowed to be null");
		}

		if (!appenderList.contains(appender)) {
			appenderList.addElement(appender);
		}
	}

	/**
	 * Remove the specified appender from the appender list.
	 * 
	 * @param appender
	 *            the <code>Appender</code> to remove.
	 */
	public void removeAppender(Appender appender)
			throws IllegalArgumentException {
		if (appender == null) {
			throw new IllegalArgumentException("The appender must not be null.");
		}

		if (appender.isLogOpen()) {
			try {
				appender.close();
			} catch (IOException e) {
				System.err.println("Failed to close appender. " + e);
			}
		}
		appenderList.removeElement(appender);
	}

	/**
	 * Remove all the appenders.
	 * 
	 */
	public void removeAllAppenders() {
		for (Enumeration enumeration = appenderList.elements(); enumeration
				.hasMoreElements();) {
			Appender appender = (Appender) enumeration.nextElement();
			if (appender.isLogOpen()) {
				try {
					appender.close();
				} catch (IOException e) {
					System.err.println("Failed to close appender. " + e);
				}
			}
		}
		appenderList.removeAllElements();
	}

	/**
	 * Get the number of active appenders.
	 * 
	 * @return the number of appenders.
	 */
	public int getNumberOfAppenders() {
		return appenderList.size();
	}

	/**
	 * Reset the Logger, i.e. remove all appenders and set the log level to
	 * Level.ERROR.
	 */
	public synchronized void resetLogger() {
		Logger.appenderList.removeAllElements();
		logLevel = Level.ERROR;
		Logger.stopWatch.stop();
		Logger.stopWatch.reset();
	}

	/**
	 * Get the specified appender, starting at index = 0.
	 * 
	 * @param index
	 *            the index of the appender.
	 * @return the appender.
	 */
	public Appender getAppender(int index) {
		return (Appender) appenderList.elementAt(index);
	}

	/**
	 * Log the message at the specified level.
	 * 
	 * @param level
	 *            the <code>Level</code> to log at.
	 * @param message
	 *            the message to log.
	 * @throws IllegalArgumentException
	 *             if the <code>level</code> is <code>null</code>.
	 */
	public void log(Level level, Object message)
			throws IllegalArgumentException {
		this.log(level, message, null);
	}

	/**
	 * Log the message and the Throwable object at the specified level.
	 * 
	 * @param level
	 *            the log level
	 * @param message
	 *            the message to log.
	 * @param t
	 *            the <code>Throwable</code> object.
	 * @throws IllegalArgumentException
	 *             if the <code>level</code> is <code>null</code>.
	 */
	public void log(Level level, Object message, Throwable t)
			throws IllegalArgumentException {
		if (level == null) {
			throw new IllegalArgumentException("The level must not be null.");
		}

		if (logLevel.toInt() <= level.toInt()) {
			int nofAppenders = appenderList.size();

			if (firstLogEvent == true) {
				if (nofAppenders == 0) {
					System.err
							.println("Warning! No appender was added to the logger.");
				}

				try {
					open();
				} catch (IOException e) {
					System.err.println("Failed to open the log. " + e);
				}

				stopWatch.start();
				firstLogEvent = false;
			}

			for (int index = 0; index < nofAppenders; index++) {
				Appender appender = (Appender) appenderList.elementAt(index);
				appender.doLog(name, stopWatch.getCurrentTime(), level, message, t);
			}
		}
	}

	/**
	 * Is this <code>Logger</code> enabled for TRACE level?
	 * 
	 * @return true if logging is enabled.
	 */
	public boolean isTraceEnabled() {
		return logLevel.toInt() >= Level.TRACE_INT;
	}

	/**
	 * Log the message at <code>Level.TRACE</code> level.
	 * 
	 * @param message
	 *            the message to log.
	 */
	public void trace(Object message) {
		log(Level.TRACE, message, null);
	}

	/**
	 * Log the message and the <code>Throwable</code> object at
	 * <code>Level.TRACE</code>.
	 * 
	 * @param message
	 *            the message to log.
	 * @param t
	 *            the Throwable object to log.
	 */
	public void trace(Object message, Throwable t) {
		log(Level.TRACE, message, t);
	}

	/**
	 * Is this <code>Logger</code> enabled for DEBUG level?
	 * 
	 * @return true if logging is enabled.
	 */
	public boolean isDebugEnabled() {
		return logLevel.toInt() >= Level.DEBUG_INT;
	}

	/**
	 * Log the message at <code>Level.DEBUG</code> level.
	 * 
	 * @param message
	 *            the message to log.
	 */
	public void debug(Object message) {
		log(Level.DEBUG, message, null);
	}

	/**
	 * Log the message and the <code>Throwable</code> object at
	 * <code>Level.DEBUG</code> level.
	 * 
	 * @param message
	 *            the message to log.
	 * @param t
	 *            the <code>Throwable</code> object to log.
	 */
	public void debug(Object message, Throwable t) {
		log(Level.DEBUG, message, t);
	}

	/**
	 * Is this <code>Logger</code> enabled for INFO level?
	 * 
	 * @return true if the <code>Level.INFO</code> level is enabled.
	 */
	public boolean isInfoEnabled() {
		return logLevel.toInt() >= Level.INFO_INT;
	}

	/**
	 * Log the specified message at <code>Level.INFO</code> level.
	 * 
	 * @param message
	 *            the message to log.
	 */
	public void info(Object message) {
		log(Level.INFO, message, null);
	}

	/**
	 * Log the specified message and the <code>Throwable</code> at
	 * <code>Level.INFO</code> level.
	 * 
	 * @param message
	 *            the message to log.
	 * @param t
	 *            the <code>Throwable</code> to log.
	 */
	public void info(Object message, Throwable t) {
		log(Level.INFO, message, t);
	}

	/**
	 * Is this <code>Logger</code> enabled for <code>Level.WARN</code>
	 * level?
	 * 
	 * @return true if WARN level is enabled.
	 */
	public boolean isWarnEnabled() {
		return logLevel.toInt() >= Level.WARN_INT;
	}

	/**
	 * Log the specified message at <code>Level.WARN</code> level.
	 * 
	 * @param message
	 *            the message to log.
	 */
	public void warn(Object message) {
		log(Level.WARN, message, null);
	}

	/**
	 * Log the specified message and <code>Throwable</code> object at
	 * <code>Level.WARN</code> level.
	 * 
	 * @param message
	 *            the object to log.
	 * @param t
	 *            the <code>Throwable</code> to log.
	 */
	public void warn(Object message, Throwable t) {
		log(Level.WARN, message, t);
	}

	/**
	 * Is this LOGGER enabled for ERROR level?
	 * 
	 * @return true if the ERROR level is enabled.
	 */
	public boolean isErrorEnabled() {
		return logLevel.toInt() >= Level.ERROR_INT;
	}

	/**
	 * Log the specified message at ERROR level.
	 * 
	 * @param message
	 *            the object to log.
	 */
	public void error(Object message) {
		log(Level.ERROR, message, null);
	}

	/**
	 * Log the specified message and Throwable object at ERROR level.
	 * 
	 * @param message
	 *            the object to log.
	 * @param t
	 *            the <code>Throwable</code> to log.
	 */
	public void error(Object message, Throwable t) {
		log(Level.ERROR, message, t);
	}

	/**
	 * Is this LOGGER enabled for FATAL level?
	 * 
	 * @return true if the FATAL level is enabled.
	 */
	public boolean isFatalEnabled() {
		return logLevel.toInt() >= Level.FATAL_INT;
	}

	/**
	 * Log the specified message at FATAL level.
	 * 
	 * @param message
	 *            the object to log.
	 */
	public void fatal(Object message) {
		log(Level.FATAL, message, null);
	}

	/**
	 * Log the specified message and Throwable object at FATAL level.
	 * 
	 * @param message
	 *            the object to log.
	 * @param t
	 *            the <code>Throwable</code> to log.
	 */
	public void fatal(Object message, Throwable t) {
		log(Level.FATAL, message, t);
	}

	/**
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		StringBuffer stringBuffer = new StringBuffer();
		stringBuffer.append(super.toString());
		stringBuffer.append('[');

		int nofAppenders = appenderList.size();
		for (int index = 0; index < nofAppenders; index++) {
			Appender appender = (Appender) appenderList.elementAt(index);
			stringBuffer.append(appender);
			stringBuffer.append(';');
		}
		stringBuffer.append(']');
		return stringBuffer.toString();
	}

	/**
	 * Configure the logger.
	 * 
	 * @param properties
	 *            Properties to configure with
	 * @throws IllegalArgumentException
	 *             if <code>properties</code> is <code>null</code>.
	 */
	public void configure(PropertiesGetter properties)
			throws IllegalArgumentException {
		if (properties == null) {
			throw new IllegalArgumentException(
					"The properties are not allowed to be null.");
		}

		configureLogLevel(properties.getString(LOG_LEVEL_STRING));
		configureAppender(properties);
		configureFormatter(properties);

	}

	/**
	 * Configure the log level.
	 * 
	 * @param logLevel
	 *            The logLevel to set.
	 */
	void configureLogLevel(String logLevel) {

		if (logLevel != null) {
			if (logLevel.compareTo(Level.FATAL_STRING) == 0) {
				setLogLevel(Level.FATAL);
			} else if (logLevel.compareTo(Level.ERROR_STRING) == 0) {
				setLogLevel(Level.ERROR);
			} else if (logLevel.compareTo(Level.WARN_STRING) == 0) {
				setLogLevel(Level.WARN);
			} else if (logLevel.compareTo(Level.INFO_STRING) == 0) {
				setLogLevel(Level.INFO);
			} else if (logLevel.compareTo(Level.DEBUG_STRING) == 0) {
				setLogLevel(Level.DEBUG);
			} else if (logLevel.compareTo(Level.TRACE_STRING) == 0) {
				setLogLevel(Level.TRACE);
			}

			System.out.println("Log level is configured to " + getLogLevel());
		}
	}

	/**
	 * Configure the appender for the specified logger.
	 * 
	 * @param appenderString
	 *            the <code>String</code> to use for configuring the
	 *            <code>Appender</code>.
	 */
	private void configureAppender(PropertiesGetter properties) {
		// remove all appenders and close the log
		removeAllAppenders();
		String appenderString = properties.getString(APPENDER_STRING);

		if ((appenderString != null) && (appenderString.length() > 0)) {
			try {
				int delimiterPos = appenderString.indexOf(PROPERTY_DELIMETER);
				if (delimiterPos == -1) {
					// There is only one appender
					Class appenderClass = Class.forName(appenderString);
					Appender appender = (Appender) appenderClass.newInstance();
					appender.configure(properties);
					try {
						appender.open();
					} catch (IOException e) {
						System.err.println("Failed to open the " + appender
								+ " " + e);
					}
					addAppender(appender);
				} else {
					// Loop through all the Appenders in appenderString
					int startPos = 0;
					int endPos;
					boolean finished = false;
					do {
						// find out if and where the next string is
						delimiterPos = appenderString.indexOf(
								PROPERTY_DELIMETER, startPos);
						if (delimiterPos == -1) {
							// this is the last appender
							endPos = appenderString.length();
							finished = true;
						} else {
							// has a delimiter at the end
							endPos = delimiterPos;
						}

						// get the appender string
						String singleAppenderString = appenderString.substring(
								startPos, endPos);

						// Advance the start position
						startPos = endPos + 1;

						// create the appender
						if (singleAppenderString.length() > 0) {
							Class appenderClass = Class
									.forName(singleAppenderString);

							Appender appender = (Appender) appenderClass
									.newInstance();
							appender.configure(properties);
							try {
								appender.open();
							} catch (IOException e) {
								System.err.println("Failed to open the "
										+ appender + " " + e);
							}
							addAppender(appender);
							System.out.println("Added appender " + appender);
						}
					} while (!finished);
				}
			} catch (ClassNotFoundException e) {
				System.err.println("Did not find the appender class. " + e);
			} catch (InstantiationException e) {
				System.err
						.println("Did not manage to initiate the appender class. "
								+ e);
			} catch (IllegalAccessException e) {
				System.err
						.println("Did not have access to create the appender class. "
								+ e);
			}
		}
	}

	/**
	 * Configure the formatter for the specified logger.
	 * 
	 * @param formatterString
	 *            the <code>String</code> to use for configuring the
	 *            <code>Formatter</code>.
	 */
	private void configureFormatter(PropertiesGetter properties) {
		String formatterString = properties.getString(FORMATTER_STRING);
		if (formatterString != null) {
			try {
				String className = formatterString;
				if (formatterString.indexOf(PROPERTY_DELIMETER) != -1) {
					className = formatterString.substring(0, formatterString
							.indexOf(PROPERTY_DELIMETER));
				}
				Class formatterClass = Class.forName(className);
				Formatter formatter = (Formatter) formatterClass.newInstance();
				System.out.println("Using formatter " + formatter);
				formatter.configure(properties);

				int nofAppenders = getNumberOfAppenders();
				for (int index = 0; index < nofAppenders; index++) {
					Appender appender = getAppender(index);
					if (appender != null) {
						appender.setFormatter(formatter);
					}
				}
			} catch (ClassNotFoundException e) {
				System.err.println("Did not find the formatter class. " + e);
			} catch (InstantiationException e) {
				System.err
						.println("Did not manage to initiate the formatter class. "
								+ e);
			} catch (IllegalAccessException e) {
				System.err
						.println("Did not have access to create the formatter class. "
								+ e);
			}
		}

	}

}
