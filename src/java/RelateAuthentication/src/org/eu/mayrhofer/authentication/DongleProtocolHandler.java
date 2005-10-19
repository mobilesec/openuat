package org.eu.mayrhofer.authentication;

import org.eu.mayrhofer.authentication.exceptions.*;

import java.util.HashMap;

import uk.ac.lancs.relate.SerialConnector;

/**
 * 
 * @author Rene Mayrhofer
 *
 */
public class DongleProtocolHandler {
	/** 
	 * The serial port used by this instance. Needs to be set in the constructor.
	 */
	private String serialPort;
	/** This is the SerialConnector instance used to communicate with the dongle. */
	private SerialConnector serialConn;
	
	/** With the current Relate dongle hard-/firmware, each round of the dongle authentication protocol transports 3 bits
	 * of entropy.
	 */
	private int EntropyBitsPerRound = 3; 

	 /** 
	  * We use a pseudo-singleton pattern here: for each port, only one instance can exist. This map holds the known instances.
	  */
	//private static HashMap instances;

	/** Initializes the serialConn object. */
	public DongleProtocolHandler(String serial) {
		serialPort = serial;
		serialConn = SerialConnector.getSerialConnector(true);
	}
	
	/** This method performs a full authentication of the pre-established shared secrets with another Relate dongle.
	 * 
	 * In the current implementation, it waits for the SerialConnector background thread to send events. If this thread
	 * blocks, the authentication will fail with a timeout.
	 * 
	 * @param remoteRelateId The remote dongle id to authenticate with.
	 * @param nonce The random nonce used to derive the ultrasound delays from. 
	 * @param rfMessage The RF message transported over the Relate RF network to the remote dongle. At the moment, it is an encrypted version of the random nonce.
	 * @param rounds The number of rounds to use. Due to the protocol and hardware limitations, the security of this authentication is given by round * EnropyBitsPerRound.
	 * @return true if the authentication succeeded, false otherwise
	 * @throws DongleAuthenticationProtocolException
	 */
	public boolean authenticateWith(int remoteRelateId, byte[] nonce, byte[] rfMessage, int rounds) throws DongleAuthenticationProtocolException {
		// first check the parameters
		if (remoteRelateId < 0)
			throw new DongleAuthenticationProtocolException("Remote relate id must be >= 0.");
		if (nonce == null || nonce.length != 16)
			throw new DongleAuthenticationProtocolException("Expecting random nonce with a length of 16 Bytes.");
		if (rfMessage == null || rfMessage.length != 16)
			throw new DongleAuthenticationProtocolException("Expecting RF message with a length of 16 Bytes.");
		if (rounds < 2)
			throw new DongleAuthenticationProtocolException("Need at least 2 rounds for the interlock protocol to be secure.");
		
		// Connect here to the dongle so that we don't block it when not necessary. This needs better integration with the Relate framework. 
		int localRelateId = serialConn.connect(serialPort, 0, 255);
		if (localRelateId != -1) {
			System.out.println("-------- connected successfully to dongle, including first handshake. My ID is " + localRelateId);
			return false;
		}
		else
			System.out.println("-------- failed to connect to dongle, didn't get my ID.");
		
		serialConn.run();
		
		serialConn.disconnect();
		
		return true;
	}
}
