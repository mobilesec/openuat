/* Copyright Martin Kuttner
 * File created 2011-01
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.apps.android;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import android.util.Log;

public class SocketHandler 
{
	/** The Socket that's listening for connections */
	private Socket socket = null;
	private PrintWriter out = null;
	private BufferedReader in = null;
	
	SocketHandler()
	{
		try
		{
		    socket = new Socket(Globals.server_addr, Globals.server_port);
			try 
			{
				out = new PrintWriter(socket.getOutputStream(), true);
				try 
			    {
					in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
					Log.d(Globals.name, "Successful Socket Connection");
				} catch (IOException e) 
				{
					Log.e(Globals.name,"Can't get Input Stream.");
				}
			} catch (IOException e) 
			{
				Log.e(Globals.name,"Can't get Output Stream.");
			}
		} catch (UnknownHostException e) 
		{
			Log.e(Globals.name,"Unknown host.");
		} catch  (IOException e) 
		{
			Log.e(Globals.name,"Can't establish Connection.");
		}	    
	}
	
	/** 
	 * Closes the Socket Connection and does some cleaning up. 
	 */
	public void close()
	{
		try 
		{
			in.close(); 
			out.close();
			socket.close();
		} 
		catch (IOException e) {}
	}
	
	/**
	 * Sends the Speed Data to the Server
	 * @param _x X-Axis Array
	 * @param _y Y-Axis Array
	 * @param _z Z-Axis Array
	 */
	public void sendSpeedData(double[] _x, double[] _y, double[] _z)
	{
		//Line the Arrays up into 3 Strings
		String x = new String("");
		for (int i = 0; i<_x.length-1;i++)
		{
			x+=_x[i]+Globals.separator;
		}
		x+=_x[_x.length-1];
		
		String y = new String("");
		for (int i = 0; i<_y.length-1;i++)
		{
			y+=_y[i]+Globals.separator;
		}
		y+=_y[_y.length-1];
		
		String z = new String("");
		for (int i = 0; i<_z.length-1;i++)
		{
			z+=_z[i]+Globals.separator;
		}
		z+=_z[_z.length-1];
		
		//Send the data over the Socket
		if (!socket.isClosed())
		{
			out.println("SpeedDataInit");
			out.println(x);
			out.println(y);
			out.println(z);
			
			//Check if the data has been received correctly
			try 
			{
				String cx = in.readLine();
				String cy = in.readLine();
				String cz = in.readLine();
				if (cx!=null && cy!=null && cz!=null && cx.equals(x) && cy.equals(y) && cz.equals(z))
				{
					Log.d(Globals.name,"SUCCESSFUL SPEED DATA TRANSFER");
				}
			} catch (IOException e) 
			{
				Log.e(Globals.name,"Messages not successfully sent back.");
			}
		}
	}
	
	/**
	 * Sends the Tilt Data to the Server
	 * @param _x X-Axis Array
	 * @param _y Y-Axis Array
	 * @param _z Z-Axis Array
	 */
	public void sendTiltData(double[] _x, double[] _y, double[] _z)
	{
		//Line the Arrays up into 3 Strings
		String x = new String("");
		for (int i = 0; i<_x.length-1;i++)
		{
			x+=_x[i]+Globals.separator;
		}
		x+=_x[_x.length-1];
		
		String y = new String("");
		for (int i = 0; i<_y.length-1;i++)
		{
			y+=_y[i]+Globals.separator;
		}
		y+=_y[_y.length-1];
		
		String z = new String("");
		for (int i = 0; i<_z.length-1;i++)
		{
			z+=_z[i]+Globals.separator;
		}
		z+=_z[_z.length-1];
		
		//Send the data over the Socket
		if (!socket.isClosed())
		{
			out.println("TiltDataInit");
			out.println(x);
			out.println(y);
			out.println(z);
			
			//Check if the data has been received correctly
			try 
			{
				String cx = in.readLine();
				String cy = in.readLine();
				String cz = in.readLine();
				if (cx!=null && cy!=null && cz!=null && cx.equals(x) && cy.equals(y) && cz.equals(z))
				{
					Log.d(Globals.name,"SUCCESSFUL TILT DATA TRANSFER");
				}
			} catch (IOException e) 
			{
				Log.e(Globals.name,"Messages not successfully sent back.");
			}
		}
	}
	
	/**
	 * Sends the Compass Data to the Server
	 * @param _x X-Axis Array
	 * @param _y Y-Axis Array
	 * @param _z Z-Axis Array
	 */
	public void sendMagnetData(double[] _x, double[] _y, double[] _z)
	{
		//Line the Arrays up into 3 Strings
		String x = new String("");
		for (int i = 0; i<_x.length-1;i++)
		{
			x+=_x[i]+Globals.separator;
		}
		x+=_x[_x.length-1];
		
		String y = new String("");
		for (int i = 0; i<_y.length-1;i++)
		{
			y+=_y[i]+Globals.separator;
		}
		y+=_y[_y.length-1];
		
		String z = new String("");
		for (int i = 0; i<_z.length-1;i++)
		{
			z+=_z[i]+Globals.separator;
		}
		z+=_z[_z.length-1];
		
		//Send the data over the Socket
		if (!socket.isClosed())
		{
			out.println("MagnetDataInit");
			out.println(x);
			out.println(y);
			out.println(z);
			
			//Check if the data has been received correctly
			try 
			{
				String cx = in.readLine();
				String cy = in.readLine();
				String cz = in.readLine();
				if (cx!=null && cy!=null && cz!=null && cx.equals(x) && cy.equals(y) && cz.equals(z))
				{
					Log.d(Globals.name,"SUCCESSFUL MAGNET DATA TRANSFER");
				}
			} catch (IOException e) 
			{
				Log.e(Globals.name,"Messages not successfully sent back.");
			}
		}
	}
}
