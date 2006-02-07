package org.eu.mayrhofer.authentication;

import org.eu.mayrhofer.authentication.exceptions.*;

import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import uk.ac.lancs.relate.core.MessageQueue;
import uk.ac.lancs.relate.core.SerialConnector;
import uk.ac.lancs.relate.core.DongleException;
import uk.ac.lancs.relate.events.RelateEvent;
import uk.ac.lancs.relate.events.MeasurementEvent;

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
	
	/** The serial port that is used by this authentication protocol instance to connect to its dongle. */
	private String serialPort;

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
	
	/** If set to true, the JSSE will be used, if set to false, the Bouncycastle Lightweight API. */
	private boolean useJSSE;

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
	
	/** This is just a helper to hold the remoteRelateId that is passed to startAuthentication
	 * until it is needed when starting the DongleProtocolHandler (after the HostProtocolHandler
	 * has finished successfully). When set to -1, this indicates "server" mode where the remote
	 * relate id is taken from the optionalParameter of the HostAuthenticationSuccess event. When
	 * not -1, this indicates "client" mode and this id will be taken.
	 * @see #startAuthentication
	 * @see HostAuthenticationEventHandler
	 */
	private int remoteRelateId = -1;
	
	/** TODO: temporary, needs better integration. */
	private int referenceMeasurement;
	
	/** This is only a helper to get the reference measurement now - pending better integration
	 * with the Relate framework.
	 * @param remoteRelateId
	 * @return
	 */
	private static int helper_getReferenceMeasurement(String serialPort, byte remoteRelateId) throws ConfigurationErrorException, InternalApplicationException {
		int referenceMeasurement;
		SerialConnector serialConn;
		
		// immediately get the reference measurement to the specific remote relate dongle
		try {
			// Connect here to the dongle so that we don't block it when not necessary. This needs better integration with the Relate framework. 
			serialConn = SerialConnector.getSerialConnector(serialPort);
			logger.info("-------- connected successfully to dongle, including first handshake. My ID is " + serialConn.getLocalRelateId());
		}
		catch (DongleException e) {
			logger.error("-------- failed to connect to dongle, didn't get my ID.");
			throw new ConfigurationErrorException("Can't connect to dongle.", e);
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
		logger.debug("Trying to get reference measurement from local id " + localRelateId + " to relate id " + remoteRelateId);
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
			if (e instanceof MeasurementEvent && ((MeasurementEvent) e).getDongleId() == localRelateId) {
				MeasurementEvent me = (MeasurementEvent) e;
				
				if (me.getMeasurement().getTransducers() != 0 && me.getMeasurement().getDistance() != 4094) {
					logger.debug("Got local measurement from dongle " + me.getDongleId() + " to dongle " + me.getMeasurement().getRelatumId() + ": " + me.getMeasurement().getDistance());
					ThreeInts x = s[me.getMeasurement().getRelatumId()];
					x.n++;
					x.sum += me.getMeasurement().getDistance();
					x.sum2 += me.getMeasurement().getDistance() * me.getMeasurement().getDistance();
					logger.debug("To dongle " + me.getMeasurement().getRelatumId() + ": mean=" + (float) x.sum/x.n + ", variance=" + 
							Math.sqrt((x.sum2 - 2*(float) x.sum/x.n*x.sum + (float) x.sum/x.n*x.sum)/x.n) );
				}
				else {
					logger.debug("Discarded invalid local measurement from dongle " + me.getDongleId() + " to dongle " + me.getMeasurement().getRelatumId() + ": " + me.getMeasurement().getDistance());
				}
			}
			// test code end
			
			if (e instanceof MeasurementEvent && ((MeasurementEvent) e).getMeasurement().getDongleId() == localRelateId &&  
					((MeasurementEvent) e).getMeasurement().getRelatumId() == remoteRelateId && ((MeasurementEvent) e).getMeasurement().getTransducers() != 0 && ((MeasurementEvent) e).getMeasurement().getDistance() != 4094) {
				MeasurementEvent me = (MeasurementEvent) e;

				logger.info("Received reference measurement from dongle " + localRelateId + " to dongle " + remoteRelateId + ": " + me.getMeasurement().getDistance());
				ref[numMeasurements++] = me.getMeasurement().getDistance();
			}
		}
		Arrays.sort(ref);
		referenceMeasurement = (ref[4] + ref[5]) / 2;
		logger.info("Mean over reference measurements from dongle " + localRelateId + " to dongle " + remoteRelateId + ": " + referenceMeasurement);
		
		serialConn.unregisterEventQueue(eventQueue);
		
		return referenceMeasurement;
	}
	
	/** Initialized the authentication object with the contact data of the remote device to authenticate with.
	 * This constructor also gets a reference measurement to the remote relate id by itself. This needs better
	 * integration with the Relate framework, the reference measurement should come from "the outside".
	 * 
	 * @param serialPort The serial port to which the dongle is connected
	 * @param referenceMeasurement The reference measurement to the remoteRelateId device taken when the
	 *                             user selected this device for authentication, or the last one taken 
	 *                             before the authentication request came in on the server end.
	 *                             TODO: This parameter must be removed!
	 * @param useJSSE If set to true, the JSSE API with the default JCE provider of the JVM will be used
	 *                for cryptographic operations. If set to false, an internal copy of the Bouncycastle
	 *                Lightweight API classes will be used.
	 */
	public RelateAuthenticationProtocol(String serialPort, int referenceMeasurement, boolean useJSSE) 
			throws ConfigurationErrorException, InternalApplicationException {
		this.serialPort = serialPort;
		this.referenceMeasurement = referenceMeasurement;
		this.useJSSE = useJSSE;
	}

	/** Starts the spatial authentication protocol in the background. Listeners should subscribe to
	 * authentication events to get notified about the progress of authentication.
	 * @param remoteHost The hostname/IP address of the remote device to send an authentication request to.
	 * @param remoteRelateId The relate id of the remote device. This is set by the client only
	 *                       to enable stricter error checks. T
	 * @param rounds The number of rounds that should be used for the dongle authentication. This
	 * directly influences the achieved security level, as described in DongleProtocolHandler#handleDongleCommunication.
	 * @see AuthenticationEventSender#addAuthenticationProgressHandler
	 * @see DongleProtocolHandler#handleDongleCommunication
	 */
	public void startAuthentication(String remoteHost, byte remoteRelateId, int rounds) 
			throws UnknownHostException, IOException, ConfigurationErrorException, InternalApplicationException {
		/* remember the remote relate id for later to pass it to the DongleAuthenticationHandler when
		 * the HostProtocolHandler has finished successfully.
		 */	
		this.remoteRelateId = remoteRelateId;
		
		// TODO: check state!
		
		/* This is simple in the implementation, because we just need to start the
		 * host authentication here. When that suceeds, the event handler will 
		 * continue to start the dongle authentication.
		 */ 
		state = STATE_HOST_AUTH_RUNNING; 

		// this code block only gets our local relate id so that it can be transmitted to the other host
		SerialConnector serialConn;
		try {
			// Connect here to the dongle so that we don't block it when not necessary. This needs better integration with the Relate framework. 
			serialConn = SerialConnector.getSerialConnector(serialPort);
			logger.info("-------- connected successfully to dongle, including first handshake. My ID is " + serialConn.getLocalRelateId());
		}
		catch (DongleException e) {
			logger.error("-------- failed to connect to dongle, didn't get my ID.");
			throw new ConfigurationErrorException("Can't connect to dongle.", e);
		}
		
		int localRelateId = serialConn.getLocalRelateId();
		if (localRelateId == -1)
			throw new InternalApplicationException("Dongle reports id -1, which is an error case.");
		
		// create the optional parameter object to pass, consisting of the relate id and the number of rounds
		String param = Integer.toString(rounds) + " " + Integer.toString(localRelateId);
			
		/* There is no need to unregister this new object, since it is only 
		 * registered with a temporary HostProtocolHandler object, which will
		 * be garbage collected when its background authentication thread
		 * finishes. */
		HostProtocolHandler.startAuthenticationWith(remoteHost, TcpPort, new HostAuthenticationEventHandler(), 
				true, param, useJSSE);
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
	        /* and also extract the optional parameters (in the case of the RelateAuthenticationProtocol: the remote
	           relate id to authenticate with and the number of rounds - we assume them to be set) as well as the 
	           socket (which is assumed to be still connected to the remote) */
	        String param1 = ((String) res[2]).substring(0, ((String) res[2]).indexOf(' '));
	        String param2 = ((String) res[2]).substring(((String) res[2]).indexOf(' ')+1, ((String) res[2]).length()-1);
	        // distinguish between client and server mode here
	        byte otherRelateId;
	        if (remoteRelateId != -1) {
	        		// "client" mode - this is the id that was passed to startAuthentication
	        		otherRelateId = (byte) remoteRelateId;
	        		logger.debug("Client mode: taking remote relate id that was passed earlier: " + otherRelateId);
	        }
	        else {
	        		// "server" mode - take the id that was passed by the client
	        		otherRelateId = Byte.parseByte(param1);
	        		logger.debug("Server mode: taking remote relate id from authentication request message: " + otherRelateId);
	        }
	        int rounds = Integer.parseInt(param2);
	        // this could need some error handling, but at the moment we depend on it being set
	        socketToRemote = (Socket) res[3];

	        // and use the agreed authentication key to start the dongle authentication
	        logger.debug("Starting dongle authentication with remote relate id " + otherRelateId + " and " + rounds + " rounds.");
	        DongleProtocolHandler dh = new DongleProtocolHandler(serialPort, otherRelateId, useJSSE);
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
	            logger.info("Exception: " + e /*+ "\n" +  e.getStackTrace()*/);
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
	        	logger.error("Could not report failure to remote host: " + ex /*+ "\n" + ex.getStackTrace()*/);
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
	
	// helper function to better facilitate the experiments, just interrupt both dongles
	private static void resetBothDongles() {
		try {
			SerialConnector.getSerialConnector("/dev/ttyUSB0").switchDiagnosticMode(false);
		}
		catch (DongleException e) { 
			logger.error("Could not reset dongle");
		}
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {}
		try {
			SerialConnector.getSerialConnector("/dev/ttyUSB1").switchDiagnosticMode(false);
		}
		catch (DongleException e) { 
			logger.error("Could not reset dongle");
		}
	}
	
    public static void main(String[] args) throws Exception
	{
		if (System.getProperty("os.name").startsWith("Windows CE")) {
			PropertyConfigurator.configure("log4j.properties");
		}

		class TempAuthenticationEventHandler implements AuthenticationProgressHandler {
			private int mode; // 0 = client, 1 = server, 2 = both
    		
			public TempAuthenticationEventHandler(int mode) {
    				this.mode = mode;
    			}
    		
    			synchronized public void AuthenticationSuccess(Object sender, Object remote, Object result)
    			{
    				logger.info("Received relate authentication success event with " + remote);
    				System.out.println("SUCCESS");

    				// HACK HACK HACK HACK: interrupt the dongle to be sure to get it out of authentication mode
    				/*try {
    				 Thread.sleep(500); // should be long enough to send the last packet, if necessary
    				 } catch (InterruptedException e) {}
    				 outer.serialConn.switchDiagnosticMode(false);*/
   	        	
    				if (mode == 0) {
    					if (! System.getProperty("os.name").startsWith("Windows CE")) 
    						Runtime.getRuntime().exit(0);
    				}
    				else if (mode == 2) {
    					// give it time to settle....
    					try {
    						Thread.sleep(3000);
    					} catch (InterruptedException e) {}
    					resetBothDongles();
    					Runtime.getRuntime().exit(0);
    				}
    			}
    	    
    			synchronized public void AuthenticationFailure(Object sender, Object remote, Exception e, String msg)
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
    	        
    				if (mode == 0) {
    					if (! System.getProperty("os.name").startsWith("Windows CE"))
    						Runtime.getRuntime().exit(1);
    				}
    				else if (mode == 2) {
    					resetBothDongles();
    					Runtime.getRuntime().exit(1);
    				}
    			}

    			public void AuthenticationProgress(Object sender, Object remote, int cur, int max, String msg)
    			{
    				logger.info("Received relate authentication progress event with " + remote + " " + cur + " out of " + max + ": " + msg);
    			}
    		}
    
    		boolean useJSSEServer = true;
    		boolean useJSSEClient = true;
    		if (System.getProperty("os.name").startsWith("Windows CE")) {
    			useJSSEServer = useJSSEClient = false;
    		}
    	
        if (args.length > 2 && args[0].equals("server")) {
        	logger.info("Starting server mode");
            HostServerSocket h1 = new HostServerSocket(TcpPort, true, useJSSEServer);
            int referenceMeasurement = helper_getReferenceMeasurement(args[1], (byte) Integer.parseInt(args[2]));
            RelateAuthenticationProtocol r = new RelateAuthenticationProtocol(args[1], referenceMeasurement, useJSSEServer);
            TempAuthenticationEventHandler ht = new TempAuthenticationEventHandler(1);
            r.addAuthenticationProgressHandler(ht);
            HostAuthenticationEventHandler hh = r.new HostAuthenticationEventHandler();
        		h1.addAuthenticationProgressHandler(hh);
            h1.startListening();
            //new BufferedReader(new InputStreamReader(System.in)).readLine();

            while (true) Thread.sleep(1000);

            //h1.stopListening();
        } 
        else if (args.length > 4 && args[0].equals("client")) {
        		System.out.println("starting client mode: port=" + args[1] + ", server=" + args[2] + ", remoteid=" + args[3] + ", rounds=" + args[4]);
        		logger.info("Starting client mode");
            int referenceMeasurement = helper_getReferenceMeasurement(args[1], (byte) Integer.parseInt(args[3]));
        		RelateAuthenticationProtocol r = new RelateAuthenticationProtocol(args[1], referenceMeasurement, useJSSEClient);
        		r.addAuthenticationProgressHandler(new TempAuthenticationEventHandler(0));
        		r.startAuthentication(args[2], (byte) Integer.parseInt(args[3]), Integer.parseInt(args[4]));
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
   	        		resetBothDongles();
   	        		if (! System.getProperty("os.name").startsWith("Windows CE"))
   	        			System.exit(100);
            	}
            }).start();

            //new BufferedReader(new InputStreamReader(System.in)).readLine();

            while (true) Thread.sleep(1000);
        }
        else if (args.length == 2 && args[0].equals("both")) {
        		logger.info("Starting mutual authentication mode with two dongles");
        		int localId1 = -1, localId2 = -1;

        		// first need to get my local ids
        		try {
        			SerialConnector s1 = SerialConnector.getSerialConnector("/dev/ttyUSB0");
        			localId1 = s1.getLocalRelateId();
        			Thread.sleep(3000);
        			SerialConnector s2 = SerialConnector.getSerialConnector("/dev/ttyUSB1");
        			localId2 = s2.getLocalRelateId();
        		}
        		catch (DongleException e) {
        			logger.error("-------- failed to connect to dongle, didn't get my ID.");
        			System.out.println(e);
        			//e.printStackTrace();
        			if (! System.getProperty("os.name").startsWith("Windows CE"))
        				System.exit(1);
        		}

        		logger.info("Connected to my two dongles: ID " + localId1 + " on /dev/ttyUSB0, and ID " + localId2 + " on /dev/ttyUSB1");

        		// server side
        		TempAuthenticationEventHandler ht = new TempAuthenticationEventHandler(2);
        		HostServerSocket h1 = new HostServerSocket(TcpPort, true, useJSSEServer);
            int referenceMeasurement2 = helper_getReferenceMeasurement("/dev/ttyUSB0", (byte) localId2);
            RelateAuthenticationProtocol r_serv = new RelateAuthenticationProtocol("/dev/ttyUSB0", referenceMeasurement2, useJSSEServer);
            r_serv.addAuthenticationProgressHandler(ht);
            HostAuthenticationEventHandler hh_serv = r_serv.new HostAuthenticationEventHandler();
            h1.addAuthenticationProgressHandler(hh_serv);
            h1.startListening();

            // client side
            int referenceMeasurement1 = helper_getReferenceMeasurement("/dev/ttyUSB1", (byte) localId1);
            RelateAuthenticationProtocol r_client = new RelateAuthenticationProtocol("/dev/ttyUSB1", referenceMeasurement1, useJSSEClient);
        		r_client.addAuthenticationProgressHandler(ht);
        		r_client.startAuthentication("localhost", (byte) localId1, Integer.parseInt(args[1]));
        	
        		// safety belt
            new Thread(new Runnable() {
            		public void run() {
            			System.out.println("******** Starting timer");
            			// two minutes should really be enough
            			try {
            				Thread.sleep(120 * 1000);
            			} catch (InterruptedException e) {}
            			System.out.println("******** Timed out");
            			statisticsLogger.error("- Timer killed client");
            			if (	! System.getProperty("os.name").startsWith("Windows CE"))
            				System.exit(100);
            		}
            	}).start();

            // and wait
            while (true) Thread.sleep(1000);
        }
        
        // problem with the javax.comm API - doesn't release its native thread
        if (! System.getProperty("os.name").startsWith("Windows CE"))
        		System.exit(0);
	}
}
