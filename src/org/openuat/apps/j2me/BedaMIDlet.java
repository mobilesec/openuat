/* Copyright Lukas Huser
 * File created 2008-12-09
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.apps.j2me;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Hashtable;
import java.util.Random;
import java.util.Vector;

import javax.bluetooth.RemoteDevice;
import javax.bluetooth.ServiceRecord;
import javax.bluetooth.UUID;
import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Choice;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.List;
import javax.microedition.midlet.MIDlet;
import javax.microedition.midlet.MIDletStateChangeException;

import net.sf.microlog.appender.FileAppender;
import net.sf.microlog.ui.LogForm;

import org.apache.commons.codec.binary.Hex;
import org.openuat.authentication.AuthenticationProgressHandler;
import org.openuat.authentication.HostProtocolHandler;
import org.openuat.authentication.OOBChannel;
import org.openuat.authentication.OOBMessageHandler;
import org.openuat.authentication.exceptions.InternalApplicationException;
import org.openuat.channel.oob.ButtonChannelImpl;
import org.openuat.channel.oob.ButtonToButtonChannel;
import org.openuat.channel.oob.FlashDisplayToButtonChannel;
import org.openuat.channel.oob.LongVibrationToButtonChannel;
import org.openuat.channel.oob.ProgressBarToButtonChannel;
import org.openuat.channel.oob.ShortVibrationToButtonChannel;
import org.openuat.channel.oob.j2me.J2MEButtonChannelImpl;
import org.openuat.log.Log;
import org.openuat.log.LogFactory;
import org.openuat.log.j2me.MicrologFactory;
import org.openuat.log.j2me.MicrologLogger;
import org.openuat.util.BluetoothPeerManager;
import org.openuat.util.BluetoothRFCOMMChannel;
import org.openuat.util.BluetoothRFCOMMServer;
import org.openuat.util.Hash;
import org.openuat.util.LineReaderWriter;
import org.openuat.util.ProtocolCommandHandler;
import org.openuat.util.RemoteConnection;

/**
 * This MIDlet demonstrates the different button channels within the OpenUAT
 * toolkit on the J2ME platform. Most of the channels are described in 
 * 'BEDA: Button-Enabled Device Association' by C. Soriente and G. Tsudik.
 * However, there are possibly additional channels implemented here.
 * 
 * @author Lukas Huser
 * @version 1.0
 */
public class BedaMIDlet extends MIDlet implements AuthenticationProgressHandler {	
	
	/* string constants */
	private static final String DEVICE_LIST_TITLE 	= "Choose a device";
	private static final String CHANNEL_LIST_TITLE	= "Choose a channel";
	
	/* The authentication service uuid. */
	private static final UUID SERVICE_UUID = new UUID("447d8ecbefea4b2d93107ced5d1bba7e", false);
	
	/* Defines a channel for bluetooth connections */
	private static final int BLUETOOTH_CHANNEL_NR	= 5;
	
	/* Current display */
	private Display display;
	
	/* Welcome the user to the BedaMIDlet! */
	private Form welcomeScreen;
	
	/* A list of all available button channels */
	private List channelList;
	
	/* A list of all found bluetooth devices */
	private List deviceList;
	
	/* The microlog LogForm */
	private LogForm logForm;

	/* Enables the user to go back to the last screen */
	private Command backCommand;
	
	/* Standard OK command */
	private Command okCommand;
	
	/* Display while searching for bluetooth devices */
	private Alert bluetoothAlert;
	
	
	/* Needed to build the various button channels */
	private ButtonChannelImpl impl;
	
	/* Scans for bluetooth devices around */
	private BluetoothPeerManager peerManager;
	
	/* Bluetooth server to advertise our services and accept incoming connections */
	private BluetoothRFCOMMServer btServer;
	
	/* Is this device the initiator of the verification protocol? */
	private boolean isInitiator;
	
	/* A collection of found devices */
	private RemoteDevice[] devices;
	
	/* Bluetooth address of the current peer */
	private String currentPeerAddress;
	
	/* Remember the currently used button channel */
	private OOBChannel currentChannel;
	
	/* Keep a mapping of the different button channels with their names */
	private Hashtable buttonChannels;
	
	/* Random number generator to build random messages (for testing) */
	private Random random;
	
