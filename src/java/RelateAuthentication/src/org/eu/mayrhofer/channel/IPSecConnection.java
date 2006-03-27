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
 * implemented via IPSec. It offers additional init and start methods that can be used to
 * specify more details for the IPSec connection than what can be specified with the
 * general SecureChannel interface. Additionally, it adds a method to import 
 * certificates depending on how the unterlying platform manages them.
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
	
	/** Establishes and starts a new secure channel to another host. 
	 * init() needs to be called once before this method.
	 * @see #init
	 * 
	 * @param caDistinguishedName The remote certificate authority's distinguisged
	 *                            name. This CA should be used to sign both the the
	 *                            remote host's certificate and this host's 
	 *                            certificate, which must have been imported before
	 *                            calling this method (e.g. with importCertificate).
	 *                            This parameter can be set to null if no specific CA
	 *                            is to be enforced, but any certificate that the local
	 *                            host accepts as valid is OK for the remote end.
	 * @param persistent If set to true, the secure channel will persist accross 
	 *                   application restarts (and might persist across reboots).
	 *                   This is dependent on the implementation, only some might
	 *                   support persistance.
	 * @return true if the channel could be started, false otherwise.
	 * 
	 * @see #importCertificate(String, String, boolean)
	 */ 
	public boolean start(String caDistinguishedName, boolean persistent);
	
	/** This native method allows to import an X.509 certificate into the appropriate location
	 * for use with IPSec authentication.
	 * 
	 * @param file The file name of the certificate to import. It must point to a PKCS#12 encoded file that
	 *             contains the X.509 client certificate and the corresponding private key that should be used
	 *             for authentication as well as the CA certificate chain up to the root CA certificate that
	 *             represents the trusted path of the client certificate. The other end of the IPSec tunnel
	 *             must present a certificate that has been signed by the same CA as the client certificate
	 *             imported from this file.
	 * @param password The password necessary to decrypt the PKCS#12 file.
	 * @param overwriteExisting If true, existing certificates with the same common name and serial number and
	 *                          signed by the same CA will be overwritten.
	 * @return 0 if the certificates and the private key could be imported successfully, 
	 *         1 if the file could not be found or opened,
	 *         2 if the private key could not be decrypted (password mismatch),
	 *         3 if it could not be decoded,
	 *         4 if importing failed,
	 *         5 if (at least one of the) certificates existed already and overwriteExisting was set to false
	 *         5 if anything else went wrong (like parameter error).
	 */
	public int importCertificate(String file, String password, boolean overwriteExisting);
}
