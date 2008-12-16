/* Copyright Rene Mayrhofer
 * File created 2008-12-16, refactored parts of InterlockProtocol
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.util;

import java.security.SecureRandom;

import org.apache.log4j.Logger;
import org.openuat.authentication.exceptions.InternalApplicationException;

/** This class implements a simple interface to a block cipher (AES/Rijndael)
 * with as little parameters as possible.
 * 
 * @author Rene Mayrhofer
 */
public class SimpleBlockCipher {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger("org.openuat.util.SimpleBlockCipher" /*SimpleBlockCipher.class*/);

	/** The current length in byte of the key. */
	public static final int KeyByteLength = 32;
	
	/** The current block size of the used cipher in bytes. */
	public static final int BlockByteLength = 16;
	
	/** If set to true, the JSSE will be used, if set to false, the Bouncycastle Lightweight API. */
	private boolean useJSSE;

	/** This may be set to distinguish multiple instances running on the same machine. */
	public String instanceId = null;

	/** Construct the simple block cipher object.
	 * @param useJSSE If set to true, the JSSE API with the default JCE provider of the JVM will be used
	 *                for cryptographic operations. If set to false, an internal copy of the Bouncycastle
	 *                Lightweight API classes will be used.
	 */
	public SimpleBlockCipher(boolean useJSSE) {
		this.useJSSE = useJSSE;
	}
	
	/** Encrypt the plain text message with the shared key set in the constructor.
	 * If the message length equals the block size of the cipher, it is assumed to
	 * be a nonce and is encrypted as a single block in ECB mode. If it is larger, it
	 * is encrypted in CBC mode with a random IV prepended.
	 * @param plainText The message to encrypt. It must contain exactly as many bits
	 *                  as specified in the numMessageBits parameter in the constructor.
	 * @param numMessageBits The number of bits to use of this message. If set to -1,
	 *                  will use all bits from plainText.
	 * @param sharedKey The key to use for encryption. It must be of length KeyByteLength.
	 * @return The cipher text, which is either one block long or the number of blocks
	 *         necessary to encrypt numMessageBits plus one block for the IV.
	 * @throws InternalApplicationException
	 */
	public byte[] encrypt(byte[] plainText, int numMessageBits, byte[] sharedKey) throws InternalApplicationException {
		// sanity check
		if (plainText.length < BlockByteLength)
			throw new IllegalArgumentException("Message can currently not be shorter than the block size "
					+ "(" + BlockByteLength + "), because padding is not used" + 
					(instanceId != null ? " [instance " + instanceId : ""));
		if (numMessageBits == -1)
			numMessageBits = plainText.length*8;
		if (plainText.length*8 > numMessageBits+7 ||
				plainText.length*8 < numMessageBits)
			throw new IllegalArgumentException("Message length does not match numMessageBits" + 
					(instanceId != null ? " [instance " + instanceId : ""));
		if (sharedKey == null)
			throw new InternalApplicationException("Can not encrypt without shared key" + 
					(instanceId != null ? " [instance " + instanceId : ""));
		if (sharedKey.length != KeyByteLength)
			throw new InternalApplicationException("Invalid key length: expected " +
					KeyByteLength + " bytes, got " + sharedKey.length + 
					(instanceId != null ? " [instance " + instanceId : ""));
		
		byte[] cipherText;
		Object cipher; 
//#if cfg.includeJSSESupport
		if (useJSSE) 
			cipher = initCipher_JSSE(true, sharedKey);
		else
//#endif
			cipher = initCipher_BCAPI(true, sharedKey);
		
		// now distinguish between the single-block and the multiple-block cases
		if (plainText.length == BlockByteLength) {
			// ok, the simple case - just one block in ECB mode
//#if cfg.includeJSSESupport
			if (useJSSE) 
				cipherText = processBlock_JSSE(cipher, plainText);
			else
//#endif
				cipherText = processBlock_BCAPI(cipher, plainText);
		}
		else {
			// more difficult: multiple block in CBC mode with prepended IV
			int numCipherTextBlocks = (numMessageBits%(SimpleBlockCipher.BlockByteLength*8) == 0 ? 
						numMessageBits/(SimpleBlockCipher.BlockByteLength*8) : 
						numMessageBits/(SimpleBlockCipher.BlockByteLength*8) + 1) + 1;
			
	        SecureRandom r = new SecureRandom();
			byte[] iv = new byte[BlockByteLength];
			r.nextBytes(iv);
			// the ciphertext will need to keep the whole message and the IV
			cipherText = new byte[numCipherTextBlocks * BlockByteLength];
			// first block is the IV
			System.arraycopy(iv, 0, cipherText, 0, BlockByteLength);
			// and then as many rounds of CBC as we need
			for (int i=0; i<numCipherTextBlocks-1; i++) {
				byte[] plainBlock = new byte[BlockByteLength];
				// the number of bytes left for this block - may be less for the last
				int bytesInBlock = (i+1)*BlockByteLength <= plainText.length ? 
						BlockByteLength : plainText.length - i*BlockByteLength;
				if (logger.isDebugEnabled())
					logger.debug("Encrypting block " + i + ": " + bytesInBlock + " bytes" + 
							(instanceId != null ? " [instance " + instanceId : ""));
				System.arraycopy(plainText, i*BlockByteLength, plainBlock, 0, bytesInBlock);
				// if not filled, the rest is padded with zeros (initialized by the JVM)
				// then XOR with the last cipher text block
				for (int j=0; j<BlockByteLength; j++)
					plainBlock[j] ^= cipherText[i*BlockByteLength + j];
				byte[] cipherBlock; 
//#if cfg.includeJSSESupport
				if (useJSSE)
					cipherBlock = processBlock_JSSE(cipher, plainBlock);
				else
//#endif
					cipherBlock = processBlock_BCAPI(cipher, plainBlock);
				// and finally add to the output
				System.arraycopy(cipherBlock, 0, cipherText, (i+1)*BlockByteLength, BlockByteLength);
			}
		}

		return cipherText;
	}
	
