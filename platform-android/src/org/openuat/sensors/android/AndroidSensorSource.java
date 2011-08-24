/* Copyright Michael Schöllhammer
 * Extended/cleaned up by Rene Mayrhofer
 * File created 2010-05
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.sensors.android;

import org.openuat.sensors.SamplesSource;
import org.openuat.sensors.TimeSeries_Int;
import org.openuat.sensors.TimeSeries.Parameters;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.util.Log;

/** This class is used to get the sensor values from the sensor and
 * delivers them to the toolkit
 * 
 * @author Michael Schöllhammer, Rene Mayrhofer
 */
public class AndroidSensorSource extends SamplesSource implements SensorEventListener {
	private SensorManager sensorManager;
	private Sensor accSensor;
	private double[] accSample;

	/**
	 * Constructor
	 * 
	 * @param androidContext
	 *            needed to get an instance of the sensor manager
	 * @param androidSensorType
	 * 			  the type of sensor, either Sensor.TYPE_GYROSCOPE,
	 * 			  Sensor.TYPE_MAGNETIC_FIELD, or Sensor.TYPE_ACCELEROMETER
	 */
	public AndroidSensorSource(Context androidContext, int androidSensorType) {
		super(3, 0);
		sensorManager = (SensorManager) androidContext
				.getSystemService(android.content.Context.SENSOR_SERVICE);
		accSensor = sensorManager.getDefaultSensor(androidSensorType);

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
		return null;
	}


	@Override
	public org.openuat.sensors.TimeSeries_Int.Parameters getParameters_Int() {
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
		emitSample(accSample);
		return true;
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
	}

	/**
	 * called when new acceleration values are available
	 */
	@Override
	public void onSensorChanged(SensorEvent event) {
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
			Log.e(AndroidAccelerometerSource.class.toString(), "Unable to destroy object", e);
		}
	}
}
