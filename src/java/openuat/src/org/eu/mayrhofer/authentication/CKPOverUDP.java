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
import java.util.HashMap;
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
 * TODO: Also emit protocol failed events based on criteria!
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
	
	/** The minimum number of matching parts before a key will be created. This is set by the
	 * constructor.
	 * @see #CKPOverUDP(int, String, String, boolean, boolean, int, int, boolean)
	 */
	private int minMatchingParts;
	
	/** The minimum entropy of the matching parts before a key will be created. This is set by the
	 * constructor.
	 * @see #CKPOverUDP(int, String, String, boolean, boolean, int, int, boolean)
	 */
	private int minMatchingEntropy;
	
	/** Just a small helper class to keep a list of generated keys for each host. */
	private class GeneratedKeyCandidates {
		/** A circular buffer of the last candidate keys that have been generated. It is used for
		 * selecting the key when a key acknowledgment is received (because another key might have been
		 * generated while the network messages were delivered.
		 */
		private CandidateKey[] list;
		/** The index where to insert the next generated candidate key into generatedKeys.
		 * @see #generatedKeys
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
			// and keep a history of the last 5 generated keys
			list = new CandidateKey[5];
			index = 0;
			foundMatchingKey = null;
		}
	}
	/** Keep one list for each remote host we have contact to. Keys are remote object identifiers
	 * (in this case InetAddress objects), values are GeneratedKeyCandidates. */
	HashMap generatedKeys = new HashMap();
	
	/** Construct the object by initializing basic variables.
	 * 
	 * @param udpPort The UDP port to use for listening and for connecting to remote hosts.
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
	 * @param minMatchingParts The minimum number of key parts that need to match before a key will
	 *                         be generated. The CandidateKeyProtocol instance will be initialized
	 *                         with twice this number of entries in the matching part history and
	 *                         8 times this number of entries in the recent candidates history.
	 * @param minMatchingEntropy The minimum entropy that needs to be collected in matching key parts
	 *                           before a key will be generated.
	 * @throws IOException 
	 */
	protected CKPOverUDP(int udpPort, String multicastGroup, String instanceId, 
			boolean broadcastCandidates, boolean sendMatches, 
			int minMatchingParts, int minMatchingEntropy, boolean useJSSE) throws IOException {
		this.useJSSE = useJSSE;
		this.instanceId = instanceId;
		this.broadcastCandidates = broadcastCandidates;
		this.sendMatches = sendMatches;
		this.minMatchingParts = minMatchingParts;
		this.minMatchingEntropy = minMatchingEntropy;

		channel = new UDPMulticastSocket(udpPort, multicastGroup);
		channel.addIncomingMessageListener(new UDPMessageHandler());
		// channel.dispose() takes care of calling stopListening();
		channel.startListening();
		// keep the match history for each remote host for 5 minutes - should really be enough
		ckp = new CandidateKeyProtocol(minMatchingParts*8, minMatchingParts*2, 300, instanceId, useJSSE);
		generatedKeys = new HashMap();
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
				String cand = new String(Hex.encodeHex(candidateKeyParts[i].hash)) + " ";
				System.arraycopy(cand.getBytes(), 0, buffer, outIndex, cand.length());
				outIndex += cand.length();
			}
			if (buffer != null) {
				logger.debug("Sending UDP packet with " + outIndex + " bytes" + 
						(instanceId != null ? " [" + instanceId + "]" : ""));
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
	 * @param sharedSessionKey The shared session key (which is different from the
	 *                         shared authentication key used for verification) that
	 *                         can now be used for subsequent secure communication.
	 */
	protected abstract void protocolSucceededHook(InetAddress remote, 
			byte[] sharedSessionKey);
	
	/** This hook will be called when the whole authentication protocol has
	 * failed. Derived classes should implement it to react to this failure.
	 * @param remote The remote host with which the key exchange succeeded.
	 * @param e If not null, the exception describing the failure.
	 * @param message If not null, the message describing the failure.
	 */
	protected abstract void protocolFailedHook(InetAddress remote, 
			Exception e, String message);

	/** This hook will be called when the whole authentication protocol has
	 * made some progress. Derived classes should implement it to react to 
	 * this progress.
	 * @param remote The remote host with which the key exchange succeeded.
	 * @param cur @see AuthenticationProgressHandler#AuthenticationProgress
	 * @param max @see AuthenticationProgressHandler#AuthenticationProgress
	 * @param message @see AuthenticationProgressHandler#AuthenticationProgress
	 */
	protected abstract void protocolProgressHook(InetAddress remote, 
			int cur, int max, String message);
	
	/** This is a helper class for handling incoming UDP packets. It is the
	 * heart of CKoverUDP and does most of the work.
	 */
	private class UDPMessageHandler implements MessageListener {
		public void handleMessage(byte[] message, int offset, int length, Object sender) {
			// should be synchronized so that we process packets in their order of arrival
			synchronized (this) {
				// this is inefficient, but Java is anyway, so don't care at the moment...
				byte[] packet = new byte[length];
				System.arraycopy(message, offset, packet, 0, length);
				String pack = new String(packet);
				logger.debug("Received UDP packet with  " + pack.length() + " bytes from " + sender);
				try {
					// this handles the different packet types
					if (pack.startsWith(Protocol_CandidateKeyPart)) {
						// for a candidate key part package, try to match all of the candidates and
						// optionally send back the matching number
						int off = pack.indexOf(' ', Protocol_CandidateKeyPart.length());
						int round = Integer.parseInt(pack.substring(Protocol_CandidateKeyPart.length(), off));
						StringTokenizer st = new StringTokenizer(pack.substring(off));
						CandidateKeyPartIdentifier[] keyParts = new CandidateKeyPartIdentifier[st.countTokens()]; 
						logger.debug("Received packet with " + keyParts.length + " candidate key parts for round " + round + 
								(instanceId != null ? " [" + instanceId + "]" : ""));
						for (int i=0; i<keyParts.length; i++) {
							keyParts[i] = new CandidateKeyPartIdentifier();
							keyParts[i].hash = Hex.decodeHex(st.nextToken().toCharArray());
							keyParts[i].round = round;
						}
						int match = ckp.matchCandidates(sender, keyParts);
						if (match > -1) {
							// yes, we have a match, optionally flag that
							logger.debug("Number " + match + " of these candidate key parts matches" + 
									(instanceId != null ? " [" + instanceId + "]" : ""));
							if (sendMatches) {
								String ackPacket = Protocol_CandidateMatch + round + " " + match;
								channel.sendTo(ackPacket.getBytes(), (InetAddress) sender);
							}
						
							/* Since a new match was now added the the local match list, check 
							 * if there are enough to create a candidate key.
							 */ 
							checkForKeyGeneration((InetAddress) sender);
						}
					}
					else if (pack.startsWith(Protocol_CandidateMatch)) {
						// for an incoming match, just add them
						int off = pack.indexOf(' ', Protocol_CandidateMatch.length());
						int round = Integer.parseInt(pack.substring(Protocol_CandidateMatch.length(), off));
						int match = Integer.parseInt(pack.substring(off));
						logger.debug("Received packet with matching index " + match + " for round " + round + 
								(instanceId != null ? " [" + instanceId + "]" : ""));
						ckp.acknowledgeMatches(sender, round, match);

						/* Since a new match was now added the the local match list, check 
						 * if there are enough to create a candidate key.
						 */ 
						checkForKeyGeneration((InetAddress) sender);
					}
					else if (pack.startsWith(Protocol_CandidateKey)) {
						int off = pack.indexOf(' ', Protocol_CandidateKey.length());
						int numParts = Integer.parseInt(pack.substring(Protocol_CandidateKey.length(), off));
						logger.debug("Received candidate key composed of " + numParts + " parts");
						byte[] candKeyHash = Hex.decodeHex(pack.substring(off).toCharArray());
						CandidateKey candKey = ckp.searchKey(sender, candKeyHash, numParts);
					
						if (candKey != null) {
							// this is just a sanity check
							for (int i=0; i<candKeyHash.length; i++)
								if (candKey.hash[i] != candKeyHash[i])
									throw new InternalApplicationException("Search for matching key returned "+
										"different hash from what we searched for. This should not happen!" + 
										(instanceId != null ? " [" + instanceId + "]" : ""));
							logger.info("Generated local key that matches received candidate key identifier:" +
									new String(Hex.encodeHex(candKey.key)) + 
									(instanceId != null ? " [" + instanceId + "]" : ""));
							// this sends a key acknowledge message to the remote host
							authenticationSucceededStage1((InetAddress) sender, candKey.hash, candKey.key);
						}
						else {
							logger.debug("Could not generate local key that matches received candidate key identifier" + 
									(instanceId != null ? " [" + instanceId + "]" : ""));
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
						logger.error("Received unknown packet type, ignoring it" + 
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
					logger.error("Could not search for matching key" + 
							(instanceId != null ? " [" + instanceId + "]" : ""));
					authenticationFailed((InetAddress) sender, true, e, "Could not search for matching key");
				}
			}
		}
		
		/** Small helper function to wipe the generated candidate keys for a remote host. */
		private void wipe(InetAddress remoteHost) {
			GeneratedKeyCandidates cand = (GeneratedKeyCandidates) generatedKeys.remove(remoteHost);
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
		
		/** Small helper function to raise an authentication failure event and wipe all state
		 * for the specified remote host. If some state was kept about this remote host,
		 * a protocol termination message is also sent to it.
		 */
		private void authenticationFailed(InetAddress remoteHost, boolean sendTerminateMsg,
				Exception e, String msg) {
			logger.debug("Authentication with remote host " + remoteHost + " failed" +
					(e != null ? " with exception '" + e + "'" : "") +
					(msg != null ? " with message '" + msg + "'" : "") + 
					(instanceId != null ? " [" + instanceId + "]" : ""));
			if (ckp.wipe(remoteHost) && sendTerminateMsg) {
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
			wipe(remoteHost);
			
			// raise the event to notify others
			raiseAuthenticationFailureEvent(remoteHost, e, msg);
			
			// also allow derived classes to do special failure handling
			protocolFailedHook(remoteHost, e, msg);
		}
		
		/** Small helper function to deal with authentication success in stage 1. For details 
		 * about the two stages, see @see GeneratedKeyCandidates#foundMatchingKeyHash.
		 */
		private void authenticationSucceededStage1(InetAddress remoteHost,
				byte[] foundKeyHash, byte[] foundKey) throws InternalApplicationException, IOException {
			if (! generatedKeys.containsKey(remoteHost))
				throw new InternalApplicationException("Got candidate key message from remote host " + 
						remoteHost + " with no generated key candidates. This should not happen!" + 
						(instanceId != null ? " [" + instanceId + "]" : ""));

			GeneratedKeyCandidates cand = (GeneratedKeyCandidates) generatedKeys.get(remoteHost);
			if (cand.foundMatchingKey != null) {
				logger.warn("Not ovverwriting the found matching key for remote host " + remoteHost +
						", because stage 2 not entered yet." + 
						(instanceId != null ? " [" + instanceId + "]" : ""));
			}
			else {
				cand.foundMatchingKey = foundKey;

				String ackPacket = Protocol_KeyAcknowledge + new String(Hex.encodeHex(foundKeyHash));
				logger.debug("Sending key acknowledge message for hash " + new String(Hex.encodeHex(foundKeyHash))
						+ " to remote host " + remoteHost + 
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
			if (! generatedKeys.containsKey(remoteHost))
				throw new InternalApplicationException("Got candidate key message from remote host " + 
						remoteHost + " with no generated key candidates. This should not happen!" + 
						(instanceId != null ? " [" + instanceId + "]" : ""));

			GeneratedKeyCandidates cand = (GeneratedKeyCandidates) generatedKeys.get(remoteHost);
			if (cand.foundMatchingKey == null) {
				logger.warn("Received an acknowledge for a locally found matching key " +
						"but nothing known about this key. This might indicate an ongoing attack! " +
						"Wiping state for remote host " + remoteHost + 
						(instanceId != null ? " [" + instanceId + "]" : ""));
				authenticationFailed(remoteHost, true, null, 
						"Message received that shouldn't. Remote host is bad");
				return;
			}

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
						"Wiping state for remote host " + remoteHost + 
						(instanceId != null ? " [" + instanceId + "]" : ""));
				authenticationFailed(remoteHost, true, null, 
					"Message received that shouldn't. Remote host is bad");
				return;
			}
			// another sanity check
			if (ackedMatchingKey.length != cand.foundMatchingKey.length)
				throw new InternalApplicationException("Found matching key has different length than " +
						"acknowledged key" + 
						(instanceId != null ? " [" + instanceId + "]" : ""));
				byte[] realSharedKey = null;
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
				realSharedKey = cand.foundMatchingKey;
				logger.info("Last matching candidate key has been acknowledged, thus both acknowledges should match." +
						"Using it as the shared key: " + new String(Hex.encodeHex(realSharedKey)) + 
						(instanceId != null ? " [" + instanceId + "]" : ""));
			}

			// now that we finally have the (real) shared key, can wipe the state
			wipe(remoteHost);
			ckp.wipe(remoteHost);
			
			// raise the event to notify others
			raiseAuthenticationSuccessEvent(remoteHost, realSharedKey);
				
			// also allow derived classes to do special success handling
			protocolSucceededHook(remoteHost, realSharedKey);
		}
		
		/** This helper function checks if a key can already be generated and sends it
		 * to the remote host, if yes.
		 */
		private void checkForKeyGeneration(InetAddress remoteHost) {
			if (ckp.getNumTotalMatches(remoteHost) >= minMatchingParts &&
					ckp.getSumMatchEntropy(remoteHost) >= minMatchingEntropy) {
				logger.info("Received enough matches to generate candidate keys" + 
						(instanceId != null ? " [" + instanceId + "]" : ""));
				try {
					CandidateKey candKey = ckp.generateKey(remoteHost);
					// and remember this key for later matching with the acknowledge
					GeneratedKeyCandidates genList = null;
					if (generatedKeys.containsKey(remoteHost)) {
						genList = (GeneratedKeyCandidates) generatedKeys.get(remoteHost);
					}
					else {
						genList = new GeneratedKeyCandidates();
						generatedKeys.put(remoteHost, genList);
					}
					genList.list[genList.index++] = candKey;
					
					String candKeyPacket = Protocol_CandidateKey + candKey.numParts +
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
	}
}
