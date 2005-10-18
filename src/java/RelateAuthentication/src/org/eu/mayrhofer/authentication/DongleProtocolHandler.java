package org.eu.mayrhofer.authentication;

import java.util.HashMap;

import uk.ac.lancs.relate.SerialConnector;

public class DongleProtocolHandler {
	/** 
	 * The serial port used by this instance. Needs to be set in the constructor.
	 */
	private String serialPort;
	private SerialConnector serialConn;

	 /** 
	  * We use a pseudo-singleton pattern here: for each port, only one instance can exist. This map holds the known instances.
	  */
	private static HashMap instances;
	
	public DongleProtocolHandler(String serial) {
		serialPort = serial;
		serialConn = SerialConnector.getSerialConnector(true);
	}
	
	public void startAuthenticationWith(int remoteRelateId, byte[] nonce, byte[] rfMessage, int rounds) {
		// Connect here to the dongle so that we don't block it when not necessary. This needs better integration with the Relate framework. 
		int localRelateId = serialConn.connect(serialPort, 0, 255);
		if (localRelateId != -1)
			System.out.println("-------- connected successfully to dongle, including first handshake. My ID is " + localRelateId);
		else
			System.out.println("-------- failed to connect to dongle, didn't get my ID.");
	}
}
