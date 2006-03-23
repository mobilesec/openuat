/* Copyright Rene Mayrhofer
 * File created 2006-03-23
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.channel;

/** This interface represents a secure channel to another (usually remote) device,
 * implemented via IPSec. It offers an additional init method that can be used to
 * specify more details for the IPSec connection than what can be specified with the
 * general SecureChannel interface.
 *  
 * Specific implementations take care of constructing, terminating, and querying the
 * secure channel.
 * 
 * All implementations should take care to clean up connections if they have not been
 * set to be persistent.
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public interface IPSecConnection extends SecureChannel {
	/** Initializes an instance of an IPSec connection. The minimal initialization is
	 * to remember the remote host description for future calls to start() and stop(),
	 * but the implementation might do additional tasks when necessary. 
	 * 
	 * This method is an alternative to the init method defined by the SecureChannel
	 * interface. <b>Either of them must be called before any of the others.</b>
	 *
	 * @param remoteHost The remote host to establish the connection to. This string can 
	 *                   either be a hostname, or an IP (version 4 or 6) address.
	 * @param remoteNetwork The remote network behind the IPSec gateway specified with
	 *                      remoteHost, if any. This parameter may be null to indicate
	 *                      that no remote network should be used, but that the IPSec
	 *                      connection should be created only for reaching the remote
	 *                      host. Specifically, if this parameter is set to a network
	 *                      (in IPv4 or IPv6 address notation), then an IPsec <b>tunnel</b>
	 *                      connection will be created. If set to null, an IPSec
	 *                      <b>transport</b> connection will be created.
	 * @param remoteNetmask If remoteNetwork has been set, this parameter should be set
	 *                      to the remote netmask in CIDR notation, i.e. the number of bits
	 *                      that represent the remote network. It must be between 0 and 32
	 *                      for IPv4 remote networks and between 0 and 128 for IPv6 remote
	 *                      networks. If remoteNetwork is null, this parameter is ignored.
	 * @return true if the channel could be initialized, false otherwise. It will return
	 *         false if the channel has already been initialized previously.
	 */
	public boolean init(String remoteHost, String remoteNetwork, int remoteNetmask);
}
