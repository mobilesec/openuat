/**
 *  Filename: StartupScreen.java (in org.openbandy.ui)
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

import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Gauge;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.ImageItem;
import javax.microedition.lcdui.StringItem;

import org.openbandy.service.LogService;


/**
 * The startup screen is a simple form that can display a logo image, a gauge to
 * indicate the progress of the startup process along with a short text to
 * inform the user about what is going on.
 * 
 * <br>
 * <br>
 * (c) Copyright Philipp Bolliger 2007, ALL RIGHTS RESERVED.
 * 
 * @author Philipp Bolliger (philipp@bolliger.name)
 * @version 1.0
 * 
 */
public class StartupScreen extends Form {

	/* Gauge to indicate progress of startup */
	private Gauge startupProgressGauge = null;

	private int gaugeLastIndex = 0;

	private StringItem startupProgressText = null;

	/**
	 * Constructor for the StartupScreen
	 * 
	 * @param logo
	 *            Image shown on the startup screen
	 * @param numberOfStepsToBeCompletedDuringStartup
	 *            The number of steps that will be executed during startup
	 */
	public StartupScreen(Image logo, int numberOfStepsToBeCompletedDuringStartup) {
		super("Startup");

		/* initialize the gauge and the startup text */
		startupProgressGauge = new Gauge(null, false, numberOfStepsToBeCompletedDuringStartup, 0);
		startupProgressGauge.setLayout(Gauge.LAYOUT_CENTER | Gauge.LAYOUT_DEFAULT);

		startupProgressText = new StringItem("", "");
		startupProgressText.setLayout(StringItem.LAYOUT_CENTER | StringItem.LAYOUT_DEFAULT);

		/* create the logo image */
		if (logo != null) {
			ImageItem logoItem = new ImageItem("", logo, 0, "");
			logoItem.setLayout(ImageItem.LAYOUT_CENTER | ImageItem.LAYOUT_DEFAULT | ImageItem.LAYOUT_NEWLINE_AFTER);
			this.append(logoItem);
		}

		/* append the gauge and the text item */
		this.append(startupProgressText);
		this.append(startupProgressGauge);
	}

	/**
	 * Shows the actionText at the startup screen and advances the gauge.
	 * 
	 * @param actionText
	 *            Text to be shown to the user on the startup screen
	 */
	public void showActionAtStartupGauge(String actionText) {
		if (gaugeLastIndex < startupProgressGauge.getMaxValue()) {
			startupProgressText.setText(actionText);
			startupProgressGauge.setValue(gaugeLastIndex++);
		}
		else {
			LogService.error(this, "Gauge reached max value", null);
		}
	}
}
