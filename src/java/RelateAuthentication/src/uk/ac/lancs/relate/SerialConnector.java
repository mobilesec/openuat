package uk.ac.lancs.relate;

/**
 ** Based on code by Martin Strohbach (WeightTableSerialConnector)
 **/

import javax.comm.*;

import java.io.*;
import java.util.*;

import org.apache.log4j.Logger;

/**
 * This is a helper class for SerialConnector and encapsulates the low-level communication with the dongle. It offers a 
 * simple interface to send messages to the dongle and receive its output in normal (monitoring) mode. All communication
 * with the serial port is handled through this helper. It also manages the javax.comm API. 
 * 
 * @author Rene Mayrhofer, based on code by Chris Kray and Henoc Agbota 
 */
class SerialCommunicationHelper {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(SerialCommunicationHelper.class);
	/** This is a special log4j logger used for logging only statistics. It is separate from the main logger
	 * so that it's possible to turn statistics on an off independently.
	 */
	private static Logger statisticsLogger = Logger.getLogger("statistics.serialhelper");
	
	/** List of available ports returned by the javax.comm API.  */
	private String[] portNames = null;

	/** Holds the CommPortIdentifier objects associated with the portNames. */
	private Vector availablePorts = null;

	/**
	 * The port identifier used to reference the serial port used by the dongle.
	 * From this object, the serialPort object is reconstructed every time after
	 * changing the baud rate.
	 */
	private CommPortIdentifier portId = null;

	/** The main serial port object. It gets reconstructed from the portId by prepareMode whenever the current mode is changed. */
	private SerialPort serialPort = null;
	
	/**
	 * The initialization of fis and fos depends on the state variable
	 * interacting. In interacting mode (i.e. interacting = true), both fos and
	 * fis are initialized. I In receiving only mode (i.e. interacting = false),
	 * only fis is initialized and fos is null
	 */
	private InputStream fis;

	/** @see #fis */
	private OutputStream fos;

	/**
	 * Specifies the current mode: false for receiving only mode (measurements,
	 * 57600 baud), true for command/interacting mode (19200 baud).
	 */
	private boolean interacting = false;
	
	/** Stored the local dongle id that has been reported last. */
	private int myRelateId = -1;
	
	/** Stores the last time when we interrupted the dongle. getDongleAttention sets this variable
	 * and uses it to sleep when this method is called in quick succession. The dongle doesn't like to
	 * be interrupted twice in a row and on the whole, it's even quicker when we wait for a bit before 
	 * trying to interrupt it again. 
	 * 
	 * This variable is only modified and read by getDongleAttention.
	 * 
	 * @see #getDongleAttention
	 * @see #MAGIC_5
	 */
	private long lastTimeDongleInterrupted = 0;

	/**
	 * acknowledgement bytes sent by dongle; also served as prefixed bytes to
	 * the dongle's relate id
	 */
	private final static byte[] ACK = { (byte) 'A', (byte) 'C', (byte) 'K' };

	/** MAGIC VALUE NUMBER 1: Send 10 garbage bytes, seems to work well (at least > 50ms garbage at 19200 baud). 
	 * It's magic because there's no real reason for that specific number other than trial&error (and a systematic brute-force
	 * search of possible parameter combinations).
	 * Warning: don't set this too high. The dongle currently has a statically sized receive buffer of 100 bytes, which will
	 * take all the garbage <b>and</b> the commands sent after the garbage. So this number must be set to at most 100 minus 
	 * the size of the largest message being sent from the host to the dongle.
	 *  
	 * Used in getDongleAttention.
	 * @see #getDongleAttention
	 */
	private final static int MAGIC_1 = 20;

	/** MAGIC VALUE NUMBER 2: Wait for 200 ms for the dongle to realize we sent garbage. also seems to work reasonably well.
	 * It's magic because there's no real reason for that specific number other than trial&error (and a systematic brute-force
	 * search of possible parameter combinations).
	 * Warning: Don't set this too low (i.e. too aggressively trying to get the dongle's attention) or the dongle will take a
	 * <b>long</b> time to wake up again. The suspected reason for this is that some part in the host-USB-USB/serial-PIC chain is
	 * buffering the garbage sent to the dongle and continues sending it even after the dongle responded. That in turn puts
	 * the dongle back into interactive mode without the host listening. Although the parameter search suggests that lower values
	 * (specifically 75) produce lower delays in getting the dongle's attention, they also lead to <b>many</b> more retries and
	 * thus fill up the buffer. The real question is though: Why is the javax.comm API's send method not really synchronous when
	 * it's supposed to be? Which part of the chain buffers without the send method noticing and thus causing it to wait? 
	 *  
	 * Used in getDongleAttention.
	 * @see #getDongleAttention
	 */
	private final static int MAGIC_2 = 200;
	
	/** MAGIC VALUE NUMBER 3: Set the serial port read timeout to 5 times the timeout passed to the receive method. I am really not 
	 * sure why this is necessary at all (the normal timeout should be enough), but elongating that time should not matter too much.
	 * It is only a safeguard against a "dead" dongle that does not want to send anything, anyway.
	 * It's magic because there's no real reason for that specific number other than trial&error.
	 *  
	 * Used in receiveFromDongle.
	 * @see #receiveFromDongle 
	 */ 
	private final static int MAGIC_3 = 5;

	/** MAGIC VALUE NUMBER 4: Wait for 1000 ms by default, if no other receive timeout is specified. This will be overwritten
	 * anyway by the next call to receiveFromDongle (which uses MAGIC_3), so it doesn't matter too much.
	 * It's magic because there's no real reason for that specific number other than trial&error.
	 *
	 * Used in prepareMode.
	 * @see #prepareMode
	 */  
	private final static int MAGIC_4 = 1000;
	
	/** MAGIC VALUE NUMBER 5: Leave at least 5000 ms between two tries to interrupt the dongle. It doesn't like that so we
	 * prevent it.
	 * It's magic because there's no real reason for that specific number other than trial&error.
	 * 
	 * @see #getDongleAttention
	 * @see #lastTimeDongleInterrupted
	 */
	private final static int MAGIC_5 = 5000;
	
	
	/** This constructor only initializes the portNames and availablePorts members by querying the javax.comm API for serial ports. */
	public SerialCommunicationHelper() {
		CommPortIdentifier id;
		Enumeration portList;

		portList = CommPortIdentifier.getPortIdentifiers();
		availablePorts = new Vector();
		/* generate list of (available) ports */
		while ((portList.hasMoreElements()) && (portId == null)) {
			id = (CommPortIdentifier) portList.nextElement();
			logger.info("port " + id + " (" + id.getName() + ") is "
					+ (id.isCurrentlyOwned() ? " not " : "") + " available.");
			if (!(id.isCurrentlyOwned())) {
				availablePorts.addElement(id);
			}
		}
		if (availablePorts.size() > 0) {
			portNames = new String[availablePorts.size()];
			for (int i = 0; i < availablePorts.size(); i++) {
				portNames[i] = ((CommPortIdentifier) availablePorts
						.elementAt(i)).getName();
			}
		} else {
			logger.error("no ports available");
		}
	}

	/** return list of available serial ports */
	public String[] getPorts() {
		return portNames;
	}

	/** Prepares the dongle for either interacting mode (used to send commands to the dongle and get its status) or read-only monitoring mode.
	 * 
	 * @param interacting true for interacting mode, false for monitoring mode
	 */
	private synchronized void prepareMode(boolean interacting) throws IOException, PortInUseException {
		if (this.interacting == interacting) {
			// already in the requested mode, nothing to do
			return;
		}
		else {
			if (fis != null) {
				fis.close();
				fis = null;
			}
			if (fos != null) {
				fos.close();
				fos = null;
			}
			if (serialPort != null)
				serialPort.close();
			
			serialPort = (SerialPort) portId.open("RelatePort", 500);
			try {
				logger.info("Switching serial port baud rate (previously in interactive mode: " + this.interacting + ", now: " + interacting + ")");
				/* according to http://forum.java.sun.com/thread.jspa?threadID=673793&tstart=0, this fixes the params not supported IOException
				 * that can't be caught because it occurs in native code.
				 */
				//Thread.sleep(10);
				System.out.println("horrible hack line 1");
				serialPort.setSerialPortParams(interacting ? 19200 : 57600,
						SerialPort.DATABITS_8,
						SerialPort.STOPBITS_1,
						SerialPort.PARITY_NONE);
				// so that read on the getInputStream does not hang indefinitely but times out
				//Thread.sleep(10);
				System.out.println("horrible hack line 2");
				serialPort.enableReceiveTimeout(MAGIC_4);
				if (!serialPort.isReceiveTimeoutEnabled())
					logger.warn("Warning: serial port driver does not support receive timeouts! It is possible that read operations will block indefinitely.");
				//Thread.sleep(10);
				System.out.println("horrible hack line 3");
				serialPort.setFlowControlMode(SerialPort.FLOWCONTROL_NONE);
			} catch (UnsupportedCommOperationException e) {
				logger.error("UnsupportedCommOperationException: " + e + "\n" + e.getStackTrace());
			}
			if (interacting) 
				fos = serialPort.getOutputStream();
			fis = serialPort.getInputStream();
			this.interacting = interacting;
		}
	}
	
