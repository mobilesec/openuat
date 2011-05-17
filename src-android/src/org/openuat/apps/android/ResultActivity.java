/* Copyright Michael Schöllhammer
 * Extended/cleaned up by Rene Mayrhofer
 * File created 2010-05
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.apps.android;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ImageView;

/**
 * Activity that shows the success or failure of the authentication by traffic light
 * @author Michael Schöllhammer
 *
 */
public class ResultActivity extends Activity {

	private ImageView trafficLight;
	private boolean isConnected;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.result_screen);
		
		trafficLight = (ImageView) findViewById(R.id.ImageView01);
		
		if (isConnected == true) {
			trafficLight.setImageResource(R.drawable.tl_green);
		} else {
			trafficLight.setImageResource(R.drawable.tl_red);
		}
	}

	public void setIsConnected(boolean isConnected) {
		this.isConnected = isConnected;
	}
}