	/* Logger instance */
	private Log logger;
	
	
	/**
	 * Creates a new MIDlet. Used for one-time initializations.
	 */
	public BedaMIDlet() {
		super();
	}
	
	/**
	 * Destroys this MIDlet. Frees resources first.
	 * @param unconditional
	 */
	protected void destroyApp(boolean unconditional) throws MIDletStateChangeException {
		cleanUp();
		notifyDestroyed();
	}

	/**
	 * Pauses this MIDlet. Frees resources first.
	 */
	protected void pauseApp() {
		cleanUp();
	}

	/**
	 * Starts this MIDlet. MIDlet initialization is done here.
	 */
	protected void startApp() throws MIDletStateChangeException {
		
		// MIDlet initialization
		display = Display.getDisplay(this);
		welcomeScreen	= new Form("BEDA MIDlet");
		welcomeScreen.append("Button Enabled Device Association");
		backCommand		= new Command("Back", Command.BACK, 1);
		okCommand		= new Command("OK", Command.OK, 1);
		bluetoothAlert	= new Alert("Bluetooth", "Searching for devices...", null, AlertType.INFO);
		bluetoothAlert.setTimeout(Alert.FOREVER);
		
		impl			= new J2MEButtonChannelImpl(display);
		devices			= new RemoteDevice[0];
		deviceList		= new List(DEVICE_LIST_TITLE, Choice.IMPLICIT);
		isInitiator		= false;
		currentChannel	= null;
		currentPeerAddress = "";
		random			= new Random(System.currentTimeMillis());
		
		buttonChannels = new Hashtable(10);
		OOBChannel c = new ButtonToButtonChannel(impl);
		buttonChannels.put(c.toString(), c);
		c = new FlashDisplayToButtonChannel(impl);
		buttonChannels.put(c.toString(), c);
		c = new ProgressBarToButtonChannel(impl);
		buttonChannels.put(c.toString(), c);
		c = new ShortVibrationToButtonChannel(impl);
		buttonChannels.put(c.toString(), c);
		c = new LongVibrationToButtonChannel(impl);
		buttonChannels.put(c.toString(), c);
		
		// Initialize the logger. Use a wrapper around the microlog framework.
		LogFactory.init(new MicrologFactory());
		logger = LogFactory.getLogger("org.openuat.apps.j2me.BedaMIDlet");
		// TODO: should configure microlog in its properties file
		net.sf.microlog.Logger nativeLogger = ((MicrologLogger)logger).getNativeLogger();
		nativeLogger.setLogLevel(net.sf.microlog.Level.DEBUG);
		FileAppender fileAppender = new FileAppender();
		fileAppender.setRootDir(FileAppender.DEFAULT_MEMORY_CARD_ROOT);
		nativeLogger.addAppender(fileAppender);
		logForm = new LogForm();
		logForm.setDisplay(display);
		logForm.setPreviousScreen(welcomeScreen);
		nativeLogger.addAppender(new net.sf.microlog.appender.FormAppender(logForm));
		logger.debug("Logger initialized!");
		
		// bluetooth initialization
		setUpPeerManager();
		try {
			btServer = new BluetoothRFCOMMServer(null, SERVICE_UUID, "UACAP-Beda", 30000, true, false);
			btServer.addAuthenticationProgressHandler(this);
			btServer.start();
			if (logger.isInfoEnabled()) {
				logger.info("Finished starting SDP service at " + btServer.getRegisteredServiceURL());
			}
		} catch (IOException e) {
			logger.error("Could not create bluetooth server.", e);
		}
		ProtocolCommandHandler inputProtocolHandler = new ProtocolCommandHandler() {
			// @Override
			public boolean handleProtocol(String firstLine, RemoteConnection remote) {
				if (logger.isDebugEnabled()) {
					logger.debug("Handle protocol command: " + firstLine);
				}
				// TODO: PRE_AUTH as constant
				if (firstLine.equals("PRE_AUTH")) {
					inputProtocol(false, remote, null);
					return true;
				}
				return false;
			}
		};
		btServer.addProtocolCommandHandler("PRE_AUTH", inputProtocolHandler);
		
		// create menu on welcome screen
		final Command exitCommand	= new Command("Exit", Command.EXIT, 1);
		final Command testCommand	= new Command("Test", "Test channels", Command.ITEM, 3);
		final Command searchCommand	= new Command("Search", "Search devices", Command.ITEM, 2);
		final Command logCommand	= new Command("Log", "Show log", Command.ITEM, 3);
		
		CommandListener listener = new CommandListener() {
			public void commandAction(Command c, Displayable d) {
				if (c == searchCommand) {
					//updateDeviceList();
					//display.setCurrent(deviceList);
					if (peerManager != null) {
						if (deviceList.size() > 0) {
							display.setCurrent(deviceList);
						}
						else {
							peerManager.startInquiry(false);
							display.setCurrent(bluetoothAlert);
						}
					}
				}
				else if (c == testCommand) {
					buildChannelList(true);
					logger.debug("Test channels...");
					display.setCurrent(channelList);
				}
				else if (c == exitCommand) {
					notifyDestroyed();
				}
				else if (c == logCommand) {
					display.setCurrent(logForm);
				}
			}
		};
		welcomeScreen.addCommand(exitCommand);
		welcomeScreen.addCommand(searchCommand);
		welcomeScreen.addCommand(testCommand);
		welcomeScreen.addCommand(logCommand);
		welcomeScreen.setCommandListener(listener);
		
		// launch gui
		display.setCurrent(welcomeScreen);
	}
	
	
	// @Override
	public void AuthenticationFailure(Object sender, Object remote, Exception e, String msg) {
		// in the input case, reset the shared key
		btServer.setPresharedShortSecret(null);
		logger.error(msg, e);
		alertError(msg);
	}
	