	/** Send raw data to the dongle.
	 * This implicitly switches the dongle to interactive mode.
	 */
	private synchronized void sendToDongle(byte[] data) throws IOException, PortInUseException {
		prepareMode(true);
		try {
			fos.write(data) ;
			fos.flush();
		} catch (IOException ex) {
			logger.error("sending info to dongle failed due to " + ex + "\n" + ex.getStackTrace());
			throw ex;
		}
	}
	
	/**
	 * This method also resceives from the dongle, but skips bytes until they
	 * match an expected start header (e.g. acknowledge).
	 * 
	 * @param bytesToRead
	 *            The number of bytes to read from the dongle.
	 * @param expectedStart
	 *            The expected bytes that the dongle should return. On success,
	 *            the returned data will start with exactly these bytes. This
	 *            parameter can be null to indicate that there is no expectation.
	 *            In this case, any data sent by the dongle will be returned.
	 * @param timeout
	 *            Maximum time in milliseconds to wait for the data.
	 * @return The data received from the dongle or null if unable to receive
	 *         (due to timeout, unable to read, or expectedStart not found
	 *         within timeout).
	 */
	public synchronized byte[] receiveFromDongle(int bytesToRead, byte[] expectedStart, int timeout) {
		byte[] received = new byte[bytesToRead];
		int alreadyRead = 0, readBytes, retries = 0;
		long startTime = System.currentTimeMillis();
		
		// sanity check: the input stream must have been initialized
		if (fis == null) {
			logger.error("Error: trying to read from serial port while it has not been opened yet correctly. This should not happen!");
			return null;
		}
		
		try {
			// set the read timeout
			try {
				serialPort.enableReceiveTimeout(timeout * MAGIC_3);
			} catch (UnsupportedCommOperationException e) {
				// this can be ignored - it won't kill if timeout doesn't work. but it will just miss the protection against a "dead" dongle
				logger.warn("UnsupportedCommOperationException: " + e + "\n" + e.getStackTrace()) ;
			}

			// if we got some expected start bytes, really wait for those to appear (at least until the timeout)
			if (expectedStart != null && expectedStart.length > 0) {
				int recv = fis.read();
				while(recv != expectedStart[0] && recv != -1 && (System.currentTimeMillis() - startTime) < timeout) {
					recv = fis.read();
					retries++;
				}
				if (recv == expectedStart[0]) {
					alreadyRead++;
					received[0] = (byte) recv;
				}
				else if (recv == -1 ){
					logger.debug("Got EOF after receiving " + retries + " bytes");
					return null;
				} else {
					logger.debug("Could not find first expected byte, returning with timeout after receiving " + retries + " bytes");
					// unable to find even our first expected byte, either due to timeout or to read error
					return null;
				}
			}
			do {
				readBytes = fis.read(received, alreadyRead, bytesToRead - alreadyRead);
				if (readBytes > 0)
					alreadyRead += readBytes;
			} while (alreadyRead < bytesToRead && readBytes != -1 && (System.currentTimeMillis() - startTime) < timeout);
			if (alreadyRead != bytesToRead) {
				logger.debug("Didn't get enough bytes from dongle within timeout, wanted " + bytesToRead + " but got " + alreadyRead);
				return null;
			}
			// before returning, check the remaining expected bytes (if there are any)
			if (expectedStart != null) {
				// the first byte must already match, but checking is cheap so do it anyway
				for (int i=0; i<expectedStart.length; i++)
					if (received[i] != expectedStart[i]) {
						logger.warn("Received bytes didn't match: byte " + i + " is " + received[i] + " but expected " + expectedStart[i]);
						String echoBytes = "";
						for (int j=0; j<expectedStart.length; j++)
							echoBytes += Integer.toHexString(received[j]) + " ";
						logger.debug("Echoed bytes were " + echoBytes);
						// error in comparison
						return null;
					}
			}
			// finally: read all bytes that have been requested and compared ok to the expected start
			return received;
		} catch (IOException ex) {
			logger.warn("receiving from dongle failed due to " + ex + "\n" + ex.getStackTrace());
			return null;
		}
	}

	/**
	 * Try to catch the dongle's attention by
	 * repeatedly sending '10101...'
	 * @param timeout The maximum time in ms that this method will try to catch the dongle's attention.
	 * @return The dongle ID as returned by its acknowledge or -1 if the dongle's attention could not be caught.
	 */
	private synchronized int getDongleAttention(long timeout) throws PortInUseException {
		int localId = -1;
		byte[] recv;
		int counter = 0;
		boolean unacknowledged = true ;
		long startTime = 0 ;
		long lastTry = 0;
		
		try {
			startTime =  System.currentTimeMillis();
			// check that we don't interrupt the dongle two quickly again
			if (startTime - lastTimeDongleInterrupted < MAGIC_5) {
				logger.info("Trying to interrupt dongle again in quick succession. Waiting for " + MAGIC_5 + "ms to give the dongle time to recover.");
				statisticsLogger.info("* Waiting for " + (MAGIC_5 - (startTime - lastTimeDongleInterrupted)) + " ms before next dongle interruption");
				try {
					Thread.sleep(MAGIC_5 - (startTime - lastTimeDongleInterrupted));
				} catch (InterruptedException e) {}
				startTime =  System.currentTimeMillis();
			}
			lastTimeDongleInterrupted = startTime;
			
			logger.info("Trying to get dongle's attention now");
			
			// special case here: need to call it manually so that fis.skip will work
			prepareMode(true);
			while (unacknowledged && (System.currentTimeMillis() - startTime) < timeout) {
				counter++ ;
				
				// another safety belt: sometimes it falls back to 57600 if it was set to that earlier!
				forceBaudrateReset();
				
				/*Discard everything currently in the serial input buffer.*/
				fis.skip(fis.available());
				/*Send garbage..*/
				byte[] garbage = new byte[MAGIC_1];
				for (int i=0; i<garbage.length; i++)
					garbage[i] = (byte) 0xAA;
				sendToDongle(garbage);
				lastTry = System.currentTimeMillis();
				recv = receiveFromDongle(ACK.length+1, ACK, MAGIC_2); 
				if (recv != null) {
					unacknowledged = false;
					localId = recv[ACK.length];
					logger.info("time to get dongle's attention: " + (System.currentTimeMillis() - startTime) + "ms");
				}
			}
		} catch (IOException ex) {
			logger.error("Geting dongle's attention failed due to " + ex + "\n" + ex.getStackTrace());
		}
		if (localId != -1) {
			logger.info("Number of trials before getting ACK: " + counter) ;
			logger.info("Time from garbage to ack: " + (System.currentTimeMillis() - lastTry));
			statisticsLogger.info("! " + (System.currentTimeMillis() - startTime) + " " + (System.currentTimeMillis() - lastTry) + 
					" " + counter + " got dongle attention");
		}
		else
			logger.error("Could not get dongle's attention after " + counter + " tries and " + (System.currentTimeMillis()-startTime) + "ms");
		
		return localId ;
	}
	
	/** This public method just interrupts the dongle and stores its reported id so that it can be read
	 * with getLocalRelateId.
	 * @param timeout The maximum time in ms to wait for the dongle's attention.
	 * @see #getLocalRelateId()
	 */
	public boolean interruptDongle(int timeout) throws PortInUseException {
		myRelateId = getDongleAttention(timeout);
		return myRelateId != -1;
	}
	
	/** Sends a complete message to the dongle:
	 * 1. Gets the dongle's attention (by switching to interactive mode and sending garbage)
	 * 2. Waits for the dongle to acknowledge entering the interactive mode and reporting its id (loops to 1 until ack is received).
	 *    The reported local dongle ID is stored in the myRelateId member variable.
	 * 3. Sends the passed message to the dongle, assuming that it should now be listening.
	 * 4. Loop to 2 until the dongle acknowledges the message/command by echoing it (with a prepended number of bytes for the echo).
	 *    In every loop, the msg is sent again to the dongle.
	 * 
	 * @param msg The message/command to send to the dongle.
	 * @param timeout The maximum number of milliseconds allowed to pass.
	 * @see #myRelateId
	 */ 
	public synchronized boolean sendMessage(byte[] msg, int timeout) throws IOException, PortInUseException {
		long startTime = System.currentTimeMillis(), curTime;
		int retries = 0;

		// the dongle always first sends the number of bytes and then echos the complete message
		// TODO: listening for the number of bytes doesn't work. Just ignore it for now, but FIXME
		byte[] expectedMsgAck = new byte[msg.length/*+1*/];
		//expectedMsgAck[0] = (byte) msg.length;
		System.arraycopy(msg, 0, expectedMsgAck, 0/*1*/, msg.length);
		
		logger.info("Sending message to dongle and waiting for ack");
		do {
			// get the dongle to communicate and remember the ID it reported back (switches implicitly to interactive mode)
			myRelateId = getDongleAttention(timeout);
			if (myRelateId == -1)
				logger.info("Warning: could not get dongle's attention, skipping sending of message for this retry");
			else
				sendToDongle(msg);
			curTime = System.currentTimeMillis();
			retries++;
		} while (curTime - startTime < timeout &&
				// if there is some acknowledge expected, try to read it from the dongle
				// the timeout here is just a heuristic
				receiveFromDongle(expectedMsgAck.length, expectedMsgAck, timeout) == null);
		if (curTime - startTime < timeout)
			logger.info("Sent message successfully after " + retries + " tries");
		else
			logger.error("Timeout sending message after " + retries + " tries");
		
		return curTime - startTime < timeout;
	}

