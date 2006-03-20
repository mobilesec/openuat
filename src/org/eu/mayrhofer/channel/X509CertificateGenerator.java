/* Copyright Rene Mayrhofer
 * 
 * This file may be copied under the terms of the GNU GPL version 2.
 */ 

package org.eu.mayrhofer.channel;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Calendar;
import java.util.Date;
import java.util.Hashtable;
import java.util.Vector;

import org.apache.commons.codec.binary.Hex;
import org.apache.log4j.Logger;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERBMPString;
import org.bouncycastle.asn1.DERBitString;
import org.bouncycastle.asn1.DERInteger;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.DERObjectIdentifier;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DEROutputStream;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.DigestInfo;
import org.bouncycastle.asn1.x509.RSAPublicKeyStructure;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.x509.TBSCertificateStructure;
import org.bouncycastle.asn1.x509.Time;
import org.bouncycastle.asn1.x509.V3TBSCertificateGenerator;
import org.bouncycastle.asn1.x509.X509CertificateStructure;
import org.bouncycastle.asn1.x509.X509Extension;
import org.bouncycastle.asn1.x509.X509Extensions;
import org.bouncycastle.asn1.x509.X509Name;
import org.bouncycastle.asn1.x509.X509ObjectIdentifiers;
import org.bouncycastle.crypto.AsymmetricBlockCipher;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.CryptoException;
import org.bouncycastle.crypto.DataLengthException;
import org.bouncycastle.crypto.digests.SHA1Digest;
import org.bouncycastle.crypto.encodings.PKCS1Encoding;
import org.bouncycastle.crypto.engines.RSAEngine;
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator;
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.bouncycastle.crypto.params.RSAPrivateCrtKeyParameters;
import org.bouncycastle.jce.PrincipalUtil;
import org.bouncycastle.jce.interfaces.PKCS12BagAttributeCarrier;
import org.bouncycastle.jce.provider.X509CertificateObject;
import org.bouncycastle.x509.X509Util;
import org.bouncycastle.x509.extension.AuthorityKeyIdentifierStructure;
import org.bouncycastle.x509.extension.SubjectKeyIdentifierStructure;

/** This class uses the Bouncycastle lightweight API to generate X.509 certificates programmatically.
 * It assumes a CA certificate and its private key to be available and can sign the new certificate with
 * this CA. Some of the code for this class was taken from 
 * org.bouncycastle.x509.X509V3CertificateGenerator, but adapted to work with the lightweight API instead of
 * JCE (which is usually not available on MIDP2.0). 
 * 
 * @author Rene Mayrhofer
 */
public class X509CertificateGenerator {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(X509CertificateGenerator.class);
	
	/** This method is used for signing the certificate. */
	public static final String CertificateSignatureAlgorithm = "SHA1WithRSAEncryption";
	/** This string is used as the friendly name for the certificate in the PKCS12 exported file. */
	public static final String CertificateExportFriendlyName = "Certificate for IPSec WLAN access";
	/** This string is used as the friendly name for the private key in the PKCS12 exported file. */
	public static final String KeyExportFriendlyName = "Private key for IPSec WLAN access"; 
	
	/** This holds the certificate of the CA used to sign the new certificate. The object is created in the constructor. */
	private X509Certificate caCert;
	/** This holds the private key of the CA used to sign the new certificate. The object is created in the constructor. */
	private RSAPrivateCrtKeyParameters caPrivateKey;
	/** Used to remember the value of useBCAPI as passed to the constructor. */
	private boolean useBCAPI;
	
