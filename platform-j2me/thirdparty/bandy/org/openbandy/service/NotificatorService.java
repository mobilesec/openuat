/**
 *  Filename: NotificatorService.java (in org.openbandy.service)
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

import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Display;

import org.openbandy.ui.MainMenu;


/**
 * Provides static methods to notify the user via an alert.
 * 
 * <br>
 * <br>
 * (c) Copyright P. Bolliger 2007, ALL RIGHTS RESERVED.
 * 
 * @author Philipp Bolliger (philipp@bolliger.name)
 * @version 1.0
 */
public final class NotificatorService extends Service {

	/**
	 * Create a confirmation alert with the given message and show it to the
	 * user (no timeout).
	 * 
	 * @param notificationMessage
	 *            The message that should be displayed
	 * @return True if the alert could be shown
	 */
	public static boolean confirmationAlert(String notificationMessage) {
		return showAlertAlert(AlertType.CONFIRMATION, Alert.FOREVER, notificationMessage);
	}

	/**
	 * Create a confirmation alert with the given message and show it to the
	 * user for <code>timeout</code> seconds.
	 * 
	 * @param notificationMessage
	 *            The message that should be displayed
	 * @param timeout
	 *            Number of seconds for which the alert will be shown
	 * @return True if the alert could be shown
	 */
	public static boolean confirmationAlert(String notificationMessage, int timeout) {
		return showAlertAlert(AlertType.CONFIRMATION, timeout * 1000, notificationMessage);
	}

	/**
	 * Create an error alert with the given message and show it to the user (no
	 * timeout).
	 * 
	 * @param notificationMessage
	 *            The message that should be displayed
	 */
	public static boolean errorAlert(String notificationMessage) {
		return showAlertAlert(AlertType.ERROR, Alert.FOREVER, notificationMessage);
	}

	/**
	 * Create an error alert with the given message and show it to the user for
	 * <code>timeout</code> seconds.
	 * 
	 * @param notificationMessage
	 *            The message that should be displayed
	 * @param timeout
	 *            Number of seconds for which the alert will be shown
	 * @return True if the alert could be shown
	 */
	public static boolean errorAlert(String notificationMessage, int timeout) {
		return showAlertAlert(AlertType.ERROR, timeout * 1000, notificationMessage);
	}

	/**
	 * Create an info alert with the given message and show it to the user (no
	 * timeout).
	 * 
	 * @param notificationMessage
	 *            The message that should be displayed
	 * @return True if the alert could be shown
	 */
	public static boolean infoAlert(String notificationMessage) {
		return showAlertAlert(AlertType.INFO, Alert.FOREVER, notificationMessage);
	}

	/**
	 * Create an info alert with the given message and show it to the user for
	 * <code>timeout</code> seconds.
	 * 
	 * @param notificationMessage
	 *            The message that should be displayed
	 * @param timeout
	 *            Number of seconds for which the alert will be shown
	 * @return True if the alert could be shown
	 */
	public static boolean infoAlert(String notificationMessage, int timeout) {
		return showAlertAlert(AlertType.INFO, timeout * 1000, notificationMessage);
	}

	/**
	 * Create an alarm alert with the given message and show it to the user (no
	 * timeout).
	 * 
	 * @param notificationMessage
	 *            The message that should be displayed
	 * @return True if the alert could be shown
	 */
	public static boolean alarmAlert(String notificationMessage) {
		return showAlertAlert(AlertType.ALARM, Alert.FOREVER, notificationMessage);
	}

	/**
	 * Create an alarm alert with the given message and show it to the user for
	 * <code>timeout</code> seconds.
	 * 
	 * @param notificationMessage
	 *            The message that should be displayed
	 * @param timeout
	 *            Number of seconds for which the alert will be shown
	 * @return True if the alert could be shown
	 */
	public static boolean alarmAlert(String notificationMessage, int timeout) {
		return showAlertAlert(AlertType.ALARM, timeout * 1000, notificationMessage);
	}

	/**
	 * Create a warning alert with the given message and show it to the user (no
	 * timeout).
	 * 
	 * @param notificationMessage
	 *            The message that should be displayed
	 * @return True if the alert could be shown
	 */
	public static boolean warningAlert(String notificationMessage) {
		return showAlertAlert(AlertType.WARNING, Alert.FOREVER, notificationMessage);
	}

	/**
	 * Create a warning alert with the given message and show it to the user for
	 * <code>timeout</code> seconds.
	 * 
	 * @param notificationMessage
	 *            The message that should be displayed
	 * @param timeout
	 *            Number of seconds for which the alert will be shown
	 * @return True if the alert could be shown
	 */
	public static boolean warningAlert(String notificationMessage, int timeout) {
		return showAlertAlert(AlertType.WARNING, timeout * 1000, notificationMessage);
	}

	private static boolean showAlertAlert(AlertType alertType, int timeout, String notificationMessage) {
		Alert alert = new Alert("", notificationMessage, null, alertType);
		alert.setTimeout(timeout);

		Display display = MainMenu.display;
		if (display != null) {
			display.setCurrent(alert);
			return true;
		}
		else {
			LogService.warn("Notificator", "No display available");
		}
		return false;
	}
}