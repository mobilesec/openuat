package org.openuat.apps;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.Scanner;
import java.util.logging.Logger;

import javax.bluetooth.UUID;
import javax.microedition.lcdui.Alert;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;

import org.apache.commons.codec.binary.Hex;
import org.apache.log4j.Level;
import org.codec.audio.AudioUtils;
import org.codec.audio.PlayerPiano;
import org.codec.audio.WavPlayer;
import org.codec.mad.MadLib;
import org.openuat.authentication.exceptions.InternalApplicationException;
import org.openuat.util.BluetoothRFCOMMChannel;
import org.openuat.util.BluetoothRFCOMMServer;
import org.openuat.util.BluetoothSupport;
import org.openuat.util.Hash;
import org.openuat.util.LineReaderWriter;
import org.openuat.util.RemoteConnection;

import com.swetake.util.QRCanvas;
import com.swetake.util.QRcodeGen;

public class OpenUATtoolkit {
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


	//#if cfg.includeTestCode 
	///////////////////////////////////////// test code begins here //////////////////////
	protected static class TempHandler implements 
	org.openuat.authentication.AuthenticationProgressHandler,
	org.openuat.authentication.KeyManager.VerificationHandler {
		private boolean performMirrorAttack, requestSensorStream;

		TempHandler(boolean attack, boolean requestStream) {
			this.performMirrorAttack = attack;
			this.requestSensorStream = requestStream;
		}

		public void AuthenticationFailure(Object sender, Object remote, Exception e, String msg) {
			System.out.println("DH with " + remote + " failed: " + e + "/" + msg);
			System.exit(1);
		}

		public void AuthenticationProgress(Object sender, Object remote, int cur, int max, String msg) {
			System.out.println("DH with " + remote + " progress: " + cur + "/" + max + ": " + msg);
		}

		public boolean AuthenticationStarted(Object sender, Object remote) {
			System.out.println("DH with " + remote + " started");
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
				line = LineReaderWriter.readLine(connectionToRemote.getInputStream(), 100000);
			} catch (IOException e) {
//				logger.debug("Unable to open stream to remote: " + e);
				e.printStackTrace();
			}
			return line;

		}
		public void AuthenticationSuccess(Object sender, Object remote, Object result) {
			System.out.println("DH with " + remote + " SUCCESS");

			Object[] res = (Object[]) result;
			// remember the secret key shared with the other device
			byte[] sharedKey = (byte[]) res[0];
			// and extract the shared authentication key for phase 2
			byte[] authKey = (byte[]) res[1];
			System.out.println("Shared session key is now '" + new String(Hex.encodeHex(sharedKey)) + 
					"' with length " + sharedKey.length + 
					", shared authentication key is now '" + new String(Hex.encodeHex(authKey)) + 
					"' with length " + authKey.length);
			// then extraxt the optional parameter
			//String param = (String) res[2];

			RemoteConnection connectionToRemote = (RemoteConnection) res[3];
			System.out.println("reading line");
			String verify = readLine(connectionToRemote);
			System.out.println(verify);
			if (verify.equals(VERIFY)){
				String method = readLine(connectionToRemote);
				System.out.println("method: " + method);
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
						


//						System.out.println("Please choose the desired verification method: \n\t[1] visual \n\t[2] audio \n\t[3] slowcodec  \n\t[4] madlib");
//						Scanner sc = new Scanner(System.in);
//						int option = sc.nextInt();
//						if(option == 1){

						byte [] trimmedHash = new byte[7];
						System.arraycopy(hash, 0, trimmedHash, 0, 7);
						String toSend = new String(Hex.encodeHex(trimmedHash));
						System.out.println("hash: -------" + toSend);

						JFrame qrcode = generateAndShowQRCode(toSend.getBytes());

						String success = readLine(connectionToRemote);

						if(success.equals(SUCCESS)){
							qrcode.setVisible(false);
							qrcode.dispose();

							System.out.println("success!!");
						}
					}
				}else if (method.equals(AUDIO)){
					method = AUDIO;
					send (ACK, connectionToRemote);
//					audio codec encode sound and play
					try {
					byte [] trimmedHash = new byte[8];
					System.arraycopy(hash, 0, trimmedHash, 0, 8);
					String toSend = new String(Hex.encodeHex(trimmedHash));
					System.out.println("hash: -------" + toSend);

					byte [] sound = AudioUtils.encodeFileToWav(new ByteArrayInputStream(toSend.getBytes()));
					String start = readLine(connectionToRemote);
					if(start.equals(START)){
						System.out.println("Playing");
						WavPlayer.PlayWav(new ByteArrayInputStream(sound));
						System.out.println("done");
						send(DONE, connectionToRemote);
					}
					
					} catch (Exception ioex) {
					ioex.printStackTrace();
					}

//				send(VERIFY);
//				send(VISUAL);
//				
//				String response = readLine();
//				//System.out.println("response "+response);
//				if (response.equals(ACK)){
//					send (START);
//					getInputViaVisualChannel();
//				}
				}
			}
		}

//		byte[] hash;
//		try {
//		hash = Hash.doubleSHA256(authKey, false);


//		System.out.println("Please choose the desired verification method: \n\t[1] visual \n\t[2] audio \n\t[3] slowcodec  \n\t[4] madlib");
//		Scanner sc = new Scanner(System.in);
//		int option = sc.nextInt();
//		if(option == 1){

