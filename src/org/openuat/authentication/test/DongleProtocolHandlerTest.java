/* Copyright Rene Mayrhofer
 * File created 2005-09
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.authentication.test;

import org.openuat.authentication.exceptions.*;
import org.openuat.authentication.relate.DongleProtocolHandler;

import junit.framework.*;

public class DongleProtocolHandlerTest extends TestCase {
	public void testCompareBits() {
		byte[] a1 = {0x01, 0x02, 0x03};
		byte[] a2 = {0x01, 0x02, 0x07};
		byte[] a3 = {0x01, 0x02, 0x27};
		byte[] a4 = {0x00, 0x02, 0x27};
		
		Assert.assertTrue(DongleProtocolHandler.compareBits(a1, a2, 18));
		Assert.assertFalse(DongleProtocolHandler.compareBits(a1, a2, 19));
		Assert.assertTrue(DongleProtocolHandler.compareBits(a2, a3, 19));
		Assert.assertTrue(DongleProtocolHandler.compareBits(a2, a3, 20));
		Assert.assertTrue(DongleProtocolHandler.compareBits(a2, a3, 21));
		Assert.assertFalse(DongleProtocolHandler.compareBits(a2, a3, 22));
		Assert.assertFalse(DongleProtocolHandler.compareBits(a3, a4, 22));
	}

	public void testHammingDistance() {
		byte[] a1 = {0x01, 0x02, 0x03};
		byte[] a2 = {0x01, 0x02, 0x07};
		byte[] a3 = {0x01, 0x02, 0x27};
		byte[] a4 = {0x00, 0x02, 0x27};
		
		Assert.assertEquals(1, DongleProtocolHandler.hammingDistance(a1, a2, 24));
		Assert.assertEquals(2, DongleProtocolHandler.hammingDistance(a1, a3, 24));
		Assert.assertEquals(3, DongleProtocolHandler.hammingDistance(a1, a4, 24));
		Assert.assertEquals(1, DongleProtocolHandler.hammingDistance(a1, a4, 16));
	}
}