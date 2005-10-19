package org.eu.mayrhofer.authentication;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;

import java.security.KeyFactory;
import java.security.SecureRandom;
import javax.crypto.*;
import javax.crypto.spec.*;

/// <summary>
/// This is the main class of the relate authentication software: it ties together
/// the host and dongle protocol handlers. Since both handlers work asynchronously
/// in their own threads, this class must also handle the synchronisation between 
/// all events coming in from them.
/// </summary>
public class RelateAuthenticationProtocol implements AuthenticationProgressHandler {
	// temporary test code
	static public int MAGIC_1, MAGIC_2;
	
    public void AuthenticationSuccess(InetAddress remote, byte[] sharedSessionKey, byte[] sharedAuthenticationKey)
    {
        System.out.println("Received authentication success event with " + remote);
        System.out.println("Shared session key is now '" + sharedSessionKey + "', shared authentication key is now '" + sharedAuthenticationKey + "'");
    }

    public void AuthenticationFailure(InetAddress remote, Exception e, String msg)
    {
        System.out.println("Received authentication failure event with " + remote);
        if (e != null)
            System.out.println("Exception: " + e);
        if (msg != null)
            System.out.println("Message: " + msg);
    }

    public void AuthenticationProgress(InetAddress remote, int cur, int max, String msg)
    {
        System.out.println("Received authentication progress event with " + remote + " " + cur + " out of " + max + ": " + msg);
    }

    public static void main(String[] args) throws Exception
	{
    	HostProtocolHandler.addAuthenticationProgressHandler(new RelateAuthenticationProtocol());
        
        /*if (args.length > 0 && args[0].equals("server"))
        {
            HostServerSocket h1 = new HostServerSocket(54321);
            h1.startListening();
            new BufferedReader(new InputStreamReader(System.in)).readLine();
            h1.stopListening();
        }
        if (args.length > 0 && args[0].equals("client"))
        {
            HostProtocolHandler.startAuthenticationWith("localhost", 54321);
        }*/
        //Environment.Exit(0);
        //System.Diagnostics.Process.GetCurrentProcess().Kill();

        // temporary test code
    	MAGIC_1 = args.length >= 1 ? Integer.parseInt(args[0]) : 20;
    	MAGIC_2 = args.length >= 2 ? Integer.parseInt(args[1]) : 100;
    	
        DongleProtocolHandler h = new DongleProtocolHandler("/dev/ttyUSB0");
        SecureRandom r = new SecureRandom();
        byte[] sharedKey = new byte[16];
        byte[] nonce = new byte[16];
        r.nextBytes(sharedKey);
        r.nextBytes(nonce);
        // need to specifically request no padding or padding would enlarge the one 128 bits block to two
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(sharedKey, "AES"));
        byte[] rfMessage = cipher.doFinal(nonce);
        if (rfMessage.length != 16)
        	System.out.println("Encryption went wrong, got " + rfMessage.length + " bytes");
        
        h.authenticateWith(1, nonce, rfMessage, 2);
        
        // problem with the javax.comm API - doesn't release its native thread
        System.exit(0);
        
        /*try
        {
            DongleProtocolHandler.StartAuthenticationWith("COM3", -1, null, null);
        }
        catch (ConfigurationErrorException e)
        {
            Console.WriteLine("Caught exception, is the device present and not yet in use?");
            Console.WriteLine(e.Message);
            Console.WriteLine(e.StackTrace);
            Exception f = e.InnerException;
            while (f != null)
            {
                Console.WriteLine(f.Message);
                Console.WriteLine(f.StackTrace);
                f = f.InnerException;
            }
        }*/
	}

}
