/* Copyright Rene Mayrhofer
 * File created 2005-09
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.authentication;

import org.openuat.authentication.exceptions.*;

import java.net.*;
import java.io.*;

import org.apache.commons.codec.*;
import org.apache.commons.codec.binary.*;
import org.apache.log4j.Logger;

/**
 * This class handles the key agreement protocol between two hosts on the TCP/IP
 * level. It implements both sides of the protocol, allowing to handle incoming
 * connections (i.e. incoming authentication requests) as well as initiating
 * outgoing connections (i.e. outgoing authentication requests). Events are
 * raised upon authentication success, failure and during the progress of an
 * authentication protocol.
 * 
 * The authentication success event generated by this protocol will return an 
 * InetAddress object for the remote parameter and an Object array as the result
 * parameter. This object array will always have at least 3 objects: two byte arrays
 * representing the session key and the authentication key and a String representing
 * the optional parameter that might have been specified by the client or which might
 * have been passed to the protocol when in client mode. This third object in the object
 * array can be null if no parameter was specified, but it will always be there. 
 * An optional fourth object will be included with the array when the keepSocketConnected
 * flag was set. This fourth paramater will then contain the still connected socket object.
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class HostProtocolHandler extends AuthenticationEventSender {
	/** Our primary logger. */
	private static Logger logger = Logger.getLogger(HostProtocolHandler.class);
	
	/** These are the messages of the ASCII authentication protocol. */
    public static final String Protocol_Hello = "HELO RelateAuthentication";
    /** @see #Protocol_Hello */
    public static final String Protocol_AuthenticationRequest = "AUTHREQ ";
    /** This is an optional field in the authentication request line, where the
     * client can pass parameters to the next authentication protocol.
     * @see #Protocol_AuthenticationRequest */
    public static final String Protocol_AuthenticationRequest_Param = "PARAM ";
    /** @see #Protocol_Hello */
    public static final String Protocol_AuthenticationAcknowledge = "AUTHACK ";

    /** At the moment, the whole protocol consists of 4 stages. */
    public static final int AuthenticationStages = 4;
    
	/** If set to true, the JSSE will be used, if set to false, the Bouncycastle Lightweight API. */
	private boolean useJSSE;

    /** The socket used to communicate with the remote end, for both incoming and outgoing connections. */
    private Socket socket;
    /** If set to false, socket will be closed. 
     * @see #socket
     * @see #HostProtocolHandler(Socket, boolean, boolean)
     */
    private boolean keepSocketConnected;
    /** An optional parameter that can be passed from the client to the server
     * in its authentication request message. If not null, this message will be
     * forwarded by both the server and the client in their respective authentication
     * success messages.
     */
    private String optionalParameter = null;
    /** The stream to send messages to the remote end. */
    private PrintWriter toRemote;
    /** The stream to receive messages from the remote end. */
    private BufferedReader fromRemote;
    
    /**
	 * This class should only be instantiated by HostServerSocket for incoming
	 * connections or with the static startAuthenticatingWith method for
	 * outgoing connections.
	 * 
	 * @param soc
	 *            The socket to use for communication. It must already be
	 *            connected to the other side, but will be shut down and closed
	 *            before the protocol handler methods return, depending on the
	 *            parameter keepSocketConnected. The reason for
	 *            this asymmetry (the socket must be connected by the caller,
	 *            but is closed by the methods of this class) lies in the
	 *            asynchronity: the protocol handler methods are called in
	 *            background threads and must therefore dispose the objects
	 *            before exiting.
	 *            
	 * @param keepSocketConnected
	 *            If set to true, the opened client socket soc is passed to the
	 *            authentication success event (in the results parameter) for 
	 *            further re-use of the connection (e.g. passing additional 
	 *            information about further protocol steps). If set to false, the
	 *            socket will be closed when this protocol is done with it.
	 *
	 * @param useJSSE If set to true, the JSSE API with the default JCE provider of the JVM will be used
	 *                for cryptographic operations. If set to false, an internal copy of the Bouncycastle
	 *                Lightweight API classes will be used.
	 */
    HostProtocolHandler(Socket soc, boolean keepSocketConnected, boolean useJSSE) 
	{
		this.socket = soc;
		this.keepSocketConnected = keepSocketConnected;
		this.useJSSE = useJSSE;
    }
    
    /**
	 * Helper method used for closing the streams connected to the socket
	 * cleanly. 
	 * 
	 * @see #fromRemote
	 * @see #toRemote
	 * @see #socket
	 */
    void shutdownStreamsCleanly()
    {
    	logger.debug("Shutting down streams");
    	try {
    		if (fromRemote != null)
    			fromRemote.close();
    		if (toRemote != null) {
    			toRemote.flush();
    			toRemote.close();
    		}
   		}
   		catch (IOException e) {
   			throw new RuntimeException("Unable to close streams cleanly", e);
   		}
    }
    
    /**
	 * Helper method used for closing the socket cleanly. Calls 
	 * shutdownStreamsCleanly beforehand.
	 *
	 * @see #shutdownStreamsCleanly()
	 * @see #socket
	 */
    void shutdownSocketCleanly() {
    	shutdownStreamsCleanly();
    	logger.debug("Shutting down sockets");
    	try {
    		if (socket != null && socket.isConnected())
    		{
    			if (! socket.isInputShutdown() && !socket.isClosed())
    				socket.shutdownInput();
    			if (! socket.isOutputShutdown() && !socket.isClosed())
					socket.shutdownOutput();
    			socket.close();
    		}
		}
		catch (IOException e) {
			throw new RuntimeException("Unable to close socket cleanly", e);
		}
    }
    
    /**
	 * Tries to receive a properly formatted parameter line from the remote host.
	 * This will always include the public key and might include an optional parameter.
	 * If the line could not be received (i.e. no line at all or starting with an
	 * unexpected command), an OnAuthenticaionFailure event is raised.
	 * 
	 * @param expectedMsg
	 *            Gives the message that is expected to be received: for server
	 *            mode, a Protocol_AuthenticationRequest message is expected,
	 *            while for client mode, a Protocol_AuthenticationAcknowledge is
	 *            expected
	 * @param remote
	 *            The remote socket. This is only needed for raising events
	 *            and is passed unmodified to the event method.
	 *            
	 * @return The complete parameter line on success, null otherwise.
	 */
    private String helper_getAuthenticationParamLine(String expectedMsg, InetAddress remote) throws IOException
    {
    	String msg = fromRemote.readLine();
        if (msg == null)
        {
        	logger.warn("helper_getAuthenticationParamLine called with null argument");
            raiseAuthenticationFailureEvent(remote, null, "Protocol error: no message received");
            return null;
        }

        // try to extract the remote key from it
        if (!msg.startsWith(expectedMsg))
        {
        	logger.warn("Protocol error: unkown message '" + msg + "'");
            toRemote.println("Protocol error: unknown message: '" + msg + "'");
            raiseAuthenticationFailureEvent(remote, null, "Protocol error: unknown message");
            return null;
        }
        return msg;
    }
    
	/** Tries to decode a properly formatted public key from the parameter line. If 
     * decoding fails, an OnAuthenticationFailure event is raised.
     * 
     * @param paramLine
     * 	          The complete parameter line from the remote, including the command
     *            prefix.
	 * @param expectedMsg
	 *            Gives the message that is expected to be received: for server
	 *            mode, a Protocol_AuthenticationRequest message is expected,
	 *            while for client mode, a Protocol_AuthenticationAcknowledge is
	 *            expected
	 * @param remote
	 *            The remote socket. This is only needed for raising events
	 *            and is passed unmodified to the event method.
	 * 
	 * @return The extracted public key is returned in this array. If decoding
	 *         failed, null will be returned instead of the (meaningless) parts
	 *         that might have been decoded.
	 * @throws IOException
	 */
    private byte[] helper_extractPublicKey(String paramLine, String expectedMsg, InetAddress remote) throws IOException
    {
    	if (paramLine == null)
    		return null;
    	
    	byte[] remotePubKey;
    	// need to figure out if there is something after the public key (i.e. a space after it)
    	int secondSpaceOff = paramLine.indexOf(' ', expectedMsg.length());
    	// if yes, only the part in between the two spaces is the public key, otherwise just the rest of the line
        String remotePubKeyStr = paramLine.substring(expectedMsg.length(), 
        		secondSpaceOff != -1 ? secondSpaceOff : paramLine.length());
        try {
        	remotePubKey = Hex.decodeHex(remotePubKeyStr.toCharArray());
        }
        catch (DecoderException e) {
            logger.warn("Protocol error: could not parse public key, expected 128 Bytes hex-encoded.");
            toRemote.println("Protocol error: could not parse public key, expected 128 Bytes hex-encoded.");
            raiseAuthenticationFailureEvent(remote, e, "Protocol error: can not decode remote public key");
            return null;
        }
        if (remotePubKey.length < 128)
        {
            logger.warn("Protocol error: could not parse public key, expected 128 Bytes hex-encoded.");
            toRemote.println("Protocol error: could not parse public key, expected 128 Bytes hex-encoded.");
            raiseAuthenticationFailureEvent(remote, null, "Protocol error: remote key too short (only " + remotePubKey.length + " bytes instead of 128)");
            return null;
        }
        return remotePubKey;
    }
    
    /**
	 * This method depends on prior initialization and assumes to be launched
	 * in an independent thread, i.e. it performs blocking operations. It
	 * assumes that the socket variable already contains a valid, connected
	 * socket that can be used for communication with the remote authentication
	 * partner. fromRemote and toRemote will be initialized as streams connected
	 * to this socket.
	 * 
	 * @param serverSide
	 *            true for server side ("authenticator"), false for client side
	 *            ("authenticatee")
	 */
    private void performAuthenticationProtocol(boolean serverSide) {
    		SimpleKeyAgreement ka = null;
        // remember whom we are communication with: when the socket gets closed,
		// we can no longer access it
        InetAddress remote = socket.getInetAddress();
        String inOrOut, serverToClient, clientToServer;
        
        logger.debug("Starting authentication protocol as " + (serverSide ? "server" : "client"));
        logger.debug("Remote address is " + remote);

        if (serverSide) {
        	inOrOut = "Incoming";
        	serverToClient = "sent";
        	clientToServer = "received";
        } else {
        	inOrOut = "Outgoing";
        	serverToClient = "received";
        	clientToServer = "sent";
        }
        
        logger.debug(inOrOut + " connection to authentication service with " + remote);
        
        try
        {
            fromRemote = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            // this enables auto-flush
            toRemote = new PrintWriter(socket.getOutputStream(), true);

            if (serverSide) {
            	toRemote.println(Protocol_Hello);
            }
            else {
                String msg = fromRemote.readLine();
                if (!msg.equals(Protocol_Hello))
                {
                	raiseAuthenticationFailureEvent(remote, null, "Protocol error: did not get greeting from server");
                    shutdownSocketCleanly();
                    return;
                }
        	}
            raiseAuthenticationProgressEvent(remote, 1, AuthenticationStages, inOrOut + " authentication connection, " + serverToClient + " greeting");

            byte[] remotePubKey = null;
            if (serverSide) {
            	String paramLine = helper_getAuthenticationParamLine(Protocol_AuthenticationRequest, remote);
                remotePubKey = helper_extractPublicKey(paramLine, Protocol_AuthenticationRequest, remote);
                if (remotePubKey == null)
                {
                    shutdownSocketCleanly();
                    return;
                }
                int optParamOff = paramLine.indexOf(Protocol_AuthenticationRequest_Param);
                if (optParamOff != -1) {
                		optionalParameter = paramLine.substring(optParamOff + Protocol_AuthenticationRequest_Param.length());
                		logger.debug("Received optional parameter from client: '" + optionalParameter + "'.");
                }
            }
            else {
            		// now send my first message, but already need the public key for it
            		ka = new SimpleKeyAgreement(useJSSE);
            		toRemote.println(Protocol_AuthenticationRequest + new String(Hex.encodeHex(ka.getPublicKey())) +
            				(optionalParameter != null ? " " + Protocol_AuthenticationRequest_Param + optionalParameter : ""));
            }
        		raiseAuthenticationProgressEvent(remote, 2, AuthenticationStages, inOrOut + " authentication connection, " + clientToServer + " public key");

        		if (serverSide) {
                // for performance reasons: only now start the DH phase
                ka = new SimpleKeyAgreement(useJSSE);
                toRemote.println(Protocol_AuthenticationAcknowledge + new String(Hex.encodeHex(ka.getPublicKey())));
        		}
        		else {
                remotePubKey = helper_extractPublicKey(
                		helper_getAuthenticationParamLine(Protocol_AuthenticationAcknowledge, remote),
                		Protocol_AuthenticationAcknowledge, remote);
                if (remotePubKey == null)
                {
                    shutdownSocketCleanly();
                    return;
                }
        		}
            raiseAuthenticationProgressEvent(remote, 3, AuthenticationStages, inOrOut + " authentication connection, " + serverToClient + " public key");

            ka.addRemotePublicKey(remotePubKey);
            raiseAuthenticationProgressEvent(remote, 4, AuthenticationStages, inOrOut + " authentication connection, computed shared secret");

            // the authentication success event sent here is just an array of two keys
            if (keepSocketConnected) {
            	logger.debug("Not closing socket as requested, but passing it to the success event.");
            	// don't shut down the streams because this effectively shuts down the TCP connection
            	// but make sure that the last message has been sent successfully
            	toRemote.flush();
            	raiseAuthenticationSuccessEvent(remote, new Object[] {ka.getSessionKey(), ka.getAuthenticationKey(),
            			optionalParameter, socket});
            }
            else {
            	raiseAuthenticationSuccessEvent(remote, new Object[] {ka.getSessionKey(), ka.getAuthenticationKey(),
            			optionalParameter });
            	shutdownSocketCleanly();
            }
        }
        catch (InternalApplicationException e)
        {
            logger.error(e);
            // also communicate any application exception to interested
			// listeners
            raiseAuthenticationFailureEvent(remote, e, null);
            shutdownSocketCleanly();
        }
        catch (IOException e)
        {
            logger.debug(e);
            // even if we ignore the exception and not treat it as an error
			// case, report it to listeners
            // so that they can clean up their state of this authentication
			// (identified by the remote)
            raiseAuthenticationFailureEvent(remote, null, "Client closed connection unexpectedly\n");
            shutdownSocketCleanly();
        }
        catch (Exception e)
        {
            logger.fatal("UNEXPECTED EXCEPTION: " + e /*+ "\n" + e.getStackTrace()*/);
            e.printStackTrace();
            shutdownSocketCleanly();
        }
        finally {
            if (ka != null)
                ka.wipe();
            logger.debug("Ended " + inOrOut + " authentication connection with " + remote);
        }
    }
    
    /** Hack to just allow one method to be called asynchronously while still having access to the outer class. */
    private abstract class AsynchronousCallHelper implements Runnable {
    		protected HostProtocolHandler outer;
    	
    		protected AsynchronousCallHelper(HostProtocolHandler outer) {
    			this.outer = outer;
    		}
    }

    /**
	 * Starts a background thread for handling an incoming authentication
	 * request. Should only be called by HostServerSocket after accepting a new
	 * connection.
	 */
	void startIncomingAuthenticationThread() {
		logger.debug("Starting incoming authentication thread handler");
		new Thread(new AsynchronousCallHelper(this) {
			public void run() {
				outer.performAuthenticationProtocol(true);
			}
		}).start();
		logger.debug("Started incoming authentication thread handler");
	}

    /**
	 * Outgoing authentication connections are done asynchronously just like the
	 * incoming connections. This method starts a new thread that tries to
	 * authenticate with the host given as remote. Callers need to subscribe to
	 * the Authentication* events to get notifications of authentication
	 * success, failure and progress.
	 * 
	 * @param remoteAddress
	 *            The remote host to try to connect to.
	 * @param remotePort
	 *            The remote TCP port to try to connect to.
	 * @param eventHandler
	 *            The event handler that should be notified of authentication
	 *            events. Can be null (in which case no events are sent). If not
	 *            null, it will be registered with a new HostProtocolHandler
	 *            object before starting the authentication protocol so that it
	 *            is guaranteed that all events are posted to the event handler.
	 * @param keepSocketConnected
	 *            When set to true, the socket created in this method is not
	 *            closed but passed to the authentation success event for
	 *            further reuse.
	 * @param optionalParameter
	 *            If not null, this string will be passed to the server in the
	 *            authentication request message. Both the server and the client 
	 *            will then subsequently forward this string in their 
	 *            authentication success message. This parameter <b>must</b> be
	 *            encoded in 7-bit ASCII and <b>must not</b> contain spaces.
	 *            
	 * @param useJSSE If set to true, the JSSE API with the default JCE provider of the JVM will be used
	 *                for cryptographic operations. If set to false, an internal copy of the Bouncycastle
	 *                Lightweight API classes will be used.
	 */
    static public void startAuthenticationWith(String remoteAddress,
			int remotePort, AuthenticationProgressHandler eventHandler,
			boolean keepSocketConnected, String optionalParameter,
			boolean useJSSE) throws UnknownHostException, IOException {
		logger.info("Starting authentication with " + remoteAddress);

		Socket clientSocket = new Socket(remoteAddress, remotePort);

		logger.info("Connected successfully to " + remoteAddress);
    	
		HostProtocolHandler tmpProtocolHandler = new HostProtocolHandler(clientSocket, keepSocketConnected, useJSSE);
		tmpProtocolHandler.useJSSE = useJSSE;
		if (eventHandler != null)
			tmpProtocolHandler.addAuthenticationProgressHandler(eventHandler);
		tmpProtocolHandler.optionalParameter = optionalParameter;
		
		// start the authentication protocol in the background
		new Thread(tmpProtocolHandler.new AsynchronousCallHelper(
				tmpProtocolHandler) {
			public void run() {
				outer.performAuthenticationProtocol(false);
			}
		}).start();
	}
}