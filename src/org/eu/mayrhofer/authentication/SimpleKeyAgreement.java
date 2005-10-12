package org.eu.mayrhofer.authentication;

import org.eu.mayrhofer.authentication.exceptions.*;
import javax.crypto.interfaces.*;
import javax.crypto.spec.*;
import java.security.*;
import java.security.spec.*;
import java.math.BigInteger;

public class SimpleKeyAgreement {
	/* These are our states of the key agreement protocol: 
	   - initialized means that the internal nonce has been generated (by the init method),
	     but that no message has been transmitted yet, i.e. that we have neither sent our 
	     own part to the remote nor that we have received the remote part yet.
	   - inTransit means that the own key has been sent, but the remote key has not yet 
	     been received and added.
	   - completed means that we have received the remote part and thus completed the protocol.
	*/  
	private static final int STATE_INITIALIZED = 1;
	private static final int STATE_INTRANSIT = 2;
	private static final int STATE_COMPLETED = 3;
	
	private int state;
	
	private javax.crypto.KeyAgreement dh;
	
	// these should hold the DH key data
	private KeyPair myKeypair;

	private static final String KEYAGREEMENT_ALGORITHM = "DiffieHellman";
	private static final String DIGEST_ALGORITHM = "SHA-256";
	private static final String MAGIC_COOKIE = "MAGIC COOKIE FOR RELATE AUTHENTICAION";
	
	/*public static final short[] p1024_orig = {
     0x9B,0xD1,0x92,0x1F,0x5F,0xAA,0xF7,0x94,0xEC,0xBA,0x8B,0x7C,
     0x45,0x0B,0xA1,0x92,0xAE,0xF7,0x8D,0x34,0xC1,0x0D,0x29,0x1D,
     0xD7,0xC4,0xE4,0xA0,0xC9,0xEE,0x3C,0x59,0xB3,0x81,0xF1,0xEF,
     0x09,0x55,0x3F,0xCE,0xD4,0x80,0x89,0xAC,0x0C,0xFF,0xF5,0x51,
     0x3A,0x51,0x0D,0x9B,0x6F,0xF3,0xE2,0xAE,0x4D,0xF4,0x9F,0xEA,
     0x33,0xDA,0x60,0x31,0x3B,0x43,0x9F,0x5C,0xE8,0x1E,0x20,0x60,
     0x23,0x19,0xF3,0xD6,0xC0,0xB1,0x49,0x93,0xDE,0x94,0x87,0xC5,
     0x2D,0x14,0xC9,0xA1,0x2E,0x38,0xB2,0x19,0x12,0x11,0x7F,0xB7,
     0xF9,0x28,0xE9,0xA8,0x6E,0x7A,0x91,0x74,0x13,0x1D,0x41,0x04,
     0x06,0x75,0x2C,0x89,0xE8,0x44,0xE6,0x23,0x4C,0x45,0xDE,0x55,
     0x8D,0xF1,0xFB,0xD5,0xF9,0xB5,0xCB,0xB3,
 };
	public static byte[] p1024;
	public static final byte[] g1024 = {
		0x02,
	};*/
	
    // The 1024 bit Diffie-Hellman modulus values used by SKIP
    private static final byte skip1024ModulusBytes[] = {
        (byte)0xF4, (byte)0x88, (byte)0xFD, (byte)0x58,
        (byte)0x4E, (byte)0x49, (byte)0xDB, (byte)0xCD,
        (byte)0x20, (byte)0xB4, (byte)0x9D, (byte)0xE4,
        (byte)0x91, (byte)0x07, (byte)0x36, (byte)0x6B,
        (byte)0x33, (byte)0x6C, (byte)0x38, (byte)0x0D,
        (byte)0x45, (byte)0x1D, (byte)0x0F, (byte)0x7C,
        (byte)0x88, (byte)0xB3, (byte)0x1C, (byte)0x7C,
        (byte)0x5B, (byte)0x2D, (byte)0x8E, (byte)0xF6,
        (byte)0xF3, (byte)0xC9, (byte)0x23, (byte)0xC0,
        (byte)0x43, (byte)0xF0, (byte)0xA5, (byte)0x5B,
        (byte)0x18, (byte)0x8D, (byte)0x8E, (byte)0xBB,
        (byte)0x55, (byte)0x8C, (byte)0xB8, (byte)0x5D,
        (byte)0x38, (byte)0xD3, (byte)0x34, (byte)0xFD,
        (byte)0x7C, (byte)0x17, (byte)0x57, (byte)0x43,
        (byte)0xA3, (byte)0x1D, (byte)0x18, (byte)0x6C,
        (byte)0xDE, (byte)0x33, (byte)0x21, (byte)0x2C,
        (byte)0xB5, (byte)0x2A, (byte)0xFF, (byte)0x3C,
        (byte)0xE1, (byte)0xB1, (byte)0x29, (byte)0x40,
        (byte)0x18, (byte)0x11, (byte)0x8D, (byte)0x7C,
        (byte)0x84, (byte)0xA7, (byte)0x0A, (byte)0x72,
        (byte)0xD6, (byte)0x86, (byte)0xC4, (byte)0x03,
        (byte)0x19, (byte)0xC8, (byte)0x07, (byte)0x29,
        (byte)0x7A, (byte)0xCA, (byte)0x95, (byte)0x0C,
        (byte)0xD9, (byte)0x96, (byte)0x9F, (byte)0xAB,
        (byte)0xD0, (byte)0x0A, (byte)0x50, (byte)0x9B,
        (byte)0x02, (byte)0x46, (byte)0xD3, (byte)0x08,
        (byte)0x3D, (byte)0x66, (byte)0xA4, (byte)0x5D,
        (byte)0x41, (byte)0x9F, (byte)0x9C, (byte)0x7C,
        (byte)0xBD, (byte)0x89, (byte)0x4B, (byte)0x22,
        (byte)0x19, (byte)0x26, (byte)0xBA, (byte)0xAB,
        (byte)0xA2, (byte)0x5E, (byte)0xC3, (byte)0x55,
        (byte)0xE9, (byte)0x2F, (byte)0x78, (byte)0xC7
    };

