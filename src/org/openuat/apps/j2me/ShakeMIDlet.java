/* Copyright Rene Mayrhofer
 * File created 2007-05-03
 * Initial public release 2007-12-04
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * For commercial usage, please contact the Lancaster University 
 * Intellectual Property department. Academic and open source use is
 * hereby granted without requiring any further permission.
 */
package org.openuat.apps.j2me;

import java.io.IOException;
import java.io.OutputStreamWriter;

import javax.bluetooth.BluetoothStateException;
import javax.bluetooth.LocalDevice;
import javax.bluetooth.UUID;
import javax.microedition.media.Manager;
import javax.microedition.media.MediaException;
import javax.microedition.media.Player;
import javax.microedition.media.control.VolumeControl;
import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;

import net.sf.microlog.Level;
import net.sf.microlog.appender.FormAppender;
import net.sf.microlog.ui.LogForm;

import org.apache.commons.codec.binary.Hex;
import org.apache.log4j.Logger;
import org.openuat.authentication.AuthenticationProgressHandler;
import org.openuat.authentication.KeyManager;
import org.openuat.authentication.accelerometer.ShakeWellBeforeUseProtocol1;
import org.openuat.sensors.SamplesSink_Int;
import org.openuat.sensors.SamplesSource;
import org.openuat.sensors.TimeSeriesAggregator;
import org.openuat.sensors.j2me.NokiaAccelerometerReader;
import org.openuat.util.BluetoothOpportunisticConnector;
import org.openuat.util.BluetoothRFCOMMChannel;
import org.openuat.util.BluetoothRFCOMMServer;
import org.openuat.util.BluetoothSupport;
import org.openuat.util.HostAuthenticationServer;
import org.openuat.util.LineReaderWriter;
import org.openuat.util.ProtocolCommandHandler;
import org.openuat.util.RemoteConnection;

public class ShakeMIDlet extends MIDlet implements CommandListener {
	private final static String IMAGE_GOOD = "/button_ok.png";
	private final static String IMAGE_BAD = "/button_cancel.png";
	private final static String IMAGE_RETRY = "/cache.png";
	
	/** Code for the Ubicomp 2007 Demo. Will be ignored if the preprocessor defines are not set. */
	private static boolean FIXED_DEMO_MODE = false;
	private final static String FIXED_DEMO_UUID = "b76a37e5e5404bf09c2a1ae3159a02d8";
	private final static int FIXED_DEMO_CHANNELNUM = 2;
	private final static byte[] FIXED_DEMO_SHAREDKEY = {
		0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
		0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f,
		0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17,
		0x18, 0x19, 0x1a, 0x1b, 0x1c, 0x1d, 0x1e, 0x1f,
	};
	private static String FIXED_DEMO_PEER_1;
	private static String FIXED_DEMO_PEER_2;
	static {
//#if cfg.shakingDemoMode
		FIXED_DEMO_MODE = true;
		//#= FIXED_DEMO_PEER_1 = "${ demo.peer1 }";
		//#= FIXED_DEMO_PEER_2 = "${ demo.peer2 }";
//#endif
	}
	
	public final static int HoldOffConcurrentVerificationDelay = 20000;
	
	public final static String Command_Debug_Streaming = "DEBG_Stream";
	
	public final static float CoherenceThresholdSucceed = 0.45f;
	public final static float CoherenceThresholdFailHard = 0.15f;
	
	Form mainForm;
	
	Image good, bad, retry;
	ImageItem goodOrBad; 
	StringItem status, lastValue;
	String previousStatus = "";
	
	Gauge lastMatch;
	
	Command exit, log;
	
	Display display;
	
	ShakeAuthenticator protocol;
	
	// these are only needed for fixed demo mode...
	BluetoothRFCOMMServer rfcommServer;
	DemoModeConnector connector;
	boolean connected = false;
	
	LogForm logForm;

	// our logger
	Logger logger = Logger.getLogger("");

