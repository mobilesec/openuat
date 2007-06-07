/* Copyright Rene Mayrhofer
 * File created 2006-05-03
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.authentication.test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.BitSet;

import org.openuat.authentication.InterlockProtocol;
import org.openuat.authentication.exceptions.*;

import junit.framework.*;

public class InterlockProtocolTest extends TestCase {
	protected boolean useJSSE = true;
	protected boolean useJSSE2 = true;
	
	public InterlockProtocolTest(String s) {
		super(s);
	}
	
	// TODO: activate me again when J2ME polish can deal with Java5 sources!
	//@Override
	public void tearDown() {
		System.gc();
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
	
	public void testExtractPart() throws InternalApplicationException {
		byte[] src = new byte[8];
		for (int i=0; i<8; i++)
			src[i] = (byte) (i+1);

		byte[] dst = new byte[1];
		byte[] dst2 = new byte[2];
		InterlockProtocol.extractPart(dst, src, 0, 1);
		Assert.assertEquals((byte) 0x01, dst[0]);
		InterlockProtocol.extractPart(dst, src, 1, 1);
		Assert.assertEquals((byte) 0x00, dst[0]);
		InterlockProtocol.extractPart(dst, src, 0, 2);
		Assert.assertEquals((byte) 0x01, dst[0]);
		InterlockProtocol.extractPart(dst, src, 8, 1);
		Assert.assertEquals((byte) 0x00, dst[0]);
		InterlockProtocol.extractPart(dst, src, 8, 2);
		Assert.assertEquals((byte) 0x02, dst[0]);
		InterlockProtocol.extractPart(dst, src, 9, 2);
		Assert.assertEquals((byte) 0x01, dst[0]);
		InterlockProtocol.extractPart(dst, src, 10, 1);
		Assert.assertEquals((byte) 0x00, dst[0]);
		InterlockProtocol.extractPart(dst, src, 10, 6);
		Assert.assertEquals((byte) 0x00, dst[0]);
		InterlockProtocol.extractPart(dst, src, 9, 8);
		Assert.assertEquals((byte) 0x81, dst[0]);
		InterlockProtocol.extractPart(dst2, src, 9, 9);
		Assert.assertEquals((byte) 0x81, dst2[0]);
		Assert.assertEquals((byte) 0x01, dst2[1]);
	}

	public void testParameterCheckConstructor1() {
		// this should not work with incorrect parameters
		try {
			InterlockProtocol p = new InterlockProtocol(new byte[] {1}, 2, 128, null, useJSSE);
			System.out.println("Should not get here, and this makes Eclipse warning shut up: " + p);
			Assert.fail();
		} catch (IllegalArgumentException e) {
			Assert.assertTrue(true);
		}
	}

	public void testParameterCheckConstructor2() {
		// this should not work with incorrect parameters
		try {
			InterlockProtocol p = new InterlockProtocol(null, 1, 128, null, useJSSE);
			System.out.println("Should not get here, and this makes Eclipse warning shut up: " + p);
			Assert.fail();
		} catch (IllegalArgumentException e) {
			Assert.assertTrue(true);
		}
	}

	public void testParameterCheckConstructor3() {
		// this should not work with incorrect parameters
		try {
			InterlockProtocol p = new InterlockProtocol(null, 129, 128, null, useJSSE);
			System.out.println("Should not get here, and this makes Eclipse warning shut up: " + p);
			Assert.fail();
		} catch (IllegalArgumentException e) {
			Assert.assertTrue(true);
		}
	}

	public void testParameterCheckConstructor4() {
		// this should not work with incorrect parameters
		try {
			InterlockProtocol p = new InterlockProtocol(new byte[32], 2, 127, null, useJSSE);
			System.out.println("Should not get here, and this makes Eclipse warning shut up: " + p);
			Assert.fail();
		} catch (IllegalArgumentException e) {
			Assert.assertTrue(true);
		}
	}
	
	public void testParameterCheckEncrypt1() {
		InterlockProtocol p = new InterlockProtocol(null, 2, 128, null, useJSSE);
		try {
			// this should not work with incorrect parameters
			p.encrypt(new byte[16]);
			Assert.fail();
		} catch (InternalApplicationException e) {
			Assert.assertTrue(true);
		}
	}

	public void testParameterCheckEncrypt2() throws InternalApplicationException {
		InterlockProtocol p = new InterlockProtocol(new byte[32], 2, 128, null, useJSSE);
		try {
			// this should not work with incorrect parameters
			p.encrypt(new byte[15]);
			Assert.fail();
		} catch (IllegalArgumentException e) {
			Assert.assertTrue(true);
		}
	}

	public void testParameterCheckEncrypt3() throws InternalApplicationException {
		InterlockProtocol p = new InterlockProtocol(new byte[32], 2, 128, null, useJSSE);
		try {
			// this should not work with incorrect parameters
			p.encrypt(new byte[17]);
			Assert.fail();
		} catch (IllegalArgumentException e) {
			Assert.assertTrue(true);
		}
	}

	public void testParameterCheckEncrypt4() throws InternalApplicationException {
		InterlockProtocol p = new InterlockProtocol(new byte[32], 2, 128, null, useJSSE);
		try {
			// this should not work with incorrect parameters
			p.encrypt(new byte[0]);
			Assert.fail();
		} catch (IllegalArgumentException e) {
			Assert.assertTrue(true);
		}
	}

	public void testParameterCheckDecrypt1() {
		InterlockProtocol p = new InterlockProtocol(null, 2, 128, null, useJSSE);
		try {
			// this should not work with incorrect parameters
			p.encrypt(new byte[16]);
			Assert.fail();
		} catch (InternalApplicationException e) {
			Assert.assertTrue(true);
		}
	}

	public void testParameterCheckDecrypt2() throws InternalApplicationException {
		InterlockProtocol p = new InterlockProtocol(new byte[32], 2, 128, null, useJSSE);
		try {
			// this should not work with incorrect parameters
			p.encrypt(new byte[15]);
			Assert.fail();
		} catch (IllegalArgumentException e) {
			Assert.assertTrue(true);
		}
	}
	
	public void testEncryptDecrypt_SingleBlock() throws InternalApplicationException {
		byte[] sharedKey = new byte[32];
		for (int i=0; i<sharedKey.length; i++)
			sharedKey[i] = (byte) i;
		InterlockProtocol p1 = new InterlockProtocol(sharedKey, 2, 128, null, useJSSE);
		InterlockProtocol p2 = new InterlockProtocol(sharedKey, 2, 128, null, useJSSE2);
		byte[] plainText = new byte[16];
		for (int i=0; i<plainText.length; i++)
			plainText[i] = (byte) (plainText.length-1-i);
		
		byte[] cipherText = p1.encrypt(plainText);
		Assert.assertTrue("cipher text has invalid length", cipherText.length == plainText.length);
		byte[] plainText2 = p2.decrypt(cipherText);
		Assert.assertTrue("decrypted plain text has invalid length", plainText2.length == plainText.length);
		Assert.assertTrue("decrypted plain text does not match original", SimpleKeyAgreementTest.compareByteArray(plainText, plainText2));
	}

	public void testEncryptDecrypt_MultipleBlocks() throws InternalApplicationException {
		byte[] sharedKey = new byte[32];
		for (int i=0; i<sharedKey.length; i++)
			sharedKey[i] = (byte) i;
		InterlockProtocol p1 = new InterlockProtocol(sharedKey, 2, 129, null, useJSSE);
		InterlockProtocol p2 = new InterlockProtocol(sharedKey, 2, 129, null, useJSSE2);
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
	
	public void testSplitAndReassemble_Variant1_Case1() throws InternalApplicationException {
		int messageBytes=16;
		for (int rounds=2; rounds<=50; rounds++) {
			InterlockProtocol p = new InterlockProtocol(new byte[32], rounds, messageBytes*8, null, useJSSE);
			byte[] plainText = new byte[messageBytes];
			for (int i=0; i<plainText.length; i++)
				plainText[i] = (byte) (plainText.length-1-i);
			byte[][] parts = p.split(plainText);
			Assert.assertEquals("number of parts does not match requested number of rounds", rounds, parts.length);
			byte[] plainText2 = p.reassemble(parts);
			Assert.assertTrue("reassembled plain text has invalid length", plainText2.length == plainText.length);
			Assert.assertTrue("reassemlbed plain text does not match original", SimpleKeyAgreementTest.compareByteArray(plainText, plainText2));
		}
	}

	public void testSplitAndReassemble_Variant1_Case2() throws InternalApplicationException {
		for (int rounds=2; rounds<=40; rounds++) {
			// TODO: would rather like to run that up to 128 or 256, but for some reason the ant junit task will abort with out of memory if it's higher
			for (int messageBytes=17; messageBytes<=64; messageBytes+=16) {
				// test a case with only 1 bit in the last block (and thus only one byte in the last block)
				InterlockProtocol p = new InterlockProtocol(new byte[32], rounds, messageBytes*8-7, null, useJSSE);
				byte[] plainText = new byte[p.getCipherTextBlocks()*16];
				for (int i=0; i<plainText.length; i++)
					plainText[i] = (byte) (plainText.length-1-i);
				byte[][] parts = p.split(plainText);
				Assert.assertEquals("number of parts does not match requested number of rounds", rounds, parts.length);
				byte[] plainText2 = p.reassemble(parts);
				Assert.assertTrue("reassembled plain text has invalid length", plainText2.length == plainText.length);
				Assert.assertTrue("reassemlbed plain text does not match original", SimpleKeyAgreementTest.compareByteArray(plainText, plainText2));
				
				// that doesn't help to free up enough heap space...
				plainText = null;
				parts = null;
				plainText2 = null;
				p = null;
				System.gc();
			}
		}
	}

	public void testSplitAndReassemble_Variant2_Case1() throws InternalApplicationException {
		int messageBytes=16;
		for (int rounds=2; rounds<=50; rounds++) {
			InterlockProtocol p = new InterlockProtocol(new byte[32], rounds, messageBytes*8, null, useJSSE);
			byte[] plainText = new byte[messageBytes];
			for (int i=0; i<plainText.length; i++)
				plainText[i] = (byte) (plainText.length-1-i);
			byte[][] parts = p.split(plainText);
			Assert.assertEquals("number of parts does not match requested number of rounds", rounds, parts.length);
			for (int i=0; i<parts.length; i++)
				p.addMessage(parts[i], i);
			byte[] plainText2 = p.reassemble();
			Assert.assertTrue("reassembled plain text has invalid length", plainText2.length == plainText.length);
			Assert.assertTrue("reassemlbed plain text does not match original", SimpleKeyAgreementTest.compareByteArray(plainText, plainText2));
		}
	}

	public void testSplitAndReassemble_Variant2_Case2() throws InternalApplicationException {
		for (int rounds=2; rounds<=40; rounds++) {
			// TODO: would rather like to run that up to 128 or 256, but for some reason the ant junit task will abort with out of memory if it's higher
			for (int messageBytes=17; messageBytes<=64; messageBytes+=16) {
				// test a case with only 1 bit in the last block (and thus only one byte in the last block)
				InterlockProtocol p = new InterlockProtocol(new byte[32], rounds, messageBytes*8-7, null, useJSSE);
				byte[] plainText = new byte[p.getCipherTextBlocks()*16];
				for (int i=0; i<plainText.length; i++)
					plainText[i] = (byte) (plainText.length-1-i);
				byte[][] parts = p.split(plainText);
				Assert.assertEquals("number of parts does not match requested number of rounds", rounds, parts.length);
				for (int i=0; i<parts.length; i++)
					p.addMessage(parts[i], i);
				byte[] plainText2 = p.reassemble();
				Assert.assertTrue("reassembled plain text has invalid length", plainText2.length == plainText.length);
				Assert.assertTrue("reassemlbed plain text does not match original", SimpleKeyAgreementTest.compareByteArray(plainText, plainText2));
				
				// that doesn't help to free up enough heap space...
				plainText = null;
				parts = null;
				plainText2 = null;
				p = null;
				System.gc();
			}
		}
	}

	public void testEncryptSplitReassembleDecrypt_MultipleBlocks_1() throws InternalApplicationException {
		byte[] sharedKey = new byte[32];
		for (int i=0; i<sharedKey.length; i++)
			sharedKey[i] = (byte) i;
		InterlockProtocol p1 = new InterlockProtocol(sharedKey, 2, 129, null, useJSSE);
		InterlockProtocol p2 = new InterlockProtocol(sharedKey, 2, 129, null, useJSSE2);
		// 17 bytes is more than one block, so the protocol should switch from ECB to CBC mode
		byte[] plainText = new byte[17];
		for (int i=0; i<plainText.length; i++)
			plainText[i] = (byte) (plainText.length-1-i);
		
		byte[] cipherText = p1.encrypt(plainText);

		byte[][] parts = p1.split(cipherText);
		Assert.assertEquals("number of parts does not match requested number of rounds", 2, parts.length);
		for (int i=0; i<parts.length; i++)
			p2.addMessage(parts[i], i);

		Assert.assertTrue("cipher text has invalid length", cipherText.length == p1.getCipherTextBlocks() * 16);
		byte[] plainText2 = p2.decrypt(p2.reassemble());
		Assert.assertTrue("decrypted plain text has invalid length", plainText2.length == plainText.length);
		Assert.assertTrue("decrypted plain text does not match original", SimpleKeyAgreementTest.compareByteArray(plainText, plainText2));
	}
	
	public void testEncryptSplitReassembleDecrypt_MultipleBlocks_2() throws InternalApplicationException {
		byte[] sharedKey = new byte[32];
		for (int i=0; i<sharedKey.length; i++)
			sharedKey[i] = (byte) i;
		InterlockProtocol p1 = new InterlockProtocol(sharedKey, 2, 257, null, useJSSE);
		InterlockProtocol p2 = new InterlockProtocol(sharedKey, 2, 257, null, useJSSE2);
		// 33 bytes is more than two blocks, so the protocol should switch from ECB to CBC mode
		byte[] plainText = new byte[33];
		for (int i=0; i<plainText.length; i++)
			plainText[i] = (byte) (plainText.length-1-i);
		
		byte[] cipherText = p1.encrypt(plainText);

		byte[][] parts = p1.split(cipherText);
		Assert.assertEquals("number of parts does not match requested number of rounds", 2, parts.length);
		for (int i=0; i<parts.length; i++)
			p2.addMessage(parts[i], i);

		Assert.assertTrue("cipher text has invalid length", cipherText.length == p1.getCipherTextBlocks() * 16);
		byte[] plainText2 = p2.decrypt(p2.reassemble());
		Assert.assertTrue("decrypted plain text has invalid length", plainText2.length == plainText.length);
		Assert.assertTrue("decrypted plain text does not match original", SimpleKeyAgreementTest.compareByteArray(plainText, plainText2));
	}
	
	public void testExchangeHelper() throws IOException, InterruptedException {
		PipedOutputStream writePipe1 = new PipedOutputStream();
		PipedOutputStream writePipe2 = new PipedOutputStream();
		PipedInputStream readPipe1 = new PipedInputStream(writePipe2);
		PipedInputStream readPipe2 = new PipedInputStream(writePipe1);
		
		final byte[] sharedKey = new byte[32];
		for (int i=0; i<sharedKey.length; i++)
			sharedKey[i] = (byte) i;
		
		class Helper implements Runnable {
			byte[] myMsg;
			byte[] remoteMsg;
			InputStream in;
			OutputStream out;
			boolean myUseJSSE;
			
			public void run() {
				try {
					remoteMsg = InterlockProtocol.interlockExchange(myMsg, in, out, 
							sharedKey, 2, true, false, 0, myUseJSSE,
							null, 0, 0);
				} catch (Exception e) {
					e.printStackTrace();
					Assert.fail();
				}
			}
		}
		
		Helper h1 = new Helper();
		Helper h2 = new Helper();
		h1.in = readPipe1;
		h1.out = writePipe1;
		h1.myMsg = new byte[25];
		h1.myUseJSSE = useJSSE;
		for (int i=0; i<h1.myMsg.length; i++)
			h1.myMsg[i] = (byte) (h1.myMsg.length-1-i);
		h2.in = readPipe2;
		h2.out = writePipe2;
		h2.myMsg = new byte[27];
		h2.myUseJSSE = useJSSE2;
		for (int i=0; i<h1.myMsg.length; i++)
			h1.myMsg[i] = (byte) (h1.myMsg.length-2-i);

		Thread t1 = new Thread(h1);
		Thread t2 = new Thread(h2);
		
		t1.start();
		t2.start();
		t1.join();
		t2.join();
		
		Assert.assertNotNull(h1.remoteMsg);
		Assert.assertNotNull(h2.remoteMsg);
		
		Assert.assertTrue(SimpleKeyAgreementTest.compareByteArray(h1.myMsg, h2.remoteMsg));
		Assert.assertTrue(SimpleKeyAgreementTest.compareByteArray(h2.myMsg, h1.remoteMsg));
	}

	// tests timeout when the remote host doesn't send anything
	public void testExchangeHelperTimeoutAtGreeting() throws InternalApplicationException, IOException {
		PipedOutputStream writePipe1 = new PipedOutputStream();
		PipedOutputStream writePipe2 = new PipedOutputStream();
		PipedInputStream readPipe1 = new PipedInputStream(writePipe2);
		
		final byte[] sharedKey = new byte[32];
		for (int i=0; i<sharedKey.length; i++)
			sharedKey[i] = (byte) i;
		final byte[] myMsg = new byte[23];
		for (int i=0; i<myMsg.length; i++)
			myMsg[i] = (byte) (myMsg.length-1-i);

		byte[] remoteMsg = null;
		try {
			remoteMsg = InterlockProtocol.interlockExchange(myMsg, readPipe1, writePipe1, 
					sharedKey, 2, true, false, 500, useJSSE,
					null, 0, 0);
			Assert.fail();
		} catch (IOException e) {
			Assert.assertTrue(true);
		}
		Assert.assertNull(remoteMsg);
	}

	// this tests the timeout feature at the last round (because one side tries to use 10 rounds while the other wants 11)
	public void testExchangeHelperTimeoutAtRounds() throws IOException, InterruptedException {
		PipedOutputStream writePipe1 = new PipedOutputStream();
		PipedOutputStream writePipe2 = new PipedOutputStream();
		PipedInputStream readPipe1 = new PipedInputStream(writePipe2);
		PipedInputStream readPipe2 = new PipedInputStream(writePipe1);
		
		final byte[] sharedKey = new byte[32];
		for (int i=0; i<sharedKey.length; i++)
			sharedKey[i] = (byte) i;
		
		class Helper implements Runnable {
			byte[] myMsg;
			byte[] remoteMsg;
			InputStream in;
			OutputStream out;
			boolean myUseJSSE;
			int myRounds;
			boolean iShouldFail;
			
			public void run() {
				try {
					remoteMsg = InterlockProtocol.interlockExchange(myMsg, in, out, 
							sharedKey, myRounds, true, false, 500, myUseJSSE,
							null, 0, 0);
					Assert.assertFalse("Should have failed, but didn't", iShouldFail);
				} catch (IOException e) {
					Assert.assertTrue("Should not have failed, but did", iShouldFail);
				} catch (InternalApplicationException e) {
					e.printStackTrace();
					Assert.fail();
				}
			}
		}
		
		Helper h1 = new Helper();
		Helper h2 = new Helper();
		h1.in = readPipe1;
		h1.out = writePipe1;
		h1.myMsg = new byte[25];
		h1.myUseJSSE = useJSSE;
		for (int i=0; i<h1.myMsg.length; i++)
			h1.myMsg[i] = (byte) (h1.myMsg.length-1-i);
		h1.myRounds = 11;
		h1.iShouldFail = true;
		h2.in = readPipe2;
		h2.out = writePipe2;
		h2.myMsg = new byte[27];
		h2.myUseJSSE = useJSSE2;
		for (int i=0; i<h1.myMsg.length; i++)
			h1.myMsg[i] = (byte) (h1.myMsg.length-2-i);
		h2.myRounds = 10;
		h2.iShouldFail = false;

		Thread t1 = new Thread(h1);
		Thread t2 = new Thread(h2);
		
		t1.start();
		t2.start();
		t1.join();
		t2.join();
		
		Assert.assertNull(h1.remoteMsg);
		Assert.assertNotNull(h2.remoteMsg);
	}

	private void testExchangeHelperMirrorAttack(boolean preventAttack) throws IOException, InterruptedException {
		final boolean preventAttackF = preventAttack;
		
		PipedOutputStream writePipe1 = new PipedOutputStream();
		PipedOutputStream writePipe2 = new PipedOutputStream();
		PipedInputStream readPipe1 = new PipedInputStream(writePipe2);
		PipedInputStream readPipe2 = new PipedInputStream(writePipe1);
		
		final byte[] sharedKey = new byte[32];
		for (int i=0; i<sharedKey.length; i++)
			sharedKey[i] = (byte) i;
		
		class Helper implements Runnable {
			byte[] myMsg;
			byte[] remoteMsg;
			InputStream in;
			OutputStream out;
			boolean myUseJSSE;
			
			public void run() {
				try {
					remoteMsg = InterlockProtocol.interlockExchange(myMsg, in, out, 
							sharedKey, 2, preventAttackF, false, 0, myUseJSSE,
							null, 0, 0);
				} catch (Exception e) {
					e.printStackTrace();
					Assert.fail();
				}
			}
		}
		
		class MirrorAttacker implements Runnable {
			InputStream in;
			OutputStream out;

			public void run() {
				try {
					int c = in.read();
					while (c != -1) {
						out.write(c);
						c = in.read();
					}
				} catch (IOException e) {
					// this is expected when the pipe is closed
					Assert.assertEquals("Pipe broken", e.getMessage());
				} catch (Exception e) {
					e.printStackTrace();
					Assert.fail();
				}
			}
		}
		
		Helper h1 = new Helper();
		MirrorAttacker h2 = new MirrorAttacker();
		h1.in = readPipe1;
		h1.out = writePipe1;
		h1.myMsg = new byte[25];
		h1.myUseJSSE = useJSSE;
		for (int i=0; i<h1.myMsg.length; i++)
			h1.myMsg[i] = (byte) (h1.myMsg.length-1-i);
		h2.in = readPipe2;
		h2.out = writePipe2;

		Thread t1 = new Thread(h1);
		Thread t2 = new Thread(h2);
		
		t1.start();
		t2.start();
		t1.join();
		t2.join();

		if (preventAttackF) {
			Assert.assertNull("Mirror attack succeeded", h1.remoteMsg);
		}
		else {
			Assert.assertNotNull("Mirror attack should have succeeded", h1.remoteMsg);
			Assert.assertTrue("Mirror attack should have succeeded", SimpleKeyAgreementTest.compareByteArray(h1.myMsg, h1.remoteMsg));
		}
	}

	public void testExchangeHelperMirrorAttackSuccess() throws IOException, InterruptedException {
		testExchangeHelperMirrorAttack(false);
	}

	public void testExchangeHelperMirrorAttackPrevented() throws IOException, InterruptedException {
		testExchangeHelperMirrorAttack(true);
	}

	// this starts, from the point of view of one host, 3 parallel runs
	private void testExchangeHelperInterlockGroup(int timeoutMs) throws IOException, InterruptedException {
		PipedOutputStream[] myWritePipes = new PipedOutputStream[3];
		for (int i=0; i<myWritePipes.length; i++) {
			myWritePipes[i] = new PipedOutputStream();
		}
		PipedOutputStream[] theirWritePipes = new PipedOutputStream[3];
		for (int i=0; i<theirWritePipes.length; i++) {
			theirWritePipes[i] = new PipedOutputStream();
		}
		PipedInputStream[] myReadPipes = new PipedInputStream[3];
		for (int i=0; i<myReadPipes.length; i++) {
			myReadPipes[i] = new PipedInputStream(theirWritePipes[i]);
		}
		PipedInputStream[] theirReadPipes = new PipedInputStream[3];
		for (int i=0; i<theirReadPipes.length; i++) {
			theirReadPipes[i] = new PipedInputStream(myWritePipes[i]);
		}
		
		final byte[] sharedKey = new byte[32];
		for (int i=0; i<sharedKey.length; i++)
			sharedKey[i] = (byte) i;
		final int timeout = timeoutMs;
		
		class Helper implements Runnable {
			byte[] myMsg;
			byte[] remoteMsg;
			InputStream in;
			OutputStream out;
			boolean myUseJSSE;
			BitSet group = null;
			int groupSize = 0;
			int instanceNum = 0;
			
			public void run() {
				try {
					remoteMsg = InterlockProtocol.interlockExchange(myMsg, in, out, 
							sharedKey, 2, true, false, timeout, myUseJSSE,
							group, groupSize, instanceNum);
				} catch (Exception e) {
					e.printStackTrace();
					Assert.fail();
				}
			}
		}

		final BitSet interlockGroup = new BitSet();
		byte[] myMsg = new byte[25];
		for (int i=0; i<myMsg.length; i++)
			myMsg[i] = (byte) (myMsg.length-1-i);
		Helper myHelpers[] = new Helper[3];
		for (int i=0; i<myHelpers.length; i++) {
			myHelpers[i] = new Helper();
			myHelpers[i].in = myReadPipes[i];
			myHelpers[i].out = myWritePipes[i];
			myHelpers[i].myUseJSSE = useJSSE;
			myHelpers[i].group = interlockGroup;
			myHelpers[i].groupSize = 3;
			myHelpers[i].instanceNum = i;
			myHelpers[i].myMsg = myMsg;
		}
		Helper theirHelpers[] = new Helper[3];
		for (int i=0; i<theirHelpers.length; i++) {
			theirHelpers[i] = new Helper();
			theirHelpers[i].in = theirReadPipes[i];
			theirHelpers[i].out = theirWritePipes[i];
			theirHelpers[i].myUseJSSE = useJSSE2;
			theirHelpers[i].myMsg = new byte[27];
			for (int j=0; j<theirHelpers[i].myMsg.length; j++)
				theirHelpers[i].myMsg[j] = (byte) (theirHelpers[i].myMsg.length-2-j+2*i);
		}

		Thread[] threads = new Thread[6];
		for (int i=0; i<threads.length; i++) {
			if (i<3) threads[i] = new Thread(myHelpers[i]);
			else threads[i] = new Thread(theirHelpers[i-3]);
			threads[i].start();
		}
		for (int i=0; i<threads.length; i++) {
			threads[i].join();
		}
		
		for (int i=0; i<myHelpers.length; i++) {
			Assert.assertNotNull("did not get their message from " + i, myHelpers[i].remoteMsg);
		}
		for (int i=0; i<theirHelpers.length; i++) {
			Assert.assertNotNull("they did not get my message to " + i, theirHelpers[i].remoteMsg);
		}
		for (int i=0; i<myHelpers.length; i++) {
			Assert.assertTrue("my message does not match their received " + i, SimpleKeyAgreementTest.compareByteArray(myHelpers[i].myMsg, theirHelpers[i].remoteMsg));
			Assert.assertTrue("their message does not match my received " + i, SimpleKeyAgreementTest.compareByteArray(myHelpers[i].remoteMsg, theirHelpers[i].myMsg));
		}
	}

	public void testExchangeHelperInterlockGroupNoTimeout() throws IOException, InterruptedException {
		testExchangeHelperInterlockGroup(0);
	}

	public void testExchangeHelperInterlockGroupWithTimeout() throws IOException, InterruptedException {
		testExchangeHelperInterlockGroup(500);
	}

	// this tries to copy the steps performed in DongleProtocolHandler
	public void testInterlockForRelateDongleProtocol() throws InternalApplicationException {
		final int EntropyBitsPerRound = 3;
		final int EntropyBitsOffset = 7;
		final int NonceByteLength = 16;
		
		int delta = 2;
		
		for (int rounds=2; rounds<=43; rounds++) {
			InterlockProtocol interlockUs = new InterlockProtocol(null, rounds, EntropyBitsPerRound*rounds, "test", useJSSE);
			for (int round=0; round<rounds; round++) {
				byte delay = (byte) (delta >> EntropyBitsOffset);
				int curBits = (NonceByteLength * 8) - (EntropyBitsPerRound * round) % (NonceByteLength * 8) >= EntropyBitsPerRound ? 
						EntropyBitsPerRound : 
							(NonceByteLength * 8) - (EntropyBitsPerRound * round) % (NonceByteLength * 8);
				Assert.assertTrue("Could not add round", interlockUs.addMessage(new byte[] {delay}, 
						(round * EntropyBitsPerRound) % (NonceByteLength * 8), 
						curBits, round));
			}
			byte[] receivedDelays = interlockUs.reassemble();
			Assert.assertNotNull("Could not reassamble", receivedDelays);
		}
	}
}
