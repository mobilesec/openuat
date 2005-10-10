package org.eu.mayrhofer.authentication;

import javax.crypto.*;
import javax.crypto.interfaces.*;
import javax.crypto.spec.*;
import java.security.*;
import java.math.BigInteger;

public class KeyAgreement {
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
	
	private DHPublicKey myPublicKey; 

	private static final short[] p1024 = {
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
 private static final byte[] g1024 = {
     0x02,
 };
	
	private byte[] sharedKey;

	public KeyAgreement() throws NoSuchAlgorithmException
	{
		init();
	}
	
	/// Initializes the random nonce of this side for generating the shared session key.
	/// This method can only be called in any state and wipes all old values.
	public void init() throws NoSuchAlgorithmException
	{
		// before overwriting the object references, wipe the old values in memory to really destroy them
		wipe();

     // OPTION 1: Bouncycastle
     // instead of using fixed parameters, generate them;
		/*DHParametersGenerator pGen = new DHParametersGenerator();
			pGen.init(size, 10, new SecureRandom());
			DHParameters dhParams = pGen.generateParameters(); */
		
		/*dhParams = new DHParameters(p1024, g1024);
				
		KeyGenerationParameters keyParams = new DHKeyGenerationParameters(new SecureRandom(), dhParams);
		DHBasicKeyPairGenerator kpGen = new DHBasicKeyPairGenerator();
		kpGen.init(keyParams);
		AsymmetricCipherKeyPair pair = kpGen.generateKeyPair();
		pubKey = (DHPublicKeyParameters) pair.getPublic();
		privKey = (DHPrivateKeyParameters) pair.getPrivate();
		
		dh = new DHBasicAgreement();	
		dh.init(privKey);*/

     // OPTION 2: DH-helper
     // this also generates the private value X with a bit length of 256
		dh = javax.crypto.KeyAgreement.getInstance("DiffieHellman");
		KeyGenerator kg = KeyGenerator.getInstance("DiffieHellman");
		kg.init(new DHParameterSpec(new BigInteger(p1024), new BigInteger(g1024), 256));
		DHPrivateKey = (DHPrivateKey) kg.generateKey();
		
		state = STATE_INITIALIZED;
	}
	
	public void wipe() 
	{
		// TODO: secure wipe of old values!
		// wipe dh, dhParams, pubKey, privKey, sharedKey

	}
	
	/// This method can only be called in state initialized and changes it to inTransit.
	public byte[] GetPublicKey()
	{
		if (state != States.initialized)
			throw new KeyAgreementProtocolException("getPublicKey called in unallowed state! The public key " +
				"can only transmitted once and immediately after it has been initialized to prevent any kind " +
				"of replay attacks. To get a new public key for transmit, restart the protocol."); 
	
		state = States.inTransmit;
     // OPTION 1: Bouncycastle
     /*return pubKey.getY().toByteArray();*/

     // OPTION 2: DH-helper
     byte[] pubKey = Dh.CreateKeyExchange();
     MyPublicKey = new BigInteger(pubKey);
     return pubKey;
	}
	
	/// This method can only be called in state inTransmit and changes it to completed.
	public void AddRemotePublicKey(byte[] key) 
	{
		if (state != States.inTransmit)
			throw new KeyAgreementProtocolException("addRemotePublicKey called in unallowed state! A remote " +
				"public key can only be added once and only after the own public key has been sent to prevent " +
				"any kind of replay attacks. To add a remote public key, either first send the own public key " +
				"to the remote or restart the protocol if you have already added one.");
				
		if (new BigInteger(key).Equals(MyPublicKey))
			throw new KeyAgreementProtocolException("addRemotePublicKey called with a public key equal to our " +
				"own! This is strictly forbidden since the created random numbers must be different.");
				
		/* check that: 
		   - 1 < key < p
		   - key^q = 1
		*/
		if (new BigInteger(key) <= 1)
			throw new KeyAgreementProtocolException("addRemotePublicKey error: received public key is <= 1");
		if (new BigInteger(key) >= new BigInteger(P_1024))
			throw new KeyAgreementProtocolException("addRemotePublicKey error: received public key is >= p");
		// TODO: where is my q ??
		/* if(! new BigInteger(key).pow(q).Equals(BigInteger.ONE))
			throw new KeyAgreementProtocolException("addRemotePublicKey error: received public key k does not fulfill key^q = 1"); */

     // OPTION 1: Bouncycastle
     /*sharedKey = dh.calculateAgreement(new DHPublicKeyParameters(new BigInteger(key), dhParams));*/

     // OPTION 2: DH-helper
     sharedKey = Dh.DecryptKeyExchange(key);
		state = States.completed;
	}

	/// this is a small utility function for computing a secure hash from the shared key		
	private byte[] DoubleSHA256(byte[] text) 
	{
		// this double hashing with the first hash being prepended to the message is suggested by 
		// Practical Cryptography, p. 93 - it should solve the length extension attacks and thus the
		// MD5 attacks
		HashAlgorithm h1 = new SHA256Managed();
		HashAlgorithm h2 = new SHA256Managed();
		
		byte[] tmp1 = h1.ComputeHash(text);
		byte[] tmp2 = new byte[tmp1.Length + text.Length];
		tmp1.CopyTo(tmp2, 0);
		text.CopyTo(tmp2, tmp1.Length);
		return h2.ComputeHash(tmp2);  
	}

	/// This method can only be called in state completed.
	/// The returned session key must only be used for deriving authentication and encryption keys,
	/// e.g. as a PSK for IPSec. It must _not_ be used directly for authentication, since this could
	/// leak the encryption key to any attacker if the authentication function is not strong enough.
	public byte[] GetSessionKey() 
	{
     if (state != States.completed)
         throw new KeyAgreementProtocolException("getSessionKey called in unallowed state! Must have completed DH before a shared key can be computed.");
     
     /* the DH secret must be hashed to get 128 bits of entropy from it ! */
		// since a hash is vulnerable to collisions, the key must actually be 256 bits long
		
		// this should not be too slow, since the shared key generated by DH is rather short
		// what we return is basically sha256(sha256(dhShared) + dhShared) for dhSharedKey being the shared secret generated by DH
		return DoubleSHA256(sharedKey);
	}
	
	/// This method can only be called in state completed.
	/// The returned key should be used for the initial authentication phase, but must _not_ be
	/// used for deriving other channel authentication and encryption keys. It is a fingerprint of 
	/// the key returned by getSessionKey, and one can thus assume that if this key is equal on 
	/// both sides, then both sides also share the same session key.  
	public byte[] GetAuthenticationKey()
	{
     if (state != States.completed)
         throw new KeyAgreementProtocolException("getSessionKey called in unallowed state! Must have completed DH before a shared key can be computed.");

     // what we return is basically sha256(sha256(dhShared) + dhShared) for 
		// dhSharedKey being <the shared secret generated by DH> concatenated with "MAGIC COOKIE FOR RELATE AUTHENTICAION"
		byte[] dhShared = sharedKey;
		string magicCookie = "MAGIC COOKIE FOR RELATE AUTHENTICAION";
		byte[] dhSharedModified = new byte[dhShared.Length + magicCookie.Length];
		dhShared.CopyTo(dhSharedModified, 0);
		for (int i=0; i<magicCookie.Length; i++)
			dhSharedModified[dhShared.Length + i] = (byte) (magicCookie[i] % 0xff);
		return DoubleSHA256(dhSharedModified);
	}

}
