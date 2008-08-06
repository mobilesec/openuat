package org.openuat.apps.j2me;


import com.swetake.util.j2me.QRcodeGen;
import com.swetake.util.j2me.QRCanvas;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.ChoiceGroup;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Item;
import javax.microedition.lcdui.ItemCommandListener;
import javax.microedition.lcdui.StringItem;
import javax.microedition.media.Manager;
import javax.microedition.media.MediaException;
import javax.microedition.media.Player;
import javax.microedition.media.control.RecordControl;
import javax.microedition.media.control.ToneControl;
import javax.microedition.midlet.MIDlet;
import javax.microedition.midlet.MIDletStateChangeException;

import org.codec.audio.j2me.AudioUtils;
import org.codec.audio.j2me.PlayerPianoJ2ME;

/**
 * Demonstrate the HAPADEP protocol on the mobile phone. This class shows tentative UI, recording and playing audio files. No cryptographic functionality yet and no actual integration with the HAPADEP implementation.
 * @author Iulia Ion
 *
 */
public class HapadepMIDlet extends MIDlet implements CommandListener, ItemCommandListener {

	//screens
	private Form mainScreen;
	private Form recordScreen;
	private Form decodeScreen;
	private Form encodeScreen;

	private Command exit;
	//private Command back;
	//screen 1
	private Command playRecCmd;
	private Command verifyCmd;
	private Command testDecodeCmd;
	private Command testEncodeCmd;
	private Command testQRCmd;
	//screen 2
	private Command stopRec;
	private Command playRec = new Command("Play", Command.SCREEN, 1);
	 Command decodeCmd = new Command("Decode", Command.SCREEN, 1);
	private Command encodeCmd;

	/** The type of device: server or client */
	private ChoiceGroup client_server;
	/** The type of authentication: unilateral, mutual or session-to-session */
	private ChoiceGroup auth;

	private String[] mode = new String[] { "Unilateral", "Bilateral", "STS" };
	private String[] type = new String[] { "Client", "Server" };


	private Player captureAudioPlayer;
	private RecordControl rc;
	private ByteArrayOutputStream output;

	/** The last recorded sequence */
	 byte [] recorded = null ;


	public HapadepMIDlet() {
		// TODO Auto-generated constructor stub
	}

	protected void destroyApp(boolean arg0) throws MIDletStateChangeException {
		// TODO Auto-generated method stub

	}

	protected void pauseApp() {
		// TODO Auto-generated method stub

	}

	/**
	 * Creates the initial UI components.
	 */
	protected void startApp() throws MIDletStateChangeException {
		
		initGUI();

	}

	private void initGUI() {
		mainScreen = new Form("HAPADEP");

//		client_server = new ChoiceGroup("Device type", ChoiceGroup.EXCLUSIVE, type,
//		null);
//		mainScreen.append(client_server);

//		auth = new ChoiceGroup("Authentication mode", ChoiceGroup.EXCLUSIVE, mode,
//		null);
//		mainScreen.append(auth);

		StringItem record = new StringItem("Record", " a sound\n", Item.BUTTON);
		StringItem verify = new StringItem("Verify", " hash file from jar\n", Item.BUTTON);
		StringItem encode = new StringItem("Encode", " key from jar file\n", Item.BUTTON);
		StringItem decode = new StringItem("Decode", " wav file from jar\n", Item.BUTTON);
		

		StringItem qrgen = new StringItem("GenerateQRCode", "", Item.BUTTON);
		
		mainScreen.append(record);
		mainScreen.append(verify);
		mainScreen.append(encode);
		mainScreen.append(decode);
		mainScreen.append(qrgen);

		playRecCmd = new Command("Record", Command.ITEM, 1);
		verifyCmd = new Command("Play", Command.ITEM, 1);
		testEncodeCmd = new Command("Encode", Command.ITEM, 1);
		testDecodeCmd = new Command("Decode", Command.ITEM, 1);
		testQRCmd = new Command("QR Gen", Command.ITEM, 1);

		record.addCommand(playRecCmd);
		verify.addCommand(verifyCmd);
		encode.addCommand(testEncodeCmd);
		decode.addCommand(testDecodeCmd);
		qrgen.addCommand(testQRCmd);
		
		record.setItemCommandListener(this);
		verify.setItemCommandListener(this);
		encode.setItemCommandListener(this);
		decode.setItemCommandListener(this);
		qrgen.setItemCommandListener(this);
		
		exit = new Command("Exit", Command.EXIT, 1);
		mainScreen.addCommand(exit);

		mainScreen.setCommandListener(this);
		Display.getDisplay(this).setCurrent(mainScreen);


	}



