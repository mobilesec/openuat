/* Copyright Martin Kuttner
 * File created 2011-01
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.apps.android;

import java.util.Timer;
import java.util.TimerTask;
import android.util.Log;

public class DebugOutput
{
	Timer timer;

	public DebugOutput(SpeedCalc _calc)
	{
		timer = new Timer();
		timer.schedule(new Output(_calc), 1000, // initial delay
									1 * 1000); 	// subsequent rate
	}

	class Output extends TimerTask
	{
		SpeedCalc m_calc;

		Output(SpeedCalc _calc)
		{
			m_calc = _calc;
		}

		public void run()
		{
			String[] speed_strings = new String[3];
			if ((m_calc != null) && (m_calc.m_speed_data != null))
			{
				for (int i = 0; i < m_calc.m_speed_data.length; i++)
				{
					if (m_calc.m_speed_data[i] < 0)
					{
						speed_strings[i] = String.format("%.1f",
								m_calc.m_speed_data[i]);
						if (m_calc.m_speed_data[i] > -100)
							speed_strings[i] = " " + speed_strings[i];
						if (m_calc.m_speed_data[i] > -10)
							speed_strings[i] = " " + speed_strings[i];
					} else
					{
						speed_strings[i] = " "
								+ String.format("%.1f", m_calc.m_speed_data[i]);
						if (m_calc.m_speed_data[i] < 100)
							speed_strings[i] = " " + speed_strings[i];
						if (m_calc.m_speed_data[i] < 10)
							speed_strings[i] = " " + speed_strings[i];
					}

				}

				Log.v(Globals.name, "T:" + speed_strings[0] + ","
						+ speed_strings[1] + "," + speed_strings[2]);
			}
		}
	}
}
