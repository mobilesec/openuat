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

import android.content.Context;
import android.hardware.Sensor;

/** This class is used to get the accelerometer values from the sensor and
 * delivers them to the toolkit
 * 
 * @author Michael Schöllhammer, Rene Mayrhofer
 */
public class AndroidAccelerometerSource extends AndroidSensorSource {
	/**
	 * Constructor
	 * 
	 * @param androidContext
	 *            needed to get an instance of the sensor manager
	 */
	public AndroidAccelerometerSource(Context androidContext) {
		super(androidContext, Sensor.TYPE_ACCELEROMETER);
	}
}