	/** Decrypt the cipher text message with the shared key set in the constructor.
	 * If the message length equals the block size of the cipher, the plain text is
	 * assumed to have been a nonce and is decrypted as a single block in ECB mode. 
	 * If it is larger, it is decrypted in CBC mode with a random IV prepended.
	 * @param cipherText The cipher text to decrypt. It must be either one block long 
	 *         or the number of blocks necessary to encrypt numMessageBits plus one block 
	 *         for the IV. 
	 * @param numMessageBits The number of bits to extract from cipherText after decrypting.
	 * @param sharedKey The key to use for encryption. It must be of length KeyByteLength.
	 * @return The plain text, which contains exactly as many bits as specified in the 
	 *         numMessageBits parameter in the constructor.
	 * @throws InternalApplicationException
	 */
	public byte[] decrypt(byte[] cipherText, int numMessageBits, byte[] sharedKey) throws InternalApplicationException {
		// sanity check
		if (cipherText.length % BlockByteLength != 0)
			throw new IllegalArgumentException("Can only decrypt multiples of the block cipher length" + 
					(instanceId != null ? " [instance " + instanceId : ""));
		if (sharedKey == null)
			throw new InternalApplicationException("Can not encrypt without shared key" + 
					(instanceId != null ? " [instance " + instanceId : ""));
		if (sharedKey.length != KeyByteLength)
			throw new InternalApplicationException("Invalid key length: expected " +
					KeyByteLength + " bytes, got " + sharedKey.length + 
					(instanceId != null ? " [instance " + instanceId : ""));

		byte[] plainText;
		Object cipher; 
//#if cfg.includeJSSESupport
		if (useJSSE)
			cipher = initCipher_JSSE(false, sharedKey);
		else
//#endif
			cipher = initCipher_BCAPI(false, sharedKey);
		// now distinguish between the single-block and the multiple-block cases
		if (cipherText.length == BlockByteLength) {
			// ok, the simple case - just one block in ECB mode
//#if cfg.includeJSSESupport
			if (useJSSE) 
				plainText = processBlock_JSSE(cipher, cipherText);
			else
//#endif
				plainText = processBlock_BCAPI(cipher, cipherText);
		}
		else {
			// more difficult: multiple block in CBC mode with prepended IV
			int numCipherTextBlocks = (numMessageBits%(SimpleBlockCipher.BlockByteLength*8) == 0 ? 
					numMessageBits/(SimpleBlockCipher.BlockByteLength*8) : 
					numMessageBits/(SimpleBlockCipher.BlockByteLength*8) + 1) + 1;

			// the plain text will only need to have enough bytes to extract the message
			plainText = new byte[numMessageBits%8 == 0 ? numMessageBits/8 : numMessageBits/8+1];
			// first block is the IV
			byte[] iv = new byte[BlockByteLength];
			System.arraycopy(cipherText, 0, iv, 0, BlockByteLength);
			// and then as many rounds of CBC as we need
			for (int i=0; i<numCipherTextBlocks-1; i++) {
				byte[] cipherBlock = new byte[BlockByteLength];
				System.arraycopy(cipherText, (i+1)*BlockByteLength, cipherBlock, 0, BlockByteLength);
				byte[] plainBlock; 
//#if cfg.includeJSSESupport
				if (useJSSE) 
					plainBlock = processBlock_JSSE(cipher, cipherBlock);
				else
//#endif
					plainBlock = processBlock_BCAPI(cipher, cipherBlock);
				// then XOR with the last cipher text block
				for (int j=0; j<BlockByteLength; j++)
					plainBlock[j] ^= cipherText[i*BlockByteLength + j];
				// the number of bytes left for this block - may be less for the last
				int bytesInBlock = (i+1)*BlockByteLength <= plainText.length ? 
						BlockByteLength : plainText.length - i*BlockByteLength; 
				if (logger.isDebugEnabled())
					logger.debug("Decrypting block " + i + ": " + bytesInBlock + " bytes" + 
							(instanceId != null ? " [instance " + instanceId : ""));
				// and finally add to the output
				System.arraycopy(plainBlock, 0, plainText, i*BlockByteLength, bytesInBlock);
			}
		}

		return plainText;
	}

