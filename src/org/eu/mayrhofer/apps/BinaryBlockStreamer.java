/* Copyright Rene Mayrhofer
 * File created 2006-03-21
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package org.eu.mayrhofer.apps;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import org.apache.log4j.Logger;

/** This is a helper class for streaming binary blocks over some (byte-safe) connection.
 * It can e.g. be used to stream a file over a TCP connection that has been opened previsouly.
 * The sender side can prefix the binary block with a name so that the receiver side can
 * distinguish different types of binary blocks in a protocol. This name prefix as well as
 * the length of the following binary block in bytes is prefixing the block itself as a
 * complete line terminated with "\n".
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class BinaryBlockStreamer {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(BinaryBlockStreamer.class);
	
	private final static String BinaryStreamCommand = "PUSH";

	/** The input from which to read binary blocks. It may be null if this object is 
	 * only used for writing.
	 */
	private InputStream input;
	/** The output to which binary blocks should be written. It may be null if this object is 
	 * only used for reading.
	 */
	private OutputStream output;
	
	/** Initializes the object with input and/or output. At least one of them must be
	 * set.
	 * 
	 * @param input The input from which to read, if this object is to be used for 
	 *              receiving binary blocks from a channel. 
	 *              It can be null, but then output must be set. 
	 * @param output The output to write to, if this object is to be used for 
	 *               sending binary blocks to a channel. 
	 *               It can be null, but then input must be set. 
	 */
	public BinaryBlockStreamer(InputStream input, OutputStream output) {
		this.input = input;
		this.output = output;
		if (input == null && output == null) {
			logger.error("Need at least either input or output to be set");
			throw new IllegalArgumentException("Need either input or output (or both) to be set");
		}
	}
	
	/** Send a binary block over the output that has been passed to the constructor.
	 * 
	 * @param blockName The name that should be prefixed before sending the block.
	 * @param block The block itself, represented as InputStream. This stream is read from the beginning.
	 * @param size The size of the block to send, i.e. how many bytes of block should be read and sent to output.
	 * @throws IOException When something does not work as expected, i.e. when the bytes can not be sent,
	 *                     an IOException is thrown (or passed through from the underlying streams).
	 */
	public void sendBinaryBlock(String blockName, InputStream block, int size) throws IOException {
		if (output == null) {
			logger.error("Can't send binary block as no output has been defined");
			throw new IOException("Output has not been defined, can not send");
		}
		if (blockName == null || block == null || size <= 0) {
			throw new IllegalArgumentException("Got illegal argument");
		}
		
		logger.info("Sending binary block with " + size + "B named '" + blockName + "'");
		OutputStreamWriter lineWriter = new OutputStreamWriter(output);
		lineWriter.write(BinaryStreamCommand + " " + size + " " + blockName + "\n");
		lineWriter.flush();
		lineWriter = null;
		for (int i=0; i<size; i++)
			output.write(block.read());
		output.flush();
		logger.info("Successfully finished sending binary block");
	}
	
	/** Receive a binary block from the input that has been passed to the constructor.
	 * 
	 * @param blockName The name of the block that has been received in the prefix. This
	 *                  parameter should point to an empty StringBuffer object, to which the
	 *                  name will be appended (also known as poor/Java man's output parameter).
	 * @param block The block itself, represented as OutputStream. This stream is written to.
	 * @return size The number of bytes that have actually been received, if it was possible to
	 *              receive the number of bytes that the sender of the block specified in its
	 *              prefix line, If less then this intended number was received, the number of bytes 
	 *              that have been received is returned as a negative number. E.g. when the sender
	 *              stated that 20 bytes would be sent, but only 15 could be received before an end
	 *              of file or other read error occured, this method will return -15. In short, any
	 *              return value >0 indicates an successful receive of the whole block and specifies
	 *              the number of bytes that have been written to block. Any number <=0 indicates an
	 *              error.
	 * @throws IOException When something does not work as expected, i.e. when the bytes can not be received,
	 *                     an IOException is thrown (or passed through from the underlying streams).
	 */
	public int receiveBinaryBlock(StringBuffer blockName, OutputStream block) throws IOException {
		if (input == null) {
			logger.error("Can't receive binary block as no input has been defined");
			throw new IOException("Input has not been defined, can not receive");
		}
		if (blockName == null || block == null) {
			throw new IllegalArgumentException("Got illegal argument");
		}
		
		// we can only go on if we actually get a proper start line
		logger.debug("Trying to get prefix line");
		/* do not use a BufferedReader here because that would potentially mess up
		 * the stream for other users of the socket (by consuming too many bytes)
		 */
		String prefixLine = "";
		int buf = input.read();
		while (buf != -1 && buf != '\n') {
			if (buf != '\r')
				prefixLine += (char) buf;
			buf = input.read();
		}
		if (prefixLine == null || ! prefixLine.startsWith(BinaryStreamCommand)) {
			logger.error("Did not receive properly formatted streaming command line while trying to receive binary block. Received '" + prefixLine + "'");
			return -1;
		}
		logger.debug("Received prefix line '" + prefixLine + "'");
		// try to decode the two parameters: first the size, then the name
		int offset = prefixLine.indexOf(' ', BinaryStreamCommand.length()+1);
		int intendedSize = Integer.parseInt(prefixLine.substring(BinaryStreamCommand.length()+1, offset));
		blockName.append(prefixLine.substring(offset+1, prefixLine.length()));
		logger.info("Receiving binary block with " + intendedSize + "B named '" + blockName + "'");
		int i=0;
		do {
			buf = input.read();
			if (buf != -1)
				block.write(buf);
			i++;
		} while (i<intendedSize && buf != -1);
		if (i == intendedSize)
			logger.info("Successfully finished receiving binary block");
		else
			logger.error("Could not receive all requested " + intendedSize + "B, only got " + i + "B before end of file or read error");
		return i;
	}
}