	// @Override
	public void AuthenticationProgress(Object sender, Object remote, int cur, int max, String msg) {
		// so this is nice... just keep going
	}
	
	// @Override
	public boolean AuthenticationStarted(Object sender, Object remote) {
		// not sure what to do here, just return true...
		return true;
	}

	// @Override
	public void AuthenticationSuccess(Object sender, Object remote, Object result) {
		logger.info("Authentication success event.");
        Object[] res = (Object[]) result;
        // remember the secret key shared with the other device
        byte[] sharedKey = (byte[]) res[0];
        // and extract the shared authentication key for phase 2
        byte[] authKey = (byte[]) res[1];
        // then extract the optional parameter
        String param = (String) res[2];
        if (logger.isInfoEnabled()) {
        	logger.info("Extracted session key of length " + sharedKey.length +
        		", authentication key of length " + (authKey != null ? authKey.length : 0) + 
        		" and optional parameter '" + param + "'");
        }
        RemoteConnection connectionToRemote = null;
        if (res.length > 3) {
        	connectionToRemote = (RemoteConnection) res[3];	
        }
        // TODO: authentication method as string constants
        if (param != null) {
	        if (param.equals("INPUT")) {
	        	// for input: authentication successful finished
	        	btServer.setPresharedShortSecret(null);
	        	logger.info("Authentication through input successful!");
	        	Alert successAlert = new Alert("Success", 
						"Authentication successful!", null, AlertType.CONFIRMATION);
				successAlert.setTimeout(Alert.FOREVER);
				display.setCurrent(successAlert, welcomeScreen);
	        }
	        else if (param.equals("TRANSFER_AUTH")) {
	        	byte[] oobMsg = getShortHash(authKey);
	        	if (oobMsg != null) {
	        		transferProtocol(isInitiator, connectionToRemote, currentChannel, oobMsg);
	        	}
	        }
        }
	}

