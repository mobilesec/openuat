package uk.ac.lancs.relate;

import junit.framework.*;

public class TestSerialConnector extends TestCase {
	private final static String Dongle1 = "/dev/ttyUSB0";
	private final static String Dongle2 = "/dev/ttyUSB1";
	
	private SerialConnector conn1;
	private MessageQueue queue;
	
	public TestSerialConnector(String s) {
		super(s);
	}

	public void setUp() {
		try {
			conn1 = SerialConnector.getSerialConnector(Dongle1);
			Assert.assertTrue("SerialConnector is not operational after successful connection to dongle", conn1.isOperational());
			queue = new MessageQueue();
			conn1.registerEventQueue(queue);
		} catch (DongleException e) {
			conn1 = null;
			String stackTrace = "";
			for (int i=0; i<e.getStackTrace().length; i++)
				stackTrace += e.getStackTrace()[i].toString() + "\n";
			Assert.fail("Could not connect to dongle at " + Dongle1 + ", skipping unit tests for SerialConnector! Reason was: "
					+ e + "\n" + stackTrace);
		}
	}

	public void tearDown() throws DongleException, InterruptedException {
		if (conn1 != null) {
			conn1.stop();
			conn1.unregisterEventQueue(queue);
			conn1.destroy();
			conn1 = null;
			// and give it time to shut down
			Thread.sleep(2000);
		}
		queue = null;
	}
	
	private RelateEvent waitForMessage(/*Class messageType, */int messageType, long timeout) {
		long startTime = System.currentTimeMillis();
		
		while (System.currentTimeMillis() <= startTime + timeout) {
			while (queue.isEmpty() && System.currentTimeMillis() <= startTime + timeout)
				queue.waitForMessage(500);
			RelateEvent e = (RelateEvent) queue.getMessage();
			if (e != null && e.getType() == messageType)
				return e;
		}
		return null;
	}
	
	public void testRelateId() {
		Assert.assertTrue("Reported relate ID is invalid", conn1.getLocalRelateId() > 0 && conn1.getLocalRelateId() <= 255);
	}
	
	public void testMonitoringOnOff() {
		conn1.start();
		conn1.stop();
	}
	
	public void testSendHostInfo() {
		byte[] hostinfo = new byte[51];
		Assert.assertTrue(conn1.getLocalRelateId() > 0 && conn1.getLocalRelateId() <= 255);
		hostinfo[0] = (byte) conn1.getLocalRelateId();
		Assert.assertTrue("Could not send host info message to dongle", conn1.setHostInfo(hostinfo));
	}
	
	public void testReceiveMeasurement() {
		conn1.start();
		Assert.assertTrue("Did not receive measurements. Is any other dongle in sight?", waitForMessage(RelateEvent.NEW_MEASUREMENT, 3000) != null);
		conn1.stop();
	}
	
	public void testDiagnosticMode() {
		conn1.switchDiagnosticMode(true);
		conn1.start();
		Assert.assertTrue("Did not receive error/diagnostic code message from dongle while in diagnostic mode", 
				waitForMessage(RelateEvent.ERROR_CODE, 5000) != null);
		// we will only get sensorinfo events when there are measurements, i.e. if there is another dongle in sight....
/*		if (waitForMessage(RelateEvent.NEW_MEASUREMENT, 5000) != null)
			Assert.assertTrue("Did not receive sensor info message from dongle while in diagnostic mode",
					waitForMessage(RelateEvent. 3000) != null);*/
		conn1.switchDiagnosticMode(false);
		Assert.assertTrue("Received error/diagnostic code message from dongle while not in diagnostic mode", 
				waitForMessage(RelateEvent.ERROR_CODE, 5000) == null);
/*		if (waitForMessage(RelateEvent.NEW_MEASUREMENT, 5000) != null)
		Assert.assertTrue("Did not receive sensor info message from dongle while in diagnostic mode",
				waitForMessage(RelateEvent. 3000) == null);*/
		conn1.stop();
	}

	public void testStartAuthentication() {
		conn1.start();
		conn1.startAuthenticationWith(0, new byte[128], new byte[128], 2, 3);
		conn1.switchDiagnosticMode(false);
		conn1.stop();
	}
}