	/** Connect to a dongle at the given port. This method just initializes the portId member to point to 
	 * a valid CommPortIdentifier object corresponding to the specified port, but does not open the hardware port.
	 * The portId member will be used by prepareMode to open the hardware port. 
	 * @param port The port to use.
	 * @return true if the port is available and not owned by another application, false otherwise.
	 * @see #portId
	 * @see #prepareMode
	 */
	public synchronized boolean connect(String port) {
		// initialize the portId object based on the objects gathered from the javax.comm API and the passed port name
		if (port != null) {
			for (int i=0; i<availablePorts.size(); i++) {
				if (port.equals(((CommPortIdentifier) availablePorts.elementAt(i)).getName())) {
					portId = (CommPortIdentifier) availablePorts.elementAt(i);
					break;
				}
			}
			if (portId == null) {
				logger.error("no port named '" + port + "' found!");
			} else {
				if (portId.isCurrentlyOwned()) {
					logger.error("port " + port + " is currently in use by " +
							portId.getCurrentOwner());
				} else {
					/* Set serial port parameters directly that are not yet accessible via the (deeply broken) javax.comm API.
					 * This is of course very OS specific, but hopefully only needs to be done once and not each time the port
					 * is opened (i.e. in prepareMode).
					 */
					if (System.getProperty("os.name").startsWith("Linux")) {
						logger.info("Using Linux-specific configuration of the serial port.");
						try {
							// WATCHME: sometimes, the option -opost fixes the "10" duplication, but is not enough 
							// ("255" duplication still happens and seems to be solved by the raw option)
							String[] cmdArgs = new String[] {"stty", "-F", port, "raw"};
							int exitCode = Runtime.getRuntime().exec(cmdArgs).waitFor();
							if (exitCode != 0) {
								logger.error("Unable to set serial port parameters to prohibit post-processing of received characters. " +
										"Exit code of 'stty -F " + port + " raw' was " + exitCode + ". " +
								        "This is non-fatal, but the dongle communication might now be subtly broken.");
							}
						}
						catch (InterruptedException e) {
							logger.error("The process was interrupted while trying to set serial port parameters with " + e + ". " +
							    "This is non-fatal, but the dongle communication might now be subtly broken.");
						}
						catch (IOException e) {
							logger.error("The process execution failed while trying to set serial port parameters with " + e + ". " +
						    "This is non-fatal, but the dongle communication might now be subtly broken.");
						}
					}
					
					return true;
				}
			}
		}
		return false;
	}

	/** Disconnect from the hardware port. */
	public synchronized void disconnect() {
		/*b = null;*/
		if (serialPort != null) {
			serialPort.close();
			serialPort = null;
		}
	}
	
	/** Returns the local dongle id that was reported by the dongle during sending the last message to it. It will be invalid
	 * before the first call to sendMessage, because it is only initialized in that method. However, connect already initializes
	 * it. It is updated every time the dongle's attention is successfully caught, i.e. in every call to the public sendMessage
	 * (which calls the private getDongleAttention that really updates this variable).
	 * @return The dongle ID reported during the last try to get the dongle's attention.
	 * @see #getDongleAttention
	 * @see #sendMessage
	 */
	public int getLocalRelateId() {
		return myRelateId;
	}
	
	/** Switches the communication mode to the non-interactice receive-only mode used to transmit measurements and
	 * status information from the dongle to the host. Use this method after sending commands to the dongle and before
	 * using receiveFromDongle to get measurements or status messages in its normal (non-interactive) mode.
	 * @throws IOException
	 * @throws PortInUseException
	 * @see #receiveFromDongle
	 */
	public synchronized void switchToReceive() throws IOException, PortInUseException {
		prepareMode(false);
	}

	/** The USB to serial bridge seems to be really nasty in losing its baud rate setting when only 
	 * receiving. Therefore it needs to be reset periodically by calling this method while in
	 * non-interactive mode.
	 */
	public synchronized void forceBaudrateReset() {
		// This is _really_ nasty! Why do we need to reset the baud rate continuously?
		// The baud rate just resets itself to 19200 if we don't do that pariodically!
		if (serialPort.getBaudRate() != (this.interacting ? 19200 : 57600)) {
			try {
				logger.debug("BAD BAD DONGLE, NO COOKIE FOR YOU: The USB/serial bridge lost its last baud rate setting, forcing it back to "
								+ (this.interacting ? 19200 : 57600));

				// Thread.sleep(10);
				System.out.println("horrible hack line 4");
				serialPort.setSerialPortParams(
						this.interacting ? 19200 : 57600,
						SerialPort.DATABITS_8, SerialPort.STOPBITS_1,
						SerialPort.PARITY_NONE);
			} catch (Exception e) {
				// sometimes there are just exceptions from the native routines
				// - ignore them
				// dirty hack
				// e.printStackTrace();
			}
		}
	}
}

/**
 * * This class handles communication protocols, data's exchange and parsing
 * between a Relate dongle and its host device (laptop, desktop, PDA, projector) *
 * 
 * To use this class, only a few steps are necessary:<br>
 * 1. Call the static method getSerialConnector to get the singleton object.<br>
 * 2. Call the connect method with the serial port name to use.<br>
 * 3. Register (at least) one event queue that will receive decoded events with registerEventQueue.
 * 4. Create a thread object for the SerialConnector object and start the thread.<br>
 * Optionally, the setHostInfo, switchDiagnosticMode and startAuthenticationWith methods
 * can be used to talk to the dongle at any time. When using one of these methods, the
 * background thread will be suspended until the dongle interaction has been completed.<br>
 * The getLocalRelateId method can be used at any time to query the local id reported by the
 * connected dongle (after connect has been called).
 * 
 * @see #getSerialConnector()
 * @see #connect
 * @see #registerEventQueue
 * @see #setHostInfo
 * @see #switchDiagnosticMode
 * @see #startAuthenticationWith
 * @see #getLocalRelateId
 * 
 * @author Rene Mayrhofer, heavily based on code by Chris Kray, Henoc Agbota, especially the parseEvent method
 */

public class SerialConnector implements Runnable {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(SerialConnector.class);
	/** This is a special log4j logger used for logging only statistics. It is separate from the main logger
	 * so that it's possible to turn statistics on an off independently.
	 */
	private static Logger statisticsLogger = Logger.getLogger("statistics.serialconnector");

	/**
	 * At the moment, there can only be a single instance of SerialConnector.
	 * This singleton is held here and returned by getSerialConnector.
	 * 
	 * @see #getSerialConnector
	 */
	private static SerialConnector sConn = null;
	
	/** This helper class is used to communicate with the serial port. */
	private SerialCommunicationHelper commHelper = null;
	
	/** The serial port name this connector refers to. Used to reconnect to the dongle if it fails to respond.
	 * @see #receiveHelper
	 */
	private String serialPort;

	/**
	 * Flag indicating wether this instance should continue to run. Used by
	 * die() to signal the thread to terminate.
	 * 
	 * @see #die
	 */
	private boolean alive = true;

	/** Flag indicating wether this instance is connected to a dongle. 
	 * It is changed by connect (setting it to true on success) and disconnect (setting it to false).
	 * 
	 * @see #connect
	 * @see #disconnect
	 * @see #isOperational
	 */
	private boolean operational = false;

	/**
	 * Flag indicating wether this instance is fully initialised. It is used by
	 * getSerialConnector to wait for an instance to be initialised before
	 * returning it.
	 * 
	 * @see #getSerialConnector
	 * @see #SerialConnector()
	 */
	private boolean initialised = false;

	/** flag indicating the dongle's state */
	private boolean dongle_on = true;

	/**
	 * flag indicating the dongle state AFTER sending BLOCK_DONGLE or
	 * UNBLOCK_DONGLE
	 */
	private boolean awaken = true;
	
	/** This is the thread object used to run the background monitoring Thread. 
	 * @see #start
	 * @see #stop 
	 */
	private Thread monitoringThread;
	
	/** Used to suspend the thread without modifying the dongle state. This flag should be set to false before
	 * trying to send messages to the dongle so that the background thread will suspend reading from it.
	 */
	private boolean monitoring = true;
	
	/** The monitoring thread will set this flag to true when it has entered its suspended state and set it to
	 * false when it is running again. It is necessary for proper thread synchronization.
	 * @see #monitoring
	 * @see #setMonitoring(boolean)
	 * @see #run
	 */
	private boolean monitoringThreadSuspended = false;
	
	/** Used to synchronize access to the monitoring flag. 
	 * @see #monitoring
	 */
	private Object changeMonitoringWaiter = new Object();
	
	/** This flag inidcates if the monitoring thread has been started yet. It is used by setMonitoring to 
	 * check if the thread should be suspended.
	 * @see #setMonitoring(boolean)
	 */
	private boolean monitoringThreadRunning = false;

