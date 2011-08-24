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

import java.io.IOException;
import java.io.OutputStream;
import java.util.Enumeration;

import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;
import javax.microedition.io.file.FileSystemRegistry;

import net.sf.microlog.Level;
import net.sf.microlog.util.PropertiesGetter;

/**
 * A class that logs to a file. The class uses the FileConnection API from
 * JSR-75.
 * <p>
 * The file name can be passed with the property
 * <code>microlog.appender.FileAppender.filename</code>.
 * 
 * The default directory for storing files is fetched from
 * <code>FileSystemRegistry.listRoots()</code>, where the first root is used.
 * The directory is possible to set with the <code>setDirectory()</code>
 * method.
 * 
 * @author Johan Karlsson (johan.karlsson@jayway.se)
 * @author Karsten Ohme
 * @since 0.1
 */
public class FileAppender extends AbstractAppender {

	/**
	 * The property to set for the filename to log to.
	 */
	public static final String FILENAME_STRING = "microlog.appender.FileAppender.filename";

	/**
	 * The default log filename.
	 */
	public static final String DEFAULT_FILENAME = "microlog.txt";

	private static final int BUFFER_SIZE = 256;

	private static final String FILE_PROTOCOL = "file:///";

	private String lineSeparator = "\r\n";

	private String directory;

	protected String fileName = DEFAULT_FILENAME;

	protected FileConnection fileConnection;

	private OutputStream outputStream;

	/**
	 * @see net.sf.microlog.appender.AbstractAppender#open()
	 */
	public synchronized void open() throws IOException {

		if (fileConnection == null && directory == null) {
			Enumeration rootsEnum = FileSystemRegistry.listRoots();

			if (rootsEnum.hasMoreElements()) {
				directory = (String) rootsEnum.nextElement();
			} else {
				System.err.println("No root directory is found.");
			}
		}

		if (fileConnection == null) {
			StringBuffer connectionStringBuffer = new StringBuffer(BUFFER_SIZE);
			connectionStringBuffer.append(FILE_PROTOCOL);
			connectionStringBuffer.append(directory);
			connectionStringBuffer.append(fileName);

			fileConnection = (FileConnection) Connector.open(
					connectionStringBuffer.toString(), Connector.READ_WRITE);
			if (!fileConnection.exists()) {
				fileConnection.create();
			}

		}
		if (fileConnection != null) {
			outputStream = fileConnection.openDataOutputStream();
			logOpen = true;
		}
	}

	/**
	 * @see net.sf.microlog.appender.AbstractAppender#doLog(String,
	 *      long, net.sf.microlog.Level, java.lang.Object, java.lang.Throwable)
	 */
	public synchronized void doLog(String name, long time, Level level,
			Object message, Throwable t) {
		if (logOpen && formatter != null) {
			String logString = formatter.format(name, time, level, message, t);
			try {
				byte[] stringData = logString.getBytes();
				outputStream.write(stringData);
				outputStream.write(lineSeparator.getBytes());
				outputStream.flush();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * @see net.sf.microlog.appender.AbstractAppender#clear()
	 */
	public synchronized void clear() {
		if (fileConnection != null && fileConnection.isOpen()) {
			try {
				fileConnection.truncate(0);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * @see net.sf.microlog.appender.AbstractAppender#close()
	 */
	public synchronized void close() throws IOException {
		if (fileConnection != null && fileConnection.isOpen()) {
			if (outputStream != null) {
				outputStream.close();
			}

			fileConnection.close();
			logOpen = false;
		}
	}

	/**
	 * Get the size of the log.
	 * 
	 * @return the size of the log.
	 */
	public synchronized long getLogSize() {

		long logSize = SIZE_UNDEFINED;

		if (fileConnection != null) {
			try {
				logSize = fileConnection.fileSize();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return logSize;
	}

	/**
	 * Set the filename of the logfile. This is the name of file, without the
	 * directory specifier.
	 * 
	 * @return the fileName
	 */
	public synchronized String getFileName() {
		return fileName;
	}

	/**
	 * Get the filename of the logfile.
	 * 
	 * Note that changing this after the logfile has been opened has no effect.
	 * 
	 * @param fileName
	 *            the fileName to set
	 * @throws IllegalArgumentException
	 *             if the filename is null.
	 */
	public synchronized void setFileName(String fileName)
			throws IllegalArgumentException {
		if (fileName == null) {
			throw new IllegalArgumentException("The filename must not be null.");
		}

		this.fileName = fileName;
	}

	/**
	 * Get the line separator.
	 * 
	 * @return the lineSeparator
	 */
	public String getLineSeparator() {
		return lineSeparator;
	}

	/**
	 * Set the line separator.
	 * 
	 * @param lineSeparator
	 *            the lineSeparator to set
	 */
	public void setLineSeparator(String lineSeparator)
			throws IllegalArgumentException {
		if (lineSeparator == null) {
			throw new IllegalArgumentException(
					"The line separator must not be null.");
		}

		this.lineSeparator = lineSeparator;
	}

	/**
	 * Set the directory.
	 * 
	 * @param directory
	 *            the path to the log directory. It must start with a valid root directory.
	 */
	public void setDirectory(String directory)
			throws IllegalArgumentException {
		if (directory == null) {
			throw new IllegalArgumentException(
					"The directory must not be null.");
		}

		this.directory = directory;
	}

	/**
	 * Configure the FileAppender.
	 * <p>
	 * The file name can be passed with the property
	 * <code>microlog.appender.FileAppender.filename</code>.
	 * 
	 * @param properties
	 *            Properties to configure with
	 */
	public synchronized void configure(PropertiesGetter properties) {
		// Set the record store name from Properties
		fileName = properties.getString(FILENAME_STRING);
		if (fileName == null) {
			fileName = DEFAULT_FILENAME;
		}
	}

	/**
	 * @param fileConnection
	 *            the fileConnection to set
	 */
	synchronized void setFileConnection(FileConnection fileConnection) {
		this.fileConnection = fileConnection;
	}

}