	/* Helper method to initialize the channelList */
	private void buildChannelList(boolean isTest) {
		String[] listItems = {"Input", "Flash display", "Progressbar", "Short vibration", "Long vibration"};
		channelList = new List(CHANNEL_LIST_TITLE, Choice.IMPLICIT, listItems, null);
		channelList.addCommand(backCommand);
		final Command transmitCommand = new Command("Transmit", Command.ITEM, 2);
		final Command captureCommand = new Command("Capture", Command.ITEM, 2);
		
		if (isTest) {
			channelList.setSelectCommand(transmitCommand);
			channelList.addCommand(captureCommand);
		}
		else {
			channelList.setSelectCommand(okCommand);
		}
		
		CommandListener listener = new CommandListener() {
			public void commandAction(Command c, Displayable d) {
				if (c == backCommand) {
					display.setCurrent(welcomeScreen);
				}
				else {
					int index = channelList.getSelectedIndex();
					if (index > -1) {
						switch (index) {
						case 0:
							currentChannel = new ButtonToButtonChannel(impl);
							break;
						case 1:
							currentChannel = new FlashDisplayToButtonChannel(impl);
							break;
						case 2:
							currentChannel = new ProgressBarToButtonChannel(impl);
							break;
						case 3:
							currentChannel = new ShortVibrationToButtonChannel(impl);
							break;
						case 4:
							currentChannel = new LongVibrationToButtonChannel(impl);
							break;
						default:	
							currentChannel = null;
						}
					}
					if (c == okCommand && currentChannel != null) {
						if (index == 0) {
							// input case
							try {
								isInitiator = true;
								BluetoothRFCOMMChannel btChannel = new BluetoothRFCOMMChannel(currentPeerAddress, BLUETOOTH_CHANNEL_NR);
								btChannel.open();
								LineReaderWriter.println(btChannel.getOutputStream(), "PRE_AUTH");
								inputProtocol(true, btChannel, currentChannel);
							} catch (IOException e) {
								logger.error("Failed to start authentication.", e);
							}
						}
						else {
							// transfer case
							try {
								isInitiator = true;
								// TODO: service discovery etc
								String peerUrl = BluetoothPeerManager.getRemoteServiceURL(
										currentPeerAddress, SERVICE_UUID, ServiceRecord.NOAUTHENTICATE_NOENCRYPT, 10000);
								BluetoothRFCOMMChannel btChannel = new BluetoothRFCOMMChannel(peerUrl);
								// BluetoothRFCOMMChannel btChannel = new BluetoothRFCOMMChannel(currentPeerAddress, BLUETOOTH_CHANNEL_NR);
								btChannel.open();
								boolean keepConnected = true; // since the key has to be authenticated after key agreement
								// TODO: authentication method should be a constant
								HostProtocolHandler.startAuthenticationWith(btChannel, BedaMIDlet.this, -1, keepConnected, "TRANSFER_AUTH", false);
							} catch (IOException e) {
								logger.error("Failed to start authentication.", e);
							}
						}
					}
					else if (c == transmitCommand && currentChannel != null) {
						testTransmit(currentChannel);
					}
					else if (c == captureCommand && currentChannel != null) {
						testCapture(currentChannel);
					}
				}		
			}
		};
		channelList.setCommandListener(listener);
	}
	
	/* Helper method to update the list of all found devices */
	private void updateDeviceList() {
		if (peerManager != null) {
			devices = peerManager.getPeers();
		}
		final Command refreshCommand = new Command("Refresh list", Command.ITEM, 1);
		
		String[] listItems = new String[devices.length];
		for (int i = 0; i < devices.length; i++) {
			RemoteDevice device = devices[i];
			try {
				listItems[i] = device.getFriendlyName(false);
			} catch (IOException e) {
				logger.warn("Could not get name of a bluetooth device.", e);
				listItems[i] = "Unknown device";
			}
		}
		
		CommandListener listener = new CommandListener() {
			public void commandAction(Command c, Displayable d) {
				if (c == backCommand) {
					if (peerManager != null) {
						peerManager.stopInquiry(true);
					}
					display.setCurrent(welcomeScreen);
				}
				else if (c == okCommand) {
					if (peerManager != null) {
						peerManager.stopInquiry(true);
					}
					int index = deviceList.getSelectedIndex();
					if (index > -1 && index < devices.length) {
						currentPeerAddress = devices[index].getBluetoothAddress();
						buildChannelList(false);
						display.setCurrent(channelList);
					}
				}
				else if (c == refreshCommand) {
					peerManager.startInquiry(false);
					display.setCurrent(bluetoothAlert);
				}
			}
		};
		
		deviceList = new List(DEVICE_LIST_TITLE, Choice.IMPLICIT, listItems, null);
		deviceList.setSelectCommand(okCommand);
		deviceList.addCommand(backCommand);
		deviceList.addCommand(refreshCommand);
		deviceList.setCommandListener(listener);
		display.setCurrent(deviceList);
	}
	
