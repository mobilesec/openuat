/*
 * Copyright 2008 The Microlog project @sourceforge.net
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.sf.microlog.appender;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Hashtable;

import javax.microedition.rms.InvalidRecordIDException;
import javax.microedition.rms.RecordEnumeration;
import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;
import javax.microedition.rms.RecordStoreFullException;
import javax.microedition.rms.RecordStoreNotFoundException;
import javax.microedition.rms.RecordStoreNotOpenException;

import net.sf.microlog.Formatter;
import net.sf.microlog.Level;
import net.sf.microlog.rms.DescendingComparator;
import net.sf.microlog.util.PropertiesGetter;

/**
 * An Appender that appends the logging to the record store.
 * 
 * <p>
 * The maximum log entry size can be set with the parameter
 * <code>microlog.appender.RecordStoreAppender.maxLogEntries</code> The file
 * name can be passed with the property
 * <code>microlog.appender.RecordStoreAppender.recordStoreName</code>.
 * 
 * @author Johan Karlsson
 * @author Darius Katz
 * @author Karsten Ohme
 * @since 0.1
 */
public class RecordStoreAppender extends AbstractAppender {

	/**
	 * The property to set for the RecordStore name.
	 */
	public static final String RECORD_STORE_NAME_STRING = "microlog.appender.RecordStoreAppender.recordStoreName";

	/**
	 * The property to set for the maximum log entries.
	 */
	public static final String RECORD_STORE_MAX_ENTRIES_STRING = "microlog.appender.RecordStoreAppender.maxLogEntries";

	/**
	 * The default RecordStore name
	 */
	public static final String RECORD_STORE_DEFAULT_NAME = "LogRecordStore";

	/**
	 * The number of default maximum log entries.
	 */
	public static final int RECORD_STORE_DEFAULT_MAX_ENTRIES = 20;

	/**
	 * RecordStore of this appender.
	 */
	private RecordStore logRecordStore;

	/**
	 * The RecordStore name of this appender.
	 */
	private String recordStoreName = RECORD_STORE_DEFAULT_NAME;

	/**
	 * Used for synchronization purposes on the shared data.
	 */
	private final static Object synchronization = new Object();

	/**
	 * Keeps track of all record stores using as key the record store name
	 */
	private final static Hashtable recordStores = new Hashtable(1);

	/**
	 * Keeps track of all reference counts to all record stores using as key the
	 * record store name.
	 */
	private static Hashtable recordStoreReferenceCounts = new Hashtable(1);

	private ByteArrayOutputStream byteArrayOutputStream;
	private DataOutputStream dataOutputStream;

	// variables used by the limited record entries functionality
	private int maxRecordEntries = RECORD_STORE_DEFAULT_MAX_ENTRIES;
	private int currentOldestEntry;
	private int[] limitedRecordIDs;

	/**
	 * Create a RecordStoreAppender with the default name and limited record
	 * size.
	 */
	public RecordStoreAppender() {
		super();
		byteArrayOutputStream = new ByteArrayOutputStream(64);
		dataOutputStream = new DataOutputStream(byteArrayOutputStream);
	}

	/**
	 * Get the recordstore name to use for logging.
	 * 
	 * @return the recordStoreName
	 */
	public String getRecordStoreName() {
		return recordStoreName;
	}

	/**
	 * Set the recordstore name to use for logging.
	 * 
	 * Note: this has no effect if the log is opened or if the re.
	 * 
	 * @param recordStoreName
	 *            the recordStoreName to set
	 * @throws IllegalArgumentException
	 *             if the <code>recordStoreName</code> is null
	 */
	public void setRecordStoreName(String recordStoreName)
			throws IllegalArgumentException {

		if (recordStoreName == null) {
			throw new IllegalArgumentException(
					"The recordStoreName must not be null.");
		}

		if (logOpen == false) {
			this.recordStoreName = recordStoreName;
		}
	}

	/**
	 * Get the max number of recordstore entries.
	 * 
	 * @return the maxRecordEntries
	 */
	public int getMaxRecordEntries() {
		return maxRecordEntries;
	}

	/**
	 * Set the recordstore name to use for logging.
	 * 
	 * Note: this has no effect if the log is opened.
	 * 
	 * @param maxRecordEntries
	 *            the maxRecordEntries to set
	 */
	public void setMaxRecordEntries(int maxRecordEntries) {
		if (logOpen == false) {
			this.maxRecordEntries = maxRecordEntries;
		}
	}

	/**
	 * Do the logging.
	 * 
	 * @param name
	 *            the name of the logger.
	 * @param level
	 *            the level to use for the logging.
	 * @param message
	 *            the message to log.
	 * @param t
	 *            the exception to log.
	 */
	public void doLog(String name, long time, Level level, Object message,
			Throwable t) {
		synchronized (synchronization) {
			RecordStoreAppender master = getMasterRecordStoreAppender();
			if (logOpen && formatter != null) {
				master.doLog(name, time, level, message, t, formatter);
			}
		}
	}

