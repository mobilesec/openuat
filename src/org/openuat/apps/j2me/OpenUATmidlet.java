/* Copyright Rene Mayrhofer
 * File created 2008-06-25
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.apps.j2me;

import java.io.ByteArrayInputStream;
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
import javax.microedition.media.Manager;
import javax.microedition.media.MediaException;
import javax.microedition.media.Player;
import javax.microedition.media.control.ToneControl;
import javax.microedition.media.control.VolumeControl;
import javax.microedition.midlet.MIDlet;

import net.sf.microlog.Level;
import net.sf.microlog.appender.FormAppender;
import net.sf.microlog.ui.LogForm;

import org.apache.commons.codec.binary.Hex;
import org.apache.log4j.Logger;
import org.codec.audio.j2me.AudioUtils;
import org.codec.audio.j2me.PlayerPianoJ2ME;
import org.codec.mad.MadLib;
import org.openuat.authentication.AuthenticationProgressHandler;
import org.openuat.authentication.HostProtocolHandler;
import org.openuat.authentication.OOBChannel;
import org.openuat.authentication.OOBMessageHandler;
import org.openuat.authentication.exceptions.InternalApplicationException;
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
 *
 */
public class OpenUATmidlet extends MIDlet implements CommandListener,
BluetoothPeerManager.PeerEventsListener, AuthenticationProgressHandler{

	List main_list;

	List dev_list;

//	List serv_list;

	Command exit;

	Command back;

	Command log;

	Command auth;

	Display display;

	BluetoothPeerManager peerManager;

	BluetoothRFCOMMServer rfcommServer;

	LogForm logForm;

	/** Tells whether this device initiated the set-up or not. If it did, it will be the one coordinating the verification process.	 */
	private boolean initiator = false;

	/** The key resulting from the message exchange during the authentication process */
	private byte [] authKey;
	
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
			rfcommServer = new BluetoothRFCOMMServer(null, new UUID("447d8ecbefea4b2d93107ced5d1bba7e", false), "J2ME Test Service", 
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

		initGui();
	}

	private void initGui() {
		main_list = new List("Select Operation", Choice.IMPLICIT); //the main menu
		dev_list = new List("Select Device", Choice.IMPLICIT); //the list of devices
//		serv_list = new List("Available Services", Choice.IMPLICIT); //the list of services
		exit = new Command("Exit", Command.EXIT, 1);
		back = new Command("Back", Command.BACK, 1);
		log = new Command("Log", Command.ITEM, 2);

		main_list.addCommand(exit);
		main_list.addCommand(log);
		main_list.setCommandListener(this);
		dev_list.addCommand(exit);
		dev_list.addCommand(log);
		dev_list.setCommandListener(this);
//		serv_list.addCommand(exit);
//		serv_list.addCommand(back);
//		serv_list.setCommandListener(this);

		main_list.append("Find Devices", null);
		main_list.append("pair with my N95", null);
		main_list.append("pair with my N82", null);
		main_list.append("pair with computer", null);
	}

	private void initLogger() {
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
		logForm.setPreviousScreen(main_list);

//		main_list.append("----", null);
//		main_list.append("Server started, waiting for incomming connections", null);
		display.setCurrent(main_list);
		
		dummyRecord();
		dummyRecord();
	}
	
	private void dummyRecord() {
		AudioChannel.prepare();
		
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
				}if (main_list.getSelectedIndex() == 1) { //demo - hack for demo purposes
					String btAdd = "001C9AF755EB";
					connectTo(btAdd, 5);
				}else if (main_list.getSelectedIndex() == 2) { //demo
					String btAdd = "001DFD71C3C3";
					connectTo(btAdd, 5);
				}else if(main_list.getSelectedIndex() == 3) { //demo
					String btAdd = "001F5B7B16F7";
					connectTo(btAdd, 2);
				}
			}
			if (dis == dev_list) { //select triggered from the device list
				if (dev_list.getSelectedIndex() >= 0) { //find services
					
					RemoteDevice[] devices = peerManager.getPeers();
					RemoteDevice peer = devices[dev_list.getSelectedIndex()];
					
					String btAdd = peer.getBluetoothAddress();
					connectTo(btAdd, 5);
				}
			}
		}
		else if (com == back) {
			display.setCurrent(main_list);
		}
		else if (com == log) {
			display.setCurrent(logForm);
		}

	}

	/**
	 * Starts the connection and authentication process to the given Bluetooth address.
	 * @param btAdd
	 */
	private void connectTo(String btAdd, int channel) {
		boolean keepConnected = true;
		String optionalParam = null;  
		logger.info("starting authentication ");
		try {
			initiator = true;
			BluetoothRFCOMMChannel c = new BluetoothRFCOMMChannel(btAdd, channel);
			c.open();
			HostProtocolHandler.startAuthenticationWith(c, this, 20000, keepConnected, optionalParam, true);

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
		display.setCurrent(main_list);
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
			Form chooseMethod = new Form("How to verify");
			chooseMethod.append("Key exchange finished successfully. \n How do you want to verify the key transfer? \n");
			List options = new List("Method", List.IMPLICIT, new String[]{"visual","audio","slowcode","madlib"}, null);
//			options.addCommand(cmd)
			options.setCommandListener(new KeyVerifier(authKey, connectionToRemote, this));

			options.addCommand(back);
			options.addCommand(exit);

			display.setCurrent(options);
		}


	}
	
	public void verificationDone(){
		Display.getDisplay(this).setCurrent(new Form("authentication completed successfully"));
	}

