/* Copyright Rene Mayrhofer
 * File created 2006-04-28
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.authentication;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.BitSet;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.openuat.authentication.exceptions.InternalApplicationException;
import org.openuat.util.SafetyBeltTimer;
import org.openuat.util.SimpleBlockCipher;

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
	/** Our logger. */
	private static Logger logger = Logger.getLogger("org.openuat.authentication.InterlockProtocol" /*InterlockProtocol.class*/);
	/** This is a special logger used for logging only statistics. It is separate from the main logger
	 * so that it's possible to turn statistics on an off independently.
	 */
	private static Logger statisticsLogger = Logger.getLogger("statistics.interlock");

	/** This is the start of the line sent during initializing the interlock
	 * exchange.
	 * @see #interlockExchange
	 */
	private static final String ProtocolLine_Init = "ILCKINIT";
	
	/** This is the start of the line sent each round of the interlock exchange.
	 * @see #interlockExchange
	 */
	private static final String ProtocolLine_Round = "ILCKRND";
	
	/** This may be set to distinguish multiple instances running on the same machine. */
	private String instanceId = null;
	
	/** The shared key used to encrypt and decrypt. */
	private byte[] sharedKey;

	/** The number of rounds to use in the interlock protocol. */
	private int rounds;
	
	/** This size of the plain text message in Bits. */
	private int numMessageBits;
	
	private SimpleBlockCipher cipher;

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
	 * @param instanceId This parameter may be used to distinguish differenc instances of
	 *                   this class running on the same machine. It will be used in logging
	 *                   and error messages. May be set to null.
	 */
	public InterlockProtocol(byte[] sharedKey, int rounds, int numMessageBits, String instanceId, 
			boolean useJSSE) {
		if (rounds < 2)
			throw new IllegalArgumentException("Need at least 2 rounds for the interlock protocol to be secure." + 
					(instanceId != null ? " [instance " + instanceId : ""));
		if (rounds > numMessageBits)
			throw new IllegalArgumentException("Can not use more rounds than the number of message bits" + 
					(instanceId != null ? " [instance " + instanceId : ""));
		if (sharedKey == null)
			logger.warning("Initializing interlock protocol without shared key - encrypt and decrypt will not work" + 
					(instanceId != null ? " [instance " + instanceId : ""));
		if (sharedKey != null && sharedKey.length != SimpleBlockCipher.KeyByteLength)
			throw new IllegalArgumentException("Expecting shared key with a length of " + SimpleBlockCipher.KeyByteLength + 
					" bytes, but got " + sharedKey.length + 
					(instanceId != null ? " [instance " + instanceId : ""));
		if (sharedKey != null && numMessageBits < SimpleBlockCipher.BlockByteLength*8)
			throw new IllegalArgumentException("Can not use with a message size less than the cipher block size " +
					"(got message size of " + numMessageBits + " bits)" + 
					(instanceId != null ? " [instance " + instanceId : ""));

		this.sharedKey = sharedKey;
		this.rounds = rounds;
		this.numMessageBits = numMessageBits;
		this.instanceId = instanceId;
		this.cipher = new SimpleBlockCipher(useJSSE);
		cipher.instanceId = instanceId;

		// compute a few helper variables
		if (sharedKey == null || numMessageBits == SimpleBlockCipher.BlockByteLength*8) {
			// simple - one block
			if (logger.isLoggable(Level.FINER))
				logger.finer("Case 1: cipher is one block long: " + SimpleBlockCipher.BlockByteLength + " bytes" + 
						(instanceId != null ? " [instance " + instanceId : ""));
			numCipherTextBlocks = 1;
		}
		else {
			// more complicated: number of blocks plus IV block
			numCipherTextBlocks = (numMessageBits%(SimpleBlockCipher.BlockByteLength*8) == 0 ? 
					numMessageBits/(SimpleBlockCipher.BlockByteLength*8) : 
					numMessageBits/(SimpleBlockCipher.BlockByteLength*8) + 1) + 1;
			if (logger.isLoggable(Level.FINER))
				logger.finer("Case 2: cipher takes " + numCipherTextBlocks + " blocks: " + 
						(numCipherTextBlocks*SimpleBlockCipher.BlockByteLength) + " bytes" + 
						(instanceId != null ? " [instance " + instanceId : ""));
		}
		
		cipherBitsPerRoundPerBlock = SimpleBlockCipher.BlockByteLength*8 / rounds;
		if (SimpleBlockCipher.BlockByteLength*8 > cipherBitsPerRoundPerBlock * rounds)
			cipherBitsPerRoundPerBlock++;
		logger.info("Transmitting " + cipherBitsPerRoundPerBlock + " bits of message per block each round, " +
				"total of " + cipherBitsPerRoundPerBlock*numCipherTextBlocks + " bits per message each round" + 
				(instanceId != null ? " [instance " + instanceId : ""));
	}
	
	/** Encrypt the plain text message with the shared key set in the constructor.
	 * If the message length equals the block size of the cipher, it is assumed to
	 * be a nonce and is encrypted as a single block in ECB mode. If it is larger, it
	 * is encrypted in CBC mode with a random IV prepended.
	 * @param plainText The message to encrypt. It must contain exactly as many bits
	 *                  as specified in the numMessageBits parameter in the constructor.
	 * @return The cipher text, which is either one block long or the number of blocks
	 *         necessary to encrypt numMessageBits plus one block for the IV.
	 * @throws InternalApplicationException
	 */
	public byte[] encrypt(byte[] plainText) throws InternalApplicationException {
		if (plainText.length*8 > numMessageBits+7 ||
				plainText.length*8 < numMessageBits)
			throw new IllegalArgumentException("Message length does not match numMessageBits" + 
					(instanceId != null ? " [instance " + instanceId : ""));

		return cipher.encrypt(plainText, numMessageBits, sharedKey);
	}
	
	/** Decrypt the cipher text message with the shared key set in the constructor.
	 * If the message length equals the block size of the cipher, the plain text is
	 * assumed to have been a nonce and is decrypted as a single block in ECB mode. 
	 * If it is larger, it is decrypted in CBC mode with a random IV prepended.
	 * @param cipherText The cipher text to decrypt. It must be either one block long 
	 *         or the number of blocks necessary to encrypt numMessageBits plus one block 
	 *         for the IV. 
	 * @return The plain text, which contains exactly as many bits as specified in the 
	 *         numMessageBits parameter in the constructor.
	 * @throws InternalApplicationException
	 */
	public byte[] decrypt(byte[] cipherText) throws InternalApplicationException {
		if (cipherText.length != numCipherTextBlocks * SimpleBlockCipher.BlockByteLength)
			throw new IllegalArgumentException("Cipher text length differs from expected length: wanted " +
					numCipherTextBlocks * SimpleBlockCipher.BlockByteLength + " bytes but got " + cipherText.length + 
					(instanceId != null ? " [instance " + instanceId : ""));

		return cipher.decrypt(cipherText, numMessageBits, sharedKey);
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
		if (cipherText.length % SimpleBlockCipher.BlockByteLength != 0)
			throw new IllegalArgumentException("Can only split multiples of the block cipher length" + 
					(instanceId != null ? " [instance " + instanceId : ""));
		// second option is necessary for when we are called recursively
		if (cipherText.length != numCipherTextBlocks * SimpleBlockCipher.BlockByteLength && cipherText.length != SimpleBlockCipher.BlockByteLength)
			throw new IllegalArgumentException("Cipher text length differs from expected length: wanted " +
					numCipherTextBlocks * SimpleBlockCipher.BlockByteLength + " bytes but got " + cipherText.length + 
					(instanceId != null ? " [instance " + instanceId : ""));

		// in any case, the number of parts is equal to the number of rounds
		byte[][] parts = new byte[rounds][];
		// need to explicitly check for the size length because of recursive calling
		if (cipherText.length == SimpleBlockCipher.BlockByteLength) {
			if (logger.isLoggable(Level.FINER))
				logger.finer("Case 1: splitting cipher text of " + cipherText.length + " bytes with one block into " + 
						rounds + " parts" + 
						(instanceId != null ? " [instance " + instanceId : ""));
			// simple case: the parts are just taken one after each other
			for (int round=0; round<rounds; round++) {
				int curBits = cipherBitsPerRoundPerBlock*(round+1) <= SimpleBlockCipher.BlockByteLength*8 ? 
						cipherBitsPerRoundPerBlock : (SimpleBlockCipher.BlockByteLength*8 - cipherBitsPerRoundPerBlock*round);
				if (curBits > 0) {
					parts[round] = new byte[curBits%8 == 0 ? curBits/8 : curBits/8+1];
					/*logger.finer("Part " + round + " holds " + curBits + " bits, thus " + parts.length + " bytes" + 
							(instanceId != null ? " [instance " + instanceId : ""));*/
					extractPart(parts[round], cipherText, round*cipherBitsPerRoundPerBlock, curBits);
				}
				else {
					// no more left
					if (logger.isLoggable(Level.FINER))
						logger.finer("Part " + round + " is empty" + 
								(instanceId != null ? " [instance " + instanceId : ""));
					parts[round] = null;
				}
			}
		}
		else {
			// the more complicated case is reduced to the simple case - each block split independently, then merged
			if (logger.isLoggable(Level.FINER))
				logger.finer("Case 2: splitting cipher text of " + cipherText.length + " bytes with " 
						+ numCipherTextBlocks + " blocks into " + rounds + " parts" + 
						(instanceId != null ? " [instance " + instanceId : ""));
			for (int block=0; block<numCipherTextBlocks; block++) {
				byte[] cipherBlock = new byte[SimpleBlockCipher.BlockByteLength];
				System.arraycopy(cipherText, block*SimpleBlockCipher.BlockByteLength, cipherBlock, 0, SimpleBlockCipher.BlockByteLength);
				byte[][] blockParts = split(cipherBlock);
				if (blockParts.length != parts.length) {
					throw new InternalApplicationException("Split of a single block did not return as many parts as we wanted. This is wrong.");
				}
				// and immediately merge into the output
				for (int round=0; round<rounds; round++) {
					// these are now the bits for each of the blocks that should belong to this round
					int curBits = cipherBitsPerRoundPerBlock*(round+1) <= SimpleBlockCipher.BlockByteLength*8 ? 
							cipherBitsPerRoundPerBlock : (SimpleBlockCipher.BlockByteLength*8 - cipherBitsPerRoundPerBlock*round);
					if (curBits > 0) {
						// if this is the first block, then we need to create the array first
						if (block==0) {
							int partBits = curBits*numCipherTextBlocks;
							parts[round] = new byte[partBits%8 == 0 ? partBits/8 : partBits/8+1];
						}
						if (logger.isLoggable(Level.FINER))
							logger.finer("Adding " + curBits + " bits of block " + block + " to part " + 
									round + " at offset " + (block*cipherBitsPerRoundPerBlock) + 
									(instanceId != null ? " [instance " + instanceId : ""));
						addPart(parts[round], blockParts[round], block*curBits, curBits);
					}
					else {
						// no more left
						if (logger.isLoggable(Level.FINER))
							logger.finer("Part " + round + " is empty" + 
									(instanceId != null ? " [instance " + instanceId : ""));
						parts[round] = null;
					}
				}
			}
		}
		return parts;
	}

    /** This method is the inverse of split(). All comments there apply here. 
     * 
     * @param messages The parts to reassemble.
     * @return The assembled cipher text.
     * @throws InternalApplicationException 
     */
	public byte[] reassemble(byte[][] messages) throws InternalApplicationException {
		// sanity check
		if (messages.length != rounds)
			throw new IllegalArgumentException("Number of message parts does not match number of rounds, "
					+ "excpected " + rounds + " but received " + messages.length + 
					(instanceId != null ? " [instance " + instanceId : ""));
		if (assembledCipherText != null)
			throw new InternalApplicationException("Can not use both reassemble variants at the same time. " 
					+ "Complete reassambly method called while iterative is active" + 
					(instanceId != null ? " [instance " + instanceId : ""));
		
		// in any case, the reassembled cipher text will have the same length
		byte[] cipherText = new byte[numCipherTextBlocks * SimpleBlockCipher.BlockByteLength];
		if (numCipherTextBlocks == 1) {
			// simple case
			if (logger.isLoggable(Level.FINER))
				logger.finer("Case 1: reassembling " + rounds + " parts to cipher text of " + 
						cipherText.length + " bytes" + 
						(instanceId != null ? " [instance " + instanceId : ""));
			for (int round=0; round<rounds; round++) {
				int curBits = cipherBitsPerRoundPerBlock*(round+1) <= SimpleBlockCipher.BlockByteLength*8 ? 
						cipherBitsPerRoundPerBlock : (SimpleBlockCipher.BlockByteLength*8 - cipherBitsPerRoundPerBlock*round);
				if (curBits > 0)
					addPart(cipherText, messages[round], cipherBitsPerRoundPerBlock*round, curBits);
				else  {
					if (messages[round] != null) {
						logger.severe("Expected null part, but got some content" + 
								(instanceId != null ? " [instance " + instanceId : ""));
					}
				}
			}
		} 
		else {
			// more complex case, need to reassemble blocks
			if (logger.isLoggable(Level.FINER))
				logger.finer("Case 2: reassembling " + rounds + " parts to cipher text of " + 
						cipherText.length + " bytes with " + numCipherTextBlocks + " blocks" + 
						(instanceId != null ? " [instance " + instanceId : ""));
			for (int block=0; block<numCipherTextBlocks; block++) {
				for (int round=0; round<rounds; round++) {
					int curBits = cipherBitsPerRoundPerBlock*(round+1) <= SimpleBlockCipher.BlockByteLength*8 ? 
							cipherBitsPerRoundPerBlock : (SimpleBlockCipher.BlockByteLength*8 - cipherBitsPerRoundPerBlock*round);
					if (curBits > 0) {
						distributeBlockSlicesHelper(cipherText, messages[round], round, block, curBits);
					}
					else { 
						if (messages[round] != null) {
							logger.severe("Expected null part, but got some content" + 
									(instanceId != null ? " [instance " + instanceId : ""));
						}
					}
				}
			}
		}
		
		return cipherText;
	}
	
	/** This is only an internal helper function to add block parts correctly 
	 * on reassambly. Called from reassamble() and addMessage.
	 * @throws InternalApplicationException 
	 */
	private void distributeBlockSlicesHelper(byte[] cipherText, byte[] message,
			int round, int block, int numBits) throws InternalApplicationException {
		byte[] partInBlock = new byte[numBits%8 == 0 ? numBits/8 : numBits/8+1];
		extractPart(partInBlock, message, block*numBits, numBits);
		if (logger.isLoggable(Level.FINER))
			logger.finer("Extracting " + numBits + " bits of block " + block + " from part " + round + 
					(instanceId != null ? " [instance " + instanceId : ""));
		addPart(cipherText, partInBlock, 
				block*SimpleBlockCipher.BlockByteLength*8+round*cipherBitsPerRoundPerBlock, numBits);
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
			logger.severe("ERROR: Did not receive all required messages, first missing is round " + 
					(receivedRounds.nextClearBit(0)+1) + ", received rounds are " + receivedRounds + 
					(instanceId != null ? " [instance " + instanceId : ""));
			return null;
		}

    	return assembledCipherText;
    }

	/** Adds a message to the cipher text assembly. This method should only
	 * be used if not the whole message is to be transferred and/or the
	 * application has very specific needs concerning re-assembly. Better use
	 * the simple addMessage variant unless sure you need this method. 
	 * 
	 * @param message The message to add.
	 * @param offset The bit offset where this message part starts in the reassembly.
	 * @param numBits The number of bits to take from the message array.
	 * @param round The round number of this message. Rounds are counted from 0 to rounds-1.
	 * @return true if added successfully, false if not added. When false is returned, then
	 *         the part for this round has already been added earlier.
	 * @throws InternalApplicationException
	 */
	public boolean addMessage(byte[] message, int offset, int numBits, int round)
			throws InternalApplicationException {
		if (message.length*8 > cipherBitsPerRoundPerBlock*numCipherTextBlocks+7)
			throw new IllegalArgumentException("Message length does not match expected length, " +
					"got " + message.length + " bytes, but expected " + 
					cipherBitsPerRoundPerBlock*numCipherTextBlocks + " bits" + 
					(instanceId != null ? " [instance " + instanceId : ""));
		if (round >= rounds || round < 0)
			throw new IllegalArgumentException("Round " + round + " invalid, must be between 0 and " +
					rounds + 
					(instanceId != null ? " [instance " + instanceId : ""));
		// offset and numBits will be checked in addPart
		
		if (assembledCipherText == null) {
			if (logger.isLoggable(Level.FINER))
				logger.finer("First call to addMessage, creating helper variables for assembly of " + rounds + " rounds" + 
						(instanceId != null ? " [instance " + instanceId : ""));
			assembledCipherText = new byte[numCipherTextBlocks * SimpleBlockCipher.BlockByteLength];
			receivedRounds = new BitSet(rounds);
		}
		if (logger.isLoggable(Level.FINER))
			logger.finer("Adding cipher text message part " + round + ": " + numBits + " bits" + 
					(instanceId != null ? " [instance " + instanceId : ""));

		// check if we already got that round - only use the first packet so to ignore any ack-only packets
		if (receivedRounds.get(round)) {
			logger.info("Ignoring message part " + round + ". Reason: already received this round." + 
					(instanceId != null ? " [instance " + instanceId : ""));
			return false;
		}		
		receivedRounds.set(round, true);

		// also need to distinguish between the two cases here
		if (numCipherTextBlocks == 1) {
			// simple case
			addPart(assembledCipherText, message, offset, numBits);
		}
		else {
			// the more complex one: need to split the blocks from this message and add them in slides
			for (int block=0; block<numCipherTextBlocks; block++) {
				distributeBlockSlicesHelper(assembledCipherText, message, round, block, numBits);
			}
		}
		logger.info("Added message part " + round + " (" + numBits + " bits at offset " + offset + ")" + 
				(instanceId != null ? " [instance " + instanceId : ""));
		return true;
	}
	
	/** This is very ugly, but swapLine just records the number of in and out bytes
	 * in this variable. It is reset on each call.
	 */
	private static int swapSize;
	/** This is a small helper function to exchange a line with the remote host. First,
	 * the own line is sent, and then it waits for a line to be received.
	 * @param command The command name that describes this line. It is prepended to the
	 *                actual value (the two are separated by a space). Only when the other
	 *                host sends a line staring with the same command, its line is accepted.
	 * @param value The value to send
	 * @return The value that the other host sent, or null if no matching line has been
	 *         received.
	 * @throws IOException 
	 */
	private static String swapLine(String command, String value, 
			InputStream fromRemote, OutputStreamWriter toRemote) throws IOException {
		if (logger.isLoggable(Level.FINER))
			logger.finer("Sending line to remote host: command '" + command + "', value '" + value + "'");
		String outStr = command + " " + value + "\n";
		toRemote.write(outStr);
		toRemote.flush();
		swapSize = outStr.length();

		StringBuffer remoteLine = new StringBuffer();
		int ch = fromRemote.read();
		while (ch != -1 && ch != '\n') {
			// TODO: check if this is enough to deal with line ending problems
			if (ch != '\r')
				remoteLine.append((char) ch);
			ch = fromRemote.read();
		}
		swapSize += remoteLine.length();

		if (remoteLine.length() > command.length()+1 && 
			remoteLine.toString().substring(0, command.length()+1).equals(command + " ")) {
			String ret = remoteLine.toString().substring(command.length() + 1);
			if (logger.isLoggable(Level.FINER))
				logger.finer("Received line from remote host: command '" + command + "', value + '" + ret + "'");
			return ret;
		}
		else {
			logger.severe("Did not receive proper line from remote, expected command '" + command + 
					"', received line '" + remoteLine + "'");
			return null;
		}
	}
	
	/** This method runs a complete interlock exchange with another host. To this end,
	 * this method must be started on both sides with the same values for the number of
	 * rounds and, obviously, for the shared key.
	 * @param message The message to send to the remote host.
	 * @param fromRemote This stream is used for receiving bytes from the remote host. This
	 *                   method takes care not to consume any more bytes than stricly 
	 *                   necessary, so that this stream can be re-used for subsequent
	 *                   communication betweem the hosts.
	 * @param toRemote This stream is used for sending bytes to the remote host.
	 * @param sharedKey The shared key to use for encryption and decryption. Must be equal
	 *                  on both sides or the messages will not decrypt successfully (and 
	 *                  this indicates a man-in-the-middle attack).
	 * @param rounds The number of rounds to use.
	 * @param protectAgainstMirrorAttack When set to true, an additional check will be
	 *                  activated to protect against the "mirror attack", where a remote
	 *                  attacker (e.g. a MITM) simply mirrors all the interlock messages,
	 *                  to the effect that this method would return a remote message that
	 *                  was exactly equal to the local message. The upper layers that
	 *                  call this method may not be able to detect this case. Note that
	 *                  this protection can currently only be enabled when message.length
	 *                  is greater than SimpleBlockCipher.BlockByteLength.
	 *                  <b>It is strongly recommended to set this to true<b>, unless the
	 *                  upper protocol layers protect against this attack.
	 * @param retransmit Is set to true, lost messages are allowed and rounds will be
	 *                   retransmitted until the other end acknowledges it or a timeout
	 *                   occurs. THIS IS CURRENTLY NOT IMPLEMENTED. SET TO FALSE.
	 * @param timeoutMs If retransmit is set to true, every round will be limited to take
	 *                  this amount of milliseconds. If the other side has not acknowledged
	 *                  the receipt within this time, the protocol aborts. The overall
	 *                  timeout of the whole protocol is therefore defined to be a maximum
	 *                  of timeoutMs*rounds, but a timeout error may occur earlier than 
	 *                  this. Set to 0 to disable timeouts (which is not recommended!).
	 *                  <br>
	 *                  <b>Attention:</b>Because of long-standing JRE bug 4514257 (see
	 *                  http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4514257), it is
	 *                  impossible to cleanly interrupt the block read() on fromRemote.
	 *                  Therefore, when a timeout is triggered while waiting for the
	 *                  remote host to send (either the initial interlock greeting or
	 *                  it respective round number), then <b>fromRemote will be closed</b>.
	 *                  This is currently the only way of enforcing the timeout on 
	 *                  blocking reads, and unfortunately makes it impossible to 
	 *                  explicitly signal abort to the remote. In the case of toRemote
	 *                  being closed, one must therefore assume any abort reason from the
	 *                  remote including regular timeout aborts.
	 * @param interlockGroup Setting this to a valid BitSet object and groupSize>=2 
	 *                       allows for multiple instances of interlockExchange runs
	 *                       to be synchronized. All instances will enter a barrier
	 *                       just before transmitting their last round and will only
	 *                       continue to transmit when <b>all</b> instances (the number
	 *                       of instances in the group that are to be synchronized is
	 *                       specified by groupSize) have reached this barrier. 
	 *                       This is necessary when the same message is used in
	 *                       multiple interlock instances to prevent an attack where
	 *                       multiple attackers are colluding.
	 *                       Set to null to disable when only one interlock instance
	 *                       is used with the same message. Using this function may
	 *                       increase the total timeout to timeoutMs*rounds*2. 
	 * @param groupSize When interlockGroup is set, this specifies the number of instances
	 *                  to synchronize. Must be >=2, and all instances <b>must</b> be called
	 *                  in parallel threads with the same interlockGroup object.
	 * @param instanceInGroup The instance number in the group that this interlock run
	 *                        should use. No two instances may use the same number, and
	 *                        every number in [0; groupSize-1] must be used for exactly
	 *                        one instance.
	 * @return The message that the remote host sent, or null if the interlock protocol 
	 *         could not be completed successfully.
	 * @throws IOException When reading from fromRemote or writing to toRemote failed.
	 *                     This may also be caused by a timeout that forcefully closed
	 *                     fromRemote. 
	 * @throws InternalApplicationException
	 */
	// TODO: implement handling of retransmit
	public static byte[] interlockExchange(byte[] message, InputStream fromRemote, OutputStream toRemote,
			byte[] sharedKey, int rounds, boolean protectAgainstMirrorAttack, 
			boolean retransmit, int timeoutMs, boolean useJSSE,
			BitSet interlockGroup, int groupSize, int instanceInGroup) 
			throws IOException, InternalApplicationException {
		if (fromRemote == null || toRemote == null)
			throw new IllegalArgumentException("Both input and output stream must be set");
		if (message == null)
			throw new IllegalArgumentException("message can not be null");
		if (sharedKey == null)
			throw new IllegalArgumentException("sharedKey can not be null");
		if (protectAgainstMirrorAttack && message.length <= SimpleBlockCipher.BlockByteLength)
			throw new IllegalArgumentException("Can not protect against mirror attacks with messages of only one cipher block length");
		if (retransmit)
			throw new IllegalArgumentException("Retransmit is currently not implemented");
		if (interlockGroup != null && (groupSize < 2 || instanceInGroup >= groupSize))
			throw new IllegalArgumentException("Using interlock group, but either group size is <2 or this instance number is >= group size");

		if (logger.isLoggable(Level.INFO))
			logger.info("Running interlock exchange with " + rounds + " rounds and timeout of " +
				timeoutMs + "ms. My message is " + message.length + " bytes long");

        int totalTransferTime=0, totalCryptoTime=0, totalCodingTime=0, 
        	totalTransferSize=0, totalMessageNum=0;
       	long timestamp = System.currentTimeMillis();
       	
		InterlockProtocol myIp = new InterlockProtocol(sharedKey, rounds, 
				message.length*8, null, useJSSE);
		byte[] localCiphertext = myIp.encrypt(message);
		byte[][] localParts = myIp.split(localCiphertext);
       	totalCryptoTime += System.currentTimeMillis()-timestamp;
       	timestamp = System.currentTimeMillis();

		OutputStreamWriter writer = new OutputStreamWriter(toRemote);
		/* do not use a BufferedReader here because that would potentially mess up
		 * the stream for other users of the socket (by consuming too many bytes)
		 */

		// protect against timeouts, e.g. the host not responding or not sending its round
		SafetyBeltTimer timer = null;
		if (timeoutMs > 0)
			timer = new SafetyBeltTimer(timeoutMs, fromRemote);
		// first exchange length of message
		String remoteLength = swapLine(ProtocolLine_Init, Integer.toString(message.length), 
				fromRemote, writer);
       	totalTransferTime += System.currentTimeMillis()-timestamp;
       	timestamp = System.currentTimeMillis();
		totalTransferSize += swapSize;
		totalMessageNum++;
		if (remoteLength == null) {
			logger.severe("Did not receive remote message length. Can not continue.");
			return null;
		}
		int remLen = Integer.parseInt(remoteLength);

		InterlockProtocol remoteIp = new InterlockProtocol(sharedKey, rounds, 
				remLen*8, null, useJSSE);
		
		int round=0;
		while (round<rounds && !(timer != null && timer.isTriggered())) {
			if (logger.isLoggable(Level.FINER))
				logger.finer("Sending my round " + round + ", length of part is " + localParts[round].length + " bytes");
			StringBuffer remoteTmp = new StringBuffer();
			remoteTmp.append(round);
			remoteTmp.append(' ');
			remoteTmp.append(Hex.encodeHex(localParts[round]));
	       	totalCodingTime += System.currentTimeMillis()-timestamp;
	       	timestamp = System.currentTimeMillis();

			String remotePart = swapLine(ProtocolLine_Round, 
					remoteTmp.toString(),
					fromRemote, writer);
	       	totalTransferTime += System.currentTimeMillis()-timestamp;
	       	timestamp = System.currentTimeMillis();
			totalTransferSize += swapSize;
			totalMessageNum++;
			if (remotePart == null) {
				logger.severe("Did not receive round " + round + " from remote. Can not continue.");
				return null;
			}

			// first part is the round number, then the part
			int remoteRound = Integer.parseInt(remotePart.substring(0, remotePart.indexOf(' ')));
			if (logger.isLoggable(Level.FINER))
				logger.finer("Received remote round " + remoteRound);
	       	totalCodingTime += System.currentTimeMillis()-timestamp;
	       	timestamp = System.currentTimeMillis();
			if (remoteRound == round) {
				try { 
					byte[] part = Hex.decodeHex(remotePart.substring(remotePart.indexOf(' ')+1).toCharArray());
					remoteIp.addMessage(part, round);
					if (logger.isLoggable(Level.FINER))
						logger.finer("Received " + part.length + " bytes from other host");
					round++;
					// successfully got a new round, reset timer
					if (timer != null)
						timer.reset();
				}
				catch (DecoderException e) {
					logger.severe("Could not decode remote byte array. Can not continue.");
					return null;
				}
		       	totalCodingTime += System.currentTimeMillis()-timestamp;

				/* When the second to last round has just been received and we are part
				 * of an interlock group, then need to wait for all other members before
				 * transmitting our last round. */
				if (round==rounds-1 && interlockGroup != null) {
					// mark ourselves as having arrived at the barrier and waiting
					synchronized(interlockGroup) {
						interlockGroup.set(instanceInGroup);
						// other protocols may already be waiting for us, wake them up
						interlockGroup.notifyAll();
					}
					/* But this may potentially take a lot longer than a single round 
					 * timeout, therefore wait for the maximum possible time here. */
					if (timer != null) {
						timer.stop();
						timer = new SafetyBeltTimer(timeoutMs * rounds, fromRemote);
					}
				
					synchronized(interlockGroup) {
						boolean groupSynchronized = false;
						while (!groupSynchronized && (timer == null || !timer.isTriggered())) {
							groupSynchronized = true;
							for (int i=0; i<groupSize && groupSynchronized; i++)
								if (!interlockGroup.get(i)) groupSynchronized = false;
							// if not all interlock protocols have arrived at the barrier, wait for a change
							if (!groupSynchronized)
								try {
									interlockGroup.wait(timeoutMs);
								} catch (InterruptedException e) {
									// just ignore if we're interrupted, we'll just re-enter the outer loop
								}
						}
						if (!groupSynchronized) {
							logger.severe("Timeout while waiting for interlock group to arrive at barrier, aborting protocol");
							return null;
						}
					}

					// for the last round, set the timer back to normal
					if (timer != null) {
						timer.stop();
						timer = new SafetyBeltTimer(timeoutMs, fromRemote);
					}
				}
		       	timestamp = System.currentTimeMillis();
			}
			else {
				logger.severe("Round number does not match local round. Can not continue.");
				return null;
			}
		}

		if (round == rounds) {
			if (timer != null)
				timer.stop();
			if (logger.isLoggable(Level.FINER))
				logger.finer("Interlock protocol completed");

	       	timestamp = System.currentTimeMillis();
			byte[] remoteCiphertext = remoteIp.reassemble();
			if (protectAgainstMirrorAttack) {
				boolean equals=true;
				// the first block of both cipher texts _must_ be different, as it is a random IV
				for (int i=0; i<SimpleBlockCipher.BlockByteLength && equals; i++)
					if (localCiphertext[i] != remoteCiphertext[i]) equals=false;
				if (equals) {
					logger.severe("Mirror attack detected! Aborting interlock protocol!");
					return null;
				}
			}

			byte[] ret = remoteIp.decrypt(remoteCiphertext); 
	       	totalCryptoTime += System.currentTimeMillis()-timestamp;

           	statisticsLogger.warning("Key transfers took " + totalTransferTime + 
           			"ms for total " + totalTransferSize + 
           			" chars in " + totalMessageNum + 
           			" messages, coding took " + totalCodingTime + 
           			"ms, crypto took " + totalCryptoTime + "ms");
	       	
	       	return ret;
		}
		else {
			logger.severe("Interlock protocol did not finish within timeout");
			return null;
		}
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
	 * @return true if added successfully, false if not added. When false is returned, then
	 *         the part for this round has already been added earlier. It still returns true
	 *         when the part was not added because the cipher text was already complete without
	 *         it (this can only happen when the number of bits to be transmitted fits into 
	 *         less rounds than were used).
	 * @throws InternalApplicationException
	 */
    public boolean addMessage(byte[] message, int round)
    		throws InternalApplicationException {
		if (message != null && message.length*8 > cipherBitsPerRoundPerBlock*numCipherTextBlocks+7)
			throw new IllegalArgumentException("Message length does not match expected length, " +
					"got " + message.length + " bytes, but expected " + 
					cipherBitsPerRoundPerBlock*numCipherTextBlocks + " bits" + 
					(instanceId != null ? " [instance " + instanceId : ""));
		if (round >= rounds || round < 0)
			throw new IllegalArgumentException("Round " + round + " invalid, must be between 0 and " +
					rounds + 
					(instanceId != null ? " [instance " + instanceId : ""));

    	// if nearly all (or all) bits have already been transmitted, it might have less bits
		int curBits = cipherBitsPerRoundPerBlock*(round+1) <= SimpleBlockCipher.BlockByteLength*8 ? 
				cipherBitsPerRoundPerBlock : (SimpleBlockCipher.BlockByteLength*8 - cipherBitsPerRoundPerBlock * round);
		if (curBits > 0) {
			return addMessage(message, round*cipherBitsPerRoundPerBlock, curBits, round);
		}
		else {
			logger.info("Ignoring message part " + round + ": " + curBits + " bits. Reason: cipher text already complete.");
			// but still set the bit to mark that it has actually been "delivered"
			receivedRounds.set(round, true);
			return true;
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
					" bits starting at offset " + bitOffset + " but being called with an array of " + src.length + " bytes");
		if (dest.length * 8 < bitLen)
			throw new InternalApplicationException("Target array not long enough, requested to copy " + bitLen + 
					" bits into a target array of " + dest.length + " bytes length");
		
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

	/** Returns the number of cipher text blocks necessary to encode the message. */ 
	public int getCipherTextBlocks() {
		return numCipherTextBlocks;
	}
}