    // The SKIP 1024 bit modulus
    public static final BigInteger skip1024Modulus
    = new BigInteger(1, skip1024ModulusBytes);

    // The base used with the SKIP 1024 bit modulus
    public static final BigInteger skip1024Base = BigInteger.valueOf(2);
	
	/*static {
		// Java is not a good language for that....
		p1024 = new byte[p1024_orig.length];
		for (int i=0; i<p1024_orig.length; i++) {
			if (p1024_orig[i] < 0x80)
				p1024[i] = (byte) (p1024_orig[i] & 0x7f);
			else {
				// ok, negative bytes are a very bad idea of language designers
				// when >= 128, need to manually convert it to a negative 2's complement
				p1024[i] = (byte) (p1024_orig[i] & 0x7f);
				p1024[i] += 0x80;
			}
		}
	}*/
	
	private byte[] sharedKey;

	public SimpleKeyAgreement() throws InternalApplicationException
	{
		init();
	}
	
	/// Initializes the random nonce of this side for generating the shared session key.
	/// This method can only be called in any state and wipes all old values.
	public void init() throws InternalApplicationException
	{
		// before overwriting the object references, wipe the old values in memory to really destroy them
		wipe();

     // this also generates the private value X with a bit length of 256
		try { 
		dh = javax.crypto.KeyAgreement.getInstance(KEYAGREEMENT_ALGORITHM);
		KeyPairGenerator kg = KeyPairGenerator.getInstance(KEYAGREEMENT_ALGORITHM);
		
		//DHParameterSpec ps = new DHParameterSpec(new BigInteger(p1024), new BigInteger(g1024)); 
		DHParameterSpec ps = new DHParameterSpec(skip1024Modulus, skip1024Base); 
		kg.initialize(ps);
		myKeypair = kg.generateKeyPair();
		
		dh.init(myKeypair.getPrivate());

		state = STATE_INITIALIZED;
		}
		catch (NoSuchAlgorithmException e) {
			throw new InternalApplicationException("Required key agreement algorithm is unknown to the installed cryptography provider(s)", e);
		}
		catch (InvalidKeyException e) {
			throw new InternalApplicationException("Generated private key is not accepted by the key agreement algorithm", e);
		}
		catch (InvalidAlgorithmParameterException e) {
			throw new InternalApplicationException("Required parameters are not supported by the key agreement algorithm", e);
		}
	}
	
	public void wipe() 
	{
		// TODO: secure wipe of old values!
		// wipe dh, dhParams, pubKey, privKey, sharedKey

	}
	
	/// This method can only be called in state initialized and changes it to inTransit.
	public byte[] getPublicKey() throws KeyAgreementProtocolException
	{
		if (state != STATE_INITIALIZED)
			throw new KeyAgreementProtocolException("getPublicKey called in unallowed state! The public key " +
				"can only transmitted once and immediately after it has been initialized to prevent any kind " +
				"of replay attacks. To get a new public key for transmit, restart the protocol."); 
	
		state = STATE_INTRANSIT;

		// Do without any special encapsulation, just transfer the raw byte stream. Why should we use X.509 encoding just for transferring the key bytes? 
		return ((DHPublicKey) myKeypair.getPublic()).getY().toByteArray();
	}
	
