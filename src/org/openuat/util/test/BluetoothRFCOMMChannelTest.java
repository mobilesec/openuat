/* Copyright Rene Mayrhofer
 * File created 2007-11-10
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.util.test;

import java.io.IOException;

import org.openuat.util.BluetoothRFCOMMChannel;
import org.openuat.util.BluetoothSupport;

import junit.framework.Assert;
import junit.framework.TestCase;

public class BluetoothRFCOMMChannelTest extends TestCase {
	private boolean haveBTSupport;

	@Override
	public void setUp() {
		try {
			haveBTSupport = BluetoothSupport.init();
		} catch (UnsatisfiedLinkError e) {
			System.err.println("Unable to load native Bluetooth support: " + e.toString());
			System.err.println("Skipping Bluetooth tests");
		}
	}

	public void testClosedOnInitWithAddress() throws IOException {
		if (!haveBTSupport)
			// no point in even trying
			return;
		
		BluetoothRFCOMMChannel c1 = new BluetoothRFCOMMChannel("001122334455", -1);

		Assert.assertFalse("BluetoothRFCOMMChannel claims to be open although not explicitly requested so", 
				c1.isOpen());
	}

	public void testClosedOnInitWithAddressAndChannel() throws IOException {
		if (!haveBTSupport) return;

		BluetoothRFCOMMChannel c1 = new BluetoothRFCOMMChannel("001122334455", 1);

		Assert.assertFalse("BluetoothRFCOMMChannel claims to be open although not explicitly requested so", 
				c1.isOpen());
	}

	public void testClosedOnInitWithURL() throws IOException {
		if (!haveBTSupport) return;

		BluetoothRFCOMMChannel c1 = new BluetoothRFCOMMChannel("btspp://001122334455:1");

		Assert.assertFalse("BluetoothRFCOMMChannel claims to be open although not explicitly requested so", 
				c1.isOpen());
	}

	public void testEqualsAddress() throws IOException {
		if (!haveBTSupport) return;

		BluetoothRFCOMMChannel c1 = new BluetoothRFCOMMChannel("001122334455", -1);
		BluetoothRFCOMMChannel c2 = new BluetoothRFCOMMChannel("001122334455", -1);

		Assert.assertTrue("BluetoothRFCOMMChannel objects claim to be different although their addresses are equal", 
				c1.equals(c2));
		Assert.assertTrue("BluetoothRFCOMMChannel objects claim to be different although their addresses are equal", 
				c2.equals(c1));
	}

	public void testNotEqualsAddress() throws IOException {
		if (!haveBTSupport) return;

		BluetoothRFCOMMChannel c1 = new BluetoothRFCOMMChannel("001122334455", -1);
		BluetoothRFCOMMChannel c2 = new BluetoothRFCOMMChannel("001122334466", -1);

		Assert.assertFalse("BluetoothRFCOMMChannel objects claim to be equal although their addresses are different", 
				c1.equals(c2));
		Assert.assertFalse("BluetoothRFCOMMChannel objects claim to be euqal although their addresses are different", 
				c2.equals(c1));
	}

	public void testEqualsServiceURL() throws IOException {
		if (!haveBTSupport) return;

		BluetoothRFCOMMChannel c1 = new BluetoothRFCOMMChannel("btspp://001122334455:1");
		BluetoothRFCOMMChannel c2 = new BluetoothRFCOMMChannel("btspp://001122334455:1");

		Assert.assertTrue("BluetoothRFCOMMChannel objects claim to be different although their addresses are equal", 
				c1.equals(c2));
		Assert.assertTrue("BluetoothRFCOMMChannel objects claim to be different although their addresses are equal", 
				c2.equals(c1));
	}

	public void testNotEqualsServiceURL1() throws IOException {
		if (!haveBTSupport) return;

		BluetoothRFCOMMChannel c1 = new BluetoothRFCOMMChannel("btspp://001122334455:1");
		BluetoothRFCOMMChannel c2 = new BluetoothRFCOMMChannel("btspp://001122334455:2");

		Assert.assertFalse("BluetoothRFCOMMChannel objects claim to be equal although their addresses are different", 
				c1.equals(c2));
		Assert.assertFalse("BluetoothRFCOMMChannel objects claim to be euqal although their addresses are different", 
				c2.equals(c1));
	}

	public void testNotEqualsServiceURL2() throws IOException {
		if (!haveBTSupport) return;

		BluetoothRFCOMMChannel c1 = new BluetoothRFCOMMChannel("btspp://001122334455:1");
		BluetoothRFCOMMChannel c2 = new BluetoothRFCOMMChannel("btspp://001122334466:1");

		Assert.assertFalse("BluetoothRFCOMMChannel objects claim to be equal although their addresses are different", 
				c1.equals(c2));
		Assert.assertFalse("BluetoothRFCOMMChannel objects claim to be euqal although their addresses are different", 
				c2.equals(c1));
	}

	public void testEqualsServiceURLAndAddrChannel() throws IOException {
		if (!haveBTSupport) return;

		BluetoothRFCOMMChannel c1 = new BluetoothRFCOMMChannel("btspp://001122334455:1");
		BluetoothRFCOMMChannel c2 = new BluetoothRFCOMMChannel("001122334455", 1);

		Assert.assertTrue("BluetoothRFCOMMChannel objects claim to be different although their addresses are equal", 
				c1.equals(c2));
		Assert.assertTrue("BluetoothRFCOMMChannel objects claim to be different although their addresses are equal", 
				c2.equals(c1));
	}

	public void testNotEqualsServiceURLAndAddrChannel1() throws IOException {
		if (!haveBTSupport) return;

		BluetoothRFCOMMChannel c1 = new BluetoothRFCOMMChannel("btspp://001122334455:1");
		BluetoothRFCOMMChannel c2 = new BluetoothRFCOMMChannel("001122334455", 2);

		Assert.assertFalse("BluetoothRFCOMMChannel objects claim to be equal although their addresses are different", 
				c1.equals(c2));
		Assert.assertFalse("BluetoothRFCOMMChannel objects claim to be euqal although their addresses are different", 
				c2.equals(c1));
	}

	public void testNotEqualsServiceURLAndAddrChannel2() throws IOException {
		if (!haveBTSupport) return;

		BluetoothRFCOMMChannel c1 = new BluetoothRFCOMMChannel("btspp://001122334455:1");
		BluetoothRFCOMMChannel c2 = new BluetoothRFCOMMChannel("001122334466", 1);

		Assert.assertFalse("BluetoothRFCOMMChannel objects claim to be equal although their addresses are different", 
				c1.equals(c2));
		Assert.assertFalse("BluetoothRFCOMMChannel objects claim to be euqal although their addresses are different", 
				c2.equals(c1));
	}

	public void testEqualsHashcode1() throws IOException {
		if (!haveBTSupport) return;

		BluetoothRFCOMMChannel c1 = new BluetoothRFCOMMChannel("001122334455", -1);
		BluetoothRFCOMMChannel c2 = new BluetoothRFCOMMChannel("001122334455", -1);

		Assert.assertTrue("BluetoothRFCOMMChannel objects hash codes are different although their addresses are equal", 
				c1.hashCode() == c2.hashCode());
	}

	public void testEqualsHashcode2() throws IOException {
		if (!haveBTSupport) return;

		BluetoothRFCOMMChannel c1 = new BluetoothRFCOMMChannel("001122334455", 1);
		BluetoothRFCOMMChannel c2 = new BluetoothRFCOMMChannel("001122334455", -1);

		Assert.assertTrue("BluetoothRFCOMMChannel objects hash codes are different although their addresses are equal", 
				c1.hashCode() == c2.hashCode());
	}

	public void testEqualsHashcode3() throws IOException {
		if (!haveBTSupport) return;

		BluetoothRFCOMMChannel c1 = new BluetoothRFCOMMChannel("001122334455", 2);
		BluetoothRFCOMMChannel c2 = new BluetoothRFCOMMChannel("001122334455", -1);

		Assert.assertTrue("BluetoothRFCOMMChannel objects hash codes are different although their addresses are equal", 
				c1.hashCode() == c2.hashCode());
	}

	public void testEqualsHashcode4() throws IOException {
		if (!haveBTSupport) return;

		BluetoothRFCOMMChannel c1 = new BluetoothRFCOMMChannel("btspp://001122334455:1");
		BluetoothRFCOMMChannel c2 = new BluetoothRFCOMMChannel("001122334455", -1);

		Assert.assertTrue("BluetoothRFCOMMChannel objects hash codes are different although their addresses are equal", 
				c1.hashCode() == c2.hashCode());
	}

	public void testEqualsHashcode5() throws IOException {
		if (!haveBTSupport) return;
		
		BluetoothRFCOMMChannel c1 = new BluetoothRFCOMMChannel("001122334455", -1);
		BluetoothRFCOMMChannel c2 = new BluetoothRFCOMMChannel("btspp://001122334455:-1;authenticate=false;master=true;encrypt=false");

		Assert.assertTrue("BluetoothRFCOMMChannel objects hash codes are different although their addresses are equal", 
				c1.hashCode() == c2.hashCode());
	}
}
