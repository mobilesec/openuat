/**
 *  Filename: LogStorage.java (in org.openbandy.log)
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

import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.rms.InvalidRecordIDException;
import javax.microedition.rms.RecordEnumeration;
import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;
import javax.microedition.rms.RecordStoreNotOpenException;

import org.openbandy.ui.ProgressAlert;


/**
 * This class provides methods to store log messages persistently using the
 * devices RMS.
 * 
 * 
 * <br>
 * <br>
 * (c) Copyright P. Bolliger 2007, ALL RIGHTS RESERVED.
 * 
 * @author Philipp Bolliger (philipp@bolliger.name)
 * @version 1.0
 */
public class LogStorage {

	/** The name of the record store holding the persistent log */
	private static final String RECORD_STORE_NAME = "LogMessages";

	/** Reference to the log canvas */
	private LogImpl logCanvas;

	/** The record store where messages are persistently stored */
	private RecordStore logRecordStore;

	/**
	 * Create a new LogStorage, i.e., open a record store in the RMS.
	 * 
	 * @param logCanvas
	 *            Reference to the log canvas
	 */
	public LogStorage(LogImpl logCanvas) {
		this.logCanvas = logCanvas;

		try {
			/* open the record store where log message are stored */
			logRecordStore = RecordStore.openRecordStore(RECORD_STORE_NAME, true);
		}
		catch (Exception e) {
			logCanvas.error(this, e.getMessage(), e);
		}
	}

	/* ************** Methods of LogStorage *************** */

	/**
	 * Store a log message with any given log level to the RMS.
	 * 
	 * @param logMessage
	 *            The message to store
	 * @return True if the messages was stored in the RMS
	 */
	protected void storeLogMessage(String logMessage) {
		try {
			/* store the log message in a new record */
			byte bytes[] = logMessage.getBytes();
			logRecordStore.addRecord(bytes, 0, bytes.length);
		}
		catch (Exception e) {
			if (logCanvas != null) {
				logCanvas.error(this, e.getMessage(), e);
			}
		}
	}

	/**
	 * Delete all persistently stored log messages.
	 */
	protected void clearStoredMessages() {
		try {
			/* close and delete log messages record store */
			logRecordStore.closeRecordStore();
			RecordStore.deleteRecordStore(LogStorage.RECORD_STORE_NAME);

			/* reopen */
			logRecordStore = RecordStore.openRecordStore(RECORD_STORE_NAME, true);

			if (logCanvas != null) {
				logCanvas.info(this, "Cleared persistent log messages");
			}
		}
		catch (Exception e) {
			if (logCanvas != null) {
				logCanvas.error(this, e.getMessage(), e);
			}
		}
	}

	/**
	 * Print all stored log messages to the associated log canvas.
	 */
	protected void displayStoredLogMessagesOnCanvas() {
		try {
			/* read to log messages */
			RecordEnumeration re = logRecordStore.enumerateRecords(null, null, false);
			if (!re.hasNextElement()) {
				logCanvas.logOnCanvas("1 ** No stored log messages to display **");
			}
			else {
				while (re.hasNextElement()) {
					byte[] nextRec = re.nextRecord();

					/* display log messages on canvas */
					logCanvas.logOnCanvas(new String(nextRec));
				}
			}
		}
		catch (Exception e) {
			logCanvas.error(this, e.getMessage(), e);
		}
	}

	/**
	 * Return one string object containing all log messages persistently stored
	 * in the devices RMS, formatted according to the specification in
	 * <code>LogHelper.createLogMessage()</code>.
	 * 
	 * Because this can take very long time, show a progress alert to keep the
	 * user informed about the progress of this method.
	 * 
	 * @param currentDisplayable
	 * @return String representation of all log messages that are stored in the RMS
	 */
	public String getPersistenLogAsString(Display display, Displayable currentDisplayable) {
		String logStorageString = "";
		try {
			int numRecords = logRecordStore.getNumRecords();
			if (numRecords > 0) {
				/* create progress alert and set display */
				ProgressAlert progressAlert = new ProgressAlert("Reading " + numRecords + " log entries from storage..", AlertType.INFO, numRecords);
				progressAlert.show(display, currentDisplayable);

				progressAlert.show();

				/* read to log messages */
				RecordEnumeration re = logRecordStore.enumerateRecords(null, null, false);
				while (re.hasNextElement()) {
					byte[] nextRec = re.nextRecord();

					String logMessage = new String(nextRec);
					int logLevel = LogHelper.getLogLevel(logMessage);
					logStorageString = logStorageString + LogLevel.levels[logLevel] + " " + logMessage.substring(1)
							+ "\n";

					progressAlert.advance();
				}
				progressAlert.dismiss();
			}

		}
		catch (NumberFormatException nfe) {
			logCanvas.error(this, "NumberFormatException: " + nfe.getMessage(), nfe);
		}
		catch (RecordStoreNotOpenException rsnoe) {
			logCanvas.error(this, "RecordStoreNotOpenException: " + rsnoe.getMessage(), rsnoe);
		}
		catch (InvalidRecordIDException orie) {
			logCanvas.error(this, "InvalidRecordIDException: " + orie.getMessage(), orie);
		}
		catch (RecordStoreException rse) {
			logCanvas.error(this, "RecordStoreException: " + rse.getMessage(), rse);
		}
		return logStorageString;
	}

}
