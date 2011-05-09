package org.openuat.apps.android;

import java.io.IOException;

import org.openuat.authentication.AuthenticationProgressHandler;
import org.openuat.authentication.HostProtocolHandler;
import org.openuat.channel.main.bluetooth.android.AndroidRFCOMMChannel;
import org.openuat.channel.main.bluetooth.android.AndroidRFCOMMServer;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

/**
 * Starting Point of the Application. contains GUI, provides near Bluetooth
 * devices, starts an AndroidRFCOMMServer and AndroidRFCOMMChannel
 * 
 * @author Michael Schöllhammer
 * 
 */
public class StartActivity extends Activity implements OnClickListener,
		AuthenticationProgressHandler {

	private TextView headline;
	private ImageView image;

	private BluetoothAdapter adapter;

	private BroadcastReceiver Receiver;
	private ArrayAdapter<String> arrayAdapter;
	private OnItemClickListener deviceClickListener;
	private ListView listView;

	private AndroidRFCOMMServer server;
	private AndroidRFCOMMChannel firstChannel;

	/**
	 * Called when the activity is first created.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.start_screen);

		Log.i(this.getClass().toString(), "onCreate");

		Button startButton = (Button) findViewById(R.id.Button01);
		startButton.setOnClickListener(this);

		Button scanButton = (Button) findViewById(R.id.Button02);
		scanButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				doDiscovery();
			}
		});

		headline = (TextView) findViewById(R.id.TextView01);

		image = (ImageView) findViewById(R.id.ImageView01);

		adapter = BluetoothAdapter.getDefaultAdapter();

		initDeviceClickListener();

		arrayAdapter = new ArrayAdapter<String>(this, R.layout.device_name);
		listView = (ListView) findViewById(R.id.ListView01);
		listView.setAdapter(arrayAdapter);
		listView.setOnItemClickListener(deviceClickListener);

		listView.setVisibility(ListView.VISIBLE);

		initBTDeviceReceiver();

		startRFCOMMServer();
	}

	@Override
	protected void onStart() {
		// TODO Auto-generated method stub
		super.onStart();
		Log.i(this.getClass().toString(), "onStart");

		if (adapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
			turnOnBTandDiscoverable();
		}
	}

	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();

		arrayAdapter.clear();
		doDiscovery();

		Log.i(this.getClass().toString(), "onResume");

	}

	@Override
	protected void onPause() {
		// TODO Auto-generated method stub
		super.onPause();

		Log.i(this.getClass().toString(), "onPause");
	}

	@Override
	protected void onStop() {
		super.onStop();

		Log.i(this.getClass().toString(), "onStop");
	}

	/**
	 * closes AndroidRFCOMMServer and AndroidRFCOMMChannel
	 */
	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub

		unregisterReceiver(Receiver);
		if (server != null || firstChannel != null) {
			server.dispose();
			firstChannel.close();
		}
		Log.i(this.getClass().toString(), "onDestroy");

		super.onDestroy();
	}

	/**
	 * opens SampleSinkAndroid-activity on button click
	 * 
	 * @param v
	 *            view which was clicked
	 */
	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub
		int id = v.getId();

		if (id == R.id.Button01) {
			Intent intent = new Intent(this, SampleSinkAndroid.class);
			startActivity(intent);
		}
	}

	/**
	 * clicklistener for the listView to get the device, which the user wants to
	 * connect to
	 */
	private void initDeviceClickListener() {
		deviceClickListener = new OnItemClickListener() {
			public void onItemClick(AdapterView<?> av, View v, int arg2,
					long arg3) {

				Log.i(this.getClass().toString(), "onItemClick");

				// Cancel discovery because it's costly and we're about to
				// connect
				adapter.cancelDiscovery();

				// Get the device MAC address, which is the last 17 chars in the
				// View
				String info = ((TextView) v).getText().toString();
				String address = info.substring(info.length() - 17);

				startAuthentication(address);
			}
		};
	}

	/**
	 * start discovering bluetooth devices
	 */
	private void doDiscovery() {
		if (adapter.isDiscovering()) {
			adapter.cancelDiscovery();
		}

		adapter.startDiscovery();
	}

	/**
	 * creates intent and calls startActivityForResult which results in a
	 * dialog, asking the user whether he wants to turn on bluetooth and set
	 * discoverability to 300 minutes.
	 */
	private void turnOnBTandDiscoverable() {
		Intent discoverableIntent = new Intent(
				BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
		discoverableIntent.putExtra(
				BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
		startActivityForResult(discoverableIntent, 1);
	}

	/**
	 * call-back method which is called by the dialog activity
	 * 
	 * @param requestCode
	 *            request code originally supplied to startActivityForResult(),
	 *            allowing to identify who this result came from.
	 * @param resultCode
	 *            result code returned by the child activity through its
	 *            setResult().
	 * @param data
	 *            An Intent, which can return result data to the caller (various
	 *            data can be attached to Intent "extras").
	 */
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == Activity.RESULT_CANCELED) {
			turnOnBTandDiscoverable();
		}
	}

	/**
	 * creates a Broadcastreceiver which listens for found Bluetooth devices
	 */
	private void initBTDeviceReceiver() {
		Receiver = new BroadcastReceiver() {
			public void onReceive(Context context, Intent intent) {
				Log.i(this.getClass().toString(), "onReceive");
				String action = intent.getAction();
				// When discovery finds a device
				if (BluetoothDevice.ACTION_FOUND.equals(action)) {
					// Get the BluetoothDevice object from the Intent
					BluetoothDevice device = intent
							.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

					// Add the name and address to an array adapter to show in a
					// ListView
					if (!isAlreadyDiscovered(device.getName() + "\n"
							+ device.getAddress())) {
						arrayAdapter.add(device.getName() + "\n"
								+ device.getAddress());
					}
				}
			}
		};
		// Register the BroadcastReceiver
		IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
		registerReceiver(Receiver, filter);
	}

	/**
	 * method to avoid two equal entries in the list
	 * 
	 * @param item
	 *            String representation of the device
	 * @return returns true if device is already in the list, false otherwise
	 */
	private boolean isAlreadyDiscovered(String item) {
		for (int i = 0; i < arrayAdapter.getCount(); i++) {
			if (arrayAdapter.getItem(i).contentEquals(item)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * starts an AndroidRFCOMMChannel based on the selected device
	 * 
	 * @param address
	 *            Mac address of the selected device
	 */
	private void startAuthentication(String address) {
		BluetoothDevice device = adapter.getRemoteDevice(address);

		Log.i(this.getClass().toString(), device.getName());
		firstChannel = new AndroidRFCOMMChannel(device);

		try {
			firstChannel.open();
			// This method starts a new thread that tries to authenticate with
			// the host given as remote
			HostProtocolHandler.startAuthenticationWith(firstChannel, this,
					30000, true, null, false);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * start AndroidRFCOMMServer
	 */
	private void startRFCOMMServer() {
		server = new AndroidRFCOMMServer(adapter,
				AndroidRFCOMMServer.serviceUUID, "SWBU", -1, true, false);
		server.addAuthenticationProgressHandler(this);
		try {
			server.start();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/**
	 * called when Authentication fails
	 */
	@Override
	public void AuthenticationFailure(Object sender, Object remote,
			Exception e, String msg) {
		// TODO Auto-generated method stub
		Log.i(this.getClass().toString(), "AuthenticationFailure");
	}

	@Override
	public void AuthenticationProgress(Object sender, Object remote, int cur,
			int max, String msg) {
		// TODO Auto-generated method stub
		Log.i(this.getClass().toString(), "AuthenticationProgress");
	}

	/**
	 * called when Authentication was started
	 */
	@Override
	public boolean AuthenticationStarted(Object sender, Object remote) {
		// TODO Auto-generated method stub
		Log.i(this.getClass().toString(), "AuthenticationStarted");
		return false;
	}

	/**
	 * called when Authentication is successful
	 */
	@Override
	public void AuthenticationSuccess(Object sender, Object remote,
			Object result) {
		// TODO Auto-generated method stub
		Log.i(this.getClass().toString(), "AuthenticationSuccess");
	}
}