	SamplesSource reader;
	TimeSeriesAggregator aggregator;
	
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

//		if (!FIXED_DEMO_MODE) {
			net.sf.microlog.Logger logBackend = net.sf.microlog.Logger.getLogger();
			logForm = new LogForm();
			logForm.setDisplay(display);
			FormAppender appender = new FormAppender(logForm);
			logBackend.addAppender(appender);
			//logBackend.addAppender(new RecordStoreAppender());
			logBackend.setLogLevel(Level.WARN);
			logger.info("Microlog initialized");
//		}
		
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
		
		mainForm = new Form("Shake Me");
		exit = new Command("Exit", Command.EXIT, 1);
		mainForm.addCommand(exit);
//		if (!FIXED_DEMO_MODE) {
			log = new Command("Log", Command.ITEM, 2);
			mainForm.addCommand(log);
//		}
		mainForm.setCommandListener(this);

		status = new StringItem("Status:", "initializing");
		status.setLayout(Item.LAYOUT_LEFT | Item.LAYOUT_TOP);
		mainForm.append(status);
		try {
			good = Image.createImage(IMAGE_GOOD);
			bad = Image.createImage(IMAGE_BAD);
			retry = Image.createImage(IMAGE_RETRY);
		} catch (IOException e1) {
			logger.warn("Could not load image: " + e1);
		}
		goodOrBad = new ImageItem(null, bad, Item.LAYOUT_DEFAULT, null);
		mainForm.append(goodOrBad);
		
		lastMatch = new Gauge("Last match", false, 99, 0);
		lastMatch.setLayout(Item.LAYOUT_EXPAND | Item.LAYOUT_NEWLINE_AFTER);
		mainForm.append(lastMatch);
		lastValue = new StringItem("Last value:", "0 / " + 
				Float.toString(CoherenceThresholdSucceed*100) + "/" + 
				Float.toString(CoherenceThresholdFailHard*100));
		lastValue.setLayout(Item.LAYOUT_DEFAULT);
		mainForm.append(lastValue);
		
		if (!startBackgroundTasks())
			return;
		
		status.setText("unconnected");
		
