/* Copyright Rene Mayrhofer
 * File created 2007-05-31
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.util;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.log4j.Logger;

/** This is a decorator for an OutputStream that will log every character
 * written to the underlying stream at log4j trace level.
 *  
 * @author Rene Mayrhofer
 */
public class DebugOutputStream extends OutputStream {
	/** The underlying output stream to read from. */
	private OutputStream realStream;
	
	/** Our log4j logger */
	private Logger logger;
	
	/** Initializes the decorator with the real stream. 
	 * @param realStream The OutputStream to write to.
	 * @param loggingName The class name to use for logging purposes. This
	 *                    should be the full class name (including package) of
	 *                    the calling class. 
	 */
	public DebugOutputStream(OutputStream realStream, String loggingName) {
		this.realStream = realStream;
		this.logger = Logger.getLogger(loggingName);
	}
	
	/** Passes through to realStream, but logs. */
	public void write(int c) throws IOException {
		logger.trace("'" + (char) c + "' " + c);
		realStream.write(c);
	}

	/** Passes through to realStream, but logs. */
	public void write(byte[] arr) throws java.io.IOException {
		StringBuffer log = new StringBuffer();
		for (int i=0; i<arr.length; i++)
			log.append("'" + (char) arr[i] + "' " + arr[i] + ", ");
		logger.trace(log.toString());
		realStream.write(arr);
	}
	
	/** Passes through to realStream, but logs. */
	public void write(byte[] arr, int off, int len) throws java.io.IOException {
		StringBuffer log = new StringBuffer();
		for (int i=off; i<off+len; i++)
			log.append("'" + (char) arr[i] + "' " + arr[i] + ", ");
		logger.trace(log.toString());
		realStream.write(arr, off, len);
	}
	
	/** Only passes through to realStream, no logging. */
	public void flush() throws java.io.IOException {
		realStream.flush();
	}
	
	/** Only passes through to realStream, no logging. */
	public void close() throws java.io.IOException {
		realStream.close();
	}
}
