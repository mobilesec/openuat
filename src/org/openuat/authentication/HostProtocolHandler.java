/* Copyright Rene Mayrhofer
 * File created 2005-09
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.authentication;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.util.Hashtable;

import org.openuat.authentication.exceptions.*;
import org.openuat.util.LineReaderWriter;
import org.openuat.util.ProtocolCommandHandler;
import org.openuat.util.RemoteConnection;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.log4j.Logger;

/** This class handles the key agreement protocol between two hosts on a stream
 * level. It implements both sides of the protocol, allowing to handle incoming
 * connections (i.e. incoming authentication requests) as well as initiating
 * outgoing connections (i.e. outgoing authentication requests). Events are
 * raised upon authentication success, failure and during the progress of an
 * authentication protocol.
 * 
 * The authentication success event generated by this protocol will return a 
 * RemoteConnection object for the remote parameter and an Object array as the result
 * parameter. This object array will always have at least 3 objects: two byte arrays
 * representing the session key and the authentication key and a String representing
 * the optional parameter that might have been specified by the client or which might
 * have been passed to the protocol when in client mode. This third object in the object
 * array can be null if no parameter was specified, but it will always be there. 
 * An optional fourth object will be included with the array when the keepSocketConnected
 * flag was set. This fourth paramater will then contain the still connected channel object.
 * 
 * If, in addition to a simple Diffie-Hellman based key agreement, the server part of this
 * HostProtocolHandler should support other commands, then custom handlers can be 
 * registered with addProtocolCommandHandlers. These commands can subsequently be handled 
 * at the stage when the Protocol_AuthenticationRequest command would be expected.
 * 
 * @author Rene Mayrhofer
 * @version 1.1, changes to 1.0: Support registering additional protocol 
 *               handlers that are called for their registered protocol.
 */
public class HostProtocolHandler extends AuthenticationEventSender {
	/** Our primary logger. */
	private static Logger logger = Logger.getLogger("org.openuat.authentication.HostProtocolHandler" /*HostProtocolHandler.class*/);
	
	/** These are the messages of the ASCII authentication protocol. */
    public static final String Protocol_Hello = "HELO OpenUAT Authentication";
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

    /** The (already opened) connection used to communicate with the remote end, for both incoming and outgoing connections. */
    private RemoteConnection connection;
    /** If set to false, connection will be closed after the protocol finished. 
     * @see #connection
     * @see #HostProtocolHandler(RemoteConnection, boolean, boolean)
     */
    private boolean keepConnected;
    /** An optional parameter that can be passed from the client to the server
     * in its authentication request message. If not null, this message will be
     * forwarded by both the server and the client in their respective authentication
     * success messages.
     */
    private String optionalParameter = null;
    /** The stream to send messages to the remote end. */
    private OutputStreamWriter toRemote;
    /** The stream to receive messages from the remote end. */
    private InputStream fromRemote;
    
    /** There may be additional handlers to call, depending on the first line
     * that is received from the other side. Keys are of type String and 
     * specify the first word (command) that a handler reacts to, values are
     * of type ProtocolCommandHandler.
     */
    private Hashtable protocolCommandHandlers = null;
    
    /** This class should only be instantiated by HostServerSocket for incoming
	 * connections or with the static startAuthenticatingWith method for
	 * outgoing connections.
	 * 
	 * @param con
	 *            The RemoteConnection to use for communication. It must already be
	 *            connected to the other side, but will be shut down and closed
	 *            before the protocol handler methods return, depending on the
	 *            parameter keepConnected. The reason for this asymmetry (the 
	 *            connection must be connected by the caller, but is closed by 
	 *            the methods of this class) lies in the asynchronity: the 
	 *            protocol handler methods are called in background threads 
	 *            and must therefore dispose the objects before exiting.
	 *            
	 * @param keepConnected
	 *            If set to true, the opened connection con is passed to the
	 *            authentication success event (in the results parameter) for 
	 *            further re-use of the connection (e.g. passing additional 
	 *            information about further protocol steps). If set to false, the
	 *            socket will be closed when this protocol is done with it.
	 *
	 * @param useJSSE If set to true, the JSSE API with the default JCE provider of the JVM will be used
	 *                for cryptographic operations. If set to false, an internal copy of the Bouncycastle
	 *                Lightweight API classes will be used.
	 */
    public HostProtocolHandler(RemoteConnection con, boolean keepConnected, boolean useJSSE) {
		this.connection = con;
		this.keepConnected = keepConnected;
		this.useJSSE = useJSSE;
    }

