package org.openuat.apps.android;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import org.openuat.authentication.AuthenticationProgressHandler;
import org.openuat.authentication.HostProtocolHandler;
import org.openuat.authentication.exceptions.InternalApplicationException;
import org.openuat.channel.main.MessageListener;
import org.openuat.channel.main.ip.RemoteTCPConnection;
import org.openuat.channel.main.ip.TCPPortServer;
import org.openuat.channel.main.ip.UDPMulticastSocket;
import org.openuat.sensors.TimeSeriesAggregator;
import org.openuat.sensors.android.AndroidAccelerometerSource;

import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

/**
 * This Service class establishes a connection to a other device with the same service.
 * The key will be generated with the accelerometer sensor an checks if the other device is the right one.
 * 
 * The AIDL- File provides the interface between Service and Client application.
 * 
 * @author Gerald Schoiber, Erich Zach
 *
 */
public class WifiService extends Service implements MessageListener, AuthenticationProgressHandler {
	protected static final int UDP_PORT = 6969;
	protected static final int TCP_PORT = 6968;
	private UDPMulticastSocket udpMultiSock = null;
	private RemoteTCPConnection remoteTCPConnection = null;
	private Socket sock = null;
	private TCPPortServer tcpPortServer = null;
	private SensorManager sensorManager = null;
	private boolean firstResponse = false;
	private InputStream input = null;
	private OutputStream output = null;
	
	private AndroidAccelerometerSource reader;
	private TimeSeriesAggregator aggregator;
	public static ShakeAuthenticator protocol;
	
	/**
	 * Binds the Service with the client application over the AIDL- File.
	 */
	private final IRemoteService.Stub mBinder = new IRemoteService.Stub() {
		/**
		 * Here comes the output from the client application and will be send to the other Device.
		 * 
		 * @param data indeed the String witch is to send.
		 */
		@Override
		public boolean send(String data) throws RemoteException {
			if(input!=null && output!=null){
				int l = data.length();
				try {
					output.write((l+'*'+data).getBytes());
					output.flush();
					Log.d("log", "send data was ok");
					return true;
				} catch (IOException e) {
					Log.e("log", "Error: " + e.getMessage());
					e.printStackTrace();
				}
			}
			Log.d("log", "send data failed");
			return false;
		}

		/**
		 * Receives data from the input stream and convert it to a string which is given back to the method.
		 * This method is blocking until new data arrived.
		 * 
		 * @return receive string
		 */
		@Override
		public String receive() throws RemoteException {
			// TODO here the code to read from IO stream and send it to client
			if(input!=null && output!=null){
				Log.d("log", "on receive");
				//block until data is incoming
				StringBuffer string = new StringBuffer();
				char x = 0;
				do{
					try {
						x = (char) input.read();
						string.append(x);
					} catch (IOException e) {
						Log.e("log", "Error: " + e.getMessage());
						e.printStackTrace();
					}
				}while(x!='*');
				string.deleteCharAt(string.length()-1);
				Log.d("log", "length: "+string.toString());
				int length = Integer.valueOf(string.toString());
				string = new StringBuffer();
				//loop to get data packet
				for(int n=0;n<length;n++){
					try {
						x = (char) input.read();
						string.append(x);
					} catch (IOException e) {
						Log.e("log", "Error: " + e.getMessage());
						e.printStackTrace();
					}
				}
				Log.d("log", "data: "+string.toString());
				//return string
				return string.toString();
			}
			return null;
		}
	};

	/**
	 * Is called when Service first is created.
	 */
	@Override
		public void onCreate() {
			super.onCreate();
			Log.d("log", "on create");
		}
	
	/**
	 * Will be called if the client application binds the service on the AIDL- File.
	 * Starts a UDP multi sock server and send out a broadcast to get identified from other devices who supports this service.
	 * A TCP server also will be start and some initialization will set.
	 * 
	 * @param intent
	 * @return AIDL interface
	 */
	@Override
	public IBinder onBind(Intent intent) {
		// start TCPPortServer so we can Listen to incoming authentications
		tcpPortServer = new TCPPortServer(TCP_PORT, 60000, true, false);
		tcpPortServer.addAuthenticationProgressHandler(this);
		try {
			tcpPortServer.start();
		} catch (IOException e) {
			Log.e("log", "Error: " + e.getMessage());
			e.printStackTrace();
		}	
		protocol = new ShakeAuthenticator(tcpPortServer);
		initAuthenPara();
		
		// start listening on udp port
		try {
			udpMultiSock = new UDPMulticastSocket(UDP_PORT, UDP_PORT, "255.255.255.255");
			udpMultiSock.sendMulticast(("someone else who can shake?").getBytes());
		} catch (IOException e) {
			Log.e("log", "Error: " + e.getMessage());
			e.printStackTrace();
		}
		
		// set MessageListener on UDPMulticastSocket
		udpMultiSock.addIncomingMessageListener(this);
		udpMultiSock.startListening();
		Log.d("log", "UDPMultiSock start to listen");

		// bind AIDL
		Log.d("log", "on bind");
		return mBinder;
	}
	
