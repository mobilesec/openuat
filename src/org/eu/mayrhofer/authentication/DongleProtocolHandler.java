package org.eu.mayrhofer.authentication;

import org.apache.log4j.Logger;
import org.eu.mayrhofer.authentication.exceptions.*;

import java.security.SecureRandom;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.BadPaddingException;

import uk.ac.lancs.relate.SerialConnector;
import uk.ac.lancs.relate.MessageQueue;
import uk.ac.lancs.relate.RelateEvent;

/**
 * 
 * @author Rene Mayrhofer
 *
 */
public class DongleProtocolHandler extends AuthenticationEventSender {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(DongleProtocolHandler.class);

	/** With the current Relate dongle hard-/firmware, each round of the dongle authentication protocol transports 3 bits
	 * of entropy.
	 */
	private static final int EntropyBitsPerRound = 3;
	
	/** The offset of the bits carrying the delay information in the reported measurement. */
	private static final int EntropyBitsOffset = 7;
	
	/** The current length in byte of the nonce (and implicitly the RF messages) expected by all parameters. */
	private static final int NonceByteLength = 16;
	
	/** The number of authentication steps, not including the rounds of the dongles. */
	public static final int AuthenticationStages = 5;
	
	/** The serial port to use for connecting to the dongle. */
	private String serialPort;
	
	/** The remote relate id to perform the authentication with. */
	private byte remoteRelateId;
	
	/** The reference measurement to use when examining the delayed US pulses from the remote. This is is millimeters. */
	private int referenceMeasurement;
	
	/** A temporary variable holding the shared key. It is used for passing data from
	 * startAuthentication to the Thread's run method.
	 */
	private byte[] sharedKey;
	/** A temporary variable holding the number of rounds. It is used for passing data from
	 * startAuthentication to the Thread's run method.
	 */
	private int rounds;
	
	/** Initializes the dongle protocol handler by setting the serialPort and remoteRelateId members.
	 * 
	 * @param serialPort The serial port to use for connecting to the dongle.
	 * @param remoteRelateId The remote relate id to perform the authentication with.
	 */
	public DongleProtocolHandler(String serialPort, byte remoteRelateId) {
		this.serialPort = serialPort;
		this.remoteRelateId = remoteRelateId;
	}
	
