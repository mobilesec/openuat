/* Copyright Rene Mayrhofer
 * File created 2006-03-24
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.apps;

import java.io.IOException;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.ProgressBar;
import org.eu.mayrhofer.authentication.AuthenticationProgressHandler;
import org.eu.mayrhofer.authentication.RelateAuthenticationProtocol;
import org.eu.mayrhofer.authentication.exceptions.ConfigurationErrorException;
import org.eu.mayrhofer.authentication.exceptions.InternalApplicationException;

import uk.ac.lancs.relate.core.DongleException;
import uk.ac.lancs.relate.core.EventDispatcher;
import uk.ac.lancs.relate.core.MeasurementManager;

/** This class implements the basic functionality of the IPSec connector applications.
 * It is common to both the client end and the admin end and can not be instantiated
 * directly. Client and admin applications should be derived from it.
 * 
 * The AuthenticationSuccess handler is not implemented as it is specific to which
 * end it should represent.
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public abstract class IPSecConnectorCommon implements AuthenticationProgressHandler {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(IPSecConnectorCommon.class);

	/** True if this is the admin end, false if it is the client end.
	 * It just remembers whatever value was given to the constructor.
	 */
	protected boolean adminEnd;
	
	/** Holds the serial port we use to talk to the dongle. It just
	 * remembers whatever value was given to the constructor.
	 */
	protected String serialPort;
	
	/** Holds our one and only instance of the relate authentication
	 * protocol. It is initialized by the constructor. For the client
	 * end, the authentication server is automatically started. 
	 */
	protected RelateAuthenticationProtocol auth;
	
	/** Holds our one and only instance of the measurement manager.
	 * It is initialized by the constructor.
	 */
	protected MeasurementManager manager;
	
	/** This variable <b>must</b> be initalized in the constructor of the 
	 * derived class, or at least before the AuthenticationProgress handler
	 * has a chance of being called.
	 */
	protected ProgressBar authenticationProgress;
	/** This variable <b>must</b> be initalized in the constructor of the 
	 * derived class, or at least before the AuthenticationProgress handler
	 * has a chance of being called.
	 */
	protected Display display;

	protected IPSecConnectorCommon(boolean adminEnd, String serialPort) 
			throws DongleException, ConfigurationErrorException, InternalApplicationException, IOException {
		this.adminEnd = adminEnd;
		this.serialPort = serialPort;

		if (System.getProperty("os.name").startsWith("Windows CE")) {
			PropertyConfigurator.configure("log4j.properties");
		}
		
        EventDispatcher.getDispatcher(new String[] {serialPort});
        manager = new MeasurementManager(serialPort);
		auth = new RelateAuthenticationProtocol(serialPort, manager, !adminEnd, true, null);
		auth.addAuthenticationProgressHandler(this);
		
		if (! adminEnd) {
			auth.startServer();
		}
	}
	
	/** This is an implementation of the AuthenticationProgressHandler interface. */
	public void AuthenticationFailure(Object sender, Object remote, Exception e, String msg)
	{
		logger.info("Received relate authentication failure event with " + remote);
		Throwable exc = e;
		while (exc != null) {
			logger.info("Exception: " + exc);
			exc = exc.getCause();
		}
		if (msg != null)
			logger.info("Message: " + msg);
		
		// TODO: display an error message and ask for retry
	}

	/** This is an implementation of the AuthenticationProgressHandler interface. */
	public void AuthenticationProgress(Object sender, Object remote, int cur, int max, String msg)
	{
		logger.info("Received relate authentication progress event with " + remote + " " + cur + " out of " + max + ": " + msg);
		
		// only update the ProgressBar...
		final int m = max;
		final int c = cur;
		display.asyncExec(new Runnable() { public void run() { 
			authenticationProgress.setMaximum(m); authenticationProgress.setSelection(c); }});
	}
}