	//#if cfg.includeJSSESupport
	/** Encrypt the nonce using the shared key. This implementation utilizes the Sun JSSE API. */
	private Object initCipher_JSSE(boolean encrypt, byte[] sharedKey) throws InternalApplicationException {
    	// encrypt already checks for correct length of plainText
        // need to specifically request no padding or padding would enlarge the one 128 bits block to two
        try {
			javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/ECB/NoPadding");
			cipher.init(encrypt ? javax.crypto.Cipher.ENCRYPT_MODE : javax.crypto.Cipher.DECRYPT_MODE,
					new javax.crypto.spec.SecretKeySpec(sharedKey, "AES"));
			return cipher;
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new InternalApplicationException(
					"Unable to get cipher object from crypto provider.", e);
		} catch (javax.crypto.NoSuchPaddingException e) {
			throw new InternalApplicationException(
					"Unable to get requested padding from crypto provider.", e);
		} catch (java.security.InvalidKeyException e) {
			throw new InternalApplicationException(
					"Cipher does not accept its key.", e);
		}
	}
//#endif
	
	/** Initializes the block cipher for encryption or decryption. This implementation utilizes the 
	 * Bouncycastle Lightweight API. */
	private static Object initCipher_BCAPI(boolean encrypt, byte[] sharedKey) {
    	// encrypt already checks for correct length of plainText
   		org.bouncycastle.crypto.BlockCipher cipher = new org.bouncycastle.crypto.engines.AESLightEngine();
    	cipher.init(encrypt, new org.bouncycastle.crypto.params.KeyParameter(sharedKey));
    	return cipher;
	}

//#if cfg.includeJSSESupport
	/** Process a block with the previously initialized block cipher (just in ECB mode). */
	private static byte[] processBlock_JSSE(Object cipher, byte[] input) throws InternalApplicationException {
		try {
			return ((javax.crypto.Cipher) cipher).doFinal(input);
		} catch (javax.crypto.IllegalBlockSizeException e) {
			throw new InternalApplicationException(
					"Cipher does not accept requested block size.", e);
		} catch (javax.crypto.BadPaddingException e) {
			throw new InternalApplicationException(
				"Cipher does not accept requested padding.", e);
		}
	}
//#endif
	
	/** Process a block with the previously initialized block cipher (just in ECB mode). */
	private static byte[] processBlock_BCAPI(Object cipher, byte[] input) {
    	byte[] output = new byte[BlockByteLength];
		int processedBytes = ((org.bouncycastle.crypto.BlockCipher) cipher).processBlock(input, 0, output, 0);
		if (processedBytes != BlockByteLength) {
   			logger.error("Block processing went wrong: unexpexted number of bytes returned");
			return new byte[processedBytes];
		}
		return output;
	}
}
