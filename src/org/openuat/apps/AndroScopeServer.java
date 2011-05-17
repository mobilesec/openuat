package org.openuat.apps;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

import javax.swing.JFrame;

import org.openuat.features.Coherence;

/** This Class implements a socket server which can open up multiple Socket Connections
 * by starting multiple "SocketConnection" Threads.
 * @author Martin Kuttner, Rene Mayrhofer
 */
public class AndroScopeServer extends JFrame {
	private static final long 	serialVersionUID = 3230091649879540810L;

	/** Whether log4j should pop debugging messages */
	public static final boolean	debug 		= false;
	/** How many Values are read before they get transmitted. */
	public static final int 	n_values	= 512;
	/** How many Windows should be used for coherence checking. */
	public static final int 	n_windows	= 16;
	/** How the Values are separated for transmission on the Socket. */
	public static final String	separator 	= ",";
	/** Port for the Socket Connection */
	public static final int 	server_port = 4444;
	/** Maximum number of Connections to the Server */
	public static final int		max_conn	= 16;
	
	/** The actual Socket Server */
	private ServerSocket 		server 	= null;
	
	/** Used as identification of the current part of the Arrays to be used. */
	public int 					mod_i 	= 0;
	/** Used for checking how many connections have already been established. */
	private int 				conn_i 	= 0;
	
	public double[][] 			m_spx 	= new double[max_conn][n_values];
	public double[][] 			m_spy 	= new double[max_conn][n_values];
	public double[][] 			m_spz 	= new double[max_conn][n_values];
	
	public double[][] 			m_tix 	= new double[max_conn][n_values];
	public double[][] 			m_tiy 	= new double[max_conn][n_values];
	public double[][] 			m_tiz 	= new double[max_conn][n_values];
	
	public double[][] 			m_mgx 	= new double[max_conn][n_values/2];
	public double[][] 			m_mgy 	= new double[max_conn][n_values/2];
	public double[][] 			m_mgz 	= new double[max_conn][n_values/2];

	/** Whether a set of Arrays is already done. */
	public boolean[][] 			done	= new boolean[3][max_conn];
	
	/** This class implements a Socket Connection to the Client that can be run multiple
	 * times simultaneously as Threads.
	 */
	class SocketConnection implements Runnable
	{
		/** The Socket Connection to the Client */
		private Socket 			client;
		/** The Server that started this Thread */
		@SuppressWarnings("hiding")
		private AndroScopeServer 	server;
		private BufferedReader 	in = null;
		private PrintWriter 	out = null;
		/** The Strings we receive from the Android Client */
		private String			m_sx, m_sy, m_sz;
		/** The parts of the Arrays in the Server-Class we're writing to in this Thread */
		private int 			j;
		/** Whether the Thread is to be continued */
		private boolean 		running = true;

		SocketConnection(Socket _client, AndroScopeServer _server, int _i)
		{
			this.client = _client;
			this.server = _server;
			this.j = _i;
		}

		protected void finalize()
		{
			try
			{
				in.close();
				out.close();
				client.close();
			} catch (IOException e)
			{
				System.err.println("Could not close.");
				System.exit(-1);
			}
		}

