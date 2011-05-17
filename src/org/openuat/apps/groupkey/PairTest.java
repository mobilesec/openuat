/* Copyright Lukas Wallentin
 * File created 2009-01-12
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package org.openuat.apps.groupkey;

import java.util.Arrays;


import junit.framework.Assert;
import junit.framework.TestCase;

/**
 * @author Lukas Wallentin
 *
 */
public class PairTest extends TestCase {
	private Pair target;
	byte[] key1 = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
	//byte[] key2 = new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 0};
	

	/**
	 * @param name
	 */
	public PairTest(String name) {
		super(name);
		target = new Pair();
	}

	/* (non-Javadoc)
	 * @see junit.framework.TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		super.setUp();
		target.setName("Testunit");
		target.setKeyPair("ni", key1);		
	}

	/* (non-Javadoc)
	 * @see junit.framework.TestCase#tearDown()
	 */
	protected void tearDown() throws Exception {
		super.tearDown();
	}

	/**
	 * Test method for {@link org.openuat.apps.groupkey.Pair#getKey(java.lang.String)}.
	 */
	public void testGetKey() {
		// TODO
		//fail("Not yet implemented");
		Assert.assertTrue(Arrays.equals(key1,target.getKey("ni")));
		Assert.assertFalse(Arrays.equals(key1,target.getKey("foo")));
	}
}
