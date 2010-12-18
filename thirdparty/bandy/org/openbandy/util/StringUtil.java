/**
 *  Filename: StringUtil.java (in org.openbandy.util)
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

/**
 * This class provides static helper methods to be used with Strings or that
 * deal with Strings.
 * 
 * <br>
 * <br>
 * (c) Copyright P. Bolliger 2007, ALL RIGHTS RESERVED.
 * 
 * @author Philipp Bolliger (philipp@bolliger.name)
 * @version 1.0
 */
public class StringUtil {

	/**
	 * Returns the name of the class of the object given as parameter without
	 * the prepend package name.
	 * 
	 * @param object
	 *            An instance of any object
	 * @return The short (i.e. without package) class name or the empty string
	 *         if the object is null
	 */
	public static String getShortClassName(Object object) {
		if (object != null) {
			String shortName = object.getClass().getName();
			int beginnOfShortName = shortName.lastIndexOf('.') + 1;
			return shortName.substring(beginnOfShortName, shortName.length());
		}
		return "";
	}
}
