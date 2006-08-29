/* Copyright Rene Mayrhofer
 * File created 2006-05-06
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.authentication.test;

import java.util.BitSet;

import org.eu.mayrhofer.authentication.CandidateKeyProtocol;
import org.eu.mayrhofer.authentication.exceptions.InternalApplicationException;

import junit.framework.Assert;
import junit.framework.TestCase;

public class CandidateKeyProtocolTest extends TestCase {
	public CandidateKeyProtocolTest(String s) {
		super(s);
	}

	protected boolean useJSSE1 = true;
	protected boolean useJSSE2 = true;
	
	private CandidateKeyProtocol p1;
	private CandidateKeyProtocol p1a;
	private CandidateKeyProtocol p2;
	private CandidateKeyProtocol p2a;
	private CandidateKeyProtocol p3;
	byte[][] keyParts_round1_side1;
	byte[][] keyParts_round1_side2;
	byte[][] keyParts_round1_side2_multipleMatches;
	byte[][] keyParts_round1_side3;
	byte[][] keyParts_round2_side1;
	byte[][] keyParts_round2_side2;
	byte[][] keyParts_round2_side2_multipleMatches;
	byte[][] keyParts_round2_side2_matchWith_round1_side1_1;
	byte[][] keyParts_round2_side2_matchWith_round1_side1_2;
	
	Integer remoteIdentifier1;
	Integer remoteIdentifier2;
	Integer remoteIdentifier3;
	
	public void setUp() {
		p1 = new CandidateKeyProtocol(10, 5, 300, "p1", useJSSE1);
		p2 = new CandidateKeyProtocol(10, 5, 300, "p2", useJSSE2);
		// more match history to keep _all_ the multiple matches
		p1a = new CandidateKeyProtocol(10, 6, 300, "p1a", useJSSE1);
		p2a = new CandidateKeyProtocol(10, 6, 300, "p2a", useJSSE2);
		// just use useJSSE1 for the third host - interoperability is tested anyway
		p3 = new CandidateKeyProtocol(10, 5, 300, "p3", useJSSE1);
		
		remoteIdentifier1 = new Integer(1);
		remoteIdentifier2 = new Integer(2);
		remoteIdentifier3 = new Integer(3);
		
		keyParts_round1_side1 = new byte[][] { 
				new byte[] {1, 2, 3, 4, 5, 4, 3, 2, 1},
				new byte[] {1, 2, 4, 4, 5, 4, 3, 2, 1},
				new byte[] {1, 2, 3, 4, 5, 4, 7, 2, 1},
				new byte[] {1, 2, 3, 4, 5, 4, 3, 2, 2} };
		// 1 matches 2 in side1
		keyParts_round1_side2 = new byte[][] { 
				new byte[] {2, 2, 3, 4, 5, 4, 3, 2, 1},
				new byte[] {1, 2, 3, 4, 5, 4, 7, 2, 1},
				new byte[] {2, 2, 4, 4, 5, 4, 3, 2, 1},
				new byte[] {2, 2, 3, 4, 5, 4, 3, 2, 2} };
		// 1 matches 3 in side1, and 2 matches 0 in side2
		keyParts_round1_side3 = new byte[][] { 
				new byte[] {5, 2, 3, 4, 5, 4, 7, 2, 1},
				new byte[] {1, 2, 3, 4, 5, 4, 3, 2, 2},
				new byte[] {2, 2, 3, 4, 5, 4, 3, 2, 1},
				new byte[] {3, 2, 3, 4, 5, 4, 3, 2, 2} };
		// 0 matches 2 in side1, 2 matches 1 in side1, and 1 matches 0 in side1  
		keyParts_round1_side2_multipleMatches = new byte[][] { 
				new byte[] {1, 2, 3, 4, 5, 4, 7, 2, 1},
				new byte[] {1, 2, 3, 4, 5, 4, 3, 2, 1},
				new byte[] {1, 2, 4, 4, 5, 4, 3, 2, 1},
				new byte[] {2, 2, 3, 4, 5, 4, 3, 2, 2} };

		keyParts_round2_side1 = new byte[][] { 
				new byte[] {5, 2, 3, 4, 5, 4, 3, 2, 3},
				new byte[] {4, 2, 4, 4, 5, 4, 3, 2, 3},
				new byte[] {3, 2, 3, 4, 5, 4, 7, 2, 3},
				new byte[] {2, 2, 3, 4, 5, 4, 7, 2, 3},
				new byte[] {1, 2, 3, 4, 5, 4, 3, 2, 4} };
		// 1 matches 4 in side1 and 3 matches 2 in side1 (all in round 2)
		keyParts_round2_side2 = new byte[][] { 
				new byte[] {9, 2, 3, 4, 5, 4, 3, 2, 5},
				new byte[] {1, 2, 3, 4, 5, 4, 3, 2, 4},
				new byte[] {7, 2, 3, 4, 5, 4, 3, 2, 3},
				new byte[] {3, 2, 3, 4, 5, 4, 7, 2, 3},
				new byte[] {5, 2, 3, 4, 5, 4, 3, 2, 8} };
		// 1 matches 4 in side1, 3 matches 2 in side1, and 4 matches 0 in side1 (all in round 2)
		keyParts_round2_side2_multipleMatches = new byte[][] { 
				new byte[] {9, 2, 3, 4, 5, 4, 3, 2, 5},
				new byte[] {1, 2, 3, 4, 5, 4, 3, 2, 4},
				new byte[] {7, 2, 3, 4, 5, 4, 3, 2, 3},
				new byte[] {3, 2, 3, 4, 5, 4, 7, 2, 3},
				new byte[] {5, 2, 3, 4, 5, 4, 3, 2, 3} };

		// 1 matches 0 in side1/round1
		keyParts_round2_side2_matchWith_round1_side1_1 = new byte[][] { 
				new byte[] {20, 2, 3, 4, 5, 4, 3, 2, 5},
				new byte[] {1, 2, 3, 4, 5, 4, 3, 2, 1},
				new byte[] {20, 2, 3, 4, 5, 4, 3, 2, 4},
				new byte[] {20, 2, 3, 4, 5, 4, 3, 2, 3},
				new byte[] {20, 2, 3, 4, 5, 4, 7, 2, 3},
				new byte[] {20, 2, 3, 4, 5, 4, 3, 2, 8} };
		// 1 matches 3 in side1/round1
		keyParts_round2_side2_matchWith_round1_side1_2 = new byte[][] { 
				new byte[] {20, 2, 3, 4, 5, 4, 3, 2, 5},
				new byte[] {1, 2, 3, 4, 5, 4, 3, 2, 2},
				new byte[] {20, 2, 3, 4, 5, 4, 3, 2, 4},
				new byte[] {20, 2, 3, 4, 5, 4, 3, 2, 3},
				new byte[] {20, 2, 3, 4, 5, 4, 7, 2, 3},
				new byte[] {20, 2, 3, 4, 5, 4, 3, 2, 8} };
	}
	
	public void testMatching_1Round() throws InternalApplicationException {
		CandidateKeyProtocol.CandidateKeyPartIdentifier i1[] = p1.generateCandidates(keyParts_round1_side1, 0);
		CandidateKeyProtocol.CandidateKeyPartIdentifier i2[] = p2.generateCandidates(keyParts_round1_side2, 0);

		int ind1 = p1.matchCandidates(remoteIdentifier2, i2);
		int ind2 = p2.matchCandidates(remoteIdentifier1, i1);
		Assert.assertEquals("Match did not return correct index", 1, ind1);
		Assert.assertEquals("Match did not return correct index", 2, ind2);

		Assert.assertEquals(1, p1.getNumTotalMatches(remoteIdentifier2));
		Assert.assertEquals(1, p2.getNumTotalMatches(remoteIdentifier1));
		Assert.assertEquals(1, p1.getNumLocalRounds(remoteIdentifier2));
		Assert.assertEquals(1, p2.getNumLocalRounds(remoteIdentifier1));
	}

	public void testMatching_2Rounds() throws InternalApplicationException {
		CandidateKeyProtocol.CandidateKeyPartIdentifier i1_1[] = p1.generateCandidates(keyParts_round1_side1, 0);
		CandidateKeyProtocol.CandidateKeyPartIdentifier i2_1[] = p2.generateCandidates(keyParts_round1_side2, 0);

		CandidateKeyProtocol.CandidateKeyPartIdentifier i1_2[] = p1.generateCandidates(keyParts_round2_side1, 0);
		int ind2_1 = p2.matchCandidates(remoteIdentifier1, i1_1);
		int ind2_2 = p2.matchCandidates(remoteIdentifier1, i1_2);
		Assert.assertEquals("Match did not return correct index", 2, ind2_1);
		Assert.assertEquals("Match did not return correct index", -1, ind2_2);

		// only 1 match now, because second round candidates not yet generated in p2
		Assert.assertEquals(1, p2.getNumTotalMatches(remoteIdentifier1));
		Assert.assertTrue(Math.abs(p2.getMatchingRoundsFraction(remoteIdentifier1) - 1.0) <= 0.0001);
		
		CandidateKeyProtocol.CandidateKeyPartIdentifier i2_2[] = p2.generateCandidates(keyParts_round2_side2, 0);

		Assert.assertEquals(2, p2.getNumLocalRounds(remoteIdentifier1));
		
		int ind1_1 = p1.matchCandidates(remoteIdentifier2, i2_1);
		int ind1_2 = p1.matchCandidates(remoteIdentifier2, i2_2);
		ind2_1 = p2.matchCandidates(remoteIdentifier1, i1_1);
		ind2_2 = p2.matchCandidates(remoteIdentifier1, i1_2);

		Assert.assertEquals("Match did not return correct index", 2, ind2_1);
		Assert.assertEquals("Match did not return correct index", 4, ind2_2);
		Assert.assertEquals("Match did not return correct index", 1, ind1_1);
		Assert.assertEquals("Match did not return correct index", 3, ind1_2);

		Assert.assertEquals(2, p2.getNumLocalRounds(remoteIdentifier1));
		Assert.assertEquals(2, p1.getNumLocalRounds(remoteIdentifier2));
		Assert.assertTrue(Math.abs(p2.getMatchingRoundsFraction(remoteIdentifier1) - 1.0) <= 0.0001);
		Assert.assertTrue(Math.abs(p1.getMatchingRoundsFraction(remoteIdentifier2) - 1.0) <= 0.0001);

		// now it must be 3 matches
		Assert.assertEquals(3, p2.getNumTotalMatches(remoteIdentifier1));
	}
	
	public void testMatchingAndKeyGeneration_1Round() throws InternalApplicationException {
		CandidateKeyProtocol.CandidateKeyPartIdentifier i1[] = p1.generateCandidates(keyParts_round1_side1, 0);
		CandidateKeyProtocol.CandidateKeyPartIdentifier i2[] = p2.generateCandidates(keyParts_round1_side2, 0);

		p1.matchCandidates(remoteIdentifier2, i2);
		p2.matchCandidates(remoteIdentifier1, i1);
		
		Assert.assertEquals(1, p1.getNumLocalRounds(remoteIdentifier2));
		Assert.assertEquals(1, p2.getNumLocalRounds(remoteIdentifier1));
		
		CandidateKeyProtocol.CandidateKey k1 = p1.generateKey(remoteIdentifier2);
		CandidateKeyProtocol.CandidateKey k2 = p2.generateKey(remoteIdentifier1);
		Assert.assertNotNull(k1);
		Assert.assertNotNull(k2);
		
		Assert.assertTrue("Generated keys do not match", SimpleKeyAgreementTest.compareByteArray(k1.hash, k2.hash));
		Assert.assertTrue("Generated keys do not match", SimpleKeyAgreementTest.compareByteArray(k1.key, k2.key));
	}

	public void testMatchingAndKeyGeneration_1Round_multipleCandidates() throws InternalApplicationException {
		CandidateKeyProtocol.CandidateKeyPartIdentifier i1[] = p1.generateCandidates(keyParts_round1_side1, 0);
		CandidateKeyProtocol.CandidateKeyPartIdentifier i2[] = p2.generateCandidates(keyParts_round1_side2_multipleMatches, 0);

		p1.matchCandidates(remoteIdentifier2, i2);
		p2.matchCandidates(remoteIdentifier1, i1);
		
		Assert.assertEquals(3, p1.getNumTotalMatches(remoteIdentifier2));
		Assert.assertEquals(3, p2.getNumTotalMatches(remoteIdentifier1));
		Assert.assertEquals(1, p1.getNumLocalRounds(remoteIdentifier2));
		Assert.assertEquals(1, p2.getNumLocalRounds(remoteIdentifier1));
		
		CandidateKeyProtocol.CandidateKey k1 = p1.generateKey(remoteIdentifier2);
		CandidateKeyProtocol.CandidateKey k2 = p2.generateKey(remoteIdentifier1);
		Assert.assertNotNull(k1);
		Assert.assertNotNull(k2);
		
		Assert.assertFalse("Generated keys should not match because of different match order, but do!", SimpleKeyAgreementTest.compareByteArray(k1.hash, k2.hash));
		Assert.assertFalse("Generated keys should not match because of different match order, but do!", SimpleKeyAgreementTest.compareByteArray(k1.key, k2.key));
	}

	public void testMatchingAndKeyGeneration_2Rounds_searchKey() throws InternalApplicationException {
		CandidateKeyProtocol.CandidateKeyPartIdentifier i1_1[] = p1.generateCandidates(keyParts_round1_side1, 0);
		CandidateKeyProtocol.CandidateKeyPartIdentifier i2_1[] = p2.generateCandidates(keyParts_round1_side2, 0);
		CandidateKeyProtocol.CandidateKeyPartIdentifier i1_2[] = p1.generateCandidates(keyParts_round2_side1, 0);
		CandidateKeyProtocol.CandidateKeyPartIdentifier i2_2[] = p2.generateCandidates(keyParts_round2_side2, 0);

		Assert.assertEquals(1, p1.matchCandidates(remoteIdentifier2, i2_1));
		Assert.assertEquals(2, p2.matchCandidates(remoteIdentifier1, i1_1));
		Assert.assertEquals(3, p1.matchCandidates(remoteIdentifier2, i2_2));
		Assert.assertEquals(4, p2.matchCandidates(remoteIdentifier1, i1_2));

		Assert.assertEquals(3, p1.getNumTotalMatches(remoteIdentifier2));
		Assert.assertEquals(3, p2.getNumTotalMatches(remoteIdentifier1));
		Assert.assertEquals(2, p1.getNumLocalRounds(remoteIdentifier2));
		Assert.assertEquals(2, p2.getNumLocalRounds(remoteIdentifier1));
		
		CandidateKeyProtocol.CandidateKey k1 = p1.generateKey(remoteIdentifier2);
		CandidateKeyProtocol.CandidateKey k2 = p2.generateKey(remoteIdentifier1);
		Assert.assertNotNull(k1);
		Assert.assertNotNull(k2);
		
		Assert.assertFalse("Generated keys should not match, but do", SimpleKeyAgreementTest.compareByteArray(k1.hash, k2.hash));
		Assert.assertFalse("Generated keys should not match, but do", SimpleKeyAgreementTest.compareByteArray(k1.key, k2.key));
		
		CandidateKeyProtocol.CandidateKey sk1 = p1.searchKey(remoteIdentifier2, k2.hash, k2.numParts);
		CandidateKeyProtocol.CandidateKey sk2 = p2.searchKey(remoteIdentifier1, k1.hash, k1.numParts);

		Assert.assertNotNull("Should have been able to generate an equal key", sk1);
		Assert.assertNotNull("Should have been able to generate an equal key", sk2);
		Assert.assertTrue("Generated and searched keys do not match", SimpleKeyAgreementTest.compareByteArray(sk1.hash, k2.hash));
		Assert.assertTrue("Generated and searched keys do not match", SimpleKeyAgreementTest.compareByteArray(sk1.key, k2.key));
		Assert.assertTrue("Generated and searched keys do not match", SimpleKeyAgreementTest.compareByteArray(sk2.hash, k1.hash));
		Assert.assertTrue("Generated and searched keys do not match", SimpleKeyAgreementTest.compareByteArray(sk2.key, k1.key));
	}

	public void testMatchingAndKeyGeneration_2Rounds_searchKey_multipleCandidates_historyTooSmall() throws InternalApplicationException {
		CandidateKeyProtocol.CandidateKeyPartIdentifier i1_1[] = p1.generateCandidates(keyParts_round1_side1, 0);
		CandidateKeyProtocol.CandidateKeyPartIdentifier i2_1[] = p2.generateCandidates(keyParts_round1_side2_multipleMatches, 0);
		CandidateKeyProtocol.CandidateKeyPartIdentifier i1_2[] = p1.generateCandidates(keyParts_round2_side1, 0);
		CandidateKeyProtocol.CandidateKeyPartIdentifier i2_2[] = p2.generateCandidates(keyParts_round2_side2_multipleMatches, 0);

		Assert.assertEquals(1, p1.matchCandidates(remoteIdentifier2, i2_1));
		Assert.assertEquals(2, p2.matchCandidates(remoteIdentifier1, i1_1));
		Assert.assertEquals(4, p1.matchCandidates(remoteIdentifier2, i2_2));
		Assert.assertEquals(4, p2.matchCandidates(remoteIdentifier1, i1_2));

		Assert.assertEquals(5, p1.getNumTotalMatches(remoteIdentifier2));
		Assert.assertEquals(5, p2.getNumTotalMatches(remoteIdentifier1));
		Assert.assertEquals(2, p1.getNumLocalRounds(remoteIdentifier2));
		Assert.assertEquals(2, p2.getNumLocalRounds(remoteIdentifier1));
		
		CandidateKeyProtocol.CandidateKey k1 = p1.generateKey(remoteIdentifier2);
		CandidateKeyProtocol.CandidateKey k2 = p2.generateKey(remoteIdentifier1);
		Assert.assertNotNull(k1);
		Assert.assertNotNull(k2);
		
		Assert.assertFalse("Generated keys should not match, but do", SimpleKeyAgreementTest.compareByteArray(k1.hash, k2.hash));
		Assert.assertFalse("Generated keys should not match, but do", SimpleKeyAgreementTest.compareByteArray(k1.key, k2.key));
		
		CandidateKeyProtocol.CandidateKey sk1 = p1.searchKey(remoteIdentifier2, k2.hash, k2.numParts);
		CandidateKeyProtocol.CandidateKey sk2 = p2.searchKey(remoteIdentifier1, k1.hash, k1.numParts);

		Assert.assertNull("Should not have been able to generate an equal key", sk1);
		Assert.assertNotNull("Should have been able to generate an equal key", sk2);
	}

	public void testMatchingAndKeyGeneration_2Rounds_searchKey_multipleCandidates_historySufficient() throws InternalApplicationException {
		CandidateKeyProtocol.CandidateKeyPartIdentifier i1_1[] = p1a.generateCandidates(keyParts_round1_side1, 0);
		CandidateKeyProtocol.CandidateKeyPartIdentifier i2_1[] = p2a.generateCandidates(keyParts_round1_side2_multipleMatches, 0);
		CandidateKeyProtocol.CandidateKeyPartIdentifier i1_2[] = p1a.generateCandidates(keyParts_round2_side1, 0);
		CandidateKeyProtocol.CandidateKeyPartIdentifier i2_2[] = p2a.generateCandidates(keyParts_round2_side2_multipleMatches, 0);

		Assert.assertEquals(1, p1a.matchCandidates(remoteIdentifier2, i2_1));
		Assert.assertEquals(2, p2a.matchCandidates(remoteIdentifier1, i1_1));
		Assert.assertEquals(4, p1a.matchCandidates(remoteIdentifier2, i2_2));
		Assert.assertEquals(4, p2a.matchCandidates(remoteIdentifier1, i1_2));

		Assert.assertEquals(6, p1a.getNumTotalMatches(remoteIdentifier2));
		Assert.assertEquals(6, p2a.getNumTotalMatches(remoteIdentifier1));
		Assert.assertEquals(2, p1a.getNumLocalRounds(remoteIdentifier2));
		Assert.assertEquals(2, p2a.getNumLocalRounds(remoteIdentifier1));
		
		CandidateKeyProtocol.CandidateKey k1 = p1a.generateKey(remoteIdentifier2);
		CandidateKeyProtocol.CandidateKey k2 = p2a.generateKey(remoteIdentifier1);
		Assert.assertNotNull(k1);
		Assert.assertNotNull(k2);
		
		Assert.assertFalse("Generated keys should not match, but do", SimpleKeyAgreementTest.compareByteArray(k1.hash, k2.hash));
		Assert.assertFalse("Generated keys should not match, but do", SimpleKeyAgreementTest.compareByteArray(k1.key, k2.key));
		
		CandidateKeyProtocol.CandidateKey sk1 = p1a.searchKey(remoteIdentifier2, k2.hash, k2.numParts);
		CandidateKeyProtocol.CandidateKey sk2 = p2a.searchKey(remoteIdentifier1, k1.hash, k1.numParts);

		Assert.assertNotNull("Should have been able to generate an equal key", sk1);
		Assert.assertNotNull("Should have been able to generate an equal key", sk2);
		Assert.assertTrue("Generated and searched keys do not match", SimpleKeyAgreementTest.compareByteArray(sk1.hash, k2.hash));
		Assert.assertTrue("Generated and searched keys do not match", SimpleKeyAgreementTest.compareByteArray(sk1.key, k2.key));
		Assert.assertTrue("Generated and searched keys do not match", SimpleKeyAgreementTest.compareByteArray(sk2.hash, k1.hash));
		Assert.assertTrue("Generated and searched keys do not match", SimpleKeyAgreementTest.compareByteArray(sk2.key, k1.key));
	}
	
	public void testMatchingAcknowledgeAndKeyGeneration_Asymmetric_1Round() throws InternalApplicationException {
		p1.generateCandidates(keyParts_round1_side1, 0);
		CandidateKeyProtocol.CandidateKeyPartIdentifier i2[] = p2.generateCandidates(keyParts_round1_side2, 0);

		int ind1 = p1.matchCandidates(remoteIdentifier2, i2);
		p2.acknowledgeMatches(remoteIdentifier1, i2[0].round, ind1);

		Assert.assertEquals(1, p1.getNumTotalMatches(remoteIdentifier2));
		Assert.assertEquals(1, p2.getNumTotalMatches(remoteIdentifier1));
		Assert.assertEquals(1, p1.getNumLocalRounds(remoteIdentifier2));
		Assert.assertEquals(1, p2.getNumLocalRounds(remoteIdentifier1));
		
		CandidateKeyProtocol.CandidateKey k1 = p1.generateKey(remoteIdentifier2);
		CandidateKeyProtocol.CandidateKey k2 = p2.generateKey(remoteIdentifier1);
		Assert.assertNotNull(k1);
		Assert.assertNotNull(k2);
		
		Assert.assertTrue("Generated keys do not match", SimpleKeyAgreementTest.compareByteArray(k1.hash, k2.hash));
		Assert.assertTrue("Generated keys do not match", SimpleKeyAgreementTest.compareByteArray(k1.key, k2.key));
	}

	public void testMatchingAcknowledgeAndKeyGeneration_Asymmetric_1Round_multipleCandidates() throws InternalApplicationException {
		p1.generateCandidates(keyParts_round1_side1, 0);
		CandidateKeyProtocol.CandidateKeyPartIdentifier i2[] = p2.generateCandidates(keyParts_round1_side2_multipleMatches, 0);

		int ind1 = p1.matchCandidates(remoteIdentifier2, i2);
		p2.acknowledgeMatches(remoteIdentifier1, i2[0].round, ind1);

		Assert.assertEquals(3, p1.getNumTotalMatches(remoteIdentifier2));
		Assert.assertEquals(1, p2.getNumTotalMatches(remoteIdentifier1));
		Assert.assertEquals(1, p1.getNumLocalRounds(remoteIdentifier2));
		Assert.assertEquals(1, p2.getNumLocalRounds(remoteIdentifier1));
		
		CandidateKeyProtocol.CandidateKey k1 = p1.generateKey(remoteIdentifier2);
		CandidateKeyProtocol.CandidateKey k2 = p2.generateKey(remoteIdentifier1);
		Assert.assertNotNull(k1);
		Assert.assertNotNull(k2);
		
		Assert.assertTrue("Generated keys do not match", SimpleKeyAgreementTest.compareByteArray(k1.hash, k2.hash));
		Assert.assertTrue("Generated keys do not match", SimpleKeyAgreementTest.compareByteArray(k1.key, k2.key));
	}
	
	public void testMatchingAcknowledgeAndKeyGeneration_Asymmetric_2Rounds() throws InternalApplicationException {
		p1.generateCandidates(keyParts_round1_side1, 0);
		CandidateKeyProtocol.CandidateKeyPartIdentifier i2_1[] = p2.generateCandidates(keyParts_round1_side2, 0);
		p1.generateCandidates(keyParts_round2_side1, 0);
		CandidateKeyProtocol.CandidateKeyPartIdentifier i2_2[] = p2.generateCandidates(keyParts_round2_side2, 0);

		int ind1_1 = p1.matchCandidates(remoteIdentifier2, i2_1);
		p2.acknowledgeMatches(remoteIdentifier1, i2_1[0].round, ind1_1);
		int ind1_2 = p1.matchCandidates(remoteIdentifier2, i2_2);
		p2.acknowledgeMatches(remoteIdentifier1, i2_2[0].round, ind1_2);

		Assert.assertEquals(3, p1.getNumTotalMatches(remoteIdentifier2));
		// only two matches here because only acknowledge matches, but p2 didn't match itself 
		Assert.assertEquals(2, p2.getNumTotalMatches(remoteIdentifier1));
		
		Assert.assertEquals(2, p1.getNumLocalRounds(remoteIdentifier2));
		Assert.assertEquals(2, p2.getNumLocalRounds(remoteIdentifier1));

		CandidateKeyProtocol.CandidateKey k1 = p1.generateKey(remoteIdentifier2);
		CandidateKeyProtocol.CandidateKey k2 = p2.generateKey(remoteIdentifier1);
		Assert.assertNotNull(k1);
		Assert.assertNotNull(k2);
		
		Assert.assertTrue("Generated keys do not match", SimpleKeyAgreementTest.compareByteArray(k1.hash, k2.hash));
		Assert.assertTrue("Generated keys do not match", SimpleKeyAgreementTest.compareByteArray(k1.key, k2.key));
	}

	public void testMatchingAcknowledgeAndKeyGeneration_Asymmetric_2Rounds_multipleCandidates() throws InternalApplicationException {
		p1.generateCandidates(keyParts_round1_side1, 0);
		CandidateKeyProtocol.CandidateKeyPartIdentifier i2_1[] = p2.generateCandidates(keyParts_round1_side2_multipleMatches, 0);
		p1.generateCandidates(keyParts_round2_side1, 0);
		CandidateKeyProtocol.CandidateKeyPartIdentifier i2_2[] = p2.generateCandidates(keyParts_round2_side2_multipleMatches, 0);

		int ind1_1 = p1.matchCandidates(remoteIdentifier2, i2_1);
		p2.acknowledgeMatches(remoteIdentifier1, i2_1[0].round, ind1_1);
		int ind1_2 = p1.matchCandidates(remoteIdentifier2, i2_2);
		p2.acknowledgeMatches(remoteIdentifier1, i2_2[0].round, ind1_2);

		Assert.assertEquals(5, p1.getNumTotalMatches(remoteIdentifier2));
		// only two matches here because only acknowledge matches, but p2 didn't match itself 
		Assert.assertEquals(2, p2.getNumTotalMatches(remoteIdentifier1));
		Assert.assertEquals(2, p1.getNumLocalRounds(remoteIdentifier2));
		Assert.assertEquals(2, p2.getNumLocalRounds(remoteIdentifier1));
		
		CandidateKeyProtocol.CandidateKey k1 = p1.generateKey(remoteIdentifier2);
		CandidateKeyProtocol.CandidateKey k2 = p2.generateKey(remoteIdentifier1);
		Assert.assertNotNull(k1);
		Assert.assertNotNull(k2);
		
		Assert.assertFalse("Generated keys should not match because of different match order, but do!", SimpleKeyAgreementTest.compareByteArray(k1.hash, k2.hash));
		Assert.assertFalse("Generated keys should not match because of different match order, but do!", SimpleKeyAgreementTest.compareByteArray(k1.key, k2.key));
	}

	public void testMatchingAcknowledgeAndKeyGeneration_Asymmetric_2Rounds_searchKey_multipleCandidates_historyTooSmall() throws InternalApplicationException {
		p1.generateCandidates(keyParts_round1_side1, 0);
		CandidateKeyProtocol.CandidateKeyPartIdentifier i2_1[] = p2.generateCandidates(keyParts_round1_side2_multipleMatches, 0);
		p1.generateCandidates(keyParts_round2_side1, 0);
		CandidateKeyProtocol.CandidateKeyPartIdentifier i2_2[] = p2.generateCandidates(keyParts_round2_side2_multipleMatches, 0);

		int ind1_1 = p1.matchCandidates(remoteIdentifier2, i2_1);
		p2.acknowledgeMatches(remoteIdentifier1, i2_1[0].round, ind1_1);
		int ind1_2 = p1.matchCandidates(remoteIdentifier2, i2_2);
		p2.acknowledgeMatches(remoteIdentifier1, i2_2[0].round, ind1_2);

		Assert.assertEquals(5, p1.getNumTotalMatches(remoteIdentifier2));
		// only two matches here because only acknowledge matches, but p2 didn't match itself 
		Assert.assertEquals(2, p2.getNumTotalMatches(remoteIdentifier1));
		Assert.assertEquals(2, p1.getNumLocalRounds(remoteIdentifier2));
		Assert.assertEquals(2, p2.getNumLocalRounds(remoteIdentifier1));
		
		CandidateKeyProtocol.CandidateKey k1 = p1.generateKey(remoteIdentifier2);
		CandidateKeyProtocol.CandidateKey k2 = p2.generateKey(remoteIdentifier1);
		Assert.assertNotNull(k1);
		Assert.assertNotNull(k2);
		
		Assert.assertFalse("Generated keys should not match because of different match order, but do!", SimpleKeyAgreementTest.compareByteArray(k1.hash, k2.hash));
		Assert.assertFalse("Generated keys should not match because of different match order, but do!", SimpleKeyAgreementTest.compareByteArray(k1.key, k2.key));

		CandidateKeyProtocol.CandidateKey sk1 = p1.searchKey(remoteIdentifier2, k2.hash, k2.numParts);
		CandidateKeyProtocol.CandidateKey sk2 = p2.searchKey(remoteIdentifier1, k1.hash, k1.numParts);

		Assert.assertNull("Should not have been able to generate an equal key", sk1);
		Assert.assertNull("Should not have been able to generate an equal key", sk2);
	}

	public void testMatchingAcknowledgeAndKeyGeneration_Asymmetric_2Rounds_searchKey_multipleCandidates_historySufficient() throws InternalApplicationException {
		p1a.generateCandidates(keyParts_round1_side1, 0);
		CandidateKeyProtocol.CandidateKeyPartIdentifier i2_1[] = p2a.generateCandidates(keyParts_round1_side2_multipleMatches, 0);
		p1a.generateCandidates(keyParts_round2_side1, 0);
		CandidateKeyProtocol.CandidateKeyPartIdentifier i2_2[] = p2a.generateCandidates(keyParts_round2_side2_multipleMatches, 0);

		int ind1_1 = p1a.matchCandidates(remoteIdentifier2, i2_1);
		p2a.acknowledgeMatches(remoteIdentifier1, i2_1[0].round, ind1_1);
		int ind1_2 = p1a.matchCandidates(remoteIdentifier2, i2_2);
		p2a.acknowledgeMatches(remoteIdentifier1, i2_2[0].round, ind1_2);

		Assert.assertEquals(6, p1a.getNumTotalMatches(remoteIdentifier2));
		// only two matches here because only acknowledge matches, but p2 didn't match itself 
		Assert.assertEquals(2, p2a.getNumTotalMatches(remoteIdentifier1));
		Assert.assertEquals(2, p1a.getNumLocalRounds(remoteIdentifier2));
		Assert.assertEquals(2, p2a.getNumLocalRounds(remoteIdentifier1));
		
		CandidateKeyProtocol.CandidateKey k1 = p1a.generateKey(remoteIdentifier2);
		CandidateKeyProtocol.CandidateKey k2 = p2a.generateKey(remoteIdentifier1);
		Assert.assertNotNull(k1);
		Assert.assertNotNull(k2);
		
		Assert.assertTrue("Generated keys do not match", SimpleKeyAgreementTest.compareByteArray(k1.hash, k2.hash));
		Assert.assertTrue("Generated keys do not match", SimpleKeyAgreementTest.compareByteArray(k1.key, k2.key));

		CandidateKeyProtocol.CandidateKey sk1 = p1a.searchKey(remoteIdentifier2, k2.hash, k2.numParts);
		CandidateKeyProtocol.CandidateKey sk2 = p2a.searchKey(remoteIdentifier1, k1.hash, k1.numParts);

		Assert.assertNotNull("Should have been able to generate an equal key", sk1);
		Assert.assertNotNull("Should have been able to generate an equal key", sk2);
		Assert.assertTrue("Generated keys do not match", SimpleKeyAgreementTest.compareByteArray(sk1.hash, sk2.hash));
		Assert.assertTrue("Generated keys do not match", SimpleKeyAgreementTest.compareByteArray(sk1.key, sk2.key));
	}
	
	public void testBug1_2Rounds_generateKey_interlocked_RoundsWithSameParts() throws InternalApplicationException {
		CandidateKeyProtocol.CandidateKeyPartIdentifier i1_1[] = p1.generateCandidates(keyParts_round1_side1, 0);
		CandidateKeyProtocol.CandidateKeyPartIdentifier i2_1[] = p2.generateCandidates(keyParts_round1_side2, 0);
		Assert.assertEquals(1, p1.matchCandidates(remoteIdentifier2, i2_1));
		Assert.assertEquals(2, p2.matchCandidates(remoteIdentifier1, i1_1));

		CandidateKeyProtocol.CandidateKeyPartIdentifier i1_2[] = p1.generateCandidates(keyParts_round2_side1, 0);
		CandidateKeyProtocol.CandidateKeyPartIdentifier i2_2[] = p2.generateCandidates(keyParts_round2_side2_matchWith_round1_side1_1, 0);
		Assert.assertEquals(1, p1.matchCandidates(remoteIdentifier2, i2_2));
		Assert.assertEquals(-1, p2.matchCandidates(remoteIdentifier1, i1_2));

		Assert.assertEquals(2, p1.getNumTotalMatches(remoteIdentifier2));
		Assert.assertEquals(1, p2.getNumTotalMatches(remoteIdentifier1));
		Assert.assertEquals(2, p1.getNumLocalRounds(remoteIdentifier2));
		Assert.assertEquals(2, p2.getNumLocalRounds(remoteIdentifier1));
		Assert.assertTrue(Math.abs(p1.getMatchingRoundsFraction(remoteIdentifier2) - 0.5) <= 0.0001);
		Assert.assertTrue(Math.abs(p2.getMatchingRoundsFraction(remoteIdentifier1) - 0.5) <= 0.0001);
		
		CandidateKeyProtocol.CandidateKey k1 = p1.generateKey(remoteIdentifier2);
		CandidateKeyProtocol.CandidateKey k2 = p2.generateKey(remoteIdentifier1);
		Assert.assertNotNull(k1);
		Assert.assertNotNull(k2);
		
		Assert.assertTrue("Generated keys do not match", SimpleKeyAgreementTest.compareByteArray(k1.hash, k2.hash));
		Assert.assertTrue("Generated keys do not match", SimpleKeyAgreementTest.compareByteArray(k1.key, k2.key));
	}

	public void testBug1_2Rounds_generateKey_generateThenMatch_RoundsWithSameParts_sameMatchOrder() throws InternalApplicationException {
		CandidateKeyProtocol.CandidateKeyPartIdentifier i1_1[] = p1.generateCandidates(keyParts_round1_side1, 0);
		CandidateKeyProtocol.CandidateKeyPartIdentifier i2_1[] = p2.generateCandidates(keyParts_round1_side2, 0);
		CandidateKeyProtocol.CandidateKeyPartIdentifier i1_2[] = p1.generateCandidates(keyParts_round2_side1, 0);
		CandidateKeyProtocol.CandidateKeyPartIdentifier i2_2[] = p2.generateCandidates(keyParts_round2_side2_matchWith_round1_side1_1, 0);

		Assert.assertEquals(1, p1.matchCandidates(remoteIdentifier2, i2_1));
		Assert.assertEquals(2, p2.matchCandidates(remoteIdentifier1, i1_1));
		Assert.assertEquals(1, p1.matchCandidates(remoteIdentifier2, i2_2));
		Assert.assertEquals(-1, p2.matchCandidates(remoteIdentifier1, i1_2));

		Assert.assertEquals(2, p1.getNumTotalMatches(remoteIdentifier2));
		Assert.assertEquals(2, p2.getNumTotalMatches(remoteIdentifier1));
		Assert.assertEquals(1, p1.getNumLocalRounds(remoteIdentifier2));
		Assert.assertEquals(2, p2.getNumLocalRounds(remoteIdentifier1));
		Assert.assertTrue(Math.abs(p1.getMatchingRoundsFraction(remoteIdentifier2) - 1.0) <= 0.0001);
		Assert.assertTrue(Math.abs(p2.getMatchingRoundsFraction(remoteIdentifier1) - 1.0) <= 0.0001);
		
		CandidateKeyProtocol.CandidateKey k1 = p1.generateKey(remoteIdentifier2);
		CandidateKeyProtocol.CandidateKey k2 = p2.generateKey(remoteIdentifier1);
		Assert.assertNotNull(k1);
		Assert.assertNotNull(k2);
		
		Assert.assertFalse("Generated keys should not match, but do", SimpleKeyAgreementTest.compareByteArray(k1.hash, k2.hash));
		Assert.assertFalse("Generated keys should not match, but do", SimpleKeyAgreementTest.compareByteArray(k1.key, k2.key));
		
		CandidateKeyProtocol.CandidateKey sk1 = p1.searchKey(remoteIdentifier2, k2.hash, k2.numParts);
		CandidateKeyProtocol.CandidateKey sk2 = p2.searchKey(remoteIdentifier1, k1.hash, k1.numParts);

		Assert.assertNull("Should not have been able to generate an equal key, because expecting 2 parts and remote should only have 1", sk1);
		Assert.assertNotNull("Should have been able to generate an equal key with just 1 part", sk2);
		Assert.assertTrue("Generated and searched keys do not match", SimpleKeyAgreementTest.compareByteArray(sk2.hash, k1.hash));
		Assert.assertTrue("Generated and searched keys do not match", SimpleKeyAgreementTest.compareByteArray(sk2.key, k1.key));
	}
	
	public void testBug1_2Rounds_searchKey_generateThenMatch_RoundsWithSameParts_differentMatchOrder() throws InternalApplicationException {
		CandidateKeyProtocol.CandidateKeyPartIdentifier i1_1[] = p1.generateCandidates(keyParts_round1_side1, 0);
		CandidateKeyProtocol.CandidateKeyPartIdentifier i2_1[] = p2.generateCandidates(keyParts_round1_side2, 0);
		CandidateKeyProtocol.CandidateKeyPartIdentifier i1_2[] = p1.generateCandidates(keyParts_round2_side1, 0);
		CandidateKeyProtocol.CandidateKeyPartIdentifier i2_2[] = p2.generateCandidates(keyParts_round2_side2_matchWith_round1_side1_2, 0);

		Assert.assertEquals(1, p1.matchCandidates(remoteIdentifier2, i2_1));
		Assert.assertEquals(2, p2.matchCandidates(remoteIdentifier1, i1_1));
		Assert.assertEquals(1, p1.matchCandidates(remoteIdentifier2, i2_2));
		Assert.assertEquals(-1, p2.matchCandidates(remoteIdentifier1, i1_2));

		Assert.assertEquals(2, p1.getNumTotalMatches(remoteIdentifier2));
		Assert.assertEquals(2, p2.getNumTotalMatches(remoteIdentifier1));
		Assert.assertEquals(1, p1.getNumLocalRounds(remoteIdentifier2));
		Assert.assertEquals(2, p2.getNumLocalRounds(remoteIdentifier1));
		
		CandidateKeyProtocol.CandidateKey k1 = p1.generateKey(remoteIdentifier2);
		CandidateKeyProtocol.CandidateKey k2 = p2.generateKey(remoteIdentifier1);
		Assert.assertNotNull(k1);
		Assert.assertNotNull(k2);
		
		Assert.assertFalse("Generated keys should not match, but do", SimpleKeyAgreementTest.compareByteArray(k1.hash, k2.hash));
		Assert.assertFalse("Generated keys should not match, but do", SimpleKeyAgreementTest.compareByteArray(k1.key, k2.key));
		
		CandidateKeyProtocol.CandidateKey sk1 = p1.searchKey(remoteIdentifier2, k2.hash, k2.numParts);
		CandidateKeyProtocol.CandidateKey sk2 = p2.searchKey(remoteIdentifier1, k1.hash, k1.numParts);

		Assert.assertNull("Should not have been able to generate an equal key, because expecting 2 parts and remote should only have 1", sk1);
		Assert.assertNotNull("Should have been able to generate an equal key with just 1 part", sk2);
		Assert.assertTrue("Generated and searched keys do not match", SimpleKeyAgreementTest.compareByteArray(sk2.hash, k1.hash));
		Assert.assertTrue("Generated and searched keys do not match", SimpleKeyAgreementTest.compareByteArray(sk2.key, k1.key));
	}

	public void testMatchingAndKeyGeneration_3Hosts_1Round() throws InternalApplicationException {
		CandidateKeyProtocol.CandidateKeyPartIdentifier i1[] = p1.generateCandidates(keyParts_round1_side1, 0);
		CandidateKeyProtocol.CandidateKeyPartIdentifier i2[] = p2.generateCandidates(keyParts_round1_side2, 0);
		CandidateKeyProtocol.CandidateKeyPartIdentifier i3[] = p3.generateCandidates(keyParts_round1_side3, 0);

		p1.matchCandidates(remoteIdentifier2, i2);
		p1.matchCandidates(remoteIdentifier3, i3);
		p2.matchCandidates(remoteIdentifier1, i1);
		p2.matchCandidates(remoteIdentifier3, i3);
		p3.matchCandidates(remoteIdentifier1, i1);
		p3.matchCandidates(remoteIdentifier2, i2);
		
		Assert.assertEquals(1, p1.getNumTotalMatches(remoteIdentifier2));
		Assert.assertEquals(1, p1.getNumTotalMatches(remoteIdentifier3));
		Assert.assertEquals(1, p2.getNumTotalMatches(remoteIdentifier1));
		Assert.assertEquals(1, p2.getNumTotalMatches(remoteIdentifier3));
		Assert.assertEquals(1, p3.getNumTotalMatches(remoteIdentifier1));
		Assert.assertEquals(1, p3.getNumTotalMatches(remoteIdentifier2));
		Assert.assertEquals(1, p1.getNumLocalRounds(remoteIdentifier2));
		Assert.assertEquals(1, p1.getNumLocalRounds(remoteIdentifier3));
		Assert.assertEquals(1, p2.getNumLocalRounds(remoteIdentifier1));
		Assert.assertEquals(1, p2.getNumLocalRounds(remoteIdentifier3));
		Assert.assertEquals(1, p3.getNumLocalRounds(remoteIdentifier1));
		Assert.assertEquals(1, p3.getNumLocalRounds(remoteIdentifier2));
		
		CandidateKeyProtocol.CandidateKey k1_2 = p1.generateKey(remoteIdentifier2);
		CandidateKeyProtocol.CandidateKey k1_3 = p1.generateKey(remoteIdentifier3);
		CandidateKeyProtocol.CandidateKey k2_1 = p2.generateKey(remoteIdentifier1);
		CandidateKeyProtocol.CandidateKey k2_3 = p2.generateKey(remoteIdentifier3);
		CandidateKeyProtocol.CandidateKey k3_1 = p3.generateKey(remoteIdentifier1);
		CandidateKeyProtocol.CandidateKey k3_2 = p3.generateKey(remoteIdentifier2);
		Assert.assertNotNull(k1_2);
		Assert.assertNotNull(k1_3);
		Assert.assertNotNull(k2_1);
		Assert.assertNotNull(k2_3);
		Assert.assertNotNull(k3_1);
		Assert.assertNotNull(k3_2);
		
		Assert.assertTrue("Generated keys do not match", SimpleKeyAgreementTest.compareByteArray(k1_2.hash, k2_1.hash));
		Assert.assertTrue("Generated keys do not match", SimpleKeyAgreementTest.compareByteArray(k1_2.key, k2_1.key));
		Assert.assertTrue("Generated keys do not match", SimpleKeyAgreementTest.compareByteArray(k1_3.hash, k3_1.hash));
		Assert.assertTrue("Generated keys do not match", SimpleKeyAgreementTest.compareByteArray(k1_3.key, k3_1.key));
		Assert.assertTrue("Generated keys do not match", SimpleKeyAgreementTest.compareByteArray(k2_3.hash, k3_2.hash));
		Assert.assertTrue("Generated keys do not match", SimpleKeyAgreementTest.compareByteArray(k2_3.key, k3_2.key));

		Assert.assertFalse("Generated keys should not match, but do", SimpleKeyAgreementTest.compareByteArray(k1_2.hash, k1_3.hash));
		Assert.assertFalse("Generated keys should not match, but do", SimpleKeyAgreementTest.compareByteArray(k1_2.key, k1_3.key));
		Assert.assertFalse("Generated keys should not match, but do", SimpleKeyAgreementTest.compareByteArray(k1_2.hash, k2_3.hash));
		Assert.assertFalse("Generated keys should not match, but do", SimpleKeyAgreementTest.compareByteArray(k1_2.key, k2_3.key));
		Assert.assertFalse("Generated keys should not match, but do", SimpleKeyAgreementTest.compareByteArray(k1_3.hash, k2_3.hash));
		Assert.assertFalse("Generated keys should not match, but do", SimpleKeyAgreementTest.compareByteArray(k1_3.key, k2_3.key));
		Assert.assertFalse("Generated keys should not match, but do", SimpleKeyAgreementTest.compareByteArray(k1_3.hash, k1_2.hash));
		Assert.assertFalse("Generated keys should not match, but do", SimpleKeyAgreementTest.compareByteArray(k1_3.key, k1_2.key));
		Assert.assertFalse("Generated keys should not match, but do", SimpleKeyAgreementTest.compareByteArray(k2_3.hash, k1_3.hash));
		Assert.assertFalse("Generated keys should not match, but do", SimpleKeyAgreementTest.compareByteArray(k2_3.key, k1_3.key));
		Assert.assertFalse("Generated keys should not match, but do", SimpleKeyAgreementTest.compareByteArray(k2_3.hash, k1_2.hash));
		Assert.assertFalse("Generated keys should not match, but do", SimpleKeyAgreementTest.compareByteArray(k2_3.key, k1_2.key));
	}

	public void testMatchingAcknowledgeAndKeyGeneration_Asymmetric_3Hosts_1Round() throws InternalApplicationException {
		p1.generateCandidates(keyParts_round1_side1, 0);
		CandidateKeyProtocol.CandidateKeyPartIdentifier i2[] = p2.generateCandidates(keyParts_round1_side2, 0);
		CandidateKeyProtocol.CandidateKeyPartIdentifier i3[] = p3.generateCandidates(keyParts_round1_side3, 0);

		int ind1_2 = p1.matchCandidates(remoteIdentifier2, i2);
		int ind1_3 = p1.matchCandidates(remoteIdentifier3, i3);
		p2.acknowledgeMatches(remoteIdentifier1, i2[0].round, ind1_2);
		p3.acknowledgeMatches(remoteIdentifier1, i3[0].round, ind1_3);

		Assert.assertEquals(1, p1.getNumTotalMatches(remoteIdentifier2));
		Assert.assertEquals(1, p1.getNumTotalMatches(remoteIdentifier3));
		Assert.assertEquals(1, p2.getNumTotalMatches(remoteIdentifier1));
		Assert.assertEquals(1, p3.getNumTotalMatches(remoteIdentifier1));
		Assert.assertEquals(0, p2.getNumTotalMatches(remoteIdentifier3));
		Assert.assertEquals(0, p3.getNumTotalMatches(remoteIdentifier2));
		Assert.assertEquals(1, p1.getNumLocalRounds(remoteIdentifier2));
		Assert.assertEquals(1, p1.getNumLocalRounds(remoteIdentifier3));
		Assert.assertEquals(1, p2.getNumLocalRounds(remoteIdentifier1));
		Assert.assertEquals(0, p2.getNumLocalRounds(remoteIdentifier3));
		Assert.assertEquals(1, p3.getNumLocalRounds(remoteIdentifier1));
		Assert.assertEquals(0, p3.getNumLocalRounds(remoteIdentifier2));
		
		CandidateKeyProtocol.CandidateKey k1_2 = p1.generateKey(remoteIdentifier2);
		CandidateKeyProtocol.CandidateKey k1_3 = p1.generateKey(remoteIdentifier3);
		CandidateKeyProtocol.CandidateKey k2_1 = p2.generateKey(remoteIdentifier1);
		CandidateKeyProtocol.CandidateKey k2_3 = p2.generateKey(remoteIdentifier3);
		CandidateKeyProtocol.CandidateKey k3_1 = p3.generateKey(remoteIdentifier1);
		CandidateKeyProtocol.CandidateKey k3_2 = p3.generateKey(remoteIdentifier2);
		Assert.assertNotNull(k1_2);
		Assert.assertNotNull(k1_3);
		Assert.assertNotNull(k2_1);
		Assert.assertNull(k2_3);
		Assert.assertNotNull(k3_1);
		Assert.assertNull(k3_2);
		
		Assert.assertTrue("Generated keys do not match", SimpleKeyAgreementTest.compareByteArray(k1_2.hash, k2_1.hash));
		Assert.assertTrue("Generated keys do not match", SimpleKeyAgreementTest.compareByteArray(k1_2.key, k2_1.key));
		Assert.assertTrue("Generated keys do not match", SimpleKeyAgreementTest.compareByteArray(k1_3.hash, k3_1.hash));
		Assert.assertTrue("Generated keys do not match", SimpleKeyAgreementTest.compareByteArray(k1_3.key, k3_1.key));

		Assert.assertFalse("Generated keys should not match, but do", SimpleKeyAgreementTest.compareByteArray(k1_2.hash, k1_3.hash));
		Assert.assertFalse("Generated keys should not match, but do", SimpleKeyAgreementTest.compareByteArray(k1_2.key, k1_3.key));
		Assert.assertFalse("Generated keys should not match, but do", SimpleKeyAgreementTest.compareByteArray(k2_1.hash, k3_1.hash));
		Assert.assertFalse("Generated keys should not match, but do", SimpleKeyAgreementTest.compareByteArray(k2_1.key, k3_1.key));
	}
	
	// this should actually be split into a helper class
	public void testSetExploder() throws InternalApplicationException {
		int[] set = {1, 2, 3, 4, 5};
		
		BitSet[] combinations = CandidateKeyProtocol.explodeKOutOfN(set, 4);
		Assert.assertNotNull(combinations);
		Assert.assertEquals(5, combinations.length);
		Assert.assertNotNull(combinations[0]);
		Assert.assertEquals(4, combinations[0].cardinality());
		Assert.assertTrue(combinations[0].get(1));
		Assert.assertTrue(combinations[0].get(2));
		Assert.assertTrue(combinations[0].get(3));
		Assert.assertTrue(combinations[0].get(4));
		Assert.assertNotNull(combinations[1]);
		Assert.assertEquals(4, combinations[1].cardinality());
		Assert.assertTrue(combinations[1].get(1));
		Assert.assertTrue(combinations[1].get(2));
		Assert.assertTrue(combinations[1].get(3));
		Assert.assertTrue(combinations[1].get(5));
		Assert.assertNotNull(combinations[2]);
		Assert.assertEquals(4, combinations[2].cardinality());
		Assert.assertTrue(combinations[2].get(1));
		Assert.assertTrue(combinations[2].get(2));
		Assert.assertTrue(combinations[2].get(4));
		Assert.assertTrue(combinations[2].get(5));
		Assert.assertNotNull(combinations[3]);
		Assert.assertEquals(4, combinations[3].cardinality());
		Assert.assertTrue(combinations[3].get(1));
		Assert.assertTrue(combinations[3].get(3));
		Assert.assertTrue(combinations[3].get(4));
		Assert.assertTrue(combinations[3].get(5));
		Assert.assertNotNull(combinations[1]);
		Assert.assertEquals(4, combinations[4].cardinality());
		Assert.assertTrue(combinations[4].get(2));
		Assert.assertTrue(combinations[4].get(3));
		Assert.assertTrue(combinations[4].get(4));
		Assert.assertTrue(combinations[4].get(5));

		combinations = CandidateKeyProtocol.explodeKOutOfN(set, 3);
		Assert.assertNotNull(combinations);
		Assert.assertEquals(10, combinations.length);
		Assert.assertNotNull(combinations[0]);
		Assert.assertEquals(3, combinations[0].cardinality());
		Assert.assertTrue(combinations[0].get(1));
		Assert.assertTrue(combinations[0].get(2));
		Assert.assertTrue(combinations[0].get(3));
		Assert.assertNotNull(combinations[1]);
		Assert.assertEquals(3, combinations[1].cardinality());
		Assert.assertTrue(combinations[1].get(1));
		Assert.assertTrue(combinations[1].get(2));
		Assert.assertTrue(combinations[1].get(4));
		Assert.assertNotNull(combinations[2]);
		Assert.assertEquals(3, combinations[2].cardinality());
		Assert.assertTrue(combinations[2].get(1));
		Assert.assertTrue(combinations[2].get(2));
		Assert.assertTrue(combinations[2].get(5));
		Assert.assertNotNull(combinations[3]);
		Assert.assertEquals(3, combinations[3].cardinality());
		Assert.assertTrue(combinations[3].get(1));
		Assert.assertTrue(combinations[3].get(3));
		Assert.assertTrue(combinations[3].get(4));
		Assert.assertNotNull(combinations[4]);
		Assert.assertEquals(3, combinations[4].cardinality());
		Assert.assertTrue(combinations[4].get(1));
		Assert.assertTrue(combinations[4].get(3));
		Assert.assertTrue(combinations[4].get(5));
		Assert.assertNotNull(combinations[5]);
		Assert.assertEquals(3, combinations[5].cardinality());
		Assert.assertTrue(combinations[5].get(1));
		Assert.assertTrue(combinations[5].get(4));
		Assert.assertTrue(combinations[5].get(5));
		Assert.assertNotNull(combinations[6]);
		Assert.assertEquals(3, combinations[6].cardinality());
		Assert.assertTrue(combinations[6].get(2));
		Assert.assertTrue(combinations[6].get(3));
		Assert.assertTrue(combinations[6].get(4));
		Assert.assertNotNull(combinations[7]);
		Assert.assertEquals(3, combinations[7].cardinality());
		Assert.assertTrue(combinations[7].get(2));
		Assert.assertTrue(combinations[7].get(3));
		Assert.assertTrue(combinations[7].get(5));
		Assert.assertNotNull(combinations[8]);
		Assert.assertEquals(3, combinations[8].cardinality());
		Assert.assertTrue(combinations[8].get(2));
		Assert.assertTrue(combinations[8].get(4));
		Assert.assertTrue(combinations[8].get(5));
		Assert.assertNotNull(combinations[9]);
		Assert.assertEquals(3, combinations[9].cardinality());
		Assert.assertTrue(combinations[9].get(3));
		Assert.assertTrue(combinations[9].get(4));
		Assert.assertTrue(combinations[9].get(5));
	}

	// this should actually be split into a helper class
	public void testSetExploder_FixedErrorCase1() throws InternalApplicationException {
		int[] set = new int[13];
		
		BitSet[] combinations = CandidateKeyProtocol.explodeKOutOfN(set, 10);
	}		
}
