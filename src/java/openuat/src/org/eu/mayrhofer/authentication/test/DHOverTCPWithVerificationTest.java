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
import java.net.Socket;
import java.net.UnknownHostException;

import junit.framework.Assert;
import junit.framework.TestCase;

import org.eu.mayrhofer.authentication.DHOverTCPWithVerification;

public class DHOverTCPWithVerificationTest extends TestCase {
	private class TestHelper extends DHOverTCPWithVerification {
		protected TestHelper(int tcpPort, boolean keepSocketConnected, String instanceId, boolean useJSSE, boolean succeed) {
			super(tcpPort, keepSocketConnected, instanceId, useJSSE);
			this.succeed = succeed;
		}

		int numResetHookCalled = 0;
		int numSucceededHookCalled = 0;
		int numFailedHookCalled = 0;
		int numProgressHookCalled = 0;
		byte[] sharedAuthKey = null;
		byte[] sharedSessKey = null;
		private boolean succeed;
		String param;
		
		Object optRemoteIdIn = null, optRemoteIdOut = null;
		String optParamIn = null, optParamOut = null;
		
		protected void startVerification(byte[] sharedAuthenticationKey, InetAddress remote, String param, Socket socketToRemote) {
			this.param = param;
			this.sharedAuthKey = sharedAuthenticationKey;
			
			if (succeed)
				this.verificationSuccess(optRemoteIdIn, optParamIn);
			else
				this.verificationFailure(optRemoteIdIn, optParamIn, null, null);
		}

		protected void resetHook() {
			numResetHookCalled++;
		}

		protected void protocolSucceededHook(InetAddress remote, Object optionalRemoteId, String optionalParameterFromRemote, byte[] sharedSessionKey) {
			System.out.println("-----------------------------------------------------------------------------------------");
			numSucceededHookCalled++;
			this.optRemoteIdOut = optionalRemoteId;
			this.optParamOut = optionalParameterFromRemote;
			this.sharedSessKey = sharedSessionKey;
		}

		protected void protocolFailedHook(InetAddress remote, Object optionalRemoteId, Exception e, String message) {
			numFailedHookCalled++;
			this.optRemoteIdOut = optionalRemoteId;
		}

		protected void protocolProgressHook(InetAddress remote, Object optionalRemoteId, int cur, int max, String message) {
			numProgressHookCalled++;
			this.optRemoteIdOut = optionalRemoteId;
		}
		
		public void startAuthentication(String param) throws UnknownHostException, IOException {
			this.startAuthentication("127.0.0.1", param);
		}
	}

	protected boolean useJSSE1 = true;
	protected boolean useJSSE2 = true;
	
	private TestHelper helper1;
	private TestHelper helper2;
	
	// TODO: this test breaks only sometimes: when the server sends its 'ACK TEST1' first, and the 
	// client sends later, the client will never get the string. when it's the other way around (the
	// client sends its 'ACK TEST2' first), it works....
	public void DISABLED_testCompleteRun_Success() throws IOException, InterruptedException {
		helper1 = new TestHelper(54326, false, "server", useJSSE1, true);
		helper2 = new TestHelper(54326, false, "client", useJSSE2, true);

		String param = "TEST";
		String param1 = "TEST1";
		String param2 = "TEST2";
		Integer remoteId1 = new Integer(1);
		Integer remoteId2 = new Integer(2);
		
		helper1.optParamIn = param1;
		helper2.optParamIn = param2;
		helper1.optRemoteIdIn = remoteId1;
		helper2.optRemoteIdIn = remoteId2;
		
		helper1.startServer();
		helper2.startAuthentication(param);
		
		while ((helper1.numFailedHookCalled == 0 && helper1.numSucceededHookCalled == 0) ||
				(helper2.numFailedHookCalled == 0 && helper2.numSucceededHookCalled == 0)) {
			Thread.sleep(50);
		}
		
		Assert.assertEquals(1, helper1.numSucceededHookCalled);
		Assert.assertEquals(1, helper2.numSucceededHookCalled);
		Assert.assertEquals(0, helper1.numFailedHookCalled);
		Assert.assertEquals(0, helper2.numFailedHookCalled);
		
		Assert.assertNotNull(helper1.param);
		Assert.assertNotNull(helper2.param);
		Assert.assertEquals(param, helper1.param);
		Assert.assertEquals(param, helper2.param);

		Assert.assertNotNull(helper1.optParamOut);
		Assert.assertNotNull(helper2.optParamOut);
		Assert.assertNotNull(helper1.optRemoteIdOut);
		Assert.assertNotNull(helper2.optRemoteIdOut);
		
		Assert.assertEquals(param1, helper2.optParamOut);
		Assert.assertEquals(param2, helper1.optParamOut);
		Assert.assertEquals(remoteId1, helper1.optRemoteIdOut);
		Assert.assertEquals(remoteId2, helper2.optRemoteIdOut);
		
		Assert.assertTrue(SimpleKeyAgreementTest.compareByteArray(helper1.sharedAuthKey, helper2.sharedAuthKey));
		Assert.assertTrue(SimpleKeyAgreementTest.compareByteArray(helper1.sharedSessKey, helper2.sharedSessKey));
		Assert.assertFalse(SimpleKeyAgreementTest.compareByteArray(helper1.sharedSessKey, helper1.sharedAuthKey));
		Assert.assertFalse(SimpleKeyAgreementTest.compareByteArray(helper2.sharedSessKey, helper2.sharedAuthKey));
	}
	
