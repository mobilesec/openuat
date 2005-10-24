package org.eu.mayrhofer.authentication;

import java.util.LinkedList;
import java.util.ListIterator;

/** This is an abtract class to encapsulate the notion of a protocol handler. The basic
 * capability of a protocol handler is to send events about the the progress of the
 * respective authentication, i.e. AuthenticationSuccess, AuthenticationFailure and
 * AuthenticationProgress events. 
 *
 * @author Rene Mayrhofer
 */
public abstract class ProtocolHandler {
    /** The list of listeners that are notified of authentication events. */
    static private LinkedList eventsHandlers = new LinkedList();

    /** Register a listener for receiving events. */
    static public void addAuthenticationProgressHandler(AuthenticationProgressHandler h) {
    	if (! eventsHandlers.contains(h))
    		eventsHandlers.add(h);
    }

    /** De-register a listener for receiving events. */
    static public boolean removeAuthenticationProgressHandler(AuthenticationProgressHandler h) {
   		return eventsHandlers.remove(h);
    }

    /** Helper method for sending an AuthenticationSuccess event to all registered listeners (if any). */
    static protected void raiseAuthenticationSuccessEvent(Object remote, Object result) {
    	if (eventsHandlers != null)
    		for (ListIterator i = eventsHandlers.listIterator(); i.hasNext(); )
    			((AuthenticationProgressHandler) i.next()).AuthenticationSuccess(remote, result);
    }

    /** Helper method for sending an AuthenticationFailure event to all registered listeners (if any). */
    static protected void raiseAuthenticationFailureEvent(Object remote, Exception e, String msg) {
    	if (eventsHandlers != null)
    		for (ListIterator i = eventsHandlers.listIterator(); i.hasNext(); )
    			((AuthenticationProgressHandler) i.next()).AuthenticationFailure(remote, e, msg);
    }

    /** Helper method for sending an AuthenticationProgress event to all registered listeners (if any). */
    static protected void raiseAuthenticationProgressEvent(Object remote, int cur, int max, String msg) {
    	if (eventsHandlers != null)
    		for (ListIterator i = eventsHandlers.listIterator(); i.hasNext(); )
    			((AuthenticationProgressHandler) i.next()).AuthenticationProgress(remote, cur, max, msg);
    }
}
