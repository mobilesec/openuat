/* Copyright Rene Mayrhofer
 * File created 2008-06-25
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.apps.j2me;

import java.io.IOException;
import java.util.Vector;

import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;
import javax.bluetooth.*;

import net.sf.microlog.Level;
import net.sf.microlog.appender.FormAppender;
import net.sf.microlog.ui.LogForm;

import org.openuat.authentication.AuthenticationProgressHandler;
import org.openuat.authentication.HostProtocolHandler;
import org.openuat.authentication.exceptions.InternalApplicationException;
import org.openuat.log.Log;
import org.openuat.log.LogFactory;
import org.openuat.log.j2me.MicrologFactory;
import org.openuat.log.j2me.MicrologLogger;
import org.openuat.util.BluetoothPeerManager;
import org.openuat.util.BluetoothRFCOMMChannel;
import org.openuat.util.BluetoothRFCOMMServer;
import org.openuat.util.BluetoothSupport;
import org.openuat.util.Hash;
import org.openuat.util.LineReaderWriter;
import org.openuat.util.RemoteConnection;

/** This MIDlet demonstrates all three possible options for out-of-band peer
 * verification when using the UACAP protocol and "manual" authentication.
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class ManualAuthentication extends MIDlet implements CommandListener,
		BluetoothPeerManager.PeerEventsListener, AuthenticationProgressHandler {
	
	/**
	 * Manual authentication service identifier.
	 */
	public static final UUID SERVICE_UUID = new UUID("447d8ecbefea4b2d93107ced5d1bba7e", false);
	
	/* 
	 * Number of characters of the hash string (hex) that will be shown to the user.
	 * Used by the 'Hash Comparison' authentication method.
	 */
	private static final int HASH_STRING_LENGTH		= 12;
	
	/* Defines a channel for bluetooth connections */
	private static final int BLUETOOTH_CHANNEL_NR	= 5;
	
	private List main_list;

	private List dev_list;

	private List serv_list; 

	private Command exit;

	private Command back;

	private Command log;
	
	private Command auth;

	private Display display;
	
	private BluetoothPeerManager peerManager;
	
	private BluetoothRFCOMMServer rfcommServer;
	
	private String currentPeerAddress;
	
	/* The microlog LogForm */
	private LogForm logForm;
	

	// our logger
	private Log logger;
	
	public ManualAuthentication() {
		display = Display.getDisplay(this);
		currentPeerAddress = null;
		
		// problem with CRLF in microlog.properies? try unix2dos...
        /*try {
            GlobalProperties.init(this);
        } catch (IllegalStateException e) {
            //Ignore this exception. It is already initiated.
        }
		logger.configure(GlobalProperties.getInstance());*/
		
		LogFactory.init(new MicrologFactory());
		logger = LogFactory.getLogger("org.openuat.apps.j2me.ManualAuthentication");
		net.sf.microlog.Logger logBackend = ((MicrologLogger)logger).getNativeLogger();
		logForm = new LogForm();
		logForm.setDisplay(display);
		logBackend.addAppender(new FormAppender(logForm));
		//logBackend.addAppender(new RecordStoreAppender());
		logBackend.setLogLevel(Level.DEBUG);
		logger.info("Microlog initialized");
		
		if (! BluetoothSupport.init()) {
			do_alert("Could not initialize Bluetooth API", Alert.FOREVER);
			return;
		}

		try {
			rfcommServer = new BluetoothRFCOMMServer(null, SERVICE_UUID, "Manual Authentication", 
					10000, true, false);
			rfcommServer.addAuthenticationProgressHandler(this);
			rfcommServer.start();
			logger.info("Finished starting SDP service at " + rfcommServer.getRegisteredServiceURL());
		} catch (IOException e) {
			logger.error("Error initializing BlutoothRFCOMMServer: " + e);
		}

		try {
			peerManager = new BluetoothPeerManager();
			peerManager.addListener(this);
		} catch (IOException e) {
			logger.error("Error initializing BlutoothPeerManager: " + e);
			return;
		}

			main_list = new List("Select Operation", Choice.IMPLICIT); //the main menu
			dev_list = new List("Select Device", Choice.IMPLICIT); //the list of devices
			serv_list = new List("Available Services", Choice.IMPLICIT); //the list of services
			exit = new Command("Exit", Command.EXIT, 1);
			back = new Command("Back", Command.BACK, 1);
			log = new Command("Log", Command.ITEM, 2);

			main_list.addCommand(exit);
			main_list.addCommand(log);
			main_list.setCommandListener(this);
			dev_list.addCommand(exit);
			dev_list.addCommand(log);
			dev_list.setCommandListener(this);
			serv_list.addCommand(exit);
			serv_list.addCommand(back);
			serv_list.setCommandListener(this);

			main_list.append("Find Devices", null);
			main_list.append("Automatically pair to first compatible device", null);
	}

	// TODO: activate me again when J2ME polish can deal with Java5 sources!
	//@Override
	public void startApp() {
		logForm.setPreviousScreen(main_list);
		display.setCurrent(main_list);
	}

	public void commandAction(Command com, Displayable dis) {
		if (com == exit) { //exit triggered from the main form
			if (rfcommServer != null)
				try {
					rfcommServer.stop();
				} catch (InternalApplicationException e) {
					do_alert("Could not de-register SDP service: " + e, Alert.FOREVER);
				}
			destroyApp(false);
			notifyDestroyed();
		}
		else if (com == List.SELECT_COMMAND) {
			if (dis == main_list) { //select triggered from the main from
				if (main_list.getSelectedIndex() == 0) { //find devices
					if (!peerManager.startInquiry(false)) {
						this.do_alert("Error in initiating search", 4000);
					}
					do_alert("Searching for devices...", Alert.FOREVER);
				}
				else {
					// TODO: automatically use the first device we find
				}
			}
			if (dis == dev_list) { //select triggered from the device list
				if (dev_list.getSelectedIndex() >= 0) { //find services
					RemoteDevice[] devices = peerManager.getPeers();
					currentPeerAddress = devices[dev_list.getSelectedIndex()].getBluetoothAddress();
					if (logger.isDebugEnabled()) {
						logger.debug("currentPeerAddress set to " + currentPeerAddress);
					}
					serv_list.deleteAll(); //empty the list of services in case user has pressed back
					if (!peerManager.startServiceSearch(devices[dev_list.getSelectedIndex()], SERVICE_UUID)) {
						this.do_alert("Error in initiating search", 4000);
					}
					do_alert("Inquiring device for services...", Alert.FOREVER);
				}
			}
			if (dis == serv_list) {
				if (serv_list.getSelectedIndex() >= 0) {
					
					// TODO: ask for authentication type and direction
					
					boolean keepConnected = true;
					String optionalParam = null;
					
					// At the moment, only hash comparison is available
					try {
						BluetoothRFCOMMChannel channel = new BluetoothRFCOMMChannel(currentPeerAddress, BLUETOOTH_CHANNEL_NR);
						channel.open();
						logger.debug("Bluetooth channel opened, start DH key agreement.");
						HostProtocolHandler.startAuthenticationWith(channel, this, 10000, keepConnected, optionalParam, false);
					} catch (IOException e) {
						logger.error("Failed to open connection to peer.", e);
					}
				}
			}
		}
		else if (com == back) {
			if (dis == serv_list) { //back button is pressed in devices list
				display.setCurrent(dev_list);
			}
		}
		else if (com == log) {
			display.setCurrent(logForm);
		}

	}

	public void do_alert(String msg, int time_out) {
		if (display.getCurrent() instanceof Alert) {
			((Alert) display.getCurrent()).setString(msg);
			((Alert) display.getCurrent()).setTimeout(time_out);
		} else {
			Alert alert = new Alert("Bluetooth");
			alert.setString(msg);
			alert.setTimeout(time_out);
			display.setCurrent(alert);
		}
	}

	// TODO: activate me again when J2ME polish can deal with Java5 sources!
	//@Override
	public void pauseApp() {
		// nothing to do when the app is paused, leave the background actions running
	}

	// TODO: activate me again when J2ME polish can deal with Java5 sources!
	//@Override
	public void destroyApp(boolean unconditional) {
		// just try to close all channels to shutdown quickly, all other resources should be freed automatically
		BluetoothRFCOMMChannel.shutdownAllChannels();
	}

	public void inquiryCompleted(Vector newDevices) {
		for (int i=0; i<newDevices.size(); i++) {
			String device_name = BluetoothPeerManager.resolveName((RemoteDevice) newDevices.elementAt(i));
			this.dev_list.append(device_name, null);
			logForm.setPreviousScreen(dev_list);
			display.setCurrent(dev_list);
		}
	}

	public void serviceSearchCompleted(RemoteDevice remoteDevice, Vector services, int errorReason) {
		if (errorReason == BluetoothPeerManager.PeerEventsListener.SEARCH_COMPLETE) {
			for (int x = 0; x < services.size(); x++) {
				try {
					DataElement ser_de = ((ServiceRecord) services.elementAt(x))
							.getAttributeValue(0x100);
					String service_name = (String) ser_de.getValue();
					serv_list.append(service_name, null);
				} catch (Exception e) {
					do_alert("Error in adding services ", 1000);
				}
			}
			display.setCurrent(serv_list);
		}
		else {
			String errorMsg = "unknown error code!";
			switch (errorReason) {
				case BluetoothPeerManager.PeerEventsListener.DEVICE_NOT_REACHABLE:
					errorMsg = "Device " + remoteDevice + " not reachable";
					break;
				case BluetoothPeerManager.PeerEventsListener.SEARCH_FAILED:
					errorMsg = "Service search on device " + remoteDevice + " failed";
					break;
				case BluetoothPeerManager.PeerEventsListener.SEARCH_ABORTED:
					errorMsg = "Service search on device " + remoteDevice + " was aborted";
					break;
			}
			do_alert(errorMsg, Alert.FOREVER);
		}
	}

	public void AuthenticationFailure(Object sender, Object remote, Exception e, String msg) {
		// TODO: display proper error message
		do_alert("Authentication with " + remote + " failed: " + msg, Alert.FOREVER);
	}

	public void AuthenticationProgress(Object sender, Object remote, int cur, int max, String msg) {
		// just ignore for this demo application 
	}
	public boolean AuthenticationStarted(Object sender, Object remote) {
		// just ignore for this demo application
		return true;
	}

	public void AuthenticationSuccess(Object sender, Object remote, Object result) {
		logger.info("Successful authentication");
        Object[] res = (Object[]) result;
        // remember the secret key shared with the other device
        byte[] sharedKey = (byte[]) res[0];
        // and extract the shared authentication key for phase 2
        byte[] authKey = (byte[]) res[1];
        // then extract the optional parameter
        String param = (String) res[2];
        logger.info("Extracted session key of length " + sharedKey.length +
        		", authentication key of length " + authKey.length + 
        		" and optional parameter '" + param + "'");
        RemoteConnection connectionToRemote = (RemoteConnection) res[3];
        
        // At the moment, only hash comparison is available
		verifyHashComparison(authKey);
		
		/*
        try {
        	LineReaderWriter.println(connectionToRemote.getOutputStream(), 
        			"Finished DH key agreement - now start to verify");
		} catch (IOException e) {
			logger.debug("Unable to open stream to remote: " + e);
		}
		
		do_alert("Authentication with " + remote + " successful", Alert.FOREVER);
		*/
	}
	
	/*
	 * Verifies that the two devices agreed on the same public data string by
	 * showing a hash of it to the user. The user will then compare the displayed
	 * strings on both devices and gives appropriate feedback.
	 */
	private void verifyHashComparison(byte[] authKey) {
		logger.debug("Start key verification: Hash comparison");
		String hashString;
        try {
			hashString = getHexString(Hash.doubleSHA256(authKey, false));
			hashString = hashString.substring(0, HASH_STRING_LENGTH);
		} catch (InternalApplicationException e) {
			logger.error("Failed to build hash.", e);
			return;
		}
		Form userFeedback = new Form ("Hash Comparison");
		userFeedback.append(hashString + "\n\n");
		userFeedback.append("Please compare the above string with the other device.\nAre they the same?");
		final Command equalsCommand 	= new Command("Yes", Command.OK, 1);
		final Command notEqualsCommand	= new Command("No", Command.CANCEL, 1);
		userFeedback.addCommand(equalsCommand);
		userFeedback.addCommand(notEqualsCommand);
		CommandListener feedbackListener = new CommandListener() {
			//@Override
			public void commandAction(Command command, Displayable d) {
				if (command == equalsCommand) {
					Alert a = new Alert("Hash comparison",
							"Authentication successful!", null, AlertType.CONFIRMATION);
					a.setTimeout(Alert.FOREVER);
					display.setCurrent(a, main_list);
				}
				else if (command == notEqualsCommand) {
					Alert a = new Alert("Hash comparison",
							"Authentication failed!", null, AlertType.ERROR);
					a.setTimeout(Alert.FOREVER);
					display.setCurrent(a, main_list);
				}
			}
		};
		userFeedback.setCommandListener(feedbackListener);
		display.setCurrent(userFeedback);
	}
	
	/* 
	 * Convenience method to convert a byte[] to a hex string
	 * (since J2ME does not support the String.format() method) 
	 */
	private String getHexString(byte[] b) {
		String result = "";
		for (int i = 0; i < b.length; i++) {
			result += Integer.toString((b[i] & 0xff) + 0x100, 16).substring(1);
		}
		return result;
	}
}
