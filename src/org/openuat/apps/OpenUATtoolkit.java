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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;

import javax.bluetooth.UUID;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import org.apache.commons.codec.binary.Hex;
import org.apache.log4j.Logger;
import org.codec.audio.AudioUtils;
import org.codec.audio.PlayerPiano;
import org.codec.audio.WavPlayer;
import org.codec.mad.MadLib;
import org.openuat.authentication.exceptions.InternalApplicationException;
import org.openuat.channel.UACAPProtocolConstants;
import org.openuat.channel.oob.VisualChannel;
import org.openuat.util.BluetoothRFCOMMChannel;
import org.openuat.util.BluetoothRFCOMMServer;
import org.openuat.util.BluetoothSupport;
import org.openuat.util.Hash;
import org.openuat.util.LineReaderWriter;
import org.openuat.util.RemoteConnection;

/**
 * Implements the OpenUAT toolkit for J2SE.
 * @author Iulia Ion
 *
 */
public class OpenUATtoolkit {
	

	private static Logger logger = Logger.getLogger("org.openuat.apps.OpenUATtoolkit");
	
	//UI components
	private JLabel status = new JLabel("");
	private JPanel progress = new JPanel();
	private JFrame frame;

/** Handles the authentication steps*/
	protected static class TempHandler implements 
	org.openuat.authentication.AuthenticationProgressHandler,
	org.openuat.authentication.KeyManager.VerificationHandler {
		
		//last authenticated key
		private byte[] authKey;
		
		//UI components
		private JLabel status;
		private JPanel progress ;
		private JFrame frame;

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
		/**
		 * Sends a message via Bluetooth to the other party. Used for synchronization during the key authentication phase.
		 * @param message The message to be sent
		 * @param connectionToRemote The connection through which to send it
		 * @return true if there was an error, false if it was successful
		 */
		private boolean send(String message,  RemoteConnection connectionToRemote) {
			try {
				LineReaderWriter.println(connectionToRemote.getOutputStream(), message);
				return false;
			} catch (IOException e) {
				logger.error("Unable to open stream to remote: " + e);
			}
			return true;

		}

		/**
		 * Waits for and receives a message from the other party. This method blocks till a message is received.
		 * @param connectionToRemote
		 * @return the read message
		 */
		private String readLine( RemoteConnection connectionToRemote) {
			String line = null;
			try {
				line = LineReaderWriter.readLine(connectionToRemote.getInputStream(), -1);
			} catch (IOException e) {
				logger.error("Unable to open stream to remote: " + e);
			}
			return line;

		}
		
		/**
		 * When the key exchange algorithm has finished, the key verification phase using an out-of-band channel starts.
		 * This method implements the verification for several channels: audio, visual, and manual verification of melody and sentence.
		 * The hash of the established session key is computed. For verification only the first bytes are used.
		 */
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
			
			/** wait for the command from the other party to start the verification process */
			String verify = readLine(connectionToRemote);
			/** check to see which out-of-band channel should be used for verification */
			if (verify.equals(UACAPProtocolConstants.VERIFY)){
				String method = readLine(connectionToRemote);
				if (logger.isDebugEnabled()) {
					logger.debug("method: " + method);
				}
				byte[] hash = null;
				try {
					hash = Hash.doubleSHA256(authKey, false);
				} catch (InternalApplicationException e) {
					logger.debug("Error while creating hash: " + e);
				}
				if (method.equals(UACAPProtocolConstants.VISUAL)){//generate QR code and display
					send (UACAPProtocolConstants.ACK, connectionToRemote);
					
					String start = readLine(connectionToRemote);
					if(start.equals(UACAPProtocolConstants.START)){
						status.setText("Take a picture of the code!");

						//we trim the hash to cope with the limited capacity of the video channel
						byte [] trimmedHash = new byte[UACAPProtocolConstants.VIDEO_KEYHASH_LENGTH];
						System.arraycopy(hash, 0, trimmedHash, 0, UACAPProtocolConstants.VIDEO_KEYHASH_LENGTH);
						
						// the trimmed hash is encoded in hexadecimal because otherwise the QR decoder cannot cope with it
						String toSend = new String(Hex.encodeHex(trimmedHash));
						if (logger.isDebugEnabled()) {
							logger.debug("hash: "+toSend);
						}
						//display the QR code 
						VisualChannel channel = new VisualChannel();
						channel.setPane(progress);
						channel.transmit(toSend.getBytes());
						frame.repaint();
						
						//wait for the verification outcome from the other party
						String success = readLine(connectionToRemote);
						
						readSuccess(success);
						channel.close();
					}
				}else if (method.equals(UACAPProtocolConstants.AUDIO)){ //encode hash key in audio format and play
					method = UACAPProtocolConstants.AUDIO;
					send (UACAPProtocolConstants.ACK, connectionToRemote);
					byte [] trimmedHash = new byte[UACAPProtocolConstants.AUDIO_KEYHASH_LENGTH];
					status.setText("Audio transmission");
					try {
						hash = Hash.doubleSHA256(authKey, false);
						System.arraycopy(hash, 0, trimmedHash, 0, UACAPProtocolConstants.AUDIO_KEYHASH_LENGTH);
						byte[] toSend = new String(Hex.encodeHex(trimmedHash)).getBytes();
						//the audio codec works best if we add some padding after the hash 
						byte [] padded = new byte[toSend.length + UACAPProtocolConstants.AUDIO_PADDING];
						System.arraycopy(toSend, 0, padded, 0, toSend.length);
						if (logger.isDebugEnabled()) {
							logger.debug("hash: "+toSend);
						}
					
					java.net.URL imageURL = getClass().getResource("/audio_bg.png");
					ImageIcon icon = new ImageIcon(imageURL);
					progress.add(new JLabel("", icon, JLabel.CENTER));
					frame.repaint();
					byte [] sound = AudioUtils.encodeFileToWav(new ByteArrayInputStream(padded));
					tryAudio(connectionToRemote, sound);
					
					} catch (Exception ioex) {
						ioex.printStackTrace();
					}

				}else if (method.equals(UACAPProtocolConstants.MADLIB)){//Generate a sentence out of the hash and display
					send (UACAPProtocolConstants.ACK, connectionToRemote);
					progress.removeAll();
					MadLib madLib = new MadLib();
					try {
						String text = madLib.GenerateMadLib(hash, 0, 5);
						if (logger.isDebugEnabled()) logger.debug("MADLIB: "+text);
//						java.net.URL imageURL = getClass().getResource("/madlib_bg.png");
//						ImageIcon icon = new ImageIcon(imageURL);
//						progress.add(new JLabel(text, icon, JLabel.CENTER));
						status.setText(text);
						status.setFont(new Font("Serif",Font.BOLD, 24));
						frame.repaint();
					} catch (UnsupportedEncodingException e) {
						logger.error("UnsupportedEncodingException", e);
					}
					send (UACAPProtocolConstants.DONE, connectionToRemote);
					String done = readLine(connectionToRemote);

					readSuccess(done);
				}else if (method.equals(UACAPProtocolConstants.SLOWCODEC)){//generate a melodic tune from the hash and play
					status.setText("Listen to the tune.");
					send (UACAPProtocolConstants.ACK, connectionToRemote);
					progress.removeAll();
					java.net.URL imageURL = getClass().getResource("/slowcodec_bg.png");
					ImageIcon icon = new ImageIcon(imageURL);
//					progress.add(new JLabel("", icon, JLabel.CENTER));
					frame.repaint();
					String score = PlayerPiano.MakeInput(hash);
					playSlowCodec(connectionToRemote, score);
				} else if (method.equals(UACAPProtocolConstants.HASH_COMP)){
					
					status.setText("Is the number the same?");
					send (UACAPProtocolConstants.ACK, connectionToRemote);
					progress.removeAll();
					try {
						String text = Hash.getHexString(Hash.doubleSHA256(authKey, false));
						text = text.substring(0, UACAPProtocolConstants.HASH_STRING_LENGTH);
						if (logger.isDebugEnabled()) logger.debug("hash: "+text);
//						java.net.URL imageURL = getClass().getResource("/madlib_bg.png");
//						ImageIcon icon = new ImageIcon(imageURL);
//						progress.add(new JLabel(text, icon, JLabel.CENTER));
						status.setText("Is the hash the same?" +text);
						status.setFont(new Font("Serif",Font.BOLD, 24));
						frame.repaint();
						System.out.println("done updating frame");
					} catch (InternalApplicationException e) {
						logger.error("Failed to build hash.", e);
						System.out.println("error"+e);
						return;
					}
					send (UACAPProtocolConstants.DONE, connectionToRemote);
					System.out.println("done updating frame");
					String done = readLine(connectionToRemote);

					readSuccess(done);
				}
			}
		}