	/** Creates a new self-signed CA (certificate authority) for subsequently signing certificates. 
	 * 
	 * @param commonName The common name (CN) field of the X.509 distinguished name that should be set
	 *                   for the new certificate. All other fields of the distinguished name are not set.
	 * @param validityDays How long the new certificate should be valid, in days.
	 * @param caFile The PKCS12 encoded file to which the CA should be exported to. It will contain the
	 *               self-signed certificate and the matching private key.
	 * @param caPassword The password used to encode the PKCS12 file.
	 * @param caAlias The friendly name of the CA in the PKCS12 file.
	 * @param useBCAPI Set to true if the Bouncycastle lightweight API should be used for cryptographical
	 *                 operations. If set to false, the JCE infrastructure with the configured default provider
	 *                 will be used. JCE may be faster depending on the provider implementation, but it might
	 *                 not be available on embedded platforms, i.e. J2ME.
	 * @return true if the CA could be created, self-signed, and exported successfully, false otherwise.
	 */
	public static boolean createNewCa(String commonName, int validityDays, 
			String caFile, String caPassword, String caAlias, boolean useBCAPI) {
		// TODO: implement me using constructor (null, null, null, useBCAPI) and createCertificate(....)
		return false;
	}
	
	/** Initializes the object for creating certificates by loading the CA certificate and private key. 
	 * 
	 * A new CA can be created with:
	 * 
	 * Comment out basicConstraints in /etc/ssl/openssl.cnf (CA:FALSE should not be set, but it does not need to be set to true)
	 * /usr/lib/ssl/misc/CA.sh -newca
	 * openssl pkcs12 -export -in demoCA/cacert.pem -inkey demoCA/private/cakey.pem -out ca.p12 -name "Test CA"
	 *
	 * @param caFile The PKCS12 encoded file containing the whole CA to use. It must contain the CA certificate
	 *               (which will be self-signed for top-level CAs) and the matching private key.
	 * @param caPassword The password necessary to decode the PKCS12 file.
	 * @param caAlias The friendly name of the CA in the PKCS12 file.
	 * @param useBCAPI Set to true if the Bouncycastle lightweight API should be used for cryptographical
	 *                 operations. If set to false, the JCE infrastructure with the configured default provider
	 *                 will be used. JCE may be faster depending on the provider implementation, but it might
	 *                 not be available on embedded platforms, i.e. J2ME.
	 */
	public X509CertificateGenerator(String caFile, String caPassword, String caAlias, boolean useBCAPI) 
			throws KeyStoreException, NoSuchAlgorithmException, CertificateException, FileNotFoundException, IOException, UnrecoverableKeyException, InvalidKeyException, NoSuchProviderException, SignatureException {
		this.useBCAPI = useBCAPI;
		
		logger.info("Loading CA certificate and private key from file '" + caFile + "', using alias '" + caAlias + "' with "
				+ (this.useBCAPI ? "Bouncycastle lightweight API" : "JCE API"));
		KeyStore caKs = KeyStore.getInstance("PKCS12");
		caKs.load(new FileInputStream(new File(caFile)), caPassword.toCharArray());
		
		// load the key entry from the keystore
		Key key = caKs.getKey(caAlias, caPassword.toCharArray());
		if (key == null) {
			throw new RuntimeException("Got null key from keystore!"); 
		}
		System.out.println(key.getClass());
		RSAPrivateCrtKey privKey = (RSAPrivateCrtKey) key;
		caPrivateKey = new RSAPrivateCrtKeyParameters(privKey.getModulus(), privKey.getPublicExponent(), privKey.getPrivateExponent(),
				privKey.getPrimeP(), privKey.getPrimeQ(), privKey.getPrimeExponentP(), privKey.getPrimeExponentQ(), privKey.getCrtCoefficient());
		// and get the certificate
		caCert = (X509Certificate) caKs.getCertificate(caAlias);
		if (caCert == null) {
			throw new RuntimeException("Got null cert from keystore!"); 
		}
		logger.debug("Successfully loaded CA key and certificate. CA DN is '" + caCert.getSubjectDN().getName() + "'");
		caCert.verify(caCert.getPublicKey());
		logger.debug("Successfully verified CA certificate with its own public key.");
	}
	