	/** flag indicating wether still waiting for calibration info */
	private boolean calibrated = false;

	/** The list of MessageQueues to send new events to. 
	 * @see #registerEventQueue(MessageQueue)
	 * @see #unregisterEventQueue(MessageQueue)
	 * @see #postEvent(RelateEvent) 
	 */
	private LinkedList eventQueues = null;
	
	/** bytes to send to bring dongle into sleep mode */
	private final static byte[] BLOCK_DONGLE = { (byte) 'C', (byte) '0' };

	/** bytes to send to awake dongle from sleep mode */
	private final static byte[] UNBLOCK_DONGLE = { (byte) 'C', (byte) '1' };

	/** bytes to send to awake dongle from sleep mode */
	private final static byte[] DIAGNOSTIC_ON = { (byte) 'C', (byte) '2' };

	/** bytes to send to awake dongle from sleep mode */
	private final static byte[] DIAGNOSTIC_OFF = { (byte) 'C', (byte) '3' };

	private final static int END_COMM = 90 ; 
	/** number of bytes for the host info */
	private final static int HOST_INFO_LENGTH = 51;

	/** number of bytes for the ultrasonic calibration info */
	private final static int CALIBRATION_INFO_LENGTH = 18;

	/** number of bytes for the ultrasonic sensor info */
	private final static int US_SENSOR_INFO_LENGTH = 11;

	/** number of bytes for this measurement */
	private final static int MES_SIZE = 6;

	/** number of bits for ultrasonic receiver id */
	/*private final static int RX_ID_SIZE = 8;*/

	/** number of bits for ultrasonic sender id */
	/*private final static int TX_ID_SIZE = 8;*/

	/** number of bits for this measurement timestamp */
	/*private final static int TIMESTAMP_SIZE = 8;*/

	/** number of bits for number of transducers involved in this measurement */
	private final static int NUMRX_SIZE = 3;

	/** number of high bits in distance measurement */
	private final static int DIST_HIGH_SIZE = 4;

	/** number of low bits in distance measurement */
	private final static int DIST_LOW_SIZE = 7;

	/** number of high bits in angle measurement */
	private final static int ANGLE_HIGH_SIZE = 1;

	/** number of low bits in angle measurement */
	private final static int ANGLE_LOW_SIZE = 7;

	/** number of bits for ball switch data */
	private final static int BALL_SWITCH_SIZE = 1;

	/** number of bits for SS high byte */
	private final static int SS_HIGH_SIZE = 2;

	/** Prefix to measurement bytes 'M' */
	private final static int DEVICE_MEASUREMENT_SIGN = (byte) 'M';

	private Object changeStateWaiter = new Object();

	/** Minimal value for a relate id */
	private int MIN_ID = 0;

	/** Maximal value for a relate id */
	private int MAX_ID = 255;

	/** Prefix to host info 'H' */
	private static final int HOST_INFO_SIGN = (byte) 'H';

	/** Prefix to calibration info 'L' */
	private static final int CALIBRATION_INFO_SIGN = (byte) 'L';

	/** Prefix to uS sensor info 'U' */
	private static final int US_SENSOR_INFO_SIGN = (byte) 'U';

	/** flag indicating wether the diagnostic mode is turned on */
	private boolean diagnosticMode = false;

	/** number of bytes for the firmware version */
	private static final int FIRMWARE_VERSION_LENGTH = 2;

	/** Prefix to firmware version */
	private static final int FIRMWARE_VERSION_SIGN = (byte) 'V';

	/** Prefix to start-of-authentication packet */
	private static final int AUTHENTICATION_START_SIGN = (byte) 'A';

	/** Prefix to authentication data (received key material from a remote dongle) */
	private static final int AUTHENTICATION_PACKET_SIGN = (byte) 'K';

	/** MAGIC VALUE NUMBER 1: Wait for a maximum of 200 ms for each byte to receive from the dongle. It should definitely be
	 * enough to receive a byte (and even more so when waiting for multiple bytes and this is the average maximum value for each
	 * of them) and we don't expect to hit this limit at all. It is just a safety belt in case the dongle fails to repsond that
	 * prevents the methods from waiting indefinitely.
	 * It's magic because there's no real reason for that specific number other than trial&error. 
	 *  
	 * Used in receiveHelper.
	 * @see #receiveHelper
	 */
	private final static int MAGIC_1 = 200;
	
	/** MAGIC VALUE NUMBER 2: The number of tries for receiving a single byte from the dongle (e.g. the first byte of a 
	 * message). This number silently includes the (emperically determined) maximum time it takes the dongle to start 
	 * sending bytes in monitor mode after interaction mode has been left (i.e. baud rate switch, entering a Relate 
	 * RF network, etc.).
	 * It's magic because there's no real reason for that specific number other than trial&error. 
	 * 
	 * Used in receiveHelper.
	 * @see #receiveHelper
	 */
	private final static int MAGIC_2 = 50;
	
	/** MAGIC VALUE NUMBER 3: The time to give the monitoring thread to check for its signal to suspend. It the monitoring
	 * thread is not getting any messages from the dongle, it will wait for a long time for data from the serial port
	 * before checking for the suspend signal.
	 * It's magic because there's no real reason for that specific number other than trial&error. 
	 * 
	 * Used in setMonitoring.
	 * @see #setMonitoring
	 */
	private final static int MAGIC_3 = 500;
	
	/** The maximum timeout for any public method that does not provide a timeout from the application. When it takes the 
	 * dongle longer than this to respond to any command or to provide the next message, something is wrong an a TimeoutException
	 * will be thrown by the respective method.
	 */
	private final static int MAXIMUM_TIMEOUT = 20000;
	
	/** Firmware version */
	private String firmwareVersion = null;

	/** Calibration object */
	private Calibration calibration = null;

	/** List of devices to exclude (temporary for Oliver) */
	private final static int[] DEVICE_NON_GRATA = {};

	/** Prefix to error code */
	private static final int ERROR_CODE_SIGN = (byte) 'E';

	/** Firmware version */
	private int errorCode = -1;

	/** */
	private static int DN_STATE_LENGTH = 0;

	private static final int DN_STATE_SIGN = 'N';
	
	/** The number of errors upon trying to receive bytes from the dongle. This is only used for debugging/statistics, nothing else. */
	private int numReceiveErrors = 0;
	/** The number of errors that decoding a received message failed. */ 
	private int numDecodeErrors = 0;
	/** The number of unkown messages received from the dongle. */
	private int numUnknownMessages = 0;

	/** Initializes the SerialCommunicationHelper object in commHelper. */
	protected SerialConnector() {
		commHelper = new SerialCommunicationHelper();
		
		eventQueues = new LinkedList();
		
		initialised = true;
	}

	/** Get the only instance of the SerialConnector. */
	public static SerialConnector getSerialConnector() {
		if (sConn == null)
			sConn = new SerialConnector();
		while (!(sConn.initialised))
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {

			}
		return sConn;
	}

	/** return list of available serial ports */
	public String[] getPorts() {
		return commHelper.getPorts();
	}
	
	/** return Calibration object */
	public Calibration getCalibration() {
		return calibration ;
	}
	
	/** initialise connector, first handshake with the dongle takes place here. This method fetches the dongle's Relate ID.
	 * @param port		  the port to connect with 
	 @param minID         the lowest valid ID for relate objects
	 @param maxID         the highest valid ID for relate objects
	 @return true if connection could be established, false otherwise.
	 
	 @see #getLocalRelateId
	 **/
	public boolean connect(String port, int minID, int maxID) {
		// TODO: get the hostinfo from somewhere
		// this.hostInfo = configuration.getFormat();

		this.MIN_ID = minID;
		this.MAX_ID = maxID;
		this.serialPort = port;

		if (!operational && commHelper.connect(port)) {
			try {
				logger.info("Connecting to dongle on port " + port);
				
				// this just get's the dongle's attention to see if it's there and implicitly reads its id
				/*if (! commHelper.interruptDongle(MAXIMUM_TIMEOUT))
					return -1;*/
				
				// send a wakeup command to the dongle so that it will definitely start sending measurements
				if (! commHelper.sendMessage(UNBLOCK_DONGLE, MAXIMUM_TIMEOUT)) {
					logger.error("Could not send UNBLOCK_DONGLE message to dongle, initialization failed.");
					return false;
				}

				// switch to receiving mode
				commHelper.switchToReceive();
				logger.info("Switched dongle to receive mode, local id is " + commHelper.getLocalRelateId());

				operational = true;
			} catch (Exception e) {
				logger.fatal("Could not connect to dongle!" + e + "\n" + e.getStackTrace());
				return false;
			}
		}
		if (commHelper.getLocalRelateId() == -1)
			logger.error("Reported dongle id is -1, connect failed.");
		return commHelper.getLocalRelateId() != -1;
	}
	
