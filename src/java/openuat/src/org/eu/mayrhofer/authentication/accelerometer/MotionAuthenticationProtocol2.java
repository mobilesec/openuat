/* Copyright Rene Mayrhofer
 * File created 2006-05-09
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.authentication.accelerometer;

import java.io.IOException;
import java.net.InetAddress;

import org.apache.log4j.Logger;
import org.eu.mayrhofer.authentication.CKPOverUDP;
import org.eu.mayrhofer.authentication.CandidateKeyProtocol;
import org.eu.mayrhofer.authentication.MessageListener;
import org.eu.mayrhofer.authentication.UDPMulticastSocket;
import org.eu.mayrhofer.sensors.SegmentsSink;

/** This is the first variant of the motion authentication protocol. It 
 * broadcasts candidate keys over UDP and creates shared keys with the
 * candidate key protocol.
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class MotionAuthenticationProtocol2 extends CKPOverUDP implements SegmentsSink  {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(MotionAuthenticationProtocol2.class);

	/** The TCP port we use for this protocol. */
	public static final int UdpPort = 54322;
	
	public static final String MulticastGroup = "228.10.10.1";

	/** Initializes the object, only setting useJSSE at the moment.
	 * 
	 * @param minMatchingParts
	 * @param useJSSE If set to true, the JSSE API with the default JCE provider of the JVM will be used
	 *                for cryptographic operations. If set to false, an internal copy of the Bouncycastle
	 *                Lightweight API classes will be used.
	 * @throws IOException 
	 */
	public MotionAuthenticationProtocol2(int minMatchingParts, boolean useJSSE) throws IOException {
		// TODO: set minimum entropy
		super(UdpPort, MulticastGroup, null, true, false, minMatchingParts, 0, useJSSE);
	}

	/** The implementation of SegmentsSink.addSegment. It will be called whenever
	 * a significant active segment has been sampled completely, i.e. when the
	 * source has become quiescent again. 
	 * 
	 * This implementation immediately computes the sliding FFT windows, quantizes
	 * the coefficients, and sends out candidate key parts. 
	 */
	public void addSegment(double[] segment, int startIndex) {
		logger.info("Received segment of size " + segment.length + " starting at index " + startIndex);
	}

	protected void protocolSucceededHook(InetAddress remote, byte[] sharedSessionKey) {
		
	}

	protected void protocolFailedHook(InetAddress remote, Exception e, String message) {
		
	}

	protected void protocolProgressHook(InetAddress remote, int cur, int max, String message) {
		
	}
}
