/* Copyright Rene Mayrhofer
 * File created 2007-01-25
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

import org.apache.log4j.Logger;
import org.openuat.authentication.AuthenticationProgressHandler;
import org.openuat.authentication.exceptions.InternalApplicationException;
import org.openuat.util.BluetoothPeerManager;
import org.openuat.util.BluetoothRFCOMMServer;
import org.openuat.util.BluetoothSupport;
import org.openuat.util.LineReaderWriter;
import org.openuat.util.RemoteConnection;

public class BluetoothDemo extends MIDlet implements CommandListener,
		BluetoothPeerManager.PeerEventsListener, AuthenticationProgressHandler {
	List main_list;

	List dev_list;

	List serv_list;

	Command exit;

	Command back;

	Command log;

	Display display;
	
	BluetoothPeerManager peerManager;
	
	BluetoothRFCOMMServer rfcommServer;
	
	LogForm logForm;

	// our logger
	Logger logger = Logger.getLogger("");
	
	public BluetoothDemo() {
		display = Display.getDisplay(this);

		// problem with CRLF in microlog.properies? try unix2dos...
        /*try {
            GlobalProperties.init(this);
        } catch (IllegalStateException e) {
            //Ignore this exception. It is already initiated.
        }
		logger.configure(GlobalProperties.getInstance());*/
		
		net.sf.microlog.Logger logBackend = net.sf.microlog.Logger.getLogger();
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
			rfcommServer = new BluetoothRFCOMMServer(null, new UUID("447d8ecbefea4b2d93107ced5d1bba7e", false), "J2ME Test Service", true, false);
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
				if (main_list.getSelectedIndex() >= 0) { //find devices
					if (!peerManager.startInquiry(false)) {
						this.do_alert("Error in initiating search", 4000);
					}
					do_alert("Searching for devices...", Alert.FOREVER);
				}
			}
			if (dis == dev_list) { //select triggered from the device list
				if (dev_list.getSelectedIndex() >= 0) { //find services
					RemoteDevice[] devices = peerManager.getPeers();
					
					serv_list.deleteAll(); //empty the list of services in case user has pressed back
					UUID uuid = new UUID(0x1002); // publicly browsable services
					if (!peerManager.startServiceSearch(devices[dev_list.getSelectedIndex()], uuid)) {
						this.do_alert("Error in initiating search", 4000);
					}
					do_alert("Inquiring device for services...", Alert.FOREVER);
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
		// nothing special to do, resources will be freed automatically
	}

	public void inquiryCompleted(Vector newDevices) {
		for (int i=0; i<newDevices.size(); i++) {
			String device_name = BluetoothPeerManager.resolveName((RemoteDevice) newDevices.elementAt(i));
			this.dev_list.append(device_name, null);
			logForm.setPreviousScreen(dev_list);
			display.setCurrent(dev_list);
		}
	}

	public void serviceListFound(RemoteDevice remoteDevice, Vector services) {
		for (int x = 0; x < services.size(); x++)
			try {
				DataElement ser_de = ((ServiceRecord) services.elementAt(x))
						.getAttributeValue(0x100);
				String service_name = (String) ser_de.getValue();
				serv_list.append(service_name, null);
				display.setCurrent(serv_list);
			} catch (Exception e) {
				do_alert("Error in adding services ", 1000);
			}
	}

	public void AuthenticationFailure(Object sender, Object remote, Exception e, String msg) {
		// just ignore for this demo application 
	}

	public void AuthenticationProgress(Object sender, Object remote, int cur, int max, String msg) {
		// just ignore for this demo application 
	}

	public void AuthenticationSuccess(Object sender, Object remote, Object result) {
		logger.info("Successful authentication");
        Object[] res = (Object[]) result;
        // remember the secret key shared with the other device
        byte[] sharedKey = (byte[]) res[0];
        // and extract the shared authentication key for phase 2
        byte[] authKey = (byte[]) res[1];
        // then extraxt the optional parameter
        String param = (String) res[2];
        logger.info("Extracted session key of length " + sharedKey.length +
        		", authentication key of length " + authKey.length + 
        		" and optional parameter '" + param + "'");
        RemoteConnection connectionToRemote = (RemoteConnection) res[3];
        try {
        	LineReaderWriter.println(connectionToRemote.getOutputStream(), 
        			"Finished DH key agreement - now start to verify");
		} catch (IOException e) {
			logger.debug("Unable to open stream to remote: " + e);
		}
	}
}
