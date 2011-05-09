package org.openuat.apps.android;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ImageView;

/**
 * Activity that shows the success or failure of the authentication by traffic light
 * @author Michael
 *
 */
public class ResultActivity extends Activity {

	private ImageView trafficLight;
	private boolean isConnected;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		setContentView(R.layout.result_screen);
		
		trafficLight = (ImageView) findViewById(R.id.ImageView01);
		
		if (isConnected == true) {
			trafficLight.setImageResource(R.drawable.tl_green);
		} else {
			trafficLight.setImageResource(R.drawable.tl_red);
		}
	}

	@Override
	protected void onPause() {
		// TODO Auto-generated method stub
		super.onPause();
	}

	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
	}

	@Override
	protected void onStart() {
		// TODO Auto-generated method stub
		super.onStart();
	}

	@Override
	protected void onStop() {
		// TODO Auto-generated method stub
		super.onStop();
	}

	public void setIsConnected(boolean isConnected) {
		this.isConnected = isConnected;
	}

}
