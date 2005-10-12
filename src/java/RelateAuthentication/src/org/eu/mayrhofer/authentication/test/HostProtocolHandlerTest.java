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

    public void setUp() throws InterruptedException, IOException
    {
        // This is a rather dirty hack: whenever the socket was open earlier and has just been closed
        // by the TearDown method before this SetUp method was called, it seems that the OS does need
        // some time to clean up the socket and free it for another listener. It's just wrong that
        // TcpListener.Stop() doesn't take care of that, i.e. that it is not really a synchronous
        // operation!
        if (socketWasAlreadyOpen)
            Thread.sleep(100);

        server = new HostServerSocket(PORT);
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

        HostProtocolHandler.StartAuthenticationWith("127.0.0.1", PORT);
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
        //Assert.assertTrue(h.AreSharedSecretsEqual());
    }

    private class EventHelper implements AuthenticationProgressHandler
    {
        private int receivedSecrets = 0, receivedFailures = 0, receivedProgress = 0;
        private byte[][] sharedSecrets = new byte[2][];

        EventHelper() 
        {
            HostProtocolHandler.addAuthenticationProgressHandler(this);
        }

        public void AuthenticationSuccess(InetAddress remote, byte[] sharedKey)
        {
            synchronized (this)
            {
                sharedSecrets[receivedSecrets++] = sharedKey;
            }
        }

        public void AuthenticationFailure(InetAddress remote, Exception e, String msg)
        {
            synchronized (this)
            {
                receivedFailures++;
            }
        }

        public void AuthenticationProgress(InetAddress remote, int cur, int max, String msg)
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
                return SimpleKeyAgreementTest.compareByteArray(sharedSecrets[0], sharedSecrets[1]);
        }
    }
}