	/* Helper method to set-up the bluetooth peer manager */
	private void setUpPeerManager() {
		BluetoothPeerManager.PeerEventsListener listener =
			new BluetoothPeerManager.PeerEventsListener() {
				public void inquiryCompleted(Vector newDevices) {
					updateDeviceList();
				}
				public void serviceSearchCompleted(RemoteDevice remoteDevice, Vector services, int errorReason) {
					// ignore
				}
		};
		
		try {
			peerManager = new BluetoothPeerManager();
			peerManager.addListener(listener);
		} catch (IOException e) {
			logger.error("Could not initiate BluetoothPeerManager.", e);
		}
	}
	
	/* Frees resources */
	private void cleanUp() {
		display 			= null;
		welcomeScreen 		= null;
		channelList 		= null;
		deviceList			= null;
		backCommand 		= null;
		okCommand			= null;
		bluetoothAlert		= null;
		impl 				= null;
		if (peerManager != null) {
			peerManager.stopInquiry(true);
		}
		peerManager			= null;
		devices 			= null;
		currentPeerAddress	= null;
		random 				= null;
		logger				= null;
		BluetoothRFCOMMChannel.shutdownAllChannels();
		btServer 			= null;
		buttonChannels		= null;
	}
	
	/* Test the transmit functionality (offline) */
	private void testTransmit(OOBChannel channel) {
		int r = random.nextInt();
		final byte[] randomMessage = {(byte)r, (byte)(r >>> 8), (byte)(r >>> 16)};
		final String hexString = getHexString(randomMessage);
		
		OOBMessageHandler handler = new OOBMessageHandler() {
			public void handleOOBMessage(int channelType, byte[] data) {
				if (data != null && data[0] == (byte)1) {
					if (logger.isInfoEnabled()) {
						logger.info("Transmission ok. Message: " + hexString);
					}
					Alert alert = new Alert("Transmission ok", 
							"The following message has been transmitted: " + hexString,
							null, AlertType.INFO);
					alert.setTimeout(Alert.FOREVER);
					display.setCurrent(alert, welcomeScreen);
				}
			}
		};
		
		channel.setOOBMessageHandler(handler);
		channel.transmit(randomMessage);
	}
	
	/* Test the capture functionality (offline) */
	private void testCapture(OOBChannel channel) {
		OOBMessageHandler handler = new OOBMessageHandler() {
			public void handleOOBMessage(int channelType, byte[] data) {
				if (logger.isInfoEnabled()) {
					logger.info("Capture ok. Message: " + getHexString(data));
				}
				Alert alert = new Alert("Capture ok", 
						"The following message has been captured: " + getHexString(data),
						null, AlertType.INFO);
				alert.setTimeout(Alert.FOREVER);
				display.setCurrent(alert, welcomeScreen);
			}
		};
		channel.setOOBMessageHandler(handler);
		channel.capture();
	}
	
	/* Convenience method to convert a byte[] to a hex string
	 * (since J2ME does not support the String.format() method) */
	private String getHexString(byte[] b) {
		String result = "";
		for (int i = 0; i < b.length; i++) {
			result += Integer.toString((b[i] & 0xff) + 0x100, 16).substring(1);
		}
		return result;
	}
	
	/* Informs the user of an error and return to main screen */
	private void alertError(String msg) {
		Alert a = new Alert("Error", msg, null, AlertType.ERROR);
		a.setTimeout(Alert.FOREVER);
		display.setCurrent(a, welcomeScreen);
	}
	
	/* 
	 * Takes the hash of the input and returns its first 3 bytes.
	 * The result is suitable to send over a button channel
	 */
	private byte[] getShortHash(byte[] bytes) {
		byte[] result = new byte[3];
    	try {
			byte[] hash = Hash.doubleSHA256(bytes, false);
			System.arraycopy(hash, 0, result, 0, 3);
		} catch (InternalApplicationException e) {
			logger.error("Could not create hash.", e);
			result = null;
		}
		return result;
	}