		// announce that we are up and about
		try {
			Manager.playTone(72, 500, 30);
		} catch (MediaException e) {
			logger.error("Unable to play tone");
		}
	}

	//@Override
	public void startApp() {
		if (logForm != null) {
			logForm.setPreviousScreen(mainForm);
		}
		display.setCurrent(mainForm);
	}

	public void commandAction(Command com, Displayable dis) {
		if (com == exit) { //exit triggered from the main form
			status.setText("exiting (server)");
			stopBackgroundTasks();
			
			status.setText("exiting (channels)");
			// and MIDlet closing sequence
			destroyApp(false);

			status.setText("exiting (final)");
			notifyDestroyed();
		}
		else if (com == log) {
			display.setCurrent(logForm);
		}

	}

	private void do_alert(String msg, int time_out) {
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
	
	private boolean startBackgroundTasks() {
		if (! BluetoothSupport.init()) {
			do_alert("Could not initialize Bluetooth API", Alert.FOREVER);
			return false;
		}

		try {
			if (!FIXED_DEMO_MODE) {
				// using split phases, disconnect the channel after host authentication
				BluetoothOpportunisticConnector.setKeepConnected(false);
				BluetoothOpportunisticConnector bt = BluetoothOpportunisticConnector.getInstance();
				protocol = new ShakeAuthenticator(bt, this);
				bt.setKeyManager(protocol.getKeyManager());
				// this is an additional command handler for streaming the acceleration values
				bt.addProtocolCommandHandler(Command_Debug_Streaming, 
						new TestBTStreamingCommandHandler());
				protocol.startListening();
				logger.warn("Opened Bluetooth RFCOMM service at " + bt.getRegisteredServiceURL());
			}
			else {
				// hard-code a simple RFCOMM server that stays connected
				// ATTENTION! setting the channel number will make startListening() crash
				rfcommServer = new BluetoothRFCOMMServer(null, /*new Integer(FIXED_DEMO_CHANNELNUM),*/ 
						new UUID(FIXED_DEMO_UUID, false), "Shake Test Service", 
						ShakeWellBeforeUseProtocol1.KeyAgreementProtocolTimeout, true, false);
				protocol = new ShakeAuthenticator(rfcommServer, this);
				protocol.startListening();
				logger.info("Finished starting SDP service for demo mode at " + rfcommServer.getRegisteredServiceURL());
				connector = new DemoModeConnector();
				// register the connector for (failure) events so that it will get notified when disconnection happens
				protocol.addAuthenticationProgressHandler(connector);
				connector.start();
			}
		} catch (IOException e) {
			logger.error("Error initializing BlutoothRFCOMMServer: " + e);
			return false;
		}
		
		reader = new NokiaAccelerometerReader();
		// this is a test/debug sink to stream the values across Bluetooth - THIS CAN BE DISABLED LATER ON
		reader.addSink(new int[] {0,1,2}, new SamplesSink_Int[] {
				new TestBTStreamingSamplesHandler(0), 
				new TestBTStreamingSamplesHandler(1), 
				new TestBTStreamingSamplesHandler(2)});
		// this is the "proper" sink
		aggregator = new TimeSeriesAggregator(3, // 3 dimensions 
				NokiaAccelerometerReader.SAMPLERATE/2, // this should be about 1/2s 
				NokiaAccelerometerReader.SAMPLERATE*4, // use segments of 4s length 
				NokiaAccelerometerReader.SAMPLERATE*4);
		aggregator.setParameters(reader.getParameters_Int());
		/* The integer TimeSeriesAggregator part does _not_ take the square 
		 * roots when computing the magnitudes, so expect to square the 
		 * threshold as well. Additionally, we don't use signals in the range
		 * [-1;1] but [-1024;1024]. The variance threshold is computed over
		 * THIS DEPENDS HEAVILY ON THE WINDOW SIZE SET ABOVE IN THE CONSTRUCTOR
		 */
		aggregator.setActiveVarianceThreshold(5000*4
				/*(ShakeWellBeforeUseParameters.activityVarianceThreshold*
				ShakeWellBeforeUseParameters.activityVarianceThreshold) *
				(SymbianTCPAccelerometerReader.VALUE_RANGE*SymbianTCPAccelerometerReader.VALUE_RANGE)*/);
		reader.addSink(new int[] {0, 1, 2}, aggregator.getInitialSinks_Int());
		// also register the activity indicator - THIS CAN BE DISABLED LATER ON
		aggregator.addNextStageSamplesSink(new ActivityHandler());
		// and the segments listener
		aggregator.addNextStageSegmentsSink_Int(protocol);
		// finally start the reader, now that everything is registered and the time series chains are ready
		reader.start();
		
		return true;
	}
	
	private void stopBackgroundTasks() {
		if (protocol != null)
			protocol.stopListening();
		
		if (FIXED_DEMO_MODE) {
			Thread tmp = connector;
			connector = null;
			if (tmp != null) {
				tmp.interrupt();
				try {
					tmp.join();
				} catch (InterruptedException e) {
					// don't care
				}
			}
		}
		
		// in case we are streaming, stop that
		status.setText("exiting (streaming)");
		if (toRemote != null) {
			try {
				toRemote.close();
			} catch (IOException e) {
				// just ignore here - we're shutting down anyways
			}
			toRemote = null;
		}
		// proper shutdown all open channels
		status.setText("exiting (channels)");
		BluetoothRFCOMMChannel[] openChannels = BluetoothRFCOMMChannel.getOpenChannels();
		for (int i=0; i<openChannels.length; i++)
			openChannels[i].close();
		// stop reading from the Symbian accelerometer wrapper
		status.setText("exiting (reader)");
		reader.stop();
	}

	//@Override
	public void pauseApp() {
		// nothing to do when the app is paused, leave the background actions running
	}

	//@Override
	public void destroyApp(boolean unconditional) {
		// just try to close all channels to shutdown quickly, all other resources should be freed automatically
		BluetoothRFCOMMChannel.shutdownAllChannels();
	}

	private class ShakeAuthenticator extends ShakeWellBeforeUseProtocol1 {
		ShakeMIDlet outer;
		
		ShakeAuthenticator(HostAuthenticationServer server, ShakeMIDlet outer) {
			super(server,  
					FIXED_DEMO_MODE, // don't keep channel open (unless in demo mode)
					true, // we support multiple authentications 
					CoherenceThresholdSucceed,
					CoherenceThresholdFailHard,
					32,
					false); // no JSSE
			this.outer = outer;
			setContinuousChecking(FIXED_DEMO_MODE);
			if (FIXED_DEMO_MODE)
				this.staticAuthenticationKey = FIXED_DEMO_SHAREDKEY;
		}
		
		KeyManager getKeyManager() {
			return keyManager;
		}

		protected void protocolFailedHook(boolean failHard, RemoteConnection remote, Object optionalVerificationId, Exception e, String message) {
			String statusText = failHard ? "FAILURE" : "NOT SIMILAR ENOUGH - RETRY";
			
			goodOrBad.setImage(failHard ? bad : retry);
			status.setText(statusText);
			previousStatus = statusText;
			lastMatch.setValue((int) (getLastCoherenceMean() * 100));
			lastValue.setText(Float.toString((float) getLastCoherenceMean() * 100) + " / " + 
					Float.toString(CoherenceThresholdSucceed*100) + "/" + 
					Float.toString(CoherenceThresholdFailHard*100));
			// I want to beep
		}

		protected void protocolSucceededHook(RemoteConnection remote, Object optionalVerificationId, String optionalParameterFromRemote, byte[] sharedSessionKey) {
			goodOrBad.setImage(good);
			status.setText("SUCCESS");
			previousStatus = "SUCCESS";
			lastMatch.setValue((int) (getLastCoherenceMean() * 100));
			lastValue.setText(Float.toString((float) getLastCoherenceMean() * 100) + " / " + 
					Float.toString(CoherenceThresholdSucceed*100) + "/" + 
					Float.toString(CoherenceThresholdFailHard*100));
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

		/** In "normal" opportunistic mode, this only remembers the remote 
		 * channel number that should be used when "calling" this remote for
		 * key verification (the channel is otherwise unknown when the key
		 * agreement that triggered this hook was incoming).
		 * 
		 * In demo mode, it will also start the continuous background
		 * verification thread.
		 */
		protected void startVerificationAsync(byte[] sharedAuthenticationKey, String optionalParam, RemoteConnection remote) {
			if (logger.isDebugEnabled())
				logger.debug("Successful key agreement with " + remote.getRemoteName() + 
					", auth key is " + new String(Hex.encodeHex(sharedAuthenticationKey)));
			status.setText("key agreed");
			// finished DH and connected to the remote
			Display.getDisplay(outer).vibrate(800); // for 800ms
			try {
				Manager.playTone(62, 100, 30);
				Manager.playTone(60, 100, 30);
			} catch (MediaException e) {
				logger.error("Unable to play tone");
			}
			
			// remember the remote channel number
			if (optionalParam != null && 
					optionalParam.startsWith("btspp://") &&
					optionalParam.indexOf(':', 8) == 20 &&
					(optionalParam.indexOf(';') >= 22 || 
					 (optionalParam.length() <= 23 && optionalParam.indexOf(';') == -1))) {
				String remoteDeviceAddress = optionalParam.substring(8, 20);
				int end = optionalParam.indexOf(';') > 0 ? optionalParam.indexOf(';') : optionalParam.length();
				int remoteChannelNumber = Integer.parseInt(optionalParam.substring(21, end));
				if (logger.isDebugEnabled())
					logger.debug("Parsed remote device address '" + remoteDeviceAddress + 
						"' and channel " + remoteChannelNumber + " from remote URL parameter '" +
						optionalParam + "'");
				BluetoothRFCOMMChannel channel = (BluetoothRFCOMMChannel) remote;
				if (! channel.getRemoteAddress().equals(remoteDeviceAddress)) {
					if (logger.isDebugEnabled())
						logger.debug("Device address of remote is '" + channel.getRemoteAddress() +
							"', but parsed '" + remoteDeviceAddress + 
							"' from URL parameter, assuming that this is the client and thus not storing channel number");
				}
				else {
					if (channel.getRemoteChannelNumber() != -1 && 
						channel.getRemoteChannelNumber() != remoteChannelNumber)
						logger.warn("Currently known remote channel number of remote is '" + channel.getRemoteChannelNumber() +
							"', but parsed '" + remoteChannelNumber + 
							"' from URL parameter, something is very wrong here");
					// and finally remember the parsed number (if it was already in there, it won't change)
					logger.info("Storing remote channel number " + remoteChannelNumber + " for remote " + remote);
					channel.setRemoteChannelNumber(remoteChannelNumber);
				}
			}
			else {
				if (logger.isDebugEnabled())
					logger.debug("Could not parse URL '" + optionalParam + "'");
				if (((BluetoothRFCOMMChannel) remote).getRemoteChannelNumber() == -1)
					logger.warn("Could neither parse remote URL parameter nor already have a remote channel number to use, thus outgoing key verification to this host will not work!");
			}
			
			if (FIXED_DEMO_MODE)
				super.startVerificationAsync(sharedAuthenticationKey, optionalParam, remote);
			else
				logger.info("Not starting background key verification, but waiting for segment");
		}
		
		//@Override
		public void addSegment(int[] segment, int startIndex) {
			// announce shaking complete
			status.setText("verifying");
			// and be sure to overwrite the status when we don't reach a conclusion
			previousStatus = "NO PEER DEVICE";
			try {
				Manager.playTone(60, 100, 30);
				Manager.playTone(62, 100, 30);
				Manager.playTone(64, 100, 30);
			} catch (MediaException e) {
				logger.error("Unable to play tone");
			}

			// if debugging is active, also push that segment
			if (toRemote != null) {
				StringBuffer tmp = new StringBuffer();
				for (int i=0; i<segment.length; i++)
					tmp.append(segment[i] + " ");
				tmp.append('\n');
				try {
					toRemote.write(tmp.toString());
					toRemote.flush();
				} catch (IOException e) {
					logger.error("Could not push segment to debug stream");
				}
			}

			// and fire off the normal protocol
			super.addSegment(segment, startIndex);
			// and try to verify with all hosts in that state
			RemoteConnection[] hostsWaitingForVerification = keyManager.getHostsInState(KeyManager.STATE_VERIFICATION);
			// let the channels be opened in the background threads instead of doing it here
			startConcurrentVerifications(hostsWaitingForVerification, true);
		}

		//@Override
		protected boolean incomingVerificationRequestHook(RemoteConnection remote) {
			connected = true;
			status.setText("connected");
			return true;
		}
		
		//@Override
		protected int holdOutgoingVerificationRequestHook(RemoteConnection remote) {
			/* This is a small but hopefully effective hack to prevent two devices 
			 * from trying to contact each other at the same time: the higher MAC 
			 * address backs off for the first try, and will only try if not contacted
			 * in the mean time.
			 */
			String localAddress;
			try {
				localAddress = LocalDevice.getLocalDevice().getBluetoothAddress();
				String remoteAddress = (String) remote.getRemoteAddress();
				if (localAddress.compareTo(remoteAddress) > 0) {
					if (logger.isInfoEnabled())
						logger.info("My Bluetooth address '" + localAddress +
							"' is higher than the remote address to connect to '" + 
							remoteAddress + "', backing off and waiting for remote to establish verification connection for " +
							HoldOffConcurrentVerificationDelay + "ms");
					return HoldOffConcurrentVerificationDelay;
				}
				else
					return 0;
			} catch (BluetoothStateException e) {
				// if we can't decide, better not wait
				return 0;
			} catch (IOException e) {
				// if we can't decide, better not wait
				return 0;
			}
		}		
	}
	
	private class DemoModeConnector extends Thread implements AuthenticationProgressHandler {
		BluetoothRFCOMMChannel conn = null;
		
		public void run() {
			String localAddr = null, remoteAddr = null;
			try {
				localAddr = LocalDevice.getLocalDevice().getBluetoothAddress();
				if (localAddr.equals(FIXED_DEMO_PEER_1))
					remoteAddr = FIXED_DEMO_PEER_2;
				else if (localAddr.equals(FIXED_DEMO_PEER_2))
					remoteAddr = FIXED_DEMO_PEER_1;
				else {
					logger.error("Aieh, my own address (" + localAddr + 
							") is neither " + FIXED_DEMO_PEER_1 + " nor " +
							FIXED_DEMO_PEER_2 + ", can't orient - aborting demo mode");
					return;
				}
			} catch (BluetoothStateException e1) {
				logger.error("Can't get my own address, aborting demo mode");
			}
			if (localAddr.compareTo(remoteAddr) > 0) {
				if (logger.isInfoEnabled())
					logger.info("My Bluetooth address '" + localAddr +
						"' is higher than the remote address to connect to '" + 
						remoteAddr + "', aborting and waiting for remote to connect");
				return;
			}
			
			logger.info("Starting in demo mode as " + localAddr +
					", trying to connect to peer " + remoteAddr);
			
			while (connector != null) {
				// if already connected, wait until disconnection
				while (connector != null && (connected || protocol.isAsyncProtocolRunning()))
					try {
						sleep(500);
					} catch (InterruptedException e1) {
						// ignore
					}
				
				logger.info("Trying to connect to " + remoteAddr + 
						" channel " + FIXED_DEMO_CHANNELNUM);
				try {
					/* construct our own serviceURL, because "master=true"
					 * seems to generate a "feature not supported" exception
					 * from Symbian
					 */
					status.setText("connecting");
					String serviceURL = "btspp://" + remoteAddr + ":" + 
						FIXED_DEMO_CHANNELNUM + ";authenticate=false;encrypt=false";
					conn = new BluetoothRFCOMMChannel(serviceURL);
					conn.open();
					// ok, connected - get the host into verification mode
					LineReaderWriter.println(conn.getOutputStream(), ShakeWellBeforeUseProtocol1.MotionVerificationCommand);
					// and consume its first line
					LineReaderWriter.readLine(conn.getInputStream());
					// and start verifying
					protocol.startVerificationAsync(FIXED_DEMO_SHAREDKEY, 
							null, conn);
					connected = true;
					status.setText("connected");
					logger.info("Successfully opened channel to " + remoteAddr +
							", demo mode connector thread now waiting for connection to terminate");
					
					while (connector != null && connected && conn.isOpen()) {
						try {
							Thread.sleep(500);
						} catch (InterruptedException e) {
							// ignore
						}
					}
					logger.info("Connection to " + remoteAddr + " has been closed");
					connected = false;
				} catch (IOException e) {
					logger.error("Unable to connect to " + remoteAddr + 
							" and start verification (will retry): " + e);
					conn = null;
				}
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					// don't care
				}
			}
		}

		public void AuthenticationFailure(Object sender, Object remote, Exception e, String msg) {
			logger.warn("Disconnected from " + remote + ": " + msg);
			// on authentication failure, the remote connection is terminated, so may need to reconnect
			connected = false;
		}

		public void AuthenticationProgress(Object sender, Object remote, int cur, int max, String msg) {
			// not interested in this method
		}

		public void AuthenticationSuccess(Object sender, Object remote, Object result) {
			// not interested in this method - the hook will be called anyway
		}

		public boolean AuthenticationStarted(Object sender, Object remote) {
			// not interested in this method for the demo mode
			return true;
		}
	}
	
	private double[] samples = new double[3];
	OutputStreamWriter toRemote = null;
	private class TestBTStreamingCommandHandler implements ProtocolCommandHandler {
		public boolean handleProtocol(String firstLine, RemoteConnection remote) {
			try {
				toRemote = new OutputStreamWriter(remote.getOutputStream());
				logger.info("Opened output stream for debugging");
				status.setText("DEBUG streaming");
				// just wait in this incoming handler thread
				while (toRemote != null) {
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						// just ignore
					}
				}
			} catch (IOException e) {
				logger.debug("Unable to open stream to remote: " + e);
			}
			return true;
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
			status.setText(previousStatus);
			try {
				Manager.playTone(60, 100, 30);
			} catch (MediaException e) {
				logger.error("Unable to play tone");
			}
		}

		public void segmentStart(int index) {
			// announce active
			previousStatus = status.getText();
			status.setText("moving");
			try {
				Manager.playTone(72, 100, 30);
			} catch (MediaException e) {
				logger.error("Unable to play tone");
			}
		}
	}
}