	public void commandAction(Command cmd, Displayable arg1) {
		if (cmd.equals(exit)) {
			try {
				this.destroyApp(true);
				this.notifyDestroyed();
			} catch (MIDletStateChangeException e) {}

		} else if (cmd.getCommandType()==Command.BACK) {
			//if(arg1.equals(recordScreen)){
			Display.getDisplay(this).setCurrent(mainScreen);
			//}
		}else if(cmd.equals(stopRec)){
			stopRecording();
		}else if(cmd.equals(playRec)){
			playRecording();
		}else if(cmd.equals(decodeCmd)){
			decode(recorded);
		}

	}

	private void stopRecording() {
		try {
			//stop recording
			rc.commit();
			rc.stopRecord();
			captureAudioPlayer.stop();
			captureAudioPlayer.close();

			recorded = output.toByteArray();
			output.close();

			//update screen
			recordScreen.deleteAll();
			recordScreen.setTitle("Recording stoped");
			recordScreen.append(recorded.length	+ " bytes captured. Play them?");
			recordScreen.removeCommand(stopRec);


			recordScreen.addCommand(playRec);
			recordScreen.addCommand(decodeCmd);

		} catch (IOException e) {
			e.printStackTrace();
			Alert alert = new Alert("error", e.getClass().toString() + ": " + e.getMessage(), null, AlertType.ERROR);
			Display.getDisplay(this).setCurrent(alert);
		} catch (MediaException e) {
			e.printStackTrace();
			Alert alert = new Alert("error", e.getClass().toString() + ": " + e.getMessage(), null, AlertType.ERROR);
			Display.getDisplay(this).setCurrent(alert);
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
			Display.getDisplay(this).setCurrent(alert);
		} catch (MediaException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			Alert alert = new Alert("error", e.getClass().toString() + ": " + e.getMessage(), null, AlertType.ERROR);
			Display.getDisplay(this).setCurrent(alert);
		}
		captureAudioPlayer.close();
	}

	private void decode(byte [] audiodata) {
		
		
			decodeScreen = new Form("decoding...\n");
			decodeScreen.setCommandListener(this);
			decodeScreen.append("initial: "+ audiodata.length + "bytes\n");
			decodeScreen.addCommand(exit);
			decodeScreen.addCommand(new Command("Back", Command.BACK, 1));
			Display.getDisplay(this).setCurrent(decodeScreen);
			
			new DecoderThread(decodeScreen, this, audiodata).start();
	}

