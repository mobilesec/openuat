package org.eu.mayrhofer.authentication;

import org.eu.mayrhofer.authentication.exceptions.*;

import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;

import org.apache.log4j.Logger;

import uk.ac.lancs.relate.MessageQueue;
import uk.ac.lancs.relate.RelateEvent;
import uk.ac.lancs.relate.SerialConnector;

/// <summary>
/// This is the main class of the relate authentication software: it ties together
/// the host and dongle protocol handlers. Since both handlers work asynchronously
/// in their own threads, this class must also handle the synchronisation between 
/// all events coming in from them.
/// </summary>
public class RelateAuthenticationProtocol extends AuthenticationEventSender {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(RelateAuthenticationProtocol.class);
	/** This is a special log4j logger used for logging only statistics. It is separate from the main logger
	 * so that it's possible to turn statistics on an off independently.
	 */
	private static Logger statisticsLogger = Logger.getLogger("statistics.relateauthentication");

	public static final int TcpPort = 54321;
	// TODO: make configurable!
	public static final String SerialPort = "/dev/ttyUSB0";

	/** The relate id of the remote device to authenticate with. */
	private byte remoteRelateId;
	/** The hostname/IP address of the remote device to authenticate with. */
	private String remoteHost;
	
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
	
	/** This message is sent via the TCP channel to the remote upon authentication success. */
	private final static String Protocol_Success = "ACK ";
	/** This message is sent via the TCP channel to the remote upon authentication failure. */
	private final static String Protocol_Failure = "NACK ";
	
	/** If the state is STATE_DONGLE_AUTH_RUNNING or STATE_SUCCEEDED, this contains
	 * the secret key shared with the other device.
	 */
	private byte[] sharedKey = null;

	/** If the state is STATE_DONGLE_AUTH_RUNNING, this contains a socket that is still
	 * connected to the remote side and which is used for transmitting success or failure
	 * messages from the dongle authentication protocol (i.e. the second stage).
	 * It is set by HostAuthenticationEventHandler.AuthenticationSuccess.
	 * @see HostAuthenticationEventHandler#AuthenticationSuccess(Object, Object)
	 */
	private Socket socketToRemote;
	
	/** The serial connector object (singleton) used to talk to the dongle. */
	private SerialConnector serialConn;
	
	/** Also temporary, needs better integration. */
	private int referenceMeasurement;
	
	/** Initialized the authentication object with the contact data of the remote device to authenticate with.
	 * This constructor also gets a reference measurement to the remote relate id by itself. This needs better
	 * integration with the Relate framework, the reference measurement should come from "the outside".
	 * 
	 * @param remoteHost The hostname/IP address of the remote device.
	 * @param remoteRelateId The relate id of the remote device.
	 */
	public RelateAuthenticationProtocol(String remoteHost, byte remoteRelateId) 
			throws ConfigurationErrorException, InternalApplicationException {
		this.remoteHost = remoteHost;
		this.remoteRelateId = remoteRelateId;

		serialConn = SerialConnector.getSerialConnector();
		
		// immediately get the reference measurement to the specific remote relate dongle
		// Connect here to the dongle so that we don't block it when not necessary. This needs better integration with the Relate framework. 
		if (serialConn.connect(SerialPort, 0, 255))
			logger.info("-------- connected successfully to dongle, including first handshake. My ID is " + serialConn.getLocalRelateId());
		else {
			logger.error("-------- failed to connect to dongle, didn't get my ID.");
			throw new ConfigurationErrorException("Can't connect to dongle.");
		}
		
		int localRelateId = serialConn.getLocalRelateId();
		if (localRelateId == -1)
			throw new InternalApplicationException("Dongle reports id -1, which is an error case.");
		
		// start the backgroud thread for getting messages from the dongle
		serialConn.start();
		
		// This message queue is used to receive events from the dongle, in this case the reference measurements.
		MessageQueue eventQueue = new MessageQueue();
		serialConn.registerEventQueue(eventQueue);

		// test code begin
		class ThreeInts { public long sum = 0, n = 0, sum2 = 0; };
		ThreeInts[] s = new ThreeInts[256]; for(int i=0; i<256; i++) s[i] = new ThreeInts();
		// test code end
		
		// wait for the first reference measurements to come in (needed to compute the delays)
		logger.debug("Trying to get reference measurement to relate id " + remoteRelateId);
		int[] ref = new int[10];
		int numMeasurements = 0;
		while (numMeasurements < 10) {
			while (eventQueue.isEmpty())
				eventQueue.waitForMessage(500);
			RelateEvent e = (RelateEvent) eventQueue.getMessage();
			if (e == null) {
				logger.warn("Warning: got null message out of message queue! This should not happen.");
				continue;
			}
			
			// test code begin
			if (e.getType() == RelateEvent.NEW_MEASUREMENT && e.getMeasurement().getRelatum() == localRelateId) {
				if (e.getMeasurement().getTransducers() != 0 && e.getMeasurement().getDistance() != 4094) {
					logger.debug("Got measurement from dongle " + e.getMeasurement().getRelatum() + " to dongle " + e.getMeasurement().getId() + ": " + e.getMeasurement().getDistance());
					ThreeInts x = s[e.getMeasurement().getId()];
					x.n++;
					x.sum += (int) e.getMeasurement().getDistance();
					x.sum2 += ((int) e.getMeasurement().getDistance() * (int) e.getMeasurement().getDistance());
					logger.debug("To dongle " + e.getMeasurement().getId() + ": mean=" + (float) x.sum/x.n + ", variance=" + 
							Math.sqrt((x.sum2 - 2*(float) x.sum/x.n*x.sum + (float) x.sum/x.n*x.sum)/x.n) );
				}
				else {
					logger.debug("Discarded invalid measurement from dongle " + e.getMeasurement().getRelatum() + " to dongle " + e.getMeasurement().getId() + ": " + e.getMeasurement().getDistance());
				}
			}
			// test code end
			
			if (e.getType() == RelateEvent.NEW_MEASUREMENT && e.getMeasurement().getRelatum() == localRelateId &&  
					e.getMeasurement().getId() == remoteRelateId && e.getMeasurement().getTransducers() != 0 && e.getMeasurement().getDistance() != 4094) {
				logger.info("Received reference measurement to dongle " + remoteRelateId + ": " + e.getMeasurement().getDistance());
				ref[numMeasurements++] = (int) e.getMeasurement().getDistance();
			}
		}
		Arrays.sort(ref);
		referenceMeasurement = (ref[4] + ref[5]) / 2;
		logger.info("Mean over reference measurements to dongle " + remoteRelateId + ": " + referenceMeasurement);
		
		serialConn.unregisterEventQueue(eventQueue);
	}

