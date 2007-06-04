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

import javax.microedition.media.Manager;
import javax.microedition.media.MediaException;
import javax.microedition.media.Player;
import javax.microedition.media.control.VolumeControl;
import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;

import net.sf.microlog.Level;
import net.sf.microlog.appender.FormAppender;
import net.sf.microlog.ui.LogForm;

import org.apache.log4j.Logger;
import org.openuat.authentication.DHWithVerification;
import org.openuat.sensors.SamplesSink_Int;
import org.openuat.sensors.SegmentsSink_Int;
import org.openuat.sensors.TimeSeriesAggregator;
import org.openuat.sensors.j2me.SymbianTCPAccelerometerReader;
import org.openuat.util.BluetoothOpportunisticConnector;
import org.openuat.util.BluetoothSupport;
import org.openuat.util.HostAuthenticationServer;
import org.openuat.util.RemoteConnection;

public class ShakeMIDlet extends MIDlet implements CommandListener {
	List main_list;

	Command exit;

	Command back;

	Command log;

	Display display;
	
	ShakeAuthenticator protocol;
	
	LogForm logForm;

	// our logger
	Logger logger = Logger.getLogger("");

	SymbianTCPAccelerometerReader reader;
	TimeSeriesAggregator aggregator;
	
	RemoteConnection connectionToRemote = null;
	OutputStreamWriter toRemote = null;
	
	// this is used for controlling the volume
	Player player;
	VolumeControl volumeControl;
	
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
		
		// need to get the player and volumeControl objects
		try {
			//InputStream is = getClass().getResourceAsStream("/your.mp3");
			//player = Manager.createPlayer(is,"audio/mpeg");
			player = Manager.createPlayer(Manager.TONE_DEVICE_LOCATOR);
			player.realize();
			//player.setLoopCount(-1);
			//player.prefetch();
			//player.start();
			volumeControl = (VolumeControl) player.getControl("VolumeControl");
		} catch (IOException e) {
			logger.error("Unable to get volume control: " + e);
		} catch (MediaException e) {
			logger.error("Unable to get volume control: " + e);
		}
		
		if (! BluetoothSupport.init()) {
			do_alert("Could not initialize Bluetooth API", Alert.FOREVER);
			return;
		}

		try {
			protocol = new ShakeAuthenticator(BluetoothOpportunisticConnector.getInstance(), this);
			protocol.startListening();
			
			// keep the socket connected for now
			/*rfcommServer = new BluetoothRFCOMMServer(null, new UUID("b76a37e5e5404bf09c2a1ae3159a02d8", false), "J2ME Test Service", true, false);
			rfcommServer.addAuthenticationProgressHandler(this);
			rfcommServer.start();
			logger.info("Finished starting SDP service at " + rfcommServer.getRegisteredServiceURL());*/
		} catch (IOException e) {
			logger.error("Error initializing BlutoothRFCOMMServer: " + e);
		}
		
		reader = new SymbianTCPAccelerometerReader();
		// this is a test/debug sink to stream the values across Bluetooth
		reader.addSink(new int[] {0,1,2}, new SamplesSink_Int[] {
				new TestBTStreamingSamplesHandler(0), 
				new TestBTStreamingSamplesHandler(1), 
				new TestBTStreamingSamplesHandler(2)});
		// this is the "proper" sink
		aggregator = new TimeSeriesAggregator(3, // 3 dimensions 
				SymbianTCPAccelerometerReader.SAMPLERATE/2, // this should be about 1/2s 
				SymbianTCPAccelerometerReader.SAMPLERATE*4, // use segments of 4s length 
				SymbianTCPAccelerometerReader.SAMPLERATE*4);
		// the values are already zero-based
		aggregator.setOffset(0);
		aggregator.setSubtractTotalMean(true);
		/* The integer TimeSeriesAggregator part does _not_ take the square 
		 * roots when computing the magnitudes, so expect to square the 
		 * threshold as well. Additionally, we don't use signals in the range
		 * [-1;1] but ca. [-300;300].
		 * THIS DEPENDS HEAVILY ON THE WINDOW SIZE SET ABOVE IN THE CONSTRUCTOR
		 */
		aggregator.setActiveVarianceThreshold(100
				/*(MotionAuthenticationParameters.activityVarianceThreshold*
				MotionAuthenticationParameters.activityVarianceThreshold) *
				(SymbianTCPAccelerometerReader.VALUE_RANGE*SymbianTCPAccelerometerReader.VALUE_RANGE)*/);
		reader.addSink(new int[] {0, 1, 2}, aggregator.getInitialSinks_Int());
		// also register the activity indicator
		aggregator.addNextStageSamplesSink(new ActivityHandler());
		// and the segments listener
		aggregator.addNextStageSegmentsSink_Int(new SegmentsHandler());
		// finally start the reader, now that everything is registered and the time series chains are ready
		reader.start();
		
			main_list = new List("Select Operation", Choice.IMPLICIT); //the main menu
			exit = new Command("Exit", Command.EXIT, 1);
			back = new Command("Back", Command.BACK, 1);
			log = new Command("Log", Command.ITEM, 2);

