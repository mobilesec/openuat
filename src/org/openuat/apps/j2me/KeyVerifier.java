package org.openuat.apps.j2me;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.ImageItem;
import javax.microedition.lcdui.Item;
import javax.microedition.lcdui.ItemCommandListener;
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
import org.openbandy.service.LogService;
import org.openuat.authentication.OOBChannel;
import org.openuat.authentication.OOBMessageHandler;
import org.openuat.authentication.exceptions.InternalApplicationException;
import org.openuat.channel.oob.j2me.J2MEAudioChannel;
import org.openuat.channel.oob.j2me.J2MEVisualChannel;
import org.openuat.util.BluetoothRFCOMMChannel;
import org.openuat.util.Hash;
import org.openuat.util.LineReaderWriter;
import org.openuat.util.RemoteConnection;

public class KeyVerifier implements CommandListener, ItemCommandListener, OOBMessageHandler  {
	
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
	
//	Logger logger = Logger.getLogger("");
	byte [] authKey;
	OpenUATmidlet mainProgram;
	RemoteConnection connectionToRemote;
	OutputStream out ;
	InputStream in ;
	
	Command successCmd;
	Image yes;
	Image no;
	Command failure;
	
	public KeyVerifier(byte [] authKey, RemoteConnection connectionToRemote, OpenUATmidlet mainProgram ) {
		this.authKey = authKey;
		this.mainProgram = mainProgram;
		this.connectionToRemote = connectionToRemote;
		try {
			out = connectionToRemote.getOutputStream();
			in = connectionToRemote.getInputStream();
		} catch (IOException e) {
			LogService.error(this, "IOException", e);
		}
		mainProgram.do_alert("Key exchanges successfully. Starting verification process.", 1000);
		
		successCmd = new Command("Success", Command.SCREEN, 1);
		failure = new Command("Failure", Command.SCREEN, 1);
		
		try {
			yes = Image.createImage("/button_ok.png");
		} catch (IOException e) {}
		try {
			no = Image.createImage("/button_cancel.png");
		} catch (IOException e) {}
		
	}
	
	
	private boolean send(String message) {
		try {
			
			LineReaderWriter.println(out, message);
			LogService.info(this, "sent message:"+message);
			return true;
		} catch (IOException e) {
			LogService.info(this, "Failed to send message: "+message);
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
						LogService.info(this, "Failed to reopen BT connection ");
					}
					
				}
			}
