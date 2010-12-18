/**
 *  Filename: Service.java (in org.openbandy.service)
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

package org.openbandy.service;

/**
 * A Service represents a high level abstraction of a framework functionality.
 * Typically, a service implementation should provide static methods in order to
 * make the usage as simple and easy as possible. Moreover, a service should
 * take car of handling exceptions and setting default values wherever possible
 * or needed.
 * 
 * <br>
 * <br>
 * (c) Copyright P. Bolliger 2007, ALL RIGHTS RESERVED.
 * 
 * @author Philipp Bolliger (philipp@bolliger.name)
 * @version 1.0
 */
public abstract class Service {

	/**
	 * Determine whether the current VM is a Java Microedition by reading the
	 * according property.
	 * 
	 * @return True if running on microedition
	 */
	public static boolean runningOnMicroEdition() {
		String conf = System.getProperty("microedition.configuration");
		return (conf != null);
	}

	/**
	 * Determine whether the current VM is Sun's Wireless Toolkit Emulator by
	 * reading the according property.
	 * 
	 * @return True if running on Sun WTK
	 */
	public static boolean runningOnSunWTK() {
		String platform = System.getProperty("microedition.platform");
		return platform.equals("SunMicrosystems_wtk");
	}
}