	/* 
	 * Runs a transfer protocol over an authenticated out-of-band channel
	 * and verifies the provided short string.
	 * 
	 * Protocol: I = initiator, R = responder
	 * I    --- "TRANSFER_AUTH:<channel_id>" -->    R
	 *      <--        "READY"               ---
	 *      o--         oobMsg               -->
	 *      <--         "DONE"               ---
	 *      <--         ack/nack             --o
	 * 
	 */
	private void transferProtocol(boolean isInitiator, final RemoteConnection connection, OOBChannel channel, final byte[] oobMsg) {
		this.isInitiator = false; // reset, so we are ready for further pairing attempts.
		final String TRANSFER_AUTH	= "TRANSFER_AUTH";
		final String READY			= "READY";
		final String DONE			= "DONE";
		final String ABORT			= "ABORT";
		
		final InputStream in;
		final OutputStream out;
		try {
			in = connection.getInputStream();
			out = connection.getOutputStream();
		} catch (IOException e) {
			logger.error("Failed to open stream from connection. Abort transfer protocol.", e);
			return;
		}
		
		if (isInitiator) {
			logger.debug("Running transfer as initiator");
			try {
				String initString = TRANSFER_AUTH + ":" + channel.toString();
				LineReaderWriter.println(out, initString);
				String lineIn = LineReaderWriter.readLine(in);
				if (lineIn.equals(READY)) {
					channel.transmit(oobMsg);
					// wait for other device
					lineIn = LineReaderWriter.readLine(in);
					if (lineIn.equals(DONE)) {
						Form successFeedback = new Form("Authentication");
						successFeedback.append("Was the other device successful?");
						final Command yesCommand	= new Command("Yes", Command.OK, 1);
						final Command noCommand		= new Command("No", Command.CANCEL, 1);
						successFeedback.addCommand(yesCommand);
						successFeedback.addCommand(noCommand);
						CommandListener listener = new CommandListener() {
							//@Override
							public void commandAction(Command command, Displayable d) {
								if (command == yesCommand) {
									Alert a = new Alert("Success", "Successfully paired with other device!", null, AlertType.CONFIRMATION);
									a.setTimeout(Alert.FOREVER);
									display.setCurrent(a, welcomeScreen);
								}
								else if (command == noCommand) {
									alertError("Authentication failed");
								}
								connection.close();
							}
						};
						successFeedback.setCommandListener(listener);
						display.setCurrent(successFeedback);
					}
					else {
						logger.error("Unexpected protocol string from remote device. Abort transfer protocol.");
					}
				}
				else {
					logger.error("Unexpected protocol string from remote device. Abort transfer protocol.");
				}
			} catch (IOException e) {
				logger.error("Failed to read/write from io stream. Abort transfer protocol.");
			}
		}
		else { // responder
			logger.debug("Running transfer as responder");
			try {
				String lineIn = LineReaderWriter.readLine(in);
				String protocolDesc = lineIn.substring(0, lineIn.indexOf(':'));
				String channelDesc = lineIn.substring(lineIn.indexOf(':') + 1);
				if (protocolDesc.equals(TRANSFER_AUTH)) {
					// get appropriate channel
					OOBChannel captureChannel = (OOBChannel)buttonChannels.get(channelDesc);
					OOBMessageHandler messageHandler = new OOBMessageHandler() {
						// @Override
						public void handleOOBMessage(int channelType, byte[] data) {
							try {
								LineReaderWriter.println(out, DONE);
								// check data
								if (logger.isInfoEnabled()) {
									logger.info("sent oobMsg: " + getHexString(oobMsg) +
											" received oobMsg: " + getHexString(data));
								}
								// compare the byte arrays as hex strings... (since no Arrays class in J2ME)
								if (getHexString(data).equals(getHexString(oobMsg))) {
									Alert successAlert = new Alert("Success", 
											"Authentication successful! Please report to the other device.",
											null, AlertType.CONFIRMATION);
									successAlert.setTimeout(Alert.FOREVER);
									display.setCurrent(successAlert, welcomeScreen);
								}
								else {
									alertError("Authentication failed! Please report to the other device");
								}
							} catch (IOException e) {
								logger.error("Failed to read/write to io stream. Abort transfer protocol.");
							}
							finally {
								connection.close();
							}
						}
					};
					captureChannel.setOOBMessageHandler(messageHandler);
					LineReaderWriter.println(out, READY);
					captureChannel.capture();
				}
				else {
					logger.error("Wrong protocol descriptor from remote device. Abort transfer protocol.");
				}
			} catch (IOException e) {
				logger.error("Failed to read/write from io stream. Abort transfer protocol.", e);
			}
		}
	}
	
