/* Copyright Rene Mayrhofer
 * File created 2007-07-20
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

/** This is a tiny helper class to provide readLine and println methods
 * around InputStream and OutputStream. In contrast to BufferedReader and
 * PrintWriter (which are not available on CDLC anyways), the implementations
 * in this class are unbuffered, and reading a line therefore does not consume
 * too many bytes from the underlying stream (as BufferedReader would do). 
 * This makes it possible to call readLine on an InputStream and then re-use
 * it for binary transfers.
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 * @since 0.10
 */
public class LineReaderWriter {
	/** If timeoutMs > 0, then this method first waits for a byte to become 
	 * available in in before reading it. If none becomes available within
	 * timeoutMs, an IOException will be thrown.
	 */
	public static int readWithTimeout(InputStream in, int timeoutMs) throws IOException {
		if (timeoutMs > 0) {
			long startTime = System.currentTimeMillis();
			while (in.available() == 0 && System.currentTimeMillis() < startTime+timeoutMs) {
				try {
					Thread.sleep(50);
				} catch (InterruptedException e) {
					throw new IOException("Timeout while trying to read from stream (waited for " + 
							timeoutMs + "ms for 1 byte)");
				}
			}
			if (in.available() == 0)
				throw new IOException("Timeout while trying to read from stream (waited for " + 
						timeoutMs + "ms for 1 byte)");
		}
		return in.read();
	}
	
	/** Read a line from an InputStream (until a '\n' character is 
	 * encountered). This method blocks until the end of line or the end
	 * of stream (-1) is received or the timeout (in milliseconds) is
	 * reached.
	 */
    public static String readLine(InputStream in, int timeoutMs) throws IOException {
		StringBuffer line = new StringBuffer();
		int buf = readWithTimeout(in, timeoutMs);
		while (buf != -1 && buf != '\n') {
			if (buf != '\r')
				line.append((char) buf);
			buf = readWithTimeout(in, timeoutMs);
		}
		return line.toString();
    }
    
    /** just calls readLine(in, -1). */
    public static String readLine(InputStream in) throws IOException {
    	return readLine(in, -1);
    }
    
    /** Print a line to an OutputStream, temporarily wrapping it in an
     * OutputStreamWriter. */
    public static void println(OutputStream out, String line) throws IOException {
    	OutputStreamWriter writer = new OutputStreamWriter(out);
    	println(writer, line);
    	writer = null;
    }
    
    /** Print a line to an OutputStreamWriter. */
    public static void println(OutputStreamWriter out, String line) throws IOException {
		out.write(line + "\n");
		out.flush();
    }
}
