/* Copyright Rene Mayrhofer
 * File created 2005-09
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.authentication.relate;

import org.apache.log4j.Logger;
import org.eu.mayrhofer.authentication.AuthenticationEventSender;
import org.eu.mayrhofer.authentication.InterlockProtocol;
import org.eu.mayrhofer.authentication.SafetyBeltTimer;
import org.eu.mayrhofer.authentication.exceptions.*;

import java.security.SecureRandom;

import uk.ac.lancs.relate.core.SerialConnector;
import uk.ac.lancs.relate.core.MessageQueue;
import uk.ac.lancs.relate.core.DeviceException;
import uk.ac.lancs.relate.events.RelateEvent;
import uk.ac.lancs.relate.events.MeasurementEvent;
import uk.ac.lancs.relate.events.AuthenticationEvent;

/** This class implements the host part of the authentication protocol between two Relate dongles. It will prepare
 * the data to initialize the dongle authentication mode (i.e. encrypt the shared secret with a block cipher) and
 * check that the nonce received via the ultrasound delays matches (at least as far as the number of bits being used)
 * the received encrypted nonce. Since the authentication protocol will also run as a background thread, this class
 * will emit AuthenticationEvents. 
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class DongleProtocolHandler extends AuthenticationEventSender {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(DongleProtocolHandler.class);

	/** With the current Relate dongle hard-/firmware, each round of the dongle authentication protocol transports 3 bits
	 * of entropy.
	 */
	private static final int EntropyBitsPerRound = 3;
	
	/** The offset of the bits carrying the delay information in the reported measurement. 
	 * This is 8 bits because the SerialConnector already converts the dongle measurements to 
	 * millimeter instead of providing the 11-bit field the dongle reports. */
	private static final int EntropyBitsOffset = 8;
	
	/** The current length in byte of the nonce (and implicitly the RF messages) expected by all parameters. */
	private static final int NonceByteLength = 16;
	
	/** The number of authentication steps, not including the rounds of the dongles. */
	public static final int AuthenticationStages = 3;

	/** MAGIC VALUE NUMBER 1: Give a maximum of 10 seconds between receiving the authentication 
	 * messages of two consecutive rounds between from the dongle. If it takes longer, this most
	 * probably means that the dongle is stuck and will therefore be reset. The authentication
	 * will then fail with a timeout.
	 * It's magic because there's no real reason for that specific number other than trial&error.
	 * 
	 * @see #handleDongleCommunication
	 */
	private final static int MAGIC_1 = 10000;
	
	/** The remote relate id to perform the authentication with. */
	private int remoteRelateId;
	
	/** The serial port to use for talking to the dongle. */
	private String serialPort;
	
	/** The reference measurement to use when examining the delayed US pulses from the remote. This is is millimeters. */
	private int referenceMeasurement;
	
	/** If set to true, the JSSE will be used, if set to false, the Bouncycastle Lightweight API. */
	private boolean useJSSE;

	/** A temporary variable holding the shared key. It is used for passing data from
	 * startAuthentication to the Thread's run method.
	 */
	private byte[] sharedKey;

	/** A temporary variable holding the number of rounds. It is used for passing data from
	 * startAuthentication to the Thread's run method.
	 */
	private int rounds;
	
	/** These are merely for keeping statistics of how quickly the protocol runs.
	 * The methods get*Time should be used for fetching the statistics. 
	 */
	private long startTime, commandSentTime, completedTime;
	
	/** Initializes the dongle protocol handler by setting the serialPort and remoteRelateId members.
	 * 
	 * @param remoteRelateId The remote relate id to perform the authentication with.
	 * @param serialPort The serial port to which the dongle is connected.
	 * @param useJSSE If set to true, the JSSE API with the default JCE provider of the JVM will be used
	 *                for cryptographic operations. If set to false, an internal copy of the Bouncycastle
	 *                Lightweight API classes will be used.
	 */
	public DongleProtocolHandler(String serialPort, int remoteRelateId, boolean useJSSE) {
		this.serialPort = serialPort;
		this.remoteRelateId = remoteRelateId;
		this.useJSSE = useJSSE;
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
	 * @param interlockUs
	 *            The received nonce transported by the ultrasond delays from
	 *            the remote dongle will be assembled with this InterlockProtocol
	 *            instance. It is assumed that the object is initialized by the 
	 *            caller with the same number of rounds used here, but the 
	 *            messages are added by this method. The final, assembled message 
	 *            will only carry (EntropyBitsPerRound * rounds) bits starting from 
	 *            the LSB of the array that can be returned by interlockUs.
	 *            Higher bits will not be added or touched in any way.
	 * @param interlockRf
	 *            The RF message <b>received</b> from the remote dongle will be
	 *            assembled with this InterlockProtocol instance. It is assumed 
	 *            that the object is initialized by the caller with the
	 *            same number of rounds user here, but the messages are added by
	 *            this method.
	 * @return true if the authentication protocol completed, false otherwise.
	 *         If true, returns the received RF message in receivedRfMessage. If
	 *         false, this method already raises a proper authentication failure
	 *         event, so the caller doesn't need to.
	 * @throws DongleAuthenticationProtocolException
	 */
	private boolean handleDongleCommunication(byte[] nonce, byte[] sentRfMessage, 
			int rounds, InterlockProtocol interlockUs, InterlockProtocol interlockRf) 
			throws DongleAuthenticationProtocolException, InternalApplicationException {
		// first check the parameters
		if (remoteRelateId < 0)
			throw new DongleAuthenticationProtocolException("Remote relate id must be >= 0.");
		if (nonce == null || nonce.length != NonceByteLength)
			throw new DongleAuthenticationProtocolException("Expecting random nonce with a length of " + NonceByteLength + " Bytes.");
		if (sentRfMessage == null || sentRfMessage.length != NonceByteLength)
			throw new DongleAuthenticationProtocolException("Expecting RF message with a length of " + NonceByteLength + " Bytes.");
		if (rounds < 2)
			throw new DongleAuthenticationProtocolException("Need at least 2 rounds for the interlock protocol to be secure.");

		SerialConnector serialConn;
		try {
			// TODO: maybe stop hard-coding the device type for the "old" dongle
			serialConn = SerialConnector.getSerialConnector(serialPort, 1);
		}
		catch (Exception e) {
			throw new InternalApplicationException("Could not get serial connector object.", e);
		}
		
		// fetch our own relate id from the serial connector (which must be connected by now)
		if (! serialConn.isOperational())
			throw new InternalApplicationException("Error: connection to dongle has not yet been established!");
		int localRelateId = serialConn.getLocalRelateId();
		if (localRelateId == -1)
			throw new InternalApplicationException("Error: local relate id is reported as -1, which is an error case!!");
		
		// This message queue is used to receive events from the dongle.
		MessageQueue eventQueue = new MessageQueue();
		serialConn.registerEventQueue(eventQueue);
		
		// HACK HACK HACK HACK: before interrupting the master dongle (higher id), give the slave some time to
		// complete its cycle, which should hopefully make it quicker for both
		// (and yes, it's cheating to do it before taking the start time)
		/*if (localRelateId > remoteRelateId) {
			logger.info("Master waiting before being interrupted");
			// yippey, I'm the master, thus I need to wait
			try {
				Thread.sleep(5000);
			}
			catch (InterruptedException e) {}
		}*/
		
		// start measuring the time for authentication noew
		startTime = System.currentTimeMillis();

		// construct the start-of-authentication message and sent it to the dongle
		try {
			if (!serialConn.startAuthenticationWith(remoteRelateId, nonce, sentRfMessage, rounds, EntropyBitsPerRound)) {
				logger.error("ERROR: could not send start-of-authentication packet to dongle");
				raiseAuthenticationFailureEvent(new Integer(remoteRelateId), null, "Unable to send start-of-authentication packet to dongle, resetting it.");
				// also reset the dongle, just in case the command was sent but just not acknowledged
				serialConn.switchDiagnosticMode(false);
				return false;
			}
		} 
		catch (DeviceException e) {
			logger.fatal("ERROR: could not send start-of-authentication packet to dongle: " + e);
			raiseAuthenticationFailureEvent(new Integer(remoteRelateId), e, "Unable to send start-of-authentication packet to dongle, resetting it.");
			serialConn.switchDiagnosticMode(false);
			return false;
		}

		// record this step
		commandSentTime = System.currentTimeMillis();
		logger.info("Sending command to dongle took " + (commandSentTime - startTime) + " ms.");

		raiseAuthenticationProgressEvent(new Integer(remoteRelateId), 2, AuthenticationStages + rounds, "Initiated authentication mode in dongle");
		
		// and wait for the measurements and authentication data to be received
		int lastAuthPart = -1, lastCompletedRound = -1;

		/* This allows a clean exit when the dongle doesn't advance its reported round for too long
		 * (e.g. when either the other side never enters authentication mode or it has been reset for
		 * some reason).  
		 */
		SafetyBeltTimer timer = new SafetyBeltTimer(MAGIC_1);
		
		while (lastCompletedRound < rounds-1 && !timer.isTriggered()) {
			while (eventQueue.isEmpty())
				eventQueue.waitForMessage(500);
			RelateEvent e = (RelateEvent) eventQueue.getMessage();
			if (e == null) {
				logger.warn("Warning: got null message out of message queue! This should normally not happen. " +
						"Is the dongle asleep or malfunctioning so that it doesn't generate any messages?");
				continue;
			}
			if (e instanceof AuthenticationEvent && ((AuthenticationEvent) e).getRemoteDeviceId() == remoteRelateId) {
				AuthenticationEvent ae = (AuthenticationEvent) e;

				// sanity check
				if (ae.getRound() > rounds) {
					logger.warn("Ignoring authentication part from dongle " + remoteRelateId + 
							": round " + ae.getRound() + 
							(ae.getAcknowledgment() ? " with" : " without") + " ack out of " + rounds + ": " +
							SerialConnector.byteArrayToHexString(ae.getAuthenticationPart()) + ". Reason: only expected + " + rounds + " rounds.");
					continue;
				}
				// authentication info event: just remember the bits received with it
				if (! interlockRf.addMessage(ae.getAuthenticationPart(), ae.getRound()-1)) {
					logger.warn("Could not add authentication part from dongle " + remoteRelateId + 
							": round " + ae.getRound() + 
							(ae.getAcknowledgment() ? " with" : " without") + " ack out of " + rounds + " to interlock protocol: " +
							SerialConnector.byteArrayToHexString(ae.getAuthenticationPart()));
					continue;
				}
				logger.info("Received authentication part from dongle " + remoteRelateId + 
						": round " + ae.getRound() + 
						(ae.getAcknowledgment() ? " with" : " without") + " ack out of " + rounds + " : " +
						SerialConnector.byteArrayToHexString(ae.getAuthenticationPart()));

				// the last messages might not even carry any bits at all
				lastAuthPart = ae.getRound()-1;
			}
			else if (e instanceof MeasurementEvent && ((MeasurementEvent) e).getDeviceId() == localRelateId &&  
					((MeasurementEvent) e).getMeasurement().getRelatumId() == remoteRelateId) {
				MeasurementEvent me = (MeasurementEvent) e;
				
				if (me.getMeasurement().getTransducers() == 0) {
					//logger.info("Measurement is reported with 0 valid transducers, using it anyway.");
					logger.debug("Discarding invalid measurement in authentication mode: 0 valid transducers.");
					continue;
				}
				if (me.getMeasurement().getDistance() == 4094) {
					logger.debug("Discarding invalid measurement in authentication mode: 4094 reported by dongle");
					continue;
				}

				// measurement event for the authentication partner: re-use the round from the authentication info event
				int delayedMeasurement = me.getMeasurement().getDistance();
				int delta = Math.abs(delayedMeasurement - referenceMeasurement);

				// first extract the delay bits (since it is delayed, it is guaranteed to be longer than the reference)
				// WATCHME: at the moment we use only 3 bits, but that might change....
				// if it's negative because of noise, we'll just get the 2's complement, so correct that
				byte delay = (byte) (delta >> EntropyBitsOffset);
				// special case: a bit error can carry over
				if ((delta & 0x80) != 0)
					delay++;
				// if it is the last round, it might have less bits (but only for >= 43 rounds)
				// if more than 43 rounds are used, it will basically overflow - allow that
				int curBits = (NonceByteLength * 8) - (EntropyBitsPerRound * lastAuthPart) % (NonceByteLength * 8) >= EntropyBitsPerRound ? 
						EntropyBitsPerRound : 
						(NonceByteLength * 8) - (EntropyBitsPerRound * lastAuthPart) % (NonceByteLength * 8);
				
				// sanity check
				if (lastAuthPart >= rounds) {
					logger.warn("Ignoring delayed measurement to dongle " + remoteRelateId + ": " + delayedMeasurement +
							", round " + (lastAuthPart+1) +
							", delay in mm=" + (delayedMeasurement-referenceMeasurement) + ", computed nonce part from delay: " + (delay /*& 0x07*/) + 
							" " + SerialConnector.byteArrayToBinaryString(new byte[] {delay}) + " (using " + curBits + " bits). Reason: only expected " + rounds + " rounds.");
					continue;
				}
				// even more sanity...
				if (lastAuthPart < 0) {
					logger.warn("Ignoring delayed measurement to dongle " + remoteRelateId + ": " + delayedMeasurement +
							", round " + (lastAuthPart+1) +
							", delay in mm=" + (delayedMeasurement-referenceMeasurement) + ", computed nonce part from delay: " + (delay /*& 0x07*/) + 
							" " + SerialConnector.byteArrayToBinaryString(new byte[] {delay}) + " (using " + curBits + " bits). Reason: got measurement before first authentication packet");
					continue;
				}
				// still do a sanity check (within our accuracy range)
				if (delayedMeasurement - referenceMeasurement <= -(1 << EntropyBitsOffset)) {
					logger.warn("Discarding invalid measurement in authentication mode: smaller than reference (got " + delayedMeasurement + 
							", reference is " + referenceMeasurement);
					continue;
				}

				// and add to the receivedNonce for later comparison
				if (! interlockUs.addMessage(new byte[] {delay}, 
						(lastAuthPart * EntropyBitsPerRound) % (NonceByteLength * 8), 
						curBits, lastAuthPart)) {
					logger.warn("Could not add delayed measurement from dongle " + remoteRelateId + 
							": " + delayedMeasurement + ", round " + (lastAuthPart+1) +
							", delay in mm=" + (delayedMeasurement-referenceMeasurement) + 
							", computed nonce part from delay: " + (delay /*& 0x07*/) + 
							" " + SerialConnector.byteArrayToBinaryString(new byte[] {delay})); 
					continue;
				}
				lastCompletedRound = lastAuthPart;
				logger.info("Received delayed measurement to dongle " + remoteRelateId + ": " + delayedMeasurement + 
						", delay in mm=" + (delayedMeasurement-referenceMeasurement) + ", computed nonce part from delay: " + (delay /*& 0x07*/) + 
						" " + SerialConnector.byteArrayToBinaryString(new byte[] {delay}) + " (using " + curBits + " bits)");
				logger.debug("reference=" + referenceMeasurement + ", delta=" + delta + " (" + Integer.toBinaryString(delta) + ")");
				raiseAuthenticationProgressEvent(new Integer(remoteRelateId), 3 + lastCompletedRound+1, AuthenticationStages + rounds, "Got delayed measurement at round " + (lastCompletedRound+1));

				// here we have progress, so reset the timer
				timer.reset();
			}
		}
		
		if (lastCompletedRound < rounds-1) {
			/* The loop was left because of a timeout, so reset the dongle and
			 * exit cleanly with a failure event. 
			 */
			logger.error("Dongle authentication timed out after " + (System.currentTimeMillis() - startTime) + 
					" ms. Last received round is " + (lastCompletedRound+1) + 
					". Resetting dongle (by turning off diagnostic mode) and generating an authentication failed event.");
			serialConn.switchDiagnosticMode(false);
			raiseAuthenticationFailureEvent(new Integer(remoteRelateId), null, 
					"Timeout while waiting for authentication messages from local dongle. Most probable cause is that " +
					"the other dongle never entered authentication mode or that it has been interrupted or disappeared.");
			return false;
		}
		
		// and stop measuring now
		completedTime = System.currentTimeMillis();
		logger.info("Dongle authentication protocol took " + (completedTime - startTime) + " ms.");
		
		serialConn.unregisterEventQueue(eventQueue);

		return true;
	}

	/** A helper method called in the background thread. Exists for the sole purpose of
	 * better code structure.
	 */
	private void handleCompleteProtocol() throws InternalApplicationException, DongleAuthenticationProtocolException {
		// first create the local nonce
        SecureRandom r = new SecureRandom();
        byte[] nonce = new byte[NonceByteLength];
        r.nextBytes(nonce);

   		logger.info("Starting authentication protocol with remote dongle " + remoteRelateId);
   		logger.debug("My shared authentication key is " + SerialConnector.byteArrayToBinaryString(sharedKey));
   		logger.debug("My nonce is " + SerialConnector.byteArrayToBinaryString(nonce));
    		
   		InterlockProtocol interlockRf = new InterlockProtocol(sharedKey, rounds, nonce.length*8, "RF at " + serialPort, useJSSE);
   		byte[] rfMessage = interlockRf.encrypt(nonce);
   		// this instance is only used for assembling the plain-text nonce received via US
   		InterlockProtocol interlockUs = new InterlockProtocol(null, rounds, EntropyBitsPerRound*rounds, "US at " + serialPort, useJSSE);
		
		if (rfMessage.length != NonceByteLength) {
			logger.error("Encryption went wrong, got "
					+ rfMessage.length + " bytes");
			raiseAuthenticationFailureEvent(new Integer(remoteRelateId), null, "Encryption went wrong, got " + rfMessage.length + " bytes instead of " + NonceByteLength + ".");
			return;
		}
		logger.debug("My RF packet is " + SerialConnector.byteArrayToBinaryString(rfMessage));

		raiseAuthenticationProgressEvent(new Integer(remoteRelateId), 1, AuthenticationStages + rounds, "Encrypted nonce");
		
		if (!handleDongleCommunication(nonce, rfMessage, rounds, interlockUs, interlockRf)) {
			// all occurances of return false already raise an authentication failure event, so no need to do it here
			return;
		}
		
		// check if everything has been received correctly
		byte[] receivedDelays = interlockUs.reassemble();
		if (receivedDelays == null) {
			raiseAuthenticationFailureEvent(new Integer(remoteRelateId), null, 
					"Did not receive all required delayed measurements from remote dongle");
			return;
		}
		byte[] receivedRfMessage = interlockRf.reassemble();
		if (receivedRfMessage == null) {
			raiseAuthenticationFailureEvent(new Integer(remoteRelateId), null, 
				"Did not receive all required authentication parts from remote dongle");
			return;
		}

		logger.debug("Received RF packet is " + SerialConnector.byteArrayToBinaryString(receivedRfMessage));
		logger.debug("Received delays have been concatenated to " + SerialConnector.byteArrayToBinaryString(receivedDelays));

		// check that the delays match the (encrypted) message sent by the remote
		byte[] receivedNonce = interlockRf.decrypt(receivedRfMessage);

		if (receivedNonce.length != NonceByteLength) {
			logger.error("Decryption went wrong, got "
					+ receivedNonce.length + " bytes");
			raiseAuthenticationFailureEvent(new Integer(remoteRelateId), null, "Decryption went wrong, got " + receivedNonce.length + " bytes instead of " + NonceByteLength + ".");
			return;
		}

		logger.debug("Received nonce is " + SerialConnector.byteArrayToBinaryString(receivedNonce));

		raiseAuthenticationProgressEvent(new Integer(remoteRelateId), 3 + rounds, AuthenticationStages + rounds, "Decrypted remote message");

		// the lower bits must match
		int numBitsToCheck = EntropyBitsPerRound * rounds <= rfMessage.length * 8 ? 
					EntropyBitsPerRound * rounds : 
					rfMessage.length * 8;
		if (compareBits(receivedNonce, receivedDelays, numBitsToCheck)) {
			logger.info("Ultrasound delays match received nonce, authentication succeeded");
			raiseAuthenticationSuccessEvent(new Integer(remoteRelateId), null);
		}
		else {
			logger.warn("Received RF packet length is " + receivedRfMessage.length);
			logger.warn("Decrypted nonce length is " + receivedNonce.length);
			logger.warn("Received delays length is " + receivedDelays.length);
			logger.warn("Ultrasound delays do not match received nonce (checking " + numBitsToCheck + " bits)!");
			logger.warn("Expected: " + SerialConnector.byteArrayToBinaryString(receivedNonce));
			logger.warn("Got:      " + SerialConnector.byteArrayToBinaryString(receivedDelays));
			logger.warn("Hamming distance between the strings is " + hammingDistance(receivedNonce, receivedDelays, numBitsToCheck));
			raiseAuthenticationFailureEvent(new Integer(remoteRelateId), null, "Ultrasound delays do not match received nonce");
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
		// this variant is usually faster when the strings are different
		/*for (int i=0; i<numBits; i++)
			if (((s[i/8]) & (1 << (i%8))) != ((t[i/8]) & (1 << (i%8))))
				return false;
		return true;*/
		return hammingDistance(s, t, numBits) == 0;
	}
	
	/** Computes the hamming distance between two bit strings, starting from the LSB.
	 * 
	 * This method is only public for the JUnit tests, there's probably not much use for it elsewhere.
	 * 
	 * @param s The first bit string.
	 * @param t The second bit string.
	 * @param numBits The number of bits to compare (starting at LSB).
	 * @return The hamming distance between the strings s and t (for numBits), i.e. the number of different bits.
	 */
	static public int hammingDistance(byte[]s, byte[] t, int numBits) {
		int dist=0;
		for (int i=0; i<numBits; i++)
			if (((s[i/8]) & (1 << (i%8))) != ((t[i/8]) & (1 << (i%8))))
				dist++;
		return dist;
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
		logger.debug("Starting authentication with " + rounds + " rounds and reference=" + referenceMeasurement);
		
		new Thread(new AsynchronousCallHelper(this) { public void run() {
			try {
				outer.handleCompleteProtocol();
			}
			catch (InternalApplicationException e) {
				logger.error("InternalApplicationException. This should not happen: " + e + "\n" + e.getStackTrace());
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
    
    /** Returns the time that it took to send the start-of-authentication command to the dongle 
     * 
     * @return Time for sending the command in ms.
     */
    public int getSendCommandTime() {
    		return (int) (commandSentTime - startTime);
    }
    
    /** Returns the time it took to complete the dongle interlock protocol, i.e. the time between
     * successfully sending the start-of-authentication command to the dongle and the receipt of the
     * last round message.
     * 
     * @return Time for the dongle interlock protocol in ms.
     */
    public int getDongleInterlockTime() {
    		return (int) (completedTime - commandSentTime);
    }
    
    /** Returns the ID of the remote relate dongle with which this authentication
     * protocol is run.
     * 
     * @return The remote relate ID used as the authentication peer.
     */
    public int getRemoteRelateId() {
    	return remoteRelateId;
    }
}
