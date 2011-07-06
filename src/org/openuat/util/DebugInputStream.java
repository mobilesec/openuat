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
import java.io.InputStream;

import java.util.logging.Logger;

/** This is a decorator for an InputStream that will log every character
 * read from the underlying stream at log4j trace level.
 *  
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class DebugInputStream extends InputStream {
	/** The underlying input stream to read from. */
	private InputStream realStream;
	
	/** Our logger */
	private Logger logger;
	
	/** Initializes the decorator with the real stream. 
	 * @param realStream The InputStream to read from.
	 * @param loggingName The class name to use for logging purposes. This
	 *                    should be the full class name (including package) of
	 *                    the calling class. 
	 */
	public DebugInputStream(InputStream realStream, String loggingName) {
		this.realStream = realStream;
		this.logger = Logger.getLogger(loggingName);
	}
	
	/** Passes through to realStream, but logs. */
	// TODO: activate me again when J2ME polish can deal with Java5 sources!
	//@Override
	public int read() throws IOException {
		int c = realStream.read();
		logger.trace("'" + (char) c + "' " + c);
		return c;
	}
	
	/** Passes through to realStream, but logs. */
	// TODO: activate me again when J2ME polish can deal with Java5 sources!
	//@Override
	public int read(byte[] arr) throws IOException {
		int len = realStream.read(arr);
		StringBuffer log = new StringBuffer();
		for (int i=0; i<len; i++)
			log.append("'" + (char) arr[i] + "' " + arr[i] + ", ");
		logger.trace(log.toString());
		return len;
	}
	
	/** Passes through to realStream, but logs. */
	// TODO: activate me again when J2ME polish can deal with Java5 sources!
	//@Override
	public int read(byte[] arr, int off, int len) throws IOException {
		int readLen = realStream.read(arr);
		StringBuffer log = new StringBuffer();
		for (int i=off; i<off+readLen; i++)
			log.append("'" + (char) arr[i] + "' " + arr[i] + ", ");
		logger.trace(log.toString());
		return readLen;
	}
	
	/** Only passes through to realStream, no logging. */
	// TODO: activate me again when J2ME polish can deal with Java5 sources!
	//@Override
	public long skip(long arg0) throws java.io.IOException {
		return realStream.skip(arg0);
	}
	
	/** Only passes through to realStream, no logging. */
	// TODO: activate me again when J2ME polish can deal with Java5 sources!
	//@Override
	public int available() throws java.io.IOException {
		return realStream.available();
	}
	
	/** Only passes through to realStream, no logging. */
	// TODO: activate me again when J2ME polish can deal with Java5 sources!
	//@Override
	public void close() throws java.io.IOException {
		realStream.close();
	}
	
	/** Only passes through to realStream, no logging. */
	// TODO: activate me again when J2ME polish can deal with Java5 sources!
	//@Override
	public synchronized void mark(int arg0) {
		realStream.mark(arg0);
	}
	
	/** Only passes through to realStream, no logging. */
	// TODO: activate me again when J2ME polish can deal with Java5 sources!
	//@Override
	public synchronized void reset() throws java.io.IOException {
		realStream.reset();
	}
	
	/** Only passes through to realStream, no logging. */
	// TODO: activate me again when J2ME polish can deal with Java5 sources!
	//@Override
	public boolean markSupported() {
		return realStream.markSupported();
	}
}
