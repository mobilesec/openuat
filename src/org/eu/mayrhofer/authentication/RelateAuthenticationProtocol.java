package org.eu.mayrhofer.authentication;

import java.io.*;
import java.security.SecureRandom;

/// <summary>
/// This is the main class of the relate authentication software: it ties together
/// the host and dongle protocol handlers. Since both handlers work asynchronously
/// in their own threads, this class must also handle the synchronisation between 
/// all events coming in from them.
/// </summary>
public class RelateAuthenticationProtocol implements AuthenticationProgressHandler {
	public static final int TcpPort = 54321;
	public static final String SerialPort = "/dev/ttyUSB0";
	
	private int remoteRelateId = 7;
	
    public void AuthenticationSuccess(Object remote, Object result)
    {
        System.out.println("Received authentication success event with " + remote);
        byte[][] keys = (byte[][]) result;
        System.out.println("Shared session key is now '" + keys[0] + "', shared authentication key is now '" + keys[1] + "'");
        System.out.println("Starting dongle authentication with remote relate id " + remoteRelateId);
    }

    public void AuthenticationFailure(Object remote, Exception e, String msg)
    {
        System.out.println("Received authentication failure event with " + remote);
        if (e != null)
            System.out.println("Exception: " + e);
        if (msg != null)
            System.out.println("Message: " + msg);
    }

    public void AuthenticationProgress(Object remote, int cur, int max, String msg)
    {
        System.out.println("Received authentication progress event with " + remote + " " + cur + " out of " + max + ": " + msg);
    }

    public static void main(String[] args) throws Exception
	{
        if (args.length > 0 && args[0].equals("server"))
        {
            HostServerSocket h1 = new HostServerSocket(TcpPort);
        	h1.addAuthenticationProgressHandler(new RelateAuthenticationProtocol());
            h1.startListening();
            new BufferedReader(new InputStreamReader(System.in)).readLine();
            h1.stopListening();
        }
        if (args.length > 1 && args[0].equals("client"))
        {
            HostProtocolHandler.startAuthenticationWith(args[1], TcpPort, new RelateAuthenticationProtocol());
        }

        SecureRandom r = new SecureRandom();
        byte[] sharedKey = new byte[16];
        r.nextBytes(sharedKey);
        DongleProtocolHandler dh = new DongleProtocolHandler(SerialPort, (byte) 7);
        dh.startAuthenticationWith(sharedKey, 2);
        
        // problem with the javax.comm API - doesn't release its native thread
        System.exit(0);
	}
}
