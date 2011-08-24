/**
 *  Filename: TwoWayHashtable.java (in org.openbandy.util)
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

package org.openbandy.util;

import java.util.Enumeration;
import java.util.Hashtable;


/**
 * Wrapper for java.util.Hashtable with methods to get objects as well as well
 * as keys by hash.
 * 
 * <br>
 * <br>
 * (c) Copyright P. Bolliger 2007, ALL RIGHTS RESERVED.
 * 
 * @author Philipp Bolliger (philipp@bolliger.name)
 * @version 1.0
 */
public class TwoWayHashtable {

	/* the table for value lookups */
	private Hashtable valueTable;

	/* the table for key lookups */
	private Hashtable keyTable;

	/* Constructor */
	public TwoWayHashtable() {
		keyTable = new Hashtable();
		valueTable = new Hashtable();
	}

	/**
	 * Put an <code>value</code> object into the hashtable using the
	 * <code>key</code> object as reference.
	 * 
	 * @param key
	 *            The key object.
	 * @param value
	 *            The value object.
	 */
	public void put(Object key, Object value) {
		keyTable.put(key, value);
		valueTable.put(value, key);
	}

	/**
	 * Check if the hashtable contains the given key object.
	 * 
	 * @param key
	 *            The key object to check for.
	 * @return True if the object is contained within the hashtable.
	 */
	public boolean containsKey(Object key) {
		return keyTable.containsKey(key);
	}

	/**
	 * Check if the hashtable contains the given value object.
	 * 
	 * @param value
	 *            The value object to check for.
	 * @return True if the object is contained within the hashtable.
	 */
	public boolean containsValue(Object value) {
		return keyTable.contains(value);
	}

	/**
	 * Returns the value objects using the key object as reference.
	 * 
	 * @param key
	 *            The key object.
	 * @return The value object if contained, null else.
	 */
	public Object getValueByKey(Object key) {
		return keyTable.get(key);
	}

	/**
	 * Return an enumeration of all value objects.
	 * 
	 * @return Enumeration of value objects.
	 */
	public Enumeration getValues() {
		return keyTable.elements();
	}

	/**
	 * Return an enumeration of all key objects.
	 * 
	 * @return Enumeration of key objects.
	 */
	public Enumeration getKeys() {
		return valueTable.elements();
	}

	/**
	 * Return the key of the hashtable using the value object.
	 * 
	 * @param value
	 *            Value object.
	 * @return The key object.
	 */
	public Object getKeyByValue(Object value) {
		return valueTable.get(value);
	}

	/**
	 * Remove an element from the hashtable using the key.
	 * 
	 * @param key
	 *            Key object.
	 * @return True if the object could be removed.
	 */
	public Object removeByKey(Object key) {
		Object value = keyTable.remove(key);
		if (value != null) {
			valueTable.remove(value);
		}
		return value;
	}

	/**
	 * Remove an element from the hashtable using the value as key.
	 * 
	 * @param value
	 *            Value object.
	 * @return True if the object could be removed.
	 */
	public Object removeByValue(Object value) {
		Object key = valueTable.remove(value);
		if (key != null) {
			return keyTable.remove(key);
		}
		else {
			return null;
		}
	}

	/**
	 * Removes all elements from the hashtable.
	 */
	public void clear() {
		valueTable.clear();
		keyTable.clear();
	}
}
