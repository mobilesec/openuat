/**
 * Filename: PreferencesService.java (in org.openbandy.service)
 * This file is part of the BandyBasics project.
 *
 * www.BandyBasics.org
 */

package org.openbandy.service;

import javax.microedition.rms.InvalidRecordIDException;
import javax.microedition.rms.RecordStoreException;
import javax.microedition.rms.RecordStoreFullException;
import javax.microedition.rms.RecordStoreNotOpenException;

import org.openbandy.pref.InvalidValueException;
import org.openbandy.pref.Preferences;
import org.openbandy.pref.UnknownPreferenceException;


/**
 * This is a helper class to simplify (and abstract) the usage of the preference
 * class by providing static methods.
 * 
 * Note: Unlike as within the Preferences class itself, we are allowed to use
 * the LogService here, as the Log itself does not use the PreferencesService
 * but rather Preferences directly.
 * 
 * <br>
 * <br>
 * (c) Copyright Philipp Bolliger 2008, ALL RIGHTS RESERVED.
 * 
 * @author Philipp Bolliger (philipp@bolliger.name)
 * @version 1.0
 * @see org.openbandy.pref.Preferences
 * 
 */
public class PreferencesService extends Service {

	/**
	 * Add a new preference to the persistent store with the given name. Make
	 * sure that you use a unique string.
	 * 
	 * @param name
	 *            The (unique) string used to identify the preference.
	 * @param screenModifiable
	 *            screenModifiable if true, the preference will appear in the
	 *            preferences form screen
	 */
	public static void add(String name, boolean screenModifiable) {
		try {
			Preferences.add(name, screenModifiable);
		}
		catch (RecordStoreNotOpenException rsnoe) {
			LogService.error("PreferencesService", rsnoe.getMessage(), rsnoe);
		}
		catch (RecordStoreFullException rsfe) {
			LogService.error("PreferencesService", rsfe.getMessage(), rsfe);
		}
		catch (RecordStoreException rse) {
			LogService.error("PreferencesService", rse.getMessage(), rse);
		}
	}

	/**
	 * Remove the preference with the given name from the persistent store.
	 * 
	 * @param name
	 *            The (unique) string used to identify the preference.
	 */
	public static void remove(String name) {
		try {
			Preferences.remove(name);
		}
		catch (InvalidRecordIDException ire) {
			LogService.error("PreferencesService", ire.getMessage(), ire);
		}
		catch (RecordStoreNotOpenException rsnoe) {
			LogService.error("PreferencesService", rsnoe.getMessage(), rsnoe);
		}
		catch (UnknownPreferenceException upe) {
			LogService.error("PreferencesService", upe.getMessage(), upe);
		}
		catch (RecordStoreException rse) {
			LogService.error("PreferencesService", rse.getMessage(), rse);
		}
	}

	/**
	 * Reads the String preference with the given name from the persistent
	 * store.
	 * 
	 * @param name
	 *            The (unique) name description of the stored preference value
	 * @return The stored value or the empty string if no string value is stored
	 *         with the given name
	 */
	public static String getValue(String name) {
		try {
			return Preferences.getValue(name);
		}
		catch (UnknownPreferenceException upe) {
			LogService.error("PreferencesService", upe.getMessage(), upe);
			return "";
		}
	}

	/**
	 * Reads the int preference with the given name from the persistent store.
	 * 
	 * @param name
	 *            The (unique) name description of the stored preference value
	 * @return The stored value or <code>0</code> if no int value is stored
	 *         with the given name
	 */
	public static int getIntValue(String name) {
		try {
			return Preferences.getIntValue(name);
		}
		catch (InvalidValueException ive) {
			LogService.error("PreferencesService", ive.getMessage(), ive);
		}
		catch (UnknownPreferenceException upe) {
			LogService.error("PreferencesService", upe.getMessage(), upe);
		}
		return 0;
	}

	/**
	 * Reads the boolean preference with the given name from the persistent
	 * store.
	 * 
	 * @param name
	 *            The (unique) name description of the stored preference value
	 * @return The stored value or <code>false</code> if no boolean value is
	 *         stored with the given name
	 */
	public static boolean getBooleanValue(String name) {
		try {
			return Preferences.getBooleanValue(name);
		}
		catch (InvalidValueException ive) {
			LogService.error("PreferencesService", ive.getMessage(), ive);
		}
		catch (UnknownPreferenceException upe) {
			LogService.error("PreferencesService", upe.getMessage(), upe);
		}
		return false;
	}

	/**
	 * Set the value of the preference that is identified with the given name
	 * string to the given value. If the preference does not already exist, it
	 * will be added, however as not screen modifiable.
	 * 
	 * @param name
	 *            The (unique) name description of the stored preference value
	 * @param value
	 *            The value to be set
	 */
	public static void setValue(String name, String value) {
		try {
			Preferences.setValue(name, value);
		}
		catch (RecordStoreFullException rsfe) {
			LogService.error("PreferencesService", rsfe.getMessage(), rsfe);
		}
		catch (RecordStoreNotOpenException rsnoe) {
			LogService.error("PreferencesService", rsnoe.getMessage(), rsnoe);
		}
		catch (InvalidValueException ive) {
			LogService.error("PreferencesService", ive.getMessage(), ive);
		}
		catch (RecordStoreException rse) {
			LogService.error("PreferencesService", rse.getMessage(), rse);
		}
	}

	/**
	 * Set the value of the preference that is identified with the given name
	 * string to the given value. If the preference does not already exist, it
	 * will be added, however as not screen modifiable.
	 * 
	 * @param name
	 *            The (unique) name description of the stored preference value
	 * @param value
	 *            The int value to be set
	 */
	public static void setIntValue(String name, int value) {
		try {
			Preferences.setIntValue(name, value);
		}
		catch (RecordStoreFullException rsfe) {
			LogService.error("PreferencesService", rsfe.getMessage(), rsfe);
		}
		catch (RecordStoreNotOpenException rsnoe) {
			LogService.error("PreferencesService", rsnoe.getMessage(), rsnoe);
		}
		catch (InvalidValueException ive) {
			LogService.error("PreferencesService", ive.getMessage(), ive);
		}
		catch (RecordStoreException rse) {
			LogService.error("PreferencesService", rse.getMessage(), rse);
		}
	}

	/**
	 * Set the value of the preference that is identified with the given name
	 * string to the given value. If the preference does not already exist, it
	 * will be added, however as not screen modifiable.
	 * 
	 * @param name
	 *            The (unique) name description of the stored preference value
	 * @param value
	 *            The boolean value to be set
	 */
	public static void setBooleanValue(String name, boolean value) {
		try {
			Preferences.setBooleanValue(name, value);
		}
		catch (RecordStoreFullException rsfe) {
			LogService.error("PreferencesService", rsfe.getMessage(), rsfe);
		}
		catch (RecordStoreNotOpenException rsnoe) {
			LogService.error("PreferencesService", rsnoe.getMessage(), rsnoe);
		}
		catch (InvalidValueException ive) {
			LogService.error("PreferencesService", ive.getMessage(), ive);
		}
		catch (RecordStoreException rse) {
			LogService.error("PreferencesService", rse.getMessage(), rse);
		}
	}

}
