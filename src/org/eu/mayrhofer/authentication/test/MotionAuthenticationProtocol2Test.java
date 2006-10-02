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

import org.eu.mayrhofer.authentication.accelerometer.MotionAuthenticationParameters;
import org.eu.mayrhofer.authentication.accelerometer.MotionAuthenticationProtocol2;

public class MotionAuthenticationProtocol2Test extends MotionAuthenticationProtocolTestBase {
	private Protocol2Hooks prot2_a, prot2_b;
	
	public void setUp() throws IOException {
		super.setUp();

		/*
		 * 2: construct the two prototol instances: two different variants, each
		 * with two sides
		 */
		prot2_a = new Protocol2Hooks(5, 56789, 56798, "A");
		prot2_b = new Protocol2Hooks(5, 56798, 56789, "B");

		/* 3: register the protocols with the respective sides */
		aggr_a.addNextStageSamplesSink(prot2_a);
		aggr_b.addNextStageSamplesSink(prot2_b);
	
		classIsReadyForTests = true;
	}
	
	public void tearDown() {
	}
	
	public void testMemoryOverflowCase() throws IOException, InterruptedException {
		runCase("tests/motionauth/specialcases/outofmemory.gz");
	}
	
	private class Protocol2Hooks extends MotionAuthenticationProtocol2 {
		protected Protocol2Hooks(int numMatches, int udpRecvPort, int udpSendPort, String instanceId) throws IOException {
			super(MotionAuthenticationParameters.samplerate, MotionAuthenticationParameters.fftMatchesWindowSize,
					MotionAuthenticationParameters.fftMatchesQuantizationLevels, MotionAuthenticationParameters.fftMatchesCandidatesPerRound,
					MotionAuthenticationParameters.fftMatchesCutOffFrequenecy, MotionAuthenticationParameters.fftMatchesWindowOverlap,
					MotionAuthenticationParameters.fftMatchesThreshold,
					numMatches, false, udpRecvPort, udpSendPort, "127.0.0.1", instanceId);
		}
		
		protected void protocolSucceededHook(String remote, byte[] sharedSessionKey, float matchingRoundsFraction) {
			numSucceeded++;
		}

		protected void protocolFailedHook(String remote, float matchingRoundsFraction, Exception e, String message) {
			numFailed++;
		}

		protected void protocolProgressHook(String remote, int cur, int max, String message) {
			numProgress++;
		}
	}
}
