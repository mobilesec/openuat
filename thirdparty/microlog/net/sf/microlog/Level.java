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

/**
 * This class represent the logging level.
 * 
 * @author Johan Karlsson (johan.karlsson@jayway.se)
 * @since 0.1
 */
public final class Level {

	public static final int FATAL_INT = 16;

	public static final int ERROR_INT = 8;

	public static final int WARN_INT = 4;

	public static final int INFO_INT = 2;

	public static final int DEBUG_INT = 1;

	public static final int TRACE_INT = 0;

	public static final String FATAL_STRING = "FATAL";

	public static final String ERROR_STRING = "ERROR";

	public static final String WARN_STRING = "WARN";

	public static final String INFO_STRING = "INFO";

	public static final String DEBUG_STRING = "DEBUG";

	public static final String TRACE_STRING = "TRACE";

	public static final Level FATAL = new Level(FATAL_INT, FATAL_STRING);

	public static final Level ERROR = new Level(ERROR_INT, ERROR_STRING);

	public static final Level WARN = new Level(WARN_INT, WARN_STRING);

	public static final Level INFO = new Level(INFO_INT, INFO_STRING);

	public static final Level DEBUG = new Level(DEBUG_INT, DEBUG_STRING);

	public static final Level TRACE = new Level(TRACE_INT, TRACE_STRING);

	private int level;

	private String levelString = "";

	/**
	 * Create a <code>Level</code> object.
	 * 
	 * @param level
	 *            the level to create. This should be set using one of the
	 *            constants defined in the class.
	 * @param levelString
	 *            the <code>String</code> that shall represent the level. This
	 *            should be set using one of the defined constants defined in
	 *            the class.
	 */
	private Level(int level, String levelString) {
		this.level = level;
		this.levelString = levelString;
	}

	/**
	 * Return the integer level for this <code>Level</code>.
	 * 
	 * @return the integer level.
	 */
	public int toInt() {
		return level;
	}

	/**
	 * Return a <code>String</code> representation for this <code>Level</code>.
	 * 
	 * @return a <code>String</code> representation for the <code>Level</code>.
	 */
	public String toString() {
		return levelString;
	}
}