		public void run()
		{
			// Get established with the client.
			try
			{
				in = new BufferedReader(new InputStreamReader(client
						.getInputStream()));
				out = new PrintWriter(client.getOutputStream(), true);
			} catch (IOException e1)
			{
				System.out.println("Can't establish Connection");
			}

			while (running)
			{
				try
				{
					String incoming = in.readLine();
					//--------------------START SPEED DATA READING----------------------//
					if (incoming != null && incoming.equals("SpeedDataInit")) //Speed Data is about to come in.
					{
						//Receive the 3 Strings
						m_sx = in.readLine();
						m_sy = in.readLine();
						m_sz = in.readLine();
						//Send them back for verification
						out.println(m_sx);
						out.println(m_sy);
						out.println(m_sz);

						//Split up the String to an array again.
						String[] sx = m_sx.split(separator);
						for (int i = 0; i < sx.length; i++)
						{
							server.m_spx[j][i] = Double.parseDouble(sx[i]);
						}
						String[] sy = m_sy.split(separator);
						for (int i = 0; i < sy.length; i++)
						{
							server.m_spy[j][i] = Double.parseDouble(sy[i]);
						}
						String[] sz = m_sz.split(separator);
						for (int i = 0; i < sz.length; i++)
						{
							server.m_spz[j][i] = Double.parseDouble(sz[i]);
						}

						System.out.println("Received Speed Data.(" + j + ")");
						server.done[0][j] = true;
						
						try //write the data to a CSV-File
						{
							FileWriter fstream = null;
							BufferedWriter writer = null;
							
							fstream = new FileWriter("Speed"+j+"-"+"X"+".csv");
							writer = new BufferedWriter(fstream);
							writer.write(m_sx);
							writer.close();

							fstream = new FileWriter("Speed"+j+"-"+"Y"+".csv");
							writer = new BufferedWriter(fstream);
							writer.write(m_sy);
							writer.close();

							fstream = new FileWriter("Speed"+j+"-"+"Z"+".csv");
							writer = new BufferedWriter(fstream);
							writer.write(m_sz);
							writer.close();
							
						}catch (Exception e){}

						if (j % 2 == 1) // if we just added the second array of data
										// we can compare to the first
						{
							//but we might still have to wait for the first to finish
							while (!server.done[0][j - 1]){}
							
							//Calculate the Coherences for all 3 Arrays
							double[] ox = Coherence.cohere(	server.m_spx[j - 1],
															server.m_spx[j], n_values/n_windows, -1);
							double[] oy = Coherence.cohere(	server.m_spy[j - 1],
															server.m_spy[j], n_values/n_windows, -1);
							double[] oz = Coherence.cohere(	server.m_spz[j - 1],
															server.m_spz[j], n_values/n_windows, -1);
							
							/** 1D
							double[] speed_sum1 = new double[n_values];
							double[] speed_sum2 = new double[n_values];
							for (int i = 0; i < server.m_spx.length; i++)
							{
								speed_sum1[i] = server.m_spx[j][i] + server.m_spy[j][i] + server.m_spz[j][i];
								speed_sum2[i] = server.m_spx[j-1][i] + server.m_spy[j-1][i] + server.m_spz[j-1][i];
							}
							double[] o = Coherence.cohere(speed_sum1, speed_sum2, n_values/n_windows, -1);
							double o_avg = 0;
							for (int i = 0; i < o.length; i++)
							{
								o_avg += o[i];
							}
							o_avg = o_avg / o.length / 3;
							*/
							
							//Calculate the Average
							double ox_sum = 0;
							for (int i = 0; i < ox.length; i++)
							{
								ox_sum += ox[i];
							}
							ox_sum = ox_sum / ox.length;

							double oy_sum = 0;
							for (int i = 0; i < oy.length; i++)
							{
								oy_sum += oy[i];
							}
							oy_sum = oy_sum / oy.length;
							
							double oz_sum = 0;
							for (int i = 0; i < oz.length; i++)
							{
								oz_sum += oz[i];
							}
							oz_sum = oz_sum / oz.length;

							//Get the Result
							double result = (ox_sum + oy_sum + oz_sum) / 3;
							System.out.println("Speed Result: " + result);
							//System.out.println("Speed Result: " + o_avg + " 1D");
						}
					}
					//--------------------END   SPEED DATA READING----------------------//
					//--------------------START TILT  DATA READING----------------------//
					if (incoming != null && incoming.equals("TiltDataInit")) //Tilt Data is about to come in.
					{
						//Receive the 3 Strings
						m_sx = in.readLine();
						m_sy = in.readLine();
						m_sz = in.readLine();
						//Send them back for verification
						out.println(m_sx);
						out.println(m_sy);
						out.println(m_sz);

						//Split up the String to an array again.
						String[] sx = m_sx.split(separator);
						for (int i = 0; i < sx.length; i++)
						{
							server.m_tix[j][i] = Double.parseDouble(sx[i]);
						}
						String[] sy = m_sy.split(separator);
						for (int i = 0; i < sy.length; i++)
						{
							server.m_tiy[j][i] = Double.parseDouble(sy[i]);
						}
						String[] sz = m_sz.split(separator);
						for (int i = 0; i < sz.length; i++)
						{
							server.m_tiz[j][i] = Double.parseDouble(sz[i]);
						}

						System.out.println("Received Tilt Data.(" + j + ")");
						server.done[1][j] = true;
						
						try //write the data to a CSV-File
						{
							FileWriter fstream = null;
							BufferedWriter writer = null;
							
							fstream = new FileWriter("Tilt"+j+"-"+"X"+".csv");
							writer = new BufferedWriter(fstream);
							writer.write(m_sx);
							writer.close();

							fstream = new FileWriter("Tilt"+j+"-"+"Y"+".csv");
							writer = new BufferedWriter(fstream);
							writer.write(m_sy);
							writer.close();

							fstream = new FileWriter("Tilt"+j+"-"+"Z"+".csv");
							writer = new BufferedWriter(fstream);
							writer.write(m_sz);
							writer.close();
							
						}catch (Exception e){}
						
						if (j % 2 == 1) // if we just added the second array of data
							// we can compare to the first
						{
							//but we might still have to wait for the first to finish
							while (!server.done[1][j - 1]){}
							
							//Calculate the Coherences for all 3 Arrays
							double[] ox = Coherence.cohere(	server.m_tix[j - 1],
															server.m_tix[j], n_values/n_windows, -1);
							double[] oy = Coherence.cohere(	server.m_tiy[j - 1],
															server.m_tiy[j], n_values/n_windows, -1);
							double[] oz = Coherence.cohere(	server.m_tiz[j - 1],
															server.m_tiz[j], n_values/n_windows, -1);
							
							//Calculate the Average
							double ox_sum = 0;
							for (int i = 0; i < ox.length; i++)
							{
								ox_sum += ox[i];
							}
							ox_sum = ox_sum / ox.length;
				
							double oy_sum = 0;
							for (int i = 0; i < oy.length; i++)
							{
								oy_sum += oy[i];
							}
							oy_sum = oy_sum / oy.length;
							
							double oz_sum = 0;
							for (int i = 0; i < oz.length; i++)
							{
								oz_sum += oz[i];
							}
							oz_sum = oz_sum / oz.length;
				
							//Get the Result
							double result = (ox_sum + oy_sum + oz_sum) / 3;
							System.out.println("Tilt Result: " + result);
						}
						
					}
					//--------------------END   TILT  DATA READING----------------------//
					//--------------------START MAG   DATA READING----------------------//
					if (incoming != null && incoming.equals("MagnetDataInit")) //Compass Data is about to come in.
					{
						//Receive the 3 Strings
						m_sx = in.readLine();
						m_sy = in.readLine();
						m_sz = in.readLine();
						//Send them back for verification
						out.println(m_sx);
						out.println(m_sy);
						out.println(m_sz);

						//Split up the String to an array again.
						String[] sx = m_sx.split(separator);
						for (int i = 0; i < sx.length; i++)
						{
							server.m_mgx[j][i] = Double.parseDouble(sx[i]);
						}
						String[] sy = m_sy.split(separator);
						for (int i = 0; i < sy.length; i++)
						{
							server.m_mgy[j][i] = Double.parseDouble(sy[i]);
						}
						String[] sz = m_sz.split(separator);
						for (int i = 0; i < sz.length; i++)
						{
							server.m_mgz[j][i] = Double.parseDouble(sz[i]);
						}

						System.out.println("Received Magnet Data.(" + j + ")");
						server.done[2][j] = true;
						
						try //write the data to a CSV-File
						{
							FileWriter fstream = null;
							BufferedWriter writer = null;
							
							fstream = new FileWriter("Magnet"+j+"-"+"X"+".csv");
							writer = new BufferedWriter(fstream);
							writer.write(m_sx);
							writer.close();

							fstream = new FileWriter("Magnet"+j+"-"+"Y"+".csv");
							writer = new BufferedWriter(fstream);
							writer.write(m_sy);
							writer.close();

							fstream = new FileWriter("Magnet"+j+"-"+"Z"+".csv");
							writer = new BufferedWriter(fstream);
							writer.write(m_sz);
							writer.close();
							
						}catch (Exception e){}
						
						if (j % 2 == 1) // if we just added the second array of data
							// we can compare to the first
						{
							//but we might still have to wait for the first to finish
							while (!server.done[2][j - 1]){}
							
							//Calculate the Coherences for all 3 Arrays
							double[] ox = Coherence.cohere(	server.m_mgx[j - 1],
															server.m_mgx[j], n_values/n_windows/2, -1);
							double[] oy = Coherence.cohere(	server.m_mgy[j - 1],
															server.m_mgy[j], n_values/n_windows/2, -1);
							double[] oz = Coherence.cohere(	server.m_mgz[j - 1],
															server.m_mgz[j], n_values/n_windows/2, -1);
							
							//Calculate the Average
							double ox_sum = 0;
							for (int i = 0; i < ox.length; i++)
							{
								ox_sum += ox[i];
							}
							ox_sum = ox_sum / ox.length;
				
							double oy_sum = 0;
							for (int i = 0; i < oy.length; i++)
							{
								oy_sum += oy[i];
							}
							oy_sum = oy_sum / oy.length;
							
							double oz_sum = 0;
							for (int i = 0; i < oz.length; i++)
							{
								oz_sum += oz[i];
							}
							oz_sum = oz_sum / oz.length;
				
							//Get the Result
							double result = (ox_sum + oy_sum + oz_sum) / 3;
							System.out.println("Mag Result: " + result);
						}
						
					}
					//--------------------END   MAG   DATA READING----------------------//
				} catch (IOException e)
				{
					System.err.println("Read failed");
					System.exit(-1);
				}
			}
		}
	}
	