		/**
		 * Plays the melodic tune representing used for the key verification. It synchronizes with the other party via Bluetooth to know when to start playing.
		 * @param connectionToRemote The remote party.
		 * @param score The tune to play, resulted from the key hash.
		 */
		private void playSlowCodec(RemoteConnection connectionToRemote,
				String score) {
			String done = readLine(connectionToRemote);
			if(done.equals(UACAPProtocolConstants.DONE)){
				try{

					PlayerPiano.PlayerPiano(score);
				} catch (Exception ioex) {
					logger.error(ioex);
				}
			}

//			send (DONE, connectionToRemote);
			String success = readLine(connectionToRemote);
			status.setText("");
			progress.removeAll();
			frame.repaint();
			
			if(success.equals(UACAPProtocolConstants.SUCCESS)){
				status.setText("Congratulations! Authentication was successful.");
				if(logger.isInfoEnabled()) logger.info("Authentication completed successfully.");
			}else if (success.equals(UACAPProtocolConstants.FAILURE)){
				if(logger.isInfoEnabled()) logger.info("Authentication failed.");
				status.setText("Error! Authentication failed.");
			
			}else if (success.equals(UACAPProtocolConstants.REPLAY)){
				playSlowCodec(connectionToRemote, score);
			}
			progress.repaint();
			frame.repaint();
		}

