/* Copyright Iulia Ion
 * File created 2008-08-01
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package org.codec.audio.test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import junit.framework.Assert;
import junit.framework.TestCase;

import com.google.zxing.ReaderException;
/**
 * JUnit tests for encoding and decoding QR codes
 * @author Iulia Ion
 *
 */
public class AudioEncodeDecodeTest extends TestCase{

	public void testEncodeDecode() {
		String []data = new String[]{"my message",
										"Zurich is optimal",
										"let's try 1234 tests"};
		byte [] encoded;
		byte [] decoded;
		for (int i = 0; i < data.length; i++) {
			
			try {
				encoded = org.codec.audio.j2me.AudioUtils.encodeFileToWav(new ByteArrayInputStream(data[i].getBytes()));
				ByteArrayOutputStream dataStream = new ByteArrayOutputStream();
				org.codec.audio.j2me.AudioUtils.decodeWavFile(encoded, dataStream);
				decoded = dataStream.toByteArray();
				
				Assert.assertEquals(new String(decoded), data[i]);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}

	}
}
