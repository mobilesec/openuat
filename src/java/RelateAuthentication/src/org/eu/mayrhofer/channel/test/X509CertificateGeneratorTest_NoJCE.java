/* Copyright Rene Mayrhofer
 * File created 2006-03-24
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.channel.test;

public class X509CertificateGeneratorTest_NoJCE extends X509CertificateGeneratorTest {
	public X509CertificateGeneratorTest_NoJCE() {
		super();
		this.useBCAPI = true;
	}
}
