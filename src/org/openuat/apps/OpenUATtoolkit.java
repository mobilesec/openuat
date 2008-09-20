package org.openuat.apps;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;

import javax.bluetooth.UUID;

import org.apache.commons.codec.binary.Hex;
import org.apache.log4j.Logger;
import org.codec.audio.AudioUtils;
import org.codec.audio.PlayerPiano;
import org.codec.audio.WavPlayer;
import org.codec.mad.MadLib;
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

	private static Logger logger = Logger.getLogger("org.openuat.apps.OpenUATtoolkit");

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
						VisualChannel channel = new VisualChannel();
						channel.transmit(toSend.getBytes());

						String success = readLine(connectionToRemote);

						if(success.equals(SUCCESS)){

							System.out.println("success!!");
						}else {
							System.out.println("failure!!");
						}
						channel.close();
					}
				}else if (method.equals(AUDIO)){
					method = AUDIO;
					send (ACK, connectionToRemote);
					byte [] trimmedHash = new byte[AUDIO_KEY_LENGTH];
					
					try {
						hash = Hash.doubleSHA256(authKey, false);
						System.arraycopy(hash, 0, trimmedHash, 0, AUDIO_KEY_LENGTH);
						byte[] toSend = new String(Hex.encodeHex(trimmedHash)).getBytes();
						//we add some padding after the hash
						byte [] padded = new byte[toSend.length + AUDIO_PADDING];
						System.arraycopy(toSend, 0, padded, 0, toSend.length);
					System.out.println("hash: -------" + toSend);

					byte [] sound = AudioUtils.encodeFileToWav(new ByteArrayInputStream(padded));
					String start = readLine(connectionToRemote);
					if(start.equals(START)){
						System.out.println("Playing");
						WavPlayer.PlayWav(new ByteArrayInputStream(sound));
						System.out.println("done");
						send(DONE, connectionToRemote);
						String done = readLine(connectionToRemote);
						if(done.equals(SUCCESS)){
							System.out.println("Success!!");
						}else{
							System.out.println(done);
						}
					}
					
					} catch (Exception ioex) {
					ioex.printStackTrace();
					}

				}else if (method.equals(MADLIB)){
					send (ACK, connectionToRemote);
					MadLib madLib = new MadLib();
					try {
					String text = madLib.GenerateMadLib(hash, 0, 5);
					System.out.println(text);
					} catch (UnsupportedEncodingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					}
					send (DONE, connectionToRemote);
					String done = readLine(connectionToRemote);
					if(done.equals(SUCCESS)){
						System.out.println("Success!!");
					}else{
						System.out.println(done);
					}
				}else if (method.equals(SLOWCODEC)){
					send (ACK, connectionToRemote);
					String done = readLine(connectionToRemote);
					if(done.equals(DONE))
					try{
						String score = PlayerPiano.MakeInput(hash);
						PlayerPiano.PlayerPiano(score);
					} catch (Exception ioex) {
						ioex.printStackTrace();
					}
					send (DONE, connectionToRemote);
					String answer = readLine(connectionToRemote);
					if(answer.equals(SUCCESS)){
						System.out.println("Success!!");
					}else{
						System.out.println(done);
					}
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

	


	public static void main(String[] args) throws IOException, NumberFormatException, InterruptedException {
		if (! BluetoothSupport.init()) {
			System.out.println("Could not initialize Bluetooth API");
			return;
		}

		try {
			BluetoothRFCOMMServer rfcommServer = new BluetoothRFCOMMServer(null, new UUID(0x0001), "OpenUAT- Print document", 
					-1, true, false);
			rfcommServer.addAuthenticationProgressHandler(new TempHandler(false, false));
			rfcommServer.start();
			System.out.println("Finished starting SDP service at " + rfcommServer.getRegisteredServiceURL());
		} catch (IOException e) {
			System.out.println("Error initializing BlutoothRFCOMMServer: " + e);
			e.printStackTrace();
		}


	}
}
