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
import javax.microedition.lcdui.Gauge;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.Ticker;
import javax.microedition.midlet.MIDlet;

import org.openbandy.service.LogService;
import org.openuat.authentication.AuthenticationProgressHandler;
import org.openuat.authentication.HostProtocolHandler;
import org.openuat.authentication.exceptions.InternalApplicationException;
import org.openuat.util.BluetoothPeerManager;
import org.openuat.util.BluetoothRFCOMMChannel;
import org.openuat.util.BluetoothRFCOMMServer;
import org.openuat.util.BluetoothSupport;
import org.openuat.util.RemoteConnection;



/** This MIDlet demonstrates four out-of-band peer
 * verification: visual, audio, madlib, slowcodec.
 * 
 * @author Rene Mayrhofer
 * @author Iulia Ion
 * @version 1.0
 *
 */
public class OpenUATmidlet extends MIDlet implements CommandListener,
BluetoothPeerManager.PeerEventsListener, AuthenticationProgressHandler{

	/** The key resulting from the message exchange during the authentication process */
	protected byte [] authKey;
	
	protected static Image search = null;
	protected static Image vCard = null;
	protected static Image print = null;
	
	//authentication options
	protected static Image visual = null;
	protected static Image audio = null;
	protected static Image slowcodec = null;
	protected static Image madlib = null;
	
	protected static Image buttonok = null;
	protected static Image buttoncancel = null;	
	protected static Image secure = null;
	protected static Image error = null;	
	
	/** Initial screen with options */
	protected List home_screen;

	/** screen that shows what Bluetooth devices have been found */
	protected List dev_list;

	/** The list of Bluetooth services offered by a device */
	protected List serv_list;

	/** displays the available verification methods: visual, audio, madlib, slowcodec */
	protected List verify_method;
	/** The result of the verification method */
	protected Form success_form;
	protected Form failure_form;

	protected Displayable logScreen;
	
	/** Chosen by the user to indicate the verification outcome in manual verification cases */
	protected Command back;
	protected Command exit;
	protected Command log;
	protected Command successCmd;
	protected Command failureCmd;
	protected Command increaseVolume;
	protected Command decreaseVolume;
	
	protected Display display;

	protected BluetoothRFCOMMServer rfcommServer;
	protected BluetoothPeerManager peerManager;
	protected Vector services;
	
	//how loud to sing
	public int volume = 50;

	
	protected Gauge bluetoothProgressGauge;
	protected ProgressScreen progressScreen;

	/** Tells whether this device initiated the set-up or not. If it did, it will be the one coordinating the verification process.	 */
	protected boolean initiator = false;

	/** the screen to which to change to when te BAck method is invoked */
	private Displayable previousScreen;
	/** the screen that is current being showed */
	private Displayable currentScreen;
	
	/** Client connection to the bluetooth server */
	private BluetoothRFCOMMChannel c; 
	
	private void loadImages(){
		try {
			search = Image.createImage("/search_sm.png");
			vCard = Image.createImage("/vcard_sm.png");
			print = Image.createImage("/print_sm.png");
			
			visual = Image.createImage("/visual_sm.png");
			audio = Image.createImage("/audio_sm.png");
			slowcodec = Image.createImage("/slowcodec_sm.png");
			madlib = Image.createImage("/madlib_sm.png");
			
			buttonok = Image.createImage("/button_ok_sm.png");
			buttoncancel = Image.createImage("/button_cancel_sm.png");
			secure = Image.createImage("/secure_sm.png");
			error = Image.createImage("/error.png");
		} catch (IOException e) {
			LogService.info(this, "could not load image");
		}
	}
	// our logger
//	Logger logger = Logger.getLogger("");

	public OpenUATmidlet() {

		if (! BluetoothSupport.init()) {
			do_alert("Could not initialize Bluetooth API", Alert.FOREVER);
			return;
		}

		try {

			rfcommServer = new BluetoothRFCOMMServer(null, new UUID(0x0001), "OpenUAT- Exchange vCard", 
					-1, true, false);
			rfcommServer.addAuthenticationProgressHandler(this);
			rfcommServer.start();
			LogService.info(this, "Finished starting SDP service at " + rfcommServer.getRegisteredServiceURL());
		} catch (IOException e) {
			LogService.error(this, "Error initializing BlutoothRFCOMMServer: ", e);
		}

		try {
			peerManager = new BluetoothPeerManager();
			peerManager.addListener(this);
		} catch (IOException e) {
			LogService.error(this, "Error initializing BlutoothPeerManager",  e);
			return;
		}

		initGui();
	}

	protected void initGui() {
		loadImages();
		
		//initialize screens
		home_screen = new List("OpenUAT", Choice.IMPLICIT); //the main menu
		dev_list = new List("Select Device", Choice.IMPLICIT); //the list of devices
		serv_list = new List("Available Services", Choice.IMPLICIT); //the list of services
		
		//initialize commands
		exit = new Command("Exit", Command.EXIT, 1);
		back = new Command("Back", Command.BACK, 1);
		log = new Command("Log", Command.ITEM, 2);

		successCmd = new Command("Success", Command.SCREEN, 1);
		failureCmd = new Command("Failure", Command.SCREEN, 1);
		
		increaseVolume = new Command("Increase volume", Command.SCREEN, 2);
		decreaseVolume = new Command("Decrease volume", Command.SCREEN, 2);
		
		home_screen.append("Find devices", search);
		home_screen.append("Send vCard", vCard);
		home_screen.append("Print document", print);
		
		home_screen.addCommand(exit);
		home_screen.addCommand(log);
		home_screen.addCommand(increaseVolume);
		home_screen.addCommand(decreaseVolume);
		
		
		dev_list.addCommand(exit);
		dev_list.addCommand(log);
		dev_list.addCommand(back);

		serv_list.addCommand(exit);
		dev_list.addCommand(log);
		serv_list.addCommand(back);
		


		logScreen = LogService.getLog();
//		logScreen = new Form("LOG");
		logScreen.addCommand(back);
		
		
		Image[]	images = new Image[]{visual, audio, slowcodec, madlib};
		verify_method = new List("Method", List.IMPLICIT, new String[]{"Visual channel","Audio channel","Compare tunes","Compare text"}, images);
		verify_method.addCommand(back);
		verify_method.addCommand(log);
		verify_method.addCommand(exit);
		verify_method.addCommand(increaseVolume);
		verify_method.addCommand(decreaseVolume);
		
		success_form =  new Form ("OpenUAT");
		failure_form =  new Form ("OpenUAT");
		
		
		success_form.append(buttonok);
		success_form.append("Congratulations! Authentication successful!\n");
		success_form.append("Your connection is now secure!\n");
		success_form.append(secure);
		
		failure_form.append("Error. Authentication failed.\n");
//		failure_form.append("Retry?");
//		failure_form.append(error);
		failure_form.append("Please try again.\n");

		success_form.addCommand(exit);
		success_form.addCommand(log);
		success_form.addCommand(back);

		failure_form.addCommand(exit);
		failure_form.addCommand(log);
		failure_form.addCommand(back);
		failure_form.addCommand(increaseVolume);
		failure_form.addCommand(decreaseVolume);
		
		dev_list.setCommandListener(this);
		home_screen.setCommandListener(this);
		serv_list.setCommandListener(this);
		success_form.setCommandListener(this);
		failure_form.setCommandListener(this);
		logScreen.setCommandListener(this);
		
		display = Display.getDisplay(this);
		//what to do about the other options? recall the thing
	}

	public void startApp() {
		display.setCurrent(home_screen);
		currentScreen = home_screen;
		
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
			if (dis == getHomeScreen()) { //select triggered from the main from
				if (home_screen.getSelectedIndex() == 0) { //find devices
					if (!peerManager.startInquiry(false)) {
						this.do_alert("Error in initiating search", 4000);
					}
					progressScreen = new ProgressScreen("/bluetooth_icon_sm.png", 10);
					progressScreen.showActionAtStartupGauge("Searching for devices...");
					display.setCurrent(progressScreen);
					currentScreen = progressScreen;
					previousScreen = home_screen; 
					
//				}if (getMain_list().getSelectedIndex() == 1) { //demo - N95
//					String btAdd = "001C9AF755EB";
//					connectTo(btAdd, 5);
				}else if (getHomeScreen().getSelectedIndex() == 1) { //demo N82
					String btAdd = "001DFD71C3C3";
					connectTo(btAdd, 5);
				}else if(getHomeScreen().getSelectedIndex() == 2) { //demo computer
					//String btAdd = "001F5B7B16F7";
//					connectTo(btAdd, 2);
					
					String btAdd = "001EC28E3024";
					connectTo(btAdd, 1);

				}
			}
			if (dis == dev_list) { //select triggered from the device list
				if (dev_list.getSelectedIndex() >= 0) { //find services
					LogService.info(this, "selected index: "+dev_list.getSelectedIndex());
					RemoteDevice[] devices = peerManager.getPeers();
					
					serv_list.deleteAll(); //empty the list of services in case user has pressed back
					UUID uuid = new UUID(0x0001); // publicly browsable services
					if (!peerManager.startServiceSearch(devices[dev_list.getSelectedIndex()], uuid)) {
						this.do_alert("Error in initiating search", 4000);
					}
					
					progressScreen = new ProgressScreen("/bluetooth_icon_sm.png", 15);
					progressScreen.showActionAtStartupGauge("Inquiring device for services...");
					display.setCurrent(progressScreen);
					currentScreen = progressScreen;
					previousScreen = dev_list;
				}
			}
			if (dis == serv_list){
				int selectedIndex = dev_list.getSelectedIndex();
				ServiceRecord service = (ServiceRecord) services.elementAt(selectedIndex);
				progressScreen = new ProgressScreen("/bluetooth_icon_sm.png", 15);
				previousScreen = serv_list;
				currentScreen = progressScreen;
				display.setCurrent(progressScreen);
				verify_method.addCommand(back);
//				String btAdd = service.getHostDevice().getBluetoothAddress();
				String connectionURL = service.getConnectionURL(ServiceRecord.NOAUTHENTICATE_NOENCRYPT, false);
				progressScreen.showActionAtStartupGauge("Connecting to service: "+connectionURL);
				connectTo(connectionURL);
				
			}
		}
		else if (com == back) {
			display.setCurrent(previousScreen);
			previousScreen = home_screen;
			//at most two steps for back
//			currentScreen = previousScreen;
//			previousScreen = currentScreen;
		}
		else if (com == log) {
			display.setCurrent(logScreen);
			previousScreen = currentScreen;
			currentScreen = logScreen;
		} else if (com == increaseVolume){
			if (volume <= 90)
				volume += 10;
			do_alert("Volume increased to " + volume, 1000);
		} else if (com == decreaseVolume){
			if (volume >= 10)
				volume -= 10;
			do_alert("Volume decreased to " + volume, 1000);
		}
	}

	/**
	 * Starts the connection and authentication process to the given Bluetooth address.
	 * @param btAdd
	 */
	protected void connectTo(String btAdd, int channel) {
		//close the connection, reopen, for demo purposes, so we do not close and restart the app.
		if (c != null && c.isOpen()){
			c.close();
		}
		boolean keepConnected = true;
		String optionalParam = null;  
		LogService.info(this, "starting authentication ");
		try {
			initiator = true;
			c = new BluetoothRFCOMMChannel(btAdd, channel);
			c.open();
			HostProtocolHandler.startAuthenticationWith(c, this, -1, keepConnected, optionalParam, true);

		} catch (IOException e) {
			LogService.error(this, "Could not conenct to "+btAdd, e);
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
		LogService.info(this, "starting authentication ");
		try {
			initiator = true;
			BluetoothRFCOMMChannel c = new BluetoothRFCOMMChannel(connectionURL);
			c.open();
			HostProtocolHandler.startAuthenticationWith(c, this, 200000, keepConnected, optionalParam, true);

		} catch (IOException e) {
			LogService.error(this, "could not connect to "+connectionURL, e);
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

	public void do_alert_gauge(String title, String msg, String img, int max_time) {
		Image icon = null;
		try {
			icon = Image.createImage(img);
		}
		catch (IOException ioe) {
			LogService.error(this, "could not load image bluetooth_icon.png",ioe);
		}
		Gauge gauge = new Gauge("Progress", false, max_time, 0);
		Alert alert = new Alert(title);
		alert.setString(msg);
		alert.setImage(icon);
		alert.setTimeout(Alert.FOREVER);
		alert.setIndicator(gauge);
//		new Thread(){
//			public void run(){
//				int i = 0;
//				while (i < gauge.getMaxValue()){
//					gauge.setValue(i++);
//					try {
//						Thread.sleep(1000);
//					} catch (InterruptedException e) {
//						// TODO Auto-generated catch block
//						e.printStackTrace();
//					}	
//				}
//			}
//		}.start();
//		alert.setTicker(new Ticker("----"));
		display.setCurrent(alert);
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
//			logForm.setPreviousScreen(dev_list);
			display.setCurrent(dev_list);
			currentScreen = dev_list;
			previousScreen = home_screen;
		}
	}


	public void AuthenticationFailure(Object sender, Object remote, Exception e, String msg) {
		// just ignore for this demo application 
	}

	public void AuthenticationProgress(Object sender, Object remote, int cur, int max, String msg) {
		// just ignore for this demo application 
	}
	public boolean AuthenticationStarted(Object sender, Object remote) {
		
		LogService.info(this, "Authentication started");
		
		Alert alert = new Alert("Incoming connection", "Pairing with" + remote.toString(), null, AlertType.CONFIRMATION);
		alert.setTimeout(Alert.FOREVER);
		display.setCurrent(home_screen);
		display.setCurrent(alert);
		return true;
	}

	
	public void AuthenticationSuccess(Object sender, Object remote, Object result) {
		LogService.info(this, "Successful authentication");
		Object[] res = (Object[]) result;
		// remember the secret key shared with the other device
		byte[] sharedKey = (byte[]) res[0];
		// and extract the shared authentication key for phase 2
		authKey = (byte[]) res[1];
		// then extract the optional parameter
		String param = (String) res[2];
		LogService.info(this, "Extracted session key of length " + sharedKey.length +
				", authentication key of length " + authKey.length + 
				" and optional parameter '" + param + "'");
		RemoteConnection connectionToRemote = (RemoteConnection) res[3];

		if(initiator){
			verify_method.setCommandListener(new KeyVerifier(authKey, connectionToRemote, this));
			previousScreen = currentScreen;
			currentScreen = verify_method;
			display.setCurrent(verify_method);
		}else{
			boolean success = new KeyVerifier(authKey, connectionToRemote, this).verify();
			informSuccess(success);

		}

	}

	public void serviceListFound(RemoteDevice remoteDevice, Vector services) {
		this.services = services;
		for (int x = 0; x < services.size(); x++)
			try {
				DataElement ser_de = ((ServiceRecord) services.elementAt(x))
						.getAttributeValue(0x100);
				String service_name = (String) ser_de.getValue();
				serv_list.append(service_name, null);
				display.setCurrent(serv_list);
				currentScreen = serv_list;
				previousScreen = dev_list;
			} catch (Exception e) {
				do_alert("Error in adding services ", 1000);
			}
	}

	/**
	 * Displays a screen to the user telling whether the exchanged key was successfully verified.
	 * @param success
	 */
	public void informSuccess(boolean success) {
		previousScreen = currentScreen;
		if(success){
			display.setCurrent(success_form);
			currentScreen = success_form;
		}
		else{
			display.setCurrent(failure_form);
			currentScreen = failure_form;
		}
		
	}

	public List getHomeScreen() {
		return home_screen;
	}

	public Command getBack() {
		return back;
	}

	public Command getFailure() {
		return failureCmd;
	}


	public Command getSuccessCmd() {
		return successCmd;
	}
}

