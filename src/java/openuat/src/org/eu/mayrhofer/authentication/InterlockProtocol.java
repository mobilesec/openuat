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
import java.security.SecureRandom;
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

	/** The number of blocks the cipher text will take.
	 * This is computed by the constructor.
	 */
	private int numCipherTextBlocks;
	
	/** The number of bits of each cipher blocks that are transmitted each round.
	 * In the multiple-block case, the number of cipher bits transmitted in each
	 * round is cipherBitsPerRoundPerBlock * numCipherTextBlocks
	 * This is computed by the constructor.
	 */
	private int cipherBitsPerRoundPerBlock;
	
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
		if (sharedKey != null && numMessageBits < BlockByteLength*8)
			throw new InvalidParameterException("Can not use with a message size less than the cipher block size " +
					"(got message size of " + numMessageBits + " bits)");

		this.sharedKey = sharedKey;
		this.rounds = rounds;
		this.numMessageBits = numMessageBits;
		this.useJSSE = useJSSE;

		// compute a few helper variables
		if (numMessageBits == BlockByteLength*8) {
			// simple - one block
			logger.debug("Case 1: cipher is one block long: " + BlockByteLength + " bytes");
			numCipherTextBlocks = 1;
		}
		else {
			// more complicated: number of blocks plus IV block
			numCipherTextBlocks = (numMessageBits%(BlockByteLength*8) == 0 ? 
					numMessageBits/(BlockByteLength*8) : 
					numMessageBits/(BlockByteLength*8) + 1) + 1;
			logger.debug("Case 2: cipher takes " + numCipherTextBlocks + " blocks: " + 
					(numCipherTextBlocks*BlockByteLength) + " bytes");
		}
		
		cipherBitsPerRoundPerBlock = BlockByteLength*8 / rounds;
		if (BlockByteLength*8 > cipherBitsPerRoundPerBlock * rounds)
			cipherBitsPerRoundPerBlock++;
		logger.info("Transmitting " + cipherBitsPerRoundPerBlock + " bits of message per block each round, " +
				"total of " + cipherBitsPerRoundPerBlock*numCipherTextBlocks + " bits per message each round");
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
		Object cipher = useJSSE ? initCipher_JSSE(true) : initCipher_BCAPI(true);
		
		// now distinguish between the single-block and the multiple-block cases
		if (plainText.length == BlockByteLength) {
			// ok, the simple case - just one block in ECB mode
			cipherText = useJSSE ? processBlock_JSSE(cipher, plainText) : processBlock_BCAPI(cipher, plainText);
		}
		else {
			// more difficult: multiple block in CBC mode with prepended IV
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
				logger.debug("Encrypting block " + i + ": " + bytesInBlock + " bytes");
				System.arraycopy(plainText, i*BlockByteLength, plainBlock, 0, bytesInBlock);
				// if not filled, the rest is padded with zeros (initialized by the JVM)
				// then XOR with the last cipher text block
				for (int j=0; j<BlockByteLength; j++)
					plainBlock[j] ^= cipherText[i*BlockByteLength + j];
				byte[] cipherBlock = useJSSE ? processBlock_JSSE(cipher, plainBlock) : processBlock_BCAPI(cipher, plainBlock);
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
	 * @return The ciper text, which contains exactly as many bits as specified in the 
	 *         numMessageBits parameter in the constructor.
	 * @throws InternalApplicationException
	 */
	public byte[] decrypt(byte[] cipherText) throws InternalApplicationException {
		// sanity check
		if (cipherText.length % BlockByteLength != 0)
			throw new InvalidParameterException("Can only decrypt multiples of the block cipher length");
		if (cipherText.length != numCipherTextBlocks * BlockByteLength)
			throw new InvalidParameterException("Cipher text length differs from expected length: wanted " +
					numCipherTextBlocks * BlockByteLength + " bytes but got " + cipherText.length);
		if (sharedKey == null)
			throw new InternalApplicationException("Can not encrypt without shared key");

		byte[] plainText;
		Object cipher = useJSSE ? initCipher_JSSE(false) : initCipher_BCAPI(false);
		// now distinguish between the single-block and the multiple-block cases
		if (cipherText.length == BlockByteLength) {
			// ok, the simple case - just one block in ECB mode
			plainText = useJSSE ? processBlock_JSSE(cipher, cipherText) : processBlock_BCAPI(cipher, cipherText);
		}
		else {
			// more difficult: multiple block in CBC mode with prepended IV
			// the plain text will only need to have enough bytes to extract the message
			plainText = new byte[numMessageBits%8 == 0 ? numMessageBits/8 : numMessageBits/8+1];
			// first block is the IV
			byte[] iv = new byte[BlockByteLength];
			System.arraycopy(cipherText, 0, iv, 0, BlockByteLength);
			// and then as many rounds of CBC as we need
			for (int i=0; i<numCipherTextBlocks-1; i++) {
				byte[] cipherBlock = new byte[BlockByteLength];
				System.arraycopy(cipherText, (i+1)*BlockByteLength, cipherBlock, 0, BlockByteLength);
				byte[] plainBlock = useJSSE ? processBlock_JSSE(cipher, cipherBlock) : processBlock_BCAPI(cipher, cipherBlock);
				// then XOR with the last cipher text block
				for (int j=0; j<BlockByteLength; j++)
					plainBlock[j] ^= cipherText[i*BlockByteLength + j];
				// the number of bytes left for this block - may be less for the last
				int bytesInBlock = (i+1)*BlockByteLength <= plainText.length ? 
						BlockByteLength : plainText.length - i*BlockByteLength; 
				logger.debug("Decrypting block " + i + ": " + bytesInBlock + " bytes");
				// and finally add to the output
				System.arraycopy(plainBlock, 0, plainText, i*BlockByteLength, bytesInBlock);
			}
		}

		return plainText;
	}
	
	/** This method splits the cipher text into multiple parts for transmission
	 * in an interlocked way. The caller <b>has</b> to make sure that part i+1 is
	 * not sent until the other host has acknowledged receipt of part i and sent its
	 * part i. If this is not followed, the interlock protocol is not secure!
	 * 
	 * How the splitting is done depends on the mode of the protocol: In the single-block
	 * case, the cipher text consists of just a single block encrypted by one call
	 * to the block cipher. Thus, this block is simply split into equally sized parts
	 * (besides the last one) taken one after the other from the cipher text.
	 * In the multi-block case, all blocks are split as in the single-block case and the
	 * parts of the independent blocks are then concatenated to form the parts. This
	 * makes sure that no single block can be decrypted before all of the message parts
	 * have been delivered. 
	 * 
	 * @param cipherText The cipher text to split.
	 * @return As many parts as there are rounds in the protocol in the first dimension
	 *         of the returned array. The last arrays may be set to null if the cipher
	 *         text does not split cleanly and the last rounds therefore do not contain
	 *         any bits. The last array that is not null may be smaller than the previous
	 *         arrays, but all other are equally sized. The last byte in each array might
	 *         be padded with 0 on the top, i.e. the array will be filled from the LSB part.
	 * @throws InternalApplicationException 
	 */
	public byte[][] split(byte[] cipherText) throws InternalApplicationException {
		// sanity check
		if (cipherText.length % BlockByteLength != 0)
			throw new InvalidParameterException("Can only split multiples of the block cipher length");
		if (cipherText.length != numCipherTextBlocks * BlockByteLength)
			throw new InvalidParameterException("Cipher text length differs from expected length: wanted " +
					numCipherTextBlocks * BlockByteLength + " bytes but got " + cipherText.length);

		// in any case, the number of parts is equal to the number of rounds
		byte[][] parts = new byte[rounds][];
		if (cipherText.length == BlockByteLength) {
			logger.debug("Splitting cipher text of " + cipherText.length + " bytes into " + rounds + " parts");
			// simple case: the parts are just taken one after each other
			for (int round=0; round<rounds; round++) {
				int curBits = cipherBitsPerRoundPerBlock*(round+1) <= BlockByteLength*8 ? 
						cipherBitsPerRoundPerBlock : (BlockByteLength*8 - cipherBitsPerRoundPerBlock*round);
				parts[round] = new byte[curBits%8 == 0 ? curBits/8 : curBits/8+1];
				logger.debug("Part " + round + " holds " + curBits + " bits, thus " + parts.length + " bytes");
				extractPart(parts[round], cipherText, round*cipherBitsPerRoundPerBlock, curBits);
			}
		}
		else {
			// the more complicated case is reduced to the simple case - each block split independently, then merged
			logger.debug("Splitting cipher text of " + cipherText.length + " bytes with " 
					+ numCipherTextBlocks + " blocks into " + rounds + " parts");
			for (int block=0; block<numCipherTextBlocks; block++) {
				byte[] cipherBlock = new byte[BlockByteLength];
				System.arraycopy(cipherText, block*BlockByteLength, cipherBlock, 0, BlockByteLength);
				byte[][] blockParts = split(cipherBlock);
				if (blockParts.length != parts.length) {
					throw new InternalApplicationException("Split of a single block did not return as many parts as we wanted. This is wrong.");
				}
				// and immediately merge into the output
				for (int round=0; round<rounds; round++) {
					// these are now the bits for each of the blocks that should belong to this round
					int curBits = cipherBitsPerRoundPerBlock*(round+1) <= BlockByteLength*8 ? 
							cipherBitsPerRoundPerBlock : (BlockByteLength*8 - cipherBitsPerRoundPerBlock*round);
					// if this is the first block, then we need to create the array first
					if (block==0) {
						int partBits = curBits*numCipherTextBlocks;
						parts[round] = new byte[partBits%8 == 0 ? partBits/8 : partBits/8+1];
					}
					addPart(parts[round], blockParts[round], 
							block*cipherBitsPerRoundPerBlock, curBits);
				}
				
			}
		}
		return parts;
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
			assembledCipherText = new byte[numCipherTextBlocks * BlockByteLength];
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
		int curBits = cipherBitsPerRoundPerBlock*(round+1) <= BlockByteLength*8 ? 
				cipherBitsPerRoundPerBlock : (BlockByteLength*8 - cipherBitsPerRoundPerBlock * round);
		if (curBits > 0) {
			return addMessage(message, round*cipherBitsPerRoundPerBlock, curBits, round);
		}
		else {
			logger.info("Ignoring message part " + round + ": " + curBits + " bits. Reason: cipher text already complete.");
			return false;
		}
    }
    
	/** Small helper function to extract a part from a byte array.
	 * 
	 * This method is only public for the JUnit tests, there's probably not much use for it elsewhere.
	 * 
	 * @param dest The byte array to put the part into. It is assumed that it has been allocated with sufficient length.
	 * @param src The byte array from which the part should be extracted. It will be taken from the LSB part.
	 * @param bitOffset The number of bits to shift src before adding to dest.
	 * @param bitLen The number of bits to add from src to dest.
	 */ 
    static public void extractPart(byte[] dest, byte[] src, int bitOffset, int bitLen) throws InternalApplicationException {
		if (src.length * 8 < bitOffset + bitLen)
			throw new InternalApplicationException("Not enough bits in the given array, requested to copy " + bitLen +
					" bits, but being called with an array of " + src.length + " bytes");
		if (dest.length * 8 < bitLen)
			throw new InternalApplicationException("Target array not long enough, requested to copy " + bitLen + 
					" bits starting at offset " + bitOffset + " into a target array of " + dest.length + " bytes length");
		
		int bytePos = bitOffset / 8; // the byte to read from
		int bitPos = bitOffset % 8;  // the bit (within the byte) to read from
		// this could be more performant when bitOffset % 8 = 0 (i.e. when the byte boundaries match), but don't care about that right now
		for (int i=0; i<bitLen; i++) {
			// first get the current bit to copy from src
			boolean bit = ((src[bytePos]) & (1 << bitPos)) != 0;
			// and copy it to dest
			if (bit)
				dest[i/8] |= 1 << i%8;
			else
				dest[i/8] &= ~(1 << i%8);
			bitPos++;
			if (bitPos == 8) {
				bytePos++;
				bitPos = 0;
			}
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
	private Object initCipher_JSSE(boolean encrypt) throws InternalApplicationException {
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
	
	/** Initializes the block cipher for encryption or decryption. This implementation utilizes the 
	 * Bouncycastle Lightweight API. */
	private Object initCipher_BCAPI(boolean encrypt) {
    	// encrypt already checks for correct length of plainText
   		org.bouncycastle.crypto.BlockCipher cipher = new org.bouncycastle.crypto.engines.AESLightEngine();
    	cipher.init(encrypt, new org.bouncycastle.crypto.params.KeyParameter(sharedKey));
    	return cipher;
	}

	/** Process a block with the previously initialized block cipher (just in ECB mode). */
	private byte[] processBlock_JSSE(Object cipher, byte[] input) throws InternalApplicationException {
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
	
	private byte[] processBlock_BCAPI(Object cipher, byte[] input) {
    	byte[] output = new byte[BlockByteLength];
		int processedBytes = ((org.bouncycastle.crypto.BlockCipher) cipher).processBlock(input, 0, output, 0);
		if (processedBytes != BlockByteLength) {
   			logger.error("Block processing went wrong: unexpexted number of bytes returned");
			return new byte[processedBytes];
		}
		return output;
	}
	
	/** Returns the number of cipher text blocks necessary to encode the message. */ 
	public int getCipherTextBlocks() {
		return numCipherTextBlocks;
	}
}
