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

import org.openuat.sensors.VectorSamplesSink;
import org.openuat.sensors.android.AndroidAccelerometerSource;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.TextView;

/**
 * simply shows the accelerator values from the AndroidSampleSource
 * 
 * @author Michael Schöllhammer
 */
public class SampleSinkAndroid extends Activity implements VectorSamplesSink {
	private double[] curValues = new double[3];
	private TextView x, y, z;
	private AndroidAccelerometerSource sampleSourceAndroid;

	/**
	 * Called when the activity is first created.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.sample_sink_android);

		x = (TextView) findViewById(R.id.TextView01);
		y = (TextView) findViewById(R.id.TextView02);
		z = (TextView) findViewById(R.id.TextView03);

		sampleSourceAndroid = new AndroidAccelerometerSource(this);
		sampleSourceAndroid.addSink(this);

		Log.i(this.getClass().toString(), "onCreate");
	}

	@Override
	protected void onDestroy() {
		Log.i(this.getClass().toString(), "onDestroy");
		sampleSourceAndroid.finalize();
		super.onDestroy();
	}

	/**
	 * sets the sample
	 */
	@Override
	public void addSample(double[] sample, int index) {
		Log.i(this.getClass().toString(), "addSample");
		curValues = sample;
		updateTextHandler.sendEmptyMessage(0);
	}

	@Override
	public void segmentStart(int index) {
	}

	@Override
	public void segmentEnd(int index) {
	}

	/**
	 * needed because the handleMessage is called in the UI thread, otherwise
	 * the textviews wouldn't be updated
	 */
	private Handler updateTextHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			x.setText(String.valueOf(curValues[0]));
			y.setText(String.valueOf(curValues[1]));
			z.setText(String.valueOf(curValues[2]));
		}
	};
}
