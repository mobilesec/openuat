package org.eu.mayrhofer.authentication;

import uk.ac.lancs.relate.Configuration;
import uk.ac.lancs.relate.SerialConnector;

public class DongleProtocolHandler {
	private uk.ac.lancs.relate.SerialConnector serialConn;
	
	public DongleProtocolHandler(String serial) {
		serialConn = uk.ac.lancs.relate.SerialConnector.getSerialConnector(true);
	}
	
	public void startAuthenticationWith(int remoteRelateId, byte[] nonce, byte[] rfMessage, int rounds) {
		Configuration configuration = new Configuration("./img/", serialConn, true);
		/* wait for the user to specify all fields */
		while (!configuration.fullySpecified()) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
			}
		}
		
		serialConn.connect(null, configuration, 0, 20);
		configuration.hideGUI();
		serialConn.run();
	}
}
