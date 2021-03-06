/* Copyright Rene Mayrhofer
 * File created 2005-09
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.authentication.test;

import java.net.*;
import java.util.Vector;
import java.io.*;

import org.openuat.authentication.*;
import org.openuat.authentication.exceptions.*;
import org.openuat.channel.main.HostServerBase;
import org.openuat.channel.main.ip.RemoteTCPConnection;
import org.openuat.channel.main.ip.TCPPortServer;

import junit.framework.*;

public class HostProtocolHandlerTest extends TestCase {
	public HostProtocolHandlerTest(String s) {
		super(s);
	}
	
    public static final int PORT = 23457;

    private boolean socketWasAlreadyOpen = false;

    private HostServerBase server;
    private Socket client;
    private BufferedReader sr;
    private PrintWriter sw;
    
    protected boolean useJSSEServer = true;
    protected boolean useJSSEClient = true;

	@Override
	public void setUp() throws InterruptedException, IOException
    {
        // This is a rather dirty hack: whenever the socket was open earlier and has just been closed
        // by the TearDown method before this SetUp method was called, it seems that the OS does need
        // some time to clean up the socket and free it for another listener. It's just wrong that
        // TcpListener.Stop() doesn't take care of that, i.e. that it is not really a synchronous
        // operation!
        if (socketWasAlreadyOpen)
            Thread.sleep(100);

        server = new TCPPortServer(PORT, 10000, true, useJSSEServer);
        server.start();
        socketWasAlreadyOpen = true;
    }

	@Override
	public void tearDown() throws InterruptedException, IOException, InternalApplicationException
    {
        if (client != null) {
        	if (!client.isInputShutdown() && !client.isClosed())
        		client.shutdownInput();
        	if (!client.isOutputShutdown() && !client.isClosed())
        		client.shutdownOutput();
        	if (!client.isClosed())
            client.close();
        }
        client = null;
        if (server != null)
            server.stop();
        server = null;
    }

    public void testReceiveHello() throws InterruptedException, IOException
    {
        client = new Socket("127.0.0.1", PORT);
        Assert.assertTrue(client.isConnected());

        sr = new BufferedReader(new InputStreamReader(client.getInputStream()));
        sw = new PrintWriter(client.getOutputStream());

        Thread.sleep(100);
        Assert.assertTrue("Server is not sending a hello message", client.getReceiveBufferSize() > 0);
        String msg = sr.readLine();
        Assert.assertEquals("Hello message is not what I expected: '" + msg + "' instead of '" + HostProtocolHandler.Protocol_Hello + "'", msg, HostProtocolHandler.Protocol_Hello);
        sw.flush();
    }

    public void testCompleteAuthentication_OOBVerificationMode() throws UnknownHostException, IOException, InterruptedException
    {
        EventHelper h = new EventHelper();
        // need to listen for both the server and the client authentication events
        server.addAuthenticationProgressHandler(h);
        client = new Socket("127.0.0.1", PORT);
        HostProtocolHandler.startAuthenticationWith(new RemoteTCPConnection(client), h, 10000, false, "", useJSSEClient);
        // this should be enough time for the authentication to complete
        // localhost authentication within the same process, therefore we should receive 2 success messages
        int i = 0;
        while (i < 50 && h.getReceivedSecrets() != 2 && h.getReceivedFailures() == 0)
        {
            Thread.sleep(100);
            i++;
        }
        Assert.assertEquals(0, h.getReceivedFailures());
        Assert.assertEquals(2*HostProtocolHandler.AuthenticationStages, h.getReceivedProgress());
        Assert.assertEquals(2, h.getReceivedStarted());

        Assert.assertEquals(2, h.getReceivedSecrets());
        Assert.assertTrue(h.areSharedSecretsEqual());

        h.shutdownSocketsCleanly();
    }
    
    public void testCompleteAuthentication_SecretPreInputMode() throws UnknownHostException, IOException, InterruptedException
    {
    	byte[] presharedShortSecret = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
    	Vector presharedShortSecrets = new Vector();
    	presharedShortSecrets.addElement(presharedShortSecret);
    	
        EventHelper h = new EventHelper();
        // need to listen for both the server and the client authentication events
        server.addAuthenticationProgressHandler(h);
        server.setPresharedShortSecret(presharedShortSecret);
        client = new Socket("127.0.0.1", PORT);
        HostProtocolHandler.startAuthenticationWith(new RemoteTCPConnection(client), h, 
        		null, presharedShortSecrets, null,
        		10000, false, "", useJSSEClient);
        // this should be enough time for the authentication to complete
        // localhost authentication within the same process, therefore we should receive 2 success messages
        int i = 0;
        while (i < 50 && h.getReceivedSecrets() != 2 && h.getReceivedFailures() == 0)
        {
            Thread.sleep(100);
            i++;
        }
        Assert.assertEquals(0, h.getReceivedFailures());
        Assert.assertEquals(2*HostProtocolHandler.AuthenticationStages, h.getReceivedProgress());
        Assert.assertEquals(2, h.getReceivedStarted());

        Assert.assertEquals(2, h.getReceivedSecrets());
        Assert.assertTrue(h.areSessionKeysEqual() && h.areOObMsgsEmpty());

        h.shutdownSocketsCleanly();
    }

    public void testCompleteAuthentication_LongPreAuthenticationMode_Server() throws UnknownHostException, IOException, InterruptedException, InternalApplicationException
    {
        EventHelper h = new EventHelper();
        // need to listen for both the server and the client authentication events
        server.addAuthenticationProgressHandler(h);
		SimpleKeyAgreement serverKa = new SimpleKeyAgreement(useJSSEServer, true);
		server.setPermanentKeyAgreementInstance(serverKa);
        byte[] serverPreAuthentication = server.getPermanentPreAuthenticationMessage();
        client = new Socket("127.0.0.1", PORT);
        HostProtocolHandler.startAuthenticationWith(new RemoteTCPConnection(client), h, 
        		null, null, serverPreAuthentication,
        		10000, false, "", useJSSEClient);
        // this should be enough time for the authentication to complete
        // localhost authentication within the same process, therefore we should receive 2 success messages
        int i = 0;
        while (i < 50 && h.getReceivedSecrets() != 2 && h.getReceivedFailures() == 0)
        {
            Thread.sleep(100);
            i++;
        }
        Assert.assertEquals(0, h.getReceivedFailures());
        Assert.assertEquals(2*HostProtocolHandler.AuthenticationStages, h.getReceivedProgress());
        Assert.assertEquals(2, h.getReceivedStarted());

        Assert.assertEquals(2, h.getReceivedSecrets());
        Assert.assertTrue(h.areSessionKeysEqual());
        Assert.assertTrue(h.isOneOObMsgEmpty());
        Assert.assertFalse(h.areOObMsgsEmpty());

        h.shutdownSocketsCleanly();
    }

    public void testCompleteAuthentication_LongPreAuthenticationMode_Client() throws UnknownHostException, IOException, InterruptedException, InternalApplicationException, KeyAgreementProtocolException
    {
        EventHelper h = new EventHelper();
        // need to listen for both the server and the client authentication events
        server.addAuthenticationProgressHandler(h);
		SimpleKeyAgreement clientKa = new SimpleKeyAgreement(useJSSEClient, true);
		// this is a bit of catch here: getting the commitment is not easy via public methods from HostProtocolHandler
		// just do it "manually"
		server.setPreAuthenticationMessageFromClient(HostProtocolHandler.commitment(clientKa.getPublicKey(), useJSSEClient));
        client = new Socket("127.0.0.1", PORT);
        HostProtocolHandler.startAuthenticationWith(new RemoteTCPConnection(client), h, 
        		clientKa, null, null,
        		10000, false, "", useJSSEClient);
        // this should be enough time for the authentication to complete
        // localhost authentication within the same process, therefore we should receive 2 success messages
        int i = 0;
        while (i < 50 && h.getReceivedSecrets() != 2 && h.getReceivedFailures() == 0)
        {
            Thread.sleep(100);
            i++;
        }
        Assert.assertEquals(0, h.getReceivedFailures());
        Assert.assertEquals(2*HostProtocolHandler.AuthenticationStages, h.getReceivedProgress());
        Assert.assertEquals(2, h.getReceivedStarted());

        Assert.assertEquals(2, h.getReceivedSecrets());
        Assert.assertTrue(h.areSessionKeysEqual());
        Assert.assertTrue(h.isOneOObMsgEmpty());
        Assert.assertFalse(h.areOObMsgsEmpty());

        h.shutdownSocketsCleanly();
    }

    public void testCompleteAuthentication_LongPreAuthenticationMode_Mutual() throws UnknownHostException, IOException, InterruptedException, InternalApplicationException, KeyAgreementProtocolException
    {
        EventHelper h = new EventHelper();
        // need to listen for both the server and the client authentication events
        server.addAuthenticationProgressHandler(h);
		SimpleKeyAgreement serverKa = new SimpleKeyAgreement(useJSSEServer, true);
		server.setPermanentKeyAgreementInstance(serverKa);
        byte[] serverPreAuthentication = server.getPermanentPreAuthenticationMessage();
		SimpleKeyAgreement clientKa = new SimpleKeyAgreement(useJSSEClient, true);
		// this is a bit of catch here: getting the commitment is not easy via public methods from HostProtocolHandler
		// just do it "manually"
		server.setPreAuthenticationMessageFromClient(HostProtocolHandler.commitment(clientKa.getPublicKey(), useJSSEClient));
        client = new Socket("127.0.0.1", PORT);
        HostProtocolHandler.startAuthenticationWith(new RemoteTCPConnection(client), h, 
        		clientKa, null, serverPreAuthentication,
        		10000, false, "", useJSSEClient);
        // this should be enough time for the authentication to complete
        // localhost authentication within the same process, therefore we should receive 2 success messages
        int i = 0;
        while (i < 50 && h.getReceivedSecrets() != 2 && h.getReceivedFailures() == 0)
        {
            Thread.sleep(100);
            i++;
        }
        Assert.assertEquals(0, h.getReceivedFailures());
        Assert.assertEquals(2*HostProtocolHandler.AuthenticationStages, h.getReceivedProgress());
        Assert.assertEquals(2, h.getReceivedStarted());

        Assert.assertEquals(2, h.getReceivedSecrets());
        Assert.assertTrue(h.areSessionKeysEqual());
        Assert.assertTrue(h.areOObMsgsEmpty());

        h.shutdownSocketsCleanly();
    }

    public void testCompleteAuthentication_LongPreAuthenticationMode_ServerPermanentTwoClients() throws UnknownHostException, IOException, InterruptedException, InternalApplicationException {
    	helperCompleteAuthentication_LongPreAuthenticationMode_ServerPermanentTwoClients(true);
    }

    public void testCompleteAuthentication_ServerTwoClients() throws UnknownHostException, IOException, InterruptedException, InternalApplicationException {
    	helperCompleteAuthentication_LongPreAuthenticationMode_ServerPermanentTwoClients(false);
    }

    private void helperCompleteAuthentication_LongPreAuthenticationMode_ServerPermanentTwoClients(boolean permanentKeyPair) throws UnknownHostException, IOException, InterruptedException, InternalApplicationException
    {
        EventHelper h = new EventHelper();
        // need to listen for both the server and the client authentication events
        server.addAuthenticationProgressHandler(h);
		SimpleKeyAgreement serverKa = new SimpleKeyAgreement(useJSSEServer, permanentKeyPair);
		if (permanentKeyPair)
			server.setPermanentKeyAgreementInstance(serverKa);
        byte[] serverPreAuthentication = server.getPermanentPreAuthenticationMessage();
        
        // client 1 with first protocol run
        Socket socket1 = new Socket("127.0.0.1", PORT);
        HostProtocolHandler.startAuthenticationWith(new RemoteTCPConnection(socket1), h, 
        		null, null, serverPreAuthentication,
        		10000, false, "", useJSSEClient);
        // this should be enough time for the authentication to complete
        // localhost authentication within the same process, therefore we should receive 2 success messages
        int i = 0;
        while (i < 50 && h.getReceivedSecrets() != 2 && h.getReceivedFailures() == 0)
        {
            Thread.sleep(100);
            i++;
        }
        Assert.assertEquals(0, h.getReceivedFailures());
        Assert.assertEquals(2*HostProtocolHandler.AuthenticationStages, h.getReceivedProgress());
        Assert.assertEquals(2, h.getReceivedStarted());

        Assert.assertEquals(2, h.getReceivedSecrets());
        Assert.assertTrue(h.areSessionKeysEqual());
		if (permanentKeyPair)
			Assert.assertTrue(h.isOneOObMsgEmpty());
		else
			Assert.assertFalse(h.isOneOObMsgEmpty());
		Assert.assertFalse(h.areOObMsgsEmpty());

        h.reset();
        
        // client 2 with second protocol run with the SAME server pre-authentication commitment
        Socket socket2 = new Socket("127.0.0.1", PORT);
        HostProtocolHandler.startAuthenticationWith(new RemoteTCPConnection(socket2), h, 
        		null, null, serverPreAuthentication,
        		10000, false, "", useJSSEClient);
        // this should be enough time for the authentication to complete
        // localhost authentication within the same process, therefore we should receive 2 success messages
        i = 0;
        while (i < 50 && h.getReceivedSecrets() != 2 && h.getReceivedFailures() == 0)
        {
            Thread.sleep(100);
            i++;
        }
        Assert.assertEquals(0, h.getReceivedFailures());
        Assert.assertEquals(2*HostProtocolHandler.AuthenticationStages, h.getReceivedProgress());
        Assert.assertEquals(2, h.getReceivedStarted());

        Assert.assertEquals(2, h.getReceivedSecrets());
        Assert.assertTrue(h.areSessionKeysEqual());
		if (permanentKeyPair)
			Assert.assertTrue(h.isOneOObMsgEmpty());
		else
			Assert.assertFalse(h.isOneOObMsgEmpty());
        Assert.assertFalse(h.areOObMsgsEmpty());

        h.shutdownSocketsCleanly();
    }
    
    public void testCompleteAuthenticationWithParam() throws UnknownHostException, IOException, InterruptedException
    {
        EventHelper h = new EventHelper();
        // need to listen for both the server and the client authentication events
        server.addAuthenticationProgressHandler(h);
        client = new Socket("127.0.0.1", PORT);
        HostProtocolHandler.startAuthenticationWith(new RemoteTCPConnection(client), h, 10000, false, "TEST_PARAMETER", useJSSEClient);
        // this should be enough time for the authentication to complete
        // localhost authentication within the same process, therefore we should receive 2 success messages
        int i = 0;
        while (i < 50 && h.getReceivedSecrets() != 2 && h.getReceivedFailures() == 0)
        {
            Thread.sleep(100);
            i++;
        }
        Assert.assertEquals(0, h.getReceivedFailures());
        Assert.assertEquals(10, h.getReceivedProgress());
        Assert.assertEquals(2, h.getReceivedStarted());

        Assert.assertEquals(2, h.getReceivedSecrets());
        Assert.assertTrue(h.areSharedSecretsEqual());
        
        Assert.assertTrue(h.areOptionalParametersSet());
        Assert.assertTrue(h.areOptionalParametersEqual());
    
        h.shutdownSocketsCleanly();
    }

    public void testCompleteAuthenticationWithParamAndOpenSockets() throws UnknownHostException, IOException, InterruptedException
    {
        EventHelper h = new EventHelper();
        // need to listen for both the server and the client authentication events
        server.addAuthenticationProgressHandler(h);
        client = new Socket("127.0.0.1", PORT);
        HostProtocolHandler.startAuthenticationWith(new RemoteTCPConnection(client), h, 10000, true, "TEST_PARAMETER", useJSSEClient);
        // this should be enough time for the authentication to complete
        // localhost authentication within the same process, therefore we should receive 2 success messages
        int i = 0;
        while (i < 50 && h.getReceivedSecrets() != 2 && h.getReceivedFailures() == 0)
        {
            Thread.sleep(100);
            i++;
        }
        Assert.assertEquals(0, h.getReceivedFailures());
        Assert.assertEquals(2*HostProtocolHandler.AuthenticationStages, h.getReceivedProgress());
        Assert.assertEquals(2, h.getReceivedStarted());

        Assert.assertEquals(2, h.getReceivedSecrets());
        Assert.assertTrue(h.areSharedSecretsEqual());
        
        Assert.assertTrue(h.areOptionalParametersSet());
        Assert.assertTrue(h.areOptionalParametersEqual());
        
        Assert.assertTrue(h.areSocketsConnectedToEachOther());

        h.shutdownSocketsCleanly();
    }
  
    // TODO: test Hollywood style with all 5 different OOB check modes
    
    private class EventHelper implements AuthenticationProgressHandler
    {
        private int receivedSecrets = 0, receivedFailures = 0, receivedProgress = 0, receivedStarted = 0;
        private byte[][] sharedSessionKeys = new byte[2][], sharedOObMsgs = new byte[2][];
        private String[] optionalParameters = new String[2];
        private Socket[] sockets = new Socket[2];

        public void reset() {
        	receivedSecrets = 0; receivedFailures = 0; receivedProgress = 0; receivedStarted = 0;
        	receivedSecrets = 0; receivedFailures = 0; receivedProgress = 0; receivedStarted = 0;
        	optionalParameters = new String[2];
        	sockets = new Socket[2];
        }
        
        public void AuthenticationSuccess(Object sender, Object remote, Object result)
        {
            synchronized (this)
            {
            	int r = receivedSecrets++;
            	Object[] res = (Object[]) result;
                sharedSessionKeys[r] = (byte[]) res[0];
                sharedOObMsgs[r] = (byte[]) res[1];
                optionalParameters[r] = (String) res[2];
                if (res.length > 3)
                	sockets[r] = ((RemoteTCPConnection) res[3]).getSocketReference();
            }
        }

        public void AuthenticationFailure(Object sender, Object remote, Exception e, String msg)
        {
            synchronized (this)
            {
                receivedFailures++;
            }
        }

        public void AuthenticationProgress(Object sender, Object remote, int cur, int max, String msg)
        {
            synchronized (this)
            {
                receivedProgress++;
            }
        }

        public boolean AuthenticationStarted(Object sender, Object remote)
        {
            synchronized (this)
            {
                receivedStarted++;
            }
            return true;
        }

        int getReceivedSecrets()
        {
                return receivedSecrets;
        }

        int getReceivedFailures()
        {
                return receivedFailures;
        }

        int getReceivedProgress()
        {
                return receivedProgress;
        }

        int getReceivedStarted()
        {
                return receivedStarted;
        }
        
        boolean areSessionKeysEqual() {
            if (receivedSecrets != 2)
                return false;
            else
                return SimpleKeyAgreementTest.compareByteArray(sharedSessionKeys[0], sharedSessionKeys[1]);
        }
        
        boolean isOneOObMsgEmpty() {
            if (receivedSecrets != 2)
                return false;
            else
                return sharedOObMsgs[0] == null || sharedOObMsgs[1] == null;
        }
        
        boolean areOObMsgsEmpty() {
            if (receivedSecrets != 2)
                return false;
            else
                return sharedOObMsgs[0] == null && sharedOObMsgs[1] == null;
        }

        boolean areSharedSecretsEqual()
        {
            if (receivedSecrets != 2)
                return false;
            else
                return SimpleKeyAgreementTest.compareByteArray(sharedSessionKeys[0], sharedSessionKeys[1]) &&
                	SimpleKeyAgreementTest.compareByteArray(sharedOObMsgs[0], sharedOObMsgs[1]);
        }
        
        boolean areOptionalParametersSet() 
        {
        	return receivedSecrets == 2 && 
        		optionalParameters[0] != null && optionalParameters[1] != null;
        }
        
        boolean areOptionalParametersEqual()
        {
            if (receivedSecrets != 2)
                return false;
            else
            	return optionalParameters[0].equals(optionalParameters[1]);
        }
        
        boolean areSocketsConnectedToEachOther()
        {
        	/*System.out.println("local 0: " + sockets[0].getLocalAddress() + " " + sockets[0].getLocalPort());
        	System.out.println("local 1: " + sockets[1].getLocalAddress() + " " + sockets[1].getLocalPort());
        	System.out.println("remote 0: " + sockets[0].getInetAddress() + " " + sockets[0].getPort());
        	System.out.println("remote 1: " + sockets[1].getInetAddress() + " " + sockets[1].getPort());*/
        	// hmm, that's bad - can't compare the addresses because getLocalAddress() always returns 0.0.0.0/0.0.0.0
        	return receivedSecrets == 2 &&
        		sockets[0] != null && sockets[1] != null &&
        		sockets[0].isConnected() && sockets[1].isConnected() &&
        		/*sockets[0].getLocalAddress().equals(sockets[1].getInetAddress()) &&
        		sockets[1].getLocalAddress().equals(sockets[0].getInetAddress()) && */
        		sockets[0].getLocalPort() == sockets[1].getPort() &&
        		sockets[1].getLocalPort() == sockets[0].getPort();
        }
        
        void shutdownSocketsCleanly() throws IOException 
        {
        	for (int i=0; i<sockets.length; i++) {
        		if (sockets[i] != null && sockets[i].isConnected())
        		{
        			if (! sockets[i].isInputShutdown() && !sockets[i].isClosed())
        				sockets[i].shutdownInput();
        			if (! sockets[i].isOutputShutdown() && !sockets[i].isClosed())
        				sockets[i].shutdownOutput();
        			sockets[i].close();
        		}
        	}
        }
    }
}