	/*
	 * Runs an input protocol over a secure out-of-band channel.
	 * Key verification will be handled afterwards through a MANA III variant
	 * in the HostProtocolHandler class.
	 * 
	 * Protocol: I = initiator, R = responder, H = human user
	 * I    ---  "INPUT:<channel_id>"   -->    R
	 *      o<-   s    --o H o--   s    ->o
	 *      ---         "DONE"          -->
	 *      <--         "DONE"          ---
	 * 
	 */
	private void inputProtocol(boolean isInitiator, final RemoteConnection connection, OOBChannel channel) {
		this.isInitiator = false; // reset, so we are ready for further pairing attempts.
		final String INPUT	= "INPUT";
		final String READY	= "READY";
		final String DONE	= "DONE";
		final String ABORT	= "ABORT";
		
		final InputStream in;
		final OutputStream out;
		try {
			in = connection.getInputStream();
			out = connection.getOutputStream();
		} catch (IOException e) {
			logger.error("Failed to open stream from connection. Abort input protocol.", e);
			return;
		}
		
		if (isInitiator) {
			logger.debug("Running input as initiator");
			try {
				String initString = INPUT + ":" + channel.toString();
				LineReaderWriter.println(out, initString);
				String lineIn = LineReaderWriter.readLine(in);
				if (lineIn.equals(READY)) {
					OOBMessageHandler messageHandler = new OOBMessageHandler() {
						// @Override
						public void handleOOBMessage(int channelType, byte[] data) {
							logger.debug("Data captured: " + new String(Hex.encodeHex(data)));
							try {
								LineReaderWriter.println(out, DONE);
								String line = LineReaderWriter.readLine(in);
								if (line.equals(DONE)) {
									connection.close();
									BluetoothRFCOMMChannel btChannel = new BluetoothRFCOMMChannel(currentPeerAddress, BLUETOOTH_CHANNEL_NR);
									btChannel.open();
									HostProtocolHandler.startAuthenticationWith(btChannel, BedaMIDlet.this, null, data, null, 20000, false, "INPUT", false);
									//HostProtocolHandler.startAuthenticationWith(connection, BedaMIDlet.this, null, null, null, 20000, false, "INPUT", false);
									display.setCurrent(welcomeScreen);
								}
								else {
									logger.error("Unexpected protocol string from remote device. Abort input protocol.");
								}
							} catch (IOException e) {
								logger.error("Failed to read/write from io stream. Abort input protocol.", e);
							}
						}
					};
					channel.setOOBMessageHandler(messageHandler);
					channel.capture();
				}
				else {
					logger.error("Unexpected protocol string from remote device. Abort input protocol.");
				}
			} catch (IOException e) {
				logger.error("Failed to read/write from io stream. Abort input protocol.", e);
			}
		}
		else { // responder
			logger.debug("Running input as responder");
			try {
				String lineIn = LineReaderWriter.readLine(in);
				String protocolDesc = lineIn.substring(0, lineIn.indexOf(':'));
				String channelDesc = lineIn.substring(lineIn.indexOf(':') + 1);
				if (protocolDesc.equals(INPUT)) {
					// get appropriate channel
					OOBChannel captureChannel = (OOBChannel)buttonChannels.get(channelDesc);
					OOBMessageHandler messageHandler = new OOBMessageHandler() {
						// @Override
						public void handleOOBMessage(int channelType, byte[] data) {
							logger.debug("Data captured: " + new String(Hex.encodeHex(data)));
							try {
								String line = LineReaderWriter.readLine(in);
								if (line.equals(DONE)) {
									// prepare server to handle incoming request
									btServer.setPresharedShortSecret(data);
									LineReaderWriter.println(out, DONE);
									//connection.close();
									display.setCurrent(welcomeScreen);
								}
								else {
									logger.error("Unexpected protocol string from remote device. Abort input protocol.");
								}
							} catch (IOException e) {
								logger.error("Failed to read/write from io stream. Abort input protocol.", e);
							}
						}
					};
					captureChannel.setOOBMessageHandler(messageHandler);
					captureChannel.capture();
					LineReaderWriter.println(out, READY);
				}
				else {
					logger.error("Wrong protocol descriptor from remote device. Abort input protocol.");
				}
			} catch (IOException e) {
				logger.error("Failed to read/write from io stream. Abort input protocol.", e);
			}
		}
	}

}
