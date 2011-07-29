/* Copyright Rene Mayrhofer
 * File created 2006-06-06
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.sensors;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.UnsupportedCommOperationException;

import java.util.logging.Logger;
import org.openuat.channel.main.bluetooth.jsr82.BluetoothRFCOMMChannel;

/** This class implements a reader for the "RAW" data format generated by 
 * Spark Fun Electronics WiTilt v2.5 sensors. It is a simple ASCII-based
 * format in the form:
 * 
 * X=526    Y=473   Z=741
 * X=522    Y=481   Z=753
 * X=514    Y=480   Z=747
 * X=518    Y=474   Z=748
 * X=521    Y=481   Z=745
 * 
 * This class depends on the devices being pre-configured appropriately, e.g.
 * to sample all 3 axises and with the correct sampling rate. They also need to
 * be configured to generate their "RAW" data stream. 
 * 
 * There are two ways to connect to a WiTilt device:
 * - With a (virtual) serial port. This can be used then the WiTilt device is
 *   connected to a physical serial port via its Debug port or with the operating
 *   system RFCOMM emulation, e.g. the /dev/rfommX devices unter Linux or COMX 
 *   devices under Windows. To use this method, use the method 
 *   @see #openSerial(String, boolean).
 * - Directly from Java with a JSR82 implementation. To use this method, use 
 *   the method @see #openBluetooth(String, boolean).
 *   
 * Additionally, this class can be used either with the original firmware, which
 * displays the menu of the WiTilt device when connecting to it, or with a modified
 * firmware that goes directly into sampling mode after connecting. This can be 
 * controlled by the boolean parameters given to the open methods.
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class WiTiltRawReader extends AsciiLineReaderBase {
	/** Our logger. */
	private static Logger logger = Logger.getLogger(WiTiltRawReader.class.getName());

	/** The baud rate to use when connecting to a serial port. This is not used
	 * when connecting directly to the RFCOMM channel.
	 */
	private final static int BAUDRATE = 57600;
	
	public final static int VALUE_RANGE = 1024;
	
	private final static String MENU_HEADER = "WiTilt Firmware v3 - Configuration Menu:";
	
	/** The serial port object when connecting via method 1 (serial port). */
	private SerialPort serialPort = null;
	
	/** The Bluetooth RFCOMM channel when connecting via method 2 (JSR82). */
	private BluetoothRFCOMMChannel rfcommChannel = null;
	
	/** An output stream used to interact with the WiTilt menu, if necessary. The
	 * InputStream object is held by the super class.
	 */
	private OutputStream portCmd = null;
	
	/** Initializes the WiTilt RAW reader object, but does not open a connection.
	 * @see #openSerial(String, boolean)
	 * @see #openBluetooth(String, boolean)
	 */
	public WiTiltRawReader() {
		// we have a maximum of 3 values (X, Y, Z) to read per sample
		super(3);
	}
	
	/** Opens a connection to the WiTilt device via a (virtual) serial port.
	 * It only saves the passed parameters and opens the InputStream to read 
	 * from the specified file, and thus implicitly to check if the file exists 
	 * and can be opened.
	 * 
	 * @param serialPortName The serial port to read from. It will be opened
	 *                       and initialized with the correct parameters. 
	 * @param usingMenu Set to true when the Witilt device runs the original
	 *                  firmware and we need to interact with its menu during
	 *                  initialization. Set to false when using the modified
	 *                  firmware that goes directly into sampling mode.
	 * @throws InternalApplicationException 
	 * @throws FileNotFoundException When filename does not exist or can not be opened.
	 */
	public void openSerial(String serialPortName, boolean usingMenu) throws IOException {
		if (serialPort != null || rfcommChannel != null) {
			throw new IOException("Already opened a channel");
		}
		
		// need to initialize the serial port properly
		try {
			logger.finer("Using port '" + serialPortName + "'");
			CommPortIdentifier portId = CommPortIdentifier.getPortIdentifier(serialPortName);
			if (portId.isCurrentlyOwned()) {
				throw new IOException("port " + port + " is currently in use by " +
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
						String[] cmdArgs = new String[] {"stty", "-F", serialPortName, "raw"};
						int exitCode = Runtime.getRuntime().exec(cmdArgs).waitFor();
						if (exitCode != 0) {
							logger.severe("Unable to set serial port parameters to prohibit post-processing of received characters. " +
									"Exit code of 'stty -F " + port + " raw' was " + exitCode + ". " +
							        "This is non-fatal, but the device communication might now be subtly broken.");
						}
					}
					catch (InterruptedException e) {
						throw new IOException("The process was interrupted while trying to set serial port parameters with " + e + ". " +
						    "This is non-fatal, but the device communication might now be subtly broken.");
					}
					catch (IOException e) {
						throw new IOException("The process execution failed while trying to set serial port parameters with " + e + ". " +
					    "This is non-fatal, but the device communication might now be subtly broken.");
					}
				}

				try {
					serialPort = (SerialPort) portId.open("WiTiltPort", 500);
					try {
						serialPort.setSerialPortParams(BAUDRATE,
								SerialPort.DATABITS_8,
								SerialPort.STOPBITS_1,
								SerialPort.PARITY_NONE);
						// so that read on the getInputStream does not hang indefinitely but times out
						serialPort.enableReceiveTimeout(1000);
						if (!serialPort.isReceiveTimeoutEnabled())
							logger.warning("Warning: serial port driver does not support receive timeouts! It is possible that read operations will block indefinitely.");
						serialPort.setFlowControlMode(SerialPort.FLOWCONTROL_NONE);
						logger.info("Opened port " + portId.getName() + " at " + serialPort.getBaudRate());

						// also open the output stream to the port so that we can interact with the menu
						portCmd = serialPort.getOutputStream();
						port = serialPort.getInputStream();
						
						openFinalize(usingMenu);
					} catch (UnsupportedCommOperationException e) {
						if (! System.getProperty("os.name").startsWith("Windows CE")) {
							logger.severe("UnsupportedCommOperationException: " + e + "\n" + e.getStackTrace());
						}
						else {
							// J2ME CLDC doesn't have reflection support and thus no getStackTrace()....
							logger.severe("UnsupportedCommOperationException");
						}
					}
				}
				catch (IOException e) {
					throw new IOException("Could not open port for reading and/or writing: " + e);
				}
				catch (PortInUseException e) {
					throw new IOException("Could not open port for reading and/or writing: " + e);
				}
			}
		}
		catch (NoSuchPortException e) {
			throw new IOException("Could not create CommPortIdentifier object from port name '" + serialPortName + "': " + e);
		}
	}

	/** Opens a connection to the WiTilt device via an RFCOMM channel with JSR82.
	 * It saves the passed parameters, tries to connect to the RFCOMM channel, and 
	 * opens the InputStream to read from the specified port.
	 * 
	 * @param deviceAddress The Bluetooth MAC address of the WiTilt sensor to connect
	 *                      to, in the format "AABBCCDDEEFF"; 
	 * @param usingMenu Set to true when the Witilt device runs the original
	 *                  firmware and we need to interact with its menu during
	 *                  initialization. Set to false when using the modified
	 *                  firmware that goes directly into sampling mode.
	 */
	public void openBluetooth(String deviceAddress, boolean usingMenu) throws IOException {
		if (serialPort != null || rfcommChannel != null) {
			throw new IOException("Already opened a channel");
		}
		
		rfcommChannel = new BluetoothRFCOMMChannel(deviceAddress, 1);
		rfcommChannel.open();
		
		// also open the output stream to the port so that we can interact with the menu
		portCmd = rfcommChannel.getOutputStream();
		port = rfcommChannel.getInputStream();
		
		openFinalize(usingMenu);
	}
	
	/** This is just a small helper function called by both public open methods
	 * that will, conditionally on usingMenu being true, try to start sampling mode
	 * via the menu.
	 * @throws IOException 
	 */
	private void openFinalize(boolean usingMenu) throws IOException {
		if (usingMenu) {
			logger.finer("Interacting with menu: trying to gain control");
			if (! waitForMenuControl()) {
				throw new IOException("Could not gain control of the WiTilt menu");
			}
			logger.finer("Gained menu control, now starting sampling mode");
			// now start the sensor output
			PrintWriter w = new PrintWriter(portCmd);
			w.print('1');
			w.flush();
		}
		else {
			logger.finer("Not interacting with menu, assuming sampling mode to be enabled already");
		}
	}
	
	/** This is just a small helper function for checking if the WiTilt device printed 
	 * its menu. */
	private boolean checkForMenuOutput(BufferedReader r) {
		boolean foundMenu = false;
		// drain all input
		String line;
		try {
			line = r.readLine();
			// TODO: add a timeout
			while (!foundMenu && line != null) {
				logger.finer("read from sensor: '" + line);
				if (line.indexOf(MENU_HEADER) != -1) {
					logger.finer("Detected menu start header");
					foundMenu = true;
				}
				line = r.readLine();
			}
		} catch (IOException e) {
			// simply ignore, this means that the menu output has ended (or no output at all)
			return false;
		}
		return foundMenu;
	}
	
	/** This is a helper function to try to get menu control. It waits for the menu
	 * being printed and asks the user to reset the device if necessary.
	 * @return
	 */
	private boolean waitForMenuControl() {
		logger.info("Waiting to gain WiTilt menu control");
		
		try {
			BufferedReader r = new BufferedReader(new InputStreamReader(port));
			PrintWriter w = new PrintWriter(portCmd);
			
			boolean gotControl = false;
			// TODO: add a timeout
			while (!gotControl) {
				logger.info("Trying to invoke menu ...");
				// before trying to invoke the menu, drain the output
				if (checkForMenuOutput(r))
					logger.finer("Initial menu was printed before gaining control, device seems to have been reset");

				// try to get the menu to print
				w.print(' ');
				w.flush();
				
				if (checkForMenuOutput(r)) {
					logger.info("Successfully invoked menu");
					gotControl = true;
				}
				else {
					logger.info("Could not invoke menu, please reset device now");
					Thread.sleep(1000);
				}
			}
			return gotControl;
		} catch (InterruptedException e) {
			logger.severe("Interrupted while trying to get menu control" + e);
			return false;
		}
	}
	
	/** This closes the serial port or Bluetooth channel properly. 
	 * It should <b>not</b> be called manually!
	 */
	//@Override
	public void dispose() {
		if (serialPort != null) {
			try {
				portCmd.close();
				port.close();
			}
			catch (IOException e) {
				logger.warning("Exception occured during closing the serial port streams: " + e);
			}
			serialPort.close();
			serialPort = null;
		}
	}
	
	/** A helper function to parse single line of the format produced by 
	 * parport-pulsewidth. This method creates the samples and emits events.
	 * @param line The line to parse.
	 */
	//@Override
	protected void parseLine(String line) {
		//System.out.println("got line from sensor: '" + line + "'");
		if (line.length() < 2)
			// silently ignore empty lines (they might be due to \r\n conversion)
			return;
		
		StringTokenizer st = new StringTokenizer(line, " =", false);
		try {
			double[] values = new double[maxNumLines];
			
			// just read all values
			for (int i=0; i<maxNumLines; i++) {
				char valName = (char) ('X' + i);
				String tok = st.nextToken();
				//System.out.println("token1: '" + tok + "'");
				if (! tok.equals(Character.toString(valName))) {
					logger.warning("Did not get start of " + valName + " value, skipping line");
					return;
				}
				try {
					tok = st.nextToken();
					//System.out.println("token2: '" + tok + "'");
					values[i] = Integer.parseInt(tok.trim());
				}
				catch (NumberFormatException e) {
					logger.warning("Could not parse value for " + valName + ", skipping line (" + e + ")");
				}
			}
			
			emitSample(values);
		}
		catch (NoSuchElementException e) {
			logger.warning("Could not parse line from WiTilt sensor, skipping it (" + e + ")");
		}
	}
	
	/** Provides appropriate parameters for interpreting the values to 
	 * normalize to the [-1;1] range.
	 */
	//@Override
	public TimeSeries.Parameters getParameters() {
		return new TimeSeries.Parameters() {
			public float getMultiplicator() {
				return 2f/VALUE_RANGE;
			}

			public float getOffset() {
				return -1f;
			}
		};
	}
	/** Instead of to [-1;1], these integer parameters map to [-1024;1024],
	 * i.e. MAXIMUM_RANGE in TimeSeries_Int. */
	public TimeSeries_Int.Parameters getParameters_Int() {
		return new TimeSeries_Int.Parameters() {
			public int getMultiplicator() {
				return 2*TimeSeries_Int.MAXIMUM_VALUE;
			}

			public int getDivisor() {
				return VALUE_RANGE;
			}

			public int getOffset() {
				return -TimeSeries_Int.MAXIMUM_VALUE;
			}
		};
	}
	
	/////////////////////////// test code begins here //////////////////////////////
	private static class TestSamplesSink implements SamplesSink {
		private char name;
		
		public void addSample(double sample, int index) {
			System.out.println(name + ": " + sample);
		}

		public void segmentStart(int index) {
			// just ignore for sample code
		}

		public void segmentEnd(int index) {
			// just ignore for sample code
		}
	}
	
	public static void main(String[] args) throws IOException {
		//mainRunner("WiTiltRawReader", args);
		
		if (args.length != 3) {
			System.err.println("Usage: <serial|bluetooth> <port name|device name> <using menu: true|false>");
			System.exit(1);
		}
		boolean serial = false;
		if (args[0].equals("serial"))
			serial = true;
		else if (args[0].equals("bluetooth"))
			serial = false;
		else {
			System.err.println("Usage: <serial|bluetooth> <port name|device name> <using menu: true|false>");
			System.exit(2);
		}
		String name = args[1];
		boolean menu = false;
		if (args[2].equals("true"))
			menu = true;
		else if (args[2].equals("false"))
			menu = false;
		else {
			System.err.println("Usage: <serial|bluetooth> <port name|device name> <using menu: true|false>");
			System.exit(3);
		}

		AsciiLineReaderBase r = new WiTiltRawReader();
		if (serial)
			((WiTiltRawReader) r).openSerial(name, menu);
		else
			((WiTiltRawReader) r).openBluetooth(name, menu);

		TestSamplesSink[] sinks = new TestSamplesSink[3];
		for (int i=0; i<=2; i++) {
			sinks[i] = new TestSamplesSink();
			sinks[i].name = (char) (('X' + i));
		}
		r.addSink(new int[] {0, 1, 2}, sinks);
		r.simulateSampling();
	}
}
