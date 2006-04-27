/* Copyright Rene Mayrhofer
 * File created 2005-09
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.authentication;

import java.util.LinkedList;
import java.util.ListIterator;

import org.apache.log4j.Logger;

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
	private static Logger logger = Logger.getLogger(AuthenticationEventSender.class);

	/** The list of listeners that are notified of authentication events. */
    protected LinkedList eventsHandlers = new LinkedList();

    /** Register a listener for receiving events. */
    public void addAuthenticationProgressHandler(AuthenticationProgressHandler h) {
    	if (! eventsHandlers.contains(h))
    		eventsHandlers.add(h);
    }

    /** De-register a listener for receiving events. */
    public boolean removeAuthenticationProgressHandler(AuthenticationProgressHandler h) {
   		return eventsHandlers.remove(h);
    }

    /** Helper method for sending an AuthenticationSuccess event to all registered listeners (if any). */
    protected void raiseAuthenticationSuccessEvent(Object remote, Object result) {
    	if (eventsHandlers != null)
    		for (ListIterator i = eventsHandlers.listIterator(); i.hasNext(); ) {
    			AuthenticationProgressHandler h = (AuthenticationProgressHandler) i.next(); 
    			try {
    				h.AuthenticationSuccess(this, remote, result);
    			}
    			catch (Exception e) {
    				logger.error("Authentication success handler '" + h + 
    						"' caused exception '" + e + "', ignoring it here");
    			}
    		}
    }

    /** Helper method for sending an AuthenticationFailure event to all registered listeners (if any). */
    protected void raiseAuthenticationFailureEvent(Object remote, Exception e, String msg) {
    	if (eventsHandlers != null)
    		for (ListIterator i = eventsHandlers.listIterator(); i.hasNext(); ) {
    			AuthenticationProgressHandler h = (AuthenticationProgressHandler) i.next(); 
    			try {
    				h.AuthenticationFailure(this, remote, e, msg);
    			}
    			catch (Exception ee) {
    				logger.error("Authentication failure handler '" + h + 
    						"' caused exception '" + ee + "', ignoring it here");
    			}
    		}
    }

    /** Helper method for sending an AuthenticationProgress event to all registered listeners (if any). */
    protected void raiseAuthenticationProgressEvent(Object remote, int cur, int max, String msg) {
    	if (eventsHandlers != null)
    		for (ListIterator i = eventsHandlers.listIterator(); i.hasNext(); ) {
    			AuthenticationProgressHandler h = (AuthenticationProgressHandler) i.next(); 
    			try {
    				h.AuthenticationProgress(this, remote, cur, max, msg);
    			}
    			catch (Exception e) {
    				logger.error("Authentication progress handler '" + h + 
    						"' caused exception '" + e + "', ignoring it here");
    			}
    		}
    }
}
