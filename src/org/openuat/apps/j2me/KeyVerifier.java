package org.openuat.apps.j2me;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

import javax.microedition.lcdui.Alert;
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

import org.apache.commons.codec.binary.Hex;
import org.apache.log4j.Logger;
import org.codec.audio.j2me.AudioUtils;
import org.codec.audio.j2me.PlayerPianoJ2ME;
import org.codec.mad.MadLib;
import org.openuat.authentication.OOBChannel;
import org.openuat.authentication.OOBMessageHandler;
import org.openuat.authentication.exceptions.InternalApplicationException;
import org.openuat.channel.oob.j2me.J2MEAudioChannel;
import org.openuat.channel.oob.j2me.J2MEVisualChannel;
import org.openuat.util.BluetoothRFCOMMChannel;
import org.openuat.util.Hash;
import org.openuat.util.LineReaderWriter;
import org.openuat.util.RemoteConnection;

public class KeyVerifier implements CommandListener, OOBMessageHandler  {
	
	/** how many bytes of the key are used for the Audio method */	
	private static final int AUDIO_KEY_LENGTH = 7;
	
	/** with how many bits to pad the hash before sending */
	private static final int AUDIO_PADDING = 6;
	
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
	
	/** informs that verification was NOT successful */
	public static final String FAILURE = "FAILURE";
	
	Logger logger = Logger.getLogger("");
	byte [] authKey;
	OpenUATmidlet mainProgram;
	RemoteConnection connectionToRemote;
	OutputStream out ;
	InputStream in ;
	
	Command successCmd;
	Command failure;
	
	public KeyVerifier(byte [] authKey, RemoteConnection connectionToRemote, OpenUATmidlet mainProgram ) {
		this.authKey = authKey;
		this.mainProgram = mainProgram;
		this.connectionToRemote = connectionToRemote;
		try {
			out = connectionToRemote.getOutputStream();
			in = connectionToRemote.getInputStream();
		} catch (IOException e) {
			logger.error(e);
		}
		mainProgram.do_alert("Key exchanges successfully. Starting verification process.", 1000);
		
		successCmd = new Command("Success", Command.SCREEN, 1);
		failure = new Command("Failure", Command.SCREEN, 1);
	}
	
	
	private boolean send(String message) {
		try {
			
			LineReaderWriter.println(out, message);
			logger.info("sent message:"+message);
			return true;
		} catch (IOException e) {
			logger.info("Failed to send message: "+message);
			logger.error(e);
			if (!connectionToRemote.isOpen()){
				if (connectionToRemote instanceof BluetoothRFCOMMChannel) {
					
					try {
						String address = connectionToRemote.getRemoteAddress().toString();
						int number = ((BluetoothRFCOMMChannel)connectionToRemote).getRemoteChannelNumber();
						connectionToRemote = new BluetoothRFCOMMChannel(address, number);
						connectionToRemote.open();
						out = connectionToRemote.getOutputStream();
						in = connectionToRemote.getInputStream();
						//try again
						send(message);
					} catch (IOException e1) {
						logger.info("Failed to reopen BT connection ");
					}
					
				}
			}
//				
//				boolean open = connectionToRemote.open();
//				if(! open)
//					logger.info("Could not reopen connection");
//			}
//			
//			LineReaderWriter.println(out, message);
//			return true;
			logger.debug("Unable to open stream to remote: " + e);
			mainProgram.do_alert("Unable to open stream to remote to send message"+message, 10000);
		}
		return false;

	}
	
