/* Copyright Rene Mayrhofer
 * File created 2006-04-28
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.authentication;

import java.security.InvalidParameterException;
import java.util.BitSet;

import org.apache.log4j.Logger;
import org.eu.mayrhofer.authentication.exceptions.InternalApplicationException;

import uk.ac.lancs.relate.core.SerialConnector;

/** This class implements the interlock protocol as first defined in
 * Ronald L. Rivest and Adi Shamir: "How to Expose an Eavesdropper", 1984.
 * 
 * It uses AES, either with the JSSE/JCE or with the Bouncycastle light API,
 * because the latter can also run on e.g. J2ME devices without JCE support.
 * 
 * <b>Attention:</b>The messages that are to be encrypted by interlock are
 * assumed not to be private, i.e. that they can be revealed after the interlock
 * protocol has completed or that information about them can be leaked. These can
 * e.g. be nonces or other pseudo-random streams that have meaning to the receiver
 * (for independent checking that no man-in-the-middle attack is happening) but do 
 * not need to be concealed afterwards. Nonetheless, a random nonce is used as
 * initialization vector (IV) when the whole plain text message does not fit into
 * a single block (i.e. 128 Bits). 
 * <b>Note</b>: When the plain text message fits into 128 Bits, it is assumed to be
 * a nonce and ECB is used.  
 * 
 * A "stream-cipher" mode like OFB or CTR might produce less overhead (the IV would
 * not need to be transmitted), but it still needs to be analyzed if this use 
 * would compromise the security properties of the interlock protocol. Currently, I
 * do not think that it would, but feel uncomfortable using them without further 
 * thought about the implications. 
 *
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class InterlockProtocol {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(InterlockProtocol.class);

	/** The current length in byte of the key. */
	private static final int KeyByteLength = 32;
	
	/** The current block size of the used cipher in bytes. */
	private static final int BlockByteLength = 16;
	
	/** If set to true, the JSSE will be used, if set to false, the Bouncycastle Lightweight API. */
	private boolean useJSSE;

	/** The shared key used to encrypt and decrypt. */
	private byte[] sharedKey;

	/** The number of rounds to use in the interlock protocol. */
	private int rounds;
	
	/** This size of the plain text message in Bits. */
	private int numMessageBits;
	
	/** The number of bits of the message that are transmitted each round.
	 * This is computed by the constructor.
	 */
	private int messageBitsPerRound;
	
	/** This array is only used when the combination of the functions
	 * addMessage and reassemble is used. The addMessage methods add the
	 * incoming message parts to this array, and reassemble() will return
	 * it. When using the other variant reassmble(byte[][] messages), this
	 * variable will not be used.
	 * 
	 * The helper method addPart will directly modify this, the addMessage
	 * methods will only call addPart.
	 *  
	 * @see #addMessage(byte[], int)
	 * @see #addMessage(byte[], int, int, int)
	 * @see #reassemble()
	 * @see #addPart(byte[], byte[], int, int)
	 */
	private byte[] assembledCipherText = null;
	
	/** This bit set is used in conjunction with assembledCipherText to 
	 * record which rounds have already been received. 
	 */
	private BitSet receivedRounds = null;
	
	/** Initializes the interlock protocol by setting all parameters that must be 
	 * immutable for a single instance of the protocol.
	 * 
	 * @param sharedKey The shared key to use for encryption and decryption.
	 * @param rounds The number of rounds to use for the protocol. Must be at least 2 and at most 
	 *               equal to the number of bits in the plain text message.
	 * @param numMessageBits The size of the plain text message that should be transmitted, measured in Bits.
	 * @param useJSSE If set to true, the JSSE API with the default JCE provider of the JVM will be used
	 *                for cryptographic operations. If set to false, an internal copy of the Bouncycastle
	 *                Lightweight API classes will be used.
	 */
	public InterlockProtocol(byte[] sharedKey, int rounds, int numMessageBits, boolean useJSSE) {
		if (rounds < 2)
			throw new InvalidParameterException("Need at least 2 rounds for the interlock protocol to be secure.");
		if (rounds > numMessageBits)
			throw new InvalidParameterException("Can not use more rounds than the number of message bits");
		if (sharedKey == null)
			logger.warn("Initializing interlock protocol without shared key - encrypt and decrypt will not work");
		if (sharedKey != null && sharedKey.length != KeyByteLength)
			throw new InvalidParameterException("Expecting shared key with a length of " + KeyByteLength + 
					" bytes, but got " + sharedKey.length);

		this.sharedKey = sharedKey;
		this.rounds = rounds;
		this.numMessageBits = numMessageBits;
		this.useJSSE = useJSSE;

		messageBitsPerRound = numMessageBits / rounds;
		if (numMessageBits > messageBitsPerRound * rounds)
			messageBitsPerRound++;
		logger.info("Transmitting " + messageBitsPerRound + " bits of message each round");
	}
	
	/** Encrypt the plain text message with the shared key set in the constructor.
	 * If the message length equals the block size of the cipher, it is assumed to
	 * be a nonce and is encrypted as a single block in ECB mode. If it is larger, it
	 * is encrypted in CBC mode with a random IV prepended.
	 * @param plainText The message to encrypt. It must contain exactly as many bits
	 *                  as specified in the numMessageBits parameter in the constructor.
	 * @return The ciper text, which is either one block long or the number of blocks
	 *         necessary to encrypt numMessageBits plus one block for the IV.
	 * @throws InternalApplicationException
	 */
	public byte[] encrypt(byte[] plainText) throws InternalApplicationException {
		if (plainText.length*8 > numMessageBits+7 ||
				plainText.length*8 < numMessageBits)
			throw new InvalidParameterException("Message length does not match numMessageBits");
		if (plainText.length < BlockByteLength)
			throw new InvalidParameterException("Message can currently not be shorter than the block size "
					+ "(" + BlockByteLength + "), because padding is not used");
		if (sharedKey == null)
			throw new InternalApplicationException("Can not encrypt without shared key");
		
		byte[] cipherText;
		if (useJSSE)
			cipherText = encrypt_JSSE(plainText);
		else
			cipherText = encrypt_BCAPI(plainText);

		return cipherText;
	}
	
	/** Decrypt the cipher text message with the shared key set in the constructor.
	 * If the message length equals the block size of the cipher, the plain text is
	 * assumed to have been a nonce and is decrypted as a single block in ECB mode. 
	 * If it is larger, it is decrypted in CBC mode with a random IV prepended.
	 * @param cipherText The cipher text to decrypt. It must be either one block long 
	 *         or the number of blocks necessary to encrypt numMessageBits plus one block 
	 *         for the IV. 
	 * @return The ciper text, which contains exactly as many bits as specified in the 
	 *         numMessageBits parameter in the constructor.
	 * @throws InternalApplicationException
	 */
	public byte[] decrypt(byte[] cipherText) throws InternalApplicationException {
		// sanity check
		if (cipherText.length % BlockByteLength != 0)
			throw new InvalidParameterException("Can only decrypt multiples of the block cipher length");
		if (sharedKey == null)
			throw new InternalApplicationException("Can not encrypt without shared key");

		byte[] plainText;
		if (useJSSE)
			plainText = decrypt_JSSE(cipherText);
		else
			plainText = decrypt_BCAPI(cipherText);

		return plainText;
	}
	
	public byte[][] split(byte[] cipherText) {
		// TODO: implement me
		
		return null;
	}
	
	/** This method only checks that all rounds have actually been received
	 * (i.e. that they have been added with one of the addMessage methods)
	 * and, if everything is ok, returns the assmbled cipher text.
	 * 
	 * @see #addMessage(byte[], int)
	 * @see #addMessage(byte[], int, int, int)
	 * @see #assembledCipherText
	 * @return The assembled cipher text if it has been received completely,
	 *         or null in case of an error.
	 */
    public byte[] reassemble() {
		if (receivedRounds.nextClearBit(0) < rounds) {
			logger.error("ERROR: Did not receive all required messages, first missing is round " + 
					(receivedRounds.nextClearBit(0)+1) + ", received rounds are " + receivedRounds);
			return null;
		}

    	return assembledCipherText;
    }

	public byte[] reassemble(byte[][] messages) {
		// TODO: implement me

		return null;
	}
    
	/** Adds a message to the cipher text assemply. This method should only
	 * be used if not the whole message is to be transferred and/or the
	 * application has very specific needs concerning re-assembly. Better use
	 * the simple addMessage variant unless sure you need this method. 
	 * 
	 * @param message The message to add.
	 * @param offset The bit offset where this message part starts in the reassembly.
	 * @param numBits The number of bits to take from the message array.
	 * @param round The round number of this message. Rounds are counted from 0 to rounds-1.
	 * @return true if added successfully, false otherwise
	 * @throws InternalApplicationException
	 */
	public boolean addMessage(byte[] message, int offset, int numBits, int round)
			throws InternalApplicationException {
		// TODO: check parameters
		
		if (assembledCipherText == null) {
			logger.debug("First call to addMessage, creating helper variables for assembly of " + rounds + " rounds");
			assembledCipherText = new byte[numMessageBits%8 == 0 ? numMessageBits/8 : numMessageBits/8 + 1];
			receivedRounds = new BitSet(rounds);
		}
		logger.debug("Adding cipher text message part " + round + ": " + numBits + " bits");

		// check if we already got that round - only use the first packet so to ignore any ack-only packets
		if (receivedRounds.get(round)) {
			logger.warn("Ignoring message part " + round + ". Reason: already received this round.");
			return false;
		}		
		receivedRounds.set(round, true);

		addPart(assembledCipherText, message, offset, numBits);
		logger.info("Added message part " + round + " (" + numBits + " bits at offset " + offset + "):" + 
				SerialConnector.byteArrayToHexString(message));
		return true;
	}
		
	/** Adds a message to the cipher text assemply. This is a convenience
	 * wrapper around the other addMessage method, which computes offset and
	 * numBits appropriately, assuming that the whole message (whose length was
	 * given to the constructor) is to be transmitted within the interlock rounds.
	 * This method should be used in favor of the other one, unless the application
	 * has very specific needs.
	 * 
	 * @param message The message to add.
	 * @param round The round number of this message. Rounds are counted from 0 to rounds-1.
	 * @return true if added successfully, false otherwise
	 * @throws InternalApplicationException
	 */
    public boolean addMessage(byte[] message, int round)
    		throws InternalApplicationException {
		// TODO: check parameters

    	// if nearly all (or all) bits have already been transmitted, it might have less bits
		int curBits = messageBitsPerRound * (round+1) <= numMessageBits ? 
				messageBitsPerRound : (numMessageBits - messageBitsPerRound * round);
		if (curBits > 0) {
			return addMessage(message, round * messageBitsPerRound, curBits, round);
		}
		else {
			logger.info("Ignoring message part " + round + ": " + curBits + " bits. Reason: cipher text already complete.");
			return false;
		}
    }

	/** Small helper function to add a part to a byte array.
	 * 
	 * This method is only public for the JUnit tests, there's probably not much use for it elsewhere.
	 * 
	 * @param dest The byte array to add to. It is assumed that it has been allocated with sufficient length.
	 * @param src The part to add to dest. It will be added from the LSB part.
	 * @param bitOffset The number of bits to shift src before adding to dest.
	 * @param bitLen The number of bits to add from src to dest.
	 */ 
	static public void addPart(byte[] dest, byte[] src, int bitOffset, int bitLen) throws InternalApplicationException {
		if (src.length * 8 < bitLen)
			throw new InternalApplicationException("Not enough bits in the given array, requested to copy " + bitLen +
					" bits, but being called with an array of " + src.length + " bytes");
		if (dest.length * 8 < bitOffset + bitLen)
			throw new InternalApplicationException("Target array not long enough, requested to copy " + bitLen + 
					" bits starting at offset " + bitOffset + " into a target array of " + dest.length + " bytes length");
		
		int bytePos = bitOffset / 8; // the byte to write to
		int bitPos = bitOffset % 8;  // the bit (within the byte) to write to
		// this could be more performant when bitOffset % 8 = 0 (i.e. when the byte boundaries match), but don't care about that right now
		for (int i=0; i<bitLen; i++) {
			// first get the current bit to copy from src
			boolean bit = ((src[i/8]) & (1 << (i%8))) != 0;
			// and copy it to dest
			if (bit)
				dest[bytePos] |= 1 << bitPos;
			else
				dest[bytePos] &= ~(1 << bitPos);
			bitPos++;
			if (bitPos == 8) {
				bytePos++;
				bitPos = 0;
			}
		}
	}

	/** Encrypt the nonce using the shared key. This implementation utilizes the Sun JSSE API. */
	private byte[] encrypt_JSSE(byte[] plainText) throws InternalApplicationException {
    	// encrypt already checks for correct length of plainText
        // need to specifically request no padding or padding would enlarge the one 128 bits block to two
        try {
			javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/ECB/NoPadding");
			cipher.init(javax.crypto.Cipher.ENCRYPT_MODE,
					new javax.crypto.spec.SecretKeySpec(sharedKey, "AES"));
			return cipher.doFinal(plainText);
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new InternalApplicationException(
					"Unable to get cipher object from crypto provider.", e);
		} catch (javax.crypto.NoSuchPaddingException e) {
			throw new InternalApplicationException(
					"Unable to get requested padding from crypto provider.", e);
		} catch (java.security.InvalidKeyException e) {
			throw new InternalApplicationException(
					"Cipher does not accept its key.", e);
		} catch (javax.crypto.IllegalBlockSizeException e) {
			throw new InternalApplicationException(
					"Cipher does not accept requested block size.", e);
		} catch (javax.crypto.BadPaddingException e) {
			throw new InternalApplicationException(
					"Cipher does not accept requested padding.", e);
		}
	}
	
	/** Decrypt the nonce using the shared key. This implementation utilizes the Sun JSSE API. */
	private byte[] decrypt_JSSE(byte[] cipherText) throws InternalApplicationException {
		try {
			javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/ECB/NoPadding");
			cipher.init(javax.crypto.Cipher.DECRYPT_MODE,
					new javax.crypto.spec.SecretKeySpec(sharedKey, "AES"));
			return cipher.doFinal(cipherText);
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new InternalApplicationException(
					"Unable to get cipher object from crypto provider.", e);
		} catch (javax.crypto.NoSuchPaddingException e) {
			throw new InternalApplicationException(
					"Unable to get requested padding from crypto provider.", e);
		} catch (java.security.InvalidKeyException e) {
			throw new InternalApplicationException(
					"Cipher does not accept its key.", e);
		} catch (javax.crypto.IllegalBlockSizeException e) {
			throw new InternalApplicationException(
					"Cipher does not accept requested block size.", e);
		} catch (javax.crypto.BadPaddingException e) {
			throw new InternalApplicationException(
					"Cipher does not accept requested padding.", e);
		}
	}

	/** Encrypt the nonce using the shared key. This implementation utilizes the Bouncycastle Lightweight API. */
	private byte[] encrypt_BCAPI(byte[] plainText) throws InternalApplicationException {
    	// encrypt already checks for correct length of plainText
    	byte[] cipherText = new byte[BlockByteLength];
   		org.bouncycastle.crypto.BlockCipher cipher = new org.bouncycastle.crypto.engines.AESLightEngine();
    	cipher.init(true, new org.bouncycastle.crypto.params.KeyParameter(sharedKey));
		int encryptedBytes = cipher.processBlock(plainText, 0, cipherText, 0);
		if (encryptedBytes != BlockByteLength) {
   			logger.error("Encryption went wrong: unexpexted number of bytes returned");
			return new byte[encryptedBytes];
		}
		return cipherText;
	}

	/** Decrypt the nonce using the shared key. This implementation utilizes the Bouncycastle Lightweight API. */
	private byte[] decrypt_BCAPI(byte[] cipherText) throws InternalApplicationException {
		byte[] plainText = new byte[BlockByteLength];
		org.bouncycastle.crypto.BlockCipher cipher = new org.bouncycastle.crypto.engines.AESLightEngine();
		cipher.init(false, new org.bouncycastle.crypto.params.KeyParameter(sharedKey));
   		int decryptedBytes = cipher.processBlock(cipherText, 0, plainText, 0);
   		if (decryptedBytes != BlockByteLength) {
   			logger.error("Decryption went wrong: unexpexted number of bytes returned");
   			return new byte[decryptedBytes];
   		}
   		return plainText;
	}
}
