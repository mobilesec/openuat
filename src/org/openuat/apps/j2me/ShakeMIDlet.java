/* Copyright Rene Mayrhofer
 * File created 2007-05-03
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
import net.sf.microlog.appender.RecordStoreAppender;
import net.sf.microlog.ui.LogForm;
import net.sf.microlog.util.GlobalProperties;

import org.apache.log4j.Logger;
import org.openuat.authentication.AuthenticationProgressHandler;
import org.openuat.authentication.exceptions.InternalApplicationException;
import org.openuat.util.BluetoothPeerManager;
import org.openuat.util.BluetoothRFCOMMServer;
import org.openuat.util.BluetoothSupport;

public class ShakeMIDlet extends MIDlet implements CommandListener, AuthenticationProgressHandler {
	List main_list;

	Command exit;

	Command back;

	Command log;

	Display display;
	
	BluetoothRFCOMMServer rfcommServer;
	
	LogForm logForm;

	// our logger
	Logger logger = Logger.getLogger("");
	
	public ShakeMIDlet() {
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
			rfcommServer = new BluetoothRFCOMMServer(null, new UUID("b76a37e5e5404bf09c2a1ae3159a02d8", false), "J2ME Test Service", false, false);
			rfcommServer.startListening();
			logger.info("Finished starting SDP service at " + rfcommServer.getRegisteredServiceURL());
		} catch (IOException e) {
			logger.error("Error initializing BlutoothRFCOMMServer: " + e);
		}

			main_list = new List("Select Operation", Choice.IMPLICIT); //the main menu
			exit = new Command("Exit", Command.EXIT, 1);
			back = new Command("Back", Command.BACK, 1);
			log = new Command("Log", Command.ITEM, 2);

			main_list.addCommand(exit);
			main_list.addCommand(log);
			main_list.setCommandListener(this);

			main_list.append("Find Devices", null);
	}

	public void startApp() {
		logForm.setPreviousScreen(main_list);
		display.setCurrent(main_list);
	}

	public void commandAction(Command com, Displayable dis) {
		if (com == exit) { //exit triggered from the main form
			if (rfcommServer != null)
				try {
					rfcommServer.stopListening();
				} catch (InternalApplicationException e) {
					do_alert("Could not de-register SDP service: " + e, Alert.FOREVER);
				}
			destroyApp(false);
			notifyDestroyed();
		}
		else if (com == List.SELECT_COMMAND) {
			if (dis == main_list) { //select triggered from the main from
				if (main_list.getSelectedIndex() >= 0) { //find devices
				}
			}
		}
		else if (com == back) {
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

	public void pauseApp() {
	}

	public void destroyApp(boolean unconditional) {
	}

	public void AuthenticationFailure(Object sender, Object remote, Exception e, String msg) {
	}

	public void AuthenticationProgress(Object sender, Object remote, int cur, int max, String msg) {
	}

	public void AuthenticationSuccess(Object sender, Object remote, Object result) {
	}
}
