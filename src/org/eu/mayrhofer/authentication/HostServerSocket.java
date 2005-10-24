package org.eu.mayrhofer.authentication;

import org.eu.mayrhofer.authentication.exceptions.*;
import java.net.*;
import java.util.ListIterator;
import java.io.*;

/** This class represents a listener on a TCP port which responds to incoming authentication requests by delegating any incoming
 * connection to the HostProtocolHandler class. More specifically, for each incoming TCP connection, the 
 * HostProtocolHandler.startIncomingAuthenticationThread is invoked with the connected TCP socket.
 * 
 * Listening is done in a background thread using blocking accept() calls. After constructing a HostServerSocket object for
 * a specific port, startListening() needs to be called to start accepting incoming connection.
 *  
 * @author Rene Mayrhofer
 */
public class HostServerSocket extends AuthenticationEventSender implements Runnable {
	/** This is the (bound but unconnected) TCP socket for listening for incoming connections. */
	private ServerSocket listener;

    // We use a pseudo-singleton pattern here: for each port, only one instance can exist. This map holds the known instances.
	//private static HashMap instances;

	/** this is a private thread object instead of the whole class being derived
	   from thread to prevent other classes from fiddling with this thread */
	private Thread listenerThread = null;

	/** Used to signal the listening thread to stop itself. */
	private boolean running = false;

	/** Initialized the listener socket by binding it to the specified port. */
	public HostServerSocket(int port) throws IOException {
		listener = new ServerSocket(port);
	}

	/** Starts a background thread (using the run() method of this class) that will listen for incoming connections. */
	public void startListening() {
		running = true;
		//System.out.println("Starting listening thread for server socket");
		listenerThread = new Thread(this);
		listenerThread.start();
		//System.out.println("Started listening thread for server socket");
	}

	/** Signals the background listening thread to stop and waits for it. This method will not return until the listening
	 * thread has terminated.
	 * @throws InternalApplicationException
	 */
	public void stopListening() throws InternalApplicationException {
		//System.out.println("Stopping listening thread for server socket");
		running = false;
		// this is not nice, but will throw an exception in the listener thread
		// and thus allow it to exit by itself
		try {
			listener.close();
			listenerThread.join();
			listenerThread = null;
		} catch (InterruptedException e) {
			throw new InternalApplicationException(
					"HostServerSocket listening thread got interrupted while waiting for it to finish. This should not happen.",
					e);
		} catch (IOException e) {
			throw new InternalApplicationException(
					"Could not close listening socket cleanly as a signal to the listener thread. This should not happen.",
					e);
		}
		//System.out.println("Stopped listening thread for server socket");
	}

	/** Does the actual listening for incoming connections by calling the blocking accept() on the listening socket in a loop.
	 * For each incoming connection, a new HostProtocolHandler object is created and its startIncomingAuthenticationThread is
	 * used to start a thread that handles the new connection.
	 */
	public void run() {
		//System.out.println("Listening thread for server socket now running");
		try {
			while (running) {
				//System.out.println("Listening thread for server socket waiting for connection");
				Socket s = listener.accept();
				HostProtocolHandler h = new HostProtocolHandler(s);
				// before starting the background thread, register all our own listeners with this new event sender
	    		for (ListIterator i = eventsHandlers.listIterator(); i.hasNext(); )
	    			h.addAuthenticationProgressHandler((AuthenticationProgressHandler) i.next());
				h.startIncomingAuthenticationThread();
			}
		} catch(SocketException e) {
			// Only ignore the SocketException when we have been signalled to stop. Otherwise it's a real error. 
			if (running)
				System.out.println("Error in listening thread: " + e);
			/*else
				System.out.println("Listening socket was forcibly closed, exiting listening thread now.");*/
		} catch (IOException e) {
			System.out.println("Error in listening thread: " + e);
		}
	}
}