	/** Switches the diagnostic mode on or off
	 * 
	 * @param diagnostic If true, dongle is switched to diagnostic mode.
	 * @return true if successful
	 */
	public boolean switchDiagnosticMode(boolean diagnostic) {
		logger.info("Switching diagnostic mode " + (diagnostic ? "on" : "off"));
		try {
			setMonitoring(false);
			boolean ret = commHelper.sendMessage(diagnostic ? DIAGNOSTIC_ON : DIAGNOSTIC_OFF, MAXIMUM_TIMEOUT);
			setMonitoring(true);
			if (!ret)
				return false;
		} 
		catch (IOException e) {
			return false;
		}
		catch (PortInUseException e) {
			return false;
		}
		diagnosticMode = diagnostic;
		return true;
	}
	
	/** This method switches the dongle into authentication mode with the specified remote dongle.
	 * During authentication mode, it will report the authentication info parts and delayed measurements
	 * from the remote, but will not send valid US pulses itself. Therefore, during authentication mode,
	 * other dongles will not get any measurements from this one.
	 * 
	 * @param remoteRelateId The remote relate id to authenticate with. It must be a valid relate id and
	 * 			the other dongle needs to be put into authentication mode too.
	 * @param nonce The nonce that is is used for deriving the US delays. Currently, it must have a length
	 *          of 16 bytes.
	 * @param rfMessage The RF message to be sent to the remote dongle, split into multiple parts.
	 *          Currently, it must have a length of 16 bytes.
	 * @param rounds The number of rounds of the interlock protocol to use. Must be >= 2. 
	 * @param bitsPerRound The number of bits of the nonce that are used for deriving the delays at 
	 *          each round. With the current hardware, this needs to be 1 <= rounds <= 3.
	 * 
	 * @return true if the start-of-authentication message could be sent to the dongle. false otherwise.
	 *
	 * @author Rene Mayrhofer
	 */
	public boolean startAuthenticationWith(int remoteRelateId, byte[] nonce, byte[] rfMessage, int rounds, int bitsPerRound) {
		if (nonce.length != 16 || rfMessage.length != 16 || rounds < 2 || rounds > 255 
				|| remoteRelateId < 0 || remoteRelateId > 255 || bitsPerRound < 1) {
			logger.fatal("ERROR in parameters while constructing start-of-auth packet!");
			// TODO: this should actually be an exception, since this is a sanity check for something that shouldn't happen
			return false;
		}
		
		byte msg[] = new byte[36];
		int ind=0;
		boolean ret = false;
		
		msg[ind++] = AUTHENTICATION_START_SIGN;
		msg[ind++] = (byte) remoteRelateId;
		System.arraycopy(nonce, 0, msg, ind, nonce.length); ind += nonce.length;
		System.arraycopy(rfMessage, 0, msg, ind, rfMessage.length); ind += rfMessage.length;
		msg[ind++] = (byte) rounds;
		msg[ind++] = (byte) bitsPerRound;
		
		logger.debug("Constructed authentication packet: " + byteArrayToHexString(msg));
		
		// before sending a message to the dongle, need to suspend the reading thread
		setMonitoring(false);
		try {
			ret = commHelper.sendMessage(msg, MAXIMUM_TIMEOUT);
		}
		catch (PortInUseException e) {
			return false;
		}
		catch (IOException e) {
			return false;
		}
		
		// start the reading again
		setMonitoring(true);
		return ret;
	}
	
	/** Sets the current host info by sending the appropriate message to the dongle. The dongle
	 * will then start broadcasting this host info.
	 *
	 * @param hostInfo The properly formatted host info.
	 * @return true if the host info message could be sent to the dongle. false otherwise.
	 */ 
	public boolean setHostInfo(byte[] hostInfo) {
		boolean ret = false;
		
		// send the host info to the dongle
		byte[] hostInfoMsg = new byte[hostInfo.length + 1];
		// the command byte
		hostInfoMsg[0] = HOST_INFO_SIGN;
		System.arraycopy(hostInfo, 0, hostInfoMsg, 1, hostInfo.length);
		logger.debug("Host info packet: " + byteArrayToHexString(hostInfoMsg));
		logger.info("Sending hostinfo to dongle and waiting for it to ack");
		
		// before sending a message to the dongle, need to suspend the reading thread
		setMonitoring(false);
		try {
			commHelper.sendMessage(hostInfoMsg, MAXIMUM_TIMEOUT);
		}
		catch (PortInUseException e) {
			return false;
		}
		catch (IOException e) {
			return false;
		}
		
		// start the reading again
		setMonitoring(true);
		return ret;
	}
	
	// TODO: this should be a thread-safe message queue!
	public void registerEventQueue(MessageQueue eventQueue) {
		if (!eventQueues.contains(eventQueue))
			eventQueues.add(eventQueue);
	}
	
	public boolean unregisterEventQueue(MessageQueue eventQueue) {
		return eventQueues.remove(eventQueue);
	}
	
	private void postEvent(RelateEvent e) {
		if (e == null) {
			logger.warn("Warning: cowardly refusing to send a null message to the event queues.");
			return;
		}
		
		for (ListIterator i = eventQueues.listIterator(); i.hasNext(); )
			((MessageQueue) i.next()).addMessage(e);
	}
	
	/** Disconnects the serial port to free the ressource. */
	public void disconnect() {
		commHelper.disconnect();
		operational = false;
	}
	
	/** Returns the status of this serial connector object. It is changed by connect (setting
	 * it to true on success) and disconnect (setting it to false).
	 * 
	 * @return true when successfully connected to a dongle, false otherwise.
	 * 
	 * @see #operational
	 * @see #connect
	 * @see #disconnect
	 */
	public boolean isOperational() {
		return operational;
	}
	
	/** Returns the relate id of the locally attached dongle. It is the id
	 * reported by the dongle when the host last got its attention.
	 * It is guaranteed to be set after a call to connect() (even if it might
	 * be erroneous because of communication problems with the dongle, although 
	 * this is highly unlikely).
	 * 
	 * @return The local dongle id or -1 if unknown (e.g. when not connected
	 *         to a dongle or when the last command failed to get the dongle's
	 *         attention).
	 *         
	 * @see #connect
	 * @see SerialCommunicationHelper#getLocalRelateId
	 */
	public int getLocalRelateId() {
		return commHelper.getLocalRelateId();
	}
	
	public void start() {
		if (monitoringThread == null) {
			monitoringThread = new Thread(this);
			monitoringThread.start();
		}
	}
	
	public void stop() {
		if (monitoringThread != null) {
			alive = false;
			try {
				monitoringThread.join();
			}
			catch (InterruptedException e) {
				logger.error("Error waiting for monitoring thread to terminate: " + e.toString() + "\n" + e.getStackTrace().toString());
			}
			monitoringThread = null;
		}
	}
	
	/** convert one byte to int */
	private static int unsign(byte b) {
		return (b<0?256+b:b);
	}
	/** convert two bytes to int */
	private static int unsign2(byte hi, byte lo) {
		return (unsign(hi)*256+unsign(lo));
	}
	
	
	/**  
	 @param size the size of the mask
	 @return size bitmask of all-set-to-1 low bits (higher bits zero)
	 **/
	private static int getMask(int size) {
		int i, mask ;
		mask = 0 ;
		for(i = 1; i <= size; i++) {
			mask = (mask << 1) | 1 ;
		}
		return mask ;
	}
	
