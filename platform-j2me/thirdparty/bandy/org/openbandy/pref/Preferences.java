/**
 *  Filename: Preferences.java (in org.openbandy.pref)
 *  This file is part of the OpenBandy project.
 * 
 *  OpenBandy is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  any later version.
 *
 *  OpenBandy is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with OpenBandy. If not, see <http://www.gnu.org/licenses/>.
 *
 *
 * (c) Copyright Philipp Bolliger 2007, ALL RIGHTS RESERVED.
 * 
 *  www.openbandy.org
 */

package org.openbandy.pref;

import java.util.Enumeration;
import java.util.Hashtable;

import javax.microedition.rms.InvalidRecordIDException;
import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;
import javax.microedition.rms.RecordStoreFullException;
import javax.microedition.rms.RecordStoreNotOpenException;


/**
 * The Preferences class provides a simple way to persistently store key
 * (String) value (String, int, boolean) pairs. For this purpose, it uses the
 * RMS in which it creates a record store named 'Preferences'.
 * 
 * Note that the whole class is forbidden to use the logger as the log itself
 * uses Preferences!
 * 
 * <br>
 * <br>
 * (c) Copyright P. Bolliger 2007, ALL RIGHTS RESERVED.
 * 
 * @author Philipp Bolliger (philipp@bolliger.name)
 * @version 1.0
 */
public final class Preferences {

	/** The name of the record store that is used to store the preferences names */
	private static final String PREFERENCES_RECORD_STORE_NAME = "Preferences";

	/**
	 * Character used to delimit the name, the value and the boolean indicator
	 * <code>screenModifiable</code> when storing one string to the
	 * PREF_NAMES_RECORD_STORE_NAME record store
	 */
	protected static final char DELIMITER = ':';

	/**
	 * Record store used to save the name, the value and the boolean indicator
	 * <code>screenModifiable</code> of a preference, delimited by
	 * <code>ID_DELIMITER</code>.
	 */
	private static RecordStore preferencesRecordStore;

	/** The preferences: name (String) -> preference (Preference) */
	private static Hashtable preferences = new Hashtable();

	/**
	 * By default, preferences are not screen modifiable, i.e., they won't be
	 * shown in the preferences screen
	 */
	private static final boolean DEFAULT_SCREEN_MODIFIABLE = false;

	/* init */
	static {
		try {
			/* open the record store */
			preferencesRecordStore = RecordStore.openRecordStore(PREFERENCES_RECORD_STORE_NAME, true);

			/* load existing preferences from the RMS */
			for (int id = 1; id < preferencesRecordStore.getNextRecordID(); id++) {
				try {
					/* read the record store entry into a string */
					byte[] record = preferencesRecordStore.getRecord(id);
					String recordStoreEntry = new String(record);

					/* create new preference and put it into hashtable */
					Preference preference = new Preference(id, recordStoreEntry);
					preferences.put(preference.name, preference);
				}
				catch (InvalidRecordIDException irie) {
					/* ignore */
				}
			}
		}
		catch (Exception e) {
			/* ignore */
		}
	}

	/**
	 * Add a preference to the RMS. The preference is identified with the name
	 * parameter. If the preference should be modifiable by the user, i.e.,
	 * appear in the GUI on the preferences form, the parameter screenModifiable
	 * must be true. The preference will not have a value after being added.
	 * 
	 * @param name
	 *            The unique identifier of the preference (make sure it is
	 *            unique yourself)
	 * @param screenModifiable
	 *            if true, the preference will appear in the PreferencesForm
	 *            screen (@see PreferencesForm)
	 * @throws RecordStoreNotOpenException
	 * @throws RecordStoreFullException
	 * @throws RecordStoreException
	 */
	public static void add(String name, boolean screenModifiable) throws RecordStoreNotOpenException, RecordStoreFullException, RecordStoreException {
		/* check if not already existing */
		if (!preferences.containsKey(name)) {
			/* create the preference */
			Preference preference = new Preference(name, "", screenModifiable);

			/* add the preference to record store */
			byte[] entry = preference.getBytes();
			preference.id = preferencesRecordStore.addRecord(entry, 0, entry.length);

			/* put newly added preference and id in hashtables */
			preferences.put(name, preference);
		}
	}

	/**
	 * Remove the preference with the given name from the persistent store.
	 * 
	 * @param name
	 *            The (unique) string used to identify the preference.
	 * @throws UnknownPreferenceException
	 *             If the preference is not known in the store
	 * @throws RecordStoreException
	 * @throws InvalidRecordIDException
	 * @throws RecordStoreNotOpenException
	 */
	public static void remove(String name) throws UnknownPreferenceException, RecordStoreException, InvalidRecordIDException, RecordStoreNotOpenException {
		if (preferences.containsKey(name)) {
			/* get preference from hashtable */
			Preference preference = (Preference) preferences.get(name);

			/* delete the record */
			preferencesRecordStore.deleteRecord(preference.id);

			/* remove the preference from the hashtable */
			preferences.remove(name);
		}
		else {
			throw new UnknownPreferenceException("Can not remove '" + name + "', unknown preference.");
		}
	}

