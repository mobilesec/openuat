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
import java.io.OutputStreamWriter;

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
import org.openuat.sensors.SamplesSink;
import org.openuat.sensors.j2me.SymbianTCPAccelerometerReader;
import org.openuat.util.BluetoothRFCOMMServer;
import org.openuat.util.BluetoothSupport;
import org.openuat.util.RemoteConnection;

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

	SymbianTCPAccelerometerReader reader;
	
	RemoteConnection connectionToRemote = null;
	OutputStreamWriter toRemote = null;
	
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
		logBackend.setLogLevel(Level.INFO);
		logger.info("Microlog initialized");
		
		if (! BluetoothSupport.init()) {
			do_alert("Could not initialize Bluetooth API", Alert.FOREVER);
			return;
		}

		try {
			// keep the socket connected for now
			rfcommServer = new BluetoothRFCOMMServer(null, new UUID("b76a37e5e5404bf09c2a1ae3159a02d8", false), "J2ME Test Service", true, false);
			rfcommServer.addAuthenticationProgressHandler(this);
			rfcommServer.startListening();
			logger.info("Finished starting SDP service at " + rfcommServer.getRegisteredServiceURL());
		} catch (IOException e) {
			logger.error("Error initializing BlutoothRFCOMMServer: " + e);
		}
		
		try {
			reader = new SymbianTCPAccelerometerReader(8101);
			reader.addSink(new int[] {0,1,2}, new SamplesSink[] {new SamplesHandler(0), new SamplesHandler(1), new SamplesHandler(2)});
			reader.start();
		} catch (IOException e) {
			logger.error("Error initializing sensor socket: " + e);
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
				
			reader.stop();
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
		toRemote = null;
	}
	
	public void AuthenticationProgress(Object sender, Object remote, int cur, int max, String msg) {
		toRemote = null;
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
        connectionToRemote = (RemoteConnection) res[3];
        try {
			toRemote = new OutputStreamWriter(connectionToRemote.getOutputStream());
		} catch (IOException e) {
			logger.debug("Unable to open stream to remote: " + e);
		}
	}

	private double[] samples = new double[3];
	
	private class SamplesHandler implements SamplesSink {
		private int dim;
		
		SamplesHandler(int dim) {this.dim = dim;}
	
	public void addSample(double sample, int index) {
		samples[dim] = sample;
		if (dim == 2) {
		try {
			//main_list.append(String.valueOf(xxx)+"\t"+String.valueOf(yyy)+"\t"+String.valueOf(zzz), null);
			if (toRemote != null) {
				toRemote.write(String.valueOf(samples[0])+"\t"+String.valueOf(samples[1])+"\t"+String.valueOf(samples[2]) + "\n");
				toRemote.flush();
			}
		} catch (IOException e) {
			logger.error("Error setting up or reading from sensor TCP socket: " + e);
		}
		}
	}

	public void segmentEnd(int index) {
	}

	public void segmentStart(int index) {
	}
	}
}
