/**
 *  Filename: LogStorageViewerMIDlet.java (in org.openbandy.log)
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

import javax.microedition.lcdui.Display;
import javax.microedition.midlet.MIDlet;
import javax.microedition.midlet.MIDletStateChangeException;


/**
 * The LogStorageViewerMIDlet opens and shows the log for viewing purposes only,
 * i.e., it is not possible to add log messages. Instead all log messages that
 * are stored in the RMS are shown in the log canvas.
 * 
 * As all MIDlets of the same MIDlet Suite have access to the same RMS records,
 * this allows to view the log messages of your main MIDlet even after it
 * crashed unexpectedly.
 * 
 * <br>
 * <br>
 * (c) Copyright Philipp Bolliger 2007, ALL RIGHTS RESERVED.
 * 
 * @author Philipp Bolliger (philipp@bolliger.name)
 * @version 1.0
 * 
 */
public class LogStorageViewerMIDlet extends MIDlet {

	/* (non-Javadoc)
	 * @see javax.microedition.midlet.MIDlet#startApp()
	 */
	protected void startApp() throws MIDletStateChangeException {

		/* get the display and set the log service as the current screen */
		Display display = Display.getDisplay(this);

		/* create a new LogCanvas and set MIDlet and Display */
		LogImpl logCanvas = new LogImpl(LogConfiguration.getPersistentLogViewerConfig(true));
		logCanvas.setMIDlet(this);
		logCanvas.show(display, null);

		/* set the log canvas as the current screen */
		display.setCurrent(logCanvas);

		/* read the log messages from the RMS and show them in canvas */
		logCanvas.displayStoredLogMessages();
	}

	/* (non-Javadoc)
	 * @see javax.microedition.midlet.MIDlet#pauseApp()
	 */
	protected void pauseApp() {
		/* do nothing */
	}

	/* (non-Javadoc)
	 * @see javax.microedition.midlet.MIDlet#destroyApp(boolean)
	 */
	protected void destroyApp(boolean arg0) throws MIDletStateChangeException {
		/* do nothing */
	}

}
