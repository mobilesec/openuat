/* Copyright Rene Mayrhofer
 * File created 2010-01-04
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.apps;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;

import org.openuat.authentication.AuthenticationProgressHandler;
import org.openuat.authentication.HostProtocolHandler;
import org.openuat.authentication.exceptions.InternalApplicationException;
import org.openuat.util.Hash;
import org.openuat.util.HostServerBase;
import org.openuat.util.RemoteTCPConnection;
import org.openuat.util.SimpleBlockCipher;
import org.openuat.util.TCPPortServer;

/** This class implements a very simple demo application for the
 * main authentication and key agreement protocol. It uses UACAP in
 * verification mode with simple text string comparison and then
 * applies SimpleBlockCipher for sending and receiving chat messages.
 * 
 * @author Rene Mayrhofer
 */
public class UacapConsoleDemo implements AuthenticationProgressHandler, Runnable {
    public static final int PORT = 23457;

    private RemoteTCPConnection socketToRemote;
    private byte[] sharedSessionKey;

    private HostServerBase server;
    private Socket client;

    // initialize as server
    private UacapConsoleDemo() throws IOException {
        server = new TCPPortServer(PORT, 10000, true, true);
	    server.start();
	    server.addAuthenticationProgressHandler(this);
		System.out.println("Waiting for connections");
    }
    
    // initialize as client
    private UacapConsoleDemo(String connectTo) throws IOException {
		System.out.println("Connecting to remote " + connectTo);
        client = new Socket(connectTo, PORT);
        HostProtocolHandler.startAuthenticationWith(
        		new RemoteTCPConnection(client), 
        		this, 10000, true, "", true);
    }
    
//    @Override
    public void finalize() {
        try {
        	if (client != null) {
				client.shutdownInput();
            client.shutdownOutput();
            client.close();
        	}
        	client = null;
        	if (server != null)
        		server.stop();
        	server = null;
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InternalApplicationException e) {
			e.printStackTrace();
		}
    }
    
	public static void main(String[] args) throws IOException, InterruptedException {
		if (args.length == 1) {
			UacapConsoleDemo testClient = new UacapConsoleDemo(args[0]);
		}
		else {
			UacapConsoleDemo testServer = new UacapConsoleDemo();
		}
		
		while (true)
			Thread.sleep(100);
	}
	
//	@Override
	public void AuthenticationFailure(Object sender, Object remote,
			Exception e, String msg) {
		System.out.println("Authentication FAILED with remote " + remote + ": "
				+ msg);
		if (e != null)
			e.printStackTrace();
	}

//	@Override
	public void AuthenticationProgress(Object sender, Object remote,
			int cur, int max, String msg) {
	}

//	@Override
	public boolean AuthenticationStarted(Object sender, Object remote) {
		System.out.println("Incoming authentication from remote " + remote);
		return true;
	}

//	@Override
	public void AuthenticationSuccess(Object sender, Object remote,
			Object result) {
		System.out.println("Authentication SUCCESS with remote " + remote);

    	Object[] res = (Object[]) result;
    	sharedSessionKey = (byte[]) res[0];
        byte[] sharedOObMsg = (byte[]) res[1];
		
		socketToRemote = (RemoteTCPConnection) remote;
		
		System.out.println("Please verify if this matches the other side: " +
				Hash.getHexString(sharedOObMsg).substring(0, 12));
		System.out.print("Y/N: ");
		BufferedReader localR = new BufferedReader(new InputStreamReader(System.in));
		
		try {
			if (! localR.readLine().equalsIgnoreCase("y")) {
				System.out.println("Aborting due to failed verification");
				return;
			}
			
			// the chat is started in another thread so as not to block this event handler
			new Thread(this).start();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

//	@Override
	public void run() {
		System.out.println("Starting chat with " + sharedSessionKey.length +
				" Bytes shared secret key ...");
		SimpleBlockCipher c = new SimpleBlockCipher(true);
		BufferedReader localR = new BufferedReader(new InputStreamReader(System.in));
		OutputStream remoteW;
		
		try {
			remoteW = socketToRemote.getOutputStream();
			InputStream remoteR = socketToRemote.getInputStream();
			
			while (true) {
				// dummy padding to always have at least 16 Bytes
				System.out.print("> ");
				String line = localR.readLine() + "                ";
				byte[] sendMsg = c.encrypt(line.getBytes(), line.getBytes().length * 8, 
						sharedSessionKey);
				remoteW.write(line.getBytes().length);
				remoteW.write(sendMsg);
				
				byte[] recvMsg = new byte[1024];
				int plainTextBytes = remoteR.read();
				remoteR.read(recvMsg);
				String remoteLine = new String(c.decrypt(recvMsg, plainTextBytes*8, sharedSessionKey));
				System.out.println("< " + remoteLine);
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InternalApplicationException e) {
			e.printStackTrace();
		}
	}
}