	public void testCompleteRun_Failure1() throws InterruptedException, IOException {
		helper1 = new TestHelper(54327, false, "server", useJSSE1, true);
		helper2 = new TestHelper(54327, false, "client", useJSSE2, false);
		
		String param = "TEST";
		String param1 = "TEST1";
		String param2 = "TEST2";
		Integer remoteId1 = new Integer(1);
		Integer remoteId2 = new Integer(2);
		
		helper1.optParamIn = param1;
		helper2.optParamIn = param2;
		helper1.optRemoteIdIn = remoteId1;
		helper2.optRemoteIdIn = remoteId2;
		
		helper1.startServer();
		helper2.startAuthentication(param);
		
		while ((helper1.numFailedHookCalled == 0 && helper1.numSucceededHookCalled == 0) ||
				(helper2.numFailedHookCalled == 0 && helper2.numSucceededHookCalled == 0)) {
			Thread.sleep(50);
		}
		
		Assert.assertEquals(0, helper1.numSucceededHookCalled);
		Assert.assertEquals(0, helper2.numSucceededHookCalled);
		Assert.assertEquals(1, helper1.numFailedHookCalled);
		Assert.assertEquals(1, helper2.numFailedHookCalled);

		Assert.assertNotNull(helper1.param);
		Assert.assertNotNull(helper2.param);
		Assert.assertEquals(param, helper1.param);
		Assert.assertEquals(param, helper2.param);

		Assert.assertNull(helper1.optParamOut);
		Assert.assertNull(helper2.optParamOut);
		Assert.assertNotNull(helper1.optRemoteIdOut);
		Assert.assertNotNull(helper2.optRemoteIdOut);
		
		Assert.assertEquals(remoteId1, helper1.optRemoteIdOut);
		Assert.assertEquals(remoteId2, helper2.optRemoteIdOut);
	}

	public void testCompleteRun_Failure2() throws InterruptedException, IOException {
		helper1 = new TestHelper(54328, false, "server", useJSSE1, false);
		helper2 = new TestHelper(54328, false, "client", useJSSE2, true);
		
		String param = "TEST";
		String param1 = "TEST1";
		String param2 = "TEST2";
		Integer remoteId1 = new Integer(1);
		Integer remoteId2 = new Integer(2);
		
		helper1.optParamIn = param1;
		helper2.optParamIn = param2;
		helper1.optRemoteIdIn = remoteId1;
		helper2.optRemoteIdIn = remoteId2;
		
		helper1.startServer();
		helper2.startAuthentication(param);
		
		while ((helper1.numFailedHookCalled == 0 && helper1.numSucceededHookCalled == 0) ||
				(helper2.numFailedHookCalled == 0 && helper2.numSucceededHookCalled == 0)) {
			Thread.sleep(50);
		}
		
		Assert.assertEquals(0, helper1.numSucceededHookCalled);
		Assert.assertEquals(0, helper2.numSucceededHookCalled);
		Assert.assertEquals(1, helper1.numFailedHookCalled);
		Assert.assertEquals(1, helper2.numFailedHookCalled);

		Assert.assertNotNull(helper1.param);
		Assert.assertNotNull(helper2.param);
		Assert.assertEquals(param, helper1.param);
		Assert.assertEquals(param, helper2.param);

		Assert.assertNull(helper1.optParamOut);
		Assert.assertNull(helper2.optParamOut);
		Assert.assertNotNull(helper1.optRemoteIdOut);
		Assert.assertNotNull(helper2.optRemoteIdOut);
		
		Assert.assertEquals(remoteId1, helper1.optRemoteIdOut);
		Assert.assertEquals(remoteId2, helper2.optRemoteIdOut);
	}

	public void testCompleteRun_Failure3() throws InterruptedException, IOException {
		helper1 = new TestHelper(54329, false, "server", useJSSE1, false);
		helper2 = new TestHelper(54329, false, "client", useJSSE2, false);
		
		String param = "TEST";
		String param1 = "TEST1";
		String param2 = "TEST2";
		Integer remoteId1 = new Integer(1);
		Integer remoteId2 = new Integer(2);
		
		helper1.optParamIn = param1;
		helper2.optParamIn = param2;
		helper1.optRemoteIdIn = remoteId1;
		helper2.optRemoteIdIn = remoteId2;
		
		helper1.startServer();
		helper2.startAuthentication(param);
		
		while ((helper1.numFailedHookCalled == 0 && helper1.numSucceededHookCalled == 0) ||
				(helper2.numFailedHookCalled == 0 && helper2.numSucceededHookCalled == 0)) {
			Thread.sleep(50);
		}
		
		Assert.assertEquals(0, helper1.numSucceededHookCalled);
		Assert.assertEquals(0, helper2.numSucceededHookCalled);
		Assert.assertEquals(1, helper1.numFailedHookCalled);
		Assert.assertEquals(1, helper2.numFailedHookCalled);

		Assert.assertNotNull(helper1.param);
		Assert.assertNotNull(helper2.param);
		Assert.assertEquals(param, helper1.param);
		Assert.assertEquals(param, helper2.param);

		Assert.assertNull(helper1.optParamOut);
		Assert.assertNull(helper2.optParamOut);
		Assert.assertNotNull(helper1.optRemoteIdOut);
		Assert.assertNotNull(helper2.optRemoteIdOut);
		
		Assert.assertEquals(remoteId1, helper1.optRemoteIdOut);
		Assert.assertEquals(remoteId2, helper2.optRemoteIdOut);
	}
}
