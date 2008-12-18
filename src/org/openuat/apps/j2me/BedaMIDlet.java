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
import java.util.Random;
import java.util.Vector;

import javax.bluetooth.RemoteDevice;
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

import org.openuat.authentication.OOBChannel;
import org.openuat.authentication.OOBMessageHandler;
import org.openuat.channel.oob.ButtonChannel;
import org.openuat.channel.oob.ButtonChannelImpl;
import org.openuat.channel.oob.ButtonToButtonChannel;
import org.openuat.channel.oob.FlashDisplayToButtonChannel;
import org.openuat.channel.oob.LongVibrationToButtonChannel;
import org.openuat.channel.oob.ProgressBarToButtonChannel;
import org.openuat.channel.oob.ShortVibrationToButtonChannel;
import org.openuat.channel.oob.j2me.J2MEButtonChannelImpl;
import org.openuat.util.BluetoothPeerManager;

/**
 * This MIDlet demonstrates the different button channels within the OpenUAT
 * toolkit on the J2ME platform. Most of the channels are described in 
 * 'BEDA: Button-Enabled Device Association' by C. Soriente and G. Tsudik.
 * However, there are possibly additional channels implemented here.
 * 
 * @author Lukas Huser
 * @version 1.0
 */
public class BedaMIDlet extends MIDlet {

	/**
	 * Creates a new MIDlet. Used for one-time initializations.
	 */
	public BedaMIDlet() {
		super();
	}
	
	/*
	 * GUI stuff
	 */
	
	/* string constants */
	private static final String DEVICE_LIST_TITLE = "Choose a device";
	private static final String CHANNEL_LIST_TITLE = "Choose a channel";
	
	/* Current display */
	private Display display;
	
	/* Welcome the user to the BedaMIDlet! */
	private Form welcomeScreen;
	
	/* A list of all available button channels */
	private List channelList;
	
	/* A list of all found bluetooth devices */
	private List deviceList;

	/* Enables the user to go back to the last screen */
	private Command backCommand;
	
	/* Standard OK command */
	private Command okCommand;
	
	/* Display while searching for bluetooth devices */
	private Alert bluetoothAlert;
	
	/*
	 * Channel related stuff
	 */
	
	/* Needed to build the various button channels */
	private ButtonChannelImpl impl;
	
	/* Scans for bluetooth devices around */
	private BluetoothPeerManager peerManager;
	
	/* A collection of found devices */
	private RemoteDevice[] devices;
	
	/* Bluetooth address of the current peer */
	private String currentPeerAddress;
	
	/* Random number generator to build random messages (for testing) */
	private Random random;
	
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
		// initialization
		display = Display.getDisplay(this);
		welcomeScreen = new Form("BEDA MIDlet");
		welcomeScreen.append("Button Enabled Device Association");
		backCommand = new Command("Back", Command.BACK, 1);
		okCommand = new Command("OK", Command.OK, 1);
		bluetoothAlert = new Alert("Bluetooth", "Searching for devices...", null, AlertType.INFO);
		bluetoothAlert.setTimeout(Alert.FOREVER);
		
		impl = new J2MEButtonChannelImpl(display);
		devices = new RemoteDevice[0];
		deviceList = new List(DEVICE_LIST_TITLE, Choice.IMPLICIT);
		currentPeerAddress = "";
		random = new Random(System.currentTimeMillis());
		
		// some more complex initialization
		setUpPeerManager();
		
		// create menu on welcome screen
		final Command exitCommand = new Command("Exit", Command.EXIT, 1);
		final Command testCommand = new Command("Test", "Test channels", Command.ITEM, 3);
		final Command searchCommand = new Command("Search", "Search devices", Command.ITEM, 2);
		
		CommandListener listener = new CommandListener() {
			public void commandAction(Command c, Displayable d) {
				if (c == searchCommand) {
					//updateDeviceList();
					//display.setCurrent(deviceList);
					if (peerManager != null) {
						peerManager.startInquiry(false);
						display.setCurrent(bluetoothAlert);
					}
				}
				else if (c == testCommand) {
					buildChannelList(true);
					display.setCurrent(channelList);
				}
				else if (c == exitCommand) {
					notifyDestroyed();
				}
			}
		};
		welcomeScreen.addCommand(exitCommand);
		welcomeScreen.addCommand(searchCommand);
		welcomeScreen.addCommand(testCommand);
		welcomeScreen.setCommandListener(listener);
		
		// launch gui
		display.setCurrent(welcomeScreen);
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
					ButtonChannel channel = null;
					int index = channelList.getSelectedIndex();
					if (index > -1) {
						switch (index) {
						case 0:
							channel = new ButtonToButtonChannel(impl);
							break;
						case 1:
							channel = new FlashDisplayToButtonChannel(impl);
							break;
						case 2:
							channel = new ProgressBarToButtonChannel(impl);
							break;
						case 3:
							channel = new ShortVibrationToButtonChannel(impl);
							break;
						case 4:
							channel = new LongVibrationToButtonChannel(impl);
							break;
						default:	
							channel = null;
						}
					}
					if (c == okCommand && channel != null) {
						// TODO
						//startProtocol(channel);
					}
					else if (c == transmitCommand && channel != null) {
						testTransmit(channel);
					}
					else if (c == captureCommand && channel != null) {
						testCapture(channel);
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
				listItems[i] = device.getFriendlyName(false) + "(" + device.getBluetoothAddress() + ")";
			} catch (IOException e) {
				// TODO: log warning
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
			// TODO: log error
			// logger.error(e);
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
	}
	
	/* Test the transmit functionality (offline) */
	private void testTransmit(OOBChannel channel) {
		int r = random.nextInt();
		final byte[] randomMessage = {(byte)r, (byte)(r >>> 8), (byte)(r >>> 16)};
		final String hexString = getHexString(randomMessage);
		
		OOBMessageHandler handler = new OOBMessageHandler() {
			public void handleOOBMessage(int channelType, byte[] data) {
				if (data != null && data[0] == (byte)1) {
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


}