	/**
	 * Do the logging.
	 * <p>
	 * Only executed by the master RecordStoreAppender.
	 * 
	 * @param name
	 *            the name of the logger.
	 * @param time
	 *            the relative time when the logging was done.
	 * @param level
	 *            the level to use for the logging.
	 * @param message
	 *            the message to log.
	 * @param t
	 *            the exception to log.
	 * @param formatter
	 *            the formatter.
	 */
	private void doLog(String name, long time, Level level, Object message,
			Throwable t, Formatter formatter) {
		// we are the master and have to do the delegated task
		if (getReferenceCount() > 0 && formatter != null) {
			byte[] data = createLogData(name, time, level, message, t,
					formatter);

			try {
				// Delete the oldest log entry
				if (limitedRecordIDs[currentOldestEntry] != -1) {
					logRecordStore
							.deleteRecord(limitedRecordIDs[currentOldestEntry]);
				}

				// Add the new entry
				int newRecId = logRecordStore.addRecord(data, 0, data.length);

				// Save the recordId for later
				limitedRecordIDs[currentOldestEntry] = newRecId;

				// Move pointer to the now oldest entry
				currentOldestEntry = (currentOldestEntry + 1)
						% maxRecordEntries;

			} catch (RecordStoreNotOpenException e) {
				e.printStackTrace();
			} catch (RecordStoreFullException e) {
				e.printStackTrace();
			} catch (RecordStoreException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Create the log data.
	 * <p>
	 * Executed only by the master.
	 * 
	 * @param name
	 *            the name of the logger.
	 * @param time
	 *            the relative time when the logging was done.
	 * @param level
	 *            the level to use for the logging.
	 * @param message
	 *            the message to log.
	 * @param t
	 *            the exception to log.
	 * @param formatter
	 *            the formatter.
	 * 
	 * @return the formatted log entry.
	 */
	private byte[] createLogData(String name, long time, Level level,
			Object message, Throwable t, Formatter formatter) {

		byte[] data = null;

		try {
			byteArrayOutputStream.reset();
			dataOutputStream.writeLong(time);
			dataOutputStream.writeUTF(formatter.format(name, time, level,
					message, t));
			data = byteArrayOutputStream.toByteArray();
			dataOutputStream.close();
			byteArrayOutputStream.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return data;
	}

	/**
	 * Clear the underlying RecordStore from data. Note if logging is done when
	 * executing this method, these new logging events are not cleared.
	 * 
	 * @see net.sf.microlog.appender.AbstractAppender#clear()
	 */
	public void clear() {
		synchronized (synchronization) {
			RecordStoreAppender master = getMasterRecordStoreAppender();
			// we are the master
			if (master == this) {
				try {
					RecordEnumeration enumeration = logRecordStore
							.enumerateRecords(null, null, false);
					while (enumeration.hasNextElement()) {
						int recordId = enumeration.nextRecordId();
						logRecordStore.deleteRecord(recordId);
					}
				} catch (RecordStoreNotOpenException e) {
					e.printStackTrace();
				} catch (InvalidRecordIDException e) {
					e.printStackTrace();
				} catch (RecordStoreException e) {
					e.printStackTrace();
				}
			}
			// we delegate
			else {
				master.clear();
			}
		}
	}

	/**
	 * @see net.sf.microlog.appender.AbstractAppender#close()
	 */
	public void close() throws IOException {
		synchronized (synchronization) {
			if (logOpen) {
				try {
					RecordStoreAppender master = getMasterRecordStoreAppender();
					// we are the master and the last RecordStoreAppender is
					// going
					// to be closed
					if (master == this && getReferenceCount() == 1) {
						logRecordStore.closeRecordStore();
						// delete RecordStoreAppender from hashtable
						recordStores.remove(recordStoreName);
					}
					// decrement reference count
					recordStoreReferenceCounts.put(recordStoreName,
							new Integer(getReferenceCount() - 1));

				} catch (RecordStoreNotOpenException e) {
					throw new IOException("The RecordStore was not open " + e);
				} catch (RecordStoreException e) {
					throw new IOException("Failed to close the RecordStore "
							+ e);
				}

				if (byteArrayOutputStream != null) {
					byteArrayOutputStream.close();
				}

				if (dataOutputStream != null) {
					dataOutputStream.close();
				}

				logOpen = false;
			}
		}
	}

	/**
	 * Returns the master RecordStoreAppender
	 * <p>
	 * The master RecordStoreAppender is the record store which opened the
	 * associated RecordStore at first. It centrally executes all tasks to keep
	 * everything in a consistent state.
	 * 
	 * @return the master RecordStoreAppender
	 */
	private RecordStoreAppender getMasterRecordStoreAppender() {
		synchronized (synchronization) {
			return ((RecordStoreAppender) recordStores.get(recordStoreName));
		}
	}

	/**
	 * Returns the reference count to the record store name.
	 * <p>
	 * 
	 * @return the reference count.
	 */
	private int getReferenceCount() {
		synchronized (synchronization) {
			return ((Integer) recordStoreReferenceCounts.get(recordStoreName))
					.intValue();
		}
	}

	/**
	 * @see net.sf.microlog.appender.AbstractAppender#open()
	 */
	public void open() throws IOException {
		synchronized (synchronization) {
			try {
				// the appender already exists
				if (recordStores.containsKey(recordStoreName)) {
					// increment reference counter
					recordStoreReferenceCounts.put(recordStoreName,
							new Integer(getReferenceCount() + 1));
				}
				// a RecordStore with a new record store name is opened and this
				// RecordStoreAppender acts as master
				else {
					logRecordStore = RecordStore.openRecordStore(
							recordStoreName, true, RecordStore.AUTHMODE_ANY,
							true);
					// store it under the record store name
					recordStores.put(recordStoreName, this);
					// set the reference count to one
					recordStoreReferenceCounts.put(recordStoreName,
							new Integer(1));
				}
				// forwards to the master RecordStoreAppender
				getMasterRecordStoreAppender().initLimitedEntries();
				logOpen = true;
			} catch (RecordStoreFullException e) {
				e.printStackTrace();
			} catch (RecordStoreNotFoundException e) {
				e.printStackTrace();
			} catch (RecordStoreException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Get the size of the log.
	 * 
	 * @return the size of the log.
	 */
	public long getLogSize() {
		synchronized (synchronization) {
			RecordStoreAppender master = getMasterRecordStoreAppender();
			// we are the master
			if (master == this) {
				long logSize = SIZE_UNDEFINED;
				if (logRecordStore != null) {
					try {
						int numRecords = logRecordStore.getNumRecords();
						if (numRecords != 0) {
							logSize = logRecordStore.getSize();
						} else if (numRecords == 0) {
							logSize = 0;
						}
					} catch (RecordStoreNotOpenException e) {
						e.printStackTrace();
					}
				}

				return logSize;
			} else {
				// otherwise we delegate
				return master.getLogSize();
			}
		}
	}

	/**
	 * Initialise the limited entries functionality
	 * <p>
	 * Only the master executes this.
	 */
	private void initLimitedEntries() {

		limitedRecordIDs = new int[maxRecordEntries];

		for (int i = 0; i < maxRecordEntries; i++)
			limitedRecordIDs[i] = -1;

		// Enumerate through all records. Copy/save timestamps and recordIDs
		// of the newest n records into the array(s) that keep track of limited
		// entries and delete the rest of the records from the RecordStore.
		// (n = max no of log-enties)

		try {
			int arrayPointer = maxRecordEntries - 1;
			// only if not already open open it, i.e. we are reconfiguring at
			// runtime
			if (!logOpen) {
				logRecordStore = RecordStore.openRecordStore(recordStoreName,
						true);
			}
			RecordEnumeration recordEnum = logRecordStore.enumerateRecords(
					null, new DescendingComparator(), false);

			while (recordEnum.hasNextElement()) {
				int recId = recordEnum.nextRecordId();
				if (arrayPointer >= 0) {
					// save recId
					limitedRecordIDs[arrayPointer] = recId;
					arrayPointer--;
				} else {
					// too old, just delete
					logRecordStore.deleteRecord(recId);
				}
			}
			recordEnum.destroy();
		} catch (RecordStoreNotFoundException e) {
			System.err.println("Failed to find recordstore. " + e);
		} catch (RecordStoreException e) {
			System.err.println("Something is wrong with the RecordStore. " + e);
		}

	}

	/**
	 * Configure the RecordStoreAppender.
	 * <p>
	 * The maximum log entry size can be set with the parameter
	 * <code>microlog.appender.RecordStoreAppender.maxLogEntries</code> The
	 * file name can be passed with the property
	 * <code>microlog.appender.RecordStoreAppender.recordStoreName</code>.
	 * 
	 * @param properties
	 *            Properties to configure with
	 */
	public synchronized void configure(PropertiesGetter properties) {

		// Set the record store name from Properties
		String midletName = properties.getString("MIDlet-Name");
		if (midletName != null && midletName.length() > 0
				&& midletName.length() < 30) {
			recordStoreName = midletName + "-ml";
		}

		String recordStoreNameProperty = properties
				.getString(RecordStoreAppender.RECORD_STORE_NAME_STRING);
		if (recordStoreNameProperty != null
				&& recordStoreNameProperty.length() > 0
				&& recordStoreNameProperty.length() < 32) {
			recordStoreName = recordStoreNameProperty;
		}

		if (recordStoreName == null) {
			recordStoreName = RecordStoreAppender.RECORD_STORE_DEFAULT_NAME;
		}

		// Set the maximum number of record/log entries from Properties
		try {
			maxRecordEntries = Integer.parseInt(properties
					.getString(RECORD_STORE_MAX_ENTRIES_STRING));
		} catch (NumberFormatException e) {
			maxRecordEntries = RECORD_STORE_DEFAULT_MAX_ENTRIES;
		}

		// reload the record store with the new settings
		// close old store
		try {
			close();
		} catch (IOException e) {
			System.err.println("Failed to close the record store. " + e);
		}
		// open new store
		try {
			open();
		} catch (IOException e) {
			System.err.println("Failed to re-open the record store. " + e);
		}
	}

}
