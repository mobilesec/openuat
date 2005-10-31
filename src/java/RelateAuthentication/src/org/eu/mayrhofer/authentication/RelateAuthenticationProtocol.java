package org.eu.mayrhofer.authentication;

import java.io.*;
import java.net.UnknownHostException;

/// <summary>
/// This is the main class of the relate authentication software: it ties together
/// the host and dongle protocol handlers. Since both handlers work asynchronously
/// in their own threads, this class must also handle the synchronisation between 
/// all events coming in from them.
/// </summary>
public class RelateAuthenticationProtocol extends AuthenticationEventSender {
	public static final int TcpPort = 54321;
	// TODO: make configurable!
	public static final String SerialPort = "/dev/ttyUSB0";

	/** The relate id of the remote device to authenticate with. */
	private byte remoteRelateId;
	/** The hostname/IP address of the remote device to authenticate with. */
	private String remoteHost;
	
	/** The number of rounds used for the spatial authentication protocol.
	 * @see DongleProtocolHandler#handleDongleCommunication
	 */
	private int rounds;
	
	/** Possible value of state, indicates that the authentication has not been started yet. 
	 * @see #state 
	 */
	private final static int STATE_NOT_STARTED = 1;
	/** Possible value of state, indicates that the host authentication is running.
	 * @see #state 
	 */
	private final static int STATE_HOST_AUTH_RUNNING = 2;
	/** Possible value of state, indicates that the dongle authentication is running
	 * (and the host authentication has thus implicitly been completed successfully).
	 * @see #state 
	 */
	private final static int STATE_DONGLE_AUTH_RUNNING = 3;
	/** Possible value of state, indicates that the spatial authentication has been completed successfully.
	 * @see #state 
	 */
	private final static int STATE_SUCCEEDED = 4;
	/** Possible value of state, indicates that the spatial authentication has failed.
	 * @see #state 
	 */
	private final static int STATE_FAILED = 5;
	
	/** The current state of the spatial authentication, one of STATE_NOT_STARTED,
	 * STATE_HOST_AUTH_RUNNING, STATE_DONGLE_AUTH_RUNNING, STATE_SUCCEEDED, STATE_FAILED.
	 * @see #STATE_NOT_STARTED
	 * @see #STATE_HOST_AUTH_RUNNING
	 * @see #STATE_DONGLE_AUTH_RUNNING
	 * @see #STATE_SUCCEEDED
	 * @see #STATE_FAILED
	 */ 
	private int state = STATE_NOT_STARTED;
	
	/** If the state is STATE_DONGLE_AUTH_RUNNING or STATE_SUCCEEDED, this contains
	 * the secret key shared with the other device.
	 */
	private byte[] sharedKey = null;
	
	/** Initialized the authentication object with the contact data of the remote device to authenticate with.
	 * 
	 * @param remoteHost The hostname/IP address of the remote device.
	 * @param remoteRelateId The relate id of the remote device.
	 */
	public RelateAuthenticationProtocol(String remoteHost, byte remoteRelateId) {
		this.remoteHost = remoteHost;
		this.remoteRelateId = remoteRelateId;
	}
	
	/** Starts the spatial authentication protocol in the background. Listeners should subscribe to
	 * authentication events to get notified about the progress of authentication.
	 * @param rounds The number of rounds that should be used for the dongle authentication. This
	 * directly influences the achieved security level, as described in DongleProtocolHandler#handleDongleCommunication.
	 * @see AuthenticationEventSender#addAuthenticationProgressHandler
	 * @see DongleProtocolHandler#handleDongleCommunication
	 */
	public void startAuthentication(int rounds) throws UnknownHostException, IOException {
		// TODO: check state!
		
		/* This is simple in the implementation, because we just need to start the
		 * host authentication here. When that suceeds, the event handler will 
		 * continue to start the dongle authentication.
		 */ 
		state = STATE_HOST_AUTH_RUNNING; 
		this.rounds = rounds;
		/* There is no need to unregister this new object, since it is only 
		 * registered with a temporary HostProtocolHandler object, which will
		 * be garbage collected when its background authentication thread
		 * finishes. */
		HostProtocolHandler.startAuthenticationWith(remoteHost, TcpPort, new HostAuthenticationEventHandler());
	}
	
	/** Small helper function to raise an authentication failure event and set state as well as wipe sharedKey.
	 * 
	 * @param remote The remote device (either InetAddress or Integer for the host address or relate id) with which the authentication failed.
	 * @param e If not null, the exception describing the failure.
	 * @param message If not null, the message describing the failure.
	 */ 
	private void authenticationFailed(Object remote, Exception e, String message) {
		// be sure to wipe the shared key if it has already been set
		if (sharedKey != null) {
			for (int i=0; i<sharedKey.length; i++)
				sharedKey[i] = 0;
			sharedKey = null;
		}
		state = STATE_FAILED;
		raiseAuthenticationFailureEvent(remote, e, message);
	}
	
	/** A helper class for handling the events from HostProtocolHandler. */
	private class HostAuthenticationEventHandler implements AuthenticationProgressHandler {
	    public void AuthenticationSuccess(Object remote, Object result)
	    {
			// TODO: check state!
			
	        System.out.println("Received authentication success event with host " + remote);
	        byte[][] keys = (byte[][]) result;
	        System.out.println("Shared session key is now '" + keys[0] + "' with length " + keys[0].length + ", shared authentication key is now '" + keys[1] + "' with length " + keys[1].length);
	        System.out.println("Starting dongle authentication with remote relate id " + remoteRelateId);
	        // remember the secret key shared with the other device
	        sharedKey = keys[0];
	        // and use the agreed authentication key to start the dongle authentication
	        DongleProtocolHandler dh = new DongleProtocolHandler(SerialPort, remoteRelateId);
	        dh.addAuthenticationProgressHandler(new DongleAuthenticationEventHandler());
        	state = STATE_DONGLE_AUTH_RUNNING;
        	dh.startAuthentication(keys[1], rounds);
	    }

