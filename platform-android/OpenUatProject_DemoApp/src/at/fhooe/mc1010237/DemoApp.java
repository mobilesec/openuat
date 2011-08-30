package at.fhooe.mc1010237;

import java.util.ArrayList;
import java.util.Date;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import at.fhooe.mc.test.IRemoteService;

public class DemoApp extends Activity implements ServiceConnection {
	
	private ListView chatConversation;
	public ArrayAdapter<String> conversationArrayAdapter;
	private Button sendMessage;
	private Button saveSettings;
	private EditText msgOut;
	private EditText chatname;
	private IRemoteService mIRemoteService;
	protected String userName = "Default";
	public ArrayList<String> arrayList;
	private boolean run = true;
	public static final String PREFS_NAME = "MyPrefsFile";
	
	public Thread listenerThread = new Thread() {
		@Override
		public void run() {
			String s=null;
			while(run){
				try {
					s = mIRemoteService.receive();
				} catch (RemoteException e) {
					Log.e("log", "error: "+e.getMessage());
					e.printStackTrace();
				}
				if(s==null){
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						Log.e("log", "error: "+e.getMessage());
						e.printStackTrace();
					}
					continue;
				}
				//TODO set data into listview
				arrayList.add(s);
				Log.d("log", "message: "+s);
			}
		}
	};

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
		SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
		userName = settings.getString("name", "default");
//        conversationArrayAdapter = new ArrayAdapter<String>(this, R.layout.message);
        arrayList = new ArrayList<String>();
        conversationArrayAdapter = new ArrayAdapter<String>(this,R.layout.message,arrayList);
        chatConversation = (ListView)findViewById(R.id.in);
        chatConversation.setAdapter(conversationArrayAdapter);
        chatConversation.setClickable(false);
        sendMessage = (Button)findViewById(R.id.send);
        sendMessage.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				msgOut = (EditText) findViewById(R.id.message_out);
				Date date = new Date();
				//String which is containing date, name of the user and the message of the edittext
                String message = "(" + date.getHours() + ":" + date.getMinutes() + ") " + userName + ": " +msgOut.getText().toString();
				//deletes the text in the edittext after pressing the send-button to be able to enter a new text
                msgOut.setText("");
				// this is only to test, if the message is built correctly. 
//                conversationArrayAdapter.add(message);
                arrayList.add(message);
				//hides the keyboard after pressing send
                InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(msgOut.getWindowToken(), 0);
                //call send-method
                try {
					mIRemoteService.send(message);
				} catch (RemoteException e) {
					Log.e("log", "error: "+e.getMessage());
					e.printStackTrace();
				}
			}
        });
        
        Intent i = new Intent("at.fhooe.mc.WifiService");
        i.putExtra("username", userName);
	    boolean ret = bindService(i, this, Context.BIND_AUTO_CREATE);
	    Log.d("log", "initService() bound with " + ret);
	    
//	    listenerThread.start();
	    
    }
    
	protected void onSettingspage() {
		setContentView(R.layout.settings);
		chatname = (EditText) findViewById(R.id.enterName);
		saveSettings = (Button) findViewById(R.id.saveButton);
		saveSettings.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				userName = chatname.getText().toString();
				
				restartMain();
			}

		});
	}
	
	protected void restartMain(){
		setContentView(R.layout.main);
		chatConversation = (ListView) findViewById(R.id.in);
		chatConversation.setAdapter(conversationArrayAdapter);
		chatConversation.setClickable(false);
		sendMessage = (Button) findViewById(R.id.send);
		sendMessage.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
//				msgOut = (EditText) findViewById(R.id.message_out);
				Date date = new Date();
				// String which is containing date, name of the user and the
				// message of the edittext
				String message = "(" + date.getHours() + ":"
						+ date.getMinutes() + ") " + userName + ": "
						+ msgOut.getText().toString();
				// deletes the text in the edittext after pressing the
				// send-button to be able to enter a new text
				msgOut.setText("");
				// this is only to test, if the message is built correctly.
				// conversationArrayAdapter.add(message);
				arrayList.add(message);
				// hides the keyboard after pressing send
				InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
				imm.hideSoftInputFromWindow(msgOut.getWindowToken(), 0);
				// call send-method
				try {
					mIRemoteService.send(message);
				} catch (RemoteException e) {
					Log.e("log", "error: " + e.getMessage());
					e.printStackTrace();
				}
			}
		});
	}
	
	@Override
	protected void onStop(){
		super.onStop();
		SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
		SharedPreferences.Editor editor = settings.edit();
		editor.putString("name", userName);
		editor.commit();
	}
	
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }
    
    @Override
    protected void onDestroy() {
    	run=false;
    	super.onDestroy();
    }
    
    @Override
    protected void finalize() throws Throwable {
    	run=false;
    	super.finalize();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.option_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
//        Intent serverIntent = null;
        switch (item.getItemId()) {
        case R.id.settings:
//            serverIntent = new Intent(this, pref.class);
//            startActivityForResult(serverIntent,0);
        	Log.d(this.getClass().getName(),"MENU WORKS!!!!!!!!");
			onSettingspage();
            return true;
        }
        return false;
    }

    /**
     * only creates dummy messages for the chat view
     */
    public void testChat(){
    	for(int i = 0; i < 25; i++){
	    	conversationArrayAdapter.add("Me: "+i);
    	}
    }

	@Override
	public void onServiceConnected(ComponentName name, IBinder service) {
        // Following the example above for an AIDL interface,
        // this gets an instance of the IRemoteInterface, which we can use to call on the service
        mIRemoteService = IRemoteService.Stub.asInterface(service);
        listenerThread.start();
	}

	@Override
	public void onServiceDisconnected(ComponentName name) {
        Log.e("log", "Service has unexpectedly disconnected");
        mIRemoteService = null;
	}
	
	
	
}

