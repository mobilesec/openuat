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
import java.io.UnsupportedEncodingException;
import java.util.Vector;

import javax.bluetooth.RemoteDevice;
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
import org.codec.mad.MadLib;
import org.openuat.authentication.AuthenticationProgressHandler;
import org.openuat.authentication.HostProtocolHandler;
import org.openuat.authentication.exceptions.InternalApplicationException;
import org.openuat.util.BluetoothPeerManager;
import org.openuat.util.BluetoothRFCOMMChannel;
import org.openuat.util.BluetoothRFCOMMServer;
import org.openuat.util.BluetoothSupport;
import org.openuat.util.Hash;
import org.openuat.util.RemoteConnection;



/** This MIDlet demonstrates all three possible options for out-of-band peer
 * verification when using the UACAP protocol and "manual" authentication.
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 *
 */
public class SlowCodecOpenUATmidlet extends OpenUATmidlet implements CommandListener,
BluetoothPeerManager.PeerEventsListener, AuthenticationProgressHandler{

	String btAdd = "001DFD71C3C3";

	Command sendToPhone;
	Form mainForm ;

	public SlowCodecOpenUATmidlet() {
		super();
	}

	protected void initGui() {
		
		mainForm = new Form("OpenUAT - MADlib");
		mainForm.append("Welcome to OpenUAT\n");
		mainForm.append("Send vCard to phone\n");
		
		exit = new Command("Exit", Command.EXIT, 2);
		setBack(new Command("Back", Command.BACK, 2));
		log = new Command("Log", Command.SCREEN, 2);
		sendToPhone = new Command("Send", Command.SCREEN, 1);
		mainForm.addCommand(sendToPhone);
		
		mainForm.addCommand(exit);
		mainForm.addCommand(log);
		
		
		mainForm.setCommandListener(this);

		display.setCurrent(mainForm);
		
		setSuccessCmd(new Command("Success", Command.SCREEN, 1));
		setFailure(new Command("Failure", Command.SCREEN, 1));
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
		}else if (com == getSuccessCmd()){
			informSuccess(true);
		}else if (com == getFailure()){
			informSuccess(false);
		}else if (com == sendToPhone){
			connectTo(btAdd, 5);
		}
//		else if (com == List.SELECT_COMMAND) {
//			if (dis == main_list) { //select triggered from the main from
//				if (main_list.getSelectedIndex() == 0) { //find devices
//					if (!peerManager.startInquiry(false)) {
//						this.do_alert("Error in initiating search", 4000);
//					}
//					do_alert("Searching for devices...", Alert.FOREVER);
//				}if (main_list.getSelectedIndex() == 1) { //demo - hack for demo purposes
//					String btAdd = "001C9AF755EB"; //N95
//					connectTo(btAdd, 5);
//				}else if (main_list.getSelectedIndex() == 2) { //demo
//					String btAdd = "001DFD71C3C3"; //N82
//					connectTo(btAdd, 5);
//				}else if(main_list.getSelectedIndex() == 3) { //demo computer
//					String btAdd = "001F5B7B16F7";
//					connectTo(btAdd, 2);
//				}
//			}
//			if (dis == dev_list) { //select triggered from the device list
//				if (dev_list.getSelectedIndex() >= 0) { //find services
//					
//					RemoteDevice[] devices = peerManager.getPeers();
//					RemoteDevice peer = devices[dev_list.getSelectedIndex()];
//					
//					String btAdd = peer.getBluetoothAddress();
//					connectTo(btAdd, 5);
//				}
//			}
//		}
//		else if (com == back) {
//			display.setCurrent(main_list);
//		}
		else if (com == log) {
			display.setCurrent(logForm);
		}

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
			KeyVerifier verifier = new KeyVerifier(authKey, connectionToRemote, this);

			verifier.verifySlowCodec();
			
		}
		//doesn't need to happen
//		else{
//			boolean success = new KeyVerifier(authKey, connectionToRemote, this).verify();
//			informSuccess(success);
//		}

	}

}