	    public void AuthenticationFailure(Object remote, Exception e, String msg)
	    {
			// TODO: check state!
			
	        System.out.println("Received authentication failure event with " + remote);
	        if (e != null)
	            System.out.println("Exception: " + e);
	        if (msg != null)
	            System.out.println("Message: " + msg);
	        authenticationFailed(remote, e, msg);
	    }

	    public void AuthenticationProgress(Object remote, int cur, int max, String msg)
	    {
			// TODO: check state!
			
	        System.out.println("Received authentication progress event with " + remote + " " + cur + " out of " + max + ": " + msg);
	        raiseAuthenticationProgressEvent(remote, cur, 
	        		HostProtocolHandler.AuthenticationStages + DongleProtocolHandler.AuthenticationStages + rounds,
	        		msg);
	    }
	}

	/** A helper class for handling the events from DongleProtocolHandler. */
	private class DongleAuthenticationEventHandler implements AuthenticationProgressHandler {
	    public void AuthenticationSuccess(Object remote, Object result)
	    {
			// TODO: check state!
			
	        System.out.println("Received authentication success event with relate dongle " + remote);
	        state = STATE_SUCCEEDED;
	        // our result object is here the secret key that is shared (host authentication) 
	        // and now spatially authenticated (dongle authentication)
	        raiseAuthenticationSuccessEvent(remote, sharedKey);
	    }

	    public void AuthenticationFailure(Object remote, Exception e, String msg)
	    {
			// TODO: check state!
			
	        System.out.println("Received authentication failure event with " + remote);
	        if (e != null)
	            System.out.println("Exception: " + e);
	        if (msg != null)
	            System.out.println("Message: " + msg);
	        authenticationFailed(remote, e, msg);
	    }

	    public void AuthenticationProgress(Object remote, int cur, int max, String msg)
	    {
			// TODO: check state!
			
	        System.out.println("Received authentication progress event with " + remote + " " + cur + " out of " + max + ": " + msg);
	        raiseAuthenticationProgressEvent(remote, HostProtocolHandler.AuthenticationStages + cur, 
	        		HostProtocolHandler.AuthenticationStages + DongleProtocolHandler.AuthenticationStages + rounds,
	        		msg);
	    }
	}
	
    public static void main(String[] args) throws Exception
	{
    	class TempAuthenticationEventHandler implements AuthenticationProgressHandler {
    		RelateAuthenticationProtocol outer;
    		
    	    public void AuthenticationSuccess(Object remote, Object result)
    	    {
    	        System.out.println("Received authentication success event with host " + remote);
    	        byte[][] keys = (byte[][]) result;
    	        System.out.println("Shared session key is now '" + keys[0] + "' with length " + keys[0].length + ", shared authentication key is now '" + keys[1] + "' with length " + keys[1].length);
    	        System.out.println("Starting dongle authentication with remote relate id " + outer.remoteRelateId);
    	        // remember the secret key shared with the other device
    	        outer.sharedKey = keys[0];
    	        // and use the agreed authentication key to start the dongle authentication
    	        DongleProtocolHandler dh = new DongleProtocolHandler(SerialPort, outer.remoteRelateId);
    	        dh.addAuthenticationProgressHandler(outer.new DongleAuthenticationEventHandler());
            	dh.startAuthentication(keys[1], outer.rounds);
    	    }
    	    public void AuthenticationFailure(Object remote, Exception e, String msg)
    	    {
    	        System.out.println("Received authentication failure event with " + remote);
    	        Throwable exc = e;
    	        while (exc != null) {
    	            System.out.println("Exception: " + exc);
    	            exc = exc.getCause();
    	        }
    	        if (msg != null)
    	            System.out.println("Message: " + msg);
    	    }
    	    public void AuthenticationProgress(Object remote, int cur, int max, String msg)
    	    {
    	        System.out.println("Received authentication progress event with " + remote + " " + cur + " out of " + max + ": " + msg);
    	    }
    	}
    	
        if (args.length > 2 && args[0].equals("server"))
        {
            HostServerSocket h1 = new HostServerSocket(TcpPort);
            TempAuthenticationEventHandler h = new TempAuthenticationEventHandler();
            h.outer = new RelateAuthenticationProtocol("", (byte) Integer.parseInt(args[1]));
            h.outer.rounds = (byte) Integer.parseInt(args[2]);
        	h1.addAuthenticationProgressHandler(h);
            h1.startListening();
            new BufferedReader(new InputStreamReader(System.in)).readLine();
            h1.stopListening();
        }
        if (args.length > 3 && args[0].equals("client"))
        {
        	RelateAuthenticationProtocol r = new RelateAuthenticationProtocol(args[1], (byte) Integer.parseInt(args[2]));
        	r.addAuthenticationProgressHandler(new TempAuthenticationEventHandler());
        	r.startAuthentication((byte) Integer.parseInt(args[3]));
            new BufferedReader(new InputStreamReader(System.in)).readLine();
        }
        
        // problem with the javax.comm API - doesn't release its native thread
        System.exit(0);
	}
}
