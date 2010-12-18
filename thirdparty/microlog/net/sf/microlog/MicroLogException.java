package net.sf.microlog;

/**
 * This exception class is used when something inside MicroLog has gone wrong.
 * 
 * @author Johan Karlsson
 */
public class MicroLogException extends Exception {

	/**
	 * Create a MicroLogException.
	 */
	public MicroLogException() {
		super();
	}

	/**
	 * Create a MicroLogException.
	 * @param message the detailed message.
	 */
	public MicroLogException(String message) {
		super(message);
	}

}
