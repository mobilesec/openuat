/**
 *  Filename: UnknownPreferenceException.java (in org.openbandy.pref)
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
 * The UnknownPreferenceException shall be thrown whenever the user tries to
 * access a preference that is unknown to the system, i.e., that has not been
 * stored before.
 * 
 * <br>
 * <br>
 * (c) Copyright P. Bolliger 2007, ALL RIGHTS RESERVED.
 * 
 * @author Philipp Bolliger (philipp@bolliger.name)
 * @version 1.0
 */
public class UnknownPreferenceException extends Exception {

	/**
	 * The UnknownPreferenceException shall be thrown whenever the user tries to
	 * access a preference that is unknown to the system, i.e., that has not
	 * been stored before.
	 * 
	 * @param message
	 *            Detailed message which describes the exception
	 */
	public UnknownPreferenceException(String message) {
		super(message);
	}

}