	/** This method should create something similar to:
	 *
	 * openssl req -new -outform PEM -newkey rsa:1024 -nodes -keyout /tmp/test.key -keyform PEM -out /tmp/test.pem -days 30 -config /etc/ssl/openssl.cnf
	 * openssl ca -policy policy_anything -out /tmp/test.crt -config /etc/ssl/openssl.cnf -infiles  /tmp/test.pem
	 * openssl pkcs12 -export -in /tmp/test.crt -inkey /tmp/test.key -certfile demoCA/cacert.pem -out test.p12
	 * 
	 * @param commonName The common name (CN) field of the X.509 distinguished name that should be set
	 *                   for the new certificate. All other fields of the distinguished name are not set.
	 * @param validityDays How long the new certificate should be valid, in days.
	 * @param exportFile The PKCS12 encoded file to which the certificate should be exported to. It will contain the
	 *               self-signed certificate and the matching private key.
	 * @param export Password The password used to encode the PKCS12 file.
	 * @return true if the certificate could be created, signed, and exported successfully, false otherwise.
	 */
	public boolean createCertificate(String commonName, int validityDays, String exportFile, String exportPassword) throws 
			IOException, InvalidKeyException, SecurityException, SignatureException, NoSuchAlgorithmException, DataLengthException, CryptoException, KeyStoreException, NoSuchProviderException, CertificateException, InvalidKeySpecException {
		logger.info("Generating certificate for distinguished common subject name '" + 
				commonName + "', valid for " + validityDays + " days");
		SecureRandom sr = new SecureRandom();
		
		PublicKey pubKey;
		PrivateKey privKey;
		
		logger.debug("Creating RSA keypair");
		// generate the keypair for the new certificate
		if (!useBCAPI) {
			RSAKeyPairGenerator gen = new RSAKeyPairGenerator();
			// TODO: what are these values??
			gen.init(new RSAKeyGenerationParameters(BigInteger.valueOf(3), sr, 1024, 80));
			AsymmetricCipherKeyPair keypair = gen.generateKeyPair();
			logger.debug("Generated keypair, extracting components and creating public structure for certificate");
			RSAKeyParameters publicKey = (RSAKeyParameters) keypair.getPublic();
			RSAPrivateCrtKeyParameters privateKey = (RSAPrivateCrtKeyParameters) keypair.getPrivate();
			// used to get proper encoding for the certificate
			RSAPublicKeyStructure pkStruct = new RSAPublicKeyStructure(publicKey.getModulus(), publicKey.getExponent());
			logger.debug("New public key is '" + new String(Hex.encodeHex(pkStruct.getEncoded())) + 
					", exponent=" + publicKey.getExponent() + ", modulus=" + publicKey.getModulus());
			// TODO: these two lines should go away
			// JCE format needed for the certificate - because getEncoded() is necessary...
	        pubKey = KeyFactory.getInstance("RSA").generatePublic(
	        		new RSAPublicKeySpec(publicKey.getModulus(), publicKey.getExponent()));
	        // and this one for the KeyStore
	        privKey = KeyFactory.getInstance("RSA").generatePrivate(
	        		new RSAPrivateCrtKeySpec(publicKey.getModulus(), publicKey.getExponent(),
	        				privateKey.getExponent(), privateKey.getP(), privateKey.getQ(), 
	        				privateKey.getDP(), privateKey.getDQ(), privateKey.getQInv()));
		}
		else {
			// this is the JSSE way of key generation
			KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
			keyGen.initialize(1024, sr);
			KeyPair keypair = keyGen.generateKeyPair();
			privKey = keypair.getPrivate();
			pubKey = keypair.getPublic();
		}
	    
		Calendar expiry = Calendar.getInstance();
		expiry.add(Calendar.DAY_OF_YEAR, validityDays);
 
		X509Name x509Name = new X509Name("CN=" + commonName);

		V3TBSCertificateGenerator certGen = new V3TBSCertificateGenerator();
	    certGen.setSerialNumber(new DERInteger(BigInteger.valueOf(System.currentTimeMillis())));
	    // Attention: this is a catch! Just using "new X509Name(caCert.getSubjectDN().getName())" will not work!
	    // I don't know why, because the issuerDN strings look similar with both versions.
		certGen.setIssuer(PrincipalUtil.getSubjectX509Principal(caCert));
		certGen.setSubject(x509Name);
		DERObjectIdentifier sigOID = X509Util.getAlgorithmOID(CertificateSignatureAlgorithm);
		AlgorithmIdentifier sigAlgId = new AlgorithmIdentifier(sigOID, new DERNull());
		certGen.setSignature(sigAlgId);
		//certGen.setSubjectPublicKeyInfo(new SubjectPublicKeyInfo(sigAlgId, pkStruct.toASN1Object()));
		// TODO: why does the coding above not work? - make me work without PublicKey class
		certGen.setSubjectPublicKeyInfo(new SubjectPublicKeyInfo((ASN1Sequence)new ASN1InputStream(
                new ByteArrayInputStream(pubKey.getEncoded())).readObject()));
		certGen.setStartDate(new Time(new Date(System.currentTimeMillis())));
		certGen.setEndDate(new Time(expiry.getTime()));

		// These X509v3 extensions are not strictly necessary, but be nice and provide them...
	    Hashtable extensions = new Hashtable();
	    Vector extOrdering = new Vector();
		ByteArrayOutputStream bOut = new ByteArrayOutputStream();
		DEROutputStream dOut = new DEROutputStream(bOut);
		dOut.writeObject(new SubjectKeyIdentifierStructure(pubKey));
		extensions.put(X509Extensions.SubjectKeyIdentifier, 
				new X509Extension(false, new DEROctetString(bOut.toByteArray())));
		extOrdering.addElement(X509Extensions.SubjectKeyIdentifier);
		bOut = new ByteArrayOutputStream();
		dOut = new DEROutputStream(bOut);
		dOut.writeObject(new AuthorityKeyIdentifierStructure(caCert));
		extensions.put(X509Extensions.AuthorityKeyIdentifier, 
				new X509Extension(false, new DEROctetString(bOut.toByteArray())));
		extOrdering.addElement(X509Extensions.AuthorityKeyIdentifier);
		certGen.setExtensions(new X509Extensions(extOrdering, extensions));

		logger.debug("Certificate structure generated, creating SHA1 digest");
		// attention: hard coded to be SHA1+RSA!
		SHA1Digest digester = new SHA1Digest();
		AsymmetricBlockCipher rsa = new PKCS1Encoding(new RSAEngine());
		TBSCertificateStructure tbsCert = certGen.generateTBSCertificate();

		bOut = new ByteArrayOutputStream();
		dOut = new DEROutputStream(bOut);
		dOut.writeObject(tbsCert);

		// and now sign
		byte[] signature;
		if (useBCAPI) {
			byte[] certBlock = bOut.toByteArray();
			// first create digest
			logger.debug("Block to sign is '" + new String(Hex.encodeHex(certBlock)) + "'");		
			digester.update(certBlock, 0, certBlock.length);
			byte[] hash = new byte[digester.getDigestSize()];
			digester.doFinal(hash, 0);
			// and sign that
			rsa.init(true, caPrivateKey);
			DigestInfo dInfo = new DigestInfo( new AlgorithmIdentifier(X509ObjectIdentifiers.id_SHA1, null), hash);
			byte[] digest = dInfo.getEncoded(ASN1Encodable.DER);
			signature = rsa.processBlock(digest, 0, digest.length);
		}
		else {
			// or the JCE way
	        PrivateKey caPrivKey = KeyFactory.getInstance("RSA").generatePrivate(
	        		new RSAPrivateCrtKeySpec(caPrivateKey.getModulus(), caPrivateKey.getPublicExponent(),
	        				caPrivateKey.getExponent(), caPrivateKey.getP(), caPrivateKey.getQ(), 
	        				caPrivateKey.getDP(), caPrivateKey.getDQ(), caPrivateKey.getQInv()));
			
	        Signature sig = Signature.getInstance(sigOID.getId());
	        sig.initSign(caPrivKey, sr);
	        sig.update(bOut.toByteArray());
	        signature = sig.sign();
		}
		logger.debug("SHA1/RSA signature of digest is '" + new String(Hex.encodeHex(signature)) + "'");

		// and finally construct the certificate structure
        ASN1EncodableVector  v = new ASN1EncodableVector();

        v.add(tbsCert);
        v.add(sigAlgId);
        v.add(new DERBitString(signature));

        X509CertificateObject clientCert = new X509CertificateObject(new X509CertificateStructure(new DERSequence(v))); 
        logger.debug("Verifying certificate for correct signature with CA public key");
        clientCert.verify(caCert.getPublicKey());

        // and export as PKCS12 formatted file along with the private key and the CA certificate 
        logger.debug("Exporting certificate in PKCS12 format");

        PKCS12BagAttributeCarrier bagCert = clientCert;
        bagCert.setBagAttribute(PKCSObjectIdentifiers.pkcs_9_at_friendlyName,
        		new DERBMPString(CertificateExportFriendlyName));
        bagCert.setBagAttribute(
                PKCSObjectIdentifiers.pkcs_9_at_localKeyId,
                new SubjectKeyIdentifierStructure(pubKey));
        
        // this does not work as in the example
        /*PKCS12BagAttributeCarrier   bagKey = (PKCS12BagAttributeCarrier)privKey;
        bagKey.setBagAttribute(
            PKCSObjectIdentifiers.pkcs_9_at_localKeyId,
            new SubjectKeyIdentifierStructure(tmpKey));*/

        KeyStore store = KeyStore.getInstance("PKCS12");

        store.load(null, null);

        X509Certificate[] chain = new X509Certificate[2];
        // first the client, then the CA certificate
        chain[0] = clientCert;
        chain[1] = caCert;
        
        store.setKeyEntry(KeyExportFriendlyName, privKey, exportPassword.toCharArray(), chain);

        FileOutputStream fOut = new FileOutputStream(exportFile);

        store.store(fOut, exportPassword.toCharArray());
		
        return true;
	}
	
