/* Copyright Rene Mayrhofer
 * File created 2006-05-16
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.apps;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import org.apache.log4j.Logger;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;

import org.eu.mayrhofer.authentication.accelerometer.MotionAuthenticationProtocol1;
import org.eu.mayrhofer.authentication.accelerometer.MotionAuthenticationProtocol2;
import org.eu.mayrhofer.sensors.AsciiLineReaderBase;
import org.eu.mayrhofer.sensors.ParallelPortPWMReader;
import org.eu.mayrhofer.sensors.TimeSeriesAggregator;
import org.eu.mayrhofer.sensors.WiTiltRawReader;

/** This is a simple demonstrator for the shaking authentication. It
 * shows both protocol variants with a simple GUI.
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class ShakingSinglePCDemonstrator {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(ShakingSinglePCDemonstrator.class);

	private Shell sShell = null;  //  @jve:decl-index=0:visual-constraint="10,10"
	private Composite coherenceField = null;
	private Composite matchingField = null;
	private Label coherence = null;
	private Label matching = null;
	private Label coherenceValue = null;
	private Label matchingValue = null;
	private Label device1 = null;
	private Label device2 = null;
	private Label device1State = null;
	private Label device2State = null;
	
	private Protocol1Hooks prot1_a = null;
	private Protocol1Hooks prot1_b = null;
	private Protocol2Hooks prot2_a = null;
	private Protocol2Hooks prot2_b = null;
	
	private AsciiLineReaderBase reader1 = null;
	private AsciiLineReaderBase reader2 = null;

	/**
	 * This method initializes composite	
	 *
	 */
	private void createComposite() {
		coherenceField = new Composite(sShell, SWT.NONE);
		coherenceField.setBackground(new Color(Display.getDefault(), 227, 227, 255));
		coherenceField.setBounds(new org.eclipse.swt.graphics.Rectangle(18,120,370,300));
		coherence = new Label(coherenceField, SWT.NONE);
		coherence.setBounds(new org.eclipse.swt.graphics.Rectangle(17,15,334,36));
		coherence.setFont(new Font(Display.getDefault(), "Sans", 18, SWT.NORMAL));
		coherence.setText("Method 1:");
		coherenceValue = new Label(coherenceField, SWT.NONE);
		coherenceValue.setBounds(new org.eclipse.swt.graphics.Rectangle(17,55,334,36));
		coherenceValue.setFont(new Font(Display.getDefault(), "Sans", 24, SWT.NORMAL));
		coherenceValue.setText("0");
	}

	/**
	 * This method initializes composite1	
	 *
	 */
	private void createComposite1() {
		matchingField = new Composite(sShell, SWT.NONE);
		matchingField.setBackground(new Color(Display.getDefault(), 227, 227, 255));
		matchingField.setBounds(new org.eclipse.swt.graphics.Rectangle(394,120,370,300));
		matching = new Label(matchingField, SWT.NONE);
		matching.setBounds(new org.eclipse.swt.graphics.Rectangle(17,15,334,36));
		matching.setFont(new Font(Display.getDefault(), "Sans", 18, SWT.NORMAL));
		matching.setText("Method 2:");
		matchingValue = new Label(matchingField, SWT.NONE);
		matchingValue.setBounds(new org.eclipse.swt.graphics.Rectangle(17,55,334,36));
		matchingValue.setFont(new Font(Display.getDefault(), "Sans", 24, SWT.NORMAL));
		matchingValue.setText("0");
	}

	/**
	 * This method initializes sShell
	 */
	private void createSShell() {
		sShell = new Shell();
		sShell.setText("Shake well before use");
		
		device1 = new Label(sShell, SWT.NONE);
		device1.setBounds(new org.eclipse.swt.graphics.Rectangle(18,10,200,30));
		device1.setFont(new Font(Display.getDefault(), "Sans", 18, SWT.NORMAL));
		device1.setAlignment(SWT.LEFT);
		device1.setText("Device 1:");
		device2 = new Label(sShell, SWT.NONE);
		device2.setBounds(new org.eclipse.swt.graphics.Rectangle(18,40,200,30));
		device2.setFont(new Font(Display.getDefault(), "Sans", 18, SWT.NORMAL));
		device2.setAlignment(SWT.LEFT);
		device2.setText("Device 2:");

		device1State = new Label(sShell, SWT.NONE);
		device1State.setBounds(new org.eclipse.swt.graphics.Rectangle(230,10,300,30));
		device1State.setFont(new Font(Display.getDefault(), "Sans", 18, SWT.NORMAL));
		device1State.setAlignment(SWT.LEFT);
		device1State.setText("quiescent");
		device2State = new Label(sShell, SWT.NONE);
		device2State.setBounds(new org.eclipse.swt.graphics.Rectangle(230,40,300,30));
		device2State.setFont(new Font(Display.getDefault(), "Sans", 18, SWT.NORMAL));
		device2State.setAlignment(SWT.LEFT);
		device2State.setText("quiescent");
		
		createComposite();
		createComposite1();
		sShell.setSize(new org.eclipse.swt.graphics.Point(786,515));
	}
	
	/** The only constructor for the shaking demonstrator. 
	 * @param device1 If deviceType is set to 1, this specifies the log file (or pipe) to
	 *                read the pulse-width parallel port data. A special case is a syntax
	 *                "port:<port number>" to open an TCP port and listen for incoming log
	 *                lines on that port. If deviceType is set to 2,
	 *                this specifies the name of the first serial port to read from the
	 *                WiTilt sensor.
	 * @param device2 If deviceType is set to 2, this specifies the name of the second 
	 *                serial port to read from the WiTilt sensor. If deviceType is set to 1,
	 *                it is simply ignored.
	 * @param deviceType The sensor device type to use. If set to 1, pulse-width signals
	 *                   will be sampled from the parallel port. If set to 2, WiTilt devices
	 *                   will be used.
	 * @throws IOException
	 */
	public ShakingSinglePCDemonstrator(String device1, String device2, int deviceType) throws IOException {
		/* 1: construct the central sensor reader object and the two segment aggregators */
		final int samplerate = 128; // Hz
		final int windowsize = samplerate/2; // 1/2 second
		final int minsegmentsize = windowsize; // 1/2 second
		final double varthreshold = 350;

		/* First of all, open the display so that there's feedback and so that the events can write
		 * to an open display.
		 */
		createSShell();
		sShell.open();
		System.out.println("+++++++++++++++++++++++++");
		
		final TimeSeriesAggregator aggr_a = new TimeSeriesAggregator(3, windowsize, minsegmentsize);
		final TimeSeriesAggregator aggr_b = new TimeSeriesAggregator(3, windowsize, minsegmentsize);
		aggr_a.setOffset(0);
		aggr_a.setMultiplicator(1/128f);
		aggr_a.setSubtractTotalMean(true);
		aggr_a.setActiveVarianceThreshold(varthreshold);
		aggr_b.setOffset(0);
		aggr_b.setMultiplicator(1/128f);
		aggr_b.setSubtractTotalMean(true);
		aggr_b.setActiveVarianceThreshold(varthreshold);

		/* 2: construct the two prototol instances: two different variants, each with two sides */
		prot1_a = new Protocol1Hooks();
		prot1_b = new Protocol1Hooks();
		// TODO: move this threshold into MotionAuthenticationProtocol2
		prot2_a = new Protocol2Hooks(5, 56789, 56798);
		prot2_b = new Protocol2Hooks(5, 56798, 56789);
		
		/* 3: register the protocols with the respective sides */
		aggr_a.addNextStageSegmentsSink(prot1_a);
		aggr_b.addNextStageSegmentsSink(prot1_b);
		aggr_a.addNextStageSamplesSink(prot2_a);
		aggr_b.addNextStageSamplesSink(prot2_b);

		/* 4: authenticate for protocol variant 1 (variant 2 doesn't need this step) */
		prot1_a.setContinuousChecking(true);
		prot1_b.setContinuousChecking(true);
		prot1_a.startServer();
		prot1_b.startAuthentication("localhost");
		
		if (deviceType == 1) {
			if (! device1.startsWith("port:")) {
				// just read from the file
				reader1 = new ParallelPortPWMReader(device1, samplerate);

				reader1.addSink(new int[] {0, 1, 2}, aggr_a.getInitialSinks());
				reader1.addSink(new int[] {4, 5, 6}, aggr_b.getInitialSinks());
				reader1.start();
			}
			else {
				// open an UDP socket and read from there
				int port = Integer.parseInt(device1.substring(5));
				logger.info("Creating TCP listening socket on port " + port);
				final ServerSocket serv = new ServerSocket(port);
				new Thread(new Runnable() { 
					public void run() {
						try {
							while (true) {
								logger.info("Waiting for TCP client to connect");
								aggr_a.reset();
								aggr_b.reset();
								Socket sock = serv.accept();
								logger.info("Client " + sock.getRemoteSocketAddress() + " connected");
								try {
									reader1 = new ParallelPortPWMReader(sock.getInputStream(), samplerate);
									reader1.addSink(new int[] {0, 1, 2}, aggr_a.getInitialSinks());
									reader1.addSink(new int[] {4, 5, 6}, aggr_b.getInitialSinks());
									reader1.simulateSampling();
								}
								catch (IOException e) {
									logger.warn("Could not read from remote host " + sock.getRemoteSocketAddress() + 
										", most probably client terminated connection. Disconnecting.");
								}
							}
						}	
						catch (IOException e) {
							logger.error("Could not accept connection from socket: " + e);
						}
					}
				}).start();
			}

		}
		else if (deviceType == 2) {
			reader1 = new WiTiltRawReader(device1);
			reader2 = new WiTiltRawReader(device2);

			reader1.addSink(new int[] {0, 1, 2}, aggr_a.getInitialSinks());
			reader2.addSink(new int[] {0, 1, 2}, aggr_b.getInitialSinks());
			reader1.start();
			reader2.start();
		}
		else
			throw new IllegalArgumentException("Device type " + deviceType + " unknown");
	}

	public static void main(String[] args) throws IOException {
		/* Before this is run, be sure to set up the launch configuration (Arguments->VM Arguments)
		 * for the correct SWT library path in order to run with the SWT dlls. 
		 * The dlls are located in the SWT plugin jar.  
		 * For example, on Windows the Eclipse SWT 3.1 plugin jar is:
		 *       installation_directory\plugins\org.eclipse.swt.win32_3.1.0.jar
		 */

		if (args.length < 2) {
			System.err.println("Required parameters: <device type: 'parallel' or 'witilt'> <device1> <device2>");
			System.exit(1);
		}
		int deviceType = -1;
		String dev1 = null, dev2 = null;
		if (args[0].equals("parallel")) {
			deviceType = 1;
			dev1 = args[1];
		}
		else if (args[0].equals("witilt")) {
			deviceType = 2;
			dev1 = args[1];
			dev2 = args[2];
		}
		else if (args[0].equals("listentcp")) {
			deviceType = 1;
			dev1 = "port:" + args[1];
		}
		
		ShakingSinglePCDemonstrator thisClass = new ShakingSinglePCDemonstrator(dev1, dev2, deviceType);

		Display display = Display.getDefault();
		while (!thisClass.sShell.isDisposed()) {
			if (!display.readAndDispatch())
				display.sleep();
		}
		display.dispose();
		
		// stop the sensor listener threads properly
		if (thisClass.reader1 != null)
			thisClass.reader1.stop();
		if (thisClass.reader2 != null)
			thisClass.reader2.stop();
		
		System.exit(0);
	}
	
	private class Protocol1Hooks extends MotionAuthenticationProtocol1 {
		protected Protocol1Hooks() {
			super(false);
		}
		
		protected void protocolSucceededHook(InetAddress remote, 
				Object optionalRemoteId, String optionalParameterFromRemote, 
				byte[] sharedSessionKey, Socket toRemote) {
			logger.info("Protocol variant 1 succedded with " + (remote != null ? remote.getHostAddress() : "null") + 
					": shared key is " + (sharedSessionKey != null ? sharedSessionKey.toString() : "null"));
			Display.getDefault().asyncExec(new Runnable() {
				public void run() {
					coherenceField.setBackground(new Color(Display.getDefault(), 0, 255, 0));
					coherenceValue.setText(Double.toString(lastCoherenceMean));
				}
			});
		}		

		protected void protocolFailedHook(InetAddress remote, Object optionalRemoteId, 
				Exception e, String message) {
			logger.info("Protocol variant 1 failed with " + remote.getHostAddress()  + ": " + e + ", " + message); 
			Display.getDefault().asyncExec(new Runnable() {
				public void run() {
					coherenceField.setBackground(new Color(Display.getDefault(), 255, 0, 0));
					coherenceValue.setText(Double.toString(lastCoherenceMean));
				}
			});
		}
		
		protected void protocolProgressHook(InetAddress remote, 
				Object optionalRemoteId, int cur, int max, String message) {
			logger.debug("Protocol variant 1 progress with " + remote.getHostAddress() +
					" " + cur + " of " + max + ": " + message); 
		}		
	}

	private class Protocol2Hooks extends MotionAuthenticationProtocol2 {
		protected Protocol2Hooks(int numMatches, int udpRecvPort, int udpSendPort) throws IOException {
			super(numMatches, false, udpRecvPort, udpSendPort, "127.0.0.1");
		}
		
		protected void protocolSucceededHook(String remote, byte[] sharedSessionKey) {
			logger.info("Protocol variant 2 succedded with " + remote + 
					": shared key is " + sharedSessionKey.toString());
			Display.getDefault().asyncExec(new Runnable() {
				public void run() {
					matchingField.setBackground(new Color(Display.getDefault(), 0, 255, 0));
					//matchingValue.setText(Double.toString(lastCoherenceMean));
				}
			});
		}

		protected void protocolFailedHook(String remote, Exception e, String message) {
			logger.info("Protocol variant 2 failed with " + remote + ": " + e + ", " + message); 
			Display.getDefault().asyncExec(new Runnable() {
				public void run() {
					matchingField.setBackground(new Color(Display.getDefault(), 255, 0, 0));
					//matchingValue.setText(Double.toString(lastCoherenceMean));
				}
			});
		}

		protected void protocolProgressHook(String remote, int cur, int max, String message) {
			logger.debug("Protocol variant 2 progress with " + remote +
					" " + cur + " of " + max + ": " + message); 
		}
	}
}