    /** Adds a protocol command handler.
     * 
     * @param command The command to react to.
     * @param handler The handler that will be called to handle the protocol 
     *                session when it is started with command.
     */
    public void addProtocolCommandHandler(String command, ProtocolCommandHandler handler) {
    	if (protocolCommandHandlers == null)
    		protocolCommandHandlers = new Hashtable();
    	protocolCommandHandlers.put(command, handler);
    }
    
    /** Removes a protocol command handler.
     * 
     * @param command The command to stop reacting to.
     * @return true if the command handler was removed, false otherwise (if
     *         no handler was previously registered for this command).
     */
    public boolean removeProtocolCommandHandler(String command) {
    	if (protocolCommandHandlers == null)
    		return false;
    	boolean removed = (protocolCommandHandlers.remove(command) != null);
    	if (protocolCommandHandlers.size() == 0)
    		protocolCommandHandlers = null;
    	return removed;
    }
    
    /** Set the list of registered protocol command handlers, if none has 
     * been registered so far. This should only be used for initialization 
     * and will not do anything if any listener has been registered before.
     * @param handlers The list of command handlers to use. Keys <b>must</b>
     *                 be of type String, while values <b>must</b> be of type
     *                 ProtocolCommandHandler.
     * @return true if the list was set, false otherwise (when any listener has
     *         been registered before, the list will not be overwritten).
     */ 
    public void setProtocolCommandHandlers(Hashtable handlers) {
    	if (protocolCommandHandlers == null) 
    		protocolCommandHandlers = handlers;
    	else
    		logger.error("Not overwriting already initialized list of protocol command handlers");
    }
    
