/*****************************************************************************
 *  Project: Gibraltar-Webinterface
 *  Description: webinterface for the firewall gibraltar
 *  Filename: $RCSfile$
 *  Author: Andreas W�ckl
 *  Copyright: Andreas W�ckl 2001
 *  Project version tag: $Name$
 *  File version: $Revision: 1016 $
 *  Last changed at: $Date: 2003-07-25 11:44:01 +0200 (Fre, 25 Jul 2003) $
 *  Last changed by: $Author: awoeckl $
 *****************************************************************************/

package org.eu.mayrhofer.channel;

/**
 *  The exception <code>ExitCodeException</code> is thrown if a systam command returned an error code != 0
 */
public class ExitCodeException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * paremeterless Constructor
	 */
	public ExitCodeException() {
		super();
	}

	/**
	 * Constructor that initializes with a message
	 *
	 * @param msg   the message to display
	 */
	public ExitCodeException(String msg) {
		super(msg);
	}
}