//				
//				boolean open = connectionToRemote.open();
//				if(! open)
//					LogService.info(this, "Could not reopen connection");
//			}
//			
//			LineReaderWriter.println(out, message);
//			return true;
			LogService.debug(this, "Unable to open stream to remote: " + e);
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
				LogService.info(this, "read: " + line);
			} 
			catch (InterruptedIOException e) {


				LogService.debug(this, "InterruptedIOException while reading: " + e);
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
//				LogService.info(this, "trying to reopen connection to "+address + " - "+number);
//				connectionToRemote = new BluetoothRFCOMMChannel(address, number);
//				connectionToRemote.open();
//				out = connectionToRemote.getOutputStream();
//				in = connectionToRemote.getInputStream();
//				//try again
//				return readLine();
//				} catch (IOException e1) {
//				LogService.info(this, "Failed to reopen BT connection ");
//				LogService.error(this, "", e1);
//				}

//				}
			}catch (IOException e) {
				LogService.debug(this, "Unable to open stream to remote: " + e);
				LogService.debug(this, "" + e.getMessage());
//				if (!connectionToRemote.isOpen()){
//				if (connectionToRemote instanceof BluetoothRFCOMMChannel) {

//				try {
//				String address = connectionToRemote.getRemoteAddress().toString();
//				int number = ((BluetoothRFCOMMChannel)connectionToRemote).getRemoteChannelNumber();
//				LogService.info(this, "trying to reopen connection to "+address + " - "+number);
//				connectionToRemote = new BluetoothRFCOMMChannel(address, number);
//				connectionToRemote.open();
//				out = connectionToRemote.getOutputStream();
//				in = connectionToRemote.getInputStream();
//				//try again
//				return readLine();
//				} catch (IOException e1) {
//				LogService.info(this, "Failed to reopen BT connection ");
//				LogService.error(this, "", e1);
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
		Form progress = new Form("OpenUAT");

		progress.addCommand(mainProgram.log);
		progress.addCommand(mainProgram.exit);

		progress.setCommandListener(mainProgram);
		Display.getDisplay(mainProgram).setCurrent(progress);
		String response = readLine();
		//System.out.println("response "+response);
		if (response.equals(ACK)){
			mainProgram.display.setCurrent(progress);
			J2MEAudioChannel verifier = new J2MEAudioChannel(mainProgram);
			verifier.setOOBMessageHandler(this);
			verifier.capture();

//			try {
//				Thread.sleep(200);
//			} catch (InterruptedException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
			Display.getDisplay(mainProgram).setCurrent(progress);
			progress.append("Capturing...\n");
//			try {
//				Thread.sleep(3500);
//			} catch (InterruptedException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
			
//			byte recorded [] = verifier.finishCapturing();
			send (START);
			String done = readLine();
			if(done.equals(DONE)){
				
				LogService.info(this, "finishing audio capture");
//				mainProgram.do_alert("finishing capture", Alert.FOREVER);
				byte recorded [] = verifier.finishCapturing();
				progress.append("Done.\n");
				progress.append("decoding "+recorded.length+ " bytes...\n");
				LogService.info(this, "captured "+recorded.length+ " ...\n");
				
				ByteArrayInputStream recordedStream = new ByteArrayInputStream(recorded);
				Player player;
				try {
					player = Manager.createPlayer(recordedStream, "audio/x-wav");

					player.prefetch(); 
					player.realize();
					
					
					VolumeControl  vc = (VolumeControl) player.getControl("VolumeControl");
					   if(vc != null) {
						   LogService.info(this, "volume level: "+vc.getLevel());
					      vc.setLevel(45);
					   }
					   player.start(); 
//					recordScreen.deleteAll();
//					recordScreen.setTitle("Playing recording ");
//					recordScreen.append("This is the recorded sequence.");
//					recordScreen.removeCommand(playRec);

				} catch (Exception e) {
					LogService.error(this, "could not play recording", e);
				}
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				long begin = System.currentTimeMillis();
				byte decoded [] = verifier.decodeAudio(recorded);
				long end = System.currentTimeMillis();
				LogService.info(this, "decoding took "+(end-begin) + "ms");
				progress.append("Done. \n");
				handleOOBMessage(J2MEAudioChannel.AUDIO_CHANNEL, decoded);
			}
		}
	}


	public void verifyVisual() {
		LogService.info(this, "visual-send");
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
//				LogService.error(this, "", e);
//			} catch (IOException e) {
//				LogService.error(this, "", e);
//		} catch (MediaException e) {
//			LogService.error(this, "", e);
//		}
//		
//	}

	/**
		 *  we handle visual and audio separately because the channels might have different properties and transmission capacity.
		 * 
		 * @param remoteHash
		 */
		private void handleAudioMessage(byte[] data) {
			LogService.info(this, "audio verification result ---------- " );
	
			byte hash[];
			try {
				hash = Hash.doubleSHA256(authKey, false);
				byte [] trimmedHash = new byte[AUDIO_KEY_LENGTH];
				System.arraycopy(hash, 0, trimmedHash, 0, AUDIO_KEY_LENGTH);
				String localHash = new String(Hex.encodeHex(trimmedHash));
				String remoteHash = new String(data);
				LogService.info(this, "local hash: " + localHash);
				LogService.info(this, "remote hash: " + remoteHash);
	
	//			if(remoteHash.equals(localHash)){
	//				LogService.info(this, "verification ended");
	//				LogService.info(this, "hashes are equal");
	//
	//				main_list.append("verification successfull", null);
	//
	//			}else{
	//				LogService.info(this, "hashes differ");
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
						LogService.info(this, "verification successful. first bits are the same.");
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
				LogService.error(this, "", e);
			}
	
		}

		/** not as initiator */
		public boolean verify(){
			Form verification = new Form ("OpenUAT");
			verification.append("Verifying the key transfer");
			Image img = null;
			try {
				img = Image.createImage("/running.png");
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			verification.append(img);
			verification.addCommand(mainProgram.exit);
			verification.addCommand(mainProgram.log);
			verification.setCommandListener(mainProgram);
			Display.getDisplay(mainProgram).setCurrent(verification);
			
			String verify = readLine();
			if (verify.equals(VERIFY)){
				String method = readLine();
				LogService.info(this, method);
				byte[] hash = null;
				try {
					hash = Hash.doubleSHA256(authKey, false);
				} catch (InternalApplicationException e) {
					LogService.error(this, "", e);
				}
				if (method.equals(VISUAL)){
					send (ACK);

					LogService.info(this, "start sleeping:"+System.currentTimeMillis());
					String start = readLine();
					if(start.equals(START)){
						
						sendViaVisualChannel();
						//prepare the input
						LogService.info(this, "done to sleep:"+System.currentTimeMillis());
						try {
							Thread.sleep(2000);
						} catch (InterruptedException e) {
							LogService.error(this, "", e);
						}
						String success = readLine();
							
						if (success == null){
							success = readLine();
						}
						if(success.equals(SUCCESS)){
							LogService.info(this, "success");
							return true;
						}else{
							LogService.info(this, "auth failed");
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
								   LogService.info(this, "volume level: "+vc.getLevel());
							      vc.setLevel(45);
							   }

							String start = readLine();
							if(start.equals(START)){
								verification.append("Sending data...\n");
								player.start();
								Thread.sleep(3200);
								send(DONE);
								verification.append("Finished sending.\n");
								String outcome = readLine();
								
								LogService.info(this, outcome);
								if(outcome.equals(SUCCESS)){
									return true;
								}else{
									return false;
								}
							}

						} catch (Exception ioex) {
							LogService.error(this, "", ioex);
						}

				}else if (method.equals(MADLIB)){
					send (ACK);
					MadLib madLib = new MadLib();
					try {
						LogService.info(this, "Madlib for key");
						String text = madLib.GenerateMadLib(Hash.doubleSHA256(authKey, false), 0, 5);
						Form outcome = new Form("OpenUAT");
						outcome.append("Verify the following sentence:\n");
						Image img_madlib = null;
						try {
							img_madlib = Image.createImage("/madlib_sm.png");
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						outcome.append(img_madlib);
						outcome.append(text);
						LogService.info(this, ": "+text);
						outcome.addCommand(mainProgram.log);
						outcome.addCommand(mainProgram.exit);
//						outcome.addCommand(successCmd);
//						outcome.addCommand(failure);

						outcome.setCommandListener(this);
						LogService.info(this, "set comands "+text);
						mainProgram.display.setCurrent(outcome);
						
						send(DONE);
						String result = readLine();
						
						LogService.info(this, result);
						if(result.equals(SUCCESS)){
							LogService.info(this, "returning true");
							return true;
						}else{
							return false;
						}

					} catch (UnsupportedEncodingException e) {
						LogService.error(this, "", e);
					} catch (InternalApplicationException e) {
						LogService.error(this, "", e);
					};
				}else if (method.equals(SLOWCODEC)){
					send (ACK);
					Form outcome = new Form("OpenUAT");
					try {
						Image slowcodec = Image.createImage("/slowcodec_sm.png");
						outcome.append(slowcodec);
					} catch (IOException e2) {
					}
					outcome.append("Listen to the tune and compare\n");
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

					LogService.info(this, result);
					if(result.equals(SUCCESS)){
						LogService.info(this, "returning true");
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
				   LogService.info(this, "volume level: "+vc.getLevel());
			      vc.setLevel(45);
			   }
			p.start(); 
		} catch (Exception e) { 
			LogService.error(this, "", e);
		}
	
	}

		//	TODO notify successin a nice manner
		private void handleVisualDecodedText(String text) {
			String remoteHash = text;
			LogService.info(this, "visual verification result ---------- " );
			LogService.info(this, "remote hash: " + remoteHash);
			byte hash[];
			try {
				hash = Hash.doubleSHA256(authKey, false);
				byte [] trimmedHash = new byte[7];
				System.arraycopy(hash, 0, trimmedHash, 0, 7);
				String localHash = new String(Hex.encodeHex(trimmedHash));
				LogService.info(this, "local hash: " + localHash);
	
				if(remoteHash.equals(localHash)){
					LogService.info(this, "verification ended");
					LogService.info(this, "hashes are equal");
					boolean b = send(SUCCESS);
					mainProgram.informSuccess(true);					
				}
			} catch (InternalApplicationException e) {
				LogService.error(this, "", e);
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
		LogService.info(this, "sendViaVisualChannel");
		byte [] trimmedHash = new byte[7];
		byte[] hash= null;

		try {
			hash = Hash.doubleSHA256(authKey, false);
		} catch (InternalApplicationException e) {

			LogService.error(this, "", e);
			return;
		}
		System.arraycopy(hash, 0, trimmedHash, 0, 7);
		String toSend = new String(Hex.encodeHex(trimmedHash));
		LogService.info(this, "creating visual channel");
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
				LogService.info(this, "Madlib for key");
				String text = madLib.GenerateMadLib(Hash.doubleSHA256(authKey, false), 0, 5);
				Form outcome = new Form("OpenUAT");
				outcome.append("Verify the following sentence:\n");
				Image img_madlib = null;
				try {
					img_madlib = Image.createImage("/madlib_sm.png");
				} catch (IOException e) {}
				
				outcome.append(img_madlib);
				outcome.append(text);
				
				ImageItem yes_btn = new ImageItem("YES", yes,ImageItem.LAYOUT_CENTER, "");
				outcome.append(yes_btn);
				
				ImageItem no_btn = new ImageItem("NO", no, ImageItem.LAYOUT_CENTER, "");
				outcome.append(no_btn);
				no_btn.addCommand(failure);
				yes_btn.addCommand(successCmd);
				no_btn.setItemCommandListener(this);
				yes_btn.setItemCommandListener(this);
				
				LogService.info(this, ": "+text);
				outcome.addCommand(mainProgram.log);
				outcome.addCommand(mainProgram.exit);
				outcome.addCommand(successCmd);
				outcome.addCommand(failure);

				outcome.setCommandListener(this);
				LogService.info(this, "set comands "+text);
				mainProgram.display.setCurrent(outcome);

			} catch (UnsupportedEncodingException e) {
				LogService.error(this, "", e);
			} catch (InternalApplicationException e) {
				LogService.error(this, "", e);
			}
		}
	}
	
	public void verifySlowCodec() {
		send(VERIFY);
		send(SLOWCODEC);
		Form outcome = new Form("OpenUAT");
		String response = readLine();
		//System.out.println("response "+response);
		if (response.equals(ACK)){

			
			
			LogService.info(this, "slowcodec for key");
			
			
			try {
				Image slowcodec = Image.createImage("/slowcodec_sm.png");
				outcome.append(slowcodec);
			} catch (IOException e2) {
			}
			outcome.append("Listen to the tune and compare\n");
			ImageItem yes_btn = new ImageItem("YES", yes,ImageItem.LAYOUT_CENTER, "");
			outcome.append(yes_btn);
			
			ImageItem no_btn = new ImageItem("NO", no, ImageItem.LAYOUT_CENTER, "");
			outcome.append(no_btn);
			no_btn.addCommand(failure);
			yes_btn.addCommand(successCmd);
			no_btn.setItemCommandListener(this);
			yes_btn.setItemCommandListener(this);
			
			outcome.addCommand(mainProgram.log);
			outcome.addCommand(mainProgram.exit);
			outcome.addCommand(successCmd);
			outcome.addCommand(failure);

			outcome.setCommandListener(this);
			mainProgram.display.setCurrent(outcome);

			slowCodec(authKey);
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			//give chance to replay tune
			
			send(DONE);
		}
	}


	public void commandAction(Command com, Item arg1) {
		if (com == successCmd){
			send(SUCCESS);
			mainProgram.informSuccess(true);
			return;
		}else if (com == failure){
			send(FAILURE);
			mainProgram.informSuccess(false);
			return;
		}
		
	}
	
}