	private String readLine() {
		String line = null;
//		int trials = 0;
//		while (line == null){
//			//try for 10 seconds, then stop
//			if(trials > 10)
//				break;
//			trials ++;
			try {

				line = LineReaderWriter.readLine(in, -1);
				logger.info("read: " + line);
			} 
			catch (InterruptedIOException e) {


				logger.debug("InterruptedIOException while reading: " + e);
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
//				if (!connectionToRemote.isOpen()){
//				if (connectionToRemote instanceof BluetoothRFCOMMChannel) {

//				try {
//				String address = connectionToRemote.getRemoteAddress().toString();
//				int number = ((BluetoothRFCOMMChannel)connectionToRemote).getRemoteChannelNumber();
//				logger.info("trying to reopen connection to "+address + " - "+number);
//				connectionToRemote = new BluetoothRFCOMMChannel(address, number);
//				connectionToRemote.open();
//				out = connectionToRemote.getOutputStream();
//				in = connectionToRemote.getInputStream();
//				//try again
//				return readLine();
//				} catch (IOException e1) {
//				logger.info("Failed to reopen BT connection ");
//				logger.error(e1);
//				}

//				}
			}catch (IOException e) {
				logger.debug("Unable to open stream to remote: " + e);
				logger.debug(": " + e.getMessage());
//				if (!connectionToRemote.isOpen()){
//				if (connectionToRemote instanceof BluetoothRFCOMMChannel) {

//				try {
//				String address = connectionToRemote.getRemoteAddress().toString();
//				int number = ((BluetoothRFCOMMChannel)connectionToRemote).getRemoteChannelNumber();
//				logger.info("trying to reopen connection to "+address + " - "+number);
//				connectionToRemote = new BluetoothRFCOMMChannel(address, number);
//				connectionToRemote.open();
//				out = connectionToRemote.getOutputStream();
//				in = connectionToRemote.getInputStream();
//				//try again
//				return readLine();
//				} catch (IOException e1) {
//				logger.info("Failed to reopen BT connection ");
//				logger.error(e1);
//				}

//				}
//			}
		}
		return line;

	}
	
	
	public void commandAction(Command com, Displayable arg1) {

		if (com == successCmd){
			send(SUCCESS);
			mainProgram.informSuccess(true);
			return;
		}else if (com == failure){
			send(FAILURE);
			mainProgram.informSuccess(false);
			return;
		}
		else if (com.getCommandType() == Command.EXIT ){
			mainProgram.commandAction(com, arg1);
			return;
		}else if (com == mainProgram.log ){
			mainProgram.commandAction(com, arg1);
			return;
		}
		List option = (List) arg1;
//		mainProgram.do_alert("Invoked command"+com, 1000);
		if (com == List.SELECT_COMMAND) {
			if (option.getSelectedIndex() == 0){
				verifyVisual();

			}else if (option.getSelectedIndex() == 1){
				verifyAudio();
			}else if (option.getSelectedIndex() == 2){ 
				verifySlowCodec();
			}else if (option.getSelectedIndex() == 3){
				verifyMadLib();
			}
		}
	}


	public void verifyAudio() {
		send(VERIFY);
		send(AUDIO);
		
		String response = readLine();
		//System.out.println("response "+response);
		if (response.equals(ACK)){
			
			J2MEAudioChannel verifier = new J2MEAudioChannel(mainProgram);
			verifier.setOOBMessageHandler(this);
			
			verifier.capture();
			//let's see if permission is modal.
//			mainProgram.do_alert("Started to capture", -1);
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
//			try {
//				Thread.sleep(3500);
//			} catch (InterruptedException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
			Form progress = new Form("OpenUAT");
			progress.append("Capturing...\n");
			progress.addCommand(mainProgram.log);
			progress.addCommand(mainProgram.exit);

			progress.setCommandListener(this);
			
			mainProgram.display.setCurrent(progress);
//			byte recorded [] = verifier.finishCapturing();
			send (START);
			String done = readLine();
			if(done.equals(DONE)){
				
				logger.info("finishing audio capture");
//				mainProgram.do_alert("finishing capture", Alert.FOREVER);
				byte recorded [] = verifier.finishCapturing();
				progress.append("Done.\n");
				progress.append("decoding "+recorded.length+ " bytes...\n");
				byte decoded [] = verifier.decodeAudio(recorded);
				progress.append("Done. \n");
				handleOOBMessage(J2MEAudioChannel.AUDIO_CHANNEL, decoded);
			}
		}
	}


