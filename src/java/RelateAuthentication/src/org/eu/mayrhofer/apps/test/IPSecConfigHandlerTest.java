package org.eu.mayrhofer.apps.test;

import java.io.*;

import org.eu.mayrhofer.apps.IPSecConfigHandler;

import junit.framework.*;

public class IPSecConfigHandlerTest extends TestCase {
	private IPSecConfigHandler handler = null;
	private String tempFile;

	public IPSecConfigHandlerTest(String s) {
		super(s);
	}

	public void setUp() {
		handler = new IPSecConfigHandler();
	}

	public void tearDown() {
		// nothing to do here
	}

	public void testGatewayProperty(){
		Assert.assertNull("Should be initialized with null", handler.getGateway());
		String test1 = "test 1";
		handler.setGateway(test1);
		Assert.assertEquals("Property not working properly", test1, handler.getGateway());
		String test2 = "test 2";
		handler.setGateway(test2);
		Assert.assertEquals("Property not working properly", test2, handler.getGateway());
	}

	public void testRemoteNetworkProperty(){
		Assert.assertNull("Should be initialized with null", handler.getRemoteNetwork());
		String test1 = "test 1";
		handler.setRemoteNetwork(test1);
		Assert.assertEquals("Property not working properly", test1, handler.getRemoteNetwork());
		String test2 = "test 2";
		handler.setRemoteNetwork(test2);
		Assert.assertEquals("Property not working properly", test2, handler.getRemoteNetwork());
	}
	
	public void testEnforceGatewaySet() {
		Assert.assertNull("Should be initialized with null", handler.getGateway());
		Assert.assertFalse("Should not work without a gateway set", handler.writeConfig(null));
	}
	
	public void testEnforceNoOverwriteGateway() {
		handler.setGateway("test");
		Assert.assertFalse("Should not work with a gateway set", handler.parseConfig(null));
	}

	public void testEnforceNoOverwriteRemoteNetwork() {
		handler.setRemoteNetwork("test");
		Assert.assertFalse("Should not work with a gateway set", handler.parseConfig(null));
	}
	
	public void testWriteAndParseGateway() throws IOException {
		String gate = "test gateway";
		handler.setGateway(gate);
		File temp = File.createTempFile("configFileTest", ".xml");
		temp.deleteOnExit();
		Assert.assertTrue("Could not write to temporary test file", temp.canWrite());
		Assert.assertTrue("Could not write config", handler.writeConfig(new FileWriter(temp)));
		
		handler = new IPSecConfigHandler();
		Assert.assertTrue("Could not read config", handler.parseConfig(new FileReader(temp)));
		Assert.assertEquals("Gateway read is different from gateway written", handler.getGateway(), gate);
	}

	public void testWriteAndParseGatewayAndNetwork() throws IOException {
		String gate = "test gateway 2";
		String net = "test network";
		handler.setGateway(gate);
		handler.setRemoteNetwork(net);
		File temp = File.createTempFile("configFileTest", ".xml");
		temp.deleteOnExit();
		Assert.assertTrue("Could not write to temporary test file", temp.canWrite());
		Assert.assertTrue("Could not write config", handler.writeConfig(new FileWriter(temp)));
		
		handler = new IPSecConfigHandler();
		Assert.assertTrue("Could not read config", handler.parseConfig(new FileReader(temp)));
		Assert.assertEquals("Gateway read is different from gateway written", handler.getGateway(), gate);
		Assert.assertEquals("Remote network read is different from remote network written", handler.getRemoteNetwork(), net);
	}
}
