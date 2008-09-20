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

import javax.bluetooth.DataElement;
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

import net.sf.microlog.Level;
import net.sf.microlog.appender.FormAppender;
import net.sf.microlog.ui.LogForm;

import org.apache.log4j.Logger;
import org.openuat.authentication.AuthenticationProgressHandler;
import org.openuat.authentication.HostProtocolHandler;
import org.openuat.authentication.exceptions.InternalApplicationException;
import org.openuat.util.BluetoothPeerManager;
import org.openuat.util.BluetoothRFCOMMChannel;
import org.openuat.util.BluetoothRFCOMMServer;
import org.openuat.util.BluetoothSupport;
import org.openuat.util.RemoteConnection;



/** This MIDlet demonstrates all three possible options for out-of-band peer
 * verification when using the UACAP protocol and "manual" authentication.
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 *
 */
public class OpenUATmidlet extends MIDlet implements CommandListener,
BluetoothPeerManager.PeerEventsListener, AuthenticationProgressHandler{

	private List main_list;

	List dev_list;

	List serv_list;

	Command exit;
	Vector services;
	private Command back;
	
	private Command successCmd;
	private Command failure;
	
	List verify_method;
	Command log;

	Command auth;

	Display display;

	BluetoothPeerManager peerManager;

	BluetoothRFCOMMServer rfcommServer;

	LogForm logForm;

	/** Tells whether this device initiated the set-up or not. If it did, it will be the one coordinating the verification process.	 */
	protected boolean initiator = false;

	/** The key resulting from the message exchange during the authentication process */
	protected byte [] authKey;
	
	// our logger
	Logger logger = Logger.getLogger("");

	public OpenUATmidlet() {
		display = Display.getDisplay(this);

		initLogger();

		if (! BluetoothSupport.init()) {
			do_alert("Could not initialize Bluetooth API", Alert.FOREVER);
			return;
		}

		try {
			rfcommServer = new BluetoothRFCOMMServer(null, new UUID("447d8ecbefea4b2d93107ced5d1bba7e", false), "OpenUAT- Exchange vCard", 
					-1, true, false);
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

		initGui();
	}

	protected void initGui() {
		setMain_list(new List("Select Operation", Choice.IMPLICIT)); //the main menu
		dev_list = new List("Select Device", Choice.IMPLICIT); //the list of devices
		serv_list = new List("Available Services", Choice.IMPLICIT); //the list of services
		exit = new Command("Exit", Command.EXIT, 1);
		setBack(new Command("Back", Command.BACK, 1));
		log = new Command("Log", Command.ITEM, 2);

		getMain_list().addCommand(exit);
		getMain_list().addCommand(log);
		getMain_list().setCommandListener(this);
		dev_list.addCommand(exit);
		dev_list.addCommand(log);
		dev_list.setCommandListener(this);
		serv_list.addCommand(exit);
		serv_list.addCommand(back);
		serv_list.setCommandListener(this);

		getMain_list().append("Find Devices", null);
		getMain_list().append("pair with my N95", null);
		getMain_list().append("pair with my N82", null);
		getMain_list().append("pair with computer", null);
		
		setSuccessCmd(new Command("Success", Command.SCREEN, 1));
		setFailure(new Command("Failure", Command.SCREEN, 1));
	}

	protected void initLogger() {
		net.sf.microlog.Logger logBackend = net.sf.microlog.Logger.getLogger();
		logForm = new LogForm();
		logForm.setDisplay(display);
		logBackend.addAppender(new FormAppender(logForm));
		//logBackend.addAppender(new RecordStoreAppender());
		logBackend.setLogLevel(Level.DEBUG);
		logger.info("Microlog initialized");
	}

	// TODO: activate me again when J2ME polish can deal with Java5 sources!
	public void startApp() {
		logForm.setPreviousScreen(getMain_list());

		display.setCurrent(getMain_list());
		
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
			if (dis == getMain_list()) { //select triggered from the main from
				if (getMain_list().getSelectedIndex() == 0) { //find devices
					if (!peerManager.startInquiry(false)) {
						this.do_alert("Error in initiating search", 4000);
					}
					do_alert("Searching for devices...", Alert.FOREVER);
				}if (getMain_list().getSelectedIndex() == 1) { //demo - hack for demo purposes
					String btAdd = "001C9AF755EB";
					connectTo(btAdd, 5);
				}else if (getMain_list().getSelectedIndex() == 2) { //demo
					String btAdd = "001DFD71C3C3";
					connectTo(btAdd, 5);
				}else if(getMain_list().getSelectedIndex() == 3) { //demo
					String btAdd = "001F5B7B16F7";
					connectTo(btAdd, 2);
				}
			}
			if (dis == dev_list) { //select triggered from the device list
				if (dev_list.getSelectedIndex() >= 0) { //find services
					logger.info("selected index: "+dev_list.getSelectedIndex());
					RemoteDevice[] devices = peerManager.getPeers();
					
					serv_list.deleteAll(); //empty the list of services in case user has pressed back
					UUID uuid = new UUID(0x0001); // publicly browsable services
					if (!peerManager.startServiceSearch(devices[dev_list.getSelectedIndex()], uuid)) {
						this.do_alert("Error in initiating search", 4000);
					}
					do_alert("Inquiring device for services...", Alert.FOREVER);
				}
			}
			if (dis == serv_list){
				int selectedIndex = dev_list.getSelectedIndex();
				ServiceRecord service = (ServiceRecord) services.elementAt(selectedIndex);
//				String btAdd = service.getHostDevice().getBluetoothAddress();
				String connectionURL = service.getConnectionURL(ServiceRecord.NOAUTHENTICATE_NOENCRYPT, false);
				connectTo(connectionURL);
				
			}
		}
		else if (com == getBack()) {
			display.setCurrent(getMain_list());
		}
		else if (com == log) {
			display.setCurrent(logForm);
		}

	}

	/**
	 * Starts the connection and authentication process to the given Bluetooth address.
	 * @param btAdd
	 */
	protected void connectTo(String btAdd, int channel) {
		boolean keepConnected = true;
		String optionalParam = null;  
		logger.info("starting authentication ");
		try {
			initiator = true;
			BluetoothRFCOMMChannel c = new BluetoothRFCOMMChannel(btAdd, channel);
			c.open();
			HostProtocolHandler.startAuthenticationWith(c, this, 200000, keepConnected, optionalParam, true);

		} catch (IOException e) {
			logger.error(e);
			do_alert("error", Alert.FOREVER);
		}
	}


	/**
	 * Starts the connection and authentication process to the given Bluetooth address.
	 * @param btAdd
	 */
	protected void connectTo(String connectionURL) {
		boolean keepConnected = true;
		String optionalParam = null;  
		logger.info("starting authentication ");
		try {
			initiator = true;
			BluetoothRFCOMMChannel c = new BluetoothRFCOMMChannel(connectionURL);
			c.open();
			HostProtocolHandler.startAuthenticationWith(c, this, 200000, keepConnected, optionalParam, true);

		} catch (IOException e) {
			logger.error(e);
			do_alert("error", Alert.FOREVER);
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


	public void AuthenticationFailure(Object sender, Object remote, Exception e, String msg) {
		// just ignore for this demo application 
	}

	public void AuthenticationProgress(Object sender, Object remote, int cur, int max, String msg) {
		// just ignore for this demo application 
	}
	public boolean AuthenticationStarted(Object sender, Object remote) {
		
		logger.info("authentication started");
		
		Alert alert = new Alert("Incoming connection", "Pairing with" + remote.toString(), null, AlertType.CONFIRMATION);
		alert.setTimeout(Alert.FOREVER);
		display.setCurrent(getMain_list());
		display.setCurrent(alert);
		return true;
	}

	
	public void AuthenticationSuccess(Object sender, Object remote, Object result) {
		logger.info("Successful authentication");
		Object[] res = (Object[]) result;
		// remember the secret key shared with the other device
		byte[] sharedKey = (byte[]) res[0];
		// and extract the shared authentication key for phase 2
		authKey = (byte[]) res[1];
		// then extract the optional parameter
		String param = (String) res[2];
		logger.info("Extracted session key of length " + sharedKey.length +
				", authentication key of length " + authKey.length + 
				" and optional parameter '" + param + "'");
		RemoteConnection connectionToRemote = (RemoteConnection) res[3];

		if(initiator){

			verify_method = new List("Method", List.IMPLICIT, new String[]{"visual","audio","slowcode","madlib"}, null);
//			options.addCommand(cmd)
			verify_method.setCommandListener(new KeyVerifier(authKey, connectionToRemote, this));

			verify_method.addCommand(back);
			verify_method.addCommand(exit);

			display.setCurrent(verify_method);
		}else{
			boolean success = new KeyVerifier(authKey, connectionToRemote, this).verify();
			informSuccess(success);

		}

	}

//	boolean authentic = true;

	public void serviceListFound(RemoteDevice remoteDevice, Vector services) {
		this.services = services;
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

	public void informSuccess(boolean success) {
		Form outcome = new Form ("OpenUAT");
		if(success){
			outcome.append("Congratulations. Authentication successful.");
		}
		else{
			outcome.append("Error. Authentication failed.");
		}
		outcome.addCommand(exit);
		outcome.addCommand(log);
		outcome.setCommandListener(this);
		Display.getDisplay(this).setCurrent(outcome);

	}

	public void setMain_list(List main_list) {
		this.main_list = main_list;
	}

	public List getMain_list() {
		return main_list;
	}

	public void setBack(Command back) {
		this.back = back;
	}

	public Command getBack() {
		return back;
	}

	public void setFailure(Command failure) {
		this.failure = failure;
	}

	public Command getFailure() {
		return failure;
	}

	public void setSuccessCmd(Command successCmd) {
		this.successCmd = successCmd;
	}

	public Command getSuccessCmd() {
		return successCmd;
	}
}
