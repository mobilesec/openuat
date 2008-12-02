/* Copyright Iulia Ion
 * File created 2008-08-01
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.apps;

import java.awt.Font;
import java.awt.Label;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;

import javax.bluetooth.UUID;
import javax.microedition.lcdui.Alert;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.SwingConstants;
import javax.swing.border.Border;

import org.apache.commons.codec.binary.Hex;
import org.apache.log4j.Logger;
import org.codec.audio.AudioUtils;
import org.codec.audio.PlayerPiano;
import org.codec.audio.WavPlayer;
import org.codec.mad.MadLib;
import org.eclipse.swt.graphics.Image;
import org.openuat.authentication.exceptions.InternalApplicationException;
import org.openuat.channel.oob.VisualChannel;
import org.openuat.util.BluetoothRFCOMMChannel;
import org.openuat.util.BluetoothRFCOMMServer;
import org.openuat.util.BluetoothSupport;
import org.openuat.util.Hash;
import org.openuat.util.LineReaderWriter;
import org.openuat.util.RemoteConnection;


public class OpenUATtoolkit {
	/** verification methods */
	public static final String VISUAL = "VISUAL";
	public static final String AUDIO = "AUDIO";
	public static final String SLOWCODEC = "SLOWCODEC";
	public static final String MADLIB = "MADLIB";

	/** how many bytes of the key are used for the Audio method */	
	private static final int AUDIO_KEY_LENGTH = 7;
	
	/** with how many bits to pad the hash before sending */
	private static final int AUDIO_PADDING = 6;
	
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

	/** informs that verification was NOT successful */
	public static final String FAILURE = "FAILURE";

	/** Replay the slow codec tune */
	public static final String REPLAY = "REPLAY";
	
	private static Logger logger = Logger.getLogger("org.openuat.apps.OpenUATtoolkit");

	//#if cfg.includeTestCode 
	///////////////////////////////////////// test code begins here //////////////////////
	protected static class TempHandler implements 
	org.openuat.authentication.AuthenticationProgressHandler,
	org.openuat.authentication.KeyManager.VerificationHandler {
		
		//last authenticated key
		private byte[] authKey;
		
		JLabel status ;
		JPanel progress ;
		//so we know what to refresh
		JFrame frame;
		private boolean performMirrorAttack, requestSensorStream;

		TempHandler(boolean attack, boolean requestStream, JLabel status, JPanel pane, JFrame frame) {
			this.performMirrorAttack = attack;
			this.requestSensorStream = requestStream;
			this.status = status;
			this.progress = pane;
			this.frame = frame;
		}

		public void AuthenticationFailure(Object sender, Object remote, Exception e, String msg) {
			System.out.println("DH with " + remote + " failed: " + e + "/" + msg);
			System.exit(1);
		}

		public void AuthenticationProgress(Object sender, Object remote, int cur, int max, String msg) {
			System.out.println("DH with " + remote + " progress: " + cur + "/" + max + ": " + msg);
			status.setText("Key exchange progress: " + cur + "/" + max + ": " + msg);
		}

		public boolean AuthenticationStarted(Object sender, Object remote) {
			System.out.println("DH with " + remote + " started");
			status.setText("Starting key exchange...") ; 
			progress.removeAll();
			frame.repaint();
			
			return true;
		}
		private boolean send(String message,  RemoteConnection connectionToRemote) {
			try {
				LineReaderWriter.println(connectionToRemote.getOutputStream(), message);
				return false;
			} catch (IOException e) {
//				logger.debug("Unable to open stream to remote: " + e);
				e.printStackTrace();
			}
			return true;

		}

		private String readLine( RemoteConnection connectionToRemote) {
			String line = null;
			try {
				line = LineReaderWriter.readLine(connectionToRemote.getInputStream(), -1);
			} catch (IOException e) {
//				logger.debug("Unable to open stream to remote: " + e);
				e.printStackTrace();
			}
			return line;

		}
		public void AuthenticationSuccess(Object sender, Object remote, Object result) {
			System.out.println("DH with " + remote + " SUCCESS");
			status.setText("Starting key verification");
			
			Object[] res = (Object[]) result;
			// remember the secret key shared with the other device
			byte[] sharedKey = (byte[]) res[0];
			// and extract the shared authentication key for phase 2
			authKey = (byte[]) res[1];
			System.out.println("Shared session key is now '" + new String(Hex.encodeHex(sharedKey)) + 
					"' with length " + sharedKey.length + 
					", shared authentication key is now '" + new String(Hex.encodeHex(authKey)) + 
					"' with length " + authKey.length);
			// then extraxt the optional parameter
			//String param = (String) res[2];

			RemoteConnection connectionToRemote = (RemoteConnection) res[3];
			
			String verify = readLine(connectionToRemote);
			if (verify.equals(VERIFY)){
				String method = readLine(connectionToRemote);
				if (logger.isDebugEnabled()) logger.debug("method: " + method);
				byte[] hash = null;
				try {
					hash = Hash.doubleSHA256(authKey, false);
				} catch (InternalApplicationException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				if (method.equals(VISUAL)){
					send (ACK, connectionToRemote);
					
					String start = readLine(connectionToRemote);
					if(start.equals(START)){
						status.setText("Take a picture of the code!");

						byte [] trimmedHash = new byte[7];
						System.arraycopy(hash, 0, trimmedHash, 0, 7);
						String toSend = new String(Hex.encodeHex(trimmedHash));
						if (logger.isDebugEnabled()) logger.debug("hash: "+toSend);
						VisualChannel channel = new VisualChannel();
						channel.setPane(progress);
						channel.transmit(toSend.getBytes());
						frame.repaint();
						String success = readLine(connectionToRemote);

						readSuccess(success);
						channel.close();
					}
				}else if (method.equals(AUDIO)){
					method = AUDIO;
					send (ACK, connectionToRemote);
					byte [] trimmedHash = new byte[AUDIO_KEY_LENGTH];
					status.setText("Audio transmission");
					try {
						hash = Hash.doubleSHA256(authKey, false);
						System.arraycopy(hash, 0, trimmedHash, 0, AUDIO_KEY_LENGTH);
						byte[] toSend = new String(Hex.encodeHex(trimmedHash)).getBytes();
						//we add some padding after the hash
						byte [] padded = new byte[toSend.length + AUDIO_PADDING];
						System.arraycopy(toSend, 0, padded, 0, toSend.length);
						if (logger.isDebugEnabled()) logger.debug("hash: "+toSend);
					
					java.net.URL imageURL = getClass().getResource("/audio_bg.png");
					ImageIcon icon = new ImageIcon(imageURL);
					progress.add(new JLabel("", icon, JLabel.CENTER));
					frame.repaint();
					byte [] sound = AudioUtils.encodeFileToWav(new ByteArrayInputStream(padded));
					tryAudio(connectionToRemote, sound);
					
					} catch (Exception ioex) {
						ioex.printStackTrace();
					}

				}else if (method.equals(MADLIB)){
					send (ACK, connectionToRemote);
					progress.removeAll();
					MadLib madLib = new MadLib();
					try {
					String text = madLib.GenerateMadLib(hash, 0, 5);
					if (logger.isDebugEnabled()) logger.debug("MADLIB: "+text);
//					java.net.URL imageURL = getClass().getResource("/madlib_bg.png");
//					ImageIcon icon = new ImageIcon(imageURL);
//					progress.add(new JLabel(text, icon, JLabel.CENTER));
					status.setText(text);
					status.setFont(new Font("Serif",Font.BOLD, 24));
					frame.repaint();
					} catch (UnsupportedEncodingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					}
					send (DONE, connectionToRemote);
					String done = readLine(connectionToRemote);
					
					readSuccess(done);
				}else if (method.equals(SLOWCODEC)){
					status.setText("Listen to the tune.");
					send (ACK, connectionToRemote);
					progress.removeAll();
					java.net.URL imageURL = getClass().getResource("/slowcodec_bg.png");
					ImageIcon icon = new ImageIcon(imageURL);
//					progress.add(new JLabel("", icon, JLabel.CENTER));
					frame.repaint();
						String score = PlayerPiano.MakeInput(hash);
						playSlowCodec(connectionToRemote, score);
				}
			}
			

		}

		private void playSlowCodec(RemoteConnection connectionToRemote,
				String score) {
			String done = readLine(connectionToRemote);
			if(done.equals(DONE)){
				try{

					PlayerPiano.PlayerPiano(score);
				} catch (Exception ioex) {
					ioex.printStackTrace();
				}
			}

//			send (DONE, connectionToRemote);
			String success = readLine(connectionToRemote);
			status.setText("");
			progress.removeAll();
			frame.repaint();
			
			if(success.equals(SUCCESS)){
				status.setText("Congratulations! Authentication was successful.");
				if(logger.isInfoEnabled()) logger.info("Authentication completed successfully.");
			}else if (success.equals(FAILURE)){
				if(logger.isInfoEnabled()) logger.info("Authentication failed.");
				status.setText("Error! Authentication failed.");
			
			}else if (success.equals(REPLAY)){
				playSlowCodec(connectionToRemote, score);
			}
			progress.repaint();
			frame.repaint();
		}

		private void tryAudio(RemoteConnection connectionToRemote, byte[] sound) {
			String start = readLine(connectionToRemote);
			if(start.equals(START)){
				if (logger.isDebugEnabled()) logger.debug("Playing...");
				WavPlayer.PlayWav(new ByteArrayInputStream(sound));
				if (logger.isDebugEnabled()) logger.debug("Done.");
				send(DONE, connectionToRemote);
				String outcome = readLine(connectionToRemote);
				status.setText("");
				progress.removeAll();
				frame.repaint();
				if(outcome.equals(SUCCESS)){

					//					java.net.URL imageURL = getClass().getResource("/secure_sm.png");
					//					ImageIcon icon = new ImageIcon(imageURL);
					//					progress.add(new JLabel("Congratulations! Authentication was successful.", icon, JLabel.CENTER));
					status.setText("Congratulations! Authentication was successful.");
					if(logger.isInfoEnabled()) logger.info("Authentication completed successfully.");
					
					
					//for demo purposes
					connectionToRemote.close();
				}else if (outcome.equals(FAILURE)){
					if(logger.isInfoEnabled()) logger.info("Authentication failed.");

					//					java.net.URL imageURL = getClass().getResource("/error_bg.png");
					//					ImageIcon icon = new ImageIcon(imageURL);
					//					progress.add(new JLabel("Authentication failed.", icon, JLabel.CENTER));
					status.setText("Error! Authentication failed.");
					//for demo purposes
					connectionToRemote.close();
				} else if (outcome.equals(REPLAY)){
					tryAudio(connectionToRemote, sound);
				}
			}
			progress.repaint();
			frame.repaint();
		}
		

		private void readSuccess(String success) {
			status.setText("");
			progress.removeAll();
			frame.repaint();
			
			if(success.equals(SUCCESS)){
				
//				java.net.URL imageURL = getClass().getResource("/secure_sm.png");
//				ImageIcon icon = new ImageIcon(imageURL);
//				progress.add(new JLabel("Congratulations! Authentication was successful.", icon, JLabel.CENTER));
				status.setText("Congratulations! Authentication was successful.");
					if(logger.isInfoEnabled()) logger.info("Authentication completed successfully.");
				
			}else if (success.equals(FAILURE)){
				if(logger.isInfoEnabled()) logger.info("Authentication failed.");
				
//				java.net.URL imageURL = getClass().getResource("/error_bg.png");
//				ImageIcon icon = new ImageIcon(imageURL);
//				progress.add(new JLabel("Authentication failed.", icon, JLabel.CENTER));
				status.setText("Error! Authentication failed.");
			
			}
			progress.repaint();
			frame.repaint();
			
		}



		public void startVerification(byte[] sharedAuthenticationKey, String optionalParam, RemoteConnection toRemote) {
			if (((BluetoothRFCOMMChannel) toRemote).isOpen()) {
				System.out.println("Called for verification and connection to remote is still open, mirroring on it");
				inVerificationPhase(toRemote, true);
			}
		}

		void inVerificationPhase(RemoteConnection connectionToRemote, boolean exitAfterClosing) {
			InputStream i;
			OutputStream o;
			try {
				i = connectionToRemote.getInputStream();
				o = connectionToRemote.getOutputStream();
				OutputStreamWriter ow = new OutputStreamWriter(o);

				if (requestSensorStream) {
					ow.write("DEBG_Stream\n");
					ow.flush();
				}

				int tmp = i.read();
				while (tmp != -1) {
					System.out.print((char) tmp);
					if (performMirrorAttack)
						o.write(tmp);
					tmp = i.read();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}

			if (exitAfterClosing)
				System.exit(0);
		}
	}
	JLabel status = new JLabel("");
	JPanel progress = new JPanel();
	
	JFrame frame;

	public OpenUATtoolkit() {
		initUI();
		initBluetoothServer();
	
	}

	public static void main(String[] args) throws  NumberFormatException {
		new OpenUATtoolkit();
		
	}


	private  void initBluetoothServer() {
		if (! BluetoothSupport.init()) {
			logger.error("Could not initialize Bluetooth API");
			return;
		}

		try {
			BluetoothRFCOMMServer rfcommServer = new BluetoothRFCOMMServer(null, new UUID("447d8ecbefea4b2d93107ced5d1bba7e", false), "OpenUAT- Print document", 
					-1, true, false);
			rfcommServer.addAuthenticationProgressHandler(new TempHandler(false, false, status, progress, frame));
			rfcommServer.start();
			logger.info("Finished starting SDP service at " + rfcommServer.getRegisteredServiceURL());
		} catch (IOException e) {
			logger.error("Error initializing BlutoothRFCOMMServer: " + e);
			e.printStackTrace();
		}
	}

	private  void initUI() {
		 frame = new JFrame("OpenUAT Toolkit");
		frame.setSize(600, 600);
		frame.setLocation(200, 300);
		JPanel pane = new JPanel();
		pane.setLayout(new BoxLayout(pane, BoxLayout.Y_AXIS));

		java.net.URL imageURL = getClass().getResource("/printer_bg.png");

		ImageIcon icon = new ImageIcon(imageURL);
		pane.add(new JLabel("Print service started", icon, SwingConstants.CENTER));
		pane.add(status);
		pane.add(progress);
		pane.setBorder(BorderFactory.createRaisedBevelBorder());
		frame.setContentPane(pane);
		frame.setVisible(true);
	}
}
