package org.eu.mayrhofer.authentication;

import org.eu.mayrhofer.authentication.exceptions.*;
import java.net.*;
import java.io.*;
import java.util.*;

import org.apache.commons.codec.*;
import org.apache.commons.codec.binary.*;

public class HostProtocolHandler {
    public static final String Protocol_Hello = "HELO RelateAuthentication";
    public static final String Protocol_AuthenticationRequest = "AUTHREQ ";
    public static final String Protocol_AuthenticationAcknowledge = "AUTHACK ";

    public static final int AuthenticationStages = 4;

    private Socket socket;
    private PrintWriter toRemote;
    private BufferedReader fromRemote;
    
    static private LinkedList eventsHandlers;

    // / <summary>
    // / This class should only be instantiated by HostServerSocket for incoming
	// connections or with the
    // / static StartAuthenticatingWith method for outgoing connections.
    // / </summary>
    // / <param name="soc">The socket to use for communication. It must already
	// be connected to the
    // / other side, but will be shut down and closed before the protocol
	// handler methods return. The
    // / reason for this asymmetry (the socket must be connected by the caller,
	// but is closed by the
    // / methods of this class) lies in the asynchronity: the protocol handler
	// methods are called in
    // / background threads and must therefore dispose the objects before
	// exiting.</param>
    HostProtocolHandler(Socket soc) 
	{
    	eventsHandlers = new LinkedList();
		socket = soc;
    }
    
    static public void addAuthenticationProgressHandler(AuthenticationProgressHandler h) {
    	if (eventsHandlers.contains(h))
    		eventsHandlers.add(h);
    }

    static public boolean removeAuthenticationProgressHandler(AuthenticationProgressHandler h) {
   		return eventsHandlers.remove(h);
    }
    
    static private void raiseAuthenticationSuccessEvent(InetAddress remote, byte[] sharedKey) {
    	if (eventsHandlers != null)
    		for (ListIterator i = eventsHandlers.listIterator(); i.hasNext(); )
    			((AuthenticationProgressHandler) i.next()).AuthenticationSuccess(remote, sharedKey);
    }

    static private void raiseAuthenticationFailureEvent(InetAddress remote, Exception e, String msg) {
    	if (eventsHandlers != null)
    		for (ListIterator i = eventsHandlers.listIterator(); i.hasNext(); )
    			((AuthenticationProgressHandler) i.next()).AuthenticationFailure(remote, e, msg);
    }

    static private void raiseAuthenticationProgressEvent(InetAddress remote, int cur, int max, String msg) {
    	if (eventsHandlers != null)
    		for (ListIterator i = eventsHandlers.listIterator(); i.hasNext(); )
    			((AuthenticationProgressHandler) i.next()).AuthenticationProgress(remote, cur, max, msg);
    }

    void shutdownSocketsCleanly()
    {
    	try {
    	if (fromRemote != null)
    		fromRemote.close();
    	if (toRemote != null) {
    		toRemote.flush();
    		toRemote.close();
    	}
        if (socket != null && socket.isConnected())
        {
        	socket.shutdownInput();
        	socket.shutdownOutput();
        	socket.close();
        }
    	}
    	catch (IOException e) {
    		throw new RuntimeException("Unable to close sockets cleanly", e);
    	}
    }
    
    // / <summary>
    // / Tries to receive a properly formatted public key from the remote host.
	// If decoding fails, an
    // / OnAuthenticationFailure event is raised.
    // / </summary>
    // / <param name="expectedMsg">Gives the message that is expected to be
	// received: for server mode,
    // / a Protocol_AuthenticationRequest message is expected, while for client
	// mode, a
    // / Protocol_AuthenticationAcknowledge is expected.</param>
    // / <param name="remotePubKey">The extracted public key is returned in this
	// array. If decoding failed,
    // / null will be returned instead of the (meaningless) parts that might
	// have been decoded.</param>
    // / <param name="remote">The remote socket. This is only needed for raising
	// events and is passed
    // / unmodified to the event method.</param>
    // / <returns>true if a properly formatted public key was found within the
	// expected message,
    // / false otherwise.</returns>
    private byte[] Helper_ExtractPublicKey(String expectedMsg, InetAddress remote) throws IOException
    {
    	String msg = fromRemote.readLine();
    	byte[] remotePubKey;
        if (msg == null)
        {
            raiseAuthenticationFailureEvent(remote, null, "Protocol error: no message received");
            return null;
        }

        // try to extract the remote key from it
        if (!msg.startsWith(expectedMsg))
        {
            toRemote.println("Protocol error: unknown message: '" + msg + "'");
            raiseAuthenticationFailureEvent(remote, null, "Protocol error: unknown message");
            return null;
        }
        String remotePubKeyStr = msg.substring(expectedMsg.length());
        try {
        	remotePubKey = Hex.decodeHex(remotePubKeyStr.toCharArray());
        }
        catch (DecoderException e) {
            toRemote.println("Protocol error: could not parse public key, expected 128 Bytes hex-encoded.");
            raiseAuthenticationFailureEvent(remote, e, "Protocol error: can not decode remote public key");
            return null;
        }
        if (remotePubKey.length != 128)
        {
            toRemote.println("Protocol error: could not parse public key, expected 128 Bytes hex-encoded.");
            raiseAuthenticationFailureEvent(remote, null, "Protocol error: remote key too short");
            return null;
        }
        return remotePubKey;
    }
    
    // Hack to just allow one method to be called asynchronously while still having access to the outer class
    private abstract class AsynchronousCallHelper implements Runnable {
    	protected HostProtocolHandler outer;
    	
