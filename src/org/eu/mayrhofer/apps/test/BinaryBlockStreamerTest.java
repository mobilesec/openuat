package org.eu.mayrhofer.apps.test;

import java.io.*;

import org.eu.mayrhofer.apps.BinaryBlockStreamer;

import junit.framework.*;

public class BinaryBlockStreamerTest extends TestCase {
	public BinaryBlockStreamerTest(String s) {
		super(s);
	}

	public void setUp() {
		// nothing to do here, no sensible defaults
	}

	public void tearDown() {
		// nothing to do here
	}
	
	public void testEitherInputOrOutputCheck() {
		try {
			BinaryBlockStreamer streamer = new BinaryBlockStreamer(null, null);
		} catch (IllegalArgumentException e) {
			// we expect this
			return;
		}
		Assert.fail("Constructor accepted null input and output");
	}
	
	public void testInputNotNullForReceiveCheck() {
		try {
			BinaryBlockStreamer streamer = new BinaryBlockStreamer(null, new ByteArrayOutputStream());
			streamer.receiveBinaryBlock(new StringBuffer(), new ByteArrayOutputStream());
		} catch (IOException e) {
			// we expect this
			return;
		}
		Assert.fail("Constructor accepted null input and output");
	}

	public void testOutputNotNullForSendCheck() {
		try {
			BinaryBlockStreamer streamer = new BinaryBlockStreamer(new ByteArrayInputStream(new byte[] {0}), null);
			streamer.sendBinaryBlock("test", new ByteArrayInputStream(new byte[] {0}), 1);
		} catch (IOException e) {
			// we expect this
			return;
		}
		Assert.fail("Constructor accepted null input and output");
	}
	
	private boolean compareByteArrays(byte[] b1, byte[] b2) {
		if (b1.length != b2.length)
			return false;
		for (int i=0; i<b1.length; i++)
			if (b1[i] != b2[i])
				return false;
		return true;
	}
	
	public void testSendAndReceive() throws IOException {
		// need to BinaryBlockStreamers, coupled via a pipe (it's the easiest)
		PipedOutputStream writePipe = new PipedOutputStream();
		PipedInputStream readPipe = new PipedInputStream(writePipe);
		BinaryBlockStreamer src = new BinaryBlockStreamer(null, writePipe);
		BinaryBlockStreamer dst = new BinaryBlockStreamer(readPipe, null);
		
		// two test cases: send a binary array and a string
		// this tests that the streams are left open and that they are indexed properly
		// this includes CR and LF characters and a null character - so it should test for byte safety
		byte[] test1 = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13};
		String test1name = "bytetest";
		String test2 = "my string test block with\nsome\nnewlines\nand\n\t\ttabs\nmixed in.";
		String test2name = "string test name with blanks";
		src.sendBinaryBlock(test1name, new ByteArrayInputStream(test1), test1.length);
		StringBuffer recvName = new StringBuffer();
		ByteArrayOutputStream test1rcv = new ByteArrayOutputStream();
		Assert.assertEquals("Did not receive the same amount of bytes as have been sent", test1.length, dst.receiveBinaryBlock(recvName, test1rcv));
		Assert.assertEquals("The received block name does not match the sent one", test1name, recvName.toString());
		Assert.assertTrue("Received binary array does not match the sent one", compareByteArrays(test1, test1rcv.toByteArray()));
		src.sendBinaryBlock(test2name, new ByteArrayInputStream(test2.getBytes()), test2.length());
		recvName = new StringBuffer();
		ByteArrayOutputStream test2rcv = new ByteArrayOutputStream();
		Assert.assertEquals("Did not receive the same amount of bytes as have been sent", test2.length(), dst.receiveBinaryBlock(recvName, test2rcv));
		Assert.assertEquals("The received block name does not match the sent one", test2name, recvName.toString());
		Assert.assertEquals("Received string does not match the sent one", test2, test2rcv.toString());
	}
}
