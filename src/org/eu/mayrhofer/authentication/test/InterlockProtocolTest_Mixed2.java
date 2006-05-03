/* Copyright Rene Mayrhofer
 * File created 2006-05-03
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.authentication.test;

public class InterlockProtocolTest_Mixed2 extends InterlockProtocolTest {
	public InterlockProtocolTest_Mixed2(String s) {
		super(s);
		this.useJSSE = false;
		this.useJSSE2 = true;
	}
}