//	boolean authentic = true;

//@Override
	public void serviceListFound(RemoteDevice remoteDevice, Vector services) {
		// TODO Auto-generated method stub
		
	}
}

class KeyVerifier implements CommandListener, OOBMessageHandler  {
	
	/** verification methods */
	public static final String VISUAL = "VISUAL";
	public static final String AUDIO = "AUDIO";
	public static final String SLOWCODEC = "SLOWCODEC";
	public static final String MADLIB = "MADLIB";
	
	
	/** synchronization commands */
	public static final String VERIFY = "VERIFY";
	public static final String ACK = "OK";
	
	/** prepares to transmit the code on the previously selected channel */
	public static final String PREPARE = "PREPARE";
	
	/** tells to start transmitting */
	public static final String START = "START";
	
	/** informs that transmission was completed */
	public static final String DONE = "DONE";
	
	/** informs that verification was successful */
	public static final String SUCCESS = "SUCCESS";
	
	Logger logger = Logger.getLogger("");
	byte [] authKey;
	OpenUATmidlet mainProgram;
	RemoteConnection connectionToRemote;
	
	public KeyVerifier(byte [] authKey, RemoteConnection connectionToRemote, OpenUATmidlet mainProgram ) {
		this.authKey = authKey;
		this.mainProgram = mainProgram;
		this.connectionToRemote = connectionToRemote;
	}
	
	private boolean send(String message) {
		try {
			LineReaderWriter.println(connectionToRemote.getOutputStream(), message);
			return true;
		} catch (IOException e) {
			logger.debug("Unable to open stream to remote: " + e);
			mainProgram.do_alert("Unable to open stream to remote to send message"+message, 6000);
		}
		return false;

	}
	
