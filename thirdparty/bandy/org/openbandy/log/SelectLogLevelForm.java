/**
 *  Filename: SelectLogLevelForm.java (in org.openbandy.log)
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

package org.openbandy.log;

import java.io.IOException;

import javax.microedition.lcdui.Choice;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.List;

import org.openbandy.service.LogService;
import org.openbandy.ui.BandyDisplayable;


/**
 * This form is used to set the level of logging (i.e. which kind of log
 * messages are logged).
 * 
 * <br>
 * <br>
 * (c) Copyright P. Bolliger 2007, ALL RIGHTS RESERVED.
 * 
 * @author Philipp Bolliger (philipp@bolliger.name)
 * @version 1.0
 */
public class SelectLogLevelForm extends List implements BandyDisplayable, CommandListener {

	/* Commands */
	private Command cmdOk;

	private Command cmdCancel;

	/* Images */
	private Image imgChecked;

	private Image imgUnchecked;

	/** Reference to the MIDlets display */
	private Display display;

	/** Reference to the log canvas */
	private LogImpl logCanvas;

	/**
	 * Create a new select log level form
	 */
	public SelectLogLevelForm() {
		super("Log Level", Choice.IMPLICIT);

		/* compose the GUI */
		cmdOk = new Command("Ok", Command.OK, 1);
		cmdCancel = new Command("Cancel", Command.CANCEL, 1);
		addCommand(cmdOk);
		addCommand(cmdCancel);

		/* Ok is the default command */
		this.setSelectCommand(cmdOk);

		/* load images */
		try {
			imgChecked = Image.createImage("/img/checked.png");
			imgUnchecked = Image.createImage("/img/unchecked.png");
		}
		catch (IOException ioe) {
			LogService.error(this, ioe.getMessage(), ioe);
		}

		/* make myself command listener */
		this.setCommandListener(this);
	}

	/* ******************** Methods for BandyDisplayable ************** */

	/* (non-Javadoc)
	 * @see org.openbandy.ui.BandyDisplayable#show(javax.microedition.lcdui.Display, javax.microedition.lcdui.Displayable)
	 */
	public void show(Display display, Displayable previousDisplayable) {
		this.display = display;
		try {
			this.logCanvas = (LogImpl) previousDisplayable;

			/* append levels, set images correctly */
			for (int i = 0; i < LogLevel.levels.length; i++) {
				if (i == logCanvas.config.getLogLevel()) {
					append(LogLevel.levels[i], imgChecked);
				}
				else {
					append(LogLevel.levels[i], imgUnchecked);
				}
			}
		}
		catch (ClassCastException cce) {
			LogService.error(this, "Must have LogCanvas as previous screen", cce);
		}

		display.setCurrent(this);
	}

	/* ******************** Methods for CommandListener ************** */

	/* (non-Javadoc)
	 * @see javax.microedition.lcdui.CommandListener#commandAction(javax.microedition.lcdui.Command, javax.microedition.lcdui.Displayable)
	 */
	public void commandAction(Command c, Displayable d) {
		if (c == cmdCancel) {
			display.setCurrent(logCanvas);
		}
		else if (c == cmdOk) {
			try {
				/* set new log level */
				logCanvas.config.setLogLevel(getSelectedIndex());

				/* reset log canvas and show it */
				logCanvas.resetCanvas();
				display.setCurrent(logCanvas);
			}
			catch (Exception e) {
				LogService.error(this, e.getMessage(), e);
			}
		}
	}
}
