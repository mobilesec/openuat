package org.openuat.channel.oob.j2me;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.media.Manager;
import javax.microedition.media.MediaException;
import javax.microedition.media.Player;
import javax.microedition.media.control.RecordControl;
import javax.microedition.media.control.VolumeControl;

import org.apache.log4j.Logger;
import org.codec.audio.j2me.AudioUtils;
import org.openbandy.service.LogService;
import org.openuat.apps.j2me.OpenUATmidlet;
import org.openuat.authentication.OOBChannel;
import org.openuat.authentication.OOBMessageHandler;

public class J2MEAudioChannel implements OOBChannel, CommandListener {
	
	//UI --> not sure how much of this we need, just the display should be fine
	OpenUATmidlet mainProgram;
	
	OOBMessageHandler messageHandler;
	Logger logger = Logger.getLogger("");
	
	Display display;
	private Form recordScreen;
	
	private Command stopRec;
	private Command playRec = new Command("Play", Command.SCREEN, 1);
	 Command decodeCmd = new Command("Decode", Command.SCREEN, 1);
	 
		private Player captureAudioPlayer;
		private RecordControl rc;
		private ByteArrayOutputStream output;

		/** The last recorded sequence */
		 byte [] recorded = null ;
		 
		 //put only the display
	public J2MEAudioChannel(OpenUATmidlet mainProgram) {
		this.mainProgram = mainProgram;
		display = Display.getDisplay(mainProgram);
		
	}
	
	public void commandAction(Command com, Displayable dis) {
//		if (com == exit) { //exit triggered from the main form
//			if (rfcommServer != null)
//				try {
//					rfcommServer.stop();
//				} catch (InternalApplicationException e) {
//					do_alert("Could not de-register SDP service: " + e, Alert.FOREVER);
//				}
//				destroyApp(false);
//				notifyDestroyed();
//		}
//		else 
//			if (com == back) {
//			if (dis == serv_list) { //back button is pressed in devices list
//				display.setCurrent(dev_list);
//			}
//		}
//		else if (com == log) {
//			display.setCurrent(logForm);
//		}else 
			if(com.equals(stopRec)){
			finishCapturing();
		}
		else if(com.equals(playRec)){
			playRecording();
		}else if(com.equals(decodeCmd)){
			decodeAudio(recorded);
		}
		else if(com.getCommandType() == Command.BACK){
			display.setCurrent(mainProgram.getHomeScreen());
		}

	}
	
	private void playRecording() {
		ByteArrayInputStream recordedStream = new ByteArrayInputStream(recorded);
		Player player;
		try {
			player = Manager.createPlayer(recordedStream, "audio/x-wav");

			player.prefetch(); 
			player.realize();
			player.start(); 

//			recordScreen.deleteAll();
//			recordScreen.setTitle("Playing recording ");
//			recordScreen.append("This is the recorded sequence.");
//			recordScreen.removeCommand(playRec);

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			Alert alert = new Alert("error", e.getClass().toString() + ": " + e.getMessage(), null, AlertType.ERROR);
			display.setCurrent(alert);
		} catch (MediaException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			Alert alert = new Alert("error", e.getClass().toString() + ": " + e.getMessage(), null, AlertType.ERROR);
			display.setCurrent(alert);
		}
		captureAudioPlayer.close();
	}
	private Form decodeScreen;
	public void  decodeAudio(byte [] audiodata) {

		
//		decodeScreen = new Form("decoding...\n");
//		decodeScreen.setCommandListener(this);
//		decodeScreen.append("initial: "+ audiodata.length + "bytes\n");
//		decodeScreen.addCommand(mainProgram.exit);
//		decodeScreen.addCommand(new Command("Back", Command.BACK, 1));
//		display.setCurrent(decodeScreen);
		
//		new DecoderThread(decodeScreen, display, this, audiodata).start();
		ByteArrayOutputStream dataStream = new ByteArrayOutputStream();
		
		DecoderThread decoder = new DecoderThread(audiodata, dataStream, this);
		decoder.start();
//		while(!decoder.done){
//			try {
//				Thread.sleep(200);
//			} catch (InterruptedException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//		}
//		handleAudioDecodedText(decoder.retrieved);
//		for (int i = 0; i < retrieved.length; i++) {
//		decodeScreen.append( retrieved [i] + ", ");
//		}

//		return retrieved;
//		handleAudioDecodedText(retrieved);
}
	
	
	public byte [] finishCapturing() {
		try {
			//stop recording
			rc.commit();
			rc.stopRecord();
			captureAudioPlayer.stop();
			captureAudioPlayer.close();

			recorded = output.toByteArray();
			output.close();

			//update screen
//			recordScreen.deleteAll();
//			recordScreen.setTitle("Recording stoped");
//			recordScreen.append(recorded.length	+ " bytes captured. Play them?");
//			recordScreen.removeCommand(stopRec);
//
//
//			recordScreen.addCommand(playRec);
//			recordScreen.addCommand(decodeCmd);
			

		} catch (IOException e) {
			e.printStackTrace();
			Alert alert = new Alert("error", e.getClass().toString() + ": " + e.getMessage(), null, AlertType.ERROR);
			display.setCurrent(alert);
		} catch (MediaException e) {
			e.printStackTrace();
			Alert alert = new Alert("error", e.getClass().toString() + ": " + e.getMessage(), null, AlertType.ERROR);
			display.setCurrent(alert);
		}
		return recorded;
	}
	
	
	/**
	 * Starts recording the audio sequence. Then the MIDlet waits for the user to press the STOP button.
	 */
	private void recordAudio() {
		try {
			//change the screen to let the user know it's recording
//			recordScreen = new Form("Recording");
//			recordScreen.append("Recording. When done press stop");
//
//			stopRec = new Command("stop", Command.STOP, 1);
//
//			recordScreen.addCommand(stopRec);
//			recordScreen.addCommand(new Command("Back", Command.BACK, 1));
//			recordScreen.setCommandListener(this);
//			display.setCurrent(recordScreen);

			// Create a Player that captures live audio.
			//captureAudioPlayer = Manager.createPlayer("capture://audio?encoding=pcm&rate=44100&bits=8&channels=1&endian=little&signed=true");
			//cannot use channel 1
			captureAudioPlayer = Manager.createPlayer("capture://audio?encoding=pcm&rate=44100");
			captureAudioPlayer.realize();
			// Get the RecordControl, set the record stream,
			// start the Player and record until stop
			rc = (RecordControl) captureAudioPlayer.getControl("RecordControl");
			//this is where data is stored
			output = new ByteArrayOutputStream();
			rc.setRecordStream(output);
			rc.startRecord();
			captureAudioPlayer.start();

		} catch (IOException e) {
			Alert alert = new Alert("error", e.getClass().toString() + ": "	+ e.getMessage(), null, AlertType.ERROR);
			display.setCurrent(alert);
		} catch (MediaException e) {
			Alert alert = new Alert("error", e.getClass().toString() + ": "	+ e.getMessage(), null, AlertType.ERROR);
			display.setCurrent(alert);
		} 
	}

