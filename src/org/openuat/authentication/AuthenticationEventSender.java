/* Copyright Rene Mayrhofer
 * File created 2005-09
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.authentication;

import java.util.Vector;

import org.apache.log4j.Logger;
import org.openuat.util.LoggingHelper;

/** This is an abstract class to encapsulate the notion of an authentication event sender. The basic
 * capability is to send events about the the progress of the
 * respective authentication, i.e. AuthenticationSuccess, AuthenticationFailure and
 * AuthenticationProgress events. 
 *
 * @author Rene Mayrhofer
 * @version 1.0
 */
public abstract class AuthenticationEventSender {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger("org.openuat.authentication.AuthenticationEventSender" /*AuthenticationEventSender.class*/);

	/** The list of listeners that are notified of authentication events. */
    protected Vector eventsHandlers = new Vector();

    /** Register a listener for receiving events. */
    public void addAuthenticationProgressHandler(AuthenticationProgressHandler h) {
    	if (! eventsHandlers.contains(h))
    		eventsHandlers.addElement(h);
    }

    /** De-register a listener for receiving events. */
    public boolean removeAuthenticationProgressHandler(AuthenticationProgressHandler h) {
   		return eventsHandlers.removeElement(h);
    }

    /** Helper method for sending an AuthenticationSuccess event to all registered listeners (if any). */
    protected void raiseAuthenticationSuccessEvent(Object remote, Object result) {
    	if (eventsHandlers != null)
    		for (int i = 0; i < eventsHandlers.size(); i++) {
    			AuthenticationProgressHandler h = (AuthenticationProgressHandler) eventsHandlers.elementAt(i); 
    			try {
    				h.AuthenticationSuccess(this, remote, result);
    			}
    			catch (Exception e) {
    				logger.error("Authentication success handler '" + h + 
    						"' caused exception '" + e + "', ignoring it here");
    				LoggingHelper.debugWithException(logger, null, e);
    			}
    		}
    }

    /** Helper method for sending an AuthenticationFailure event to all registered listeners (if any). */
    protected void raiseAuthenticationFailureEvent(Object remote, Exception e, String msg) {
    	if (eventsHandlers != null)
    		for (int i = 0; i < eventsHandlers.size(); i++) {
    			AuthenticationProgressHandler h = (AuthenticationProgressHandler) eventsHandlers.elementAt(i); 
    			try {
    				h.AuthenticationFailure(this, remote, e, msg);
    			}
    			catch (Exception ee) {
    				logger.error("Authentication failure handler '" + h + 
    						"' caused exception '" + ee + "', ignoring it here");
    				LoggingHelper.debugWithException(logger, null, e);
    			}
    		}
    }

    /** Helper method for sending an AuthenticationProgress event to all registered listeners (if any). */
    protected void raiseAuthenticationProgressEvent(Object remote, int cur, int max, String msg) {
    	if (eventsHandlers != null)
    		for (int i = 0; i < eventsHandlers.size(); i++) {
    			AuthenticationProgressHandler h = (AuthenticationProgressHandler) eventsHandlers.elementAt(i); 
    			try {
    				h.AuthenticationProgress(this, remote, cur, max, msg);
    			}
    			catch (Exception e) {
    				logger.error("Authentication progress handler '" + h + 
    						"' caused exception '" + e + "', ignoring it here");
    				LoggingHelper.debugWithException(logger, null, e);
    			}
    		}
    }
}
