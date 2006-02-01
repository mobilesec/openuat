package org.eu.mayrhofer.authentication.test;

import java.net.*;
import java.io.*;

import org.eu.mayrhofer.authentication.*;
import org.eu.mayrhofer.authentication.exceptions.*;
import junit.framework.*;

public class HostProtocolHandlerTest extends TestCase {
	public HostProtocolHandlerTest(String s) {
		super(s);
	}
	
    public static final int PORT = 23457;

    private boolean socketWasAlreadyOpen = false;

    private HostServerSocket server;
    private Socket client;
    private BufferedReader sr;
    private PrintWriter sw;
    
    protected boolean useJSSEServer = true;
    protected boolean useJSSEClient = true;

    public void setUp() throws InterruptedException, IOException
    {
        // This is a rather dirty hack: whenever the socket was open earlier and has just been closed
        // by the TearDown method before this SetUp method was called, it seems that the OS does need
        // some time to clean up the socket and free it for another listener. It's just wrong that
        // TcpListener.Stop() doesn't take care of that, i.e. that it is not really a synchronous
        // operation!
        if (socketWasAlreadyOpen)
            Thread.sleep(100);

        server = new HostServerSocket(PORT, true, useJSSEServer);
        server.startListening();
        socketWasAlreadyOpen = true;
    }

    public void tearDown() throws InterruptedException, IOException, InternalApplicationException
    {
        if (client != null) {
            client.shutdownInput();
            client.shutdownOutput();
            client.close();
        }
        client = null;
        if (server != null)
            server.stopListening();
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

    public void testCompleteAuthentication() throws UnknownHostException, IOException, InternalApplicationException, InterruptedException
    {
        EventHelper h = new EventHelper();
        // need to listen for both the server and the client authentication events
        server.addAuthenticationProgressHandler(h);
        HostProtocolHandler.startAuthenticationWith("127.0.0.1", PORT, h, false, "", useJSSEClient);
        // this should be enough time for the authentication to complete
        // localhost authentication within the same process, therefore we should receive 2 success messages
        int i = 0;
        while (i < 50 && h.getReceivedSecrets() != 2 && h.getReceivedFailures() == 0)
        {
            Thread.sleep(100);
            i++;
        }
        Assert.assertEquals(0, h.getReceivedFailures());
        Assert.assertEquals(8, h.getReceivedProgress());

        Assert.assertEquals(2, h.getReceivedSecrets());
        Assert.assertTrue(h.areSharedSecretsEqual());

        h.shutdownSocketsCleanly();
    }

    public void testCompleteAuthenticationWithParam() throws UnknownHostException, IOException, InternalApplicationException, InterruptedException
    {
        EventHelper h = new EventHelper();
        // need to listen for both the server and the client authentication events
        server.addAuthenticationProgressHandler(h);
        HostProtocolHandler.startAuthenticationWith("127.0.0.1", PORT, h, false, "TEST_PARAMETER", useJSSEClient);
        // this should be enough time for the authentication to complete
        // localhost authentication within the same process, therefore we should receive 2 success messages
        int i = 0;
        while (i < 50 && h.getReceivedSecrets() != 2 && h.getReceivedFailures() == 0)
        {
            Thread.sleep(100);
            i++;
        }
        Assert.assertEquals(0, h.getReceivedFailures());
        Assert.assertEquals(8, h.getReceivedProgress());

        Assert.assertEquals(2, h.getReceivedSecrets());
        Assert.assertTrue(h.areSharedSecretsEqual());
        
        Assert.assertTrue(h.areOptionalParametersSet());
        Assert.assertTrue(h.areOptionalParametersEqual());
    
        h.shutdownSocketsCleanly();
    }

    public void testCompleteAuthenticationWithParamAndOpenSockets() throws UnknownHostException, IOException, InternalApplicationException, InterruptedException
    {
        EventHelper h = new EventHelper();
        // need to listen for both the server and the client authentication events
        server.addAuthenticationProgressHandler(h);
        HostProtocolHandler.startAuthenticationWith("127.0.0.1", PORT, h, true, "TEST_PARAMETER", useJSSEClient);
        // this should be enough time for the authentication to complete
        // localhost authentication within the same process, therefore we should receive 2 success messages
        int i = 0;
        while (i < 50 && h.getReceivedSecrets() != 2 && h.getReceivedFailures() == 0)
        {
            Thread.sleep(100);
            i++;
        }
        Assert.assertEquals(0, h.getReceivedFailures());
        Assert.assertEquals(8, h.getReceivedProgress());

        Assert.assertEquals(2, h.getReceivedSecrets());
        Assert.assertTrue(h.areSharedSecretsEqual());
        
        Assert.assertTrue(h.areOptionalParametersSet());
        Assert.assertTrue(h.areOptionalParametersEqual());
        
        Assert.assertTrue(h.areSocketsConnectedToEachOther());

        h.shutdownSocketsCleanly();
    }
    
    private class EventHelper implements AuthenticationProgressHandler
    {
        private int receivedSecrets = 0, receivedFailures = 0, receivedProgress = 0;
        private byte[][] sharedSessionKeys = new byte[2][], sharedAuthenticationKeys = new byte[2][];
        private String[] optionalParameters = new String[2];
        private Socket[] sockets = new Socket[2];

        public void AuthenticationSuccess(Object sender, Object remote, Object result)
        {
            synchronized (this)
            {
            	int r = receivedSecrets++;
            	Object[] res = (Object[]) result;
                sharedSessionKeys[r] = (byte[]) res[0];
                sharedAuthenticationKeys[r] = (byte[]) res[1];
                optionalParameters[r] = (String) res[2];
                if (res.length > 3)
                	sockets[r] = (Socket) res[3];
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

        boolean areSharedSecretsEqual()
        {
            if (receivedSecrets != 2)
                return false;
            else
                return SimpleKeyAgreementTest.compareByteArray(sharedSessionKeys[0], sharedSessionKeys[1]) &&
                	SimpleKeyAgreementTest.compareByteArray(sharedAuthenticationKeys[0], sharedAuthenticationKeys[1]);
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