//		byte [] trimmedHash = new byte[8];
//		System.arraycopy(hash, 0, trimmedHash, 0, 8);
//		String toSend = new String(Hex.encodeHex(trimmedHash));
//		System.out.println("hash: -------" + toSend);

//		generateAndShowQRCode(toSend.getBytes());
//		}else if (option == 2){
//		//audio codec encode sound and play
//		try {
//		byte [] trimmedHash = new byte[8];
//		System.arraycopy(hash, 0, trimmedHash, 0, 8);
//		String toSend = new String(Hex.encodeHex(trimmedHash));
//		System.out.println("hash: -------" + toSend);

//		byte [] sound = AudioUtils.encodeFileToWav(new ByteArrayInputStream(toSend.getBytes()));
//		WavPlayer.PlayWav(new ByteArrayInputStream(sound));
//		} catch (Exception ioex) {
//		ioex.printStackTrace();
//		}
//		}
//		else if (option == 3){
//		try {
//		String score = PlayerPiano.MakeInput(hash);
//		PlayerPiano.PlayerPiano(score);
//		} catch (Exception ioex) {
//		ioex.printStackTrace();
//		}
//		}else if (option == 4){
//		MadLib madLib = new MadLib();
//		try {
//		String text = madLib.GenerateMadLib(hash, 0, 5);
//		System.out.println(text);
//		} catch (UnsupportedEncodingException e) {
//		// TODO Auto-generated catch block
//		e.printStackTrace();
//		}
//		}


//		} catch (InternalApplicationException e) {
//		// TODO Auto-generated catch block
//		e.printStackTrace();
//		}

//		RemoteConnection connectionToRemote = (RemoteConnection) res[3];
//		if (connectionToRemote != null) {
//		System.out.println("Connection to remote is still open, mirroring on it");
//		inVerificationPhase(connectionToRemote, true);
//		}
//		}


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

	//put this in a visual channel class
	private static JFrame generateAndShowQRCode(byte [] content) {
		JFrame frame = new JFrame("Key hash");
		JPanel panel = new JPanel();
		
		int version = 0;
		
		if (content.length <15){
			version = 1;
		}else if (content.length <27){
			version = 2;
		}else if (content.length <43){
			version = 3;
		}else{
			System.out.println("message length is too big - qr code generator");
		}

		QRcodeGen x=new QRcodeGen();
		x.setQrcodeErrorCorrect('M');
		x.setQrcodeEncodeMode('B');
		x.setQrcodeVersion(version);

		System.out.println("hash length: "+content.length);

		if (content.length>0 && content.length <120){
			boolean[][] s = x.calQrcode(content);

			QRCanvas canvas =  new QRCanvas(s);
			canvas.setSize(1000, 1000);
			canvas.setBackground(java.awt.Color.white);

			panel.add(canvas);
			frame.setContentPane(panel);
			canvas.repaint();
		}
		panel.repaint();
		frame.setSize(1000, 1000);
		frame.setVisible(true);
		
		return frame;
	}


	public static void main(String[] args) throws IOException, NumberFormatException, InterruptedException {
		if (! BluetoothSupport.init()) {
			System.out.println("Could not initialize Bluetooth API");
			return;
		}

		try {
			BluetoothRFCOMMServer rfcommServer = new BluetoothRFCOMMServer(null, new UUID("447d8ecbefea4b2d93107ced5d1bba7e", false), "Test Service", 
					10000, true, false);
			rfcommServer.addAuthenticationProgressHandler(new TempHandler(false, false));
			rfcommServer.start();
			System.out.println("Finished starting SDP service at " + rfcommServer.getRegisteredServiceURL());
		} catch (IOException e) {
			System.out.println("Error initializing BlutoothRFCOMMServer: " + e);
			e.printStackTrace();
		}

//		BluetoothRFCOMMChannel c;


//		if (args[0].equals("URL"))
//		c = new BluetoothRFCOMMChannel(args[1]);
//		else
//		c = new BluetoothRFCOMMChannel(args[0], Integer.parseInt(args[1]));
//		c.open();



//		if (args.length > 2 && args[2].equals("DH")) {
//		boolean attack = false, requestStream = false;
//		if (args.length > 3 && args[3].equals("mirror"))
//		attack = true;
//		if (args.length > 3 && args[3].equals("stream"))
//		requestStream = true;
//		// this is our test client, keep connected, and use JSSE (interoperability tests...)
//		org.openuat.authentication.HostProtocolHandler.startAuthenticationWith(
//		c, new TempHandler(attack, requestStream), 20000, true, null, true);
//		System.out.println("Waiting for protocol to run in the background");
//		while (true) Thread.sleep(500);
//		}
//		else {
//		InputStream i = c.getInputStream();
//		if (args.length > 2 && args[2].equals("stream")) {
//		OutputStream o = c.getOutputStream();
//		OutputStreamWriter ow = new OutputStreamWriter(o);
//		ow.write("DEBG_Stream\n");
//		ow.flush();
//		}

//		int tmp = i.read();
//		while (tmp != -1) {
//		System.out.print((char) tmp);
//		tmp = i.read();
//		}
//		}


	}
}