	public void handleAudioDecodedText(byte[] retrieved) {
		messageHandler.handleOOBMessage(AUDIO_CHANNEL, retrieved);
		
	}

	//@Override
	public void setOOBMessageHandler(OOBMessageHandler messageHandler) {
		this.messageHandler = messageHandler;
		
	}

	/**
	 * Encodes the message in audio format and plays it.
	 */
	public void transmit(byte[] message) {
		try {
			byte[]encodedtoSend = AudioUtils.encodeFileToWav(new ByteArrayInputStream(message));
			
			Alert play = new Alert ("Encoded", "Play?", null, AlertType.CONFIRMATION);
			play.setTimeout(Alert.FOREVER);
			display.setCurrent(play);
			//press play when to start singing ?? 
			Player player = Manager.createPlayer(new ByteArrayInputStream(encodedtoSend), "encoding=pcm&rate=44100&bits=8&channels=1&endian=little&signed=true");
			   // get volume control for player and set volume to max
			VolumeControl  vc = (VolumeControl) player.getControl("VolumeControl");
			   if(vc != null) {
				   logger.info("volume level: "+vc.getLevel());
			      vc.setLevel(mainProgram.volume);
			   }
			player.prefetch(); 
			player.realize(); 
			player.start();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (MediaException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

//	@Override
	public void capture() {
		//start the recording process
		
		//when it's done, we'll call the handler and pass on the message
		recordAudio();
		
	}

}

class DecoderThread extends Thread{
	byte[] audiodata;
	ByteArrayOutputStream dataStream;
	J2MEAudioChannel channel;
	public DecoderThread(byte[] audiodata, ByteArrayOutputStream dataStream, J2MEAudioChannel channel) {
		this.audiodata = audiodata;
		this.dataStream = dataStream;
		this.channel = channel;	
		
		
	}
	public byte retrieved [];
	boolean done = false;
	public void run(){
		try {
			long start = System.currentTimeMillis();
			AudioUtils.decodeWavFile(audiodata, dataStream);
			
			long end = System.currentTimeMillis();
			 retrieved  = dataStream.toByteArray();
			LogService.info(this, "decoded data size: "+ retrieved.length+". \n");

			LogService.info(this, "decoded data: " + new String(retrieved)+"\n");
			LogService.info(this, "decoding took: " + (end - start) + " ms.\n");
			channel.handleAudioDecodedText(retrieved);
			done = true;
		} catch (IOException e) {
			LogService.error(this, "Decoding error", e); 
		}
	}
}
