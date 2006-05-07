/* Copyright Rene Mayrhofer
 * File created 2006-05-06
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.authentication.test;

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
	private CandidateKeyProtocol p2;
	byte[][] keyParts_round1_side1;
	byte[][] keyParts_round1_side2;
	byte[][] keyParts_round2_side1;
	byte[][] keyParts_round2_side2;
	
	public void setUp() {
		p1 = new CandidateKeyProtocol(10, 5, "p1", useJSSE1);
		p2 = new CandidateKeyProtocol(10, 5, "p2", useJSSE2);
		
		// 2 matches 1
		keyParts_round1_side1 = new byte[][] { 
				new byte[] {1, 2, 3, 4, 5, 4, 3, 2, 1},
				new byte[] {1, 2, 4, 4, 5, 4, 3, 2, 1},
				new byte[] {1, 2, 3, 4, 5, 4, 7, 2, 1},
				new byte[] {1, 2, 3, 4, 5, 4, 3, 2, 2} };
		keyParts_round1_side2 = new byte[][] { 
				new byte[] {2, 2, 3, 4, 5, 4, 3, 2, 1},
				new byte[] {1, 2, 3, 4, 5, 4, 7, 2, 1},
				new byte[] {2, 2, 4, 4, 5, 4, 3, 2, 1},
				new byte[] {2, 2, 3, 4, 5, 4, 3, 2, 2} };

		// 4 matches 1 and 2 matches 3
		keyParts_round2_side1 = new byte[][] { 
				new byte[] {5, 2, 3, 4, 5, 4, 3, 2, 3},
				new byte[] {4, 2, 4, 4, 5, 4, 3, 2, 3},
				new byte[] {3, 2, 3, 4, 5, 4, 7, 2, 3},
				new byte[] {2, 2, 3, 4, 5, 4, 7, 2, 3},
				new byte[] {1, 2, 3, 4, 5, 4, 3, 2, 4} };
		keyParts_round2_side2 = new byte[][] { 
				new byte[] {9, 2, 3, 4, 5, 4, 3, 2, 5},
				new byte[] {1, 2, 3, 4, 5, 4, 3, 2, 4},
				new byte[] {7, 2, 3, 4, 5, 4, 3, 2, 3},
				new byte[] {3, 2, 3, 4, 5, 4, 7, 2, 3},
				new byte[] {5, 2, 3, 4, 5, 4, 3, 2, 8} };
	}
	
	public void testMatching_1Round() throws InternalApplicationException {
		CandidateKeyProtocol.CandidateKeyPartIdentifier i1[] = p1.generateCandidates(keyParts_round1_side1, 0);
		CandidateKeyProtocol.CandidateKeyPartIdentifier i2[] = p2.generateCandidates(keyParts_round1_side2, 0);
		
		int ind1 = p1.matchCandidates(i2);
		int ind2 = p2.matchCandidates(i1);
		Assert.assertEquals("Match did not return correct index", 1, ind1);
		Assert.assertEquals("Match did not return correct index", 2, ind2);
	}

	public void testMatching_2Rounds() throws InternalApplicationException {
		CandidateKeyProtocol.CandidateKeyPartIdentifier i1_1[] = p1.generateCandidates(keyParts_round1_side1, 0);
		CandidateKeyProtocol.CandidateKeyPartIdentifier i2_1[] = p2.generateCandidates(keyParts_round1_side2, 0);
		
		CandidateKeyProtocol.CandidateKeyPartIdentifier i1_2[] = p1.generateCandidates(keyParts_round2_side1, 0);
		int ind2_1 = p2.matchCandidates(i1_1);
		int ind2_2 = p2.matchCandidates(i1_2);
		Assert.assertEquals("Match did not return correct index", 2, ind2_1);
		Assert.assertEquals("Match did not return correct index", -1, ind2_2);

		CandidateKeyProtocol.CandidateKeyPartIdentifier i2_2[] = p2.generateCandidates(keyParts_round2_side2, 0);
		int ind1_1 = p1.matchCandidates(i2_1);
		int ind1_2 = p1.matchCandidates(i2_2);
		ind2_1 = p2.matchCandidates(i1_1);
		ind2_2 = p2.matchCandidates(i1_2);
		Assert.assertEquals("Match did not return correct index", 2, ind2_1);
		Assert.assertEquals("Match did not return correct index", 2, ind2_2);
		Assert.assertEquals("Match did not return correct index", 1, ind1_1);
		Assert.assertEquals("Match did not return correct index", 1, ind1_2);
	}
	
	public void testMatchingAndKeyGeneration_1Round() throws InternalApplicationException {
		CandidateKeyProtocol.CandidateKeyPartIdentifier i1[] = p1.generateCandidates(keyParts_round1_side1, 0);
		CandidateKeyProtocol.CandidateKeyPartIdentifier i2[] = p2.generateCandidates(keyParts_round1_side2, 0);

		p1.matchCandidates(i2);
		p2.matchCandidates(i1);
		
		CandidateKeyProtocol.CandidateKey k1 = p1.generateKey();
		CandidateKeyProtocol.CandidateKey k2 = p2.generateKey();
		Assert.assertNotNull(k1);
		Assert.assertNotNull(k2);
		
		Assert.assertTrue("Generated keys do not match", SimpleKeyAgreementTest.compareByteArray(k1.hash, k2.hash));
		Assert.assertTrue("Generated keys do not match", SimpleKeyAgreementTest.compareByteArray(k1.key, k2.key));
	}

	public void testMatchingAndKeyGeneration_2Rounds() throws InternalApplicationException {
		CandidateKeyProtocol.CandidateKeyPartIdentifier i1_1[] = p1.generateCandidates(keyParts_round1_side1, 0);
		CandidateKeyProtocol.CandidateKeyPartIdentifier i2_1[] = p2.generateCandidates(keyParts_round2_side1, 0);
		CandidateKeyProtocol.CandidateKeyPartIdentifier i1_2[] = p1.generateCandidates(keyParts_round1_side2, 0);
		CandidateKeyProtocol.CandidateKeyPartIdentifier i2_2[] = p2.generateCandidates(keyParts_round2_side2, 0);

		p1.matchCandidates(i2_1);
		p2.matchCandidates(i1_1);
		p1.matchCandidates(i2_2);
		p2.matchCandidates(i1_2);
		
		CandidateKeyProtocol.CandidateKey k1 = p1.generateKey();
		CandidateKeyProtocol.CandidateKey k2 = p2.generateKey();
		Assert.assertNotNull(k1);
		Assert.assertNotNull(k2);
		
		Assert.assertTrue("Generated keys do not match", SimpleKeyAgreementTest.compareByteArray(k1.hash, k2.hash));
		Assert.assertTrue("Generated keys do not match", SimpleKeyAgreementTest.compareByteArray(k1.key, k2.key));
	}
	
	public void testMatchingAcknowledgeAndKeyGeneration_Asymmetric_1Round() throws InternalApplicationException {
		p1.generateCandidates(keyParts_round1_side1, 0);
		CandidateKeyProtocol.CandidateKeyPartIdentifier i2[] = p2.generateCandidates(keyParts_round1_side2, 0);

		int ind1 = p1.matchCandidates(i2);
		p2.acknowledgeMatches(i2[0].round, ind1);

		CandidateKeyProtocol.CandidateKey k1 = p1.generateKey();
		CandidateKeyProtocol.CandidateKey k2 = p2.generateKey();
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

		int ind1_1 = p1.matchCandidates(i2_1);
		p2.acknowledgeMatches(i2_1[0].round, ind1_1);
		int ind1_2 = p1.matchCandidates(i2_2);
		p2.acknowledgeMatches(i2_2[0].round, ind1_2);

		CandidateKeyProtocol.CandidateKey k1 = p1.generateKey();
		CandidateKeyProtocol.CandidateKey k2 = p2.generateKey();
		Assert.assertNotNull(k1);
		Assert.assertNotNull(k2);
		
		Assert.assertTrue("Generated keys do not match", SimpleKeyAgreementTest.compareByteArray(k1.hash, k2.hash));
		Assert.assertTrue("Generated keys do not match", SimpleKeyAgreementTest.compareByteArray(k1.key, k2.key));
	}
}
