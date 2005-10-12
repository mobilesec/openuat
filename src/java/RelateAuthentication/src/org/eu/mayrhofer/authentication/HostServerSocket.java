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
		listenerThread = new Thread(this);
		listenerThread.start();
	}

	public void stopListening() throws InternalApplicationException {
		running = false;
		listenerThread.interrupt();
		try {
			listenerThread.join();
			listenerThread = null;
		} catch (InterruptedException e) {
			throw new InternalApplicationException(
					"HostServerSocket listening thread got interrupted while waiting for it to finish. This should not happen.",
					e);
		}
	}

	public void run() {
		try {
			while (running) {
				Socket s = listener.accept();
				HostProtocolHandler h = new HostProtocolHandler(s);
				h.startIncomingAuthenticationThread();
			}
		} catch (IOException e) {
		}
	}
}