	/// This method can only be called in state inTransmit and changes it to completed.
	public void addRemotePublicKey(byte[] key) throws KeyAgreementProtocolException, InternalApplicationException
	{
		if (state != STATE_INTRANSIT)
			throw new KeyAgreementProtocolException("addRemotePublicKey called in unallowed state! A remote " +
				"public key can only be added once and only after the own public key has been sent to prevent " +
				"any kind of replay attacks. To add a remote public key, either first send the own public key " +
				"to the remote or restart the protocol if you have already added one.");
				
		if (new BigInteger(key).equals(((DHPublicKey) myKeypair.getPublic()).getY()))
			throw new KeyAgreementProtocolException("addRemotePublicKey called with a public key equal to our " +
				"own! This is strictly forbidden since the created random numbers must be different.");
				
		/* check that: 
		   - 1 < key < p
		   - key^q = 1
		*/
		if (new BigInteger(key).compareTo(BigInteger.ONE) <= 0)
			throw new KeyAgreementProtocolException("addRemotePublicKey error: received public key is <= 1");
//		if (new BigInteger(key).compareTo(new BigInteger(p1024)) > 0)
		if (new BigInteger(key).compareTo(skip1024Modulus) >= 0)
			throw new KeyAgreementProtocolException("addRemotePublicKey error: received public key is >= p");
		// TODO: where is my q ??
		/* if(! new BigInteger(key).pow(q).Equals(BigInteger.ONE))
			throw new KeyAgreementProtocolException("addRemotePublicKey error: received public key k does not fulfill key^q = 1"); */

		try {
		DHPublicKey remotePublicKey = (DHPublicKey) KeyFactory.getInstance(KEYAGREEMENT_ALGORITHM).generatePublic(
				new DHPublicKeySpec(new BigInteger(key), skip1024Modulus, skip1024Base));
//				new DHPublicKeySpec(new BigInteger(key), new BigInteger(p1024), new BigInteger(g1024)));
		dh.doPhase(remotePublicKey, true);
		sharedKey = dh.generateSecret();
		state = STATE_COMPLETED;
		}
		catch (InvalidKeySpecException e) {
			throw new InternalApplicationException("Key specification was not accepted for required key agreement protocol", e);
		}
		catch (NoSuchAlgorithmException e) {
			throw new InternalApplicationException("Required key agreement algorithm is unknown to the installed cryptography provider(s)", e);
		}
		catch (InvalidKeyException e) {
			throw new KeyAgreementProtocolException("Received remote public key is not accepted by the key agreement algorithm", e);
		}
	}

	/// this is a small utility function for computing a secure hash from the shared key		
	private byte[] doubleSHA256(byte[] text) throws InternalApplicationException
	{
		// this double hashing with the first hash being prepended to the message is suggested by 
		// Practical Cryptography, p. 93 - it should solve the length extension attacks and thus the
		// MD5 attacks
		try {
		MessageDigest h1 = MessageDigest.getInstance(DIGEST_ALGORITHM);
		MessageDigest h2 = MessageDigest.getInstance(DIGEST_ALGORITHM);
		
		byte[] tmp1 = h1.digest(text);
		byte[] tmp2 = new byte[tmp1.length + text.length];
		System.arraycopy(tmp1, 0, tmp2, 0, tmp1.length);
		System.arraycopy(text, 0, tmp2, tmp1.length, text.length);
		return h2.digest(tmp2);
		}
		catch (NoSuchAlgorithmException e) {
			throw new InternalApplicationException("Required digest algorithm is unknown to the installed cryptography provider(s)", e);
		}
	}

	/// This method can only be called in state completed.
	/// The returned session key must only be used for deriving authentication and encryption keys,
	/// e.g. as a PSK for IPSec. It must _not_ be used directly for authentication, since this could
	/// leak the encryption key to any attacker if the authentication function is not strong enough.
	public byte[] getSessionKey() throws KeyAgreementProtocolException, InternalApplicationException
	{
     if (state != STATE_COMPLETED)
         throw new KeyAgreementProtocolException("getSessionKey called in unallowed state! Must have completed DH before a shared key can be computed.");
     
     /* the DH secret must be hashed to get 128 bits of entropy from it ! */
		// since a hash is vulnerable to collisions, the key must actually be 256 bits long
		
		// this should not be too slow, since the shared key generated by DH is rather short
		// what we return is basically sha256(sha256(dhShared) + dhShared) for dhSharedKey being the shared secret generated by DH
		return doubleSHA256(sharedKey);
	}
	
	/// This method can only be called in state completed.
	/// The returned key should be used for the initial authentication phase, but must _not_ be
	/// used for deriving other channel authentication and encryption keys. It is a fingerprint of 
	/// the key returned by getSessionKey, and one can thus assume that if this key is equal on 
	/// both sides, then both sides also share the same session key.  
	public byte[] getAuthenticationKey() throws KeyAgreementProtocolException, InternalApplicationException
	{
     if (state != STATE_COMPLETED)
         throw new KeyAgreementProtocolException("getSessionKey called in unallowed state! Must have completed DH before a shared key can be computed.");

     // what we return is basically sha256(sha256(dhShared) + dhShared) for 
		// dhSharedKey being <the shared secret generated by DH> concatenated with "MAGIC COOKIE FOR RELATE AUTHENTICAION"
		byte[] cookie = MAGIC_COOKIE.getBytes();
		byte[] dhSharedModified = new byte[sharedKey.length + cookie.length];
		System.arraycopy(sharedKey, 0, dhSharedModified, 0, sharedKey.length);
		System.arraycopy(cookie, 0, dhSharedModified, sharedKey.length, cookie.length);
		return doubleSHA256(dhSharedModified);
	}

}