			main_list.addCommand(exit);
			main_list.addCommand(log);
			main_list.setCommandListener(this);

			//main_list.append("Find Devices", null);
			
			// announce that we are up and about
			try {
				Manager.playTone(72, 500, 30);
			} catch (MediaException e) {
				logger.error("Unable to play tone");
			}
	}

	// TODO: activate me again when J2ME polish can deal with Java5 sources!
	//@Override
	public void startApp() {
		logForm.setPreviousScreen(main_list);
		display.setCurrent(main_list);
	}

	public void commandAction(Command com, Displayable dis) {
		if (com == exit) { //exit triggered from the main form
			if (protocol != null)
				protocol.stopListening();
			/*if (rfcommServer != null)
				try {
					rfcommServer.stop();
				} catch (InternalApplicationException e) {
					do_alert("Could not de-register SDP service: " + e, Alert.FOREVER);
				}*/
				
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
			// TODO: clean me up here - what should our interface be?
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

	private double[] samples = new double[3];

	
	private class ShakeAuthenticator extends DHWithVerification {
		ShakeMIDlet outer;
		
		ShakeAuthenticator(HostAuthenticationServer server, ShakeMIDlet outer) {
			super(server, false, // the channel is not needed after authentication 
					true, // we support multiple authentications 
					null, false); // no JSSE
			this.outer = outer;
		}

		protected void protocolFailedHook(RemoteConnection remote, Object optionalVerificationId, Exception e, String message) {
			toRemote = null;
		}

		protected void protocolProgressHook(RemoteConnection remote, int cur, int max, String message) {
			toRemote = null;
			// indicate progress in the first phase
			// min 0, max 100
			//volumeControl.setLevel(30);
			/*try {
				Manager.playTone(60, 300, 30);
			} catch (MediaException e) {
				logger.error("Unable to play tone");
			}*/
		}

		protected void protocolSucceededHook(RemoteConnection remote, Object optionalVerificationId, String optionalParameterFromRemote, byte[] sharedSessionKey) {
			try {
				Manager.playTone(60, 100, 30);
				Manager.playTone(62, 100, 30);
				Manager.playTone(64, 100, 30);
				Manager.playTone(62, 100, 30);
				Manager.playTone(60, 100, 30);
				Manager.playTone(64, 100, 30);
			} catch (MediaException e) {
				logger.error("Unable to play tone");
			}
		}

		protected void resetHook(RemoteConnection remote) {
			toRemote = null;
		}

		protected void startVerificationAsync(byte[] sharedAuthenticationKey, String optionalParam, RemoteConnection remote) {
			logger.info("Successful authentication");
	        try {
				toRemote = new OutputStreamWriter(remote.getOutputStream());
				
				// finished DH and connected to the remote
				Display.getDisplay(outer).vibrate(800); // for 800ms
				try {
					Manager.playTone(62, 100, 30);
					Manager.playTone(60, 100, 30);
				} catch (MediaException e) {
					logger.error("Unable to play tone");
				}
			} catch (IOException e) {
				logger.debug("Unable to open stream to remote: " + e);
			}
		}
	}

	private class TestBTStreamingSamplesHandler implements SamplesSink_Int {
		private int dim;
		
		TestBTStreamingSamplesHandler(int dim) {this.dim = dim;}
	
		public void addSample(int sample, int index) {
			samples[dim] = sample;
			if (dim == 2) {
				try {
					//main_list.append(String.valueOf(xxx)+"\t"+String.valueOf(yyy)+"\t"+String.valueOf(zzz), null);
					if (toRemote != null) {
						toRemote.write(String.valueOf(samples[0])+"\t"+String.valueOf(samples[1])+"\t"+String.valueOf(samples[2]) + "\n");
						toRemote.flush();
					}
				} catch (IOException e) {
					logger.error("Error sending samples to RFCOMM channel, dropping connection: " + e);
					toRemote = null;
				}
			}
		}

		public void segmentEnd(int index) {
			// not interested in segments when only forwarding to BT
		}

		public void segmentStart(int index) {
			// not interested in segments when only forwarding to BT
		}
	}
	
	private class ActivityHandler implements SamplesSink_Int {
		public void addSample(int sample, int index) {
			// ignore
		}

		public void segmentEnd(int index) {
			// announce quiescent
			try {
				Manager.playTone(60, 100, 30);
			} catch (MediaException e) {
				logger.error("Unable to play tone");
			}
		}

		public void segmentStart(int index) {
			// announce active
			try {
				Manager.playTone(72, 100, 30);
			} catch (MediaException e) {
				logger.error("Unable to play tone");
			}
		}
	}
	
	private class SegmentsHandler implements SegmentsSink_Int {
		public void addSegment(int[] segment, int startIndex) {
			// announce shaking complete
			try {
				Manager.playTone(60, 100, 30);
				Manager.playTone(62, 100, 30);
				Manager.playTone(64, 100, 30);
			} catch (MediaException e) {
				logger.error("Unable to play tone");
			}
		}
	}
}
