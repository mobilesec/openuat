package org.eu.mayrhofer.channel;

public class IPSecConnection_Linux_Openswan implements SecureChannel {
	/** Creates a new connection entry for openswan/strongswan/freeswan and tries to
	 * start that connection.
	 * 
	 * @param remoteHost The IP address or host name of the remote host.
	 * @param sharedSecret The PSK to use - this byte array will be BASE64-encoded to form a textual representation.
	 * @param persistent Supported. If set to true, the connection will be set to auto=start, if set to false,
	 *                   it will be set to auto=add.
	 */
	public boolean start(String remoteHost, byte[] sharedSecret, boolean persistent) {
		return true;
	}
	
	public boolean stop() {
		return true;
	}
	
	public boolean isEstablished() {
		return true;
	}
}