	/** The test CA can e.g. be created with
	 * 
	 * Hmm, this CA doesn't work - look at the Javadoc comment for the constructor for how to create it correctly.
	 * echo -e "AT\nUpper Austria\nSteyr\nMy Organization\nNetwork tests\nTest CA certificate\nme@myserver.com\n\n\n" | \
	     openssl req -new -x509 -outform PEM -newkey rsa:2048 -nodes -keyout /tmp/ca.key -keyform PEM -out /tmp/ca.crt -days 365;
	   echo "test password" | openssl pkcs12 -export -in /tmp/ca.crt -inkey /tmp/ca.key -out ca.p12 -name "Test CA" -passout stdin
	 * 
	 * The created certificate can be displayed with
	 * 
	 * openssl pkcs12 -nodes -info -in test.p12 > /tmp/test.cert && openssl x509 -noout -text -in /tmp/test.cert
	 * 
	 * @param args
	 * @throws Exception
	 */
	
	public static void main(String[] args) throws Exception {
		System.out.println(new X509CertificateGenerator("ca.p12", "test password", "Test CA", false).createCertificate("Test CN", 30, "test.p12", "test"));
	}
}

/*Here we go again, new year, new experiences, new ideas, new cognitions, new strength … :) Here is a piece of code that I have written for my thesis to convert a RSA key that have been created with BC into an OpenSSL readable PEM Format. This was a little bit tricky but it works.
private String getPublicKeyPEM() throws IOException
    {
       int line_length = 64;     // PEM-encoded data has 64-character lines
        int length, remaining, position=0;
        String pem_encoded_data = "";
        
        RSAPublicKeyStructure rpks = new RSAPublicKeyStructure(RSApubKey.getModulus(),
                                                               RSApubKey.getExponent());
        String key_pem_format = new                              String(Base64.encode(rpks.getDERObject().getEncoded()));
        key_pem_format = key_pem_format.trim();
        length = key_pem_format.length();
        remaining = length - position;
       while (remaining > line_length) 
        {
            pem_encoded_data += key_pem_format.substring(position, position + line_length) + "\n";
            remaining -= line_length;
            position += line_length;
        }
       if (position < length) 
            pem_encoded_data += key_pem_format.substring(position) + "\n";
        
        key_pem_format = "—–BEGIN RSA PUBLIC KEY—–\n"+
                        pem_encoded_data+
                         "—–END RSA PUBLIC KEY—–\n";
        
        return key_pem_format;
    }*/