		/**
		 * Plays the audio message. If authentication fails, it retries (i.e. send again and wait for the other party to acknowledge success
		 * @param connectionToRemote The remote party. Used for synchronization.
		 * @param sound The tune to play in byte array.
		 */
		private void tryAudio(RemoteConnection connectionToRemote, byte[] sound) {
			String start = readLine(connectionToRemote);
			if(start.equals(UACAPProtocolConstants.START)){
				if (logger.isDebugEnabled()) logger.debug("Playing...");
				WavPlayer.PlayWav(new ByteArrayInputStream(sound));
				if (logger.isDebugEnabled()) logger.debug("Done.");
				send(UACAPProtocolConstants.DONE, connectionToRemote);
				String outcome = readLine(connectionToRemote);
				status.setText("");
				progress.removeAll();
				frame.repaint();
				if(outcome.equals(UACAPProtocolConstants.SUCCESS)){

					//					java.net.URL imageURL = getClass().getResource("/secure_sm.png");
					//					ImageIcon icon = new ImageIcon(imageURL);
					//					progress.add(new JLabel("Congratulations! Authentication was successful.", icon, JLabel.CENTER));
					status.setText("Congratulations! Authentication was successful.");
					if(logger.isInfoEnabled()) logger.info("Authentication completed successfully.");
					
					
					//for demo purposes
					connectionToRemote.close();
				}else if (outcome.equals(UACAPProtocolConstants.FAILURE)){
					if(logger.isInfoEnabled()) logger.info("Authentication failed.");

					//					java.net.URL imageURL = getClass().getResource("/error_bg.png");
					//					ImageIcon icon = new ImageIcon(imageURL);
					//					progress.add(new JLabel("Authentication failed.", icon, JLabel.CENTER));
					status.setText("Error! Authentication failed.");
					//for demo purposes
					connectionToRemote.close();
				} else if (outcome.equals(UACAPProtocolConstants.REPLAY)){
					tryAudio(connectionToRemote, sound);
				}
			}
			progress.repaint();
			frame.repaint();
		}
		

		/**
		 * Processes the message received from the other party which says if the verification process was successful or not
		 * Update the screen and inform the user accordingly
		 * @param success 
		 */
		private void readSuccess(String success) {
			status.setText("");
			progress.removeAll();
			frame.repaint();
			
			if(success.equals(UACAPProtocolConstants.SUCCESS)){
				
//				java.net.URL imageURL = getClass().getResource("/secure_sm.png");
//				ImageIcon icon = new ImageIcon(imageURL);
//				progress.add(new JLabel("Congratulations! Authentication was successful.", icon, JLabel.CENTER));
				status.setText("Congratulations! Authentication was successful.");
					if(logger.isInfoEnabled()) logger.info("Authentication completed successfully.");
				
			}else if (success.equals(UACAPProtocolConstants.FAILURE)){
				if(logger.isInfoEnabled()) logger.info("Authentication failed.");
				
//				java.net.URL imageURL = getClass().getResource("/error_bg.png");
//				ImageIcon icon = new ImageIcon(imageURL);
//				progress.add(new JLabel("Authentication failed.", icon, JLabel.CENTER));
				status.setText("Error! Authentication failed.");
			
			}
			progress.repaint();
			frame.repaint();
			
		}


/**
 * Starts the key verification process using our of band channels.
 */
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
		frame.setSize(1000, 1000);
		frame.setLocation(100, 100);
		JPanel pane = new JPanel();
		pane.setLayout(new BoxLayout(pane, BoxLayout.Y_AXIS));

		java.net.URL printerImage = getClass().getResource("/printer_bg.png");

		ImageIcon printerIcon = new ImageIcon(printerImage);
		pane.add(new JLabel("Print service started", printerIcon, SwingConstants.CENTER));
//		pane.add(new JLabel("Available input/output methods: button, small display, audio", null, SwingConstants.CENTER));
		
		pane.add(status);
		pane.add(progress);
		pane.setBorder(BorderFactory.createRaisedBevelBorder());
		frame.setContentPane(pane);
		frame.setVisible(true);
	}
}