	/**
	 * Perform the necessary steps of the authentication with the dongle,
	 * constructing and decoding the messages for the low-level host/dongle
	 * communication.
	 * 
	 * In the current implementation, it waits for the SerialConnector
	 * background thread to send events. If this thread blocks, the
	 * authentication will fail with a timeout.
	 * 
	 * @param nonce
	 *            The random nonce used to derive the ultrasound delays from.
	 * @param sentRfMessage
	 *            The RF message transported over the Relate RF network to the
	 *            remote dongle. At the moment, it is an encrypted version of
	 *            the random nonce.
	 * @param rounds
	 *            The number of rounds to use. Due to the protocol and hardware
	 *            limitations, the security of this authentication is given by
	 *            rounds * EnropyBitsPerRound.
	 * @param receivedNonce
	 *            The received nonce transported by the ultrasond delays from
	 *            the remote dongle. It is assumed that this array is
	 *            initialized by the caller with the same length as nonce, but
	 *            the contents are returned by this method. It will only carry
	 *            EntropyBitsPerRound * rounds bits starting from the LSB.
	 *            Higher bits will not be modified.
	 * @param receivedRfMessage
	 *            The RF message <b>received</b> from the remote dongle. It is
	 *            assumed that this array is initialized by the caller with the
	 *            same length as sentRfMessage, but the contents are returned by
	 *            this method.
	 * @return true if the authentication protocol completed, false otherwise.
	 *         If true, returns the received RF message in receivedRfMessage
	 * @throws DongleAuthenticationProtocolException
	 */
	private boolean handleDongleCommunication(byte[] nonce, byte[] sentRfMessage, int rounds, byte[] receivedNonce, byte[] receivedRfMessage) throws DongleAuthenticationProtocolException {
		// first check the parameters
		if (remoteRelateId < 0)
			throw new DongleAuthenticationProtocolException("Remote relate id must be >= 0.");
		if (nonce == null || nonce.length != NonceByteLength)
			throw new DongleAuthenticationProtocolException("Expecting random nonce with a length of 16 Bytes.");
		if (sentRfMessage == null || sentRfMessage.length != NonceByteLength)
			throw new DongleAuthenticationProtocolException("Expecting RF message with a length of 16 Bytes.");
		if (receivedRfMessage == null || receivedRfMessage.length != NonceByteLength)
			throw new DongleAuthenticationProtocolException("Received RF message will have 16 Bytes, expecting pre-allocated array.");
		if (rounds < 2)
			throw new DongleAuthenticationProtocolException("Need at least 2 rounds for the interlock protocol to be secure.");

		SerialConnector serialConn = SerialConnector.getSerialConnector();
		
		// Connect here to the dongle so that we don't block it when not necessary. This needs better integration with the Relate framework. 
		int localRelateId = serialConn.connect(serialPort, 0, 255);
		if (localRelateId != -1)
			logger.info("-------- connected successfully to dongle, including first handshake. My ID is " + localRelateId);
		else {
			logger.error("-------- failed to connect to dongle, didn't get my ID.");
			return false;
		}
		
		// This message queue is used to receive events from the dongle.
		MessageQueue eventQueue = new MessageQueue();

		// start the backgroud thread for getting messages from the dongle
		Thread serialThread = new Thread(serialConn);
		serialConn.registerEventQueue(eventQueue);
		serialThread.start();

		raiseAuthenticationProgressEvent(new Integer(remoteRelateId), 2, AuthenticationStages + rounds, "Connected to dongle.");

		// test code
		class ThreeInts { public long sum = 0, n = 0, sum2 = 0; };
		ThreeInts[] s = new ThreeInts[256]; for(int i=0; i<256; i++) s[i] = new ThreeInts();
		
		// wait for the first reference measurements to come in (needed to compute the delays)
		logger.debug("Trying to get reference measurement to relate id " + remoteRelateId);
		int numMeasurements = 0;
		referenceMeasurement = 0;
		while (numMeasurements < 10) {
			while (eventQueue.isEmpty())
				eventQueue.waitForMessage(500);
			RelateEvent e = (RelateEvent) eventQueue.getMessage();
			if (e == null) {
				logger.warn("Warning: got null message out of message queue! This should not happen.");
				continue;
			}
			
			// test code begin
			if (e.getType() == RelateEvent.NEW_MEASUREMENT && e.getMeasurement().getRelatum() == localRelateId) {
				if (/*e.getMeasurement().getTransducers() != 0*/ e.getMeasurement().getDistance() != 4094) {
					//logger.debug("Got measurement from dongle " + e.getMeasurement().getRelatum() + " to dongle " + e.getMeasurement().getId() + ": " + e.getMeasurement().getDistance());
					ThreeInts x = s[e.getMeasurement().getId()];
					x.n++;
					x.sum += (int) e.getMeasurement().getDistance();
					x.sum2 += ((int) e.getMeasurement().getDistance() * (int) e.getMeasurement().getDistance());
					//logger.debug("To dongle " + e.getMeasurement().getId() + ": n=" + x.n + ", sum=" + x.sum + ", sum2=" + x.sum2);
					/*logger.debug("To dongle " + e.getMeasurement().getId() + ": mean=" + (float) x.sum/x.n + ", variance=" + 
							Math.sqrt((x.sum2 - 2*(float) x.sum/x.n*x.sum + (float) x.sum/x.n*x.sum)/x.n) );*/
				}
				else {
					logger.info("Discarded invalid measurement from dongle " + e.getMeasurement().getRelatum() + " to dongle " + e.getMeasurement().getId() + ": " + e.getMeasurement().getDistance());
				}
			}
			// test code end
			
			if (e.getType() == RelateEvent.NEW_MEASUREMENT && e.getMeasurement().getRelatum() == localRelateId &&  
					e.getMeasurement().getId() == remoteRelateId && /*e.getMeasurement().getTransducers() != 0*/ e.getMeasurement().getDistance() != 4094) {
				logger.info("Received reference measurement to dongle " + remoteRelateId + ": " + e.getMeasurement().getDistance());
				referenceMeasurement += (int) e.getMeasurement().getDistance();
				numMeasurements++;
			}
		}
		referenceMeasurement /= numMeasurements;
		logger.info("Mean over reference measurements to dongle " + remoteRelateId + ": " + referenceMeasurement);

		raiseAuthenticationProgressEvent(new Integer(remoteRelateId), 3, AuthenticationStages + rounds, "Got reference measurement");
		
		// construct the start-of-authentication message and sent it to the dongle
		if (!serialConn.startAuthenticationWith(remoteRelateId, nonce, sentRfMessage, rounds, EntropyBitsPerRound, referenceMeasurement)) {
			logger.error("ERROR: could not send start-of-authentication packet to dongle");
			raiseAuthenticationFailureEvent(new Integer(remoteRelateId), null, "Unable to send start-of-authentication packet to dongle.");
			return false;
		}

		raiseAuthenticationProgressEvent(new Integer(remoteRelateId), 4, AuthenticationStages + rounds, "Initiated authentication mode in dongle");
		
		// and wait for the measurements and authentication data to be received
		int lastCompletedRound = -1;
		int messageBitsPerRound = sentRfMessage.length / rounds;
		if (sentRfMessage.length > messageBitsPerRound * rounds)
			messageBitsPerRound++;
		
		while (lastCompletedRound < rounds) {
			while (eventQueue.isEmpty())
				eventQueue.waitForMessage(500);
			RelateEvent e = (RelateEvent) eventQueue.getMessage();
			if (e == null) {
				logger.warn("Warning: got null message out of message queue! This should not happen.");
				continue;
			}
			if (e.getType() == RelateEvent.AUTHENTICATION_INFO && e.getDevice().getId() == remoteRelateId) {
				// authentication info event: just remember the bits received with it
				addPart(receivedRfMessage, e.authenticationPart, e.round * messageBitsPerRound,
						// if it is the last round, it might have less bits
						e.round < rounds ? messageBitsPerRound : (sentRfMessage.length - messageBitsPerRound * rounds));
				lastCompletedRound = e.round;
				logger.info("Received authentication part from dongle " + remoteRelateId + 
						": round " + lastCompletedRound + " out of " + rounds);
			}
			if (e.getType() == RelateEvent.NEW_MEASUREMENT && e.getMeasurement().getRelatum() == localRelateId &&  
					e.getMeasurement().getId() == remoteRelateId) {
				if (e.getMeasurement().getTransducers() == 0)
					logger.debug("WARNING: got measurement with 0 valid transducers during authentication! Not discarding it.");
				if (e.getMeasurement().getDistance() == 4094) {
					logger.debug("Discarding invalid measurement in authentication mode: reported by dongle");
					continue;
				}
				// measurement event for the authentication partner: re-use the round from the authentication info event
				// first extract the delay bits (since it is delayed, it is guaranteed to be longer than the reference)
				int delayedMeasurement = (int) e.getMeasurement().getDistance();
				// WATCHME: at the moment we use only 3 bits, but that might change....
				int delay = (delayedMeasurement - referenceMeasurement) >> EntropyBitsOffset;
				// still do a sanity check (within our accuracy range)
				if (delay > (1 << (EntropyBitsPerRound+1)) || delay < 0) {
					logger.debug("Discarding invalid measurement in authentication mode: smaller than reference");
					continue;
				}
				// and add to the receivedNonce for later comparison
				addPart(receivedNonce, new byte[] {(byte) delay}, lastCompletedRound * EntropyBitsPerRound, EntropyBitsPerRound);
				logger.info("Received delayed measurement to dongle " + remoteRelateId + ": " + delayedMeasurement + 
						", computed nonce part from delay: " + (delay >= 0 ? delay : delay + 0xff));
				raiseAuthenticationProgressEvent(new Integer(remoteRelateId), 4 + lastCompletedRound, AuthenticationStages + rounds, "Got delayed measurement at round " + lastCompletedRound);
			}
		}

		serialConn.unregisterEventQueue(eventQueue);
		serialConn.die();
		try {
			serialThread.join();
		}
		catch (InterruptedException e) {
		}
		serialConn.disconnect();
		
		return true;
	}

