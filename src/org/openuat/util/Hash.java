/* Copyright Rene Mayrhofer
 * File created 2006-05-05
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.util;

import java.util.Arrays;

import org.openuat.authentication.exceptions.InternalApplicationException;

/** This is a small helper class that implements SHAd-256, a double execution of SHA256 
 * to counter extension attacks. It is defined in 
 * Niels Ferguson, Bruce Schneier: Practical Cryptography, Wiley 2003
 *
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class Hash {
	/** We use this algorithm for computing the shared key. It is hard-coded for simpler use of this class. */
	private static final String DIGEST_ALGORITHM = "SHA-256";
	
	/** Constant IPAD as defined by HMAC. */
	private static final byte IPAD_BYTE = 0x5c;
	/** Constant OPAD as defined by HMAC. */
	private static final byte OPAD_BYTE = 0x36;
	
	/** This is only a helper function to select JSSE or Bouncycastle implementation. */
	private static byte[] SHA256(byte[] text, boolean useJSSE) 
			throws InternalApplicationException {
//		#if cfg.includeJSSESupport
		if (useJSSE)
			return SHA256_JSSE(text);
		else
//#endif
			return SHA256_BCAPI(text);
	}
	
	/** This is an implementation of SHA256 using the Sun JSSE. */
//	#if cfg.includeJSSESupport
		private static byte[] SHA256_JSSE(byte[] text)
				throws InternalApplicationException {
			try {
				java.security.MessageDigest h = java.security.MessageDigest.getInstance(DIGEST_ALGORITHM);
				return h.digest(text);
			} catch (java.security.NoSuchAlgorithmException e) {
				throw new InternalApplicationException(
						"Required digest algorithm is unknown to the installed cryptography provider(s)",
						e);
			}
		}
//	#endif

		/** This is an implementation of SHA256 using the Bouncycastle Lightweight API. */
		private static byte[] SHA256_BCAPI(byte[] text)
				throws InternalApplicationException {
			org.bouncycastle.crypto.Digest h = new org.bouncycastle.crypto.digests.SHA256Digest();
			
			byte[] tmp = new byte[h.getDigestSize()];
			if (tmp.length != 32)
				throw new InternalApplicationException("Digst does not produce 256 bits, but claims to produce " + tmp.length);
			h.update(text, 0, text.length);
			h.doFinal(tmp, 0);
			return tmp;
		}	

	/** This is a small utility function for computing a secure hash from the shared key.
	 * @param text The text to hash, it may be of arbitrary length.
	 * @param useJSSE If set to true, the JSSE API with the default JCE provider of the JVM will be used
	 *                for cryptographic operations. If set to false, an internal copy of the Bouncycastle
	 *                Lightweight API classes will be used.
	 * @return The SHAd-256 hash over text.
	 */		
	public static byte[] doubleSHA256(byte[] text, boolean useJSSE)
			throws InternalApplicationException {
		/* This double hashing with the first hash being prepended to the message is suggested by 
		 * Practical Cryptography, p. 93 - it should solve the length extension attacks and thus the
		 * MD5 attacks. It is defined as SHAd256(m) = SHA256( SHA256(m) | m ). */

		byte[] tmp1 = SHA256(text, useJSSE);
		if (tmp1.length != 32)
			throw new InternalApplicationException("Digst does not produce 256 bits, but claims to produce " + tmp1.length);
		byte[] tmp2 = new byte[tmp1.length + text.length];
		System.arraycopy(tmp1, 0, tmp2, 0, tmp1.length);
		System.arraycopy(text, 0, tmp2, tmp1.length, text.length);
		return SHA256(tmp2, useJSSE);
	}
	
	/** This is a small utility function for computing HMAC-SHA256 in its standard definition.
	 * HMAC-SHA256 is defined as \mathrm{HMAC}_K(m) = h\bigg((K \oplus \mathrm{opad}) \| h\big((K \oplus \mathrm{ipad}) \| m\big)\bigg)
	 * @param text The text to hash, it may be of arbitrary length.
	 * @param key The key for the HMAC. It should conform to the block size 
	 * 			  of the underlying hash function, i.e. 256 Bits / 32 Bytes, 
	 * 		      but will be hashed if longer or zero-padded if shorter. For
	 *            a guaranteed security level of 128 Bits, you <b>must</b> use
	 *            a 256 Bit key.
	 *            To fulfill the security assumptions of HMAC, this key <b>must</b> be kept secret.
	 * @param useJSSE If set to true, the JSSE API with the default JCE provider of the JVM will be used
	 *                for cryptographic operations. If set to false, an internal copy of the Bouncycastle
	 *                Lightweight API classes will be used.
	 * @return The HMAC-SHA256 value.
	 */		
	public static byte[] hmacSHA256(byte[] text, byte[] key, boolean useJSSE)
			throws InternalApplicationException {
		// shorten key if it is too long
		if (key.length > 32)
			key = doubleSHA256(key, useJSSE);
		// and zero-pad if it is too short
		if (key.length < 32) {
			byte[] tmp = new byte[32];
			System.arraycopy(key, 0, tmp, 0, key.length);
			Arrays.fill(tmp, key.length, tmp.length-1, (byte) 0);
			key = tmp;
		}
		
		if (key.length != 32) {
			throw new InternalApplicationException("Key for HMAC-SHA256 is not 32 Bytes long even after pre-processing. This should not happen!");
		}
		
		// XOR the key with IPAD and OPED
		byte[] key_ipad = new byte[key.length], key_opad = new byte[key.length];
		for (int i=0; i<key.length; i++) {
			key_ipad[i] = (byte) (IPAD_BYTE ^ key[i]);
			key_opad[i] = (byte) (OPAD_BYTE ^ key[i]);
		}
		
		// first: prepend ipad to message
		byte[] ipadAndText = new byte[key.length + text.length];
		System.arraycopy(key_ipad, 0, ipadAndText, 0, key.length);
		System.arraycopy(text, 0, ipadAndText, key.length, text.length);
		// and hash this
		byte[] firstHash = SHA256(ipadAndText, useJSSE);
		
		// second: prepend opad to this first hash
		byte[] opadAndHash = new byte[2*key.length];
		System.arraycopy(key_opad, 0, opadAndHash, 0, key.length);
		System.arraycopy(firstHash, 0, opadAndHash, key.length, key.length);
		// and hash
		return SHA256(opadAndHash, useJSSE);
	}
}
