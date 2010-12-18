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
package net.sf.microlog.util;

import java.util.Hashtable;

import javax.microedition.midlet.MIDlet;

import net.sf.microlog.util.properties.DefaultValues;
import net.sf.microlog.util.properties.PropertyFile;
import net.sf.microlog.util.properties.PropertySource;

/**
 * A class that makes the properties available to the system. The properties are
 * based on key/value, i.e: <code>some.key=some_value</code>
 * 
 * The properties are taken from the following sources in this order. (That is,
 * the values have precedence in this order.)
 * 
 * <ol>
 * <li>The application properties (from the JAD file). </li>
 * <li>The properties from the property file, e.g. microlog.properties. </li>
 * <li>Default values (defined in the DefaultValues class)</li>
 * </ol>
 * 
 * 
 * @author Johan Karlsson (johan.karlsson@jayway.se)
 * @author Darius Katz
 */
public class Properties implements PropertiesGetter {

	public static final String DEFAULT_PROPERTY_FILE = "/microlog.properties";

	private final Hashtable properties = new Hashtable(31);

	private String propertyFileName;

	private MIDlet midlet;

	DefaultValues defaultValues;

	boolean propertiesInitialized = false;

	/**
	 * Create a new <code>Properties</code> object with the default settings.
	 */
	public Properties() {

	}

	/**
	 * Create a new <code>Properties</code> object. This could fetch
	 * application properties found in the Application Descriptor, i.e. fetched
	 * by calling <code>MIDlet.getAppProperty()</code>. If the midlet is
	 * null, no properties will be fetched from the application description
	 * (JAD).
	 * 
	 * @param midlet
	 *            the MIDlet from which application properties will be fetched
	 */
	public Properties(MIDlet midlet) {
		this.midlet = midlet;
	}

	/**
	 * Create a new <code>Properties</code> object. This could fetch
	 * application properties found in the Application Descriptor, i.e. fetched
	 * by calling <code>MIDlet.getAppProperty()</code>.
	 * 
	 * @param midlet
	 *            the MIDlet from which application properties will be fetched
	 * @param propertyFileName
	 *            the name of the property file to use.
	 */
	public Properties(MIDlet midlet, String propertyFileName) {
		this.midlet = midlet;
		this.propertyFileName = propertyFileName;
	}

	/**
	 * Get the midlet that when getting the application properties.
	 * 
	 * @return the midlet to use for getting the application properties.
	 */
	public MIDlet getMidlet() {
		return midlet;
	}

	/**
	 * GSet the midlet that when getting the application properties.
	 * 
	 * @param midlet
	 *            the midlet to use for getting the application properties. If
	 *            null, no properties are fetched from the application
	 *            descriptior.
	 */
	public void setMidlet(MIDlet midlet) {
		this.midlet = midlet;
		propertiesInitialized = false;
	}

	/**
	 * Get the name of the property file.
	 * 
	 * @return propertyFileName the name of the property file.
	 */
	public String getPropertyFileName() {
		return propertyFileName;
	}

	/**
	 * Set the name of the property file.
	 * 
	 * @param propertyFileName
	 *            the propertyFileName to use for getting the file properties.
	 *            If null, the default file name is used.
	 */
	public void setPropertyFileName(String propertyFileName) {
		this.propertyFileName = propertyFileName;
		propertiesInitialized = false;
	}

	/**
	 * Returns the Object to which the specified key is mapped.
	 * 
	 * @param key
	 *            the key associated to the stored Object
	 * 
	 * @return the Object to which the key is mapped; null if the key is not
	 *         mapped to any Object in this hashtable.
	 */
	public Object get(String key) {
		return properties.get(key);
	}

	/**
	 * Returns the String to which the specified key is mapped.
	 * 
	 * @param key
	 *            the key associated to the stored Object
	 * 
	 * @return the Object to which the key is mapped; null if the key is not
	 *         mapped to any Object in this hashtable.
	 */
	public String getString(String key) {

		if (!propertiesInitialized) {
			initProperties();
		}

		String property = null;
		String appProperty = null;

		if (midlet != null) {
			appProperty = midlet.getAppProperty(key);
		}

		if (midlet != null && appProperty != null && appProperty.length() > 0) {
			property = appProperty;
		} else {
			property = (String) properties.get(key);
		}

		return property;
	}

	/**
	 * Returns the Object to which the specified key is mapped directly from the
	 * default values. Any overridden settings are ignored. Useful if an
	 * overridden value is erroneous and a proper value is needed. (The default
	 * values are considered to be checked and therefore proper.)
	 * 
	 * @param key
	 *            the key associated to the stored Object
	 * 
	 * @return the Object to which the key is mapped; null if the key is not
	 *         mapped to any Object in this hashtable.
	 */
	public Object getDefaultValue(String key) {

		if (!propertiesInitialized) {
			initProperties();
		}

		return defaultValues.get(key);
	}

	/**
	 * Initializes the Properties by reading values from different
	 * <code>PropertySources</code> in a determined order. This order
	 * determines the way in which values are overridden.
	 */
	void initProperties() {
		// Insert default values
		defaultValues = new DefaultValues();
		defaultValues.insertProperties(properties);

		// Insert/overwrite values from the property file
		PropertySource fileProperties = null;
		if (propertyFileName == null) {
			fileProperties = new PropertyFile(DEFAULT_PROPERTY_FILE);
		} else {
			fileProperties = new PropertyFile(propertyFileName);
		}

		fileProperties.insertProperties(properties);

		propertiesInitialized = true;
	}

}
