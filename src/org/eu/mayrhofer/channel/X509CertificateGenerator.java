package org.eu.mayrhofer.channel;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Calendar;
import java.util.Date;

import org.apache.commons.codec.binary.Hex;
import org.apache.log4j.Logger;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERBMPString;
import org.bouncycastle.asn1.DERBitString;
import org.bouncycastle.asn1.DERInteger;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.DERObjectIdentifier;
import org.bouncycastle.asn1.DEROutputStream;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.RSAPublicKeyStructure;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.x509.TBSCertificateStructure;
import org.bouncycastle.asn1.x509.Time;
import org.bouncycastle.asn1.x509.V3TBSCertificateGenerator;
import org.bouncycastle.asn1.x509.X509CertificateStructure;
import org.bouncycastle.asn1.x509.X509Name;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.CryptoException;
import org.bouncycastle.crypto.DataLengthException;
import org.bouncycastle.crypto.Signer;
import org.bouncycastle.crypto.digests.SHA1Digest;
import org.bouncycastle.crypto.engines.RSAEngine;
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator;
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.bouncycastle.crypto.params.RSAPrivateCrtKeyParameters;
import org.bouncycastle.crypto.signers.PSSSigner;
import org.bouncycastle.jce.interfaces.PKCS12BagAttributeCarrier;
import org.bouncycastle.jce.provider.X509CertificateObject;
import org.bouncycastle.x509.X509Util;
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
	
	private X509CertificateObject caCert;
	private CipherParameters caPrivateKey;
	
	public X509CertificateGenerator(String caFile) {
		// TODO: need to load CA private key and certificate from file and store into ca*
		/*java.security.KeyStore ks = java.security.KeyStore.getInstance("PKCS12"); 
		 ks.load(new 
		 java.io.FileInputStream("yourStore.p12"),"yourPassword".toCharArray());*/ 	
	}
	
	public boolean createCertificate(String dn, int validityDays, String exportFile, String exportPassword) throws 
			IOException, InvalidKeyException, SecurityException, SignatureException, NoSuchAlgorithmException, DataLengthException, CryptoException, KeyStoreException, NoSuchProviderException, CertificateException, InvalidKeySpecException {
		logger.info("Generating certificate for distinguished subject name '" + 
				dn + "', valid for " + validityDays + " days");
		SecureRandom sr = new SecureRandom();
		
		logger.debug("Creating RSA keypair");
		// generate the keypair for the new certificate
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
	    
		Calendar expiry = Calendar.getInstance();
		expiry.add(Calendar.DAY_OF_YEAR, validityDays);
 
		X509Name x509Name = new X509Name("CN=" + dn);

		V3TBSCertificateGenerator certGen = new V3TBSCertificateGenerator();

	    certGen.setSerialNumber(new DERInteger(BigInteger.valueOf(System.currentTimeMillis())));
	    // TODO: this will of course need to be the CA issuer!
		certGen.setIssuer(x509Name);
		certGen.setSubject(x509Name);
		DERObjectIdentifier sigOID = X509Util.getAlgorithmOID("SHA1WithRSAEncryption");
		AlgorithmIdentifier sigAlgId = new AlgorithmIdentifier(sigOID, new DERNull());
		certGen.setSignature(sigAlgId);
		//certGen.setSubjectPublicKeyInfo(new SubjectPublicKeyInfo(sigAlgId, pkStruct.toASN1Object()));
        PublicKey tmpKey = KeyFactory.getInstance("RSA").generatePublic(
        		new RSAPublicKeySpec(publicKey.getModulus(), publicKey.getExponent()));
		certGen.setSubjectPublicKeyInfo(new SubjectPublicKeyInfo((ASN1Sequence)new ASN1InputStream(
                new ByteArrayInputStream(tmpKey.getEncoded())).readObject()));
		certGen.setStartDate(new Time(new Date(System.currentTimeMillis())));
		certGen.setEndDate(new Time(expiry.getTime()));

		logger.debug("Certificate structure generated, creating SHA1 digest");
		// attention: hard coded to be SHA1+RSA!
		SHA1Digest digester = new SHA1Digest();
		RSAEngine rsa = new RSAEngine();
		TBSCertificateStructure tbsCert = certGen.generateTBSCertificate();

		ByteArrayOutputStream   bOut = new ByteArrayOutputStream();
		DEROutputStream         dOut = new DEROutputStream(bOut);

		// and now sign
		Signer signer = new PSSSigner(rsa, digester, 64);
		// TODO: this will of course need to be the CA private key!
		signer.init(true, keypair.getPrivate());
		dOut.writeObject(tbsCert);
		byte[] certBlock = bOut.toByteArray();
		logger.debug("Block to sign is '" + new String(Hex.encodeHex(certBlock)) + "'");		
		
		signer.update(certBlock, 0, certBlock.length);
		byte[] signature = signer.generateSignature();
		logger.debug("SHA1/RSA signature of digest is '" + new String(Hex.encodeHex(signature)) + "'");

        ASN1EncodableVector  v = new ASN1EncodableVector();

        v.add(tbsCert);
        v.add(sigAlgId);
        v.add(new DERBitString(signature));

        logger.debug("Exporting certificate in PKCS12 format");
        
        X509CertificateObject clientCert = new X509CertificateObject(new X509CertificateStructure(new DERSequence(v))); 
        
        PKCS12BagAttributeCarrier bagCert = clientCert;
        bagCert.setBagAttribute(PKCSObjectIdentifiers.pkcs_9_at_friendlyName,
        		new DERBMPString("Certificate for IPSec WLAN access"));
        
        // need to convert to JSSE format...
        PrivateKey privKey = KeyFactory.getInstance("RSA").generatePrivate(
        		new RSAPrivateCrtKeySpec(publicKey.getModulus(), publicKey.getExponent(),
        				privateKey.getExponent(), privateKey.getP(), privateKey.getQ(), 
        				privateKey.getDP(), privateKey.getDQ(), privateKey.getQInv()));
        PublicKey pubKey = KeyFactory.getInstance("RSA").generatePublic(
        		new RSAPublicKeySpec(publicKey.getModulus(), publicKey.getExponent()));

        //
        // add the friendly name for the private key
        //
        //PKCS12BagAttributeCarrier   bagKey = (PKCS12BagAttributeCarrier)privKey;

        //
        // this is also optional - in the sense that if you leave this
        // out the keystore will add it automatically, note though that
        // for the browser to recognise which certificate the private key
        // is associated with you should at least use the pkcs_9_localKeyId
        // OID and set it to the same as you do for the private key's
        // corresponding certificate.
        //
        /*bagKey.setBagAttribute(
            PKCSObjectIdentifiers.pkcs_9_at_friendlyName,
            new DERBMPString("Private Key for IPSec WLAN access"));
        bagKey.setBagAttribute(
            PKCSObjectIdentifiers.pkcs_9_at_localKeyId,
            new SubjectKeyIdentifierStructure(pubKey));*/
        //
        // store the key and the certificate chain
        //
        KeyStore store = KeyStore.getInstance("PKCS12");

        store.load(null, null);

        X509Certificate[] chain = new X509Certificate[1];
        // first the client, then the CA certificate
        chain[0] = clientCert;
        //chain[1] = caCert;
        
        //
        // if you haven't set the friendly name and local key id above
        // the name below will be the name of the key
        //
        store.setKeyEntry("Private key for IPSec WLAN access", privKey, exportPassword.toCharArray(), chain);

        FileOutputStream fOut = new FileOutputStream(exportFile);

        store.store(fOut, exportPassword.toCharArray());
		
        return true;
	}
	
	public static void main(String[] args) throws Exception {
		System.out.println(new X509CertificateGenerator("").createCertificate("Test CN", 30, "test.p12", "test"));
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