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
import java.net.Socket;
import java.net.UnknownHostException;

import junit.framework.Assert;
import junit.framework.TestCase;

import org.openuat.authentication.DHWithVerification;
import org.openuat.util.RemoteConnection;
import org.openuat.util.RemoteTCPConnection;
import org.openuat.util.TCPPortServer;

/** There are no _BCAPI and _Mixed variants because DHOverTCPWithVerification only uses the crypto embedded into
 * SimpleKeyAgreement and InterlockProtocol, and those are tested with _BCAPI and _Mixed.
 * @author rene
 *
 */
public class DHOverTCPWithVerificationTest extends TestCase {
	private class TestHelper extends DHWithVerification {
		protected TestHelper(int tcpPort, boolean keepConnected, String instanceId, boolean useJSSE, 
				boolean succeed, boolean failHard) {
			super(new TCPPortServer(tcpPort, 10000, keepConnected, useJSSE), false, keepConnected, instanceId, useJSSE);
			this.succeed = succeed;
			this.failHard = failHard;
			this.tcpPort = tcpPort;
		}

		int tcpPort;
		int numResetHookCalled = 0;
		int numSucceededHookCalled = 0;
		int numFailedHardHookCalled = 0;
		int numFailedSoftHookCalled = 0;
		int numProgressHookCalled = 0;
		int numStartedHookCalled = 0;
		byte[] sharedAuthKey = null;
		byte[] sharedSessKey = null;
		private boolean succeed;
		private boolean failHard;
		String param;
		
		Object optVerifyIdIn = null, optVerifyIdOut = null;
		String optParamIn = null, optParamOut = null;
		
		@Override
		protected void startVerificationAsync(byte[] sharedAuthenticationKey, String parm, RemoteConnection remote) {
			this.param = parm;
			// need to copy here to retain until after success of failure - the original will be wiped
			this.sharedAuthKey = new byte[sharedAuthenticationKey.length];
			System.arraycopy(sharedAuthenticationKey, 0, this.sharedAuthKey, 0, sharedAuthenticationKey.length);

			if (succeed)
				this.verificationSuccess(remote, optVerifyIdIn, optParamIn);
			else
				this.verificationFailure(failHard, remote, optVerifyIdIn, optParamIn, null, null);
		}

		@Override
		protected void resetHook(RemoteConnection remote) {
			numResetHookCalled++;
		}

		@Override
		protected void protocolSucceededHook(RemoteConnection remote, Object optionalVerificationId,
				String optionalParameterFromRemote, byte[] sharedSessionKey) {
			System.out.println("-----------------------------------------------------------------------------------------");
			numSucceededHookCalled++;
			this.optVerifyIdOut = optionalVerificationId;
			this.optParamOut = optionalParameterFromRemote;
			this.sharedSessKey = sharedSessionKey;
		}

		@Override
		protected void protocolFailedHook(boolean failedHard, RemoteConnection remote, Object optionalVerificationId,
				Exception e, String message) {
			if (failedHard)
				numFailedHardHookCalled++;
			else
				numFailedSoftHookCalled++;
			this.optVerifyIdOut = optionalVerificationId;
		}

		@Override
		protected void protocolProgressHook(RemoteConnection remote, int cur, int max, String message) {
			numProgressHookCalled++;
		}

		protected void protocolStartedHook(RemoteConnection remote) {
			numStartedHookCalled++;
		}
		
		public void startAuthentication(String parm) throws UnknownHostException, IOException {
			this.startAuthentication(new RemoteTCPConnection(new Socket("127.0.0.1", tcpPort)), 10000, parm);
		}
	}

	protected boolean useJSSE1 = true;
	protected boolean useJSSE2 = true;
	
	private TestHelper helper1;
	private TestHelper helper2;
	
	public void testCompleteRun_Success() throws IOException, InterruptedException {
		helper1 = new TestHelper(54326, false, "server", useJSSE1, true, true);
		helper2 = new TestHelper(54326, false, "client", useJSSE2, true, true);

		String param = "TEST";
		String param1 = "TEST1";
		String param2 = "TEST2";
		Integer remoteId1 = new Integer(1);
		Integer remoteId2 = new Integer(2);
		
		helper1.optParamIn = param1;
		helper2.optParamIn = param2;
		helper1.optVerifyIdIn = remoteId1;
		helper2.optVerifyIdIn = remoteId2;
		
		helper1.startListening();
		helper2.startAuthentication(param);
		
		while ((helper1.numFailedHardHookCalled == 0 && helper1.numFailedSoftHookCalled == 0 && 
				helper1.numSucceededHookCalled == 0) ||
				(helper2.numFailedHardHookCalled == 0 && helper2.numFailedSoftHookCalled == 0 && 
				helper2.numSucceededHookCalled == 0)) {
			Thread.sleep(50);
		}
		
		Assert.assertEquals(1, helper1.numStartedHookCalled);
		Assert.assertEquals(1, helper2.numStartedHookCalled);
		Assert.assertEquals(1, helper1.numSucceededHookCalled);
		Assert.assertEquals(1, helper2.numSucceededHookCalled);
		Assert.assertEquals(0, helper1.numFailedHardHookCalled);
		Assert.assertEquals(0, helper2.numFailedHardHookCalled);
		Assert.assertEquals(0, helper1.numFailedSoftHookCalled);
		Assert.assertEquals(0, helper2.numFailedSoftHookCalled);
		
		Assert.assertNotNull(helper1.param);
		Assert.assertNotNull(helper2.param);
		Assert.assertEquals(param, helper1.param);
		Assert.assertEquals(param, helper2.param);

		Assert.assertNotNull(helper1.optParamOut);
		Assert.assertNotNull(helper2.optParamOut);
		Assert.assertNotNull(helper1.optVerifyIdOut);
		Assert.assertNotNull(helper2.optVerifyIdOut);
		
		Assert.assertEquals(param1, helper2.optParamOut);
		Assert.assertEquals(param2, helper1.optParamOut);
		Assert.assertEquals(remoteId1, helper1.optVerifyIdOut);
		Assert.assertEquals(remoteId2, helper2.optVerifyIdOut);
		
		Assert.assertTrue(SimpleKeyAgreementTest.compareByteArray(helper1.sharedAuthKey, helper2.sharedAuthKey));
		Assert.assertTrue(SimpleKeyAgreementTest.compareByteArray(helper1.sharedSessKey, helper2.sharedSessKey));
		Assert.assertFalse(SimpleKeyAgreementTest.compareByteArray(helper1.sharedSessKey, helper1.sharedAuthKey));
		Assert.assertFalse(SimpleKeyAgreementTest.compareByteArray(helper2.sharedSessKey, helper2.sharedAuthKey));
	}
	