	/** Currently this causes the serial connector to be shut down properly. TODO: Needs better integration with
	 * the Relate framework.
	 */
	public void dispose() {
		serialConn.stop();
		serialConn.disconnect();
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
		/* There is no need to unregister this new object, since it is only 
		 * registered with a temporary HostProtocolHandler object, which will
		 * be garbage collected when its background authentication thread
		 * finishes. */
		HostProtocolHandler.startAuthenticationWith(remoteHost, TcpPort, new HostAuthenticationEventHandler(), 
				true, Integer.toString(rounds));
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
	
	/** A helper class for handling the events from HostProtocolHandler.
	 * Its main purpose is to react to the AuthenticationSuccess event of
	 * HostAuthenticationProtocol (i.e. stage 1), record its results and
	 * fire off a new DongleAuthenticationProtocol (i.e. stage 2).
	 * Additionally, it will forward failure and progress events.  
	 */
	private class HostAuthenticationEventHandler implements AuthenticationProgressHandler {
	    public void AuthenticationSuccess(Object sender, Object remote, Object result)
	    {
			// TODO: check state!
			
	        logger.info("Received host authentication success event with " + remote);
	        Object[] res = (Object[]) result;
	        logger.debug("Shared session key is now '" + res[0] + "' with length " + ((byte[]) res[0]).length + ", shared authentication key is now '" + res[1] + "' with length " + ((byte[]) res[1]).length);
	        // remember the secret key shared with the other device
	        sharedKey = (byte[]) res[0];
	        // and also extract the optional parameter (in the case of the RelateAuthenticationProtocol the number of
	        // rounds (we assume it to be set) as well as the socket (which is assumed to be still connected to the
	        // remote)
	        int rounds = Integer.parseInt((String) res[2]);
	        // this could need some error handling, but at the moment we depend on it being set
	        socketToRemote = (Socket) res[3];

	        // and use the agreed authentication key to start the dongle authentication
	        logger.debug("Starting dongle authentication with remote relate id " + remoteRelateId + " and " + rounds + " rounds.");
	        DongleProtocolHandler dh = new DongleProtocolHandler(remoteRelateId);
	        dh.addAuthenticationProgressHandler(new DongleAuthenticationEventHandler(rounds));
        	state = STATE_DONGLE_AUTH_RUNNING;
        	dh.startAuthentication((byte[]) res[1], rounds, referenceMeasurement);
	    }

	    public void AuthenticationFailure(Object sender, Object remote, Exception e, String msg)
	    {
			// TODO: check state!
			
	        logger.info("Received host authentication failure event with " + remote);
	        if (e != null)
	            logger.info("Exception: " + e);
	        if (msg != null)
	            logger.info("Message: " + msg);
	        authenticationFailed(remote, e, msg);
	    }

	    public void AuthenticationProgress(Object sender, Object remote, int cur, int max, String msg)
	    {
			// TODO: check state!
			
	        logger.debug("Received host authentication progress event with " + remote + " " + cur + " out of " + max + ": " + msg);
	        // this is not optional because we don't know the number of rounds to use yet
	        raiseAuthenticationProgressEvent(remote, cur, 
	        		HostProtocolHandler.AuthenticationStages + DongleProtocolHandler.AuthenticationStages,
	        		msg);
	    }
	}

	/** A helper class for handling the events from DongleProtocolHandler. */
	private class DongleAuthenticationEventHandler implements AuthenticationProgressHandler {
		/** The number of rounds used for the spatial authentication protocol. */
		private int rounds;
		
		public DongleAuthenticationEventHandler(int rounds) {
			this.rounds = rounds;
		}
		
		/** Small helper method for logging a success and the protocol execution times of both sides. */
		private void logSuccess(DongleProtocolHandler localSide, String remoteStatus) {
			// first extract the values from the remote status string
			String values[] = remoteStatus.substring(Protocol_Success.length()).split(" ", 2);
			// first local, than remote times
			statisticsLogger.info("+ " + rounds + " " + referenceMeasurement + " " +
					localSide.getSendCommandTime() + " " + localSide.getDongleInterlockTime() +
					" " + values[0] + " " + values[1] + " Dongle authentication succeeded");
		}
		
		/** Small helper method for logging a failure (on either of the sides. */
		private void logFailure(String reason) {
			statisticsLogger.error("- " + rounds + " " + referenceMeasurement + " " + " Dongle authentication failed:" + reason);
		}
		
	    public void AuthenticationSuccess(Object sender, Object remote, Object result)
	    {
			// TODO: check state!
			
	        logger.info("Received dongle authentication success event with id " + remote);
	        
	        // before forwarding the success event, send a success message to the remote and wait for its success message
	        try {
	        	BufferedReader fromRemote = new BufferedReader(new InputStreamReader(socketToRemote.getInputStream()));
	        	// this enables auto-flush
	        	PrintWriter toRemote = new PrintWriter(socketToRemote.getOutputStream(), true);
	        	DongleProtocolHandler h = (DongleProtocolHandler) sender;
	        	// (also report the time it took on this side to the remote)
	        	toRemote.println(Protocol_Success + h.getSendCommandTime() + " " + h.getDongleInterlockTime());
	        	toRemote.flush();
	        	String remoteStatus = fromRemote.readLine();
	        	if (remoteStatus == null) {
		        	logger.error("Could not get status message from remote host");
			        logFailure("Could not get status message from remote host");
		        	authenticationFailed(remote, null, "Could not get status message from remote host");
	        	}
	        	else if (remoteStatus.startsWith(Protocol_Success)) {
	        		logger.info("Received success status from remote host");

			        state = STATE_SUCCEEDED;
			        // our result object is here the secret key that is shared (host authentication) 
			        // and now spatially authenticated (dongle authentication)
			        logSuccess(h, remoteStatus);
			        raiseAuthenticationSuccessEvent(remote, sharedKey);
	        	}
	        	else if (remoteStatus.startsWith(Protocol_Failure)) {
	        		logger.error("Received failure status from remote host although local dongle authentication was successful. Authentication protocol failed.");
			        logFailure("Received authentication failure status from remote host");
	        		authenticationFailed(remote, null, "Received authentication failure status from remote host");
	        	}
	        	// don't forget to properly close the socket
	        	socketToRemote.close();
	        } 
	        catch (IOException e) {
	        	logger.error("Could not report success to remote host or get status message from remote host: " + e);
		        logFailure(e.toString());
	        	authenticationFailed(remote, e, "Could not report success to remote host or get status message from remote host");
	        }
	    }

	    public void AuthenticationFailure(Object sender, Object remote, Exception e, String msg)
	    {
			// TODO: check state!
			
	        logger.info("Received dongle authentication failure event with id " + remote);
	        if (e != null)
	            logger.info("Exception: " + e + "\n" +  e.getStackTrace());
	        if (msg != null)
	            logger.info("Message: " + msg);
	        logFailure((e != null ? e.toString() : "") + "/" + msg);
	        authenticationFailed(remote, e, msg);
	        
	        // and also send an authentication failed status to the remote
	        try {
	        	// this enables auto-flush
	        	PrintWriter toRemote = new PrintWriter(socketToRemote.getOutputStream(), true);
	        	DongleProtocolHandler h = (DongleProtocolHandler) sender;
	        	toRemote.println(Protocol_Failure + h.getSendCommandTime() + " " + h.getDongleInterlockTime());
	        	toRemote.flush();
	        	// don't forget to properly close the socket
	        	socketToRemote.close();
	        }
	        catch (IOException ex) {
	        	logger.error("Could not report failure to remote host: " + ex + "\n" + ex.getStackTrace());
	        }
	    }

	    public void AuthenticationProgress(Object sender, Object remote, int cur, int max, String msg)
	    {
			// TODO: check state!
			
	        logger.debug("Received dongle authentication progress event with id " + remote + " " + cur + " out of " + max + ": " + msg);
	        raiseAuthenticationProgressEvent(remote, HostProtocolHandler.AuthenticationStages + cur, 
	        		HostProtocolHandler.AuthenticationStages + DongleProtocolHandler.AuthenticationStages + rounds,
	        		msg);
	    }
	}
	
    public static void main(String[] args) throws Exception
	{
    	class TempAuthenticationEventHandler implements AuthenticationProgressHandler {
    		private boolean serverMode;
    		private RelateAuthenticationProtocol outer;
    		
    		public TempAuthenticationEventHandler(boolean serverMode, RelateAuthenticationProtocol outer) {
    			this.serverMode = serverMode;
    			this.outer = outer;
    		}
    		
    	    public void AuthenticationSuccess(Object sender, Object remote, Object result)
    	    {
    	        logger.info("Received relate authentication success event with " + remote);
   	        	System.out.println("SUCCESS");

	        	// HACK HACK HACK HACK: interrupt the dongle to be sure to get it out of authentication mode
	        	/*try {
	        		Thread.sleep(500); // should be long enough to send the last packet, if necessary
	        	} catch (InterruptedException e) {}
	        	outer.serialConn.switchDiagnosticMode(false);*/
   	        	
   	        	if (!serverMode)
   	        		Runtime.getRuntime().exit(0);
    	    }
    	    
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

	        	// HACK HACK HACK HACK: interrupt the dongle to be sure to get it out of authentication mode
	        	/*try {
	        		Thread.sleep(500); // should be long enough to send the last packet, if necessary
	        	} catch (InterruptedException e1) {}
	        	outer.serialConn.switchDiagnosticMode(false);*/
    	        
    	        if (!serverMode)
   	        		Runtime.getRuntime().exit(1);
    	    }

    	    public void AuthenticationProgress(Object sender, Object remote, int cur, int max, String msg)
    	    {
    	        logger.info("Received relate authentication progress event with " + remote + " " + cur + " out of " + max + ": " + msg);
    	    }
    	}
    	
        if (args.length > 1 && args[0].equals("server"))
        {
        	logger.info("Starting server mode");
            HostServerSocket h1 = new HostServerSocket(TcpPort, true);
            RelateAuthenticationProtocol r = new RelateAuthenticationProtocol("", (byte) Integer.parseInt(args[1]));
            TempAuthenticationEventHandler ht = new TempAuthenticationEventHandler(true, r);
            r.addAuthenticationProgressHandler(ht);
            HostAuthenticationEventHandler hh = r.new HostAuthenticationEventHandler();
        	h1.addAuthenticationProgressHandler(hh);
            h1.startListening();
            //new BufferedReader(new InputStreamReader(System.in)).readLine();

            while (true) Thread.sleep(1000);

            //h1.stopListening();
        }
        if (args.length > 3 && args[0].equals("client"))
        {
        	logger.info("Starting client mode");
        	RelateAuthenticationProtocol r = new RelateAuthenticationProtocol(args[1], (byte) Integer.parseInt(args[2]));
        	r.addAuthenticationProgressHandler(new TempAuthenticationEventHandler(false, r));
        	r.startAuthentication((byte) Integer.parseInt(args[3]));
            // This is the last safety belt: a timer to kill the client if the dongle hangs for some reason. This is
            // not so simple for the server.
            new Thread(new Runnable() {
            	public void run() {
            		System.out.println("******** Starting timer");
            		// two minutes should really be enough
            		try {
            			Thread.sleep(120 * 1000);
            		} catch (InterruptedException e) {}
            		System.out.println("******** Timed out");
        			statisticsLogger.error("- Timer killed client");
            		System.exit(100);
            	}
            }).start();

            //new BufferedReader(new InputStreamReader(System.in)).readLine();

            while (true) Thread.sleep(1000);
        }
        
        // problem with the javax.comm API - doesn't release its native thread
        System.exit(0);
	}
}
