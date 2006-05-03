/* Copyright Rene Mayrhofer
 * File created 2006-05-03
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.authentication.test;

import java.security.InvalidParameterException;

import org.eu.mayrhofer.authentication.InterlockProtocol;
import org.eu.mayrhofer.authentication.exceptions.*;

import junit.framework.*;

public class InterlockProtocolTest extends TestCase {
	protected boolean useJSSE = true;
	protected boolean useJSSE2 = true;
	
	public InterlockProtocolTest(String s) {
		super(s);
	}

	public void testAddPart() throws InternalApplicationException {
		byte[] dest = new byte[8];
		byte[] src1 = {0x01, 0x02, 0x03};
		byte[] src2 = {(byte) 0xff};
		byte[] src3 = {(byte) 0xfe};
		byte[] src4 = {(byte) 0xaa};
		
		InterlockProtocol.addPart(dest, src1, 0, 1);
		Assert.assertEquals((byte) 0x01, dest[0]);
		InterlockProtocol.addPart(dest, src1, 0, 8);
		Assert.assertEquals((byte) 0x01, dest[0]);
		InterlockProtocol.addPart(dest, src1, 8, 10);
		Assert.assertEquals((byte) 0x01, dest[0]);
		Assert.assertEquals((byte) 0x01, dest[1]);
		Assert.assertEquals((byte) 0x02, dest[2]);
		InterlockProtocol.addPart(dest, src1, 12, 6);
		Assert.assertEquals((byte) 0x01, dest[0]);
		Assert.assertEquals((byte) 0x11, dest[1]);
		Assert.assertEquals((byte) 0x00, dest[2]);
		InterlockProtocol.addPart(dest, src1, 0, 24);
		Assert.assertEquals((byte) 0x01, dest[0]);
		Assert.assertEquals((byte) 0x02, dest[1]);
		Assert.assertEquals((byte) 0x03, dest[2]);

		InterlockProtocol.addPart(dest, src2, 16, 4);
		Assert.assertEquals((byte) 0x0f, dest[2]);
		InterlockProtocol.addPart(dest, src2, 16, 8);
		Assert.assertEquals((byte) 0xff, dest[2]);

		InterlockProtocol.addPart(dest, src3, 0, 8);
		Assert.assertEquals((byte) 0xfe, dest[0]);

		InterlockProtocol.addPart(dest, src4, 28, 8);
		Assert.assertEquals((byte) 0xa0, dest[3]);
		Assert.assertEquals((byte) 0x0a, dest[4]);

		InterlockProtocol.addPart(dest, src2, 42, 7);
		Assert.assertEquals((byte) 0xfc, dest[5]);
		Assert.assertEquals((byte) 0x01, dest[6]);
	}
	
	public void testAddPart_Exceptions() {
		byte[] dest = new byte[8];
		byte[] src1 = {0x01, 0x02, 0x03};
		try {
			InterlockProtocol.addPart(dest, src1, 0, 25);
			Assert.fail();
		} catch (InternalApplicationException e) {
			Assert.assertTrue(true);
		}
		try {
			InterlockProtocol.addPart(dest, src1, 41, 24);
			Assert.fail();
		} catch (InternalApplicationException e) {
			Assert.assertTrue(true);
		}
		try {
			InterlockProtocol.addPart(dest, src1, 64, 1);
			Assert.fail();
		} catch (InternalApplicationException e) {
			Assert.assertTrue(true);
		}
	}
	
	public void testParameterCheckConstructor1() {
		// this should not work with incorrect parameters
		try {
			InterlockProtocol p = new InterlockProtocol(new byte[] {1}, 2, 128, useJSSE);
			Assert.fail();
		} catch (InvalidParameterException e) {
			Assert.assertTrue(true);
		}
	}

	public void testParameterCheckConstructor2() {
		// this should not work with incorrect parameters
		try {
			InterlockProtocol p = new InterlockProtocol(null, 1, 128, useJSSE);
			Assert.fail();
		} catch (InvalidParameterException e) {
			Assert.assertTrue(true);
		}
	}

	public void testParameterCheckConstructor3() {
		// this should not work with incorrect parameters
		try {
			InterlockProtocol p = new InterlockProtocol(null, 129, 128, useJSSE);
			Assert.fail();
		} catch (InvalidParameterException e) {
			Assert.assertTrue(true);
		}
	}

	public void testParameterCheckConstructor4() {
		// this should not work with incorrect parameters
		try {
			InterlockProtocol p = new InterlockProtocol(null, 2, 127, useJSSE);
			Assert.fail();
		} catch (InvalidParameterException e) {
			Assert.assertTrue(true);
		}
	}
	
	public void testParameterCheckEncrypt1() {
		// this should not work with incorrect parameters
		InterlockProtocol p = new InterlockProtocol(null, 2, 128, useJSSE);
		try {
			p.encrypt(new byte[16]);
			Assert.fail();
		} catch (InternalApplicationException e) {
			Assert.assertTrue(true);
		}
	}

	public void testParameterCheckEncrypt2() throws InternalApplicationException {
		// this should not work with incorrect parameters
		InterlockProtocol p = new InterlockProtocol(new byte[32], 2, 128, useJSSE);
		try {
			p.encrypt(new byte[15]);
			Assert.fail();
		} catch (InvalidParameterException e) {
			Assert.assertTrue(true);
		}
	}

	public void testParameterCheckEncrypt3() throws InternalApplicationException {
		// this should not work with incorrect parameters
		InterlockProtocol p = new InterlockProtocol(new byte[32], 2, 128, useJSSE);
		try {
			p.encrypt(new byte[17]);
			Assert.fail();
		} catch (InvalidParameterException e) {
			Assert.assertTrue(true);
		}
	}

	public void testParameterCheckEncrypt4() throws InternalApplicationException {
		// this should not work with incorrect parameters
		InterlockProtocol p = new InterlockProtocol(new byte[32], 2, 128, useJSSE);
		try {
			p.encrypt(new byte[0]);
			Assert.fail();
		} catch (InvalidParameterException e) {
			Assert.assertTrue(true);
		}
	}

	public void testParameterCheckDecrypt1() {
		// this should not work with incorrect parameters
		InterlockProtocol p = new InterlockProtocol(null, 2, 128, useJSSE);
		try {
			p.encrypt(new byte[16]);
			Assert.fail();
		} catch (InternalApplicationException e) {
			Assert.assertTrue(true);
		}
	}

	public void testParameterCheckDecrypt2() throws InternalApplicationException {
		// this should not work with incorrect parameters
		InterlockProtocol p = new InterlockProtocol(new byte[32], 2, 128, useJSSE);
		try {
			p.encrypt(new byte[15]);
			Assert.fail();
		} catch (InvalidParameterException e) {
			Assert.assertTrue(true);
		}
	}
	
	public void testEncryptDecryptSingleBlock() throws InternalApplicationException {
		byte[] sharedKey = new byte[32];
		for (int i=0; i<sharedKey.length; i++)
			sharedKey[i] = (byte) i;
		InterlockProtocol p1 = new InterlockProtocol(sharedKey, 2, 128, useJSSE);
		InterlockProtocol p2 = new InterlockProtocol(sharedKey, 2, 128, useJSSE2);
		byte[] plainText = new byte[16];
		for (int i=0; i<plainText.length; i++)
			plainText[i] = (byte) (plainText.length-1-i);
		
		byte[] cipherText = p1.encrypt(plainText);
		Assert.assertTrue("cipher text has invalid length", cipherText.length == plainText.length);
		byte[] plainText2 = p2.decrypt(cipherText);
		Assert.assertTrue("decrypted plain text has invalid length", plainText2.length == plainText.length);
		Assert.assertTrue("decrypted plain text does not match original", SimpleKeyAgreementTest.compareByteArray(plainText, plainText2));
	}

	public void testEncryptDecryptMultipleBlocks() throws InternalApplicationException {
		byte[] sharedKey = new byte[32];
		for (int i=0; i<sharedKey.length; i++)
			sharedKey[i] = (byte) i;
		InterlockProtocol p1 = new InterlockProtocol(sharedKey, 2, 129, useJSSE);
		InterlockProtocol p2 = new InterlockProtocol(sharedKey, 2, 129, useJSSE2);
		// 17 bytes is more than one block, so the protocol should switch from ECB to CBC mode
		byte[] plainText = new byte[17];
		for (int i=0; i<plainText.length; i++)
			plainText[i] = (byte) (plainText.length-1-i);
		
		byte[] cipherText = p1.encrypt(plainText);
		Assert.assertTrue("cipher text has invalid length", cipherText.length == p1.getCipherTextBlocks() * 16);
		byte[] plainText2 = p2.decrypt(cipherText);
		Assert.assertTrue("decrypted plain text has invalid length", plainText2.length == plainText.length);
		Assert.assertTrue("decrypted plain text does not match original", SimpleKeyAgreementTest.compareByteArray(plainText, plainText2));
	}
}
