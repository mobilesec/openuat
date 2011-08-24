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
package net.sf.microlog.util.properties;

import java.util.Hashtable;

/**
 * A property source that contains the default values. This is the lowest level
 * from which properties originate.
 * 
 * @author Darius Katz
 * @author Johan Karlsson (johan.karlsson@jayway.se)
 */
public class DefaultValues implements PropertySource {

	private static final String defaultValues[][] = {
			{ "microlog.level", "INFO" },
			{ "microlog.appender", "net.sf.microlog.appender.ConsoleAppender" },
			{ "microlog.formatter", "net.sf.microlog.format.SimpleFormatter" }, };

	/**
	 * Insert the values taken from a property source into the Hashtable. This
	 * is the lowest level from which properties originate.
	 * 
	 * @param properties
	 *            the Hashtable in which the properties are stored
	 * 
	 */
	public void insertProperties(Hashtable properties) {
		for (int i = 0; i < defaultValues.length; i++) {
			properties.put(defaultValues[i][0], defaultValues[i][1]);
		}
	}

	/**
	 * Returns the Object to which the specified key is mapped, directly from
	 * the source of the default values (that is not from the Hashtable).
	 * 
	 * @param key
	 *            the key associated to the stored Object
	 * 
	 * @return the Object to which the key is mapped; null if the key is not
	 *         mapped to any Object
	 */
	public Object get(String key) {
		boolean notFound = true;
		for (int i = 0; i < defaultValues.length && notFound; i++) {
			if (key.compareTo(defaultValues[i][0]) == 0) {
				return defaultValues[i][1];
			}
		}

		return null;
	}
}