	/** A helper method called in the background thread. Exists for the sole purpose of
	 * better code structure.
	 */
	private void handleCompleteProtocol() throws InternalApplicationException, DongleAuthenticationProtocolException {
		// first create the local nonce
        SecureRandom r = new SecureRandom();
        byte[] nonce = new byte[16];
        r.nextBytes(nonce);
        // need to specifically request no padding or padding would enlarge the one 128 bits block to two
        try {
			Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE,
					new SecretKeySpec(sharedKey, "AES"));
			byte[] rfMessage = cipher.doFinal(nonce);
			if (rfMessage.length != 16)
				logger.error("Encryption went wrong, got "
						+ rfMessage.length + " bytes");

			raiseAuthenticationProgressEvent(new Integer(remoteRelateId), 1, AuthenticationStages + rounds, "Encrypted nonce");
			
			byte[] receivedNonce = new byte[16], receivedRfMessage = new byte[16];
			if (!handleDongleCommunication(nonce, rfMessage, rounds, receivedNonce, receivedRfMessage)) {
				raiseAuthenticationFailureEvent(new Integer(remoteRelateId), null, "Dongle authentication protocol failed");
				return;
			}
			// check that the delays match the (encrypted) message sent by the remote
			cipher.init(Cipher.DECRYPT_MODE,
					new SecretKeySpec(sharedKey, "AES"));
			byte[] decryptedRfMessage = cipher.doFinal(receivedRfMessage);

			raiseAuthenticationProgressEvent(new Integer(remoteRelateId), 5 + rounds, AuthenticationStages + rounds, "Connected to dongle.");

			// the lower bits must match
			if (compareBits(receivedNonce, decryptedRfMessage, EntropyBitsPerRound * rounds))
				raiseAuthenticationSuccessEvent(new Integer(remoteRelateId), null);
			else
				raiseAuthenticationFailureEvent(new Integer(remoteRelateId), null, "Ultrasound delays do not match received nonce");
				
		} catch (NoSuchAlgorithmException e) {
			throw new InternalApplicationException(
					"Unable to get cipher object from crypto provider.", e);
		} catch (NoSuchPaddingException e) {
			throw new InternalApplicationException(
					"Unable to get requested padding from crypto provider.", e);
		} catch (InvalidKeyException e) {
			throw new InternalApplicationException(
					"Cipher does not accept its key.", e);
		} catch (IllegalBlockSizeException e) {
			throw new InternalApplicationException(
					"Cipher does not accept requested block size.", e);
		} catch (BadPaddingException e) {
			throw new InternalApplicationException(
					"Cipher does not accept requested padding.", e);
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
	static public void addPart(byte[] dest, byte[] src, int bitOffset, int bitLen) {
		if (src.length * 8 < bitLen)
			// TODO: throw exception
			return;
		if (dest.length * 8 < bitOffset + bitLen)
			// TODO: throw exception
			return;
		
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
	
	/**
	 * Compares a number of bits starting at LSB.
	 * 
	 * This method is only public for the JUnit tests, there's probably not much use for it elsewhere.
	 * 
	 * @param s The first bit string.
	 * @param t The second bit string.
	 * @param numBits The number of bits to compare (starting at LSB).
	 * @return true if all numBits are equal, false otherwise.
	 */
	static public boolean compareBits(byte[] s, byte[] t, int numBits) {
		for (int i=0; i<numBits; i++)
			if (((s[i/8]) & (1 << (i%8))) != ((t[i/8]) & (1 << (i%8))))
				return false;
		return true;
	}
	
	/**
	 * This method performs a full authentication of the pre-established shared
	 * secrets with another Relate dongle.The authentication is started as a 
	 * background thread. 
     *
	 * @param sharedKey The secret authentication key shared with the remote host.
	 * @param rounds
	 *            The number of rounds to use. Due to the protocol and hardware
	 *            limitations, the security of this authentication is given by
	 *            rounds * EnropyBitsPerRound.
	 * @param referenceMeasurement The reference measurement to the remote relate
	 * dongle. It is assumed that the real distance between the dongles will not 
	 * change during the authentication.
	 */
	public void startAuthentication(byte[] sharedKey, int rounds, int referenceMeasurement) {
		this.sharedKey = sharedKey;
		this.rounds = rounds;
		this.referenceMeasurement = referenceMeasurement;
		
		new Thread(new AsynchronousCallHelper(this) { public void run() {
			try {
				outer.handleCompleteProtocol();
			}
			catch (InternalApplicationException e) {
				outer.raiseAuthenticationFailureEvent(new Integer(outer.remoteRelateId), e, "Dongle authentication protocol failed");
			}
			catch (DongleAuthenticationProtocolException e) {
				outer.raiseAuthenticationFailureEvent(new Integer(outer.remoteRelateId), e, "Dongle authentication protocol failed");
			}
		}}).start();
	}

    /** Hack to just allow one method to be called asynchronously while still having access to the outer class. */
    private abstract class AsynchronousCallHelper implements Runnable {
    	protected DongleProtocolHandler outer;
    	
    	protected AsynchronousCallHelper(DongleProtocolHandler outer) {
    		this.outer = outer;
    	}
    }
}
