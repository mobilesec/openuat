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
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.ImageItem;
import javax.microedition.lcdui.Item;
import javax.microedition.lcdui.List;
import javax.microedition.midlet.MIDlet;
import javax.microedition.midlet.MIDletStateChangeException;

import net.sf.microlog.Level;
import net.sf.microlog.appender.FileAppender;
import net.sf.microlog.appender.FormAppender;
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
import org.openuat.channel.oob.PowerBarToButtonChannel;
import org.openuat.channel.oob.ProgressBarToButtonChannel;
import org.openuat.channel.oob.ShortVibrationToButtonChannel;
import org.openuat.channel.oob.TrafficLightToButtonChannel;
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
	
	/* The authentication service uuid... */
	private static final UUID SERVICE_UUID = new UUID("447d8ecbefea4b2d93107ced5d1bba7e", false);
	
	/* ...and it's name */
	private static final String SERVICE_NAME = "UACAP - Beda";
	
	/* An identifier used to register a command handler with the RFCOMMServer */
	private static final String PRE_AUTH = "PRE_AUTH";
	
	/* Authentication method supported by BEDA: authentic transfer */
	private static final String TRANSFER_AUTH = "TRANSFER_AUTH";
	
	/* Authentication method supported by BEDA: input */
	private static final String INPUT = "INPUT";
	
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
	
	/* Simple icons (showing a button) in the colors red, yellow and green */
	private Image errorIcon;
	private Image warnIcon;
	private Image successIcon;
	
	/* Simple icon (showing a button) used as list 'bullets' */
	private Image listIcon;

	/* Enables the user to go back to the last screen */
	private Command backCommand;
	
	/* Standard OK command */
	private Command okCommand;
	
	/* Scans for bluetooth devices around */
	private BluetoothPeerManager peerManager;
	
	/* Bluetooth server to advertise our services and accept incoming connections */
	private BluetoothRFCOMMServer btServer;
	
	/* Is this device the initiator of the verification protocol? */
	private boolean isInitiator;
	
	/* A collection of found devices */
	private RemoteDevice[] devices;
	
	/* Url of the authentication service on other device */
	private String currentPeerUrl;
	
	/* Remember the currently used button channel */
	private OOBChannel currentChannel;
	
	/* Keep a mapping of the different button channels with their names */
	private Hashtable buttonChannels;
	
	/* Random number generator to build random messages (for testing) */
	private Random random;
	
	/* Remember the start time of a pairing attempt. Used for statistics logging. */
	private long startTime;
	
	/* Logger instance */
	private Log logger;
	
	/* Logger instance to log statistics data */
	private Log statisticsLogger;
	
	
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
		backCommand		= new Command("Back", Command.BACK, 1);
		okCommand		= new Command("OK", Command.OK, 1);
		
		devices			= new RemoteDevice[0];
		deviceList		= new List(DEVICE_LIST_TITLE, Choice.IMPLICIT);
		isInitiator		= false;
		currentChannel	= null;
		currentPeerUrl	= "";
		random			= new Random(System.currentTimeMillis());
		startTime		= 0L;
		
		// Initialize the logger. Use a wrapper around the microlog framework.
		LogFactory.init(new MicrologFactory());
		logger = LogFactory.getLogger("org.openuat.apps.j2me.BedaMIDlet");
		statisticsLogger = LogFactory.getLogger("statistics");
		
		// TODO: should configure microlog in its properties file
		FileAppender fileAppender = new FileAppender();
		fileAppender.setDirectory("Memory card/Others/");
		logForm = new LogForm();
		logForm.setDisplay(display);
		logForm.setPreviousScreen(welcomeScreen);
		FormAppender formAppender = new FormAppender(logForm);
		net.sf.microlog.Logger nativeLogger = ((MicrologLogger)logger).getNativeLogger();
		nativeLogger.addAppender(fileAppender);
		nativeLogger.addAppender(formAppender);
		nativeLogger = ((MicrologLogger)statisticsLogger).getNativeLogger();
		nativeLogger.addAppender(fileAppender);
		nativeLogger.addAppender(formAppender);
		nativeLogger.setLogLevel(Level.TRACE);
		
		// create icon images
		try {
			errorIcon = Image.createImage("/Button_Icon_Red_32.png");
		} catch (IOException ioe) {
			errorIcon = null;
			logger.warn("Could not create error icon", ioe);
		}
		try {
			warnIcon = Image.createImage("/Button_Icon_Yellow_32.png");
		} catch (IOException ioe) {
			warnIcon = null;
			logger.warn("Could not create warn icon", ioe);
		}
		try {
			successIcon = Image.createImage("/Button_Icon_Green_32.png");
		} catch (IOException ioe) {
			successIcon = null;
			logger.warn("Could not create success icon", ioe);
		}
		try {
			listIcon = Image.createImage("/Button_Icon_Blue_12.png");
		} catch (IOException ioe) {
			listIcon = null;
			logger.warn("Could not create list icon", ioe);
		}
		
		// build button channels
		CommandListener abortHandler = new CommandListener() {
			public void commandAction(Command command, Displayable displayable) {
				if (command.getCommandType() == Command.STOP) {
					// TODO: cleanly stop protocol runs, channels etc.
					logger.warn("Protocol run aborted by user.");
					statisticsEnd(currentChannel.toString(), false);
					BluetoothRFCOMMChannel.shutdownAllChannels();
					alertError("Protocol run aborted.");
				}
				else {
					logger.warn("Command not handled: " + command.getLabel());
				}
			}
		};
		ButtonChannelImpl impl = new J2MEButtonChannelImpl(display, abortHandler);
		buttonChannels = new Hashtable(10);
		OOBChannel c = new ButtonToButtonChannel(impl);
		buttonChannels.put(c.toString(), c);
		c = new FlashDisplayToButtonChannel(impl);
		buttonChannels.put(c.toString(), c);
		c = new TrafficLightToButtonChannel(impl);
		buttonChannels.put(c.toString(), c);
		c = new ProgressBarToButtonChannel(impl);
		buttonChannels.put(c.toString(), c);
		c = new PowerBarToButtonChannel(impl);
		buttonChannels.put(c.toString(), c);
		c = new ShortVibrationToButtonChannel(impl);
		buttonChannels.put(c.toString(), c);
		c = new LongVibrationToButtonChannel(impl);
		buttonChannels.put(c.toString(), c);
		
		// bluetooth initialization
		setUpBluetooth();
		
		// build welcome screen: create menu
		final Command exitCommand	= new Command("Exit", Command.EXIT, 1);
		final Command searchCommand = new Command("Search", "Search devices", Command.ITEM, 2);
		final Command testCommand	= new Command("Test", "Test channels", Command.ITEM, 3);
		final Command logCommand	= new Command("Log", "Show log", Command.ITEM, 3);
		
		CommandListener listener = new CommandListener() {
			public void commandAction(Command cmd, Displayable d) {
				if (cmd == searchCommand) {
					if (peerManager != null) {
						if (deviceList.size() > 0) {
							display.setCurrent(deviceList);
						}
						else {
							peerManager.startInquiry(false);
							alertWait("Searching for devices...", false);
						}
					}
				}
				else if (cmd == testCommand) {
					buildChannelList(true);
					logger.debug("Test channels...");
					display.setCurrent(channelList);
				}
				else if (cmd == exitCommand) {
					notifyDestroyed();
				}
				else if (cmd == logCommand) {
					display.setCurrent(logForm);
				}
			}
		};
		welcomeScreen.addCommand(exitCommand);
		welcomeScreen.addCommand(searchCommand);
		welcomeScreen.addCommand(testCommand);
		welcomeScreen.addCommand(logCommand);
		welcomeScreen.setCommandListener(listener);
		
		// build welcome screen: create form elements
		try {
			Image logo = Image.createImage("/Button_Icon_Blue_beda.png");
			welcomeScreen.append(new ImageItem(null, logo, Item.LAYOUT_CENTER, null));
		} catch (IOException e) {
			logger.warn("Could not create beda logo", e);
		}
		welcomeScreen.append("\nButton Enabled Device Association");
		
		// launch gui
		display.setCurrent(welcomeScreen);
	}
	
	
	// @Override
	public void AuthenticationFailure(Object sender, Object remote, Exception e, String msg) {
		// in the input case, reset the shared key
		btServer.setPresharedShortSecret(null);
		logger.error(msg, e);
    	if (currentChannel != null) {
    		// log session duration only on initiator
    		// on responder, currentChannel is null
    		statisticsEnd(currentChannel.toString(), false);
    	}
		alertError(msg);
	}
	
	// @Override
	public void AuthenticationProgress(Object sender, Object remote, int cur, int max, String msg) {
		// so this is nice... just keep going
	}
	
	// @Override
	public boolean AuthenticationStarted(Object sender, Object remote) {
		// not interested in it, just return true...
		return true;
	}

	// @Override
	public void AuthenticationSuccess(Object sender, Object remote, Object result) {
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
        if (param != null) {
	        if (param.equals(INPUT)) {
	        	// for input: authentication successfully finished!
	        	if (currentChannel != null) {
	        		// log session duration only on initiator
	        		// on responder, currentChannel is null
	        		statisticsEnd(currentChannel.toString(), true);
	        	}
	        	btServer.setPresharedShortSecret(null);
	        	logger.info("Authentication through input successful!");
	        	Alert successAlert = new Alert("Success", 
						"Authentication successful!", successIcon, AlertType.CONFIRMATION);
				successAlert.setTimeout(Alert.FOREVER);
				display.setCurrent(successAlert, welcomeScreen);
	        }
	        else if (param.equals(TRANSFER_AUTH)) {
	        	byte[] oobMsg = getShortHash(authKey);
	        	if (oobMsg != null) {
	        		transferProtocol(isInitiator, connectionToRemote, currentChannel, oobMsg);
	        	}
	        }
        }
	}

	/* Helper method to initialize the channelList */
	private void buildChannelList(boolean isTest) {
		// TODO: get channels from existing map
		String[] listItems = {"Input", "Flash Display", "Traffic Light", "Progress Bar", "Power Bar", "Short Vibration", "Long Vibration"};
		Image[] imageItems = {listIcon, listIcon, listIcon, listIcon, listIcon, listIcon, listIcon};
		channelList = new List(CHANNEL_LIST_TITLE, Choice.IMPLICIT, listItems, imageItems);
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
						String channelDesc = channelList.getString(index);
						currentChannel = (OOBChannel)buttonChannels.get(channelDesc);
					}
					if (c == okCommand && currentChannel != null) {
						alertWait("Prepare authentication...", false);
						// start protocols in new thread, so the gui can be updated
						Thread thread = new Thread(new Runnable(){
							public void run() {
								startAuthentication();
							}
						});
						thread.start();
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
		
		deviceList = new List(DEVICE_LIST_TITLE, Choice.IMPLICIT);
		for (int i = 0; i < devices.length; i++) {
			RemoteDevice device = devices[i];
			try {
				String deviceName = device.getFriendlyName(false);
				deviceList.append(deviceName, listIcon);
			} catch (IOException e) {
				logger.warn("Could not get name of a bluetooth device.", e);
				deviceList.append("Unknown device", listIcon);
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
					final int index = deviceList.getSelectedIndex();
					if (index > -1 && index < devices.length) {
						alertWait("Searching for authenticaton service...", false);
						// search for service in a separate thread, so the gui can be updated
						Thread thread = new Thread(new Runnable(){
							public void run() {
								searchForService();
							}
						});
						thread.start();
					}
				}
				else if (c == refreshCommand) {
					peerManager.startInquiry(false);
					alertWait("Searching for devices...", false);
				}
			}
		};
		
		deviceList.setSelectCommand(okCommand);
		deviceList.addCommand(backCommand);
		deviceList.addCommand(refreshCommand);
		deviceList.setCommandListener(listener);
		display.setCurrent(deviceList);
	}
	
	/* Helper method to set-up the bluetooth facilities */
	private void setUpBluetooth() {
		// peer manager
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
		
		// bluetooth server
		try {
			btServer = new BluetoothRFCOMMServer(null, SERVICE_UUID, SERVICE_NAME, 30000, true, false);
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
				if (firstLine.equals(PRE_AUTH)) {
					inputProtocol(false, remote, null);
					return true;
				}
				return false;
			}
		};
		btServer.addProtocolCommandHandler(PRE_AUTH, inputProtocolHandler);
	}
	
	/* Initiates the authentication protocol. */
	private void startAuthentication() {
		try {
			isInitiator = true;
			BluetoothRFCOMMChannel btChannel = new BluetoothRFCOMMChannel(currentPeerUrl);
			btChannel.open();
			statisticsStart(currentChannel.toString());
			if (currentChannel.toString().equals("Input")) {
				// input case
				String hello = LineReaderWriter.readLine(btChannel.getInputStream());
				if (!hello.equals(HostProtocolHandler.Protocol_Hello)) {
					logger.warn("Got wrong greeting string from server. This probably leads to protocol failure.");
				}
				LineReaderWriter.println(btChannel.getOutputStream(), PRE_AUTH);
				inputProtocol(true, btChannel, currentChannel);
			}
			else {
				// transfer case
				boolean keepConnected = true; // since the key has to be authenticated after key agreement
				HostProtocolHandler.startAuthenticationWith(btChannel, BedaMIDlet.this, -1, keepConnected, TRANSFER_AUTH, false);
			}
		} catch (IOException e) {
			logger.error("Failed to start authentication.", e);
		}
	}
	
	/* 
	 * Searches for the Beda service on currentPeerAddress 
	 * and launches the channel list if the service is available.
	 */
	private void searchForService() {
		int index = deviceList.getSelectedIndex();
		currentPeerUrl = null;
		String currentPeerAddress = devices[index].getBluetoothAddress();
		try {
			currentPeerUrl = BluetoothPeerManager.getRemoteServiceURL(
					currentPeerAddress, SERVICE_UUID, ServiceRecord.NOAUTHENTICATE_NOENCRYPT, 10000);
		} catch (IOException e) {
			currentPeerUrl = null;
		}
		if (currentPeerUrl != null) {
			buildChannelList(false);
			display.setCurrent(channelList);
		}
		else {
			Alert a = new Alert("Warning", 
					"Authentication service " + SERVICE_NAME + " is not currently available on this device.", 
					warnIcon, AlertType.WARNING);
			a.setTimeout(Alert.FOREVER);
			display.setCurrent(a, deviceList);
		}
	}
	
	/* Test the transmit functionality (offline) */
	private void testTransmit(OOBChannel channel) {
		int r = random.nextInt();
		final byte[] randomMessage = {(byte)r, (byte)(r >>> 8), (byte)(r >>> 16)};
		final String hexString = new String(Hex.encodeHex(randomMessage));
		
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
					logger.info("Capture ok. Message: " + new String(Hex.encodeHex(data)));
				}
				Alert alert = new Alert("Capture ok", 
						"The following message has been captured: " + new String(Hex.encodeHex(data)),
						null, AlertType.INFO);
				alert.setTimeout(Alert.FOREVER);
				display.setCurrent(alert, welcomeScreen);
			}
		};
		channel.setOOBMessageHandler(handler);
		channel.capture();
	}
	
	/* Informs the user of an error and return to main screen */
	private void alertError(String msg) {
		Alert a = new Alert("Error", msg, errorIcon, AlertType.ERROR);
		a.setTimeout(Alert.FOREVER);
		display.setCurrent(a, welcomeScreen);
	}
	
	/* Places a "Please wait..." message on screen.
	 * Launch it while some background processing is done. */
	private void alertWait(String msg, boolean returnToHome) {
		Alert a = new Alert("Please wait...", msg, warnIcon, AlertType.INFO);
		a.setTimeout(Alert.FOREVER);
		if (returnToHome) {
			display.setCurrent(a, welcomeScreen);
		}
		else {
			display.setCurrent(a);
		}
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
	
	/* Log statistics info: start of a protocol run */
	private void statisticsStart(String desc) {
		if (statisticsLogger.isTraceEnabled()) {
			startTime = System.currentTimeMillis();
			statisticsLogger.trace("[STAT] START " + desc);
		}
	}
	
	/* Log statistics info: end of a protocol run */
	private void statisticsEnd(String desc, boolean success) {
    	if (statisticsLogger.isTraceEnabled() && startTime != 0L) {
    		long duration = System.currentTimeMillis() - startTime;
    		String result = success ? "success" : "failure";
    		statisticsLogger.trace("[STAT] END: " + result + " " +
    					desc + " - authentication duration in ms: " + duration);
    		startTime = 0L;
    	}
	}

	/* 
	 * Runs a transfer protocol over an authenticated out-of-band channel
	 * and verifies the provided short string.
	 * 
	 * Legend:
	 * I	initiator 
	 * R 	responder
	 * --->	insecure channel (bluetooth)
	 * o-->	authentic channel (out-of-band)
	 * 
	 * Protocol:
	 * I    --- "TRANSFER_AUTH:<channel_id>" -->    R
	 *      <--        "READY"               ---
	 *      o--         oobMsg               -->
	 *      <--         "DONE"               ---
	 *      <--         ack/nack             --o
	 * 
	 */
	private void transferProtocol(boolean isInitiator, final RemoteConnection connection, OOBChannel channel, final byte[] oobMsg) {
		this.isInitiator = false; // reset, so we are ready for further pairing attempts.
		final String READY	= "READY";
		final String DONE	= "DONE";
		
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
				if (!lineIn.equals(READY)) {
					logger.error("Unexpected protocol string from remote device. Abort transfer protocol.");
					return;
				}
				channel.transmit(oobMsg);
				// wait for other device
				lineIn = LineReaderWriter.readLine(in);
				if (!lineIn.equals(DONE)) {
					logger.error("Unexpected protocol string from remote device. Abort transfer protocol.");
					return;
				}
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
							Alert a = new Alert("Success", "Successfully paired with other device!",
									successIcon, AlertType.CONFIRMATION);
							a.setTimeout(Alert.FOREVER);
							display.setCurrent(a, welcomeScreen);
						}
						else if (command == noCommand) {
							alertError("Authentication failed");
						}
						statisticsEnd(currentChannel.toString(), command == yesCommand);
						connection.close();
					}
				};
				successFeedback.setCommandListener(listener);
				display.setCurrent(successFeedback);
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
				if (!protocolDesc.equals(TRANSFER_AUTH)) {
					logger.error("Wrong protocol descriptor from remote device. Abort transfer protocol.");
					return;
				}
				// get appropriate channel
				OOBChannel captureChannel = (OOBChannel)buttonChannels.get(channelDesc);
				OOBMessageHandler messageHandler = new OOBMessageHandler() {
					// @Override
					public void handleOOBMessage(int channelType, byte[] data) {
						try {
							LineReaderWriter.println(out, DONE);
							// check data
							if (logger.isInfoEnabled()) {
								logger.info("sent oobMsg: " + new String(Hex.encodeHex(oobMsg)) +
										" received oobMsg: " + new String(Hex.encodeHex(data)));
							}
							// compare the byte arrays as hex strings... (since no Arrays class in J2ME)
							if ((new String(Hex.encodeHex(data))).equals(new String(Hex.encodeHex(oobMsg)))) {
								Alert successAlert = new Alert("Success", 
										"Authentication successful! Please report to the other device.",
										successIcon, AlertType.CONFIRMATION);
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
	 * Legend:
	 * I	initiator
	 * R	responder
	 * H	human user
	 * --->	insecure channel (bluetooth)
	 * o->o secure channel, both authentic and confidential (out-of-band)
	 * 
	 * Protocol:
	 * I    ---  "INPUT:<channel_id>"   -->    R
	 *      o<-   s    --o H o--   s    ->o
	 *      ---         "DONE"          -->
	 *      <--         "DONE"          ---
	 * 
	 */
	private void inputProtocol(boolean isInitiator, final RemoteConnection connection, OOBChannel channel) {
		this.isInitiator = false; // reset, so we are ready for further pairing attempts.
		final String READY	= "READY";
		final String DONE	= "DONE";
		
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
				if (!lineIn.equals(READY)) {
					logger.error("Unexpected protocol string from remote device. Abort input protocol.");
					return;
				}
				OOBMessageHandler messageHandler = new OOBMessageHandler() {
					// @Override
					public void handleOOBMessage(int channelType, byte[] data) {
						if (statisticsLogger.isTraceEnabled()) {
							statisticsLogger.trace("[STAT] Data captured: " + new String(Hex.encodeHex(data)));
						}
						try {
							LineReaderWriter.println(out, DONE);
							String line = LineReaderWriter.readLine(in);
							if (!line.equals(DONE)) {
								logger.error("Unexpected protocol string from remote device. Abort input protocol.");
								return;
							}
							connection.close();
							alertWait("Authentication in progress...", true);
							BluetoothRFCOMMChannel btChannel = new BluetoothRFCOMMChannel(currentPeerUrl);
							btChannel.open();
							HostProtocolHandler.startAuthenticationWith(
									btChannel, BedaMIDlet.this, null, data, null, 20000, false, "INPUT", false);
						} catch (IOException e) {
							logger.error("Failed to read/write from io stream. Abort input protocol.", e);
						}
					}
				};
				channel.setOOBMessageHandler(messageHandler);
				channel.capture();
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
				if (!protocolDesc.equals(INPUT)) {
					logger.error("Wrong protocol descriptor from remote device. Abort input protocol.");
					return;
				}
				// get appropriate channel
				OOBChannel captureChannel = (OOBChannel)buttonChannels.get(channelDesc);
				OOBMessageHandler messageHandler = new OOBMessageHandler() {
					// @Override
					public void handleOOBMessage(int channelType, byte[] data) {
						if (statisticsLogger.isTraceEnabled()) {
							statisticsLogger.trace("[STAT] Data captured: " + new String(Hex.encodeHex(data)));
						}
						try {
							String line = LineReaderWriter.readLine(in);
							if (!line.equals(DONE)) {
								logger.error("Unexpected protocol string from remote device. Abort input protocol.");
								return;
							}
							// prepare server to handle incoming request
							btServer.setPresharedShortSecret(data);
							LineReaderWriter.println(out, DONE);
							//connection.close();
							alertWait("Authentication in progress...", true);
						} catch (IOException e) {
							logger.error("Failed to read/write from io stream. Abort input protocol.", e);
						}
					}
				};
				captureChannel.setOOBMessageHandler(messageHandler);
				captureChannel.capture();
				LineReaderWriter.println(out, READY);
			} catch (IOException e) {
				logger.error("Failed to read/write from io stream. Abort input protocol.", e);
			}
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
		if (peerManager != null) {
			peerManager.stopInquiry(true);
		}
		peerManager			= null;
		devices 			= null;
		currentPeerUrl		= null;
		random 				= null;
		logger				= null;
		BluetoothRFCOMMChannel.shutdownAllChannels();
		btServer 			= null;
		buttonChannels		= null;
	}

}
