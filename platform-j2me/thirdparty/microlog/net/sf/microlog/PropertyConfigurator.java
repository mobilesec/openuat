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

package net.sf.microlog;

import javax.microedition.midlet.MIDlet;

/**
 * The <code>PropertyConfigurator</code> is similar to the
 * <code>PropertyConfigurator</code> found in Log4j.
 * 
 * @author Johan Karlsson (johan.karlsson@jayway.se)
 * @since 1.0
 */
public class PropertyConfigurator {

	/**
	 * Configure Microlog using the default property file. Properties are not
	 * fetched from the application descriptor.
	 */
	public void configure() {

	}

	/**
	 * Configure Microlog using the default property file and the application
	 * properties for the specified MIDlet.
	 * 
	 * @param midlet
	 *            the midlet to be used for getting the application properties.
	 */
	public void configure(MIDlet midlet) {

	}
	
	/**
	 * 
	 * @param propertyFileName
	 */
	public void configure(String propertyFileName) {

	}

	/**
	 * 
	 * @param propertyFileName
	 */
	public void configure(MIDlet midlet, String propertyFileName) {

	}
}