	/**
	 * Initializes some variables to handle with the data from the accelerometer and starts it.
	 */
	private void initAuthenPara(){
		Log.d("log","StartInitAuthen");

		try {
			protocol.startListening();
		} catch (IOException e) {
			e.printStackTrace();
		}
		Log.d("log","SensorManager finished");
		reader = new AndroidAccelerometerSource(getApplicationContext());
		Log.d("log","SensorManager finished2");
		Log.i("log", "initialised reader");

		aggregator = new TimeSeriesAggregator(3, 50 / 2, 50 * 4, 50 * 4);
		Log.i("log", "initialised aggregator");

		aggregator.setParameters(reader.getParameters_Int());
		Log.i("log", "set parameters");

		aggregator.setActiveVarianceThreshold(7500 * 4);
		Log.i("log", "set ActiveVarianceThreshold");

		reader.addSink(new int[] { 0, 1, 2 }, aggregator.getInitialSinks());
		Log.i("log", "add sink");

		aggregator.addNextStageSegmentsSink(protocol);
		Log.i("log", "add NextStageSegmentsSink");

		reader.start();
	}
	
	/**
	 * Called when the service starts first.
	 * 
	 * @param intent, startId
	 */
	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);
	}
	
	/**
	 * Called when the service gets disconnectet with the client application.
	 * 
	 * @param intent
	 * @return boolean
	 */
	@Override
	public boolean onUnbind(Intent intent) {
		return super.onUnbind(intent);
	}
	
	/**
	 * Called when the service ends.
	 */
	@Override
	public void onDestroy() {
		super.onDestroy();
		//stop listening and remove listener
		Log.d("log", "stop listening on UDP port and remove message listener");
		udpMultiSock.stopListening();
		udpMultiSock.removeIncomingMessageListener(this);
		udpMultiSock.dispose();
		try {
			if(sock!=null)sock.close();
			if(tcpPortServer!=null)tcpPortServer.stop();
		} catch (InternalApplicationException e) {
			Log.e("log", "Error: " + e.getMessage());
			e.printStackTrace();
		} catch (IOException e) {
			Log.e("log", "IO Exception: " + e.getMessage());
			e.printStackTrace();
		}
		tcpPortServer.removeAuthenticationProgressHandler(this);
	}
	
	/**
	 * Called when the service gets destroyed by various reasons.
	 * 
	 * @throws Throwable
	 */
	@Override
	protected void finalize() throws Throwable {
		onDestroy();
		super.finalize();
	}

	/**
	 * Receives UDP packages from udpMultiSock Listener and start authentication with the other device over TCP connection.
	 * 
	 */
	@Override
	public void handleMessage(byte[] message, int offset, int length, Object sender) {
		StringBuffer b = new StringBuffer();
		for(int n=0;n<27;n++)b.append((char)message[n]);
		Log.d("log",'*'+b.toString()+'*');
		if("someone else who can shake?".equalsIgnoreCase(b.toString())){
			if(!firstResponse){
				//ignore first response- cause its the own
				firstResponse=true;
				return;
			}
			Log.d("log", "new device found for shake");
			
			//TODO add new device in ListActivity
			InetAddress address = (InetAddress)sender;
			Log.d("log","InetAdress");
			
			Log.d("log","initAuthenfinished");
			
			//TODO tcp verbindung mit device herstellen
			//start tcp remote connection and try to connect to other device
		  	
			try {
				sock = new Socket(address, TCP_PORT);
			} catch (UnknownHostException e) {
				Log.e("log", "UnknownHost Exception: " + e.getMessage());
				e.printStackTrace();
				//TODO toast
				return;
			} catch (IOException e) {
				Log.e("log", "IO Exception: " + e.getMessage());
				e.printStackTrace();
				//TODO toast
				return;
			} 
			remoteTCPConnection = new RemoteTCPConnection(sock);
			
			try {
				remoteTCPConnection.open();
				HostProtocolHandler.startAuthenticationWith(remoteTCPConnection, this, 10000, true, null, true);
				Log.d("log","start authentication with device");
			} 
			catch (IOException e) {
				Log.e("log", "IO Exception: " + e.getMessage());
			}
		}
	}

	/**
	 * Raised from AuthenticationHandler when authentication was successfully.
	 * 
	 * @param sender, remote, result as object
	 */
	@Override
	public void AuthenticationSuccess(Object sender, Object remote, Object result) {
		// TODO Auto-generated method stub
		Log.d("log", "authentication succeeded");
		try {
			input = remoteTCPConnection.getInputStream();
			output= remoteTCPConnection.getOutputStream();
		} catch (IOException e) {
			Log.e("log", "IO Exception: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Raised from AuthenticationHandler when authentication failed.
	 * 
	 * @param sender, remote as object
	 * @param expression
	 * @param message as String
	 */
	@Override
	public void AuthenticationFailure(Object sender, Object remote, Exception e, String msg) {
		// TODO Auto-generated method stub
		Log.d("log", "authentication failure");
	}

	/**
	 * Raised from AuthenticationHandler when authentication is in progress.
	 * 
	 * @param sender, remote as object
	 * @param cur, max as integer
	 * @param message as String
	 */
	@Override
	public void AuthenticationProgress(Object sender, Object remote, int cur, int max, String msg) {
		// TODO Auto-generated method stub
		Log.d("log", "authentication in progress");
	}

	/**
	 * Raised from AuthenticationHandler when authentication starts.
	 * 
	 * @param sender, remote as object
	 * @return boolean
	 */
	@Override
	public boolean AuthenticationStarted(Object sender, Object remote) {
		// TODO Auto-generated method stub
		Log.d("log", "authentication started");
		return true;
	}

}