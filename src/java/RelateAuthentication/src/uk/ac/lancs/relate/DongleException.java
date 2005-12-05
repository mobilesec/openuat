package uk.ac.lancs.relate;

/**
 * This exception is thrown when the dongle communication fails in some fatal way. It should not be
 * used for "expected" error cases, those are better dealt with by return codes.
 */
public class DongleException extends Exception {
	private static final long serialVersionUID = 4732862155123917869L;
	public DongleException(String msg) {
		super(msg);
	}
	public DongleException(String msg, Throwable t) {
		super(msg, t);
	}
}
