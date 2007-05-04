/* Copyright Rene Mayrhofer
 * File created 2006-05-12
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.authentication.test;

import java.io.IOException;

import org.openuat.authentication.CKPOverUDP;
import org.openuat.authentication.exceptions.InternalApplicationException;

import junit.framework.Assert;
import junit.framework.TestCase;

public class CKPOverUDPTest extends TestCase {
	private class TestHelper extends CKPOverUDP {
		protected TestHelper(int udpReceivePort, int udpSendPort, String multicastGroup, String instanceId, boolean broadcastCandidates, boolean sendMatches, boolean useJSSE) throws IOException {
			super(udpReceivePort, udpSendPort, multicastGroup, instanceId, broadcastCandidates, sendMatches,
					30, 5, 60, 1f, 0, 0f, 2, useJSSE);
		}

		int numResetHookCalled = 0;
		int numSucceededHookCalled = 0;
		int numFailedHookCalled = 0;
		int numProgressHookCalled = 0;
		byte[] sharedSessKey = null;
		
		protected void resetHook() {
			numResetHookCalled++;
		}

		protected void protocolSucceededHook(String remote, byte[] sharedSessionKey, float matchingFraction) {
			numSucceededHookCalled++;
			this.sharedSessKey = sharedSessionKey;
		}

		protected void protocolFailedHook(String remote, float matchingFraction, Exception e, String message) {
			numFailedHookCalled++;
		}

		protected void protocolProgressHook(String remote, int cur, int max, String message) {
			numProgressHookCalled++;
		}
		
		void addCandidates(byte[][] keyParts) throws InternalApplicationException, IOException {
			this.addCandidates(keyParts,0);
		}
	}

	protected boolean useJSSE1 = true;
	protected boolean useJSSE2 = true;
	
	private TestHelper helper1;
	private TestHelper helper2;

	byte[][] keyParts_round1_side1;
	byte[][] keyParts_round1_side2;
	byte[][] keyParts_round2_side1;
	byte[][] keyParts_round2_side2;
	byte[][] keyParts_round2_side2_noMatch;
	
	public CKPOverUDPTest(String s) {
		super(s);
	}

	public void setUp() {
		// 2 matches 1
		keyParts_round1_side1 = new byte[][] { 
				new byte[] {1, 2, 3, 4, 5, 4, 3, 2, 1},
				new byte[] {1, 2, 4, 4, 5, 4, 3, 2, 1},
				new byte[] {1, 2, 3, 4, 5, 4, 7, 2, 1},
				new byte[] {1, 2, 3, 4, 5, 4, 3, 2, 2} };
		keyParts_round1_side2 = new byte[][] { 
				new byte[] {2, 2, 3, 4, 5, 4, 3, 2, 1},
				new byte[] {1, 2, 3, 4, 5, 4, 7, 2, 1},
				new byte[] {2, 2, 4, 4, 5, 4, 3, 2, 1},
				new byte[] {2, 2, 3, 4, 5, 4, 3, 2, 2} };

		// 4 matches 1 and 2 matches 3
		keyParts_round2_side1 = new byte[][] { 
				new byte[] {5, 2, 3, 4, 5, 4, 3, 2, 3},
				new byte[] {4, 2, 4, 4, 5, 4, 3, 2, 3},
				new byte[] {3, 2, 3, 4, 5, 4, 7, 2, 3},
				new byte[] {2, 2, 3, 4, 5, 4, 7, 2, 3},
				new byte[] {1, 2, 3, 4, 5, 4, 3, 2, 4} };
		keyParts_round2_side2 = new byte[][] { 
				new byte[] {9, 2, 3, 4, 5, 4, 3, 2, 5},
				new byte[] {1, 2, 3, 4, 5, 4, 3, 2, 4},
				new byte[] {7, 2, 3, 4, 5, 4, 3, 2, 3},
				new byte[] {3, 2, 3, 4, 5, 4, 7, 2, 3},
				new byte[] {5, 2, 3, 4, 5, 4, 3, 2, 8} };
		keyParts_round2_side2_noMatch = new byte[][] { 
				new byte[] {10, 2, 3, 4, 5, 4, 3, 2, 5},
				new byte[] {10, 2, 3, 4, 5, 4, 3, 2, 4},
				new byte[] {10, 2, 3, 4, 5, 4, 3, 2, 3},
				new byte[] {10, 2, 3, 4, 5, 4, 7, 2, 3},
				new byte[] {10, 2, 3, 4, 5, 4, 3, 2, 8} };
	}
	
/*	public void testCompleteRun_SymmetricNoSendMatches_Interlocked() throws IOException, InternalApplicationException, InterruptedException {
		helper1 = new TestHelper(54321, 54322, "127.0.0.1", "p1", true, false, useJSSE1);
		helper2 = new TestHelper(54322, 54321, "127.0.0.1", "p2", true, false, useJSSE2);
		
		helper1.addCandidates(keyParts_round1_side1);
		helper2.addCandidates(keyParts_round1_side2);
		helper1.addCandidates(keyParts_round2_side1);
		helper2.addCandidates(keyParts_round2_side2);
		
		int tries=0;
		while (((helper1.numFailedHookCalled == 0 && helper1.numSucceededHookCalled == 0) ||
				(helper2.numFailedHookCalled == 0 && helper2.numSucceededHookCalled == 0)) && 
				tries < 50) {
			Thread.sleep(100);
			tries++;
		}
		Assert.assertTrue("Protocol did not complete", tries<50);
		
		Assert.assertEquals(1, helper1.numSucceededHookCalled);
		Assert.assertEquals(1, helper2.numSucceededHookCalled);
		Assert.assertEquals(0, helper1.numFailedHookCalled);
		Assert.assertEquals(0, helper2.numFailedHookCalled);

		Assert.assertTrue(SimpleKeyAgreementTest.compareByteArray(helper1.sharedSessKey, helper2.sharedSessKey));

		helper1.dispose();
		helper1 = null;
		helper2.dispose();
		helper2 = null;
		System.gc();
	}*/
	
	public void testFailedRun_SymmetricNoSendMatches_Interlocked() throws IOException, InternalApplicationException, InterruptedException {
		helper1 = new TestHelper(54321, 54322, "127.0.0.1", "p1", true, false, useJSSE1);
		helper2 = new TestHelper(54322, 54321, "127.0.0.1", "p2", true, false, useJSSE2);
		
		helper1.addCandidates(keyParts_round1_side1);
		helper2.addCandidates(keyParts_round1_side2);
		helper1.addCandidates(keyParts_round2_side1);
		helper2.addCandidates(keyParts_round2_side2_noMatch);
		// and need a third round so that the negative criteria will be fulfilled
		helper1.addCandidates(keyParts_round2_side1);
		helper2.addCandidates(keyParts_round2_side2_noMatch);
		
		int tries=0;
		while (((helper1.numFailedHookCalled == 0 && helper1.numSucceededHookCalled == 0) ||
				(helper2.numFailedHookCalled == 0 && helper2.numSucceededHookCalled == 0)) && 
				tries < 50) {
			Thread.sleep(100);
			tries++;
		}
		Assert.assertTrue("Protocol did not complete", tries<50);
		
		Assert.assertEquals(0, helper1.numSucceededHookCalled);
		Assert.assertEquals(0, helper2.numSucceededHookCalled);
		// depending on timing, the failure event may be sent once or up to 4 times - but that's ok...
		Assert.assertTrue(helper1.numFailedHookCalled >= 1 && helper1.numFailedHookCalled <= 4);
		Assert.assertTrue(helper2.numFailedHookCalled >= 1 && helper2.numFailedHookCalled <= 4);

		helper1.dispose();
		helper1 = null;
		helper2.dispose();
		helper2 = null;
		System.gc();
	}
	
	// TODO: enable again