	private void helper_testCompleteRun_failures(int port,
			boolean succeed1, boolean hardFail1,
			boolean succeed2, boolean hardFail2) throws InterruptedException, IOException {
		helper1 = new TestHelper(port, false, "server", useJSSE1, succeed1, hardFail1);
		helper2 = new TestHelper(port, false, "client", useJSSE2, succeed2, hardFail2);
		
		String param = "TEST";
		String param1 = "TEST1";
		String param2 = "TEST2";
		Integer remoteId1 = new Integer(1);
		Integer remoteId2 = new Integer(2);
		
		helper1.optParamIn = param1;
		helper2.optParamIn = param2;
		helper1.optVerifyIdIn = remoteId1;
		helper2.optVerifyIdIn = remoteId2;
		
		helper1.startListening();
		helper2.startAuthentication(param);
		
		while ((helper1.numFailedHardHookCalled == 0 && helper1.numFailedSoftHookCalled == 0 && 
				helper1.numSucceededHookCalled == 0) ||
				(helper2.numFailedHardHookCalled == 0 && helper2.numFailedSoftHookCalled == 0 && 
				helper2.numSucceededHookCalled == 0)) {
			Thread.sleep(50);
		}
		
		Assert.assertEquals(1, helper1.numStartedHookCalled);
		Assert.assertEquals(1, helper2.numStartedHookCalled);
		Assert.assertEquals("assert 1", 0, helper1.numSucceededHookCalled);
		Assert.assertEquals("assert 2", 0, helper2.numSucceededHookCalled);
		boolean expectHardFail = (!succeed1 && hardFail1) || (!succeed2 && hardFail2);
		Assert.assertEquals((expectHardFail ? 1 : 0), helper1.numFailedHardHookCalled);
		Assert.assertEquals((expectHardFail ? 1 : 0), helper2.numFailedHardHookCalled);
		Assert.assertEquals((expectHardFail ? 0 : 1), helper1.numFailedSoftHookCalled);
		Assert.assertEquals((expectHardFail ? 0 : 1), helper2.numFailedSoftHookCalled);

		Assert.assertNotNull("assert 5", helper1.param);
		Assert.assertNotNull("assert 6", helper2.param);
		Assert.assertEquals("assert 7", param, helper1.param);
		Assert.assertEquals("assert 8", param, helper2.param);

		Assert.assertNull("assert 9", helper1.optParamOut);
		Assert.assertNull("assert 10", helper2.optParamOut);
		Assert.assertNotNull("assert 11", helper1.optVerifyIdOut);
		Assert.assertNotNull("assert 12", helper2.optVerifyIdOut);
		
		Assert.assertEquals("assert 13", remoteId1, helper1.optVerifyIdOut);
		Assert.assertEquals("assert 14", remoteId2, helper2.optVerifyIdOut);
	}
	
	public void testCompleteRun_HardFailure2_Succeed1() throws InterruptedException, IOException {
		helper_testCompleteRun_failures(54327, true, true, false, true);
	}

	public void testCompleteRun_HardFailure1_Succeed2() throws InterruptedException, IOException {
		helper_testCompleteRun_failures(54328, false, true, true, true);
	}

	public void testCompleteRun_HardFailure1and2() throws InterruptedException, IOException {
		helper_testCompleteRun_failures(54329, false, true, false, true);
	}

	public void testCompleteRun_SoftFailure2_Succeed1() throws InterruptedException, IOException {
		helper_testCompleteRun_failures(54330, true, true, false, false);
	}

	public void testCompleteRun_SoftFailure1_Succeed2() throws InterruptedException, IOException {
		helper_testCompleteRun_failures(54331, false, false, true, true);
	}

	public void testCompleteRun_SoftFailure1and2() throws InterruptedException, IOException {
		helper_testCompleteRun_failures(54332, false, false, false, false);
	}

	public void testCompleteRun_SoftFailure2_HardFailure1() throws InterruptedException, IOException {
		helper_testCompleteRun_failures(54333, false, true, false, false);
	}

	public void testCompleteRun_SoftFailure1_HardFailure2() throws InterruptedException, IOException {
		helper_testCompleteRun_failures(54334, false, false, false, true);
	}
}
