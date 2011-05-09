/**
 * 
 */
package org.openuat.sensors.android;

import org.openuat.sensors.SamplesSource;
import org.openuat.sensors.TimeSeries_Int;
import org.openuat.sensors.TimeSeries.Parameters;

import android.app.Activity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;

/**
 * this class is used to get the acceleration values from the sensor and
 * delivers them to the toolkit
 * 
 * @author Michael
 * 
 */
public class AndroidAccelerometerSource extends SamplesSource implements
		SensorEventListener {

	private SensorManager sensorManager;
	private Sensor accSensor;
	private double[] accSample;
	private Activity activity;

	/**
	 * Constructor
	 * 
	 * @param maxNumLines
	 *            The maximum number of data lines to read from the device -
	 *            depends on the sensor.
	 * @param sleepBetweenReads
	 *            The number of milliseconds to sleep between two reads
	 * @param activity
	 *            needed to get an instance of the sensor manager
	 */
	public AndroidAccelerometerSource(int maxNumLines, int sleepBetweenReads,
			Activity activity) {
		super(maxNumLines, sleepBetweenReads);
		// TODO Auto-generated constructor stub
		this.activity = activity;
		sensorManager = (SensorManager) activity
				.getSystemService(android.content.Context.SENSOR_SERVICE);
		accSensor = (Sensor) sensorManager.getSensorList(
				Sensor.TYPE_ACCELEROMETER).get(0);

		Handler handler = new Handler();
		sensorManager.registerListener(this, accSensor,
				SensorManager.SENSOR_DELAY_FASTEST, handler);
		accSample = new double[3];
	}

	/**
	 * Each time samples source must be able to provide the appropriate
	 * parameters for normalizing its values to the [-1;1] range.
	 */
	@Override
	public Parameters getParameters() {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public org.openuat.sensors.TimeSeries_Int.Parameters getParameters_Int() {
		// TODO Auto-generated method stub
		return new TimeSeries_Int.Parameters() {
			public int getMultiplicator() {
				return 2;
			}

			public int getDivisor() {
				return 1;
			}

			public int getOffset() {
				return 0;
			}
		};
	}


	/**
	 * called when a new sample is available
	 */
	@Override
	public boolean handleSample() {
		// TODO Auto-generated method stub

		emitSample(accSample);
		return true;
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// TODO Auto-generated method stub

	}

	/**
	 * called when new acceleration values are available
	 */
	@Override
	public void onSensorChanged(SensorEvent event) {
		// TODO Auto-generated method stub
		if (event.sensor == accSensor) {
			for (int i = 0; i < event.values.length; i++) {
				accSample[i] = event.values[i];
			}
			handleSample();
		}
	}

	@Override
	public void finalize() {
		sensorManager.unregisterListener(this);
		try {
			super.finalize();
		} catch (Throwable e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

}
