/* Copyright Rene Mayrhofer
 * File created 2006-05-05
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.util;

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

	/** This is a small utility function for computing a secure hash from the shared key.
	 * @param text The text to hash, it may be of arbitrary length.
	 * @param useJSSE If set to true, the JSSE API with the default JCE provider of the JVM will be used
	 *                for cryptographic operations. If set to false, an internal copy of the Bouncycastle
	 *                Lightweight API classes will be used.
	 * @return The SHAd-256 hash over text.
	 */		
	public static byte[] doubleSHA256(byte[] text, boolean useJSSE)
			throws InternalApplicationException {
		// this double hashing with the first hash being prepended to the message is suggested by 
		// Practical Cryptography, p. 93 - it should solve the length extension attacks and thus the
		// MD5 attacks
//#if cfg.includeJSSESupport
		if (useJSSE)
			return doubleSHA256_JSSE(text);
		else
//#endif
			return doubleSHA256_BCAPI(text);
	}
	
	/** This is an implementation of doubleSHA256 using the Sun JSSE. */
//#if cfg.includeJSSESupport
	private static byte[] doubleSHA256_JSSE(byte[] text)
			throws InternalApplicationException {
		try {
			java.security.MessageDigest h1 = java.security.MessageDigest.getInstance(DIGEST_ALGORITHM);
			java.security.MessageDigest h2 = java.security.MessageDigest.getInstance(DIGEST_ALGORITHM);

			byte[] tmp1 = h1.digest(text);
			byte[] tmp2 = new byte[tmp1.length + text.length];
			System.arraycopy(tmp1, 0, tmp2, 0, tmp1.length);
			System.arraycopy(text, 0, tmp2, tmp1.length, text.length);
			return h2.digest(tmp2);
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new InternalApplicationException(
					"Required digest algorithm is unknown to the installed cryptography provider(s)",
					e);
		}
	}
//#endif

	/** This is an implementation of doubleSHA256 using the Bouncycastle Lightweight API. */
	private static byte[] doubleSHA256_BCAPI(byte[] text)
			throws InternalApplicationException {
		org.bouncycastle.crypto.Digest h1 = new org.bouncycastle.crypto.digests.SHA256Digest();
		org.bouncycastle.crypto.Digest h2 = new org.bouncycastle.crypto.digests.SHA256Digest();
		
		byte[] tmp1 = new byte[h1.getDigestSize()];
		if (tmp1.length != 32)
			throw new InternalApplicationException("Digst does not produce 256 bits, but claims to produce " + tmp1.length);
		byte[] tmp2 = new byte[tmp1.length + text.length];
		h1.update(text, 0, text.length);
		h1.doFinal(tmp1, 0);
		System.arraycopy(tmp1, 0, tmp2, 0, tmp1.length);
		System.arraycopy(text, 0, tmp2, tmp1.length, text.length);
		h2.update(tmp2, 0, tmp2.length);
		h2.doFinal(tmp1, 0);
		return tmp1;
	}	

}
