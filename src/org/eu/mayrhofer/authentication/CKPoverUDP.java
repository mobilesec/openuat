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

import org.apache.commons.codec.binary.Hex;
import org.apache.log4j.Logger;
import org.eu.mayrhofer.authentication.CandidateKeyProtocol.CandidateKeyPartIdentifier;
import org.eu.mayrhofer.authentication.exceptions.InternalApplicationException;

/** This is an abstract class that implements the basics of all protocols
 * based on running a candidate key protocol over UDP. Man-in-the-middle
 * attacks are not possible because there is no key agreement. Both hosts
 * generate key part candidates, broadcast hashes of them and optionally
 * flag matches. This way, matching candidate key parts form a shared key
 * between the hosts. To be sure that lost packets do not cause creation
 * of different keys, these keys are again acknowledged by exchanging hashes.
 * 
 * For more details on the candidate key protocol, see @see CandidateKeyProtocol. 
 *
 * The final verdict of the authentication protocol will be signalled by emitting
 * standard authentication events (as defined by AuthenticationProgressHandler) and
 * by calling either the protocolSucceededHook or the protocolFailedHook
 * function. In short, the whole authentication protocol should be used as follows:
 * 
 * 1. Construct the object.
 * 2. Add potentially similar key material by calling addCandidates.
 * 3. When a key could be generated from the matches given the requirements passed
 *    when constructing the object, its hash will be sent to the remote host and,
 *    upon receiving acknowledgement, the protocolSucceededHook will be called.
 * TODO: Also emit protocol failed events based on criteria!
 * Generally, events will be emitted by this class to all registered listeners.
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public abstract class CKPoverUDP extends AuthenticationEventSender {
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

	/** The UDP multicaster/communication class used for all network communication. */
	private UDPMulticastSocket channel;
	
	/** The candidate key protocol instance used to generate candidates and keys. */
	private CandidateKeyProtocol ckp;
	
	private boolean broadcastCandidates;
	
	private boolean sendAcknowledgments;
	
	/** The minimum number of matching parts before a key will be created. This is set by the
	 * constructor.
	 * @see #CKPoverUDP
	 */
	private int minMatchingParts;
	
	/** The minimum entropy of the matching parts before a key will be created. This is set by the
	 * constructor.
	 * @see #CKPoverUDP
	 */
	private int minMatchingEntropy;
	
	/** Construct the object by initializing basic variables.
	 * 
	 * @param tcpPort The TCP port to use for listening and for connecting to remote hosts.
	 * @param useJSSE If set to true, the JSSE API with the default JCE provider of the JVM will be used
	 *                for cryptographic operations. If set to false, an internal copy of the Bouncycastle
	 *                Lightweight API classes will be used.
	 * @param instanceId This parameter may be used to distinguish different instances of
	 *                   this class running on the same machine. It will be used in logging
	 *                   and error messages. May be set to null.
	 * @param broadcastCandidates If set to true, all candidate key part idenfifiers will be
	 *                            broadcast immediately after adding them to the local history.
	 *                            Set this to true if the local host should send out its generated
	 *                            candidates.
	 * @param sendAcknowledgments If set to true, all matches between candidate key part identifiers
	 *                            received over the network that match local key parts in the history
	 *                            will be acknowledged over the network. These acknowledgements are 
	 *                            bound to leak some information about the matching key parts, although
	 *                            we currently assume that this leakage is of no use to an attacker
	 *                            because only hashes of key parts are broadcast. 
	 *                            Set this to true if communication is very unreliable and it has to
	 *                            be assumed that candidate key part identifiers get lost, or in an
	 *                            asymmetric setting where one host broadcasts the candidate key part
	 *                            identifiers and the other acknowledges matches.
	 * @param minMatchingParts The minimum number of key parts that need to match before a key will
	 *                         be generated. The CandidateKeyProtocol instance will be initialized
	 *                         with twice this number of entries in the matching part history and
	 *                         8 times this number of entries in the recent candidates history.
	 * @param minMatchingEntropy The minimum entropy that needs to be collected in matching key parts
	 *                           before a key will be generated.
	 * @throws IOException 
	 */
	protected CKPoverUDP(int udpPort, String multicastGroup, String instanceId, 
			boolean broadcastCandidates, boolean sendAcknowledgments, 
			int minMatchingParts, int minMatchingEntropy, boolean useJSSE) throws IOException {
		this.udpPort = udpPort;
		this.useJSSE = useJSSE;
		this.instanceId = instanceId;
		this.broadcastCandidates = broadcastCandidates;
		this.sendAcknowledgments = sendAcknowledgments;
		this.minMatchingParts = minMatchingParts;
		this.minMatchingEntropy = minMatchingEntropy;

		channel = new UDPMulticastSocket(udpPort, multicastGroup);
		channel.addIncomingMessageListener(new UDPMessageHandler());
		// channel.dispose() takes care of calling stopListening();
		channel.startListening();
		// keep the match history for each remote host for 5 minutes - should really be enough
		ckp = new CandidateKeyProtocol(minMatchingParts*8, minMatchingParts*2, 300, instanceId, useJSSE);
	}

	/** This method should be called whenever new key material for a round has been generated.
	 * It will trigger sending the associated identifiers when broadcastCandidates is set.
	 * @param keyParts The candidate key parts for this round.
	 * @param entropy An estimation of the entropy of all the key parts.
	 * @see #broadcastCandidates
	 */
	protected void addCandidates(byte[][] keyParts, float entropy) throws InternalApplicationException, IOException {
		CandidateKeyPartIdentifier[] candidateKeyParts = ckp.generateCandidates(keyParts, entropy);
		/* send out as many UDP multicast packets as necessary to transmit all the generated 
		 * candidate key parts
		 */
		if (broadcastCandidates) {
			byte[] buffer = new byte[Maximum_Udp_Data_Size];
			int outIndex = 0;
			for (int i=0; i<candidateKeyParts.length; i++) {
				if (buffer == null || outIndex+candidateKeyParts[i].hash.length*2+1 >= Maximum_Udp_Data_Size) {
					// send the old packet and construct a new one
					if (buffer != null) {
						logger.debug("Sending UDP packet with " + outIndex + " bytes");
						byte[] packet = new byte[outIndex];
						System.arraycopy(buffer, 0, packet, 0, outIndex);
						channel.sendMulticast(packet);
					}
						
					String packetStart = Protocol_CandidateKeyPart + candidateKeyParts[i].round + " ";
					// default ASCII coding
					System.arraycopy(packetStart.getBytes(), 0, buffer, 0, packetStart.length());
					outIndex = packetStart.length();

					logger.debug("Started new UDP packet with round " + candidateKeyParts[i].round + 
							" for candidate number " + i);
				}
				String cand = new String(Hex.encodeHex(candidateKeyParts[i].hash)) + " ";
				System.arraycopy(cand.getBytes(), 0, buffer, outIndex, cand.length());
				outIndex += cand.length();
			}
			if (buffer != null) {
				logger.debug("Sending UDP packet with " + outIndex + " bytes");
				byte[] packet = new byte[outIndex];
				System.arraycopy(buffer, 0, packet, 0, outIndex);
				channel.sendMulticast(packet);
			}
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
	
	/** This is a helper class for handling incoming UDP packets. It is the
	 * heart of CKoverUDP and does most of the work.
	 */
	private class UDPMessageHandler implements MessageListener {
		public void handleMessage(byte[] message, int offset, int length, Object sender) {
			// this is inefficient, but Java is anyway, so don't care at the moment...
			byte[] packet = new byte[length];
			System.arraycopy(message, offset, packet, 0, length);
			String pack = new String(packet);
			logger.debug("Received UDP packet with  " + pack.length() + " bytes from " + sender);
			try {
				if (pack.startsWith(Protocol_CandidateKeyPart)) {
					int off = pack.indexOf(' ', Protocol_CandidateKeyPart.length());
					int round = Integer.parseInt(pack.substring(Protocol_CandidateKeyPart.length(), off));
					logger.debug("Received packet with candidate key parts for round " + round);
				}
				else if (pack.startsWith(Protocol_CandidateMatch)) {
					int off = pack.indexOf(' ', Protocol_CandidateMatch.length());
					int round = Integer.parseInt(pack.substring(Protocol_CandidateMatch.length(), off));
					logger.debug("Received packet with matching indices for round " + round);
				}
				else if (pack.startsWith(Protocol_CandidateKey)) {
					logger.debug("Received candidate key");
				}
				else if (pack.startsWith(Protocol_KeyAcknowledge)) {
					logger.debug("Received key acknowledge");
				}
				else if (pack.startsWith(Protocol_Terminate)) {
					logger.debug("Received protocol termination request");
				}
				else {
					logger.error("Received unknown packet type, ignoring it");
				}
			}
			catch (NumberFormatException e) {
				logger.error("Can not decode number, ignoring whole packet");
			}
		}
	}
}