	/** parse bytes read over the serial port and try to recognise an event
	 * 	@author Henoc AGBOTA, modified (extended and cleaned up) by Rene Mayrhofer
	 ** @return the event that was parsed from the bytes, or null if no event
	 ** was recognised */
	private RelateEvent parseEvent(byte[] bytes) {
		RelateEvent result = null;
		Device d ;
		int i, 
		/** ultrasonic receiver id*/
		rxId = -1, 
		/** ultrasonic sender id*/
		txId = -1,
		/** measurement's timestamp from the dongle*/
		timestamp = -1,
		/** distance value in decimal mode*/
		dist = -1,
		/** angle value in decimal mode*/
		angle = -1,
		/** high bits value for the measured distance*/
		hDist = -1,
		/** low bits value for the measured distance*/
		lDist = -1,
		/** high bits value for the measured angle*/
		hAngle = -1,
		/** low bits value for the measured angle*/
		lAngle = -1,
		/** number of transducers which record a valid signal for this measurement*/ 
		trans = -1,
		/** number of transducers which record a valid signal for this measurement*/ 
		ballSwitch = -1,
		/** Average background levels for sensors uS1,uS2,uS4*/ 
		ablUs1,ablUs2,ablUs4,
		/** Maximal background levels for sensors uS1,uS2,uS4*/ 
		mblUs1,mblUs2,mblUs4,
		/** Threshold levels for sensors uS1,uS2,uS4*/ 
		thlUs1,thlUs2,thlUs4 ,
		/** TOF values for sensors uS1,uS2,uS4*/ 
		tofUs1,tofUs2,tofUs4,
		/** SS high bytes,low bytes and decimal values for sensors uS1,uS2,uS4*/ 
		hSSUs1,hSSUs2,hSSUs4,lSSUs1,lSSUs2,lSSUs4,ssUs1,ssUs2,ssUs4;
		/** Validity flag values for sensors uS1,uS2,uS4*/ 
		boolean validUs1,validUs2,validUs4, inMotion ;
		int mask, relateTime, numberOfEntries, awareconSyncRate ;
		SensorReading sensorReading = null ;
		Vector dicoveryListIds = null, dicoveryListTimes = null ;
		DongleNetworkState dnState = null ;
		
		if (bytes != null) {
			d = null ;
			if (bytes.length == MES_SIZE || bytes.length == (MES_SIZE+US_SENSOR_INFO_LENGTH)) {
				
				/* First three lower bytes respectively represent 
				 * rxId, txId and timestamp */
				rxId = bytes[0];
				txId = bytes[1];
				timestamp = unsign(bytes[2]);
				
				/* From higher to lower bits, 
				 * fourth byte represents: trans (3), hDist (4) and a spare bit (1) */
				mask = getMask(NUMRX_SIZE) ;
				mask <<= (1 + DIST_HIGH_SIZE) ;
				trans = (bytes[3] & mask)>>(1 + DIST_HIGH_SIZE) ;
				
				mask = getMask(DIST_HIGH_SIZE) ;
				mask = mask << 1 ;
				hDist = (bytes[3] & mask)>>1 ;
				//System.out.println("hDist: "+ hDist +" \n") ;
				
				/* From higher to lower bits, 
				 * fifth byte represents: lDist (7) and ball switch bit (1) */
				mask = getMask(DIST_LOW_SIZE) ;
				mask = mask << BALL_SWITCH_SIZE ;
				lDist = (bytes[4] & mask) ;
				//				System.out.println("lDist: "+ lDist +" \n") ;
				
				mask = getMask(BALL_SWITCH_SIZE) ;
				ballSwitch = (bytes[4] & mask) ;
				inMotion = (ballSwitch == 1) ;
				//System.out.println("ballSwitch: "+ ballSwitch +" \n") ;
				
				/* From higher to lower bits, 
				 * sixth byte represents: hAngle (1) lAngle (7) */
				mask = getMask(ANGLE_HIGH_SIZE) ;
				mask = mask << ANGLE_LOW_SIZE ;
				hAngle = (bytes[5] & mask)>> ANGLE_LOW_SIZE ;
				//System.out.println("hAngle: "+ hAngle +" \n") ;
				
				mask = getMask(ANGLE_LOW_SIZE) ;
				lAngle = (bytes[5] & mask)<<1 ;
				//System.out.println("lAngle: "+ lAngle +" \n") ;
				
				dist = (hDist * 256) + lDist ;
				angle = (hAngle * 256) + lAngle ;
				/*if(rxId == myRelateId)
				 System.out.println("rxID= "+rxId+", txID= "
				 +txId+", timestamp= "+timestamp+", trans= "
				 +trans+", dist= "+dist+", angle= "
				 +angle+", ballSwitch= "+ballSwitch+"\n") ;*/
				/* sanity check */ 
				d = new Device(rxId,
						System.currentTimeMillis(),/*configuration.getDeviceName(),
						configuration.getUserName(),
						configuration.getIpAddress(),configuration.getDeviceType(),
						configuration.getDeviceSide(),*/
						"", "", "", "", 0,
						new Boolean(true)) ;
				
				if ((0 <= angle) && (angle <= 360)) {
					if(rxId == commHelper.getLocalRelateId() && diagnosticMode &&
							bytes.length == (MES_SIZE+US_SENSOR_INFO_LENGTH)){
						logger.debug("Got uS sensor info..!!") ;
						/* printByteArray(bytes) ;*/
						
						/* From higher to lower bits, 
						 * 2 bytes respectively for uS1,uS2,uS4 TOFs
						 */
						tofUs1 = unsign2(bytes[6],bytes[7]) ;
						tofUs2 = unsign2(bytes[8],bytes[9]) ;
						tofUs4 = unsign2(bytes[10],bytes[11]) ;
						
						/* From higher to lower bits, 
						 * fifth byte represents: hSSUs4,hSSUs2,hSSUs1;
						 * 2 bits each . The first two bits of this fifth byte
						 * contain no info*/
						mask = getMask(SS_HIGH_SIZE) ;
						mask <<= (2*SS_HIGH_SIZE) ;
						hSSUs4 = (bytes[12] & mask)>>(2*SS_HIGH_SIZE) ;
						
						mask = getMask(SS_HIGH_SIZE) ;
						mask <<= (SS_HIGH_SIZE) ;
						hSSUs2 = (bytes[12] & mask)>>(SS_HIGH_SIZE) ;
						
						mask = getMask(SS_HIGH_SIZE) ;
						hSSUs1 = (bytes[12] & mask) ;
						
						/* From higher to lower bits, 
						 * 1 byte respectively for uS1,uS2,uS4 low bytes
						 */
						lSSUs1 = bytes[13] ;
						lSSUs2 = bytes[14] ;
						lSSUs4 = bytes[15] ;
						
						ssUs1 = unsign2((byte)hSSUs1,(byte)lSSUs1) ;
						ssUs2 = unsign2((byte)hSSUs2,(byte)lSSUs2) ;
						ssUs4 = unsign2((byte)hSSUs4,(byte)lSSUs4) ;
						
						/* From higher to lower bits, 
						 * first byte represents: validUs4,validUs2,validUs1;
						 * The first five bits of this first byte
						 * contain no info*/
						mask = 4 ; //100
						validUs4 = ((bytes[16] & mask)>>2)==1?true:false ;
						mask = 2 ; //010
						validUs2 = ((bytes[16] & mask)>>1)==1?true:false ;
						mask = 1 ; //001
						validUs1 = ((bytes[16] & mask))==1?true:false ;
						sensorReading = new SensorReading(tofUs1,tofUs2,tofUs4,
								ssUs1,ssUs2,ssUs4,validUs1,validUs2,validUs4) ;
						result = new RelateEvent(RelateEvent.NEW_MEASUREMENT, d,
								new Measurement(rxId, txId,
										dist, angle,
										trans,
										System.currentTimeMillis(),
										timestamp,inMotion,sensorReading),
										System.currentTimeMillis()
/*,null*/);
						logger.debug(sensorReading.toString()) ;	
						 logger.debug("Got uS sensor info..!!") ;
						 /*printByteArray(bytes) ;*/				
					}else {
						result = new RelateEvent(RelateEvent.NEW_MEASUREMENT, d,
								new Measurement(rxId, txId,
										dist, angle,
										trans,
										System.currentTimeMillis(),
										timestamp,inMotion),
										System.currentTimeMillis()
/*,null*/);
					}
					logger.debug(result);
					/* filter events */
					if (invalidID(rxId) || invalidID(txId)) {
						
						 logger.debug("invalid ID:" + (invalidID(rxId) ? "" + rxId : "") + " " +
						 (invalidID(txId) ? "" + txId : ""));
						 
						result = null;
					}
				}
			}
			if( (bytes.length == HOST_INFO_LENGTH) && 
					(bytes[29]==1 || bytes[29]==2 ||bytes[29]==3 || bytes[29]==4) 
					&& (bytes[50]==1 || bytes[50]==2 ||bytes[50]==3 ||bytes[50]==4) ){
				d = new Device(unsign(bytes[0]),
						System.currentTimeMillis(),getHostInfoUserName(bytes),
						getHostInfoMachineName(bytes),
						getHostInfoIp(bytes),getHostInfoDeviceType(bytes),
						bytes[50],new Boolean(true)) ;
				result = new RelateEvent(RelateEvent.DEVICE_INFO, d,null,
						System.currentTimeMillis()
/*,null*/);
				printHostInfo(bytes) ;
				/* filter events */
				if (invalidID(unsign(bytes[0]))) {
					logger.warn("invalid ID:" + (invalidID(rxId) ? "" + rxId : "") + " " +
					 (invalidID(txId) ? "" + txId : ""));
					result = null;
				}
			}
			if (bytes.length == CALIBRATION_INFO_LENGTH) {
				
				/* From higher to lower bits, 
				 * 2 bytes respectively for uS1,uS2,uS4 ABLs
				 * 2 bytes respectively for uS1,uS2,uS4 MBLs 
				 * 2 bytes respectively for uS1,uS2,uS4 thresholds  
				 */
				ablUs1 = unsign2(bytes[0],bytes[1]) ;
				ablUs2 = unsign2(bytes[2],bytes[3]) ;
				ablUs4 = unsign2(bytes[4],bytes[5]) ;
				mblUs1 = unsign2(bytes[6],bytes[7]) ;
				mblUs2 = unsign2(bytes[8],bytes[9]) ;
				mblUs4 = unsign2(bytes[10],bytes[11]) ;
				thlUs1 = unsign2(bytes[12],bytes[13]) ;
				thlUs2 = unsign2(bytes[14],bytes[15]) ;
				thlUs4 = unsign2(bytes[16],bytes[17]) ;
				calibration = new Calibration(ablUs1,ablUs2,ablUs4,
						mblUs1,mblUs2,mblUs4,thlUs1,thlUs2,thlUs4) ;
				logger.debug(calibration.toString()) ;
				result = new RelateEvent(RelateEvent.CALIBRATION_INFO, /*relate.getLocalDevice(),*/ null,
						null,System.currentTimeMillis(),
/*null,*/calibration);
				calibrated = true ;
			} if(bytes[0] == DN_STATE_SIGN && bytes.length == DN_STATE_LENGTH) {
				dicoveryListIds = new Vector() ;
				dicoveryListTimes = new Vector() ;
				relateTime = unsign(bytes[1]) ;
				numberOfEntries = unsign(bytes[2]) ;
				for(i = 0 ; i < numberOfEntries; i++) {
					dicoveryListIds.add(new Integer(unsign(bytes[i+3]))) ;
				}
				for(i = 0 ; i < numberOfEntries; i++) {
					dicoveryListTimes.add(new Integer(unsign(bytes[i+3+numberOfEntries]))) ;
				}
				awareconSyncRate = unsign(bytes[DN_STATE_LENGTH-1]) ;
				dnState = new DongleNetworkState(relateTime,numberOfEntries,
						dicoveryListIds,dicoveryListTimes,awareconSyncRate) ;
				result = new RelateEvent(RelateEvent.DN_STATE, /*relate.getLocalDevice(),*/ null,
						null,System.currentTimeMillis(),null,dnState);
			}
		}
		return result;
	}
	
