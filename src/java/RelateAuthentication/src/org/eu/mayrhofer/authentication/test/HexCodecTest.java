/* Copyright Rene Mayrhofer
 * File created 2005-09
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.authentication.test;

import junit.framework.*;

import org.apache.commons.codec.*;
import org.apache.commons.codec.binary.*;

public class HexCodecTest extends TestCase {

	public HexCodecTest(String arg0) {
		super(arg0);
	}

	public void testHexCodec() throws DecoderException {
		byte[][] testArrays = new byte[2][];
		testArrays[0] = "Testme".getBytes();
		testArrays[1] = Integer.toHexString(42).getBytes();
		
		for (int i=0; i<testArrays.length; i++) {
			char[] coded = Hex.encodeHex(testArrays[i]);
			Assert.assertTrue(SimpleKeyAgreementTest.compareByteArray(testArrays[i], Hex.decodeHex(coded)));
		}
	}
}