	public void listenSocket()
	{
		try
		{
			server = new ServerSocket(server_port);	
			while (++conn_i < max_conn)
			{
				//We create a new SocketConnection instance 
				//and we give it the client, our own server instance and 
				//an integer that tells it which part of the arrays to use.
				SocketConnection connection = new SocketConnection(server.accept(),
												this, mod_i++ % max_conn);
				Thread t = new Thread(connection);
				t.start();
			}

		} catch (IOException e)
		{
			System.err.println("Could not open Port.");
			System.exit(-1);
		}
	}

	protected void finalize()
	{
		try
		{
			server.close();
		} catch (IOException e)
		{
			System.err.println("Could not close.");
			System.exit(-1);
		}
	}

	/**
	 * Main entry point of the Socket Server
	 * @param args Standard Java Arguments
	 */
	public static void main(String[] args)
	{
		AndroScopeServer frame = new AndroScopeServer();
		for (int i = 0; i < frame.done.length; i++)
		{
			for (int j = 0; j < frame.done[i].length; j++)
			{
				frame.done[i][j] = false;
			}
		}
		frame.setTitle("Server Program");
		frame.setSize(200, 100);
		WindowListener l = new WindowAdapter()
		{
			public void windowClosing(WindowEvent e)
			{
				System.exit(0);
			}
		};
		frame.addWindowListener(l);
		frame.pack();
		frame.setVisible(true);
		frame.listenSocket();
	}
}