	/**
	 * Item actions: play/record and verify
	 */
	public void commandAction(Command cmd, Item item) {
		ByteArrayOutputStream data = new ByteArrayOutputStream();
		try {
//			int type = client_server.getSelectedIndex();
//			int mode = auth.getSelectedIndex();

			if (cmd.equals(playRecCmd)) {
				recordAudio();
			} else if (cmd.equals(verifyCmd)) {
				InputStream in = this.getClass().getResourceAsStream("my_hashfile.txt");
				System.out.println();
				int a = in.available();
				byte hash [] = new byte [a];
				in.read(hash);
				
				verify(hash);
			}else if (cmd.equals(testDecodeCmd)) {
				InputStream wav = getClass().getResourceAsStream("my_wav.wav");
				int a = wav.available();
				byte [] audioData = new byte [a];
				wav.read(audioData);
				Alert alert = new Alert("decoding", ""+audioData.length, null, AlertType.ERROR);
				Display.getDisplay(this).setCurrent(alert);

				decode(audioData);
			}else if (cmd.equals(testEncodeCmd)) {
				System.out.println("encoding");
				encode();
			}else if (cmd.equals(testQRCmd)) {
				System.out.println("qrgen");
				generateQRCode("my message".getBytes());
			}
		}catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			Alert alert = new Alert("error", e.getClass().toString() + ": " + e.getMessage(), null, AlertType.ERROR);
			Display.getDisplay(this).setCurrent(alert);

		}
	}

	private void playFile(String fileName) {
		try {

			//get bytes to encode
//			encoding=pcm&rate=11025&bits=16&channels=1
//			[6:23:06 PM] Rene Mayrhofer (mobile) says: encoding=pcm&rate=44100&bits=8&channels=1
//			[6:24:14 PM] Rene Mayrhofer (mobile) says: createPlayer(stream,"encoding=pcm&rate=44100&bits=8&channels=1")
			//encode

			//new input stream of bytes, encoding
			//Player player = Manager.createPlayer(getClass().getResourceAsStream("my_wav.wav"), "encoding=pcm&rate=8000&bits=8&channels=1&endian=little&signed=true");
			Player player = Manager.createPlayer(getClass().getResourceAsStream(fileName), "audio/x-wav");
			player.prefetch(); 
			player.realize(); 
			player.start(); 

			Alert alert = new Alert("Verify", "Playing some sample wav file", null, AlertType.CONFIRMATION);
			Display.getDisplay(this).setCurrent(alert);
			try {
				Thread.sleep(3000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		} catch (IOException e) {
			e.printStackTrace();
			Alert alert = new Alert("error", e.getClass().toString() + ": "	+ e.getMessage(), null, AlertType.ERROR);
			Display.getDisplay(this).setCurrent(alert);
		} catch (MediaException e) {
			e.printStackTrace();
			Alert alert = new Alert("error", e.getClass().toString() + ": "	+ e.getMessage(), null, AlertType.ERROR);
			Display.getDisplay(this).setCurrent(alert);
		}
	}

	/**
	 * Plays a melodic sound to verify the hash.
	 */
	private void verify(byte [] hash){
		try{ 
			Player p = Manager.createPlayer(Manager.TONE_DEVICE_LOCATOR); 
			p.realize(); 
			ToneControl c = (ToneControl)p.getControl("ToneControl"); 

			String score = PlayerPianoJ2ME.MakeInput(hash);
			//c.setSequence(PlayerPiano.PlayerPiano("/2 - Gb + . - E - E + Db + Fb + Cb - Db + Fb Cb"));
			c.setSequence(PlayerPianoJ2ME.PlayerPiano(score));

			p.start(); 
		} catch (IOException ioe) { 
		} catch (MediaException me) { }

	}

	
	/**
	 * Encodes the key from the file.
	 */
	private void encode() {
		encodeScreen = new Form("encoding");
		encodeScreen.addCommand(exit);
		encodeScreen.addCommand(new Command("Back", Command.BACK, 1));
		encodeScreen.setCommandListener(this);
		Display.getDisplay(this).setCurrent(encodeScreen);
		// TODO Auto-generated method stub
//		(float) Encoder.kSamplingFrequency,
//		(int) 8, (int) 1, true, false
		//unidirectional
		try{
			byte [] tosend = null;
			ByteArrayOutputStream out = new ByteArrayOutputStream();

			byte[] bytes = new byte[20];
			byte[] hbytes = new byte[16];

			//reads the key from my_key_file,
			//appends 10 bytes of the hash

//			try {
			//FileInputStream in = new FileInputStream(my_key_file);
			InputStream in = getClass().getResourceAsStream("my_key.txt");
			System.out.println("in: "+ in);
			InputStream inhash = getClass().getResourceAsStream("my_hashfile.txt");
			System.out.println("inhash: "+ inhash);
			in.read(bytes);

			inhash.read(hbytes);

			out.write(bytes);
			out.write(hbytes, 0, 10);

			//does this get all the data?
			tosend = out.toByteArray();
			encodeScreen.append("bytes to encode: " + tosend.length+"\n");
			encodeScreen.append(new String(tosend)+"\n");
//			for (int i = 0; i < tosend.length; i++) {
//			encodeScreen.append(tosend[i]+", ");
//			}
			in.close();
			out.close();
			Display.getDisplay(this).setCurrent(encodeScreen);

			new EncoderThread(encodeScreen, this, tosend).start(); 
		} catch (IOException e) {
			e.printStackTrace();
			encodeScreen.append("error" + e.getClass().toString() + ": "	+ e.getMessage() );
			Display.getDisplay(this).setCurrent(mainScreen);
//			} catch (MediaException e) {
//			e.printStackTrace();
//			mainScreen.append("error" + e.getClass().toString() + ": "	+ e.getMessage() );
//			Display.getDisplay(this).setCurrent(mainScreen);
		}catch (Exception e) {
			e.printStackTrace();
			encodeScreen.append("error" + e.getClass().toString() + ": "	+ e.getMessage() );
			Display.getDisplay(this).setCurrent(encodeScreen);
		}
	}


	/**
	 * Starts recording the audio sequence. Then the MIDlet waits for the user to press the STOP button.
	 */
	private void recordAudio() {
		try {
			//change the screen to let the user know it's recording
			recordScreen = new Form("Recording");
			recordScreen.append("Recording. When done press stop");

			stopRec = new Command("stop", Command.STOP, 1);

			recordScreen.addCommand(stopRec);
			recordScreen.addCommand(new Command("Back", Command.BACK, 1));
			recordScreen.setCommandListener(this);
			Display.getDisplay(this).setCurrent(recordScreen);

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
			Display.getDisplay(this).setCurrent(alert);
		} catch (MediaException e) {
			Alert alert = new Alert("error", e.getClass().toString() + ": "	+ e.getMessage(), null, AlertType.ERROR);
			Display.getDisplay(this).setCurrent(alert);
		} 
	}
	
	private void generateQRCode(byte d []){
		
		
		QRcodeGen x=new QRcodeGen(this);
		x.setQrcodeErrorCorrect('M');
		x.setQrcodeEncodeMode('B');
		x.setQrcodeVersion(1);

		
		if (d.length>0 && d.length <120){
			boolean[][] s = x.calQrcode(d);
			QRCanvas canvas = new QRCanvas(s);
			Display.getDisplay (this).setCurrent ( canvas );
			canvas.repaint();

		}
	}
}

class EncoderThread extends Thread{
	Form encodeScreen;
	HapadepMIDlet midlet;
	byte data[];
	public EncoderThread(Form encodeScreen, HapadepMIDlet midlet, byte data[]) {
		this.encodeScreen = encodeScreen;
		this.midlet = midlet;
		this.data = data;
	}

	public void run(){
		try{
			long start = System.currentTimeMillis();
			byte [] encodedtoSend = AudioUtils.encodeFileToWav(new ByteArrayInputStream(data));
			long end = System.currentTimeMillis();
			//mainScreen.append("header:");
//			for (int i = 0; i < 50; i++) {
//				encodeScreen.append(encodedtoSend[i]+",");
//			}
			encodeScreen.append("encoded as :"+encodedtoSend.length+" bytes. Now playing.\n");
			encodeScreen.append("encoding took"+ (end - start)+ "ms.\n");
//			for (int i = 120044; i < 120054; i++) {
//			mainScreen.append(encodedtoSend[i]+",");
//			}


			Display.getDisplay(midlet).setCurrent(encodeScreen);

			Player player = Manager.createPlayer(new ByteArrayInputStream(encodedtoSend), "encoding=pcm&rate=44100&bits=8&channels=1&endian=little&signed=true");

			player.prefetch(); 
			player.realize(); 
			player.start();
			
			midlet.recorded = encodedtoSend;
			
			encodeScreen.append("Want to decode it?\n");
			encodeScreen.addCommand(midlet.decodeCmd);
			
		}catch (Exception e) {
			e.printStackTrace();
			encodeScreen.append("error" + e.getClass().toString() + ": "	+ e.getMessage() );
			Display.getDisplay(midlet).setCurrent(encodeScreen);
		}
	}
}
class DecoderThread extends Thread{
	Form decodeScreen;
	MIDlet midlet;
	byte data[];
	public DecoderThread(Form decodeScreen, MIDlet midlet, byte data[]) {
		this.decodeScreen = decodeScreen;
		this.midlet = midlet;
		this.data = data;
	}

	public void run(){
		try {
			ByteArrayOutputStream dataStream = new ByteArrayOutputStream();
			long start = System.currentTimeMillis();
			AudioUtils.decodeWavFile(data, dataStream);
			long end = System.currentTimeMillis();
			byte retrieved [] = dataStream.toByteArray();
			decodeScreen.deleteAll();
			decodeScreen.append("decoded data size: "+ retrieved.length+". \n");

			decodeScreen.append("decoded data: " + new String(retrieved)+"\n");
			decodeScreen.append("decoding took: " + (end - start) + " ms.\n");
//			for (int i = 0; i < retrieved.length; i++) {
//			decodeScreen.append( retrieved [i] + ", ");
//			}


			Display.getDisplay(midlet).setCurrent(decodeScreen);

			//} catch (IOException e) {
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			decodeScreen.append(e.getClass().toString() + ": " + e.getMessage());
//			Alert alert = new Alert("error", e.getClass().toString() + ": " + e.getMessage(), null, AlertType.ERROR);
//			Display.getDisplay(this).setCurrent(alert);
		} 
	}
	

}