/*	public void testCompleteRun_SymmetricNoSendMatches_Sequenced1() throws IOException, InternalApplicationException, InterruptedException {
		// this simulates with localhost communication
		helper1 = new TestHelper(54321, 54322, "127.0.0.1", "p1", true, false, useJSSE1);
		helper2 = new TestHelper(54322, 54321, "127.0.0.1", "p2", true, false, useJSSE2);
		
		helper1.addCandidates(keyParts_round1_side1);
		helper1.addCandidates(keyParts_round2_side1);
		helper2.addCandidates(keyParts_round1_side2);
		helper2.addCandidates(keyParts_round2_side2);
		
		int tries=0;
		while (((helper1.numFailedHookCalled == 0 && helper1.numSucceededHookCalled == 0) ||
				(helper2.numFailedHookCalled == 0 && helper2.numSucceededHookCalled == 0)) && 
				tries < 50) {
			Thread.sleep(100);
			tries++;
		}
		Assert.assertTrue("Protocol did not complete", tries<50);
		
		Assert.assertEquals(1, helper1.numSucceededHookCalled);
		Assert.assertEquals(1, helper2.numSucceededHookCalled);
		Assert.assertEquals(0, helper1.numFailedHookCalled);
		Assert.assertEquals(0, helper2.numFailedHookCalled);

		Assert.assertTrue(SimpleKeyAgreementTest.compareByteArray(helper1.sharedSessKey, helper2.sharedSessKey));

		helper1.dispose();
		helper1 = null;
		helper2.dispose();
		helper2 = null;
		System.gc();
	}

	public void testCompleteRun_SymmetricNoSendMatches_Sequenced2() throws IOException, InternalApplicationException, InterruptedException {
		// this simulates with localhost communication
		helper1 = new TestHelper(54321, 54322, "127.0.0.1", "p1", true, false, useJSSE1);
		helper2 = new TestHelper(54322, 54321, "127.0.0.1", "p2", true, false, useJSSE2);
		
		helper2.addCandidates(keyParts_round1_side2);
		helper2.addCandidates(keyParts_round2_side2);
		helper1.addCandidates(keyParts_round1_side1);
		helper1.addCandidates(keyParts_round2_side1);
		
		int tries=0;
		while (((helper1.numFailedHookCalled == 0 && helper1.numSucceededHookCalled == 0) ||
				(helper2.numFailedHookCalled == 0 && helper2.numSucceededHookCalled == 0)) && 
				tries < 50) {
			Thread.sleep(100);
			tries++;
		}
		Assert.assertTrue("Protocol did not complete", tries<50);
		
		Assert.assertEquals(1, helper1.numSucceededHookCalled);
		Assert.assertEquals(1, helper2.numSucceededHookCalled);
		Assert.assertEquals(0, helper1.numFailedHookCalled);
		Assert.assertEquals(0, helper2.numFailedHookCalled);

		Assert.assertTrue(SimpleKeyAgreementTest.compareByteArray(helper1.sharedSessKey, helper2.sharedSessKey));

		helper1.dispose();
		helper1 = null;
		helper2.dispose();
		helper2 = null;
		System.gc();
	}

	public void testCompleteRun_AsymmetricOneSideSendMatches_Interlocked() throws IOException, InternalApplicationException, InterruptedException {
		// this simulates with localhost communication
		helper1 = new TestHelper(54321, 54322, "127.0.0.1", "p1", true, false, useJSSE1); // broadcast candidates
		helper2 = new TestHelper(54322, 54321, "127.0.0.1", "p2", false, true, useJSSE2); // send matches
		
		helper1.addCandidates(keyParts_round1_side1);
		helper2.addCandidates(keyParts_round1_side2);
		helper1.addCandidates(keyParts_round2_side1);
		helper2.addCandidates(keyParts_round2_side2);
		
		int tries=0;
		while (((helper1.numFailedHookCalled == 0 && helper1.numSucceededHookCalled == 0) ||
				(helper2.numFailedHookCalled == 0 && helper2.numSucceededHookCalled == 0)) && 
				tries < 50) {
			Thread.sleep(100);
			tries++;
		}
		Assert.assertTrue("Protocol did not complete", tries<50);
		
		Assert.assertEquals(1, helper1.numSucceededHookCalled);
		Assert.assertEquals(1, helper2.numSucceededHookCalled);
		Assert.assertEquals(0, helper1.numFailedHookCalled);
		Assert.assertEquals(0, helper2.numFailedHookCalled);

		Assert.assertTrue(SimpleKeyAgreementTest.compareByteArray(helper1.sharedSessKey, helper2.sharedSessKey));

		helper1.dispose();
		helper1 = null;
		helper2.dispose();
		helper2 = null;
		System.gc();
	}
	
	public void testCompleteRun_AsymmetricOneSideSendMatches_Sequenced1() throws IOException, InternalApplicationException, InterruptedException {
		// this simulates with localhost communication
		helper1 = new TestHelper(54321, 54322, "127.0.0.1", "p1", true, false, useJSSE1); // broadcast candidates
		helper2 = new TestHelper(54322, 54321, "127.0.0.1", "p2", false, true, useJSSE2); // send matches
		
		helper1.addCandidates(keyParts_round1_side1);
		helper1.addCandidates(keyParts_round2_side1);
		helper2.addCandidates(keyParts_round1_side2);
		helper2.addCandidates(keyParts_round2_side2);
		
		int tries=0;
		while (((helper1.numFailedHookCalled == 0 && helper1.numSucceededHookCalled == 0) ||
				(helper2.numFailedHookCalled == 0 && helper2.numSucceededHookCalled == 0)) && 
				tries < 50) {
			Thread.sleep(100);
			tries++;
		}
		Assert.assertTrue("Protocol did not complete", tries<50);
		
		Assert.assertEquals(1, helper1.numSucceededHookCalled);
		Assert.assertEquals(1, helper2.numSucceededHookCalled);
		Assert.assertEquals(0, helper1.numFailedHookCalled);
		Assert.assertEquals(0, helper2.numFailedHookCalled);

		Assert.assertTrue(SimpleKeyAgreementTest.compareByteArray(helper1.sharedSessKey, helper2.sharedSessKey));

		helper1.dispose();
		helper1 = null;
		helper2.dispose();
		helper2 = null;
		System.gc();
	}

	public void testCompleteRun_AsymmetricOneSideSendMatches_Sequenced2() throws IOException, InternalApplicationException, InterruptedException {
		// this simulates with localhost communication
		helper1 = new TestHelper(54321, 54322, "127.0.0.1", "p1", true, false, useJSSE1); // broadcast candidates
		helper2 = new TestHelper(54322, 54321, "127.0.0.1", "p2", false, true, useJSSE2); // send matches
		
		helper2.addCandidates(keyParts_round1_side2);
		helper2.addCandidates(keyParts_round2_side2);
		helper1.addCandidates(keyParts_round1_side1);
		helper1.addCandidates(keyParts_round2_side1);
		
		int tries=0;
		while (((helper1.numFailedHookCalled == 0 && helper1.numSucceededHookCalled == 0) ||
				(helper2.numFailedHookCalled == 0 && helper2.numSucceededHookCalled == 0)) && 
				tries < 50) {
			Thread.sleep(100);
			tries++;
		}
		Assert.assertTrue("Protocol did not complete", tries<50);
		
		Assert.assertEquals(1, helper1.numSucceededHookCalled);
		Assert.assertEquals(1, helper2.numSucceededHookCalled);
		Assert.assertEquals(0, helper1.numFailedHookCalled);
		Assert.assertEquals(0, helper2.numFailedHookCalled);

		Assert.assertTrue(SimpleKeyAgreementTest.compareByteArray(helper1.sharedSessKey, helper2.sharedSessKey));

		helper1.dispose();
		helper1 = null;
		helper2.dispose();
		helper2 = null;
		System.gc();
	}*/
}