	/** This is just a small helper function used to convert a byte array to a string of decimals for debugging purposes. */
	public static String byteArrayToDecString(byte[] a) {
		String ret = "";
		for (int i = 0; i < a.length; i++){
			ret += ("["+i+"]="+unsign(a[i])+" ") ;
		}
		return ret;
	}

	/** This is just a small helper function used to convert a byte array to a string of hex for debugging purposes. */
	public static String byteArrayToHexString(byte[] a) {
		String ret = "";
		for (int i = 0; i < a.length; i++){
			ret += ("["+i+"]="+Integer.toHexString(unsign(a[i]))+" ") ;
		}
		return ret;
	}
	
	/** This is just a small helper function used to convert a byte array to a binary string. 
	 * Attention: this binary string is printed in "normal" form, i.e. MSB first, LSB last. */
	public static String byteArrayToBinaryString(byte[] a) {
		String ret = "";
		for (int i = a.length-1; i >=0; i--){
			for (int j=7; j >= 0; j--)
				ret += (a[i] >> j) & 0x1;
			ret += " ";
		}
		return ret;
	}
	
	private void printHostInfo(byte[] a) {
		String hostInfo = "Host info:";
		if (a.length == HOST_INFO_LENGTH)
			hostInfo += (" ID: "+a[0]) ;
		hostInfo += (" IP: "+getHostInfoIp(a)) ;
		hostInfo += (" User name: "+getHostInfoUserName(a)) ;
		hostInfo += (" Device type: "+getHostInfoDeviceType(a)) ;
		hostInfo += (" Machine Name: "+getHostInfoMachineName(a)) ;
		hostInfo += (" Side: "+a[50]) ;
		logger.debug(hostInfo);
	}
	
	private static String getHostInfoIp(byte[] a) {
		int i, imax ;
		String result = "" ;
		if(a[5] != 0)
			imax = 9 ;
		else
			imax = 5 ;
		if (a.length == HOST_INFO_LENGTH)
			for (i = 1; i < imax; i++){
				result = result + unsign(a[i]) ;
				if(i<(imax-1))
					result = result + '.' ;
			}
		return result ;
	}
	
	private static String getHostInfoUserName(byte[] a) {
		int i ;
		String result = "" ;
		if (a.length == HOST_INFO_LENGTH)
			for (i = 9; i < 29; i++){
				if (a[i] != 0)
					result = result + (char)a[i];
			}
		return result ;
	}
	
	private static String getHostInfoMachineName(byte[] a) {
		int i ;
		String result = "" ;
		if (a.length == HOST_INFO_LENGTH)
			for (i = 30; i < 50; i++){
				if (a[i] != 0)
					result = result + (char)a[i];
			}
		return result ;
	}
	
	
	private static String getHostInfoDeviceType(byte[] a) {
		String result = "" ;
		if(a[29] == 1)
			result = "desktop" ;
		if(a[29] == 2)
			result = "laptop" ;
		if(a[29] == 3)
			result = "PDA" ;
		if(a[29] == 4)
			result = "projector" ;
		return result ;
	}
	
	/** set the state of the dongle
	 *  @param on if true, turn dongle on, if false, turn it off
	 */
	public void setDongleState(boolean on) {
		dongle_on = on;
		synchronized(changeStateWaiter) {
			try {
				changeStateWaiter.wait();
			} catch (InterruptedException e) {
			}
		}
	}

	/** Sets the state of the background monitoring thread.
	 * 
	 * @param monitoring If false, the thread will be suspended. If true, the thread will be woken up again.
	 */
	private void setMonitoring(boolean monitoring) {
		if (monitoringThreadRunning) {
			logger.info("Requesting monitoring thread to " + (monitoring ? "wake up" : "suspend"));
			this.monitoring = monitoring;
			synchronized(changeMonitoringWaiter) {
				changeMonitoringWaiter.notify();
				try {
					/* Don't wait until forever for a graceful suspension of the monitoring thread. The problem
					 * is that, when the monitoring thread is not getting any messages from the dongle, it can
					 * take a long time (i.e. MAGIC_1 * MAGIC_2 ms, see receiveHelper) for the monitoring thread
					 * to check if it should suspend, because it is doing a blocking wait on the serial port. So
					 * just give the thread some time that is usually enough to make it suspend itself gracefully
					 * when it gets messages from the dongle, but the interrupt it to force it to check more quickly.
					 */
					changeMonitoringWaiter.wait(MAGIC_3);
					while (!monitoringThreadSuspended) {
						monitoringThread.interrupt();
						changeMonitoringWaiter.wait(MAGIC_3);
					}
				} catch (InterruptedException e) {
				}
			}
			logger.info("Monitoring thread signalled its " + (monitoring ? "wake up" : "suspend"));
		}
	}
	
	/** change the dongle state according to dongle_on */
	private void changeDongleState() {		
		try {
			if (dongle_on) {
				/* starting up */
				commHelper.sendMessage(UNBLOCK_DONGLE, 50000);
				awaken = true ;
			} else {
				/* shutting down */
				commHelper.sendMessage(BLOCK_DONGLE, 50000);
				awaken = false ;
				logger.info("shut down dongle");
			}
		} catch (Exception e) {
			logger.error("changing dongle state to " + (dongle_on ? "on" : "off") + "failed! " + e + "\n" + e.getStackTrace());
		}
		synchronized(changeStateWaiter) {
			changeStateWaiter.notify();
		}
	}
	
	/** filter invalid IDs i.e. those not falling in the specified range */
	private boolean invalidID(int id) {
		boolean res = false ;
		if ((id < MIN_ID) || (id > MAX_ID))
			res = true;
		for (int i = 0; i < DEVICE_NON_GRATA.length; i++){
			if (id == DEVICE_NON_GRATA[i]) {
				res = true ;
				break ;
			}
		}
		return res ;
	}
	
	/** This is only a convenience wrapper around commHelper.receiveFromDongle that sets the timeouts, passes null for the 
	 * expected acknowledgement bytes etc. If bytes can't be receives, this method will throw an exception instead of returning
	 * null so that the return value does not need to be checked for being null. If there is no exception, this method guarantees
	 * to return as many bytes as requested.
	 */
	private byte[] receiveHelper(int numBytes) throws InterruptedException {
		int tries = MAGIC_2;
		byte[] ret;
		// workaround for getting just 1 byte: try it a number of times since the dongle might be busy switching mode
		do {
			ret = commHelper.receiveFromDongle(numBytes, null, numBytes * MAGIC_1);
			// this sleep is only in there so that the thread can receive an interrupted exception
			Thread.sleep(1);
			// in addition check if we have been signalled to suspend, if yes, then return immediately
			if (!monitoring)
				return null;
		} while (ret == null && numBytes == 1 && --tries > 0);
		if (ret == null) {
			disconnect();
			connect(serialPort, MIN_ID, MAX_ID);
			throw new NullPointerException("Did not receive enough bytes (wanted " + numBytes + 
					") from the dongle (error number " + (++numReceiveErrors) +
					" since startup). Timeout? Tried to reconnect serial port to reset dongle");
		}
		else
			return ret;
	}
	
