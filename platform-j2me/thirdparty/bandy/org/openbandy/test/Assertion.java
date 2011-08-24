/**
 *  Filename: Assertion.java (in org.openbandy.test)
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

package org.openbandy.test;

import org.openbandy.service.LogService;


/**
 * This class provides some static methods to assert conditions as equality or
 * not null. In the case of failure, a log message will be written.
 * 
 * <br>
 * <br>
 * (c) Copyright P. Bolliger 2007, ALL RIGHTS RESERVED.
 * 
 * @author Philipp Bolliger (philipp@bolliger.name)
 * @version 1.0
 */
public class Assertion {

	/**
	 * Check the given object reference for <code>null</code>.
	 * 
	 * @param object
	 *            Object reference to check.
	 * @return True if the object is NOT NULL.
	 */
	public static boolean notNull(Object object) {
		if (object == null) {
			LogService.warn(Assertion.class, "Object is null");
			return false;
		}
		return true;
	}

	/**
	 * Check the given int values to be equal.
	 * 
	 * @param int1
	 *            The first int to compare with.
	 * @param int2
	 *            The int to compare with int1.
	 * @return True if int1 is equal with int2.
	 */
	public static boolean equal(int int1, int int2) {
		if (int1 != int2) {
			LogService.warn(Assertion.class, "Values are not equal");
			return false;
		}
		return true;
	}

}
