/* Copyright Martin Kuttner
 * File created 2011-01
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.apps.android;

import android.app.Activity;
import android.util.DisplayMetrics;
import android.view.View.OnClickListener;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class ReadSensor extends Activity
{
	public static ReadSensor 	m_instance = null;

	@Override
	/**
	 * Main Entry-Point of our Application.
	 */
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.scope);
		m_instance = this;
		
		//Set Text in EditText
		EditText ip_addr = (EditText) this.findViewById(R.id.edIP);
		ip_addr.setText(Globals.server_addr);

		//Set StartButton Width and ClickListener
		DisplayMetrics metrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(metrics);
		Button startButton = (Button) this.findViewById(R.id.btnStart);
		startButton.setWidth(metrics.widthPixels);
		startButton.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				//Set Server Address from what's in the EditText
				EditText et = (EditText)findViewById(R.id.edIP);
				Globals.server_addr = et.getText().toString();
				//Starting Point
				new SpeedCalc(m_instance);
				m_instance.findViewById(R.id.btnStart).setEnabled(false);
			}
		});

		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
	}
}
