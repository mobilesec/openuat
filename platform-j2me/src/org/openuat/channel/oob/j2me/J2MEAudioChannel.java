/* Copyright Iulia Ion
 * File created 2008-08-01
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
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
import javax.microedition.media.Manager;
import javax.microedition.media.MediaException;
import javax.microedition.media.Player;
import javax.microedition.media.control.RecordControl;
import javax.microedition.media.control.VolumeControl;

import java.util.logging.Logger;
import org.codec.audio.j2me.AudioUtils;
import org.openbandy.service.LogService;
import org.openuat.authentication.OOBChannel;
import org.openuat.authentication.OOBMessageHandler;

/**
 * Implements the audio out of band channel for mobile devices.
 * @author Iulia Ion
 *
 */
public class J2MEAudioChannel implements OOBChannel, CommandListener {
	
	private OOBMessageHandler messageHandler;
	private Logger logger = Logger.getLogger("org.openuat.channel.oob.j2me.J2MEAudioChannel");

	//UI components
	private Display display;
	private Displayable homeScreen;
	
	/** how loud to play a tune */
	private int volume;

	private Command stopRec;
	private Command playRec = new Command("Play", Command.SCREEN, 1);
	private Command decodeCmd = new Command("Decode", Command.SCREEN, 1);

	//recording 
	private Player captureAudioPlayer;
	private RecordControl rc;
	private ByteArrayOutputStream output;

	/** The last recorded sequence */
	private byte [] recorded = null ;

	//put only the display
	public J2MEAudioChannel(Display display, Displayable homeScreen, int volume) {
		this.display = display;
		this.homeScreen = homeScreen;
		this.volume = volume;
	}
	
	public void commandAction(Command com, Displayable dis) {
		if(com.equals(stopRec)){
			finishCapturing();
		}
		else if(com.equals(playRec)){
			playRecording();
		}else if(com.equals(decodeCmd)){
			decodeAudio(recorded);
		}
		else if(com.getCommandType() == Command.BACK){
			display.setCurrent(homeScreen);
		}

	}
	
	/** 
	 * Used to repay the recorded sound. Only used for debugging purposes.
	 */
	private void playRecording() {
		ByteArrayInputStream recordedStream = new ByteArrayInputStream(recorded);
		Player player;
		try {
			player = Manager.createPlayer(recordedStream, "audio/x-wav");
			player.prefetch(); 
			player.realize();
			player.start(); 
		} catch (IOException e) {
			Alert alert = new Alert("error", e.getClass().toString() + ": " + e.getMessage(), null, AlertType.ERROR);
			display.setCurrent(alert);
		} catch (MediaException e) {
			Alert alert = new Alert("error", e.getClass().toString() + ": " + e.getMessage(), null, AlertType.ERROR);
			display.setCurrent(alert);
		}
		captureAudioPlayer.close();
	}
	
	/**
	 * Starts the thread that decodes the recorded hapadep sound. 
	 * @param audiodata
	 */
	public void  decodeAudio(byte [] audiodata) {
		ByteArrayOutputStream dataStream = new ByteArrayOutputStream();
		
		DecoderThread decoder = new DecoderThread(audiodata, dataStream, this);
		decoder.start();
}
	
	/**
	 * Stop the microphone and the recording.
	 * @return the audio sound recorded.
	 */
	public byte [] finishCapturing() {
		try {
			//stop recording
			rc.commit();
			rc.stopRecord();
			captureAudioPlayer.stop();
			captureAudioPlayer.close();

			recorded = output.toByteArray();
			output.close();

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

	/**
	 * Called by the DecoderThread when decoding of the recorded sound finished. 
	 * @param retrieved The decoded content.
	 */
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
			      vc.setLevel(volume);
			   }
			player.prefetch(); 
			player.realize(); 
			player.start();
		} catch (IOException e) {
			Alert alert = new Alert("error", e.getClass().toString() + ": "	+ e.getMessage(), null, AlertType.ERROR);
			display.setCurrent(alert);
		} catch (MediaException e) {
			Alert alert = new Alert("error", e.getClass().toString() + ": "	+ e.getMessage(), null, AlertType.ERROR);
			display.setCurrent(alert);
		}

	}

//	@Override
	public void capture() {
		//start the recording process		
		//when it's done, we'll call the handler and pass on the message
		recordAudio();
		
	}

}

/**
 * Decodes a recorded audio sound in a separate thread, not to block the application.
 * @author Iulia Ion
 *
 */
class DecoderThread extends Thread{
	/** the bytes to be decoded */
	private byte[] audiodata;
	private ByteArrayOutputStream dataStream;
	private J2MEAudioChannel channel;
	
	/** The decoded bytes */
	public byte retrieved [];
	
	public DecoderThread(byte[] audiodata, ByteArrayOutputStream dataStream, J2MEAudioChannel channel) {
		this.audiodata = audiodata;
		this.dataStream = dataStream;
		this.channel = channel;	
		
		
	}
	
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
		} catch (IOException e) {
			LogService.error(this, "Decoding error", e); 
		}
	}
}
