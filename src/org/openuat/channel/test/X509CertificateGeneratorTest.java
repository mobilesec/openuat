/* Copyright Rene Mayrhofer
 * File created 2006-03-23
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.channel.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;

import org.bouncycastle.crypto.CryptoException;
import org.bouncycastle.crypto.DataLengthException;
import org.openuat.channel.X509CertificateGenerator;

import junit.framework.*;

public class X509CertificateGeneratorTest extends TestCase {

	protected boolean useBCAPI = false;

	// TODO: activate me again when J2ME polish can deal with Java5 sources!
	//@Override
	protected void setUp() throws Exception {
		super.setUp();
	}

	// TODO: activate me again when J2ME polish can deal with Java5 sources!
	//@Override
	protected void tearDown() throws Exception {
		super.tearDown();
	}

	public void testConstructorParameterCheck1() throws IOException, InvalidKeyException, NoSuchAlgorithmException, CertificateException, NoSuchProviderException, SignatureException {
		try {
			// TODO: activate me again when J2ME polish can deal with Java5 sources!
			//@SuppressWarnings("unused")
			X509CertificateGenerator g = new X509CertificateGenerator(null, "", "", useBCAPI);
			Assert.fail("Invalid parameter was not rejected by constructor");
		}
		catch (IllegalArgumentException e) {
			Assert.assertTrue(true);
		}
	}
	public void testConstructorParameterCheck2() throws IOException, InvalidKeyException, NoSuchAlgorithmException, CertificateException, NoSuchProviderException, SignatureException {
		try {
			// TODO: activate me again when J2ME polish can deal with Java5 sources!
			//@SuppressWarnings("unused")
			X509CertificateGenerator g = new X509CertificateGenerator("", null, "", useBCAPI);
			Assert.fail("Invalid parameter was not rejected by constructor");
		}
		catch (IllegalArgumentException e) {
			Assert.assertTrue(true);
		}
	}
	public void testConstructorParameterCheck3() throws IOException, InvalidKeyException, NoSuchAlgorithmException, CertificateException, NoSuchProviderException, SignatureException {
		try {
			// TODO: activate me again when J2ME polish can deal with Java5 sources!
			//@SuppressWarnings("unused")
			X509CertificateGenerator g = new X509CertificateGenerator("", "", null, useBCAPI);
			Assert.fail("Invalid parameter was not rejected by constructor");
		}
		catch (IllegalArgumentException e) {
			Assert.assertTrue(true);
		}
	}

	// Can't execute the constructor without a valid CA...
	/*public void testCreateCertificateParameterCheck1() throws IOException, InvalidKeyException, KeyStoreException, NoSuchAlgorithmException, CertificateException, UnrecoverableKeyException, NoSuchProviderException, SignatureException, DataLengthException, SecurityException, InvalidKeySpecException, CryptoException {
		try {
			X509CertificateGenerator g = new X509CertificateGenerator("", "", "", useBCAPI);
			g.createCertificate(null, 1, "", "");
			Assert.fail("Invalid parameter was not rejected by createCertificate");
		}
		catch (IllegalArgumentException e) {
			Assert.assertTrue(true);
		}
	}
	public void testCreateCertificateParameterCheck2() throws IOException, InvalidKeyException, KeyStoreException, NoSuchAlgorithmException, CertificateException, UnrecoverableKeyException, NoSuchProviderException, SignatureException, DataLengthException, SecurityException, InvalidKeySpecException, CryptoException {
		try {
			X509CertificateGenerator g = new X509CertificateGenerator("", "", "", useBCAPI);
			g.createCertificate("", 0, "", "");
			Assert.fail("Invalid parameter was not rejected by createCertificate");
		}
		catch (IllegalArgumentException e) {
			Assert.assertTrue(true);
		}
	}
	public void testCreateCertificateParameterCheck3() throws IOException, InvalidKeyException, KeyStoreException, NoSuchAlgorithmException, CertificateException, UnrecoverableKeyException, NoSuchProviderException, SignatureException, DataLengthException, SecurityException, InvalidKeySpecException, CryptoException {
		try {
			X509CertificateGenerator g = new X509CertificateGenerator("", "", "", useBCAPI);
			g.createCertificate("", 1, null, "");
			Assert.fail("Invalid parameter was not rejected by createCertificate");
		}
		catch (IllegalArgumentException e) {
			Assert.assertTrue(true);
		}
	}
	public void testCreateCertificateParameterCheck4() throws IOException, InvalidKeyException, KeyStoreException, NoSuchAlgorithmException, CertificateException, UnrecoverableKeyException, NoSuchProviderException, SignatureException, DataLengthException, SecurityException, InvalidKeySpecException, CryptoException {
		try {
			X509CertificateGenerator g = new X509CertificateGenerator("", "", "", useBCAPI);
			g.createCertificate("", 1, "", null);
			Assert.fail("Invalid parameter was not rejected by createCertificate");
		}
		catch (IllegalArgumentException e) {
			Assert.assertTrue(true);
		}
	}*/
	
	// this is THE test
	public void testCreateCaAndCertificate() throws InvalidKeyException, KeyStoreException, NoSuchAlgorithmException, CertificateException, FileNotFoundException, NoSuchProviderException, SignatureException, IOException, DataLengthException, SecurityException, InvalidKeySpecException, CryptoException {
		// first a new CA
		File tempCa = File.createTempFile("testCA-", ".p12");
		tempCa.deleteOnExit();
		String caExportPw = "my test export password for the CA";
		String caExportAlias = "my test alias for the CA export";
		Assert.assertTrue("Could not generate new CA", 
				X509CertificateGenerator.createNewCa("My new test CA", 90, tempCa.getAbsolutePath(), 
						caExportPw, caExportAlias, useBCAPI));
		
		// then with the CA the certificate
		X509CertificateGenerator g = new X509CertificateGenerator(tempCa.getAbsolutePath(), 
						caExportPw, caExportAlias, useBCAPI);
		File tempCert = File.createTempFile("testCert-", ".p12");
		tempCert.deleteOnExit();
		String certExportPw = "test";
		Assert.assertTrue("Could not generate new certificate", g.createCertificate("My test CN", 30, tempCert.getAbsolutePath(), certExportPw));

		// but we also verify (always with JCE to test interoperability)...
		KeyStore caKs = KeyStore.getInstance("PKCS12");
		caKs.load(new FileInputStream(tempCa), caExportPw.toCharArray());
		X509Certificate caCert = (X509Certificate) caKs.getCertificate(caExportAlias);
		Assert.assertNotNull("Could not load certificate from new CA file", caCert);
		// self-verify the CA-cert
		caCert.verify(caCert.getPublicKey());

		KeyStore certKs = KeyStore.getInstance("PKCS12");
		certKs.load(new FileInputStream(tempCert), certExportPw.toCharArray());
		// OK, we just need to know that because the KeyStore API doesn't allow introspection of PKCS12 files....
		X509Certificate newCert = (X509Certificate) certKs.getCertificate(X509CertificateGenerator.KeyExportFriendlyName);
		Assert.assertNotNull("Could not load certificate from new cert file", newCert);
		// and verify the cert with the CA
		newCert.verify(caCert.getPublicKey());
		
		// and finally load the generated PKCS12 file and convert to PEM
		File tempCertPem = File.createTempFile("testCert-", ".pem");
		File tempCaCertPem = File.createTempFile("testCaCert-", ".pem");
		File tempKeyPem = File.createTempFile("testKey-", ".pem");
		// TODO: activate me when it's implemented
		/*Assert.assertTrue(X509CertificateGenerator.convertPKCS12toPEM(tempCert.getAbsolutePath(), certExportPw, 
				tempCertPem.getAbsolutePath(), tempKeyPem.getAbsolutePath(), tempCaCertPem.getAbsolutePath(), 
				useBCAPI));*/
		// TODO: check the PEM format certificates, e.g. with openssl
	}
}
