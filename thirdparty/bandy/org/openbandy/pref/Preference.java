/**
 *  Filename: Preference.java (in org.openbandy.pref)
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

/**
 * 
 * The class represents a preference. Thus, a preference is a simple key (i.e.,
 * name) value pair, both of type String. In order to store a preference in the
 * device's RMS, the getByte() method returns a byte array in which a preference
 * is represented according to the following format:
 * 
 * <code>name:value:screenModifiable</code>
 * 
 * <br>
 * <br>
 * (c) Copyright P. Bolliger 2007, ALL RIGHTS RESERVED.
 * 
 * @author Philipp Bolliger (philipp@bolliger.name)
 * @version 1.0
 * @see PreferencesForm
 */
public class Preference {

	/** The RMS id, 0 if never stored to RMS */
	protected int id = 0;

	protected String name = "";

	protected String value = "";

	protected boolean isScreenModifiable = false;

	/**
	 * Create a new preference
	 * 
	 * @param name
	 *            The name (i.e., the unique identifier)
	 * @param value
	 *            The value of the preference
	 * @param isScreenModifiable
	 *            Flag that indicates whether this preference shall be editable
	 *            by the user (from within the PreferencesForm)
	 */
	protected Preference(String name, String value, boolean isScreenModifiable) {
		this.name = name;
		this.value = value;
		this.isScreenModifiable = isScreenModifiable;
	}

	/**
	 * Create a new preference from a string. The string must be formatted as:
	 * 
	 * <code>name:value:screenModifiable</code>
	 * 
	 * @param id
	 *            The id of the preference in the RMS record
	 * @param prefString
	 *            String representation of the preference
	 */
	protected Preference(int id, String prefString) {
		this.id = id;

		int firstDelimiter = prefString.indexOf(Preferences.DELIMITER);
		int secondDelimiter = prefString.lastIndexOf(Preferences.DELIMITER);

		/* name and value */
		this.name = prefString.substring(0, firstDelimiter);
		this.value = prefString.substring(firstDelimiter + 1, secondDelimiter);

		/* screenModifiable */
		this.isScreenModifiable = false;
		String isScreenModifiableString = prefString.substring(secondDelimiter + 1);
		if (isScreenModifiableString.equals(String.valueOf(true))) {
			this.isScreenModifiable = true;
		}
	}

	/**
	 * Returns a byte array in which a preference is represented according to
	 * the following format:
	 * 
	 * <code>name:value:screenModifiable</code>
	 * 
	 * @return Byte array representation of the preference
	 */
	protected byte[] getBytes() {
		return (name + Preferences.DELIMITER + value + Preferences.DELIMITER + String.valueOf(isScreenModifiable)).getBytes();
	}
}