    	protected AsynchronousCallHelper(HostProtocolHandler outer) {
    		this.outer = outer;
    	}
    }

	void startIncomingAuthenticationThread()
	{
		new Thread(new AsynchronousCallHelper(this) 
	{ public void run() {
        SimpleKeyAgreement ka = null;
        // remember whom we are communication with: when the socket gets closed,
		// we can no longer access it
        InetAddress remote = socket.getInetAddress();

        try
        {
            fromRemote = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            toRemote = new PrintWriter(socket.getOutputStream());

            toRemote.println(Protocol_Hello);

            outer.raiseAuthenticationProgressEvent(remote, 1, AuthenticationStages, "Incoming authentication connection, sent greeting");

            byte[] remotePubKey = Helper_ExtractPublicKey(Protocol_AuthenticationRequest, remote);
            if (remotePubKey == null)
            {
                shutdownSocketsCleanly();
                return;
            }
            outer.raiseAuthenticationProgressEvent(remote, 2, AuthenticationStages, "Incoming authentication connection, received public key");

            // for performance reasons: only now start the DH phase
            ka = new SimpleKeyAgreement();
            toRemote.println(Protocol_AuthenticationAcknowledge + Hex.encodeHex(ka.getPublicKey()).toString());
            outer.raiseAuthenticationProgressEvent(remote, 3, AuthenticationStages, "Incoming authentication connection, sent public key");

            ka.addRemotePublicKey(remotePubKey);
            outer.raiseAuthenticationProgressEvent(remote, 4, AuthenticationStages, "Incoming authentication connection, computed shared secret");

            outer.raiseAuthenticationSuccessEvent(remote, ka.getSessionKey());
        }
        catch (InternalApplicationException e)
        {
            System.out.println(e);
            // also communicate any application exception to interested
			// listeners
            raiseAuthenticationFailureEvent(remote, e, null);
        }
        catch (IOException e)
        {
            // even if we ignore the exception and not treat it as an error
			// case, report it to listeners
            // so that they can clean up their state of this authentication
			// (identified by the remote
            raiseAuthenticationFailureEvent(remote, null, "Client closed connection unexpectedly\n");
        }
        catch (Exception e)
        {
            System.out.println("UNEXPECTED EXCEPTION: " + e);
        }
        finally {
            shutdownSocketsCleanly();
            if (ka != null)
                ka.wipe();
        }
    }}).start();
	}

    // / <summary>
    // / Outgoing authentication connections are done asynchronously just like
	// the incoming
    // / connections. This method starts a new thread that tries to authenticate
	// with the host
    // / given as remote. Callers need to subscribe to the Authentication*
	// events to get notifications
    // / of authentication success, failure and progress.
    // / </summary>
    static public void StartAuthenticationWith(String remoteAddress, int remotePort) throws UnknownHostException, IOException
    {
        Socket clientSocket = new Socket(remoteAddress, remotePort);

        HostProtocolHandler tmpProtocolHandler = new HostProtocolHandler(clientSocket);
        new Thread(tmpProtocolHandler.new AsynchronousCallHelper(tmpProtocolHandler) {
    public void run()
    {
        SimpleKeyAgreement ka = null;
        // remember whom we are communication with: when the socket gets closed,
		// we can no longer access it
        InetAddress remote = outer.socket.getInetAddress();

        try
        {
            outer.fromRemote = new BufferedReader(new InputStreamReader(outer.socket.getInputStream()));
            outer.toRemote = new PrintWriter(outer.socket.getOutputStream());

            String msg = outer.fromRemote.readLine();
            if (!msg.equals(Protocol_Hello))
            {
                outer.raiseAuthenticationFailureEvent(remote, null, "Protocol error: did not get greeting from server");

                outer.shutdownSocketsCleanly();
                return;
            }

            outer.raiseAuthenticationProgressEvent(remote, 1, AuthenticationStages, "Outgoing authentication connection, received greeting");

            // now send my first message, but already need the public key for it
            ka = new SimpleKeyAgreement();
            outer.toRemote.println(Protocol_AuthenticationRequest + Hex.encodeHex(ka.getPublicKey()).toString());
            outer.raiseAuthenticationProgressEvent(remote, 2, AuthenticationStages, "Outgoing authentication connection, sent public key");

            byte[] remotePubKey = outer.Helper_ExtractPublicKey(Protocol_AuthenticationAcknowledge, remote);
            if (remotePubKey == null)
            {
                outer.shutdownSocketsCleanly();
                return;
            }
            outer.raiseAuthenticationProgressEvent(remote, 3, AuthenticationStages, "Outgoing authentication connection, received public key");

            ka.addRemotePublicKey(remotePubKey);
            outer.raiseAuthenticationProgressEvent(remote, 4, AuthenticationStages, "Outgoing authentication connection, computed shared secret");

            outer.raiseAuthenticationSuccessEvent(remote, ka.getSessionKey());
        }
        catch (InternalApplicationException e)
        {
            System.out.println(e);
            // also communicate any application exception to interested
			// listeners
            outer.raiseAuthenticationFailureEvent(remote, e, null);
        }
        catch (IOException e)
        {
            // even if we ignore the exception and not treat it as an error
			// case, report it to listeners
            // so that they can clean up their state of this authentication
			// (identified by the remote
            outer.raiseAuthenticationFailureEvent(remote, null, "Server closed connection unexpectedly\n");
        }
        catch (Exception e)
        {
            System.out.println("UNEXPECTED EXCEPTION: " + e);
        }
        finally
        {
            outer.shutdownSocketsCleanly();
            if (ka != null)
                ka.wipe();
        }
    } }).start();
    }

}
