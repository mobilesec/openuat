/* Copyright Martin Kuttner
 * File created 2011-01
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.apps.android;

import android.hardware.SensorManager;

public class Globals
{
	/** Name (Tag) of our Application for Debug Output */
	public static final String 	name		= "Androscope";
	/** Enable Debug Output of the SensorValues */
	public static final boolean debug 		= true;
	/** How many Values are read before they get transmitted. */
	public static final int 	n_values	= 512;
	/** How the Values are separated for transmission on the Socket. */
	public static final String	separator 	= ",";
	/** At which Value we start reading data. */
	public static final float	peak		= 50.0F;
	/** How fast the Sensor gives us the next set of data. */
	public static final int 	delay 		= SensorManager.SENSOR_DELAY_FASTEST;
	/** IP Address of the Socket-Server */
	public static 		String 	server_addr = "10.21.99.32";
	/** Port for the Socket Connection */
	public static final int 	server_port = 4444;
}