	/**
	 * Reads the string preference with the given name from the persistent
	 * store.
	 * 
	 * @param name
	 *            The (unique) name description of the preference
	 * @return The according, stored string value
	 * @throws InvalidValueException
	 *             Thrown if the preference associated with the given name is
	 *             not a string value
	 * @throws UnknownPreferenceException
	 *             Thrown if no preference value is stored with the given name
	 */
	public static String getValue(String name) throws UnknownPreferenceException {
		if (preferences.containsKey(name)) {
			/* get preference from hashtable and return its value */
			Preference preference = (Preference) preferences.get(name);
			return preference.value;
		}
		else {
			throw new UnknownPreferenceException("Can not get value for '" + name + "', unknown preference.");
		}
	}

	/**
	 * Reads the int preference with the given name from the persistent store.
	 * 
	 * @param name
	 *            The (unique) name description of the preference
	 * @return The according, stored int value
	 * @throws InvalidValueException
	 *             Thrown if the preference associated with the given name is
	 *             not a int value
	 * @throws UnknownPreferenceException
	 *             Thrown if no preference value is stored with the given name
	 */
	public static int getIntValue(String name) throws InvalidValueException, UnknownPreferenceException {
		String value = getValue(name);
		try {
			return Integer.parseInt(value);
		}
		catch (NumberFormatException e) {
			throw new InvalidValueException("Value is not an integer: " + value);
		}
	}

	/**
	 * Reads the boolean preference with the given name from the persistent
	 * store.
	 * 
	 * @param name
	 *            The (unique) name description of the preference
	 * @return The according, stored boolean value
	 * @throws InvalidValueException
	 *             Thrown if the preference associated with the given name is
	 *             not a boolean value
	 * @throws UnknownPreferenceException
	 *             Thrown if no preference value is stored with the given name
	 */
	public static boolean getBooleanValue(String name) throws InvalidValueException, UnknownPreferenceException {
		String value = getValue(name);
		if (value.equals(String.valueOf(true))) {
			return true;
		}
		else if (value.equals(String.valueOf(false))) {
			return false;
		}
		else {
			throw new InvalidValueException("Value is not a boolean: " + value);
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
	 *            The string value to be set
	 * @throws InvalidValueException
	 *             Thrown if the preference associated with the given name is
	 *             not a string value
	 * @throws RecordStoreFullException
	 * @throws RecordStoreNotOpenException
	 * @throws RecordStoreException
	 */
	public static void setValue(String name, String value) throws InvalidValueException, RecordStoreFullException, RecordStoreNotOpenException, RecordStoreException {
		/* ensure that the value does not contain the DELIMITER char */
		if (value.indexOf(DELIMITER) != -1) {
			throw new InvalidValueException("Value must not contain '" + DELIMITER + "'");
		}

		Preference preference = null;

		/* if the preference does not already exists, add it */
		if (!preferences.containsKey(name)) {
			add(name, DEFAULT_SCREEN_MODIFIABLE);

			/* create the preference */
			preference = new Preference(name, "", DEFAULT_SCREEN_MODIFIABLE);

			/* add the preference to record store */
			byte[] entry = preference.getBytes();
			preference.id = preferencesRecordStore.addRecord(entry, 0, entry.length);

			/* put newly added preference and id in hashtables */
			preferences.put(name, preference);
		}
		else {
			/* get preference from hashtable */
			preference = (Preference) preferences.get(name);
		}

		/* set new value */
		preference.value = value;

		/* update record */
		byte[] entry = preference.getBytes();
		preferencesRecordStore.setRecord(preference.id, entry, 0, entry.length);
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
	 * @throws InvalidValueException
	 *             Thrown if the preference associated with the given name is
	 *             not an int value
	 * @throws RecordStoreFullException
	 * @throws RecordStoreNotOpenException
	 * @throws RecordStoreException
	 */
	public static void setIntValue(String name, int value) throws InvalidValueException, RecordStoreFullException, RecordStoreNotOpenException, RecordStoreException {
		setValue(name, Integer.toString(value));
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
	 * @throws InvalidValueException
	 *             Thrown if the preference associated with the given name is
	 *             not a boolean value
	 * @throws RecordStoreFullException
	 * @throws RecordStoreNotOpenException
	 * @throws RecordStoreException
	 */
	public static void setBooleanValue(String name, boolean value) throws InvalidValueException, RecordStoreFullException, RecordStoreNotOpenException, RecordStoreException {
		setValue(name, String.valueOf(value));
	}

	/**
	 * Returns an enumeration of all preferences currently stored.
	 * 
	 * @return Enumeration of all preferences currently stored
	 */
	public static Enumeration preferences() {
		return preferences.elements();
	}

}