	public void verifyVisual() {
		logger.info("visual-send");
		send(VERIFY);
		send(VISUAL);
		
		String response = readLine();
		//System.out.println("response "+response);
		if (response.equals(ACK)){
			send (START);
			getInputViaVisualChannel();
		}
	}
//	private void sendViaAudioChannel() {
//		byte [] trimmedHash = new byte[8];
//		 byte[] hash;
//		 
//		 try {
//			hash = Hash.doubleSHA256(authKey, false);
//			System.arraycopy(hash, 0, trimmedHash, 0, 8);
//			byte[] toSend = new String(Hex.encodeHex(trimmedHash)).getBytes();
//			//we add some padding after the hash
//			byte [] padded = new byte[toSend.length + 10];
//			System.arraycopy(toSend, 0, padded, 0, toSend.length);
//			byte [] sound = AudioUtils.encodeFileToWav(new ByteArrayInputStream(padded));
//
//			Player player = Manager.createPlayer(new ByteArrayInputStream(sound), "encoding=pcm&rate=44100&bits=8&channels=1&endian=little&signed=true");
//
//			player.prefetch(); 
//			player.realize(); 
//			player.start();
//		 }catch (InternalApplicationException e) {
//				logger.error(e);
//			} catch (IOException e) {
//				logger.error(e);
//		} catch (MediaException e) {
//			logger.error(e);
//		}
//		
//	}

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
				byte [] trimmedHash = new byte[AUDIO_KEY_LENGTH];
				System.arraycopy(hash, 0, trimmedHash, 0, AUDIO_KEY_LENGTH);
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
//						mainProgram.do_alert("verification successful.", Alert.FOREVER);
//						main_list.append("----", null);
//						main_list.append("Successful pairing", null);
						send(SUCCESS);
						mainProgram.informSuccess(true);
					}else{
//						main_list.append("authentication failed", null);
						send(FAILURE);
						mainProgram.informSuccess(false);
					}
					
	//			}
//				main_list.append("check out the log for details", null);
//				display.setCurrent(main_list);
			} catch (InternalApplicationException e) {
				logger.error(e);
			}
	
		}

		/** not as initiator */
		public boolean verify(){
			String verify = readLine();
			if (verify.equals(VERIFY)){
				String method = readLine();
				logger.info(method);
				byte[] hash = null;
				try {
					hash = Hash.doubleSHA256(authKey, false);
				} catch (InternalApplicationException e) {
					logger.error(e);
				}
				if (method.equals(VISUAL)){
					send (ACK);

					logger.info("start sleeping:"+System.currentTimeMillis());
					String start = readLine();
					if(start.equals(START)){
						
						sendViaVisualChannel();
						//prepare the input
						logger.info("done to sleep:"+System.currentTimeMillis());
						try {
							Thread.sleep(2000);
						} catch (InterruptedException e) {
							logger.error(e);
						}
						String success = readLine();
							
						if (success == null){
							success = readLine();
						}
						if(success.equals(SUCCESS)){
							logger.info("success");
							return true;
						}else{
							logger.info("auth failed");
							return false;
						}
					}
				}else if (method.equals(AUDIO)){
					method = AUDIO;
					send (ACK);
//					mainProgram.do_alert("AUDIO: preparing to transmit. ", Alert.FOREVER);
//					audio codec encode sound and play
						byte [] trimmedHash = new byte[AUDIO_KEY_LENGTH];
						
						try {
							hash = Hash.doubleSHA256(authKey, false);
							System.arraycopy(hash, 0, trimmedHash, 0, AUDIO_KEY_LENGTH);
							byte[] toSend = new String(Hex.encodeHex(trimmedHash)).getBytes();
							//we add some padding after the hash
							byte [] padded = new byte[toSend.length + AUDIO_PADDING];
							System.arraycopy(toSend, 0, padded, 0, toSend.length);
							byte [] sound = AudioUtils.encodeFileToWav(new ByteArrayInputStream(padded));

							Player player = Manager.createPlayer(new ByteArrayInputStream(sound), "encoding=pcm&rate=44100&bits=8&channels=1&endian=little&signed=true");
				
							player.prefetch(); 
							player.realize(); 
							
							VolumeControl  vc = (VolumeControl) player.getControl("VolumeControl");
							   if(vc != null) {
								   logger.info("volume level: "+vc.getLevel());
							      vc.setLevel(45);
							   }
							Form screen = new Form("Audio transmission");
							
							screen.addCommand(mainProgram.exit);
							screen.addCommand(mainProgram.log);
							screen.setCommandListener(this);

							mainProgram.display.setCurrent(screen);
							
							
							String start = readLine();
							if(start.equals(START)){
								screen.append("Sending data...\n");
								player.start();
								Thread.sleep(3000);
								send(DONE);
								screen.append("Finished sending.\n");
								String outcome = readLine();
								
								logger.info(outcome);
								if(outcome.equals(SUCCESS)){
									return true;
								}else{
									return false;
								}
							}

						} catch (Exception ioex) {
							logger.error(ioex);
						}

				}else if (method.equals(MADLIB)){
					send (ACK);
					MadLib madLib = new MadLib();
					try {
						logger.info("Madlib for key");
						String text = madLib.GenerateMadLib(Hash.doubleSHA256(authKey, false), 0, 5);
						Form outcome = new Form("OpenUAT");
						outcome.append("Verify the following sentence:\n");
						outcome.append(text);
						logger.info(": "+text);
						outcome.addCommand(mainProgram.log);
						outcome.addCommand(mainProgram.exit);
//						outcome.addCommand(successCmd);
//						outcome.addCommand(failure);

						outcome.setCommandListener(this);
						logger.info("set comands "+text);
						mainProgram.display.setCurrent(outcome);
						
						send(DONE);
						String result = readLine();
						
						logger.info(result);
						if(result.equals(SUCCESS)){
							logger.info("returning true");
							return true;
						}else{
							return false;
						}

					} catch (UnsupportedEncodingException e) {
						logger.error(e);
					} catch (InternalApplicationException e) {
						logger.error(e);
					};
				}else if (method.equals(SLOWCODEC)){
					send (ACK);
					Form outcome = new Form("OpenUAT");
					outcome.append("Listen to the tune and compare:\n");
					outcome.addCommand(mainProgram.log);
					outcome.addCommand(mainProgram.exit);
//					outcome.addCommand(successCmd);
//					outcome.addCommand(failure);

					outcome.setCommandListener(this);
					mainProgram.display.setCurrent(outcome);
					String done = readLine();
					if(done.equals(DONE)){
						slowCodec(authKey);
					}
					String result = readLine();

					logger.info(result);
					if(result.equals(SUCCESS)){
						logger.info("returning true");
						return true;
					}else{
						return false;
					}
				}
			}
			return false;
		}
	//	boolean authentic = true;
	
	/**
	 * Plays a melodic sound to verify the hash.
	 */
	private void slowCodec(byte [] key){
	
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
					boolean b = send(SUCCESS);
					mainProgram.informSuccess(true);					
				}
			} catch (InternalApplicationException e) {
				logger.error(e);
			}
	
		}

