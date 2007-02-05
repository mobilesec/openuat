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

import org.openuat.util.BluetoothPeerManager;
import org.openuat.util.BluetoothSupport;

public class BluetoothDemo extends MIDlet implements CommandListener,
		BluetoothPeerManager.PeerEventsListener {
	List main_list;

	List dev_list;

	List serv_list;

	Command exit;

	Command back;

	Display display;
	
	BluetoothPeerManager peerManager;
	
	public BluetoothDemo() {
		if (! BluetoothSupport.init()) {
			do_alert("Could not initialize Bluetooth API", Alert.FOREVER);
			return;
		}
		
		try {
			peerManager = new BluetoothPeerManager();
			peerManager.addListener(this);

			main_list = new List("Select Operation", Choice.IMPLICIT); //the main menu
			dev_list = new List("Select Device", Choice.IMPLICIT); //the list of devices
			serv_list = new List("Available Services", Choice.IMPLICIT); //the list of services
			exit = new Command("Exit", Command.EXIT, 1);
			back = new Command("Back", Command.BACK, 1);
			display = Display.getDisplay(this);

			main_list.addCommand(exit);
			main_list.setCommandListener(this);
			dev_list.addCommand(exit);
			dev_list.setCommandListener(this);
			serv_list.addCommand(exit);
			serv_list.addCommand(back);
			serv_list.setCommandListener(this);

			main_list.append("Find Devices", null);
		} catch (IOException e) {
			do_alert("Error initializing BlutoothPeerManager: " + e, Alert.FOREVER);
			display=null;
		}
	}

	public void startApp() {
		display.setCurrent(main_list);
	}

	public void commandAction(Command com, Displayable dis) {
		if (com == exit) { //exit triggered from the main form
			destroyApp(false);
			notifyDestroyed();
		}
		if (com == List.SELECT_COMMAND) {
			if (dis == main_list) { //select triggered from the main from
				if (main_list.getSelectedIndex() >= 0) { //find devices
					FindDevices();
					do_alert("Searching for devices...", Alert.FOREVER);
				}
			}
			if (dis == dev_list) { //select triggered from the device list
				if (dev_list.getSelectedIndex() >= 0) { //find services
					RemoteDevice[] devices = peerManager.getPeers();
					
					int[] attributes = { 0x100 }; //the name of the service
					UUID[] uuids = new UUID[1];
					uuids[0] = new UUID(0x1002); //browsable services
					FindServices(attributes, uuids, devices[dev_list.getSelectedIndex()]);
					do_alert("Inquiring device for services...", Alert.FOREVER);
				}
			}
		}
		if (com == back) {
			if (dis == serv_list) { //back button is pressed in devices list
				display.setCurrent(dev_list);
			}
		}

	}

	public void FindDevices() {
		if (!peerManager.startInquiry(false)) {
			this.do_alert("Erron in initiating search", 4000);
		}
	}

	public void FindServices(int[] attributes, UUID[] uuids, RemoteDevice device) {
		serv_list.deleteAll(); //empty the list of services
		//in case user has pressed back
		if (!peerManager.startServiceSearch(attributes, uuids, device)) {
			this.do_alert("Erron in initiating search", 4000);
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

	public void pauseApp() {
	}

	public void destroyApp(boolean unconditional) {
	}

	public void inquiryCompleted(Vector newDevices) {
		for (int i=0; i<newDevices.size(); i++) {
			String device_name = BluetoothPeerManager.resolveName((RemoteDevice) newDevices.elementAt(i));
			this.dev_list.append(device_name, null);
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
}
