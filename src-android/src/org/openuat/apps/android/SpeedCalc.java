/* Copyright Martin Kuttner
 * File created 2011-01
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.apps.android;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

/**
 * This Class manages the reading of the Sensor-Data, 
 * uses the ability of the Class "SocketHandler" to pass its data on to a remote Server 
 * and has its data read by the Class "DebugOutput" to provide some On-the-Fly Output 
 * to the Developer.
 * @author Martin Kuttner
 * 
 * TODO: this class should be removed and implemented via AndroidSensorSource and
 * the TimeSeries classes in OpenUAT
 */
public class SpeedCalc implements SensorEventListener
{
	/** The Class that handles the Debugging Output of our Gyro-Data */
	public 	DebugOutput 	m_output	= null;
	/** The Class that Handles the Socket Connection to the Server */
	private SocketHandler 	m_socket	= new SocketHandler();
	private SensorManager 	m_man 		= null;
	/** The Creator of this Class-Instance. (needed to re-enable the button) */
	private ReadSensor 		m_caller 	= null;
	/** The most recent Data of the Gyroscope. */
	public 	float[] 		m_speed_data= { 0, 0, 0 };
	/** The most recent Data of the Accelerometer. */
	public 	float[] 		m_acc_data	= { 0, 0, 0 };
	/** The most recent Data of the Compass. */
	public 	float[] 		m_mag_data	= { 0, 0, 0 };
	
	private double[] 		m_speed_X 	= new double[Globals.n_values];
	private double[] 		m_speed_Y 	= new double[Globals.n_values];
	private double[] 		m_speed_Z 	= new double[Globals.n_values];
	
	private double[] 		m_acc_X 	= new double[Globals.n_values];
	private double[] 		m_acc_Y 	= new double[Globals.n_values];
	private double[] 		m_acc_Z 	= new double[Globals.n_values];
	
	private double[] 		m_mag_X 	= new double[Globals.n_values/2];
	private double[] 		m_mag_Y 	= new double[Globals.n_values/2];
	private double[] 		m_mag_Z 	= new double[Globals.n_values/2];
	
	/** The amount of gyro values that have been saved so far. */
	private int 			m_iG		= 0;
	/** The amount of magnetic values that have been saved so far. */
	private int 			m_iM		= 0;
	/** The amount of accelerometer values that have been saved so far. */
	private int 			m_iA		= 0;
	/** Whether at least one set of measurements has been done. */
	private boolean			m_done		= false;
	/** Whether the process of reading data is still to be done. */
	private boolean 		m_onG 		= false;
	/** Whether the process of reading data is still to be done. */
	private boolean 		m_onA 		= true;
	/** Whether the process of reading data is still to be done. */
	private boolean 		m_onM 		= true;
	/** Whether we are sending at the moment, so no one else tries to send. */
	private boolean			m_sending	= false;


	/**
	 * Initializes the connection to the Gyroscope.
	 * @param _c The Context needed to access the SensorManager and therefore the Sensor.
	 */
	public SpeedCalc(Context _c)
	{
		m_man = (SensorManager) _c.getSystemService(Context.SENSOR_SERVICE);
		Sensor gyr_sensor = m_man.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
		Sensor mag_sensor = m_man.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        Sensor acc_sensor = m_man.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		m_caller = (ReadSensor) _c;
		
		int i = 0; //just in case no sensor is available, this variable will tell us

		if (m_man.registerListener(this, gyr_sensor, Globals.delay))
		{
			Log.d(Globals.name, "Gyroscope detected, and successfully connected.");
			if (Globals.debug)
			{
				m_output = new DebugOutput(this);
			}
		} else
		{
			Log.d(Globals.name, "No gyroscope found.");
			i++;
		}
		
		if (m_man.registerListener(this, mag_sensor, Globals.delay))
		{
			Log.d(Globals.name, "Compass detected, and successfully connected.");
		} else
		{
			Log.d(Globals.name, "No compass found.");
			i++;
		}
		
		if (m_man.registerListener(this, acc_sensor, Globals.delay))
		{
			Log.d(Globals.name, "Accelerometer detected, and successfully connected.");
		} else
		{
			Log.d(Globals.name, "No accelerometer found.");
			i++;
		}
		if (i == 3)
		{
			m_done = true;
		}
	}

	public void onAccuracyChanged(Sensor _s, int _accuracy){}

	public void onSensorChanged(SensorEvent _e)
	{
		final float[] vals = _e.values;
		
		// Start operations when there's a first major movement of the device.
		if (m_speed_data[0] > Globals.peak || 
			m_speed_data[1] > Globals.peak || 
			m_speed_data[2] > Globals.peak)
		{
			m_onG = true;
		}

		if (_e.sensor.getType() == Sensor.TYPE_GYROSCOPE)
		{
			System.arraycopy(vals, 0, m_speed_data, 0, 3);
			
			if (m_onG) //Read Gyro data until the Array is full.
			{
				if (m_iG < Globals.n_values)
				{
					m_speed_X[m_iG] = (double) m_speed_data[0];
					m_speed_Y[m_iG] = (double) m_speed_data[1];
					m_speed_Z[m_iG] = (double) m_speed_data[2];
					m_iG++;
				}
			}
		}
		
		if (_e.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
		{
			System.arraycopy(vals, 0, m_acc_data, 0, 3);

			if (m_onA) //Read Accelerometer data until the Array is full.
			{
				if (m_iA < Globals.n_values)
				{
					m_acc_X[m_iA] = (double) m_acc_data[0];
					m_acc_Y[m_iA] = (double) m_acc_data[1];
					m_acc_Z[m_iA] = (double) m_acc_data[2];
					m_iA++;
				}
			}
		}
		
		if (_e.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
		{
			System.arraycopy(vals, 0, m_mag_data, 0, 3);

			if (m_onM) //Read Magnetic data until the Array is full.
			{
				if (m_iM < Globals.n_values / 2)
				{
					m_mag_X[m_iM] = (double) m_mag_data[0];
					m_mag_Y[m_iM] = (double) m_mag_data[1];
					m_mag_Z[m_iM] = (double) m_mag_data[2];
					m_iM++;
				}
			}
		}
		
		//Send Data to the Server
		if (m_iG == Globals.n_values && !m_sending) 
		{
			Log.d(Globals.name,"----------Sending Speed Data");
			m_sending = true;
			m_iG = 0;
			m_done = true;
			m_socket.sendSpeedData(m_speed_X, m_speed_Y, m_speed_Z);
			m_onG = false;
			m_sending = false;
		}
		
		if (m_iA == Globals.n_values && !m_sending)
		{
			Log.d(Globals.name,"----------Sending Tilt Data");
			m_sending = true;
			m_iA = 0;
			m_done = true;
			m_socket.sendTiltData(m_acc_X, m_acc_Y, m_acc_Z);
			m_onA = false;
			m_sending = false;
		}
		
		if (m_iM == Globals.n_values/2 && !m_sending)
		{
			Log.d(Globals.name,"----------Sending Mag Data");
			m_sending = true;
			m_iM = 0;
			m_done = true;
			m_socket.sendMagnetData(m_mag_X, m_mag_Y, m_mag_Z);
			m_onM = false;
			m_sending = false;
		}
		
		if ((m_iG == 0) && (m_iA == 0) && (m_iM == 0) && m_done)
		{
			Log.d(Globals.name,"Closing Socket");
			m_caller.findViewById(R.id.btnStart).setEnabled(true);
			m_socket.close();
			m_onG = false;
			m_onA = false;
			m_onM = false;
			m_man.unregisterListener(this);
		}
	}

}
