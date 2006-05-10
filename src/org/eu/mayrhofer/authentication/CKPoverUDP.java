/* Copyright Rene Mayrhofer
 * File created 2006-05-10
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.authentication;

import java.io.IOException;
import java.net.InetAddress;

import org.apache.log4j.Logger;
import org.eu.mayrhofer.authentication.CandidateKeyProtocol.CandidateKeyPartIdentifier;
import org.eu.mayrhofer.authentication.exceptions.InternalApplicationException;

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
public abstract class CKPoverUDP implements MessageListener {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(CKPoverUDP.class);

	/** The UDP port to listen on and to connect to the remote server. */
	private int udpPort;
	
	private final static String Protocol_CandidateKeyPart = "CAND ";

	private final static String Protocol_CandidateMatch = "MATCH ";

	private final static String Protocol_CandidateKey = "KEY ";

	private final static String Protocol_KeyAcknowledge = "ACK ";

	private final static String Protocol_Terminate = "NACK ";
	
	/** The maximum size of the data transported in a single UDP packet over
	 * Ethernet: Ethernet maximum packet size is 1518, with an Ethernet header of 14
	 * and a checksum of 4 Bytes. IP header is usually XS bytes, UDP header is 8 bytes.
	 * --> 1492-X Bytes
	 * 
	 * But set this large, because we don't really care about fragmentation.
	 */
	public final static int Maximum_Udp_Data_Size = 65535-8;

	/** If set to true, the JSSE will be used, if set to false, the Bouncycastle Lightweight API. */
	protected boolean useJSSE;

	/** This may be set to distinguish multiple instances running on the same machine. */
	private String instanceId = null;

	private UDPMulticastSocket channel;
	
	private CandidateKeyProtocol ckp;
	
	/** Construct the object by initializing basic variables.
	 * 
	 * @param tcpPort The TCP port to use for listening and for connecting to remote hosts.
	 * @param useJSSE If set to true, the JSSE API with the default JCE provider of the JVM will be used
	 *                for cryptographic operations. If set to false, an internal copy of the Bouncycastle
	 *                Lightweight API classes will be used.
	 * @param instanceId This parameter may be used to distinguish differenc instances of
	 *                   this class running on the same machine. It will be used in logging
	 *                   and error messages. May be set to null.
	 * @throws IOException 
	 */
	protected CKPoverUDP(int udpPort, String multicastGroup, String instanceId, boolean useJSSE) throws IOException {
		this.udpPort = udpPort;
		this.useJSSE = useJSSE;
		this.instanceId = instanceId;

		channel = new UDPMulticastSocket(udpPort, multicastGroup);
		channel.addIncomingMessageListener(this);
		// channel.dispose() takes care of calling stopListening();
		channel.startListening();
		ckp = new CandidateKeyProtocol(64, 32, null, useJSSE);
	}

	protected void addCandidates(byte[][] keyParts, float entropy) throws InternalApplicationException {
		CandidateKeyPartIdentifier[] candidateKeyParts = ckp.generateCandidates(keyParts, entropy);
		/* send out as many UDP multicast packets as necessary to transmit all the generated 
		 * candidate key parts
		 */
		byte[] buffer = new byte[Maximum_Udp_Data_Size];
		for (int i=0; i<candidateKeyParts.length; i++) {
			
		}
	}
	
	/** This hook will be called when the final verdict is that the whole 
	 * authentication protocol succeeded, i.e. both hosts signalled success on
	 * key verification.
	 * @param remote The remote host with which the key exchange succeeded.
	 * @param optionalRemoteId An optional remote ID, exactly as it has been passed
	 *                         to verificationSuccess. May be null. 
	 * @param optionalParameterFromRemote If the remote device reported an additional
	 *                                    parameter with its success message, it will
	 *                                    be put into this parameter. May be null.
	 * @param sharedSessionKey The shared session key (which is different from the
	 *                         shared authentication key used for verification) that
	 *                         can now be used for subsequent secure communication.
	 */
	protected abstract void protocolSucceededHook(InetAddress remote, 
			Object optionalRemoteId, String optionalParameterFromRemote, 
			byte[] sharedSessionKey);
	
	/** This hook will be called when the whole authentication protocol has
	 * failed. Derived classes should implement it to react to this failure.
	 * @param remote The remote host with which the key exchange succeeded.
	 * @param optionalRemoteId An optional remote ID, exactly as it has been passed
	 *                         to verificationSuccess or verificationFailure. May be null. 
	 * @param e If not null, the exception describing the failure.
	 * @param message If not null, the message describing the failure.
	 */
	protected abstract void protocolFailedHook(InetAddress remote, 
			Object optionalRemoteId, Exception e, String message);

	/** This hook will be called when the whole authentication protocol has
	 * made some progress. Derived classes should implement it to react to 
	 * this progress.
	 * @param remote The remote host with which the key exchange succeeded.
	 * @param optionalRemoteId An optional remote ID, exactly as it has been passed
	 *                         to verificationSuccess or verificationFailure. May be null. 
	 * @param cur @see AuthenticationProgressHandler#AuthenticationProgress
	 * @param max @see AuthenticationProgressHandler#AuthenticationProgress
	 * @param message @see AuthenticationProgressHandler#AuthenticationProgress
	 */
	protected abstract void protocolProgressHook(InetAddress remote, 
			Object optionalRemoteId, int cur, int max, String message);
	
	private class UDPMessageHandler implements MessageListener {
		public void handleMessage(byte[] message, int offset, int length, Object sender) {
			
		}
	}
}
