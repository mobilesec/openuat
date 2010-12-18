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

import net.sf.microlog.util.PropertiesGetter;

/**
 * This the interface for all formatters.
 * 
 * @author Johan Karlsson (johan.karlsson@jayway.se)
 * @since 0.1
 */
public interface Formatter {

	/**
	 * Format the given message and the Throwable object.
	 * 
	 * @param name
	 *            the name of the logger.
	 * @param time
	 *            the time since the first logging has done (in milliseconds).
	 * @param level
	 *            the logging level
	 * @param message
	 *            the message
	 * @param t
	 *            the exception.
	 * @return a String that is not null.
	 */
	String format(String name, long time, Level level, Object message,
			Throwable t);

	/**
	 * Configure the appender.
	 * 
	 * @param properties
	 *            Properties to configure with
	 */
	void configure(PropertiesGetter properties);
}
