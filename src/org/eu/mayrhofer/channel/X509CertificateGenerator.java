package org.eu.mayrhofer.channel;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;

import org.apache.commons.codec.binary.Hex;
import org.apache.log4j.Logger;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERBitString;
import org.bouncycastle.asn1.DERInteger;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.DERObjectIdentifier;
import org.bouncycastle.asn1.DEROutputStream;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.RSAPublicKeyStructure;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.x509.TBSCertificateStructure;
import org.bouncycastle.asn1.x509.Time;
import org.bouncycastle.asn1.x509.V3TBSCertificateGenerator;
import org.bouncycastle.asn1.x509.X509CertificateStructure;
import org.bouncycastle.asn1.x509.X509Name;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.digests.MD5Digest;
import org.bouncycastle.crypto.engines.RSAEngine;
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator;
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.bouncycastle.crypto.params.RSAPrivateCrtKeyParameters;
import org.bouncycastle.x509.X509Util;

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
	
	public X509CertificateGenerator(String caPrivateKey) {
		// TODO: need to load CA private key from file and store into issuerPrivateKey
	}
	
	protected X509CertificateStructure createCertificate(String dn, int validityDays) throws 
			IOException, InvalidKeyException, SecurityException, SignatureException, NoSuchAlgorithmException {
		logger.info("Generating certificate for distinguished subject name '" + 
				dn + "', valid for " + validityDays + " days");
		SecureRandom sr = new SecureRandom();
		
		logger.debug("Creating RSA keypair");
		// generate the keypair for the new certificate
		RSAKeyPairGenerator gen = new RSAKeyPairGenerator();
		// TODO: what are these values??
		gen.init(new RSAKeyGenerationParameters(BigInteger.valueOf(2), sr, 12, 2));
		AsymmetricCipherKeyPair keypair = gen.generateKeyPair();
		logger.debug("Generated keypair, extracting components and creating public structure for certificate");
		RSAKeyParameters publicKey = (RSAKeyParameters) keypair.getPublic();
		RSAPrivateCrtKeyParameters privateKey = (RSAPrivateCrtKeyParameters) keypair.getPrivate();
		// used to get proper encoding for the certificate
	    RSAPublicKeyStructure rpks = new RSAPublicKeyStructure(publicKey.getModulus(), publicKey.getExponent());
	    logger.debug("New public key is '" + new String(Hex.encodeHex(rpks.getEncoded())));
	    
		Calendar expiry = Calendar.getInstance();
		expiry.add(Calendar.DAY_OF_YEAR, validityDays);
 
		X509Name x509Name = new X509Name(dn);

		V3TBSCertificateGenerator certGen = new V3TBSCertificateGenerator();

	    certGen.setSerialNumber(new DERInteger(BigInteger.valueOf(System.currentTimeMillis())));
	    // TODO: this will of course need to be the CA issuer!
		certGen.setIssuer(x509Name);
		certGen.setSubject(x509Name);
		certGen.setSubjectPublicKeyInfo(new SubjectPublicKeyInfo((ASN1Sequence)new ASN1InputStream(
                                new ByteArrayInputStream(rpks.getEncoded())).readObject()));
		/*certGen.setStartDate(new Time());
		certGen.setEndDate(new Time());*/
		DERObjectIdentifier sigOID = X509Util.getAlgorithmOID("MD5WithRSAEncryption");
		AlgorithmIdentifier sigAlgId = new AlgorithmIdentifier(sigOID, new DERNull());
		certGen.setSignature(sigAlgId);

		logger.debug("Certificate structure generated, creating MD5 digest");
		// attention: hard coded to be MD5+RSA!
		MD5Digest digester = new MD5Digest();
		RSAEngine signer = new RSAEngine();
		// TODO: this will of course need to be the CA private key!
		signer.init(true, privateKey);
		TBSCertificateStructure tbsCert = certGen.generateTBSCertificate();

		ByteArrayOutputStream   bOut = new ByteArrayOutputStream();
		DEROutputStream         dOut = new DEROutputStream(bOut);

		// first compute the MD5 hash
		dOut.writeObject(tbsCert);
		byte[] certBlock = bOut.toByteArray();
		logger.debug("Block to sign is '" + new String(Hex.encodeHex(certBlock)) + "'");		
		
		digester.update(certBlock, 0, certBlock.length);
		byte[] digest = new byte[digester.getDigestSize()];
		digester.doFinal(digest, digest.length);
		logger.debug("MD5 digest is '" + new String(Hex.encodeHex(digest)) + "'");
		
		// and sign that hash
		byte[] signature = signer.processBlock(digest, 0, digest.length);
		logger.debug("RSA signature of digest is '" + new String(Hex.encodeHex(signature)) + "'");

        ASN1EncodableVector  v = new ASN1EncodableVector();

        v.add(tbsCert);
        v.add(sigAlgId);
        v.add(new DERBitString(signature));

        logger.debug("Exporting certificate as ASN1 vector");
        
        return new X509CertificateStructure(new DERSequence(v));
	}
	
	public static void main(String[] args) throws Exception {
		System.out.println(new X509CertificateGenerator("").createCertificate("Test DN", 30));
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