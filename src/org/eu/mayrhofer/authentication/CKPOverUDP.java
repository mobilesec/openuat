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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.StringTokenizer;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.log4j.Logger;
import org.eu.mayrhofer.authentication.CandidateKeyProtocol.CandidateKey;
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
 * TODO: Also emit protocol progress events (but what is max??)
 * Generally, events will be emitted by this class to all registered listeners.
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public abstract class CKPOverUDP extends AuthenticationEventSender {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(CKPOverUDP.class);

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
	
	/** If set to true, generated key candidates will be broadcast (multicast). */
	private boolean broadcastCandidates;
	
	/** If set to true, matching candidate numbers will be signalled to the remote host that
	 * generated the candidate key parts.
	 */
	private boolean sendMatches;
	
	/** The minimum fraction of matching parts before a key will be created. This is set by the
	 * constructor.
	 * @see #CKPOverUDP
	 */
	private float minMatchingRoundsFraction;
	
	/** The maximum fraction of rounds without any matching parts before the protocol will be
	 * aborted with the respective host. Set to 0 to disable aborting the protocol.
	 * @see #CKPOverUDP
	 */
	private float maxMismatchRoundsFraction;
	
	/** The minimum entropy of the matching parts before a key will be created. This is set by the
	 * constructor.
	 * @see #CKPOverUDP
	 */
	private float minMatchingEntropy;
	
	/** The minimum number of rounds before any action (success or failure) is taken. This is
	 * set by the constructor. 
	 * @see #CKPOverUDP
	 */
	private int minNumRoundsForAction;
	
	/** This is used as a circular buffer for incoming candidate key part messages where no
	 * match has been found. When new local candidates are added, we then try to match with
	 * the parts in this buffer, because a message might be received before the local parts
	 * have been added.
	 * The elements of this array are Object array where the first element is an InetAddress
	 * holding the remote host address and and the second element is an array of
	 * CandidateKeyPartIdenfiers.
	 * @see UDPMessageHandler#handleMessage(byte[], int, int, Object)
	 * @see #addCandidates(byte[][], float)
	 */
	private Object[][] incomingKeyPartsBuffer;
	/** The index where to insert the next incoming candidate key part into incomingKeyPartsBuffer.
	 * @see #incomingKeyPartsBuffer
	 */
	private int incomingKeyPartsBufferIndex;

	/** This is used as a circular buffer for incoming candidate key messages where no
	 * match has been found. When new candidate key parts are matched, we then try to match with
	 * the candidate keys in this buffer, because a message might be received before the local parts
	 * have been added.
	 * The elements of this array are Object array where the first element is an InetAddress
	 * holding the remote host address, the second element is an Integer indicating the number
	 * of key parts that the candidate key is composed of, and and the third element is a byte array 
	 * containing the candidate key hash
	 * @see UDPMessageHandler#handleMessage(byte[], int, int, Object)
	 * @see #handleMatchingCandidateKeyPart(int, int, InetAddress)
	 */
	private Object[][] incomingCandKeyBuffer;
	/** The index where to insert the next incoming candidate key part into incomingCandKeyBuffer.
	 * @see #incomingCandKeyBuffer
	 */
	private int incomingCandKeyBufferIndex;
	
	/** This object is only used for synchronizing multi-threaded access. Specifically, it is
	 * used to make the handleMessage implementation uninterruptible by other messages and also
	 * protect handleMessage, addCandidates, and handleMatchingCandidateKeyPart from each other.
	 * @see UDPMessageHandler#handleMessage(byte[], int, int, Object)
	 * @see #addCandidates(byte[][], float)
	 * @see #handleMatchingCandidateKeyPart(int, int, InetAddress)
	 */  
	private Object globalLock = new Object();
	
	/** Just a small helper class to keep a list of generated keys for each host. */
	private class GeneratedKeyCandidates {
		/** A circular buffer of the last candidate keys that have been generated. It is used for
		 * selecting the key when a key acknowledgment is received (because another key might have been
		 * generated while the network messages were delivered.
		 */
		private CandidateKey[] list;
		/** The index where to insert the next generated candidate key into list.
		 * @see #list
		 */
		private int index;

		/** This variable is used to remember the candidate key which has been generated 
		 * locally to match the hash of a received candidate key message, and which has
		 * then subsequently been acknowledged. It is necessary to remember that key that
		 * has been acknowledged, and not only use the key received in the acknowledgement
		 * message itself, because of the following possible scenario:
		 * 
		 * 1a. Host A generates a candidate key X and sends the hash to host B. The key is
		 *     put into @see #list upon generating it.
		 * 1b. Host B generates a different candidate key Y and sends the hash to host A.
		 *     The key is put into @see #list upon generating it.
		 * <b>Note:</b> The keys X and Y can be different even when the matching key parts
		 * list of A and B contain the same key parts (when no candidate key part messages
		 * have been lost in transit). This can happen when the order in which the key parts 
		 * matches are computed is different, because then ckp.generateKey will generate
		 * different keys from the same set of key parts. 
		 * 2.  The two candidate key messages overlap during transmission.
		 * 3a. Host A receives the candidate key hash, and is able to generate a key from 
		 *     its key parts that matches this hash (and thus also has key Y now). This key
		 *     is put into this variable.
		 *     Then host A acknowledges the generation of matching key Y by sending a key
		 *     acknowledge message with the hash of Y to host B. It could then start using 
		 *     Y as the shared key (authentication success).
		 * 3b. Host B receives the candidate key hash, and is able to generate a key from
		 *     its key parts that matches this hash (and thus also has key X now). This key
		 *     is also put into this variable.
		 *     Then host B acknowledges the generation of matching key X by sending a key
		 *     acknowledge message with the hash of X to host A. It could then start using 
		 *     X as the shared key (authentication success).
		 * <b>Note:</b> Obviously, the hosts A and B would now be unable to communicate because
		 * the have different keys X and Y.
		 * 4a. Host A receives the acknowledgement that host B was able to generate A's 
		 *     originally suggested key X and switches to it as a shared key (authentication 
		 *     success again).
		 * 4b. Host B receives the acknowledgement that host A was able to generate B's
		 *     originally suggested key Y and switches to it as a shared key (authentication 
		 *     success again).
		 * <b>Note:</b> By making an effort to adapt to the shared key as used by the remote
		 * host, A and B have now effectively swapped their keys and still use different ones.
		 * 
		 * To deal with that scenario, the authentication success "process" is split into two
		 * stages, and only after receiving the key acknowledgement message, a host starts using
		 * a shared key. Phase 1 is basically step 3, while phase 2 is step 4 in the above
		 * scenario. But the hosts will wait for receiving key acknowledgement messages to decide
		 * what shared key to use. When the acknowledged hash matches the hash of the key that 
		 * the host found in stage 1, it will just use this key, and it will be guaranteed that
		 * both hosts hold the same key (because then both key acknowledge messages must have 
		 * carried the same hash, and overlapping of messages is irrelevant). When it does not
		 * match, the host will use the XOR of the key for which an own key acknowledge message
		 * was sent in stage 1 (and which is therefore stored in this variable) and the key
		 * which has been acknowledged by the other host. This also guarantees that both hosts
		 * will hold the same key, because XOR is commutative.
		 */
		private byte[] foundMatchingKey;
		
		GeneratedKeyCandidates() {
			// and keep a history of the last 5 generated keys (this is for each host)
			list = new CandidateKey[5];
			index = 0;
			foundMatchingKey = null;
		}
	}
	/** Keep one list for each remote host we have contact to. Keys are remote object identifiers
	 * (in this case Strings containing host addresses), values are GeneratedKeyCandidates. */
	HashMap generatedKeys = null;
	
	/** Construct the object by initializing basic variables.
	 * 
	 * @param udpReceivePort The UDP port to use for listening to packets.
	 * @param udpSendPort The UDP target port to send packets to. Will usually be the same as the
	 *                    udpReceivePort.
	 * @param multicastGroup The multicast group to use for exchanging candidate key parts.
	 *                       When a unicast address is specified instead, special handling will
	 *                       be used. Only use a unicast address if you have read and understood
	 *                       the source code.
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
	 * @param sendMatches If set to true, all matches between candidate key part identifiers
	 *                    received over the network that match local key parts in the history
	 *                    will be acknowledged over the network. These acknowledgements are 
	 *                    bound to leak some information about the matching key parts, although
	 *                    we currently assume that this leakage is of no use to an attacker
	 *                    because only hashes of key parts are broadcast. 
	 *                    Set this to true if communication is very unreliable and it has to
	 *                    be assumed that candidate key part identifiers get lost, or in an
	 *                    asymmetric setting where one host broadcasts the candidate key part
	 *                    identifiers and the other acknowledges matches.
	 * @param localCandidateHistorySize The number of local candidates to keep in the history.
	 *                                  If this is too small, matches might not be found. A good
	 *                                  compromise between supporting asynchronity (which requires
	 *                                  a large history) and minimizing required memory is 20 times
	 *                                  the number of candidates that are created in each round,
	 *                                  for many applications. 
	 * @param matchingPartsHistorySize The number of matching key parts to keep in the history for
	 *                                 each remote host. This needs to be large enough to hold sufficient
	 *                                 entropy (i.e. number of parts) for creating a shared key. A 
	 *                                 good compromise between asynchronity (which requires a large
	 *                                 history) and minimizing required memory and key search time
	 *                                 if 10 times the minimum number of rounds for action times the
	 *                                 number of candidates that are create in each round. 
	 * @param maxMatchAge The maximum age, in seconds, to keep matches with remote hosts before pruning
	 *                    them. For many interactive protocols, 300 (5 minutes) is a good value.
	 * @param minMatchingRoundsFraction The minimum fraction of rounds that need at least one match before a key will
	 *                                 be generated.
	*                                  The sum of minMatchingRoundsFraction and maxMismatchRoundsFraction
	*                                  must be <= 1.0, because a mismatch is defined as (1-match), and there
	*                                  must be a clear distinction between match and mismatch.
	 * @param minMatchingEntropy The minimum entropy that needs to be collected in matching key parts
	 *                           before a key will be generated.
	 * @param maxMismatchRoundsFraction The maximum fraction of rounds that are allowed to have no
	 *                                  match before the protocol is aborted with an error. 
	 *                                  The sum of minMatchingRoundsFraction and maxMismatchRoundsFraction
	 *                                  must be <= 1.0, because a mismatch is defined as (1-match), and there
	 *                                  must be a clear distinction between match and mismatch.
	 * @param minNumRoundsForAction The minimum number of (local) rounds that need to pass with each
	 *                              remote host before any action (success or failure) is taken for that
	 *                              remote host. 
	 * @throws IOException 
	 */
	protected CKPOverUDP(int udpReceivePort, int udpSendPort, String multicastGroup, String instanceId, 
			boolean broadcastCandidates, boolean sendMatches,
			int localCandidateHistorySize, int matchingPartsHistorySize, int maxMatchAge,
			float minMatchingRoundsFraction, float minMatchingEntropy, float maxMismatchRoundsFraction,  
			int minNumRoundsForAction, boolean useJSSE) throws IOException {
		this.useJSSE = useJSSE;
		this.instanceId = instanceId;
		this.broadcastCandidates = broadcastCandidates;
		this.sendMatches = sendMatches;
		this.minMatchingRoundsFraction = minMatchingRoundsFraction;
		this.minMatchingEntropy = minMatchingEntropy;
		this.maxMismatchRoundsFraction = maxMismatchRoundsFraction;
		this.minNumRoundsForAction = minNumRoundsForAction;
		
		// sanity check
		if (minMatchingRoundsFraction + maxMismatchRoundsFraction > 1.0f)
			throw new IllegalArgumentException("minMatchingRoundsFraction + maxMismatchRoundsFraction must be <= 1.0");
		
		// remember the last 5 incoming messages for matching with new local key parts
		this.incomingKeyPartsBuffer= new Object[5][];
		this.incomingKeyPartsBufferIndex = 0;
		this.incomingCandKeyBuffer= new Object[5][];
		this.incomingCandKeyBufferIndex = 0;

		channel = new UDPMulticastSocket(udpReceivePort, udpSendPort, multicastGroup);
		channel.addIncomingMessageListener(new UDPMessageHandler());
		// channel.dispose() takes care of calling stopListening();
		channel.startListening();
		ckp = new CandidateKeyProtocol(localCandidateHistorySize, matchingPartsHistorySize, 
				maxMatchAge, instanceId, useJSSE);
		generatedKeys = new HashMap();
	}

	/** This method should be called whenever new key material for a round has been generated.
	 * It will trigger sending the associated identifiers when broadcastCandidates is set.
	 * @param keyParts The candidate key parts for this round.
	 * @param entropy An estimation of the entropy of all the key parts.
	 * @see #broadcastCandidates
	 */
	protected void addCandidates(byte[][] keyParts, float entropy) throws InternalApplicationException, IOException {
		// this is synchronized so that handleMessage will not try to match while we 
		synchronized (globalLock) {
			{
				/* Optimization: Check for duplicates in the key parts - this costs some 
				 * performance now, but can save significantly later on. This whole block can be
				 * skipped entirely, and it will still work the very same way (maybe just doing some
				 * more work later on). However, hashing arrays should be fast, so performance
				 * impact should be negligable anyways.
				 */
				HashMap parts = new HashMap();
				for (int i=0; i<keyParts.length; i++) {
					parts.put(new Integer(Arrays.hashCode(keyParts[i])), keyParts[i]);
				}
				// sanity check
				if (parts.size() > keyParts.length)
					throw new InternalApplicationException("Set of hashed key parts is bigger than the original vector. This should not happen");
				if (parts.size() < keyParts.length) {
					logger.info("Duplicate feature vectors detected: " + parts.size() +
							" unique vectors out of " + keyParts.length +
							(instanceId != null ? " [" + instanceId + "]" : ""));
					keyParts = new byte[parts.size()][];
					Iterator iter = parts.values().iterator();
					for (int i=0; i<keyParts.length; i++)
						keyParts[i] = (byte[]) iter.next();
				}
			}
			
			CandidateKeyPartIdentifier[] candidateKeyParts = ckp.generateCandidates(keyParts, entropy);
			/* send out as many UDP multicast packets as necessary to transmit all the generated 
			 * candidate key parts
			 */
			if (broadcastCandidates) {
				logger.debug("Broadcasting " + candidateKeyParts.length + " candidate key parts" + 
						(instanceId != null ? " [" + instanceId + "]" : ""));
				byte[] buffer = new byte[Maximum_Udp_Data_Size];
				int outIndex = 0;
				for (int i=0; i<candidateKeyParts.length; i++) {
					if (outIndex == 0 || outIndex+candidateKeyParts[i].hash.length*2+1 >= Maximum_Udp_Data_Size) {
						// send the old packet and construct a new one
						if (outIndex > 0) {
							logger.debug("Sending UDP packet with " + outIndex + " bytes" + 
									(instanceId != null ? " [" + instanceId + "]" : ""));
							byte[] packet = new byte[outIndex];
							System.arraycopy(buffer, 0, packet, 0, outIndex);
							channel.sendMulticast(packet);
						}
						
						String packetStart = Protocol_CandidateKeyPart + candidateKeyParts[i].round + " ";
						// default ASCII coding
						System.arraycopy(packetStart.getBytes(), 0, buffer, 0, packetStart.length());
						outIndex = packetStart.length();

						logger.debug("Started new UDP packet with round " + candidateKeyParts[i].round + 
								" for candidate number " + i + 
								(instanceId != null ? " [" + instanceId + "]" : ""));
					}
					// small otpimization: the candidate number is not transmitted explicitly, but just as its position
					// but do a sanity check here (optimizations are always dangerous)
					if (candidateKeyParts[i].candidateNumber != i) 
						logger.warn("Locally generatared candidate number " + candidateKeyParts[i].candidateNumber +
								" in round " + candidateKeyParts[i].round + " does not match its position " +
								"in the array: " + i + ". Something might be subtly broken!" +
								(instanceId != null ? " [" + instanceId + "]" : ""));
					String cand = new String(Hex.encodeHex(candidateKeyParts[i].hash)) + " ";
					System.arraycopy(cand.getBytes(), 0, buffer, outIndex, cand.length());
					outIndex += cand.length();
				}
				if (outIndex > 0) {
					logger.debug("Sending UDP packet with " + outIndex + " bytes" + 
							(instanceId != null ? " [" + instanceId + "]" : ""));
					byte[] packet = new byte[outIndex];
					System.arraycopy(buffer, 0, packet, 0, outIndex);
					channel.sendMulticast(packet);
				}
			}
		
			// and also go through the list of archived yet unmatched incoming messages
			for (int i=0; i<incomingKeyPartsBuffer.length; i++)
				if (incomingKeyPartsBuffer[i] != null) {
					InetAddress sender = (InetAddress) incomingKeyPartsBuffer[i][0];
					CandidateKeyPartIdentifier[] incomingKeyParts = (CandidateKeyPartIdentifier[]) incomingKeyPartsBuffer[i][1];
					int match = ckp.matchCandidates(sender.getHostAddress(), incomingKeyParts);
					if (match > -1) {
						// yes, we have a match, handle it
						handleMatchingCandidateKeyPart(incomingKeyParts[match].round, match, sender);
						// and remove from the list to not match one incoming message twice
						incomingKeyPartsBuffer[i] = null;
					}
				}
		}
	}
	
	/** Takes care to close the UDPMulticastSocket resources properly and to wipe
	 * key material from the CandidateKeyProtocol instance.
	 */
	public void dispose() {
		ckp.wipeAll();
		channel.dispose();
		ckp = null;
		channel = null;
	}
	
	/** This hook will be called when the final verdict is that the whole 
	 * authentication protocol succeeded, i.e. both hosts signalled success on
	 * key verification.
	 * @param remote The remote host address with which the key exchange succeeded.
	 * @param sharedSessionKey The shared session key (which is different from the
	 *                         shared authentication key used for verification) that
	 *                         can now be used for subsequent secure communication.
	 */
	protected abstract void protocolSucceededHook(String remote, 
			byte[] sharedSessionKey);
	
	/** This hook will be called when the whole authentication protocol has
	 * failed. Derived classes should implement it to react to this failure.
	 * @param remote The remote host address with which the key exchange failed.
	 * @param e If not null, the exception describing the failure.
	 * @param message If not null, the message describing the failure.
	 */
	protected abstract void protocolFailedHook(String remote, 
			Exception e, String message);

	/** This hook will be called when the whole authentication protocol has
	 * made some progress. Derived classes should implement it to react to 
	 * this progress.
	 * @param remote The remote host address with which the key exchange progressed.
	 * @param cur @see AuthenticationProgressHandler#AuthenticationProgress
	 * @param max @see AuthenticationProgressHandler#AuthenticationProgress
	 * @param message @see AuthenticationProgressHandler#AuthenticationProgress
	 */
	protected abstract void protocolProgressHook(String remote, 
			int cur, int max, String message);
	
	/** This is just a small helper function to send match messages, try to
	 * generate candidate keys, and check for matches with previously received
	 * candidate keys. 
	 * @throws InternalApplicationException 
	 * @see UDPMessageHandler#handleMessage(byte[], int, int, Object)
	 * @see #addCandidates(byte[][], float)
	 */
	private void handleMatchingCandidateKeyPart(int round, int match, InetAddress remote) throws IOException, InternalApplicationException {
		logger.debug("Number " + match + " of the incoming candidate key parts from host " + remote + " matches" + 
				(instanceId != null ? " [" + instanceId + "]" : ""));
		// optionally flag
		if (sendMatches) {
			String ackPacket = Protocol_CandidateMatch + round + " " + match;
			channel.sendTo(ackPacket.getBytes(), remote);
		}
	
		/* Make sure that this is not interrupted, or else the following could (and did)
		 * happen:
		 * 1. checkforKeyGeneration generates a key, sends a KEY message to the remote.
		 * 2. The remote finds a matching key, sends an ACK message to us.
		 * 3. handleMessage processes the ACK messages and wipes the local state before
		 *    the old (buffered) incoming candidate key messages have been processed.
		 * 4. The loop below is executed, but no matching key can be generated since we
		 *    already wiped state.
		 * Solution: make sure that the block below is not interrupted by handleMessage
		 * by making it atomic with regards to the same synchronization object used in
		 * handleMessage.
		 */
		// this should actually not be necessary here, because addMessage is also synchronized
		// as whole, but leave it here since it doesn't hurt and is the secure way...
		synchronized (globalLock) {
			/* Since a new match was now added the the local match list, check 
			 * if there are enough to create a candidate key.
			 */ 
			checkForKeyGeneration(remote);

			// and also go through the list of archived yet unmatched incoming messages
			for (int i=0; i<incomingCandKeyBuffer.length; i++)
				if (incomingCandKeyBuffer[i] != null) {
					InetAddress sender = (InetAddress) incomingCandKeyBuffer[i][0];
					int numParts = ((Integer) incomingCandKeyBuffer[i][1]).intValue();
					byte[] candKeyHash = (byte[]) incomingCandKeyBuffer[i][2];
					// the call to checkForKeyMatch already handles to send the message if successful
					if (checkForKeyMatch(sender, numParts, candKeyHash))
						// and remove from the list to not match one incoming message twice
						incomingCandKeyBuffer[i] = null;
				}
		}
	}
	
	/** This helper function checks if the criteria for generating a key are
	 * already fulfilled for a remote host. If the "positive" criteria are
	 * fulfilled, this function returns true, otherwise false. Additionally, if
	 * the "negative" criteria are fulfilled, this function also returns false,
	 * but immediately aborts the protocol and generates a protocol failure event.
	 * @return true if a key can be generated according to the criteria, false
	 *         otherwise.
	 * @see #minNumRoundsForAction
	 * @see #minMatchingRoundsFraction
	 * @see #minMatchingEntropy
	 * @see #maxMismatchRoundsFraction
	 */
	private boolean checkKeyCriteria(InetAddress remoteHost) throws InternalApplicationException {
		String remoteHostAddress = remoteHost.getHostAddress();
		
		// only if enough rounds have passed in total with this remote host can we do anything
		logger.debug("Checking criteria for generating a key for remote host " + remoteHostAddress + 
				": " + ckp.getNumLocalRounds(remoteHostAddress) + " local rounds, " +
				ckp.getMatchingRoundsFraction(remoteHostAddress) + " matching, " +
				ckp.getSumMatchEntropy(remoteHostAddress) + " entropy sum; " +
				minNumRoundsForAction + " minimum rounds for action, " +
				minMatchingRoundsFraction + " minimum match treshold, " +
				minMatchingEntropy + " minimum entropy sum threshold, " + 
				maxMismatchRoundsFraction + " maximum mismatch threshold" +
				(instanceId != null ? " [" + instanceId + "]" : ""));
		if (ckp.getNumLocalRounds(remoteHostAddress) >= minNumRoundsForAction) {
			// check if the "positive" criteria are fulfilled so that we can try and create a candidate key
			if (ckp.getMatchingRoundsFraction(remoteHostAddress) >= minMatchingRoundsFraction &&
					ckp.getSumMatchEntropy(remoteHostAddress) >= minMatchingEntropy) {
				logger.info("Positive criteria are fulfilled for remote host " + remoteHostAddress + 
						", can now generate candidate key" + 
						(instanceId != null ? " [" + instanceId + "]" : ""));
				return true;
			}
			// check if the "negative" criteria are fulfilled
			else if ((1-ckp.getMatchingRoundsFraction(remoteHostAddress)) >= maxMismatchRoundsFraction) {
				logger.warn("Negative criteria are fulfilled for remote host " + remoteHostAddress + 
						", aborting protocol and generating authentication failure event" +
						(instanceId != null ? " [" + instanceId + "]" : ""));
				// abort protocol and generated authentication failure event
				authenticationFailed(remoteHost, true, null, "Too many rounds without a matching key part encountered");
				return false;
			}
			else {
				logger.debug("Enough local rounds have passed with remote host " + remoteHostAddress +
						", but neither positive nor negative criteria are fulfilled" +
						(instanceId != null ? " [" + instanceId + "]" : ""));
				return false;
			}
		}
		else {
			logger.debug("Not enough local rounds have passed yet for remote host " +
					remoteHostAddress + " to check for any action (" + 
					ckp.getNumLocalRounds(remoteHostAddress) + " passed, want " + minNumRoundsForAction + ")" +
					(instanceId != null ? " [" + instanceId + "]" : ""));
			return false;
		}
	}

	/** This helper function checks if a key can already be generated and sends it
	 * to the remote host, if yes.
	 * @throws InternalApplicationException 
	 */
	private void checkForKeyGeneration(InetAddress remoteHost) throws InternalApplicationException {
		String remoteHostAddress = remoteHost.getHostAddress();

		if (checkKeyCriteria(remoteHost)) {
			try {
				CandidateKey candKey = ckp.generateKey(remoteHostAddress);
				// and remember this key for later matching with the acknowledge
				GeneratedKeyCandidates genList = null;
				if (generatedKeys.containsKey(remoteHostAddress)) {
					genList = (GeneratedKeyCandidates) generatedKeys.get(remoteHostAddress);
				}
				else {
					logger.debug("No list of generated keys found for remote host '" + remoteHostAddress + 
							"', creating new list" +
							(instanceId != null ? " [" + instanceId + "]" : ""));
					genList = new GeneratedKeyCandidates();
					generatedKeys.put(remoteHostAddress, genList);
				}
				logger.debug("Inserting candidate key for host " + remoteHostAddress + " at list position " + genList.index +
						(instanceId != null ? " [" + instanceId + "]" : ""));
				genList.list[genList.index++] = candKey;
				if (genList.index == genList.list.length)
					genList.index = 0;
			
				logger.debug("Sending candidate key of " + candKey.numParts + " parts with hash " + 
						new String(Hex.encodeHex(candKey.hash)) +
						(instanceId != null ? " [" + instanceId + "]" : ""));
				String candKeyPacket = Protocol_CandidateKey + candKey.numParts + " " +
						new String(Hex.encodeHex(candKey.hash));
				channel.sendTo(candKeyPacket.getBytes(), remoteHost);
			}
			catch (InternalApplicationException e) {
				logger.error("Could not generate key: " + e + 
					(instanceId != null ? " [" + instanceId + "]" : ""));
			} catch (IOException e) {
				logger.debug("Can not send candidate key packet: " + e + 
					(instanceId != null ? " [" + instanceId + "]" : ""));
			}
		}
	}
	
	/** This helper function checks if a key with the same hash as an incoming candidate
	 * key can be created locally and calls authenticationSucceededStage1 if yes.
	 * @return true if the key could be generated, false otherwise. 
	 * @throws InternalApplicationException 
	 * @throws IOException 
	 * @see UDPMessageHandler#handleMessage(byte[], int, int, Object)
	 * @see #addCandidates(byte[][], float)
	 */
	private boolean checkForKeyMatch(InetAddress remoteHost, int numParts, byte[] candKeyHash) throws InternalApplicationException, IOException {
		CandidateKey candKey = ckp.searchKey(remoteHost.getHostAddress(), candKeyHash, numParts);
		
		if (candKey != null) {
			// this is just a sanity check
			for (int i=0; i<candKeyHash.length; i++)
				if (candKey.hash[i] != candKeyHash[i])
					throw new InternalApplicationException("Search for matching key returned "+
						"different hash from what we searched for. This should not happen!" + 
						(instanceId != null ? " [" + instanceId + "]" : ""));
			
			// also check if our local criteria for generating a key are fulfilled
			if (checkKeyCriteria(remoteHost)) {
				logger.info("Generated local key that matches candidate key identifier " +
						new String(Hex.encodeHex(candKey.key)) + " received from " + remoteHost.getHostAddress() + " " +
						(instanceId != null ? " [" + instanceId + "]" : ""));
				// this sends a key acknowledge message to the remote host
				authenticationSucceededStage1(remoteHost, candKey.hash, candKey.key);
				return true;
			}
			else {
				logger.warn("Received candidate key from remote host " + remoteHost.getHostAddress() +
						" and successfully generated matching key, but local criteria for key generation " +
						"are not yet fulfilled. Ignoring this candidate key." +
						(instanceId != null ? " [" + instanceId + "]" : ""));
				return false;
			}
		}
		else {
			logger.debug("Could not generate local key that matches received candidate key identifier" + 
					(instanceId != null ? " [" + instanceId + "]" : ""));
			return false;
		}
		
	}

	/** Small helper function to raise an authentication failure event and wipe all state
	 * for the specified remote host. If some state was kept about this remote host,
	 * a protocol termination message is also sent to it.
	 */
	private void authenticationFailed(InetAddress remoteHost, boolean sendTerminateMsg,
			Exception e, String msg) {
		String remoteHostAddress = remoteHost.getHostAddress();

		logger.debug("Authentication with remote host " + remoteHostAddress + " failed" +
				(e != null ? " with exception '" + e + "'" : "") +
				(msg != null ? " with message '" + msg + "'" : "") + 
				(instanceId != null ? " [" + instanceId + "]" : ""));
		if (ckp.wipe(remoteHostAddress) && sendTerminateMsg) {
			// ok, there was some state, also send a termination message
			String termPacket = Protocol_Terminate;
			logger.debug("Sending termination message to remote host" + 
					(instanceId != null ? " [" + instanceId + "]" : ""));
			try {
				channel.sendTo(termPacket.getBytes(), remoteHost);
			}
			catch (IOException f) {
				logger.error("Could not send protocol termination message to remote host: " + f + 
						(instanceId != null ? " [" + instanceId + "]" : ""));
			}
		}
		// also wipe the state local to this class
		wipe(remoteHostAddress);
		
		// raise the event to notify others
		raiseAuthenticationFailureEvent(remoteHostAddress, e, msg);
		
		// also allow derived classes to do special failure handling
		protocolFailedHook(remoteHostAddress, e, msg);
	}
	
	/** Small helper function to deal with authentication success in stage 1. For details 
	 * about the two stages, see @see GeneratedKeyCandidates#foundMatchingKeyHash.
	 */
	private void authenticationSucceededStage1(InetAddress remoteHost,
			byte[] foundKeyHash, byte[] foundKey) throws InternalApplicationException, IOException {
		String remoteHostAddress = remoteHost.getHostAddress();

		if (! generatedKeys.containsKey(remoteHostAddress)) {
			logger.debug("Got candidate key message from remote host " + remoteHostAddress + 
					" before generating our own key, creating new list" + 
					(instanceId != null ? " [" + instanceId + "]" : ""));
			generatedKeys.put(remoteHostAddress, new GeneratedKeyCandidates());
		}

		GeneratedKeyCandidates cand = (GeneratedKeyCandidates) generatedKeys.get(remoteHostAddress);
		if (cand.foundMatchingKey != null) {
			logger.warn("Not overwriting the found matching key for remote host " + remoteHost +
					", because stage 2 not entered yet." + 
					(instanceId != null ? " [" + instanceId + "]" : ""));
		}
		else {
			cand.foundMatchingKey = foundKey;

			String ackPacket = Protocol_KeyAcknowledge + new String(Hex.encodeHex(foundKeyHash));
			logger.debug("Sending key acknowledge message for hash " + new String(Hex.encodeHex(foundKeyHash))
					+ " to remote host " + remoteHostAddress + 
					(instanceId != null ? " [" + instanceId + "]" : ""));
			channel.sendTo(ackPacket.getBytes(), remoteHost);
		}
	}

	/** Small helper function to deal with authentication success in stage 2. It raises an
	 * authentication success event and wipes all state for the specified remote host when
	 * finished. For details about the two stages, see @see GeneratedKeyCandidates#foundMatchingKeyHash.
	 */
	private void authenticationSucceededStage2(InetAddress remoteHost,
			byte[] ackedKeyHash) throws InternalApplicationException, IOException {
		String remoteHostAddress = remoteHost.getHostAddress();

		if (! generatedKeys.containsKey(remoteHostAddress))
			throw new InternalApplicationException("Got key acknowledge message from remote host " + 
					remoteHostAddress + " with no locally generated key candidates or a found matching key. This should not happen!" + 
					(instanceId != null ? " [" + instanceId + "]" : ""));

		GeneratedKeyCandidates cand = (GeneratedKeyCandidates) generatedKeys.get(remoteHostAddress);

		/* We need to look for the locally generated candidate key that has just been acknowledged
		 * in any case.
		 */
		byte[] ackedMatchingKey = null;
		// need to look through the recent list to find a key with that hash
		for (int i=0; i<cand.list.length && ackedMatchingKey == null; i++) {
			if (cand.list[i] != null) {
				boolean match=true;
				for (int j=0; j<ackedKeyHash.length && match; j++)
					if (cand.list[i].hash[j] != ackedKeyHash[j])
						match = false;
				if (match) {
					logger.debug("Found recently generated key matching the acknowledged hash" + 
							(instanceId != null ? " [" + instanceId + "]" : ""));
					ackedMatchingKey = cand.list[i].key;
				}
			}
		}
		// sanity check
		if (ackedMatchingKey == null) {
			logger.warn("Could not find a recently generated key matching the acknowledged hash. " +
					"This might indicate an ongoing attack! " +
					"Wiping state for remote host " + remoteHostAddress + 
					(instanceId != null ? " [" + instanceId + "]" : ""));
			authenticationFailed(remoteHost, true, null, 
				"Message received that shouldn't. Remote host is bad");
			return;
		}
		
		/* Ok, found the locally generated key to the incoming acknowledge, but there are now two possibilities:
		 * - We have already received a key candidate from the remote host and found a key that
		 *   matched it. In this case, it will be stored in cand.foundMatchingKey.
		 * - We have not yet received a key candidate from the remote host, but the remote host
		 *   has acknowledges our key candidate. In this case, cand.foundMatchingKey will not
		 *   be set.
		 */
		byte[] realSharedKey = null;
		if (cand.foundMatchingKey != null) {
			logger.info("Received an acknowledge for a locally generated key, and already " +
					"found and acknowledged a key matching a remote candidate key." + 
					"Thus using the combination of both." +
					(instanceId != null ? " [" + instanceId + "]" : ""));

			// another sanity check
			if (ackedMatchingKey.length != cand.foundMatchingKey.length)
				throw new InternalApplicationException("Found matching key has different length than " +
						"acknowledged key" + 
						(instanceId != null ? " [" + instanceId + "]" : ""));
			/* And now check if the same key has been acknowledged that we ourselves
			 * acknowledged (because a key with the same hash could be generated locally).
			 */
			boolean receivedAckMatchesOwnAck = true;
			for (int i=0; i<cand.foundMatchingKey.length && receivedAckMatchesOwnAck; i++)
				if (ackedMatchingKey[i] != cand.foundMatchingKey[i])
					receivedAckMatchesOwnAck = false;
			if (!receivedAckMatchesOwnAck) {
				// just XOR them together to use just one key, independent of their order 
				// (which might be different on both sides)
				realSharedKey = new byte[cand.foundMatchingKey.length];
				for (int i=0; i<cand.foundMatchingKey.length; i++)
					realSharedKey[i] = (byte) (cand.foundMatchingKey[i] ^ ackedMatchingKey[i]);
				logger.info("Overlapping candidate key match and acknowledgment messages detected, using " + 
						"both keys: " + new String(Hex.encodeHex(realSharedKey)) + 
						(instanceId != null ? " [" + instanceId + "]" : ""));
			}
			else {
				// When they match, do <b>NOT</b> XOR them together.
				// [A^A = 0....]
				realSharedKey = cand.foundMatchingKey;
				logger.info("Last matching candidate key has been acknowledged, thus both acknowledges should match. " +
						"Using it as the shared key: " + new String(Hex.encodeHex(realSharedKey)) + 
						(instanceId != null ? " [" + instanceId + "]" : ""));
			}
		}
		else {
			logger.info("Received an acknowledge for a locally generated key, and did " +
					"not yet receive a candidate key from the remote host that we acknowledged. " +
					"Thus using the locally generated key that has now been acknowledged." +
					(instanceId != null ? " [" + instanceId + "]" : ""));
			realSharedKey = ackedMatchingKey;
		}

		// now that we finally have the (real) shared key, can wipe the state
		wipe(remoteHostAddress);
		ckp.wipe(remoteHostAddress);
		
		// raise the event to notify others
		raiseAuthenticationSuccessEvent(remoteHostAddress, realSharedKey);
			
		// also allow derived classes to do special success handling
		protocolSucceededHook(remoteHostAddress, realSharedKey);
	}

	/** Small helper function to wipe the generated candidate keys for a remote host. */
	private void wipe(String remoteHostAddress) {
		GeneratedKeyCandidates cand = (GeneratedKeyCandidates) generatedKeys.remove(remoteHostAddress);
		if (cand != null) {
			for (int i=0; i<cand.list.length; i++)
				if (cand.list[i] != null) {
					for (int j=0; j<cand.list[i].key.length; j++)
						cand.list[i].key[j] = 0;
					for (int j=0; j<cand.list[i].hash.length; j++)
						cand.list[i].hash[j] = 0;
				}
			if (cand.foundMatchingKey != null)
				for (int j=0; j<cand.foundMatchingKey.length; j++)
					cand.foundMatchingKey[j] = 0;
		}
	}
	
	/** This is a helper class for handling incoming UDP packets. It is the
	 * heart of CKoverUDP and triggers most of the work by calling the helper functions
	 * declared above.
	 */
	private class UDPMessageHandler implements MessageListener {
		public void handleMessage(byte[] message, int offset, int length, Object sender) {
			/* should be synchronized so that we process packets in their order of arrival
			 * (and not interrupt or be interrupted by other local methods) 
			 */
			synchronized (globalLock) {
				// only use the IP address part, but not the host (which will be dynamic for sending packets)
				String remoteHostAddress = ((InetAddress) sender).getHostAddress();
				
				// this is inefficient, but Java is anyway, so don't care at the moment...
				byte[] packet = new byte[length];
				System.arraycopy(message, offset, packet, 0, length);
				String pack = new String(packet);
				logger.debug("Received UDP packet with  " + pack.length() + " bytes from " + remoteHostAddress + 
						(instanceId != null ? " [" + instanceId + "]" : ""));
				try {
					// this handles the different packet types
					if (pack.startsWith(Protocol_CandidateKeyPart)) {
						// for a candidate key part package, try to match all of the candidates and
						// optionally send back the matching number
						int off = pack.indexOf(' ', Protocol_CandidateKeyPart.length());
						int round = Integer.parseInt(pack.substring(Protocol_CandidateKeyPart.length(), off));
						StringTokenizer st = new StringTokenizer(pack.substring(off+1));
						CandidateKeyPartIdentifier[] keyParts = new CandidateKeyPartIdentifier[st.countTokens()]; 
						if (keyParts.length > 0) {
							logger.debug("Received packet with " + keyParts.length + " candidate key parts for round " + round + 
									(instanceId != null ? " [" + instanceId + "]" : ""));
							for (int i=0; i<keyParts.length; i++) {
								keyParts[i] = new CandidateKeyPartIdentifier();
								keyParts[i].hash = Hex.decodeHex(st.nextToken().toCharArray());
								keyParts[i].round = round;
								// small otpimization: the candidate number is not transmitted explicitly, but just as its position
								keyParts[i].candidateNumber = (byte) i;
							}
							int match = ckp.matchCandidates(remoteHostAddress, keyParts);
							if (match > -1) {
								// yes, we have a match, handle it
								handleMatchingCandidateKeyPart(round, match, (InetAddress) sender);
							}
							else {
								/* No match, but remember the received key parts in case the matching local 
								 * candidates are about to be added. 
								 */
								logger.debug("None of the incoming candidate key parts matches, storing it in " +
										"buffer for future reference"+ 
										(instanceId != null ? " [" + instanceId + "]" : ""));
								Object[] tmp = new Object[2];
								tmp[0] = sender;
								tmp[1] = keyParts;
								incomingKeyPartsBuffer[incomingKeyPartsBufferIndex++] = tmp;
								if (incomingKeyPartsBufferIndex == incomingKeyPartsBuffer.length)
									incomingKeyPartsBufferIndex = 0;
								
								/* But since this was a mismatch, need to check if negative criteria might be fulfilled now.
								 * This method call takes care of it.
								 */
								checkKeyCriteria((InetAddress) sender);
								/* If positive criteria are fulfilled, don't care here. When the last
								 * match was received (or the last candidate key message), no key could
								 * be generated, so now it will not be possible either (no changes to
								 * the "positive" match set).
								 */
							}
						}
						else
							logger.warn("Received candidate key parts oacket without any key parts, ignoring it" +
									(instanceId != null ? " [" + instanceId + "]" : ""));
					}
					else if (pack.startsWith(Protocol_CandidateMatch)) {
						// for an incoming match, just add them
						int off = pack.indexOf(' ', Protocol_CandidateMatch.length());
						int round = Integer.parseInt(pack.substring(Protocol_CandidateMatch.length(), off));
						int match = Integer.parseInt(pack.substring(off+1));
						logger.debug("Received packet with matching index " + match + " for round " + round + 
								(instanceId != null ? " [" + instanceId + "]" : ""));
						ckp.acknowledgeMatches(remoteHostAddress, round, match);

						/* Since a new match was now added the the local match list, check 
						 * if there are enough to create a candidate key.
						 */ 
						checkForKeyGeneration((InetAddress) sender);
					}
					else if (pack.startsWith(Protocol_CandidateKey)) {
						int off = pack.indexOf(' ', Protocol_CandidateKey.length());
						int numParts = Integer.parseInt(pack.substring(Protocol_CandidateKey.length(), off));
						logger.debug("Received candidate key composed of " + numParts + " parts" +
								(instanceId != null ? " [" + instanceId + "]" : ""));
						byte[] candKeyHash = Hex.decodeHex(pack.substring(off+1).toCharArray());
						
						if (! checkForKeyMatch((InetAddress) sender, numParts, candKeyHash)) {
							/* No match, but remember the received candidate key in case the match local 
							 * candidates are about to be added. 
							 */
							logger.debug("Could not generate key with same hash as incoming candidate key, storing it in " +
									"buffer for future reference"+ 
									(instanceId != null ? " [" + instanceId + "]" : ""));
							Object[] tmp = new Object[3];
							tmp[0] = sender;
							tmp[1] = new Integer(numParts);
							tmp[2] = candKeyHash;
							incomingCandKeyBuffer[incomingCandKeyBufferIndex++] = tmp;
							if (incomingCandKeyBufferIndex == incomingCandKeyBuffer.length)
								incomingCandKeyBufferIndex = 0;
						}
					}
					else if (pack.startsWith(Protocol_KeyAcknowledge)) {
						byte[] ackHash = Hex.decodeHex(pack.substring(Protocol_KeyAcknowledge.length()).toCharArray());
						logger.debug("Received key acknowledge with hash " + new String(Hex.encodeHex(ackHash)) + 
								(instanceId != null ? " [" + instanceId + "]" : ""));
						authenticationSucceededStage2((InetAddress) sender, ackHash);
					}
					else if (pack.startsWith(Protocol_Terminate)) {
						logger.debug("Received protocol termination request, wiping local state" + 
								(instanceId != null ? " [" + instanceId + "]" : ""));
						// no need to reply with a terminate message...
						authenticationFailed((InetAddress) sender, false, null, 
								"Received termination message from remote host");
					}
					else {
						logger.error("Received unknown packet type '" + pack + "', ignoring it" + 
								(instanceId != null ? " [" + instanceId + "]" : ""));
					}
				}
				catch (NumberFormatException e) {
					logger.error("Can not decode number, ignoring whole packet" + 
							(instanceId != null ? " [" + instanceId + "]" : ""));
					authenticationFailed((InetAddress) sender, true, e, "Could not decode number");
				} catch (DecoderException e) {
					logger.error("Can not decode hash, ignoring whole packet" + 
							(instanceId != null ? " [" + instanceId + "]" : ""));
					authenticationFailed((InetAddress) sender, true, e, "Could not decode hash");
				} catch (IOException e) {
					logger.error("Can not send packet" + 
							(instanceId != null ? " [" + instanceId + "]" : ""));
					authenticationFailed((InetAddress) sender, true, e, "Could not send packet");
				} catch (InternalApplicationException e) {
					logger.error("Could not search for matching key: " + e + 
							(instanceId != null ? " [" + instanceId + "]" : ""));
					authenticationFailed((InetAddress) sender, true, e, "Could not search for matching key");
				}
			}
		}
	}
}
