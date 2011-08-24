/**
 *  Filename: CollectionsUtil.java (in org.openbandy.util)
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
import java.util.Vector;

import org.openbandy.service.LogService;


/**
 * The CollectionsUtil class provides some helper methods for the
 * java.util.Vector class.
 * 
 * <br>
 * <br>
 * (c) Copyright P. Bolliger 2007, ALL RIGHTS RESERVED.
 * 
 * @author Philipp Bolliger (philipp@bolliger.name)
 * @version 1.5
 */
public class CollectionsUtil {

	/**
	 * Reverses the order of the elements in the vector.
	 * 
	 * @param in
	 *            Vector to reverse.
	 * @return The resulting vector.
	 */
	public static Vector reverseOrder(Vector in) {
		int inSize = in.size();
		Vector out = new Vector(inSize);
		out.setSize(inSize);

		int outIndex = inSize - 1;
		Enumeration elements = in.elements();
		while (elements.hasMoreElements()) {
			out.setElementAt(elements.nextElement(), outIndex--);
		}
		return out;
	}

	/**
	 * This method can be used to check the vector for existence of a certain
	 * String.
	 * 
	 * @param vector
	 *            The vector to sift through.
	 * @param lookupString
	 *            The String to look after.
	 * @return True if the vector contains the string.
	 */
	public static boolean vectorContainsString(Vector vector, String lookupString) {
		Enumeration elements = vector.elements();
		while (elements.hasMoreElements()) {
			try {
				String testString = (String) elements.nextElement();
				if (testString.equals(lookupString)) {
					return true;
				}
			}
			catch (ClassCastException cce) {
			}
		}
		return false;
	}

	/**
	 * Copies the content of Vector in to the Vector out.
	 * 
	 * @param in
	 *            The vector to copy from
	 * @param out
	 *            The vector to copy to
	 */
	public static void copy(Vector in, Vector out) {
		if ((in == null) || (out == null)) {
			LogService.warn(CollectionsUtil.class.getName(), "Can not copy, vector is null");
		}
		else {
			for (Enumeration elements = in.elements(); elements.hasMoreElements();) {
				Object o = elements.nextElement();
				out.addElement(o);
			}
		}
	}

}
