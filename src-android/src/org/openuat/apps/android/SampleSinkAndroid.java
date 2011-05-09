/**
 * 
 */
package org.openuat.apps.android;

import java.util.Vector;

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
 * @author Michael
 */
public class SampleSinkAndroid extends Activity implements VectorSamplesSink {
	private double[] curValues = new double[3];
	private Vector<double[]> samples;
	private TextView x, y, z;
	private AndroidAccelerometerSource sampleSourceAndroid;

	/**
	 * Called when the activity is first created.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.sample_sink_android);

		samples = new Vector<double[]>();

		x = (TextView) findViewById(R.id.TextView01);
		y = (TextView) findViewById(R.id.TextView02);
		z = (TextView) findViewById(R.id.TextView03);

		sampleSourceAndroid = new AndroidAccelerometerSource(3, 0, this);
		sampleSourceAndroid.addSink(this);

		Log.i(this.getClass().toString(), "onCreate");
	}

	@Override
	protected void onStart() {
		// TODO Auto-generated method stub
		super.onStart();

		Log.i(this.getClass().toString(), "onStart");
	}

	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();

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

	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
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
		// TODO Auto-generated method stub

	}

	@Override
	public void segmentEnd(int index) {
		// TODO Auto-generated method stub

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
