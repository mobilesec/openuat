/* Copyright Rene Mayrhofer
 * File created 2006-05-12
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.authentication.test;

import java.io.IOException;
import java.net.InetAddress;

import org.eu.mayrhofer.authentication.CKPOverUDP;
import org.eu.mayrhofer.authentication.exceptions.InternalApplicationException;

import junit.framework.Assert;
import junit.framework.TestCase;

public class CKPOverUDPTest extends TestCase {
	private class TestHelper extends CKPOverUDP {
		protected TestHelper(int udpReceivePort, int udpSendPort, String multicastGroup, String instanceId, boolean broadcastCandidates, boolean sendMatches, boolean useJSSE) throws IOException {
			super(udpReceivePort, udpSendPort, multicastGroup, instanceId, broadcastCandidates, sendMatches,
					2, 0, useJSSE);
		}

		int numResetHookCalled = 0;
		int numSucceededHookCalled = 0;
		int numFailedHookCalled = 0;
		int numProgressHookCalled = 0;
		byte[] sharedSessKey = null;
		
		protected void resetHook() {
			numResetHookCalled++;
		}

		protected void protocolSucceededHook(InetAddress remote, byte[] sharedSessionKey) {
			numSucceededHookCalled++;
System.out.println("1 " + numSucceededHookCalled);
			this.sharedSessKey = sharedSessionKey;
		}

		protected void protocolFailedHook(InetAddress remote, Exception e, String message) {
			numFailedHookCalled++;
System.out.println("2 " + numFailedHookCalled);
			
		}

		protected void protocolProgressHook(InetAddress remote, int cur, int max, String message) {
			numProgressHookCalled++;
System.out.println("3 " + numProgressHookCalled);
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
	}
	
	public void testCompleteRun_SymmetricNoSendMatches_Interlocked() throws IOException, InternalApplicationException, InterruptedException {
		helper1 = new TestHelper(54321, 54322, "230.20.20.20.", "p1", true, false, useJSSE1);
		helper2 = new TestHelper(54322, 54321, "230.20.20.20.", "p2", true, false, useJSSE2);
		
		helper1.addCandidates(keyParts_round1_side1);
		helper2.addCandidates(keyParts_round1_side2);
		helper1.addCandidates(keyParts_round2_side1);
		helper2.addCandidates(keyParts_round2_side2);
		
		while ((helper1.numFailedHookCalled == 0 && helper1.numSucceededHookCalled == 0) ||
				(helper2.numFailedHookCalled == 0 && helper2.numSucceededHookCalled == 0)) {
			Thread.sleep(50);
		}
		
		Assert.assertEquals(1, helper1.numSucceededHookCalled);
		Assert.assertEquals(1, helper2.numSucceededHookCalled);
		Assert.assertEquals(0, helper1.numFailedHookCalled);
		Assert.assertEquals(0, helper2.numFailedHookCalled);

		Assert.assertTrue(SimpleKeyAgreementTest.compareByteArray(helper1.sharedSessKey, helper2.sharedSessKey));
	}
}