    /** Helper method used for closing the streams connected to the socket
	 * cleanly. 
	 * 
	 * @see #fromRemote
	 * @see #toRemote
	 */
    void shutdownStreamsCleanly() {
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
   			// need to ignore here, nothing we can do about it...
   			logger.error("Unable to close streams cleanly", e);
   		}
    }
    
    /** Helper method used for closing the connection cleanly. Calls 
	 * shutdownStreamsCleanly beforehand.
	 *
	 * @see #shutdownStreamsCleanly()
	 * @see #connection
	 */
    void shutdownConnectionCleanly() {
    	shutdownStreamsCleanly();
    	logger.debug("Shutting down sockets");
   		connection.close();
    }
    
    private String readLine() throws IOException {
    	return LineReaderWriter.readLine(fromRemote);
    }
    
    private void println(String line) throws IOException {
    	LineReaderWriter.println(toRemote, line);
    }
    
    /** Tries to receive a properly formatted parameter line from the remote host.
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
	 * @param allowOtherCommands
	 * 			  If true, then protocolCommandHandlers are checked when the
	 *            received word does not match expectedMsg.
	 *            
	 * @return The complete parameter line on success, null otherwise.
	 */
    private String helper_getAuthenticationParamLine(String expectedMsg, RemoteConnection remote, boolean allowOtherCommands) throws IOException {
    	String msg = readLine();
        if (msg == null) {
        	logger.warn("helper_getAuthenticationParamLine called with null argument");
            raiseAuthenticationFailureEvent(remote, null, "Protocol error: no message received");
            return null;
        }

        // try to extract the remote key from it
        if (!msg.startsWith(expectedMsg)) {
        	logger.debug("Received non-standard command line '" + msg + "'");
        	if (protocolCommandHandlers != null && allowOtherCommands) {
        		// we have registered handlers, maybe one can deal with the first word
        		String command = msg;
        		int firstSpace = msg.indexOf(' ');
        		if (firstSpace > 0)
        			command = msg.substring(0, firstSpace);
        		logger.debug("Checking " + protocolCommandHandlers.size() + 
        				" registered protocol command handlers for '" + command + "'");
        		if (protocolCommandHandlers.containsKey(command)) {
        			logger.debug("Command handler is known, calling it");
        			// yes, a handler is known, delegate here
        			if (! ((ProtocolCommandHandler) protocolCommandHandlers.get(command)).handleProtocol(
        					msg, remote)) {
        				logger.error("Could not handle protocol command '" + command + 
        						"', registered handler returned error");
        			}
        		}
        		else
        			logger.debug("No command handler known, ignoring and aborting protocol run");
        		// already handled in here, stop processing
        		return null;
        	}

        	logger.warn("Protocol error: unkown message '" + msg + "'");
            println("Protocol error: unknown message: '" + msg + "'");
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
	 * @throws IOException
	 */
    private byte[] helper_extractPublicKey(String paramLine, String expectedMsg, RemoteConnection remote) throws IOException
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
            println("Protocol error: could not parse public key, expected 128 Bytes hex-encoded.");
            raiseAuthenticationFailureEvent(remote, e, "Protocol error: can not decode remote public key");
            return null;
        }
        if (remotePubKey.length < 128)
        {
            logger.warn("Protocol error: could not parse public key, expected 128 Bytes hex-encoded.");
            println("Protocol error: could not parse public key, expected 128 Bytes hex-encoded.");
            raiseAuthenticationFailureEvent(remote, null, "Protocol error: remote key too short (only " + remotePubKey.length + " bytes instead of 128)");
            return null;
        }
        return remotePubKey;
    }
    
    /** This method depends on prior initialization and assumes to be launched
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
        String inOrOut, serverToClient, clientToServer,
        	remoteName = connection.getRemoteName();

        if (logger.isDebugEnabled()) {
        	logger.debug("Starting authentication protocol as " + (serverSide ? "server" : "client"));
        	logger.debug("Remote name is " + remoteName);
        }

        if (serverSide) {
        	inOrOut = "Incoming";
        	serverToClient = "sent";
        	clientToServer = "received";
        } else {
        	inOrOut = "Outgoing";
        	serverToClient = "received";
        	clientToServer = "sent";
        }
        
        if (logger.isDebugEnabled())
        	logger.debug(inOrOut + " connection to authentication service with " + remoteName);
        
        try
        {
        	fromRemote = connection.getInputStream();
            // this enables auto-flush
            toRemote = new OutputStreamWriter(connection.getOutputStream());
            
            if (serverSide) {
            	println(Protocol_Hello);
            }
            else {
                String msg = readLine();
                if (!msg.equals(Protocol_Hello)) {
                	raiseAuthenticationFailureEvent(connection, null, "Protocol error: did not get greeting from server");
                    shutdownConnectionCleanly();
                    return;
                }
        	}
            raiseAuthenticationProgressEvent(connection, 1, AuthenticationStages, inOrOut + " authentication connection, " + serverToClient + " greeting");

            byte[] remotePubKey = null;
            if (serverSide) {
            	String paramLine = helper_getAuthenticationParamLine(Protocol_AuthenticationRequest, connection, true);
                remotePubKey = helper_extractPublicKey(paramLine, Protocol_AuthenticationRequest, connection);
                if (remotePubKey == null) {
                    shutdownConnectionCleanly();
                    return;
                }
                int optParamOff = paramLine.indexOf(Protocol_AuthenticationRequest_Param);
                if (optParamOff != -1) {
                		optionalParameter = paramLine.substring(optParamOff + Protocol_AuthenticationRequest_Param.length());
                		if (logger.isDebugEnabled())
                			logger.debug("Received optional parameter from client: '" + optionalParameter + "'.");
                }
            }
            else {
            		// now send my first message, but already need the public key for it
            		ka = new SimpleKeyAgreement(useJSSE);
            		println(Protocol_AuthenticationRequest + new String(Hex.encodeHex(ka.getPublicKey())) +
            				(optionalParameter != null ? " " + Protocol_AuthenticationRequest_Param + optionalParameter : ""));
            }
            raiseAuthenticationProgressEvent(connection, 2, AuthenticationStages, inOrOut + " authentication connection, " + clientToServer + " public key");

            if (serverSide) {
                // for performance reasons: only now start the DH phase
                ka = new SimpleKeyAgreement(useJSSE);
                	println(Protocol_AuthenticationAcknowledge + new String(Hex.encodeHex(ka.getPublicKey())));
            }
            else {
            	remotePubKey = helper_extractPublicKey(
            			helper_getAuthenticationParamLine(Protocol_AuthenticationAcknowledge, connection, false),
                		Protocol_AuthenticationAcknowledge, connection);
                if (remotePubKey == null)
                {
                    shutdownConnectionCleanly();
                    return;
                }
            }
            raiseAuthenticationProgressEvent(connection, 3, AuthenticationStages, inOrOut + " authentication connection, " + serverToClient + " public key");

            ka.addRemotePublicKey(remotePubKey);
            raiseAuthenticationProgressEvent(connection, 4, AuthenticationStages, inOrOut + " authentication connection, computed shared secret");

            // the authentication success event sent here is just an array of two keys
            if (keepConnected) {
            	logger.debug("Not closing socket as requested, but passing it to the success event.");
            	// don't shut down the streams because this effectively shuts down the connection
            	// but make sure that the last message has been sent successfully
            	toRemote.flush();
            	raiseAuthenticationSuccessEvent(connection, new Object[] {ka.getSessionKey(), ka.getAuthenticationKey(),
            			optionalParameter, connection});
            }
            else {
            	raiseAuthenticationSuccessEvent(connection, new Object[] {ka.getSessionKey(), ka.getAuthenticationKey(),
            			optionalParameter });
            	shutdownConnectionCleanly();
            }
        }
        catch (InternalApplicationException e)
        {
            logger.error("Caught exception during host protocol run, aborting: " + e);
            // also communicate any application exception to interested
			// listeners
            raiseAuthenticationFailureEvent(connection, e, null);
            shutdownConnectionCleanly();
        }
        catch (IOException e)
        {
            logger.error("Caught exception during host protocol run, aborting: " + e);
            // even if we ignore the exception and not treat it as an error
			// case, report it to listeners
            // so that they can clean up their state of this authentication
			// (identified by the remote)
            raiseAuthenticationFailureEvent(connection, null, "Client closed connection unexpectedly\n");
            shutdownConnectionCleanly();
        }
        catch (Exception e)
        {
            logger.fatal("UNEXPECTED EXCEPTION: " + e);
            e.printStackTrace();
            shutdownConnectionCleanly();
        }
        finally {
            if (ka != null)
                ka.wipe();
            if (logger.isDebugEnabled())
            	logger.debug("Ended " + inOrOut + " authentication connection with " + remoteName);
        }
    }
    
    /** Hack to just allow one method to be called asynchronously while still having access to the outer class. */
    private abstract class AsynchronousCallHelper implements Runnable {
    		protected HostProtocolHandler outer;
    	
    		protected AsynchronousCallHelper(HostProtocolHandler outer) {
    			this.outer = outer;
    		}
    }

    /** Starts a background thread for handling an incoming authentication
	 * request. Should only be called by HostServerSocket after accepting a new
	 * connection.
	 * @param asynchronousCall When set to true, this method will perform the
	 *                         protocol asynchronously an return immediately to the
	 *                         caller (firing events later on from the other thread). 
	 *                         When set to false, this method will block until the 
	 *                         authentication protocol has been completed (events will 
	 *                         be fired from within the thread of the caller)
	 */
	public void startIncomingAuthenticationThread(boolean asynchronousCall) {
		logger.debug("Starting incoming authentication thread handler");
		if (asynchronousCall) {
			new Thread(new AsynchronousCallHelper(this) {
				public void run() {
					outer.performAuthenticationProtocol(true);
				}
			}).start();
			logger.debug("Started incoming authentication thread handler");
		}
		else {
			performAuthenticationProtocol(true);
			logger.debug("Exiting incoming authentication thread handler");
		}
	}
	
    /** Outgoing authentication connections are done asynchronously just like the
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
	 * @param keepConnected
	 *            When set to true, the connection created in this method is not
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
    static public void startAuthenticationWith(RemoteConnection remote,
			AuthenticationProgressHandler eventHandler,
			boolean keepConnected, String optionalParameter,
			boolean useJSSE) throws IOException {
    	if (logger.isInfoEnabled())
    		logger.info("Starting authentication with " + 
    				remote.getRemoteAddress() + "'/" + remote.getRemoteName() + "'");

		HostProtocolHandler tmpProtocolHandler = new HostProtocolHandler(remote, keepConnected, useJSSE);
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
