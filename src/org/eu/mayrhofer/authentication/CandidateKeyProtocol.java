/* Copyright Rene Mayrhofer
 * File created 2006-05-05
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.authentication;

import java.util.LinkedList;

import org.apache.log4j.Logger;
import org.eu.mayrhofer.authentication.exceptions.InternalApplicationException;
import org.eu.mayrhofer.util.Hash;

// TODO: rewrite me in the same style
/** This is an abstract class that implements the basics of all protocols
 * based on Diffie-Hellman key exchange over TCP with subsequent verification
 * of the key material to check that it is equal on both sides. This 
 * verification is necessary to prevent man-in-the-middle attacks. Derived
 * classed need to implement this specific check that the authentication key
 * provided by SimpleKeyAgreement matches by implementing the method
 * startVerification. This method should asynchronously start the verification
 * step, i.e. not block the caller, and should then either call 
 * verificationSuccess or verificationFailure depending on the outcome of
 * the check. Upon calling one of the methods, a status exchange with the remote
 * host will be done over the TCP channel to arrive at a common decision if the
 * whole protocol succeeded. The final verdict will be signalled by emitting
 * standard authentication events (as defined by AuthenticationProgressHandler) and
 * by calling either the protocolSucceededHook or the protocolFailedHook
 * function. In short, the whole authentication protocol should be used as follows:
 * 
 * 1. Construct the object.
 * Either:
 * 2a. Start the TCP server.
 * 3b. Start an authentication protocol to a remote device by calling
 *     startAuthentication.
 * (It is possible to start a server and then initiate a protocol run, but
 * only one protocol run can be active at a time.)
 * 4. After the key agreement phase succeeded, the abstract startVerification
 *    method is called. In this method, derived classes should asynchronously 
 *    start whatever is necessary to verify the provided shared authentication key.
 * 5. When a local decision about the key verification has been made, call either
 *    verificationSucceess or verificationFailure.
 * 6. The local decisions will be communicated over the TCP channel and if both
 *    devices signalled success, the protocolSucceededHook will be called. In any
 *    other case (both or either of the devices signalled failure on verification),
 *    the protocolFailedHook will be called.
 * Generally, events will be emitted by this class to all registered listeners.
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class CandidateKeyProtocol {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(CandidateKeyProtocol.class);

	public static class CandidateKeyPartIdentifier {
		public int counter;
		public byte[] hash;
	}
	
	private static class CandidateKeyPart {
		private static int lastCounter = 0;
		
		int counter;
		byte[] keyPart;
		byte[] hash;
		float entropy;
		
		CandidateKeyPart(byte[] keyPart, float entropy, boolean useJSSE) throws InternalApplicationException {
			this.keyPart = keyPart;
			this.entropy = entropy;
			this.hash = Hash.doubleSHA256(keyPart, useJSSE);
			this.counter = ++lastCounter;
		}
		
		CandidateKeyPartIdentifier extractPublicIdentifier() {
			CandidateKeyPartIdentifier ret = new CandidateKeyPartIdentifier();
			ret.counter = counter;
			ret.hash = hash;
			return ret;
		}
	}
	
	public static class CandidateKey {
		public byte[] key;
		public byte[] hash;
	}

	/** If set to true, the JSSE will be used, if set to false, the Bouncycastle Lightweight API. */
	private boolean useJSSE;
	
	private String remoteIdentifier;
	
	private CandidateKeyPart[] recentKeyParts;
	
	/** elements: CandidateKeyParts, window of max size */
	private LinkedList matchingKeyParts = new LinkedList();
	
	private int matchHistorySize;
	
	public CandidateKeyProtocol(int candidateHistorySize, int matchHistorySize, String remoteIdentifier, boolean useJSSE) {
		this.matchHistorySize = matchHistorySize;
		this.remoteIdentifier = remoteIdentifier;
		this.useJSSE = useJSSE;
		
		this.recentKeyParts = new CandidateKeyPart[candidateHistorySize];
		logger.info("Candidate key part protocol with " + recentKeyParts.length + 
				" key parts in history created");
	}
	
	public CandidateKeyPartIdentifier[] generateCandidates(byte[] candidateKeys) {
		return null;
	}
	
	public int[] mergeCandidates(CandidateKeyPartIdentifier[] candidateIdentifiers) {
		return null;
	}
	
	public void acknowledgeMatches(int[] matches) {
		
	}
	
	public int getNumTotalMatches() {
		return 0;
	}
	
	public float getSumMatchEntropy() {
		return 0;
	}
	
	public CandidateKey generateKey() {
		return null;
	}
	
	/** Returns null when same hash could not be generated from the matchingKeyParts */
	public CandidateKey searchKey(byte[] hash) {
		return null;
	}
}