	public void run() {
		int theByte;
		int relateTime, numberOfEntries;
		byte[] mesbytes = null, dnStateBytes = null;
		byte[] hostInfoBytes = null, calibrationInfoBytes = null, 
		usSensorInfoBytes = null, firmwareVersionBytes = null;
		/*byte[] stringDelimiters = {13,10} ;*/
		RelateEvent event = null;
		boolean last_dongle_state = dongle_on;
		
		HashMap unknownMessages = new HashMap();
		
		// temp code
		int i=0; 
		
		while (alive) {
			monitoringThreadRunning = true;
			
			try {
					if (dongle_on != last_dongle_state) {
						last_dongle_state = dongle_on;
						logger.info("Setting dongle state to " + dongle_on);
						changeDongleState();
					}
					
					synchronized(changeMonitoringWaiter) {
						// if we are not in monitoring mode, block until woken up again
						if (!monitoring) {
							logger.info("------- Monitoring thread now suspending");
							monitoringThreadSuspended = true;
							// first notify setMonitoring that we received the signal to block
							changeMonitoringWaiter.notify();
							// block until setMonitoring notifies us
							changeMonitoringWaiter.wait();
							logger.info("+++++++ Monitoring thread now waking up again");
							monitoringThreadSuspended = false;
							// and then again notify setMonitoring that we received the signal
							changeMonitoringWaiter.notify();
						}
					}
					
					if(awaken) {
						// the dongle must be in listening mode with the correct baud rate
						commHelper.switchToReceive();
						commHelper.forceBaudrateReset();

						if (++i % 100 == 0) {
							statisticsLogger.info("# SerialConnector statistics: " + numReceiveErrors + " rec.err., " + numDecodeErrors + " dec.err., " + numUnknownMessages + " unkn.msg.");
							/*for (Iterator iter=unknownMessages.keySet().iterator(); iter.hasNext(); ) {
								Integer key = (Integer) iter.next();
								System.out.println(key + " --> " + unknownMessages.get(key));
							}*/
						}
						
						theByte = unsign(receiveHelper(1)[0]);
						/*System.out.println(commHelper.serialPort.getBaudRate() + " " + commHelper.serialPort.getDataBits() + " " +
								commHelper.serialPort.getStopBits() + " " + commHelper.serialPort.getParity());*/
						
						//System.out.println("theByte: " + unsign((byte) theByte));
						if(theByte == DEVICE_MEASUREMENT_SIGN) {
							logger.debug("Measurement message from dongle");
							mesbytes = receiveHelper(MES_SIZE);
							event = parseEvent(mesbytes);
							if (event != null) {
								postEvent(event);
							} else {
								logger.warn("could not parse measurement event (now " + (++numDecodeErrors) + " decoding errors)");
							}
						} else if(theByte == US_SENSOR_INFO_SIGN) {
								usSensorInfoBytes = new byte[MES_SIZE+US_SENSOR_INFO_LENGTH] ;
								byte[] tmp = receiveHelper(US_SENSOR_INFO_LENGTH);
								System.arraycopy(tmp, 0, usSensorInfoBytes, MES_SIZE, tmp.length);
								// attention: uses the most recent mesbytes, since we assume that this packet immediately follows a measurement packet
								System.arraycopy(mesbytes, 0, usSensorInfoBytes, 0, mesbytes.length);
								event = parseEvent(usSensorInfoBytes);
								if (event != null) {
									postEvent(event);
								} else {
									logger.warn("could not parse US info event (now " + (++numDecodeErrors) + " decoding errors)");
								}
								//System.out.println("Got US info");
						} else if (theByte == END_COMM) {
							logger.info("dongle is sleeping.");
						} 
						else if(theByte == HOST_INFO_SIGN){
							//log("Host info message from dongle");
							hostInfoBytes = receiveHelper(HOST_INFO_LENGTH);
							event = parseEvent(hostInfoBytes);
							if (event != null) {
								postEvent(event);
							} else {
								logger.warn("could not parse host info event (now " + (++numDecodeErrors) + " decoding errors)");
							}
						}else if(theByte == CALIBRATION_INFO_SIGN && !calibrated) {
							//log("Calibration info message from dongle");
							calibrationInfoBytes = receiveHelper(CALIBRATION_INFO_LENGTH);
							if (calibrationInfoBytes == null)
								continue;
							event = parseEvent(calibrationInfoBytes);
							if (event != null) {
								postEvent(event);
							} else {
								logger.warn("could not parse calibration info event (now " + (++numDecodeErrors) + " decoding errors)");
							}
						}else if(theByte == FIRMWARE_VERSION_SIGN && firmwareVersion == null) {
							//log("Firmware version message from dongle");
							firmwareVersionBytes = receiveHelper(FIRMWARE_VERSION_LENGTH);
							firmwareVersion = ""+unsign(firmwareVersionBytes[0])+"."+
							+unsign(firmwareVersionBytes[1]) ;
							logger.info("The firmware version for dongle "+commHelper.getLocalRelateId()+" is "+
									firmwareVersion+".") ;
						}else if(theByte == ERROR_CODE_SIGN) {
							errorCode = unsign(receiveHelper(1)[0]) ;
							event = new RelateEvent(RelateEvent.ERROR_CODE, /*relate.getLocalDevice(),*/null,
									null,System.currentTimeMillis(),null,new Integer(errorCode));
							if (event != null) {
								postEvent(event);
							} else {
								logger.warn("could not parse error code event (now " + (++numDecodeErrors) + " decoding errors)");
							}
							logger.debug("error code: "+errorCode) ;
						}else if(theByte == DN_STATE_SIGN) {
							//log("Dongle network state message from dongle");
							byte[] tmp1 = receiveHelper(2);
							relateTime = unsign(tmp1[0]) ;
							numberOfEntries = unsign(tmp1[1]) ;
							DN_STATE_LENGTH = 3+(2*numberOfEntries)+1 ;
							byte[] tmp2 = receiveHelper(2*numberOfEntries+1);
							// FIXME: this is a bad hack - passing parameters to parseEvent this way is not good...
							dnStateBytes = new byte[3+2*numberOfEntries+1];
							dnStateBytes[0] = (byte)DN_STATE_SIGN ;
							dnStateBytes[1] = (byte)relateTime ;
							dnStateBytes[2] = (byte)numberOfEntries ;
							System.arraycopy(tmp2, 0, dnStateBytes, 3, tmp2.length);
							//printByteArray(dnStateBytes) ;
							event = parseEvent(dnStateBytes);
							if (event != null) {
								postEvent(event);
							} else {
								logger.warn("could not parse dongle network state event (now " + (++numDecodeErrors) + " decoding errors)");
							}
						}else if(theByte == AUTHENTICATION_PACKET_SIGN) {
							byte[] tmp = receiveHelper(3);
							int remoteRelateId = unsign(tmp[0]);
							// this uppermost bit indicates if we had an acknowledge
							int curRound = unsign(tmp[1]) & 0x7f;
							boolean ack = (unsign(tmp[1]) & 0x80) != 0;
							int numMsgBytes = unsign(tmp[2]);
							byte[] msgPart = receiveHelper(numMsgBytes);
							logger.debug("Got RF authentication packet from remote relate id " + remoteRelateId + " at round " + curRound +
									(ack ? " with" : " without") + " ack  (" + numMsgBytes + " bytes): " + byteArrayToDecString(msgPart));

							event = new RelateEvent(RelateEvent.AUTHENTICATION_INFO, 
									new Device(remoteRelateId, System.currentTimeMillis(), null, null, null, null, 0, new Boolean(true)), 
									System.currentTimeMillis(), msgPart, curRound, ack);
							postEvent(event);
						}
						else {
							logger.debug("Unkown message from dongle: " + theByte + " (now " + (++numUnknownMessages) + " decoding errors)");
							Integer count;
							if (unknownMessages.containsKey(new Integer(theByte)))
								count = (Integer) unknownMessages.get(new Integer(theByte));
							else
								count = new Integer(0);
							count = new Integer(count.intValue() + 1);
							unknownMessages.put(new Integer(theByte), count);
						}
					}else {       //Dongle probably sleeping..
						logger.info("Dongle sleeping?");
						if (dongle_on != last_dongle_state) {
							last_dongle_state = dongle_on;
							logger.error("nothing to read..!!");
							changeDongleState();
						}
					}
			}catch (Exception ex) {
				logger.fatal("failure in main loop: " + ex + "\n" + ex.getStackTrace());
				/*try {
					Thread.sleep(2000);
				} catch (InterruptedException e) {
					
				}*/
			}
		}	
		disconnect();
	}
	
	//Testing
	public static void main(String args[]) throws Exception {
		SerialConnector connector = SerialConnector.getSerialConnector();
		/*Configuration configuration = new Configuration("./img/", connector, true);
		
		// wait for the user to specify all fields 
		while (!configuration.fullySpecified()) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
			}
		}
		
		// connect to serial port 
		connector.connect(null, new MessageQueue(), configuration, 0, 20);
		configuration.hideGUI();
		connector.run();*/
		
		if (args.length < 1) {
			System.out.println("Error: need the port to connect to");
			System.exit(1);
		}
		
		if (!connector.connect(args[0], 0, 50)) {
			System.out.println("Error connecting to dongle!");
			System.exit(1);
		}
		System.out.println("My relate id is " + connector.getLocalRelateId());
		
		if (args.length > 1) {
			if (args[1].equals("start_auth")) {
				byte[] tmpMsg = new byte[16];
				for (int i=0; i<tmpMsg.length; i++) tmpMsg[i] = (byte) 0xaa;
				connector.startAuthenticationWith(Integer.parseInt(args[2]), tmpMsg, tmpMsg, 43, 3);
			}
			else if (args[1].equals("diag_on")) {
				connector.switchDiagnosticMode(true);
			}
			else if (args[1].equals("diag_off")) {
				connector.switchDiagnosticMode(false);
			}
			else if (args[1].equals("wakeup")) {
				connector.commHelper.sendMessage(new byte[] {'C', '1'}, MAXIMUM_TIMEOUT);
			}
			else if (args[1].equals("monitoring")) {
				connector.run();
			}
		}
		connector.run();
		
		System.out.println("done");
		System.exit(0);
	}
	
}

