/* Copyright Rene Mayrhofer
 * File created 2006-09-03
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.authentication.test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

import org.eu.mayrhofer.authentication.accelerometer.MotionAuthenticationParameters;
import org.eu.mayrhofer.authentication.accelerometer.MotionAuthenticationProtocol1;

public class MotionAuthenticationProtocol1Test extends MotionAuthenticationProtocolTestBase {
	private Protocol1Hooks prot1_a, prot1_b;
	
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
		prot1_a.startServer();
		prot1_b.startAuthentication("localhost");

		classIsReadyForTests = true;
	}
	
	public void tearDown() {
		prot1_a.stopServer();
	}
	
	private class Protocol1Hooks extends MotionAuthenticationProtocol1 {
		protected Protocol1Hooks() {
			super(MotionAuthenticationParameters.coherenceThreshold, MotionAuthenticationParameters.coherenceWindowSize, false);
		}
		
		protected void protocolSucceededHook(InetAddress remote, 
				Object optionalRemoteId, String optionalParameterFromRemote, 
				byte[] sharedSessionKey, Socket toRemote) {
			numSucceeded++;
		}		

		protected void protocolFailedHook(InetAddress remote, Object optionalRemoteId, 
				Exception e, String message) {
			numFailed++;
		}
		
		protected void protocolProgressHook(InetAddress remote, 
				Object optionalRemoteId, int cur, int max, String message) {
			numProgress++;
		}		
	}
}
