/*
 * Copyright 2008 The MicroLog project @sourceforge.net
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

package net.sf.microlog.format.command;

import net.sf.microlog.Level;

/**
 * The <code>CategoryFormatCommand</code> is used for printing the category,
 * i.e. the name of the logging class.
 * 
 * @author Johan Karlsson (johan.karlsson@jayway.se)
 * 
 * @since 1.0
 */
public class CategoryFormatCommand implements FormatCommandInterface {

	/**
	 * 
	 * 
	 * @see net.sf.microlog.format.command.FormatCommandInterface#execute(String,
	 *      long, net.sf.microlog.Level, java.lang.Object, java.lang.Throwable)
	 */
	public String execute(String name, long time, Level level, Object message,
			Throwable throwable) {
		String convertedData = "";

		if (name != null) {
			convertedData = name;
		}

		return convertedData;
	}

	/**
	 * 
	 * @see net.sf.microlog.format.command.FormatCommandInterface#init(java.lang.String)
	 */
	public void init(String initString) {
		// No effect
	}

}
