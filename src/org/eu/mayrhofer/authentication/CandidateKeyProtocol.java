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
 * In each round, the candidate key protocol collects candidate key parts which 
 * will be assembled into a key when both hosts (or all hosts for group 
 * authentication) agree on them. As a means of key exchange, a hash value of 
 * each candidate key part is broadcast, and other hosts wishing to authenticate 
 * with this one can then flag that candidate out of the current list of candidates
 * in this iteration that they also have. A key part can thus go through three phases:
 * 1. Features extracted form sensor data form a candidate key part and get
 *    broadcasts.
 * 2. After received a candidate, it is checked against all local candidates.
 *    When there is a match, this candidate becomes a matching key part and
 *    the candidate's number is signalled to the host that generated it.
 * 3. All matching key parts are then assembled into a sliding candidate
 *    key, which also gets broadcast. When other hosts hold the same key,
 *    they acknowledge it and it can be used for secure communication.
 * 
 * In short, the whole authentication protocol should be used as follows:
 * 
 * 1. Construct the object.
 * 2. For each set of feature vector that belong together, generate a set
 *    of candidate key parts with generateCandidates. These will be kept
 *    in an internal history.
 * 3. Send the candidate key parts to the remote host.
 * 4. Test all received candidate key parts with matchCandidates and send 
 *    a message signalling the matching one to the remote host. A list of
 *    matching key parts will also be kept internally. 
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

	/** This is used for deriving the shared key them from the one used for comparing 
	 * key to ensure that they are different. It's just some random text, the exact value really 
	 * does not matter. */
	private static final String MAGIC_COOKIE = "MAGIC COOKIE FOR SENSOR AUTHENTICAION";
	
	/** This class represents the complete identification information for a key 
	 * part candidate. It should be sent to the remote host(s) after being generated
	 * by generateCandidates. The combination of round and candidateNumber identifies
	 * a candidate key part uniquely within the window of recent key parts.
	 * @author rene
	 *
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
	
	/** This is only a helper class for keeping the internal candidates history and
	 * the list of matching key parts.
	 */
	private static class CandidateKeyPart implements Comparable {
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
		
		CandidateKeyPart(byte[] keyPart, int round, byte candidateNumber, float entropy, 
				boolean useJSSE) throws InternalApplicationException {
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
	 * @see #recentKeyParts.
	 */
	private int recentKeyPartsIndex;
	
	/** This holds all key parts that have been signalled to match by the remote hosts.
	 * It is also a circular buffer, so that a candidate key is always computed over a
	 * sliding window of candidate key parts. */
	private CandidateKeyPart[] matchingKeyParts;
	/** The index where to insert the next matching key part into matchingKeyParts.
	 * @see #matchingKeyParts.
	 */
	private int matchingKeyPartsIndex;

	/** Our system-wide counter. Use to generate counter values for the
	 * candidate key parts.
	 */
	private static int lastRound = 0;
	
	public CandidateKeyProtocol(int candidateHistorySize, int matchHistorySize, 
			String remoteIdentifier, boolean useJSSE) {
		this.remoteIdentifier = remoteIdentifier;
		this.useJSSE = useJSSE;
		
		this.recentKeyParts = new CandidateKeyPart[candidateHistorySize];
		this.recentKeyPartsIndex = 0;
		this.matchingKeyParts = new CandidateKeyPart[matchHistorySize];
		this.matchingKeyPartsIndex = 0;
		logger.info("Candidate key part protocol with " + recentKeyParts.length + 
				" key parts in history and a window of " + matchingKeyParts.length +
				" matching key parts created" + 
				(remoteIdentifier != null ? "[" + remoteIdentifier + "]" : ""));
	}
	
	/** Generate a list of candidate key parts out of key parts. This also stores the
	 * list in recentKeyParts.
	 * @param candidateKeys The list of candidate key parts for the current round.
	 * @param entropy The estimated entropy of this set of candidates.
	 * @return A list of candidate key parts that should be sent to the remote host
	 *         (or group). It will have the same number of elements as the input
	 *         list of key parts. 
	 * @throws InternalApplicationException 
	 * @see #recentKeyParts;
	 */
	public CandidateKeyPartIdentifier[] generateCandidates(byte[][] candidateKeys, float entropy) throws InternalApplicationException {
		if (candidateKeys == null)
			throw new IllegalArgumentException("candidateKeys can not be null");
		if (candidateKeys.length > recentKeyParts.length)
			throw new IllegalArgumentException("Length of new key set is larger than the history size");
		if (candidateKeys.length > 127)
			throw new IllegalArgumentException("Maximum of 127 key parts supported for each round");
		
		CandidateKeyPartIdentifier[] ret = new CandidateKeyPartIdentifier[candidateKeys.length];
		logger.debug("Adding " + candidateKeys.length + " candidates to local history, assigning round " +
				++lastRound + 
				(remoteIdentifier != null ? " [" + remoteIdentifier + "]" : ""));
		
		for (int i=0; i<candidateKeys.length; i++) {
			if (candidateKeys[i] == null) {
				logger.warn("Candidate with index " + i + " is null, ignoring");
				continue;
			}
			// first add to the history
			CandidateKeyPart p = new CandidateKeyPart(candidateKeys[i], lastRound, (byte) i, entropy, useJSSE);
			recentKeyParts[recentKeyPartsIndex++] = p;
			// and generate the candidate identifier to send to the remote host
			ret[i] = p.extractPublicIdentifier();
			logger.debug("Generating local candidate identifier number " + p.candidateNumber);
		}
		
		return ret;
	}
	
	/** Match an incoming list of candidate key part identifiers with the internal
	 * history and report and remember the candidate the matches.
	 * @param candidateIdentifiers The incoming identifiers to match against the own
	 *                             history.
	 * @return The index in candidateIdentifiers that points to the matching identifier,
	 *         or -1 if no match has been found. The tuple of round and candidate number
	 *         contained in the matching candidate identifier may be sent to the remote
	 *         host (or group), but does not have to. This depends on the application.
	 */
	public int matchCandidates(CandidateKeyPartIdentifier[] candidateIdentifiers) {
		if (candidateIdentifiers == null)
			throw new IllegalArgumentException("candidateIdentifiers can not be null");
		if (candidateIdentifiers.length > recentKeyParts.length)
			logger.warn("Length of incoming candidate list is larger than the history size");
		if (candidateIdentifiers.length > 127)
			throw new IllegalArgumentException("Maximum of 127 key parts supported for each round");
		
		for (int i=0; i<candidateIdentifiers.length; i++) {
			if (candidateIdentifiers[i] == null) {
				logger.warn("Candidate with index " + i + " is null, ignoring");
				continue;
			}
			// check against the whole history
			for (int j=0; j<recentKeyParts.length; j++) {
				if (recentKeyParts[j] != null) {
					int compareBytes = recentKeyParts[j].hash.length;
					if (recentKeyParts[j].hash.length != candidateIdentifiers[i].hash.length) {
						compareBytes = recentKeyParts[j].hash.length < candidateIdentifiers[i].hash.length ?
								recentKeyParts[j].hash.length : candidateIdentifiers[i].hash.length;
						logger.warn("Length of candidate " + i + " does not match expected length, " +
								"comparing only " + compareBytes + " bytes");
					}
					boolean match = true;
					for (int k=0; k<compareBytes && match; k++)
						if (recentKeyParts[j].hash[k] != candidateIdentifiers[i].hash[k])
							match = false;
					logger.debug("Incoming candidate of round " + candidateIdentifiers[i].round +
							" with number " + candidateIdentifiers[i].candidateNumber + 
							(match ? "matches" : "does not match") + "  local candidate of round " + 
							recentKeyParts[j].round + " with number " + recentKeyParts[j].candidateNumber);
					
					/* when it matches, add this local candidate to the matches list and report
					 * the remote candidate back to the other host
					 */
					if (match) {
						advanceCandidateToMatch(j);
						return i;
					}
				}
			}
		}

		logger.info("No match found, not reporting to remote host");
		return -1;
	}
	
	/** This function just adds the local candidate key part to the match list
	 * that has been reported as match by the remote host.
	 * @param matches The local counter identifiying the matching key parts, as
	 *                received from the remote host.
	 */
	public void acknowledgeMatches(int round, int candidateNumber) {
		// need to find the local index in the recent history with that round and number
		boolean found=false;
		for (int i=0; i<recentKeyParts.length && !found; i++)
			if (recentKeyParts[i] != null && recentKeyParts[i].round == round && 
					recentKeyParts[i].candidateNumber == candidateNumber) {
				advanceCandidateToMatch(i);
				found = true;
			}
		if (!found)
			logger.warn("Local candidate number of round " + round + " with number " + candidateNumber + 
					" could not be found in recent parts list, probably outdated");
	}
	
	/** This is only a small helper function to copy a CandidateKeyPart from
	 * the recent candidate history to the matching key parts list. It makes
	 * sure that no single candidate (identified by round and number) is added twice.
	 * @param candidateIndex The index of the candidate in recentKeyParts.
	 */
	private void advanceCandidateToMatch(int candidateIndex) {
		// check if it has already been inserted
		boolean found = false;
		for (int i=0; i<matchingKeyParts.length && !found; i++) {
			if (matchingKeyParts[i] != null && 
					matchingKeyParts[i].round == recentKeyParts[candidateIndex].round &&
					matchingKeyParts[i].candidateNumber == recentKeyParts[candidateIndex].candidateNumber)
				found = true;
		}
		
		if (!found) {
			matchingKeyParts[matchingKeyPartsIndex++] = recentKeyParts[candidateIndex];
			logger.debug("Advancing local candidate of round " + recentKeyParts[candidateIndex].round +
					" with number " + recentKeyParts[candidateIndex].candidateNumber + " to matching status");
		}
		else 
			logger.debug("Local candidate of round " + recentKeyParts[candidateIndex].round +
					" with number " + recentKeyParts[candidateIndex].candidateNumber + 
					" already marked as match, skipping to add it");
	}
	
	/** Returns the number of entries in the matches list. */
	public int getNumTotalMatches() {
		int numMatches = 0;
		while (numMatches < matchingKeyParts.length && matchingKeyParts[numMatches] != null)
			numMatches++;
		return numMatches+1;
	}
	
	/** Returns the sum of all entropy values for matching key parts. */
	public float getSumMatchEntropy() {
		float sum = 0;
		for (int i=0; i<matchingKeyParts.length; i++)
			if (matchingKeyParts[i] != null)
				sum += matchingKeyParts[i].entropy;
		return sum;
	}
	
	/** Generates a candidate key out of all parts currently in the matches list,
	 * sorted by the round number. If multiple candidates happen to be in the match
	 * list for the same round number, only the first one is taken for this round. 
	 * This should only happen in a symmetric setting where both hosts report 
	 * matches, which can lead to double insertion due to differences in timing.
	 * @return The candidate for which the hash and numParts should be broadcast.
	 * @throws InternalApplicationException 
	 */
	public CandidateKey generateKey() throws InternalApplicationException {
		/* TODO: this is not optimal, maybe use a second list with the remote-reported
		   rounds and candidate numbers to get rid of possible double insertions */
		CandidateKeyPart[] tmp = new CandidateKeyPart[matchingKeyParts.length];
		/* copy all rounds to the temporary array to sort them, but make sure
		   that each round is represented by one candidate */
		int numCopied=0, keyPartsLength=0;
		for (int i=0; i<matchingKeyParts.length; i++) {
			if (matchingKeyParts[i] != null) {
				boolean alreadyCopied = false;
				for (int j=0; j<numCopied; j++) {
					if (matchingKeyParts[i].round == tmp[j].round) {
						alreadyCopied = true;
						logger.warn("Round " + tmp[j].round + " has two matching candidates: " +
								matchingKeyParts[i].candidateNumber + " and " + tmp[j].candidateNumber +
								", skipping latter");
					}
				}
				if (!alreadyCopied) {
					tmp[numCopied++] = matchingKeyParts[i];
					keyPartsLength += matchingKeyParts[i].keyPart.length;
				}
			}
		}
		logger.info("Generating candidate key from " + numCopied + " matching key parts");
		Arrays.sort(tmp, 0, numCopied);
		// this is the concatenated "plain text", which does not have key-quality attributes
		byte[] keyParts = new byte[keyPartsLength];
		int offset=0;
		for (int i=0; i<numCopied; i++) {
			System.arraycopy(tmp[i].keyPart, 0, keyParts, offset, tmp[i].keyPart.length);
			offset += tmp[i].keyPart.length;
		}
		/* and do two hashes over it, one for comparing, the other for generating the
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
		ret.numParts = numCopied;
		
		return ret;
	}
	
	/** Tries to generate a key that produces the same hash from the
	 * list of matching key parts. To search for the possible key, it uses
	 * a sliding window of numParts over the local list of matching key parts
	 * and, if multiple candidates match for the same round, tries all possible
	 * combinations of these candidates. This can be an expensive operation.
	 * @param hash The hash received from the remote host.
	 * @param numParts The number of key parts that the key is composed of, as
	 *                 reported by the remote host. <b>Note:</b> This parameter is
	 *                 not strictly necessary and could be omitted for slightly
	 *                 better security (i.e. less information to an eavesdropper).
	 *                 It is used for better performance in searching for a matching
	 *                 key.
	 * @return The key matching the hash, or null when same hash could not be
	 *         generated a combination of parts in matchingKeyParts */
	public CandidateKey searchKey(byte[] hash, int numParts) {
		if (hash == null)
			throw new IllegalArgumentException("hash must be set");
		if (numParts > matchingKeyParts.length) {
			logger.error("Received candidate key has been created of more key parts than " + 
					"there are in the local list of matching key parts. Can not possibly find " +
					"a matching key. Giving up.");
			return null;
		}
		
		return null;
	}
}