	private String readLine() {
		String line = null;
		try {
			line = LineReaderWriter.readLine(connectionToRemote.getInputStream());
		} catch (IOException e) {
			logger.debug("Unable to open stream to remote: " + e);
		}
		return line;

	}
	public void commandAction(Command com, Displayable arg1) {
		List option = (List) arg1;
		mainProgram.do_alert("Invoked command"+com, 1000);
		if (com.getCommandType() == Command.EXIT){
			//mainProgram.destroyApp(unconditional)
		}
		else if (option.getSelectedIndex() == 0){
//		if (com.getLabel().equals("visual-send")){
			logger.info("visual-send");
			send(VERIFY);
			send(VISUAL);
			
			String response = readLine();
			//System.out.println("response "+response);
			if (response.equals(ACK)){
				send (START);
				getInputViaVisualChannel();
			}
//			sendViaVisualChannel();
//		}else if (com.getLabel().equals("visual-capture")){
			
		}else if (option.getSelectedIndex() == 1){
			send(VERIFY);
			send(AUDIO);
			
			String response = readLine();
			//System.out.println("response "+response);
			if (response.equals(ACK)){
				
				AudioChannel verifier = new AudioChannel(mainProgram);
				verifier.setOOBMessageHandler(this);
				send (START);
				verifier.capture();
				String done = readLine();
				if(done.equals(DONE)){
					byte recorded [] = verifier.finishCapturing();
					byte decoded [] = verifier.decodeAudio(recorded);
					handleOOBMessage(AudioChannel.AUDIO_CHANNEL, decoded);
				}
			}
//			if(com.getLabel().equals("audio-send")){
//			sendViaAudioChannel();
//		}else if(com.getLabel().equals("audio-capture")){
			authenticateAudio();
		}else if (option.getSelectedIndex() == 2){ 
//			method = SLOWCODEC;
//			if(com.getLabel().equals("slowcodec")){
			verify(authKey);
		}else if (option.getSelectedIndex() == 3){
//			method = MADLIB;
//			if(com.getLabel().equals("madlib")){
			MadLib madLib = new MadLib();
			try {
				String text = madLib.GenerateMadLib(Hash.doubleSHA256(authKey, false), 0, 5);
				Form madlib = new Form("madlib");
				madlib.append(text);
//				madlib.addCommand(back);
//				madlib.addCommand(exit);
//				display.setCurrent(madlib);
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InternalApplicationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		
	}
	private void sendViaAudioChannel() {
		byte [] trimmedHash = new byte[8];
		 byte[] hash;

		 try {
			hash = Hash.doubleSHA256(authKey, false);
			System.arraycopy(hash, 0, trimmedHash, 0, 8);
			byte[] toSend = new String(Hex.encodeHex(trimmedHash)).getBytes();
			//we add some padding after the hash
			byte [] padded = new byte[toSend.length + 10];
			System.arraycopy(toSend, 0, padded, 0, toSend.length);
			byte [] sound = AudioUtils.encodeFileToWav(new ByteArrayInputStream(padded));

			Player player = Manager.createPlayer(new ByteArrayInputStream(sound), "encoding=pcm&rate=44100&bits=8&channels=1&endian=little&signed=true");

			player.prefetch(); 
			player.realize(); 
			player.start();
		 }catch (InternalApplicationException e) {
				logger.error(e);
			} catch (IOException e) {
				logger.error(e);
		} catch (MediaException e) {
			logger.error(e);
		}
		
	}

	/**
		 *  we handle visual and audio separately because the channels might have different properties and transmission capacity.
		 * 
		 * @param remoteHash
		 */
		private void handleAudioMessage(byte[] data) {
			logger.info("audio verification result ---------- " );
	
			byte hash[];
			try {
				hash = Hash.doubleSHA256(authKey, false);
				byte [] trimmedHash = new byte[8];
				System.arraycopy(hash, 0, trimmedHash, 0, 8);
				String localHash = new String(Hex.encodeHex(trimmedHash));
				String remoteHash = new String(data);
				logger.info("local hash: " + localHash);
				logger.info("remote hash: " + remoteHash);
	
	//			if(remoteHash.equals(localHash)){
	//				logger.info("verification ended");
	//				logger.info("hashes are equal");
	//
	//				main_list.append("verification successfull", null);
	//
	//			}else{
	//				logger.info("hashes differ");
					boolean equal = true;
					byte [] toCompare = localHash.getBytes();
					for (int i = 0; i < toCompare.length; i++) {
						if(data[i]!=toCompare[i])
						{
							equal = false;
							break;
						}
					}
					if (equal){
						logger.info("verification successful. first bits are the same.");
//						main_list.append("----", null);
//						main_list.append("Successful pairing", null);
						send(SUCCESS);
					}else{
//						main_list.append("authentication failed", null);
					}
	
	//			}
//				main_list.append("check out the log for details", null);
//				display.setCurrent(main_list);
			} catch (InternalApplicationException e) {
				logger.error(e);
			}
	
		}

	//	boolean authentic = true;
	
	/**
	 * Plays a melodic sound to verify the hash.
	 */
	private void verify(byte [] key){
	
		try{ 
			byte hash [] = Hash.doubleSHA256(authKey, false);
			Player p = Manager.createPlayer(Manager.TONE_DEVICE_LOCATOR); 
			p.realize(); 
			ToneControl c = (ToneControl)p.getControl("ToneControl"); 
	
			String score = PlayerPianoJ2ME.MakeInput(hash);
			//c.setSequence(PlayerPiano.PlayerPiano("/2 - Gb + . - E - E + Db + Fb + Cb - Db + Fb Cb"));
			c.setSequence(PlayerPianoJ2ME.PlayerPiano(score));
			
			   // get volume control for player and set volume to max
			VolumeControl  vc = (VolumeControl) p.getControl("VolumeControl");
			   if(vc != null) {
				   logger.info("volume level: "+vc.getLevel());
			      vc.setLevel(45);
			   }
			p.start(); 
		} catch (Exception e) { 
			logger.error(e);
		}
	
	}

		//	TODO notify successin a nice manner
		private void handleVisualDecodedText(String text) {
			String remoteHash = text;
			logger.info("visual verification result ---------- " );
			logger.info("remote hash: " + remoteHash);
			byte hash[];
			try {
				hash = Hash.doubleSHA256(authKey, false);
				byte [] trimmedHash = new byte[7];
				System.arraycopy(hash, 0, trimmedHash, 0, 7);
				String localHash = new String(Hex.encodeHex(trimmedHash));
				logger.info("local hash: " + localHash);
	
				if(remoteHash.equals(localHash)){
					logger.info("verification ended");
					logger.info("hashes are equal");
					send(SUCCESS);
	
//					main_list.append("verification successfull", null);
//					main_list.append("check out the log for details", null);
					mainProgram.verificationDone();					
				}
			} catch (InternalApplicationException e) {
				logger.error(e);
			}
	
		}

	private void authenticateAudio() {
		AudioChannel audioVerifier = new AudioChannel(mainProgram);
		audioVerifier.setOOBMessageHandler(this);
		audioVerifier.capture();
	}

	private void getInputViaVisualChannel() {
		VisualChannel verifier = new VisualChannel(mainProgram);
		verifier.setOOBMessageHandler(this);
		verifier.capture();
	}

	private void sendViaVisualChannel() {
		logger.info("sendViaVisualChannel");
		byte [] trimmedHash = new byte[7];
		byte[] hash= null;

		try {
			hash = Hash.doubleSHA256(authKey, false);
		} catch (InternalApplicationException e) {

			logger.error(e);
			return;
		}
		System.arraycopy(hash, 0, trimmedHash, 0, 7);
		String toSend = new String(Hex.encodeHex(trimmedHash));
		logger.info("creating visual channel");
		VisualChannel verifier = new VisualChannel(mainProgram);

		verifier.transmit(toSend.getBytes());


	}


	

	public void handleOOBMessage(int channelType, byte [] data) {
		if (channelType == OOBChannel.AUDIO_CHANNEL){
			handleAudioMessage(data);
		}else	if (channelType == OOBChannel.VIDEO_CHANNEL){
			handleVisualDecodedText(new String(data));
		}
	
	}
	
}