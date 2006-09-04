/* Copyright Rene Mayrhofer
 * File created 2006-05-05
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.authentication;

import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

import org.apache.commons.codec.binary.Hex;
import org.apache.log4j.Logger;
import org.eu.mayrhofer.authentication.exceptions.InternalApplicationException;
import org.eu.mayrhofer.util.Hash;

/** This class implements the candidate key protocol (CKP) as presented in
 * Rene Mayrhofer, Hans Gellersen: Shake well before use! 
 * 
 * It is an alternative to Diffie-Hellman key exchange with subsequent
 * key verification. In contrast to DH, it uses only symmetric cryptographic
 * operations and should thus be less resource hungry. Additionally, it does
 * not depend on prior synchronization of the hosts that want to authenticate,
 * but instead hosts can "tune in" to a stream of candidate keys and select
 * those that they also generated locally. It should therefore be well suited
 * for resource limited devices and implicit authentication without any specifc
 * trigger.
 * 
 * An additional feature is that it will work with multiple hosts instead of
 * just with one other peer. So multiple instances of the candidate key protocol
 * can be executed concurrently with different hosts with just one instance of
 * this class.
 * 
 * In each round, the candidate key protocol collects candidate key parts which 
 * will be assembled into a key when both hosts (or all hosts for group 
 * authentication) agree on them. As a means of key exchange, a hash value of 
 * each candidate key part is broadcast, and other hosts wishing to authenticate 
 * with this one may then flag that candidate out of the current list of candidates
 * in this iteration that they also have. A key part can thus go through three phases:
 * 1.  Features extracted form sensor data form a candidate key part and get
 *     broadcast.
 * 2a. After received a candidate, it is checked against all local candidates.
 *     When there is a match, this candidate becomes a matching key part and
 *     the candidate's number is signalled to the host that generated it.
 * 2b. The second alternative for advancing a key part from candidate to
 *     matching status is to explicitly acknowledge a match. One one host send
 *     messages to the sender of the candidate key parts in step 1, indicating
 *     which of these parts matched, then the original sender can advance these
 *     candidates to match status. 
 *     <b>Note:</b>Options 2a and 2b are not exclusive. but can be combined. 
 *     However, they will typically be used in different settings: option 2a for
 *     symmetrical settings where each hosts generates and sends candidate key 
 *     parts, while option 2b can be used for asymmetrical settings where one host
 *     sends candidate key parts and the other acknowledges matches.
 * 3.  All matching key parts are then assembled into a sliding candidate
 *     key, which also gets broadcast. When other hosts hold the same key,
 *     they acknowledge it and it can be used for secure communication.
 * 
 * In short, the whole authentication protocol should be used as follows:
 * 1. Construct the object.
 * 2. For each set of feature vectors that belong together, generate a set
 *    of candidate key parts with generateCandidates. These will be kept
 *    in an internal history.
 * 3. Send the candidate key parts to the remote host.
 * 4. Test all received candidate key parts with matchCandidates and possibly
 *    (depending on the setting, see above for details) send a message signalling 
 *    the matching one to the remote host. A list of matching key parts will also 
 *    be kept internally. 
 * 5. Use getNumTotalMatches and/or getSumMatchEntropy to decide when enough
 *    matching key material has been generated and create a candidate key with
 *    generateKey. The hash of this candidate key should be sent to the remote
 *    host.
 * 6. Test all received candidate key hashes with searchKey, and, if there is
 *    a match, acknowledge that key.
 * At this point, both hosts (or all hosts for group authentication) should hold
 * the same key and can use it for secure authentication.
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class CandidateKeyProtocol {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(CandidateKeyProtocol.class);
	/** This is a special log4j logger used for logging only statistics. It is separate from the main logger
	 * so that it's possible to turn statistics on an off independently.
	 */
	private static Logger statisticsLogger = Logger.getLogger("statistics.motionauthentication");

	/** This is used for deriving the shared key them from the one used for comparing 
	 * key to ensure that they are different. It's just some random text, the exact value really 
	 * does not matter. */
	private static final String MAGIC_COOKIE = "MAGIC COOKIE FOR SENSOR AUTHENTICAION";
	
	/** This class represents the complete identification information for a key 
	 * part candidate. It should be sent to the remote host(s) after being generated
	 * by generateCandidates. The combination of round and candidateNumber identifies
	 * a candidate key part uniquely within the window of recent key parts.
	 */
	public static class CandidateKeyPartIdentifier {
		/** A counter that is used to refer to this round. It is assumed to
		 * overflow, but not within the history window that each hosts keeps.
		 */
		public int round;
		/** The number of this candidate within the round. */
		public byte candidateNumber;
		/** This hash value is used to compare two candidate key parts without 
		 * revealing them.
		 */ 
		public byte[] hash;
	}
	
	/** This is only a helper class for keeping the internal candidates history.
	 */
	private class CandidateKeyPart implements Comparable {
		/** A counter that is used to refer to this round. It is assumed to
		 * overflow, but not within the history window that each hosts keeps.
		 */
		int round;
		/** The number of this candidate within the round. */
		byte candidateNumber;
		/** The key part itself. This needs to be kept secret and shall never be
		 * communicated directly to other hosts.
		 */
		byte[] keyPart;
		/** The hash of the key part, generated by the constructor. */
		byte[] hash;
		/** If availably, this gives an estimate of the entropy of the key part. */
		float entropy;
		
		CandidateKeyPart(byte[] keyPart, int round, byte candidateNumber, float entropy) throws InternalApplicationException {
			this.keyPart = keyPart;
			this.candidateNumber = candidateNumber;
			this.entropy = entropy;
			this.hash = Hash.doubleSHA256(keyPart, useJSSE);
			this.round = round;
		}
		
		/** Copies the public fields, i.e. counter and hash, to an identifier that can
		 * be safely sent to other hosts.
		 */
		CandidateKeyPartIdentifier extractPublicIdentifier() {
			CandidateKeyPartIdentifier ret = new CandidateKeyPartIdentifier();
			ret.round = round;
			ret.candidateNumber = candidateNumber;
			ret.hash = hash;
			return ret;
		}
		
		/** Implementation of comparable so that an array of these objects can be sorted
		 * by round number. Used by CandidateKeyProtocol#generateKey
		 */
		public int compareTo(Object o) {
			return new Integer(round).compareTo(new Integer(((CandidateKeyPart) o).round));
		}
	}
	
	/** This is only a helper class for keeping the list of matching key parts.
	 */
	private class MatchingKeyPart extends CandidateKeyPart {
		/** This is the round number that the matching part is referred to
		 * by the remote host. Set to -1 if unknown.
		 */
		int remoteRound;
		/** This is the candidate number that the matching part is referred to
		 * by the remote host. Set to -1 if unknown.
		 */
		int remoteCandidateNumber;
		
		MatchingKeyPart(CandidateKeyPart candidateBase, int remoteRound, int remoteCandidateNumber) throws InternalApplicationException {
			super(candidateBase.keyPart, candidateBase.round, candidateBase.candidateNumber, candidateBase.entropy);
			this.remoteRound = remoteRound;
			this.remoteCandidateNumber = remoteCandidateNumber;
		}
	}
	
	/** This class represents a complete candidate key, with both the private
	 * part (key) and a hash for comparing it with a remote host's candidate
	 * (hash).
	 */
	public static class CandidateKey {
		/** The number of parts that have been used to create the key. */
		public int numParts;
		/** The key itself. This <b>must</b> be kept private and may not be
		 * communicated to the remote host (or group).
		 */
		public byte[] key;
		/** A hash of the key, which may be sent to the other host (or group)
		 * for comparison.
		 */
		public byte[] hash;
		/** The round/candidate number tuples that are used locally to refer
		 * to all parts of the key. This matrix has 2 rows (first index) and 
		 * numParts columns (second column). The first row specifies the (unique)
		 * rounds that have been used and the second row gives the candidate key
		 * numbers within those rounds.
		 */
		public int[][] localIndices;
		/** The round/candidate number tuples that are used remotely to refer
		 * to all parts of the key. This matrix has 2 rows (first index) and 
		 * numParts columns (second column). The first row specifies the (unique)
		 * rounds that have been used and the second row gives the candidate key
		 * numbers within those rounds.
		 * If these remote indices are not known to this host (because the other
		 * host has not reported them), the respective values will be set to -1.
		 */
		public int[][] remoteIndices;
	}
	
	/** This helper class defines a list of matching key parts, and should be
	 * specific for each remote host (or group).
	 */
	private class MatchingKeyParts {
		/** The parts that matched with this remote host. */
		MatchingKeyPart[] parts = new MatchingKeyPart[matchHistorySize];
		/** The index where to insert the next matching key part for this host into parts.
		 * @see #parts
		 */
		int index = 0;
		/** The time when this list of matching key parts was updated last. Used for
		 * pruning of aged entries to keep memory consumption finite.
		 */
		long lastUpdate = System.currentTimeMillis();
		/** The first local round number at which a match with this remote host
		 * occured. The difference between this value and the current value of
		 * @see #lastRound therefore specifies the number of (local) rounds during
		 * which authentication with this remote host has been running.
		 */
		int firstLocalRoundNumber;
		/** The number of rounds in which a match was found. */
		int numMatchingRounds = 0;
		
		/** Initializes an instance for a remote host. It only sets @see #firstLocalRoundNumber
		 * to the current value of @see #lastRound minus 1 (because when creating this object,
		 * exactly one round has already been involved with the respective remote host).
		 */
		MatchingKeyParts() {
			this.firstLocalRoundNumber = lastRound-1;
		}
	}

	/** If set to true, the JSSE will be used, if set to false, the Bouncycastle Lightweight API. */
	private boolean useJSSE;
	
	/** This identifies the remote host (or group) with which this authentication
	 * protocol is being run. Can be used to distinguish multiple concurrent runs
	 * with different hosts.
	 */ 
	private String remoteIdentifier;
	
	/** The history of candidate key parts that were generated recently. It is used
	 * as a circular buffer and is generated by the constructor.
	 */
	private CandidateKeyPart[] recentKeyParts;
	/** The index where to insert the next candidate key part into recentKeyParts.
	 * @see #recentKeyParts
	 */
	private int recentKeyPartsIndex;
	
	/** This holds all key parts that have been signalled to match by the remote hosts.
	 * Keys are just general objects to identify the remote host (or group) with which
	 * the protocol is run. Values are of type MatchingKeyParts and are also a circular 
	 * buffers, so that a candidate key is always computed over a
	 * sliding window of candidate key parts. */
	private HashMap matchingKeyParts;
	
	/** This is used to remember how many entries to keep in the history of matching key
	 * parts, as passed to the constructor. It's used when creating new histories for 
	 * newly seen hosts in matchCandidates.
	 * @see #matchCandidates
	 */
	private int matchHistorySize;
	
	/** The maximum age for which to keep a list of matching key parts for a remote host,
	 * in milliseconds since "the epoch".
	 */
	private int maxRemoteMatchListAge;

	/** Our protocol-wide counter, i.e. a single counter for all remote hosts. Increased with
	 * each call to @see generateCandidates, i.e. every time a new local feature vector has
	 * been added. Used to generate counter values for the candidate key parts.
	 */
	private int lastRound = 0;
	
	/** Initializes the candidate key protocol, setting a few parameters and
	 * creating the local candidate key parts history.
	 * 
	 * @param candidateHistorySize The number of locally generated candidate key parts
	 *                             to keep in the history. This list will be used to match
	 *                             incoming identifiers and thus serves as a time-window for
	 *                             past matches.
	 * @param matchHistorySize The number of matching key parts to keep for each remote host.
	 *                         Out of this matching list, candidate keys will be generated, so
	 *                         the larger this buffer, the more key parts will go into the keys.
	 * @param maxRemoteMatchListAge The maximum age for which to keep a list of matching key 
	 *                              parts for a remote host, in milliseconds since "the epoch".
	 *                              This is used to keep the number of match histories finite,
	 *                              by pruning lists that have not been updated since this time.
	 * @param instanceId This parameter may be used to distinguish different instances of
	 *                   this class running on the same machine. It will be used in logging
	 *                   and error messages. May be set to null.
	 * @param useJSSE If set to true, the JSSE API with the default JCE provider of the JVM will be used
	 *                for cryptographic operations. If set to false, an internal copy of the Bouncycastle
	 *                Lightweight API classes will be used.
	 */
	public CandidateKeyProtocol(int candidateHistorySize, int matchHistorySize, 
			int maxRemoteMatchListAge, String instanceId, boolean useJSSE) {
		this.remoteIdentifier = instanceId;
		this.maxRemoteMatchListAge = maxRemoteMatchListAge;
		this.useJSSE = useJSSE;
		
		this.recentKeyParts = new CandidateKeyPart[candidateHistorySize];
		this.recentKeyPartsIndex = 0;
		this.matchHistorySize = matchHistorySize;
		this.matchingKeyParts = new HashMap();
		logger.info("Candidate key part protocol with " + recentKeyParts.length + 
				" key parts in history and a window of " + this.matchHistorySize +
				" matching key parts and maximum match list age of " + 
				this.maxRemoteMatchListAge + " ms created" + 
				(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
	}
	
	/** Generate a list of candidate key parts out of key parts. This also stores the
	 * list in recentKeyParts.
	 * @param candidateKeys The list of candidate key parts for the current round.
	 * @param entropy The estimated entropy of this set of candidates.
	 * @return A list of candidate key parts that should be sent to the remote host
	 *         (or group). It will have the same number of elements as the input
	 *         list of key parts. 
	 * @throws InternalApplicationException 
	 * @see #recentKeyParts
	 */
	public synchronized CandidateKeyPartIdentifier[] generateCandidates(byte[][] candidateKeys, float entropy) 
			throws InternalApplicationException {
		if (candidateKeys == null)
			throw new IllegalArgumentException("candidateKeys can not be null" +
					(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
		if (candidateKeys.length > recentKeyParts.length)
			throw new IllegalArgumentException("Length of new key set is larger than the history size" +
					(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
		if (candidateKeys.length > 127)
			throw new IllegalArgumentException("Maximum of 127 key parts supported for each round" +
					(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
		
		CandidateKeyPartIdentifier[] ret = new CandidateKeyPartIdentifier[candidateKeys.length];
		lastRound++;
		logger.debug("Adding " + candidateKeys.length + " candidates to local history, assigning round " +
				lastRound + 
				(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));

		int candidateKeyPartsLength = -1;
		for (int i=0; i<candidateKeys.length; i++) {
			if (candidateKeys[i] == null) {
				logger.warn("Candidate with index " + i + " is null, ignoring" +
						(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
				continue;
			}
			
			// sanity check - all candidate key parts from the same set must have the same length
			if (candidateKeyPartsLength != -1 && candidateKeyPartsLength != candidateKeys[i].length) 
				throw new IllegalArgumentException("Candidate with index " + i + " has different length from first valid " +
						"candidate, is " + candidateKeys[i].length + " but expected " + candidateKeyPartsLength +
						(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
			candidateKeyPartsLength = candidateKeys[i].length;
			
			// first add to the history
			CandidateKeyPart p = new CandidateKeyPart(candidateKeys[i], lastRound, (byte) i, entropy);
			recentKeyParts[recentKeyPartsIndex++] = p;
			if (recentKeyPartsIndex == recentKeyParts.length) {
				statisticsLogger.debug("o recentKeyPartsIndex overflow (" + recentKeyParts.length + ") while adding " + 
						candidateKeys.length + " candidate key parts; lastRound=" + lastRound);
				recentKeyPartsIndex = 0;
			}
			// and generate the candidate identifier to send to the remote host
			ret[i] = p.extractPublicIdentifier();
			logger.debug("Generating local candidate identifier number " + p.candidateNumber +
					(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
		}
		
		return ret;
	}
	
	/** Match an incoming list of candidate key part identifiers with the internal
	 * history and report and remember the candidate the matches. 
	 * @param remoteHost An identifier for the remote host that sent the candidate
	 *                   key part identifierts. The same identifier (i.e. equal in the sense
	 *                   of Object.equal) must be passed to subsequent calls to this
	 *                   and other methods when dealing with the same remote host. It 
	 *                   can e.g. be an InetAddress object, or an Integer object, 
	 *                   defining the remote host ID. 
	 * @param candidateIdentifiers The incoming identifiers to match against the own
	 *                             history.
	 * @return The index in candidateIdentifiers that points to the matching identifier,
	 *         or -1 if no match has been found. The tuple of round and candidate number
	 *         contained in the matching candidate identifier may be sent to the remote
	 *         host (or group), but does not have to. This depends on the application.
	 */
	public synchronized int matchCandidates(Object remoteHost, CandidateKeyPartIdentifier[] candidateIdentifiers) 
			throws InternalApplicationException {
		if (candidateIdentifiers == null)
			throw new IllegalArgumentException("candidateIdentifiers can not be null" +
					(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
		if (candidateIdentifiers.length > recentKeyParts.length)
			logger.warn("Length of incoming candidate list is larger than the history size" +
					(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
		if (candidateIdentifiers.length > 127)
			throw new IllegalArgumentException("Maximum of 127 key parts supported for each round" +
					(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
		
		int firstMatch = -1, numMatches = 0, numHistoryParts = 0;
		// check against the whole history
		for (int j=0; j<recentKeyParts.length; j++) {
			if (recentKeyParts[j] != null) {
				numHistoryParts++;
				
				for (int i=0; i<candidateIdentifiers.length; i++) {
					if (candidateIdentifiers[i] == null) {
						logger.warn("Candidate with index " + i + " is null, ignoring" +
								(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
						continue;
					}
					int compareBytes = recentKeyParts[j].hash.length;
					if (recentKeyParts[j].hash.length != candidateIdentifiers[i].hash.length) {
						compareBytes = recentKeyParts[j].hash.length < candidateIdentifiers[i].hash.length ?
								recentKeyParts[j].hash.length : candidateIdentifiers[i].hash.length;
						logger.warn("Length of candidate " + i + " does not match expected length, " +
								"comparing only " + compareBytes + " bytes" +
								(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
					}
					boolean match = true;
					for (int k=0; k<compareBytes && match; k++)
						if (recentKeyParts[j].hash[k] != candidateIdentifiers[i].hash[k])
							match = false;
					logger.debug("Incoming candidate of round " + candidateIdentifiers[i].round +
							" with number " + candidateIdentifiers[i].candidateNumber + " " + 
							(match ? "matches" : "does not match") + " local candidate of round " + 
							recentKeyParts[j].round + " with number " + recentKeyParts[j].candidateNumber +
							(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
					
					/* when it matches, add this local candidate to the matches list and report
					 * the remote candidate back to the other host
					 */
					if (match) {
						numMatches++;
						advanceCandidateToMatch(remoteHost, j, candidateIdentifiers[i].round, candidateIdentifiers[i].candidateNumber);
						if (firstMatch == -1) {
							// sanity check
							if (candidateIdentifiers[i].candidateNumber != i)
								logger.warn("Incoming candidate number " + candidateIdentifiers[i].candidateNumber +
										" in round " + candidateIdentifiers[i].round + " does not match its position " +
										"in the array: " + i +
										(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
							firstMatch = candidateIdentifiers[i].candidateNumber;
							logger.debug("This is the first match, will report candidate number " + firstMatch +
									(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
						}
					}
				}
			}
		}

		statisticsLogger.info("m found " + numMatches + " matches out of " + candidateIdentifiers.length +
				" incoming candidates from + " + remoteHost + " and " + numHistoryParts + 
				" parts in the recent history; lastRound=" + lastRound + "; numMatches=" + 
				((MatchingKeyParts) matchingKeyParts.get(remoteHost)).numMatchingRounds);
		if (firstMatch == -1)
			logger.info("No match found, not reporting to remote host" +
					(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
		return firstMatch;
	}
	
	/** This function just adds the local candidate key part to the match list
	 * that has been reported as match by the remote host.
	 * @param remoteHost An identifier for the remote host that sent the match
	 *                   message. Refer to @see #matchCandidates for more details.
	 * @param round The local round number in which this match occured, as received from
	 *              the remote host.
	 * @param candidateNumber The local counter identifiying the matching key parts, as
	 *                        received from the remote host.
	 * @throws InternalApplicationException 
	 */
	public synchronized void acknowledgeMatches(Object remoteHost, int round, int candidateNumber) throws InternalApplicationException {
		// need to find the local index in the recent history with that round and number
		boolean found=false;
		for (int i=0; i<recentKeyParts.length && !found; i++)
			if (recentKeyParts[i] != null && recentKeyParts[i].round == round && 
					recentKeyParts[i].candidateNumber == candidateNumber) {
				found = true;
				advanceCandidateToMatch(remoteHost, i, -1, -1);
			}
		if (!found)
			logger.warn("Local candidate number of round " + round + " with number " + candidateNumber + 
					" could not be found in recent parts list, probably outdated" +
					(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
	}
	
	/** This is only a small helper function to copy a CandidateKeyPart from
	 * the recent candidate history to the matching key parts list. If no such
	 * list exists for the specified remote host, it will be created beforehand. 
	 * Before inserting new matches into the remote-specific match list, aged lists
	 * are pruned.
	 * This helper also makes sure that no single candidate (identified by round
	 * and number) is added twice. 
	 * @param remoteHost An identifier for the remote host. Refer to 
	 *                   @see #matchCandidates for more details.
	 * @param candidateIndex The index of the candidate in recentKeyParts.
	 * @param remoteReportedRound The round number that the remote host reported in
	 *                            referring to this match. Set to -1 if not known.
	 * @param remoteReportedCandidateNumber The candidate number that the remote host reported in
	 *                                      referring to this match. Set to -1 if not known.
	 * @throws InternalApplicationException 
	 */
	private synchronized void advanceCandidateToMatch(Object remoteHost, int candidateIndex, 
			int remoteReportedRound, int remoteReportedCandidateNumber) throws InternalApplicationException {
		MatchingKeyParts matchList = null;
		if (matchingKeyParts.containsKey(remoteHost))
			matchList = (MatchingKeyParts) matchingKeyParts.get(remoteHost);
		else {
			logger.debug("Creating new match list for remote host " + remoteHost +
					(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
			matchList = new MatchingKeyParts();
			matchingKeyParts.put(remoteHost, matchList);
			statisticsLogger.debug("+ Creating new match list for " + remoteHost + ", now " + 
					matchingKeyParts.size() + " lists; lastRound=" + lastRound);
		}
		long curTime = System.currentTimeMillis();
		matchList.lastUpdate = curTime;

		// TODO: (simple to do) move this check into a background thread
		// before inserting something new, prune matching lists that are too old
		for (Iterator allRemoteHosts = matchingKeyParts.keySet().iterator();
				allRemoteHosts.hasNext(); ) {
			Object checkHost = allRemoteHosts.next();
			long lastUpdate = ((MatchingKeyParts) matchingKeyParts.get(checkHost)).lastUpdate; 
			if (lastUpdate + maxRemoteMatchListAge < curTime) {
				logger.debug("Pruning match list for remote host " + checkHost + 
						", its last update was " + lastUpdate +
						(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
				// TODO: generate timeout events so that higher levels can react (e.g. with failure events and protocol abort)
				if (! wipe(checkHost)) 
					logger.error("Could not purge match list for remote host " + checkHost + 
							". This should not happen." +
							(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
				statisticsLogger.debug("- Removing old match list for " + checkHost + ", now " + 
						matchingKeyParts.size() + " lists; lastRound=" + lastRound);
			}
		}
		
		// check if it has already been inserted
		boolean found = false;
		for (int i=0; i<matchList.parts.length && !found; i++) {
			if (matchList.parts[i] != null && 
					matchList.parts[i].round == recentKeyParts[candidateIndex].round &&
					matchList.parts[i].candidateNumber == recentKeyParts[candidateIndex].candidateNumber)
				found = true;
		}
		
		if (!found) {
			// also mark this round as an additional match (if this round number was not yet in the match list for this host)
			boolean roundAlreadyMatched = false;
			for (int i=0; i<matchList.parts.length; i++)
				if (matchList.parts[i] != null && matchList.parts[i].round == recentKeyParts[candidateIndex].round)
					roundAlreadyMatched = true;
			if (! roundAlreadyMatched)
				increaseNumMatchingRounds(remoteHost);

			matchList.parts[matchList.index++] = new MatchingKeyPart(recentKeyParts[candidateIndex], 
					remoteReportedRound, remoteReportedCandidateNumber);
			logger.debug("Advancing local candidate of round " + recentKeyParts[candidateIndex].round +
					" with number " + recentKeyParts[candidateIndex].candidateNumber + 
					" (remote uses round " + remoteReportedRound + " with number " + remoteReportedCandidateNumber + 
					") to matching status" +
					" (match list index is now " + matchList.index + ")" +
					(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
			if (matchList.index == matchList.parts.length) {
				matchList.index = 0;
				statisticsLogger.debug("o matchList index overflow for " + remoteHost + " (" + 
						matchList.parts.length + "); lastRound=" + lastRound);
			}
			statisticsLogger.debug("= now " + matchList.numMatchingRounds + " matches for " + remoteHost + 
					" since round " + matchList.firstLocalRoundNumber + " (max list size " + matchList.parts.length + 
					"); lastRound=" + lastRound);
		}
		else
			logger.debug("Local candidate of round " + recentKeyParts[candidateIndex].round +
					" with number " + recentKeyParts[candidateIndex].candidateNumber + 
					" already marked as match, skipping to add it" +
					(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
	}
	
	/** This small helper function just increases the number of matching rounds for
	 * a remote host, i.e. @see MatchingKeyParts#numMatchingRounds. However, it does
	 * some sanity checks and makes sure that other conditions are met. This method
	 * should be called for the first match in each round, after calling 
	 * @see #advanceCandidateToMatch.
	 * @throws InternalApplicationException 
	 */
	private synchronized void increaseNumMatchingRounds(Object remoteHost) throws InternalApplicationException {
		MatchingKeyParts matchList = (MatchingKeyParts) matchingKeyParts.get(remoteHost);
		// sanity check
		if (matchList == null)
			throw new InternalApplicationException("Just advanced a candidate to a matching key part, but no match known for remote host " +
					remoteHost + ". This should not happen!" +
					(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
		matchList.numMatchingRounds++;
		logger.debug("Remote host " + remoteHost + " now has " + matchList.numMatchingRounds + 
				" rounds with matches out of " + (lastRound-matchList.firstLocalRoundNumber) +
				(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
		if (lastRound-matchList.firstLocalRoundNumber < matchList.numMatchingRounds) {
			logger.info("More matching rounds (" + matchList.numMatchingRounds + ") than total rounds (" +
					(lastRound-matchList.firstLocalRoundNumber) + "), correcting first round number for remote host " +
					remoteHost + " to " + (lastRound-matchList.numMatchingRounds) +
					(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
			matchList.firstLocalRoundNumber = lastRound-matchList.numMatchingRounds;
		}
	}
	
	/** Returns the number of rounds that have passed during the authentication with
	 * the specified remote host.
	 * @param remoteHost An identifier for the remote host. Refer to 
	 *                   @see #matchCandidates for more details.
	 * @return The number of rounds that have passed with this remote host.
	 * @throws InternalApplicationException 
	 */
	public synchronized int getNumLocalRounds(Object remoteHost) throws InternalApplicationException {
		if (! matchingKeyParts.containsKey(remoteHost)) {
			logger.warn("getNumLocalRounds called for a remote host where no match list has yet been created or it has already been pruned, returning 0" +
					(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
			return 0;
		}
		MatchingKeyParts matchList = (MatchingKeyParts) matchingKeyParts.get(remoteHost);
		// sanity check
		if (lastRound <= matchList.firstLocalRoundNumber)
			throw new InternalApplicationException("lastRound <= first round with remote host " + remoteHost + ". Overflow?" +
					(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
		return lastRound - matchList.firstLocalRoundNumber;
	}
	
	/** Returns the number of entries in the matches list. 
	 * @param remoteHost An identifier for the remote host. Refer to 
	 *                   @see #matchCandidates for more details.
	 * @return The number of matching key parts currently available in the matching
	 *         list for the specified remote host.
	 */
	public synchronized int getNumTotalMatches(Object remoteHost) {
		if (! matchingKeyParts.containsKey(remoteHost)) {
			logger.warn("getNumTotalMatches called for a remote host where no match list has yet been created or it has already been pruned, returning 0" +
					(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
			return 0;
		}
		MatchingKeyParts matchList = (MatchingKeyParts) matchingKeyParts.get(remoteHost);
		
		int numMatches = 0;
		while (numMatches < matchList.parts.length && matchList.parts[numMatches] != null)
			numMatches++;
		return numMatches;
	}
	
	/** Returns the fraction of (local) rounds where at least one matching key 
	 * part has been found for the specified remote host. A local round is defined
	 * by a call to @see #generateCandidates.
	 * @param remoteHost An identifier for the remote host. Refer to 
	 *                   @see #matchCandidates for more details.
	 * @return The fraction of matching rounds with this remote host. By definition
	 *         between 0 and 1 (inclusive).
	 * @throws InternalApplicationException 
	 */
	public synchronized float getMatchingRoundsFraction(Object remoteHost) throws InternalApplicationException {
		if (! matchingKeyParts.containsKey(remoteHost)) {
			logger.warn("getMatchingRoundsFraction called for a remote host where no match list has yet been created or it has already been pruned, returning 0" +
					(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
			return 0;
		}
		MatchingKeyParts matchList = (MatchingKeyParts) matchingKeyParts.get(remoteHost);
		
		// sanity check
		if (lastRound <= matchList.firstLocalRoundNumber)
			throw new InternalApplicationException("lastRound <= first round with remote host " + remoteHost + ". Overflow?" +
					(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
		
		float ret = ((float) matchList.numMatchingRounds) / (lastRound - matchList.firstLocalRoundNumber);
		if (ret > 1) {
			logger.warn("Computed a matching rounds fraction > 1 - this indicates a strange order " +
					"of local and remote message generation and should not happen in practice! " +
					"Check the higher level protocol implementation!");
			ret=1;
		}
		// sanity check
		if (ret < 0)
			throw new InternalApplicationException("Computed negative fraction with remote host " + remoteHost + ". Overflow?" +
					(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));

			return ret;
	}
	
	/** Returns the sum of all entropy values for matching key parts. 
	 * @param remoteHost An identifier for the remote host. Refer to 
	 *                   @see #matchCandidates for more details.
	 * @return The entropy of all matching key parts currently available in the matching
	 *         list for the specified remote host.
	 */
	public synchronized float getSumMatchEntropy(Object remoteHost) {
		if (! matchingKeyParts.containsKey(remoteHost)) {
			logger.warn("getSumMatchEntropy called for a remote host where no match list has yet been created or it has already been pruned, returning 0" +
					(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
			return 0;
		}
		MatchingKeyParts matchList = (MatchingKeyParts) matchingKeyParts.get(remoteHost);

		float sum = 0;
		for (int i=0; i<matchList.parts.length; i++)
			if (matchList.parts[i] != null)
				sum += matchList.parts[i].entropy;
		return sum;
	}
	
	/** Generates a candidate key out of all parts currently in the matches list,
	 * sorted by the round number. If multiple candidates happen to be in the match
	 * list for the same round number, only the first one is taken for this round. 
	 * This should only happen in a symmetric setting where both hosts report 
	 * matches, which can lead to double insertion due to differences in timing.
	 * @param remoteHost An identifier for the remote host. Refer to 
	 *                   @see #matchCandidates for more details.
	 * @return The candidate for which the hash and numParts should be broadcast.
	 *         Returns null if no match list has yet been created for the specified
	 *         remoteHost, either because not matches have yet been received with 
	 *         this host or because it has been pruned due to aging.
	 * @throws InternalApplicationException 
	 */
	public synchronized CandidateKey generateKey(Object remoteHost) throws InternalApplicationException {
		if (! matchingKeyParts.containsKey(remoteHost)) {
			logger.warn("generateKey called for a remote host where no match list has yet been created or it has already been pruned, returning null" +
					(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
			return null;
		}

		// assemble the key for all available parts
		Object[] keyRet = assembleKeyFromMatches(remoteHost, -1, false);
		byte[] keyParts = ((byte[][]) keyRet[0])[0];
		int numCopied = ((Integer) keyRet[1]).intValue();
		statisticsLogger.info("g generated key for " + remoteHost + " out of " + numCopied + 
				" matching parts; lastRound=" + lastRound + "; numMatches=" + 
				((MatchingKeyParts) matchingKeyParts.get(remoteHost)).numMatchingRounds);
		return generateKey(keyParts, numCopied);
	}
	
	/** Tries to generate a key that produces the same hash from the
	 * list of matching key parts. To search for the possible key, it uses
	 * a sliding window of numParts over the local list of matching key parts
	 * and, if multiple candidates match for the same round, tries all possible
	 * combinations of these candidates. This can be an expensive operation.
	 * @param remoteHost An identifier for the remote host. Refer to 
	 *                   @see #matchCandidates for more details.
	 * @param hash The hash received from the remote host.
	 * @param numParts The number of key parts that the key is composed of, as
	 *                 reported by the remote host. <b>Note:</b> This parameter is
	 *                 not strictly necessary and could be omitted for slightly
	 *                 better security (i.e. less information to an eavesdropper).
	 *                 It is used for better performance in searching for a matching
	 *                 key.
	 * @return The key matching the hash, or null when same hash could not be
	 *         generated a combination of parts in matchingKeyParts 
	 *         Returns null if no match list has yet been created for the specified
	 *         remoteHost, either because not matches have yet been received with 
	 *         this host or because it has been pruned due to aging.
	 * @throws InternalApplicationException */
	public synchronized CandidateKey searchKey(Object remoteHost, byte[] hash, int numParts) throws InternalApplicationException {
		if (hash == null)
			throw new IllegalArgumentException("hash must be set");
		if (! matchingKeyParts.containsKey(remoteHost)) {
			logger.warn("searchKey called for a remote host where no match list has yet been created or it has already been pruned, returning null" + 
					(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
			return null;
		}
		MatchingKeyParts matchList = (MatchingKeyParts) matchingKeyParts.get(remoteHost);
		if (numParts > matchList.parts.length) {
			logger.error("Received candidate key has been created of more key parts than " + 
					"there are in the local list of matching key parts. Can not possibly find " +
					"a matching key. Giving up." +
					(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
			return null;
		}

		logger.debug("Trying to create key for remote '" + remoteHost + "' with hash " + 
				new String(Hex.encodeHex(hash)) + " from " + numParts + " parts" +
				(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
		
		Object[] keyRet = assembleKeyFromMatches(remoteHost, numParts, true);
		if (keyRet == null) {
			logger.debug("Could not generate key candidates with " + numParts + " parts" +
					(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
			return null;
		}
			
		byte[][] keyParts = (byte[][]) keyRet[0];
		int numCopied = ((Integer) keyRet[1]).intValue();
		// sanity check
		if (numCopied != numParts) 
			throw new InternalApplicationException("Did not get as many parts as requestes. This should not happen" + 
					(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
		statisticsLogger.info("g* generated " + keyParts.length + " candiates keys for " + remoteHost + 
				" out of " + numCopied + " matching parts; lastRound=" + lastRound + "; numMatches=" + 
				((MatchingKeyParts) matchingKeyParts.get(remoteHost)).numMatchingRounds);

		logger.debug("Comparing " + keyParts.length + " candidate keys" +
				(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
		// and compare the target hash with hashes over all candidate keys
		for (int i=0; i<keyParts.length; i++) {
			byte[] candidateHash = Hash.doubleSHA256(keyParts[i], useJSSE);
			logger.debug("Checking candidate number " + i + ": hash " + new String(Hex.encodeHex(candidateHash)) +
				(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
			boolean match = true;
			for (int j=0; j<candidateHash.length && j<hash.length && match; j++)
				if (candidateHash[j] != hash[j])
					match = false;
			if (match) {
				logger.info("Could generate key with same hash" +
						(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
				return generateKey(keyParts[i], numParts);
			}
		}
		
		logger.info("Could not generate key with same hash" +
				(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
		return null;
	}

	// TODO: implement me
	public synchronized CandidateKey searchKey(Object remoteHost, byte[] hash, int [][] localIndices, int[][] remoteIndices) throws InternalApplicationException {
		return null;
	}
	
	/** Wipes all state that is held with respect to a remote host. This method should
	 * be called when the whole authentication fails as well as when it suceeds. By
	 * overwriting key material before freeing the memory, it makes sure that key material
	 * will not be kept in memory when it is no longer necessary.
	 * @param remoteHost An identifier for the remote host. Refer to 
	 *                   @see #matchCandidates for more details.
	 * @return true if state was kept for this remote host, false if there was not state
	 *         to wipe.
	 */
	public synchronized boolean wipe(Object remoteHost) {
		if (matchingKeyParts.containsKey(remoteHost)) {
			logger.debug("Wiping key material for remote host " + remoteHost + 
					(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
			MatchingKeyParts matchList = (MatchingKeyParts) matchingKeyParts.remove(remoteHost);
			// not only remove from list but really wipe
			int numMatches=0;
			for (int i=0; i<matchList.parts.length; i++) {
				if (matchList.parts[i] != null) {
					numMatches++;
					for (int j=0; j<matchList.parts[i].keyPart.length; j++)
						matchList.parts[i].keyPart[j] = 0;
					for (int j=0; j<matchList.parts[i].hash.length; j++)
						matchList.parts[i].hash[j] = 0;
					matchList.parts[i] = null;
				}
			}
			matchList.parts = null;
			matchList = null;
			statisticsLogger.debug("- wiping match list for " + remoteHost + ", contained " +
					numMatches + " matches; lastRound=" + lastRound);
			
			// and call the garbage collector
			System.gc();
			return true;
		}
		else
			return false;
	}
	
	/** Wipes all entries from the set of matching keys, i.e. calls wipe for
	 * all remote hosts where there have been some matches.
	 * @see #matchingKeyParts
	 * @see #wipe 
	 */
	public synchronized void wipeAll() {
		Iterator iter = matchingKeyParts.values().iterator();
		while (iter.hasNext())
			wipe(iter.next());
		// sanity check
		if (matchingKeyParts.size() > 0)
			logger.error("Wiping all matching key parts failed");
		
		// and call the garbage collector
		System.gc();
	}
	
	/** This is a helper function used by generateKey and searchKey to assemble
	 * key parts from the matching list. If numParts is -1, it is ignored. Otherwise,
	 * this method will try to collect that many unique rounds and return null if not
	 * successful. On success, it returns an array of assembled plain text keys (only
	 * one element if extractAllCombinations is set to false), and an Integer specifying how
	 * many parts have been copied (guaranteed to be equal to numParts if not -1).
	 * @throws InternalApplicationException 
	 */
	private synchronized Object[] assembleKeyFromMatches(Object remoteHost, int numParts, boolean extractAllCombinations) throws InternalApplicationException {
		if (! matchingKeyParts.containsKey(remoteHost))
			throw new IllegalArgumentException("Called for a remote host where no match list has yet been created or it has already been pruned, this should not happen!" + 
					(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
		logger.debug("assembleKeyFromMatches called for remote host " + remoteHost + 
				" for " + numParts + " parts, extractAllCombinations=" + extractAllCombinations + " in thread " + Thread.currentThread() +
				(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
		
		MatchingKeyParts matchList = (MatchingKeyParts) matchingKeyParts.get(remoteHost);

		/* TODO: this is not optimal, maybe use a second list with the remote-reported
		   rounds and candidate numbers to get rid of possible double insertions */
		CandidateKeyPart[] initialCombination = new CandidateKeyPart[matchList.parts.length];
		/* if all combinations should be generated, this holds the indices of all
		   candidates in matchingKeyParts that have _not_ been copied into tmp for each round */
		HashMap duplicateRounds = null;
		if (extractAllCombinations)
			duplicateRounds = new HashMap();
		/* copy all rounds to the temporary array to sort them, but make sure
		   that each round is represented by exactly one candidate */
		int numCopied=0, numMatches=0;
		for (int i=0; i<matchList.parts.length; i++) {
			if (matchList.parts[i] != null) {
				numMatches++;
				boolean alreadyCopied = false;
				/* (Small) Performance optimization: can stop looking after the first match, since round 
				 * numbers in initialCombination  are guaranteed to be uniqeue. 
				 */
				for (int j=0; j<numCopied && !alreadyCopied; j++) {
					if (matchList.parts[i].round == initialCombination[j].round) {
						alreadyCopied = true;
						if (!extractAllCombinations) {
							logger.warn("Round " + initialCombination[j].round + " has two matching candidates: " +
									initialCombination[j].candidateNumber + " and " + 
									matchList.parts[i].candidateNumber + ", skipping latter" +
									(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
						} 
						else {
							// instructed to copy all combinations, so remember duplicates
							Integer round = new Integer(initialCombination[j].round);
							LinkedList alternatives = null;
							if (!duplicateRounds.containsKey(round)) {
								alternatives = new LinkedList();
								duplicateRounds.put(round, alternatives);
							}
							else {
								// already detected another duplicate, so just amend the list
								alternatives = (LinkedList) duplicateRounds.get(round);
							}
							logger.debug("Adding candidate number " + matchList.parts[i].candidateNumber + 
									" as duplicate to round " + round +
									(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
							// only remember the index in matchingKeyParts, that's all we need
							alternatives.add(new Integer(i));
						}
					}
				}
				if (!alreadyCopied) {
					initialCombination[numCopied++] = matchList.parts[i];
				}
			}
		}
		if (numParts > numCopied) {
			String roundNumbers = "";
			for (int j=0; j<numCopied; j++) {
				roundNumbers += initialCombination[j].round;
				roundNumbers += " ";
			}
			logger.info("Could not assemble " + numParts + " key parts, only got " + numCopied +
					" from " + numMatches + " matches in the list, encountered round numbers: " + roundNumbers +
					(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : "") + " in thread " + Thread.currentThread());
			return null;
		}
		
		/* But if we copied more, only use as many as requested - however, this means another 
		 * explosion into the different possibilities of choosing the rounds.
		 */ 
		BitSet[] roundNumbersToUse = null;
		if (numParts != -1 && numCopied > numParts) {
			logger.debug("Collected " + numCopied + " rounds from the match list, but only want " + numParts +
					(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : "") + " in thread " + Thread.currentThread());
			
			if (!extractAllCombinations)
				logger.warn("extractAllCombinations is set to false, but collected more parts than explicitly requested. " +
						"Will skip exploding into different possibilities of choosing rounds and only choose the first rounds.");
			else {
				// really need to explode the possibilities for different round numbers here
				int[] set = new int[numCopied];
				for (int i=0; i<numCopied; i++)
					set[i] = initialCombination[i].round;
				roundNumbersToUse = explodeKOutOfN(set, numParts);
			}
		}
		else {
			// simple case: all collected rounds are used
			roundNumbersToUse = new BitSet[1];
			roundNumbersToUse[0] = new BitSet();
			for (int i=0; i<numCopied; i++)
				roundNumbersToUse[0].set(initialCombination[i].round);
			numParts = numCopied;
		}
		// from here on, numParts will represent the number we should generate, and numCopied the number we collected

		logger.info("Generating candidate key(s) from " + numParts + " matching key parts" +
				(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : "") + " in thread + " + Thread.currentThread());
		// sort by round number, ascending
		Arrays.sort(initialCombination, 0, numParts);
		// and find out how long the resulting key(s) will be (all _must_ have exactly the same length)
		int keyPartsLength=0;
		for (int i=0; i<numParts; i++)
			keyPartsLength += initialCombination[i].keyPart.length;
		
		CandidateKeyPart[][] allCombinations = null;
		// and now (on the sorted array because it can be faster), explode combinations
		if (extractAllCombinations) {
			LinkedList allTmpCombinations = new LinkedList();
			int numAllCombinations = 0;
			for (int ri=0; ri<roundNumbersToUse.length; ri++) {
				logger.debug("Generating all candidates for rounds combination " + ri +
						(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
				
				int numCombinations = 1;
				Object[] roundsWithDuplicates = duplicateRounds.keySet().toArray();
				logger.debug("Found " + roundsWithDuplicates.length + " rounds with multiple candidates" +
						(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
				for (int i=0; i<roundsWithDuplicates.length; i++) {
					LinkedList alternativeIndices = (LinkedList) duplicateRounds.get(roundsWithDuplicates[i]);
					/* (Large) Performance optimization: only use those rounds that are actually used for
					 * creating the key later on, because it might save tremendously on the "explosion".
					 */
					if (roundNumbersToUse[ri].get(((Integer) roundsWithDuplicates[i]).intValue())) { 
						logger.debug("Round " + roundsWithDuplicates[i] + " has " + alternativeIndices.size() + 
								" alternatives to its first match" + 
								(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
						numCombinations *= (alternativeIndices.size()+1);
					}
					else
						logger.debug("Ignoring round " + roundsWithDuplicates[i] + " duplicates" +
								(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
				}
				logger.debug("Exploding into " + numCombinations + " different candidate combinations for this set of rounds" +
						(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
			
				CandidateKeyPart[][] tmpCombinations = new CandidateKeyPart[numCombinations][];
				// seed with initial candidate
				tmpCombinations[0] = new CandidateKeyPart[numParts];
				// take exactly those rounds that we currently use in this iteration
				int outI=0;
				for (int i=0; i<numCopied; i++)
					if (roundNumbersToUse[ri].get(initialCombination[i].round))
						tmpCombinations[0][outI++] = initialCombination[i];
				for (int j=1; j<numCombinations; j++) {
					tmpCombinations[j] = new CandidateKeyPart[numParts];
				}
				// and copy, changing the candidates
				int spacing=1;
				for (int i=0; i<numParts; i++) {
					Integer round = new Integer(tmpCombinations[0][i].round);
					if (! duplicateRounds.containsKey(round)) {
						// simple, just copy
						logger.debug("Round " + round + " does not have multiple candidates" +
								(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
						for (int j=1; j<numCombinations; j++)
							tmpCombinations[j][i] = tmpCombinations[0][i];
					}
					else {
						LinkedList alternativeIndices = (LinkedList) duplicateRounds.get(round);
						// first collect all the candidate key parts for this round, including the initial alternative
						CandidateKeyPart[] alternatives = new CandidateKeyPart[alternativeIndices.size()+1];
						alternatives[0] = tmpCombinations[0][i];
						for (int k=1; k<alternatives.length; k++)
							alternatives[k] = matchList.parts[((Integer) alternativeIndices.get(k-1)).intValue()];
						logger.debug("Round " + round + " has " + alternatives.length + " candidates" +
								(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
						/* This looks a bit tricky, but really isn't. If e.g. the numbers of alternatives for
						 * 5 different rounds a, b, c, d, and e are 1, 2, 1, 3, and 2, respectively, it will
						 * produce the following pattern:
						 * Round        0  1  2  3  4
						 * Candidate    a  b1 c  d1 e1
						 *              a  b2 c  d1 e1
						 *              a  b1 c  d2 e1
						 *              a  b2 c  d2 e1
						 *              a  b1 c  d3 e1
						 *              a  b2 c  d3 e1
						 *              a  b1 c  d1 e2
						 *              a  b2 c  d1 e2
						 *              a  b1 c  d2 e2
						 *              a  b2 c  d2 e2
						 *              a  b1 c  d3 e2
						 *              a  b2 c  d3 e2
						 */ 
						for (int j=0; j<numCombinations; j+=alternatives.length*spacing) {
							for (int k=0; k<alternatives.length; k++) {
								for (int l=0; l<spacing; l++) {
									tmpCombinations[j+k*spacing+l][i] = alternatives[k];
								}
							}
						}
						// next column will have larger spacing
						spacing *= alternatives.length;
					}
				}
				// and remember this combination
				allTmpCombinations.add(tmpCombinations);
				numAllCombinations += numCombinations;

				// only for debugging purposes
				if (logger.isDebugEnabled()) {
					String roundNumbers = "";
					for (int j=0; j<numParts; j++) {
						roundNumbers += initialCombination[j].round;
						roundNumbers += " ";
					}
					logger.debug("Following candidate keys have been assembled (candidate numbers for rounds " +
							roundNumbers + "):" +
							(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
					for (int j=0; j<numCombinations; j++) {
						String candidateNumbers = "";
						for (int i=0; i<numParts; i++)
							candidateNumbers += tmpCombinations[j][i].candidateNumber + " ";
						logger.debug("    " + candidateNumbers +
								(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
					}
				}
			}
			// need to convert to a nice array now...
			logger.debug("All combinations (explosions into outer explosion) yielded " + numAllCombinations + " candidates" +
					(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
			allCombinations = new CandidateKeyPart[numAllCombinations][];
			int i=0;
			for (Iterator iter=allTmpCombinations.iterator(); iter.hasNext();) {
				CandidateKeyPart[][] tmpCombinations = (CandidateKeyPart[][]) iter.next();
				System.arraycopy(tmpCombinations, 0, allCombinations, i, tmpCombinations.length);
				i+=tmpCombinations.length;
			}
			// sanity check
			if (i != numAllCombinations)
				throw new InternalApplicationException("Did not copy the same number of combinations as expected. This should not happen!");
		}
		else {
			// just use the first possible candidate in each round, i.e. the one already collected
			if (logger.isDebugEnabled()) {
				String roundNumbers = "";
				for (int j=0; j<numParts; j++) {
					roundNumbers += initialCombination[j].round;
					roundNumbers += " ";
				}
				logger.debug("Using only first matches in each of the rounds " + roundNumbers + "to create a single candidate key" +
						(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
			}
			allCombinations = new CandidateKeyPart[1][];
			allCombinations[0] = new CandidateKeyPart[numParts];
			System.arraycopy(initialCombination, 0, allCombinations[0], 0, numParts);
		}
		
		// this is the concatenated "plain text", which does not have key-quality attributes
		byte[][] keyParts = new byte[allCombinations.length][];
		for (int i=0; i<allCombinations.length; i++) {
			// all keys must have the same length, because the different combinations stem from the same set
			//logger.debug("  Assembling combination " + i + " (length " + keyPartsLength + " bytes)");
			keyParts[i] = new byte[keyPartsLength];
			int outPos=0;
			for (int j=0; j<numParts; j++) {
				logger.trace("   Part " + i + ": copying " + allCombinations[i][j].keyPart.length + " bytes to offset " + outPos);
				System.arraycopy(allCombinations[i][j].keyPart, 0, keyParts[i], outPos, 
						allCombinations[i][j].keyPart.length);
				outPos += allCombinations[i][j].keyPart.length;
			}
			logger.debug("Concatenated " + allCombinations[i].length + " key parts to candidate key " + i + ": " + new String(Hex.encodeHex(keyParts[i])) +
					(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
		}
		// I hate Java
		return new Object[] {keyParts, new Integer(numParts) };
	}
	
	/** Just a small helper function to compute the factorial. */
	private static long fact(int n) {
		long f = 1;
		for (int i=2; i<=n; i++)
			f *= i;
		return f;
	}
	
	// TODO: this should actually be split into a helper class
	/** Returns an array of bit sets, where each of the bit sets represents one combination
	 * of choosing k values from the set.
	 * This is only public so that JUnit tests can cover it. 
	 * @param set The set of numbers to choose from.
	 * @param k How many to choose from the set. This must be <= set.length
	 * @return  The number of numParts (k) out of totalParts (set.length) is 
	 *          (numTotal over numParts) = (n over k) = (n!) / (k! * (n-k)!)
	 * @throws InternalApplicationException 
	 */
	public static BitSet[] explodeKOutOfN(int[] set, int k) throws InternalApplicationException {
		// just some sanity checks
		if (set == null)
			throw new IllegalArgumentException("Need a set of elements to choose from");
		if (set.length < 1)
			throw new IllegalArgumentException("Need to have at least one element in the set to choose from");
		if (k > set.length)
			throw new IllegalArgumentException("Can not choose " + k + " elements from a set of length " + set.length);
		if (k < 1)
			throw new IllegalArgumentException("Need to choose at least one element");
		
		int numSetCombinations = (int) (fact(set.length) / (fact(k) * fact(set.length - k)));
		logger.trace("(" + set.length + " over " + k + ") = " + numSetCombinations);
		// sanity check
		if (numSetCombinations < 1)
			throw new InternalApplicationException("Would pre-explode into " + numSetCombinations + 
					". This is invalid, need at least 1 combination at this point. This should not happen.");
		logger.debug("Pre-exploding into " + numSetCombinations + " different combinations of rounds");
		BitSet[] combinations = new BitSet[numSetCombinations];
		
		/* This is a classical backtracking algorithm. */
		int[] indices = new int[k];	// keeps the current indices into the set (unique, sorted)
		int level=0,				 	// the index which is currently modified in indices (i.e. a meta-index), 0<=level<k
			combinationNumber=0;		// the index into combinations, i.e. where to write the next combination to
		indices[0] = -1;				// initializazion: start "outside" normal boundaries
		// as long as it's still possible to create a new combination with this start...
		while (indices[0] <= set.length-k) {
			indices[level]++;
			logger.trace("Now at level " + level + ", index is " + indices[level]);
			if (level < k-1) {
				// is it possible to go forward?
				if (indices[level] < set.length-1) {
					logger.trace("Not last level, forward track");
					// yes, forward track
					indices[level+1] = indices[level];
					level++;
				}
				else {
					logger.trace("Not last level, back track");
					// no, need to back track even further
					level--;
				}
			}
			else {
				// still a valid combination?
				if (indices[level] < set.length) {
					logger.trace("Last level, storing indices/values at output position " +
							combinationNumber + " (out of " + combinations.length + ")");
					// yes, last level, remember current combination
					combinations[combinationNumber] = new BitSet();
					for (int i=0; i<k; i++) {
						combinations[combinationNumber].set(set[indices[i]]);
						logger.trace(indices[i] + "/" + set[indices[i]] + " ");
					}
					combinationNumber++;
				}
				else {
					logger.trace("Last level, invalid index, back track");
					// no longer valid, back track
					level--;
				}
			}
		}
		
		// sanity check
		if (combinationNumber != numSetCombinations)
			throw new InternalApplicationException("Aiee, got backtracking wrong (" + combinationNumber + 
					" instead of " + numSetCombinations + "). This should not happen!");
		
		return combinations;
	}
	
	/** Another small helper function that creates a proper CandidateKey object
	 * by calculating the two hashes.
	 */
	private CandidateKey generateKey(byte[] keyParts, int numParts) throws InternalApplicationException {
		/* do two hashes over it, one for comparing, the other for generating the
		 * actual shared key for subsequent secure channel setup
		 */ 
		CandidateKey ret = new CandidateKey();
		/* only hash the simpler one so that it is quicker (this operation is done more often, also
		 * from searchKey)
		 */
		ret.hash = Hash.doubleSHA256(keyParts, useJSSE);
		// the real shared key is <the key parts> concatenated with the MAGIC_COOKIE
		byte[] cookie = MAGIC_COOKIE.getBytes();
		byte[] keyPartsModified = new byte[keyParts.length + cookie.length];
		System.arraycopy(keyParts, 0, keyPartsModified, 0, keyParts.length);
		System.arraycopy(cookie, 0, keyPartsModified, keyParts.length,
				cookie.length);
		ret.key = Hash.doubleSHA256(keyPartsModified, useJSSE);
		ret.numParts = numParts;
		
		logger.debug("Generated key " + new String(Hex.encodeHex(ret.key)) +
				" with hash " + new String(Hex.encodeHex(ret.hash)) +
				" from " + ret.numParts + " assembled parts " + new String(Hex.encodeHex(keyParts)) +
				(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
		
		return ret;
	}
}
