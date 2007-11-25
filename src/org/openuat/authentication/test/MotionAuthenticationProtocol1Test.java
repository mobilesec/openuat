/* Copyright Rene Mayrhofer
 * File created 2006-09-03
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.authentication.test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

import org.openuat.authentication.accelerometer.ShakeWellBeforeUseParameters;
import org.openuat.authentication.accelerometer.ShakeWellBeforeUseProtocol1;
import org.openuat.util.RemoteTCPConnection;
import org.openuat.util.TCPPortServer;

public class MotionAuthenticationProtocol1Test extends MotionAuthenticationProtocolTestBase {
	private Protocol1Hooks prot1_a, prot1_b;
	
	// TODO: activate me again when J2ME polish can deal with Java5 sources!
	//@Override
	public void setUp() throws IOException {
		super.setUp();

		/*
		 * 2: construct the two prototol instances: two different variants, each
		 * with two sides
		 */
		prot1_a = new Protocol1Hooks();
		prot1_b = new Protocol1Hooks();

		/* 3: register the protocols with the respective sides */
		aggr_a.addNextStageSegmentsSink(prot1_a);
		aggr_b.addNextStageSegmentsSink(prot1_b);

		/*
		 * 4: authenticate for protocol variant 1 (variant 2 doesn't need this
		 * step)
		 */
		prot1_a.setContinuousChecking(true);
		prot1_b.setContinuousChecking(true);
		prot1_a.startListening();
		prot1_b.startAuthentication(new RemoteTCPConnection(
				new Socket("localhost", ShakeWellBeforeUseProtocol1.TcpPort)), 
				ShakeWellBeforeUseProtocol1.KeyAgreementProtocolTimeout, null);

		classIsReadyForTests = true;
	}
	
	// TODO: activate me again when J2ME polish can deal with Java5 sources!
	//@Override
	public void tearDown() {
		prot1_a.stopListening();
	}
	
	private class Protocol1Hooks extends ShakeWellBeforeUseProtocol1 {
		protected Protocol1Hooks() {
			super(new TCPPortServer(ShakeWellBeforeUseProtocol1.TcpPort, 
					ShakeWellBeforeUseProtocol1.KeyAgreementProtocolTimeout, false, true), 
					false, false,
					ShakeWellBeforeUseParameters.coherenceThreshold, 0.0, ShakeWellBeforeUseParameters.coherenceWindowSize, false);
		}
		
		// TODO: activate me again when J2ME polish can deal with Java5 sources!
		//@SuppressWarnings("unused")
		protected void protocolSucceededHook(InetAddress remote, 
				Object optionalRemoteId, String optionalParameterFromRemote, 
				byte[] sharedSessionKey, Socket toRemote) {
			numSucceeded++;
		}		

		// TODO: activate me again when J2ME polish can deal with Java5 sources!
		//@SuppressWarnings("unused")
		protected void protocolFailedHook(InetAddress remote, Object optionalRemoteId, 
				Exception e, String message) {
			numFailed++;
		}
		
		// TODO: activate me again when J2ME polish can deal with Java5 sources!
		//@SuppressWarnings("unused")
		protected void protocolProgressHook(InetAddress remote, 
				Object optionalRemoteId, int cur, int max, String message) {
			numProgress++;
		}		
	}
}
