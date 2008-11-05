package org.openuat.apps.j2me;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

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
import javax.microedition.lcdui.StringItem;
import javax.microedition.media.Manager;
import javax.microedition.media.MediaException;
import javax.microedition.media.Player;
import javax.microedition.media.control.ToneControl;
import javax.microedition.media.control.VolumeControl;

import org.apache.commons.codec.binary.Hex;
import org.codec.audio.j2me.AudioUtils;
import org.codec.audio.j2me.PlayerPianoJ2ME;
import org.codec.audio.j2me.WavCodec;
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

	/** Replay the slow codec tune */
	public static final String REPLAY = "REPLAY";
	
	private int method ;

	//	Logger logger = Logger.getLogger("");
	byte [] authKey;
	OpenUATmidlet mainProgram;
	RemoteConnection connectionToRemote;
	OutputStream out ;
	InputStream in ;
	private Form progress = new Form("OpenUAT");
	
	private Form verification = new Form ("OpenUAT");
	
	//	Command successCmd;
	//	Image yes;
	//	Image no;
	//	Command failure;

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

	}


	private boolean send(String message) {
		try {

			LineReaderWriter.println(out, message);
//			LogService.info(this, "sent message:"+message);
			return true;
		} catch (IOException e) {
			LogService.error(this, "Failed to send message: "+message, e);
		}
		return false;

	}
	private String readLine(int timeout) {
		String line = null;
		try {

			line = LineReaderWriter.readLine(in, timeout);
//			LogService.debug(this, "read: " + line);
		} 
		catch (InterruptedIOException e) {
			LogService.debug(this, "InterruptedIOException while reading: " + e);
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}catch (IOException e) {
			LogService.debug(this, "Unable to open stream to remote: " + e);
			LogService.debug(this, "" + e.getMessage());
		}
		return line;

	}
	private String readLine() {
		String line = null;
		try {

			line = LineReaderWriter.readLine(in, -1);
//			LogService.debug(this, "read: " + line);
		} 
		catch (InterruptedIOException e) {
			LogService.debug(this, "InterruptedIOException while reading: " + e);
		}catch (IOException e) {
			LogService.debug(this, "Unable to open stream to remote: " + e);
			LogService.debug(this, "" + e.getMessage());
		}
		return line;

	}


	public void commandAction(Command com, Displayable arg1) {

		if (com == mainProgram.successCmd){
			send(SUCCESS);
			mainProgram.informSuccess(true);
			return;
		}else if (com == mainProgram.failureCmd){
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
		else if (com.getCommandType() == Command.BACK ){
			mainProgram.commandAction(com, arg1);
			return;
		}

		//		mainProgram.do_alert("Invoked command"+com, 1000);
		else if (com == List.SELECT_COMMAND) {
			List option = (List) arg1;
			if (option.getSelectedIndex() == 0){
				verifyVisual();
			}else if (option.getSelectedIndex() == 1){
				verifyAudio();
			}else if (option.getSelectedIndex() == 2){ 
				verifySlowCodec();
			}else if (option.getSelectedIndex() == 3){
				verifyMadLib();
			}
		}else {
			mainProgram.commandAction(com, arg1);
		}
	}
	J2MEAudioChannel verifier;

	public void verifyAudio() {
		LogService.info(this, AUDIO);
		method = 2;
		mainProgram.startTime = System.currentTimeMillis();
		
		mainProgram.display.setCurrent(progress);
		send(VERIFY);
		send(AUDIO);
		
		progress.addCommand(mainProgram.log);
		progress.addCommand(mainProgram.exit);

		progress.setCommandListener(mainProgram);
		String response = readLine();
		//System.out.println("response "+response);
		if (response.equals(ACK)){
			mainProgram.display.setCurrent(progress);
			verifier = new J2MEAudioChannel(Display.getDisplay(mainProgram),
					mainProgram.getHomeScreen(), mainProgram.volume);
			verifier.setOOBMessageHandler(this);
			tryAudioMaster();
		}
	}


	private void tryAudioMaster() {
		progress.deleteAll();
		verifier.capture();

		//			try {
		//				Thread.sleep(200);
		//			} catch (InterruptedException e) {
		//				// TODO Auto-generated catch block
		//				e.printStackTrace();
		//			}
		Display.getDisplay(mainProgram).setCurrent(progress);
		progress.append("Capturing...\n");
		try {
			Thread.sleep(150);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		send (START);
		String done = readLine(7000);
//		LogService.debug(this, "finishing audio capture");
		//				mainProgram.do_alert("finishing capture", Alert.FOREVER);
		byte recorded [] = verifier.finishCapturing();
		if( done.equals(DONE)){

			progress.append("Done.\n");
			progress.deleteAll();
			//				progress.append("Decoding ... "+recorded.length+ " bytes...\n");
			progress.append("Decoding ... \n");
			mainProgram.display.setCurrent(progress);
			LogService.info(this, "captured "+recorded.length+ " ...\n");

//							ByteArrayInputStream recordedStream = new ByteArrayInputStream(recorded);
//							Player player;
//							try {
//								player = Manager.createPlayer(recordedStream, "audio/x-wav");
//			
//								player.prefetch(); 
//								player.realize();
//								
//								
//								VolumeControl  vc = (VolumeControl) player.getControl("VolumeControl");
//								   if(vc != null) {
//									   LogService.info(this, "volume level: "+vc.getLevel());
//								      vc.setLevel(45);
//								   }
//								   player.start(); 
//			//					recordScreen.deleteAll();
//			//					recordScreen.setTitle("Playing recording ");
//			//					recordScreen.append("This is the recorded sequence.");
//			//					recordScreen.removeCommand(playRec);
//			
//							} catch (Exception e) {
//								LogService.error(this, "could not play recording", e);
//							}
//							try {
//								Thread.sleep(5000);
//							} catch (InterruptedException e1) {
//								// TODO Auto-generated catch block
//								e1.printStackTrace();
//							}
//							long begin = System.currentTimeMillis();
			verifier.decodeAudio(recorded);
			//				long end = System.currentTimeMillis();
			//				LogService.info(this, "decoding took "+(end-begin) + "ms");
			//				progress.append("Done. \n");
			//				handleOOBMessage(J2MEAudioChannel.AUDIO_CHANNEL, decoded);
		}else {
			LogService.warn(this, "Forced finish recording");
		}
	}


	public void verifyVisual() {
		LogService.info(this, VISUAL);
		send(VERIFY);
		send(VISUAL);
		method = 1;

		mainProgram.startTime = System.currentTimeMillis();
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
	
	private int audio_failure_count = 0;
	
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
			LogService.info(this, "making string of the date " );

			String remoteHash = new String(data);
			LogService.info(this, "local hash: " + localHash);
			LogService.info(this, "remote hash: " + remoteHash);

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
				
				//						mainProgram.do_alert("verification successful.", Alert.FOREVER);
				//						main_list.append("----", null);
				//						main_list.append("Successful pairing", null);
				send(SUCCESS);
				mainProgram.informSuccess(true);
			}else{
				LogService.info(this, "audio verification failed");
				//						main_list.append("authentication failed", null);
				audio_failure_count ++;
				if (audio_failure_count > 3)
				{
					send(FAILURE);
					mainProgram.informSuccess(false);
				}else{
					Form replay_form =  new Form ("OpenUAT");
					replay_form.append("Authentication failed.\n Do you want to retry?\n");
					ImageItem cancel = new ImageItem("Cancel", mainProgram.buttoncancel, ImageItem.LAYOUT_CENTER, "");
					
					cancel.addCommand(mainProgram.failureCmd);
					
					cancel.setItemCommandListener(this);

					ImageItem  retry = new ImageItem ("Retry", mainProgram.replay, ImageItem.LAYOUT_CENTER, "");

					retry.addCommand(new Command("Retry", Command.ITEM, 1));
					replay_form.append(retry);
					replay_form.append(cancel);
					retry.setItemCommandListener(this);
					mainProgram.display.setCurrent(replay_form);

				}
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

		String verify = readLine();
		if (verify.equals(VERIFY)){


			
			verification.append("Verifying the key transfer");
			verification.append(mainProgram.running);
			verification.addCommand(mainProgram.exit);
			verification.addCommand(mainProgram.log);
			verification.setCommandListener(mainProgram);
			Display.getDisplay(mainProgram).setCurrent(verification);

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

				String start = readLine();
				if(start.equals(START)){

					sendViaVisualChannel();
					//prepare the input
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
//						LogService.info(this, "volume level: "+vc.getLevel());
						vc.setLevel(mainProgram.volume);
					}

					return audioSendSlave(verification, player);

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
					outcome.append(mainProgram.madlib);
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
					}else if (result.equals(FAILURE)){
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
				outcome.append(mainProgram.slowcodec);
				outcome.append("Listen to the tune and compare\n");
				outcome.addCommand(mainProgram.log);
				outcome.addCommand(mainProgram.exit);
				//					outcome.addCommand(successCmd);
				//					outcome.addCommand(failure);

				outcome.setCommandListener(this);
				mainProgram.display.setCurrent(outcome);
				return slowCodecSlave();
			}
		}
		return false;
	}
	//	boolean authentic = true;


	private boolean audioSendSlave(Form verification, Player player)
			throws MediaException, InterruptedException {
		String start = readLine();
		if(start.equals(START)){
			verification.append("\nSending data...\n");
			player.start();

			Thread.sleep(3000);

			send(DONE);
			verification.append("Finished sending.\n");
			String outcome = readLine();

			LogService.info(this, outcome);
			if(outcome.equals(SUCCESS)){
				return true;
			}else if (outcome.equals(FAILURE)){
				return false;
			}else if (outcome.equals(REPLAY)){
				return audioSendSlave(verification, player);
			}
		}
		return false;
	}


	
	private boolean slowCodecSlave() {
		String done = readLine();
		if(done.equals(DONE)){
			slowCodec(authKey);
		}
		String result = readLine();

		while (result != null){
			LogService.info(this, result);
			if(result.equals(SUCCESS)){
				return true;
			}else if (result.equals(FAILURE)){
				return false;
			}else if (result.equals(REPLAY)){
				return slowCodecSlave();
			}
		}
		return false;
	}

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
//				LogService.info(this, "volume level: "+vc.getLevel());
				vc.setLevel(mainProgram.volume);
			}
			p.start(); 
		} catch (Exception e) { 
			LogService.error(this, "", e);
		}

	}

	//	TODO notify successin a nice manner
	private void handleVisualDecodedText(String text) {
		String remoteHash = text;
//		LogService.debug(this, "visual verification result ---------- " );
//		LogService.debug(this, "remote hash: " + remoteHash);
		byte hash[];
		try {
			hash = Hash.doubleSHA256(authKey, false);
			byte [] trimmedHash = new byte[7];
			System.arraycopy(hash, 0, trimmedHash, 0, 7);
			String localHash = new String(Hex.encodeHex(trimmedHash));
			LogService.debug(this, "local hash: " + localHash);

			if(remoteHash.equals(localHash)){
//				LogService.info(this, "verification ended");
//				LogService.info(this, "hashes are equal");
				boolean b = send(SUCCESS);
				mainProgram.informSuccess(b);					
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
		J2MEVisualChannel verifier = new J2MEVisualChannel(
				Display.getDisplay(mainProgram), mainProgram.getClass(), mainProgram,
				mainProgram.getBack(), mainProgram.getSuccessCmd(), mainProgram.getFailure());
		verifier.setOOBMessageHandler(this);
		verifier.capture();
	}

	private void sendViaVisualChannel() {
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
//		LogService.debug(this, "creating visual channel");
		J2MEVisualChannel verifier = new J2MEVisualChannel(
			Display.getDisplay(mainProgram), mainProgram.getClass(), mainProgram,
			mainProgram.getBack(), mainProgram.getSuccessCmd(), mainProgram.getFailure());

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
		method = 4;
		send(VERIFY);
		send(MADLIB);
		LogService.info(this, MADLIB);
		String response = readLine();
		mainProgram.startTime = System.currentTimeMillis();
		//System.out.println("response "+response);
		if (response.equals(ACK)){
			MadLib madLib = new MadLib();
			try {
				
				String text = madLib.GenerateMadLib(Hash.doubleSHA256(authKey, false), 0, 5);
				Form outcome = new Form("OpenUAT");
				outcome.append("Verify the following sentence:\n");
				outcome.append(mainProgram.madlib);
				outcome.append(text);

				ImageItem yes_btn = new ImageItem("YES", mainProgram.buttonok,ImageItem.LAYOUT_CENTER, "");
				//				StringItem yes_btn = new StringItem(" YES ", null, Item.BUTTON);


				ImageItem no_btn = new ImageItem("NO", mainProgram.buttoncancel, ImageItem.LAYOUT_CENTER, "");
				//				StringItem no_btn = new StringItem("  NO ", null, Item.BUTTON);
				
				yes_btn.addCommand(mainProgram.successCmd);
				no_btn.addCommand(mainProgram.failureCmd);
				
				no_btn.setItemCommandListener(this);
				yes_btn.setItemCommandListener(this);



				outcome.append(no_btn);
				outcome.append(yes_btn);

//				LogService.debug(this, ": "+text);
				outcome.addCommand(mainProgram.log);
				outcome.addCommand(mainProgram.exit);
				//				outcome.addCommand(successCmd);
				//				outcome.addCommand(failure);

				outcome.setCommandListener(this);
				mainProgram.display.setCurrent(outcome);
				
				mainProgram.startTimeMadLib = System.currentTimeMillis();

			} catch (UnsupportedEncodingException e) {
				LogService.error(this, "", e);
			} catch (InternalApplicationException e) {
				LogService.error(this, "", e);
			}
		}
	}

	public void verifySlowCodec() {
		method = 3;
		send(VERIFY);
		send(SLOWCODEC);
		LogService.info(this, SLOWCODEC);
		mainProgram.startTime = System.currentTimeMillis();
		Form outcome = new Form("OpenUAT");
		String response = readLine();
		//System.out.println("response "+response);
		if (response.equals(ACK)){

//			LogService.debug(this, "slowcodec for key");

			outcome.append(mainProgram.slowcodec);
			outcome.append("\nListen to the tune and compare.\nIs it the same?\n");
			ImageItem yes_btn = new ImageItem("YES", mainProgram.buttonok,ImageItem.LAYOUT_CENTER, "");
			//			StringItem yes_btn = new StringItem("YES", null, Item.BUTTON);


			ImageItem no_btn = new ImageItem("NO", mainProgram.buttoncancel, ImageItem.LAYOUT_CENTER, "");
			//			StringItem no_btn = new StringItem("NO", null, Item.BUTTON);
			no_btn.addCommand(mainProgram.failureCmd);
			yes_btn.addCommand(mainProgram.successCmd);
			no_btn.setItemCommandListener(this);
			yes_btn.setItemCommandListener(this);

			
			outcome.append(no_btn);
			outcome.append(yes_btn);

			ImageItem  replay = new ImageItem ("Replay", mainProgram.replay, ImageItem.LAYOUT_CENTER, "");
			replay.addCommand(new Command("Replay", Command.ITEM, 1));
			replay.setItemCommandListener(this);
			outcome.append(replay);

			outcome.addCommand(mainProgram.log);
			outcome.addCommand(mainProgram.exit);
			outcome.addCommand(mainProgram.successCmd);
			outcome.addCommand(mainProgram.failureCmd);

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
		if (com == mainProgram.successCmd){
			send(SUCCESS);
			mainProgram.informSuccess(true);
			if(method==4)
				LogService.info(this, "madlib think time:"+(System.currentTimeMillis()-mainProgram.startTimeMadLib));
			return;
		}else if (com == mainProgram.failureCmd){
			send(FAILURE);
			mainProgram.informSuccess(false);
			if(method==4)
				LogService.info(this, "madlib think time:"+(System.currentTimeMillis()-mainProgram.startTimeMadLib));
			
			return;
		}else if (com.getLabel().equals("Replay")){
			
			
	
//			mainProgram.numListensSlowCodec ++;
			send(REPLAY);
			slowCodec(authKey);
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			send(DONE);
		}else if (com.getLabel().equals("Retry")){ //audio
			send(REPLAY);
			tryAudioMaster();
		}

	}

}
