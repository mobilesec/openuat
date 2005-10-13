package org.eu.mayrhofer.authentication;

import org.eu.mayrhofer.authentication.exceptions.*;
import java.net.*;
import java.io.*;

public class HostServerSocket implements Runnable {
	private ServerSocket listener;

	// this is a private thread object instead of the whole class being derived
	// from thread to prevent other classes from fiddling with this thread
	private Thread listenerThread = null;

	private boolean running = false;

	public HostServerSocket(int port) throws IOException {
		listener = new ServerSocket(port);
	}

	public void startListening() {
		running = true;
		//System.out.println("Starting listening thread for server socket");
		listenerThread = new Thread(this);
		listenerThread.start();
		//System.out.println("Started listening thread for server socket");
	}

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

	public void run() {
		//System.out.println("Listening thread for server socket now running");
		try {
			while (running) {
				//System.out.println("Listening thread for server socket waiting for connection");
				Socket s = listener.accept();
				HostProtocolHandler h = new HostProtocolHandler(s);
				h.startIncomingAuthenticationThread();
			}
		} catch(SocketException e) {
			//System.out.println("Listening socket was forcibly closed, exiting listening thread now.");
		} catch (IOException e) {
			System.out.println("Error in listening thread: " + e);
		}
	}
}
