package org.eu.mayrhofer.authentication;

import uk.ac.lancs.relate.SerialConnector;

public class DongleProtocolHandler {
	private String serialPort;
	private SerialConnector serialConn;
	
	public DongleProtocolHandler(String serial) {
		serialPort = serial;
		serialConn = SerialConnector.getSerialConnector(true);
	}
	
	public void startAuthenticationWith(int remoteRelateId, byte[] nonce, byte[] rfMessage, int rounds) {
		//Configuration configuration = new Configuration("./img/", serialConn, true);
		/* wait for the user to specify all fields */
		/*while (!configuration.fullySpecified()) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
			}
		}*/
		
		serialConn.connect(null, /*configuration,*/ serialPort, 0, 20);
		System.out.println("-------- connected successfully to dongle, including first handshake");
		//configuration.hideGUI();
		//serialConn.run();
	}
}
