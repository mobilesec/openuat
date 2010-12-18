/**
 *  Filename: ProgressAlert.java (in org.openbandy.ui)
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

package org.openbandy.ui;

import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Gauge;

import org.openbandy.service.LogService;


/**
 * The ProgressAlert class provides an alert that contains a gauge to indicate
 * the progress of a task that might run for possibly long time.
 * 
 * <br>
 * <br>
 * (c) Copyright Philipp Bolliger 2007, ALL RIGHTS RESERVED.
 * 
 * @author Philipp Bolliger (philipp@bolliger.name)
 * @version 1.0
 * 
 */
public class ProgressAlert extends Alert implements BandyDisplayable {

	private Display display;

	private Displayable previousDisplayable;

	private Gauge progressGauge;

	private int maxValue = 0;

	/**
	 * Create a new ProgressAlert
	 * 
	 * @param text
	 *            Indicate what task is taking this much time
	 * @param type
	 *            The type of the alert (info, error etc)
	 * @param maxValue
	 *            The maximum number of steps the current task will take to
	 *            complete
	 */
	public ProgressAlert(String text, AlertType type, int maxValue) {
		super(null, text, null, type);
		this.maxValue = maxValue;

		/* set a very long timeout (20 minutes) */
		setTimeout(20 * 60 * 1000);

		/* create the gauge that shows the process */
		progressGauge = new Gauge(null, false, maxValue, Gauge.INCREMENTAL_UPDATING);

		/* set the gauge as indicator */
		setIndicator(progressGauge);
	}

	/**
	 * Make the progress alert the current screen, i.e., show it to the user.
	 * 
	 */
	public void show() {
		if (display != null) {
			display.setCurrent(this);
		}
		else {
			LogService.error(this, "Display not set", null);
		}
	}

	/**
	 * Advance the gauge indicator by one (until it has not already reached the
	 * maximum value). If the maximum value is reached, the progress alert will
	 * be dismissed.
	 * 
	 */
	public void advance() {
		/* increase gauge value */
		progressGauge.setValue(progressGauge.getValue() + 1);

		/* if the maximum value is reached, dismiss the progress alert */
		if (progressGauge.getValue() == (maxValue - 1)) {
			dismiss();
		}
	}

	/**
	 * Release the alert and make the previous screen current again.
	 * 
	 */
	public void dismiss() {
		if ((display != null) && (previousDisplayable != null)) {
			display.setCurrent(previousDisplayable);
		}
		else {
			LogService.error(this, "Display or previous displayable not set", null);
		}
	}

	/* ******************* Methods for BandyDisplayable *************** */

	/* (non-Javadoc)
	 * @see org.openbandy.ui.BandyDisplayable#show(javax.microedition.lcdui.Display, javax.microedition.lcdui.Displayable)
	 */
	public void show(Display display, Displayable previousDisplayable) {
		this.display = display;
		this.previousDisplayable = previousDisplayable;
		display.setCurrent(this);
	}
}