//	private void authenticateAudio() {
//		AudioChannel audioVerifier = new AudioChannel(mainProgram);
//		audioVerifier.setOOBMessageHandler(this);
//		audioVerifier.capture();
//	}

	private void getInputViaVisualChannel() {
		J2MEVisualChannel verifier = new J2MEVisualChannel(mainProgram);
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
		J2MEVisualChannel verifier = new J2MEVisualChannel(mainProgram);

		verifier.transmit(toSend.getBytes());


	}


	

	public void handleOOBMessage(int channelType, byte [] data) {
		if (channelType == OOBChannel.AUDIO_CHANNEL){
			handleAudioMessage(data);
		}else	if (channelType == OOBChannel.VIDEO_CHANNEL){
			handleVisualDecodedText(new String(data));
		}
	
	}


	public void verifyMadLib() {
		send(VERIFY);
		send(MADLIB);
		String response = readLine();
		//System.out.println("response "+response);
		if (response.equals(ACK)){
			MadLib madLib = new MadLib();
			try {
				logger.info("Madlib for key");
				String text = madLib.GenerateMadLib(Hash.doubleSHA256(authKey, false), 0, 5);
				Form outcome = new Form("OpenUAT");
				outcome.append("Verify the following sentence:\n");
				outcome.append(text);
				logger.info(": "+text);
				outcome.addCommand(mainProgram.log);
				outcome.addCommand(mainProgram.exit);
				outcome.addCommand(successCmd);
				outcome.addCommand(failure);

				outcome.setCommandListener(this);
				logger.info("set comands "+text);
				mainProgram.display.setCurrent(outcome);

			} catch (UnsupportedEncodingException e) {
				logger.error(e);
			} catch (InternalApplicationException e) {
				logger.error(e);
			}
		}
	}
	
	public void verifySlowCodec() {
		send(VERIFY);
		send(SLOWCODEC);
		String response = readLine();
		//System.out.println("response "+response);
		if (response.equals(ACK)){


			logger.info("slowcodec for key");
			Form outcome = new Form("OpenUAT");
			outcome.append("Listen to the tune and compare:\n");
			outcome.addCommand(mainProgram.log);
			outcome.addCommand(mainProgram.exit);
			outcome.addCommand(successCmd);
			outcome.addCommand(failure);

			outcome.setCommandListener(this);
			mainProgram.display.setCurrent(outcome);

			slowCodec(authKey);
			try {
				Thread.sleep(3000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			send(DONE);
		}
	}
	
}
