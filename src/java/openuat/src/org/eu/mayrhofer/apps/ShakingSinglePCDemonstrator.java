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
import java.net.Socket;

import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;

import org.eu.mayrhofer.authentication.accelerometer.MotionAuthenticationProtocol1;
import org.eu.mayrhofer.authentication.accelerometer.MotionAuthenticationProtocol2;
import org.eu.mayrhofer.sensors.ParallelPortPWMReader;
import org.eu.mayrhofer.sensors.TimeSeriesAggregator;

public class ShakingSinglePCDemonstrator {

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

	/**
	 * This method initializes composite	
	 *
	 */
	private void createComposite() {
		coherenceField = new Composite(sShell, SWT.NONE);
		coherenceField.setBackground(new Color(Display.getCurrent(), 227, 227, 255));
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
		matchingField.setBackground(new Color(Display.getCurrent(), 227, 227, 255));
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
	
	public void ShakingSinglePCDemonstrator(String device1, String device2, int deviceType) throws IOException {
		/* 1: construct the central sensor reader object and the two segment aggregators */
		int samplerate = 128; // Hz
		int windowsize = samplerate/2; // 1/2 second
		int minsegmentsize = windowsize; // 1/2 second
		double varthreshold = 350;
		ParallelPortPWMReader r = new ParallelPortPWMReader(device1, samplerate);
		TimeSeriesAggregator aggr_a = new TimeSeriesAggregator(3, windowsize, minsegmentsize);
		TimeSeriesAggregator aggr_b = new TimeSeriesAggregator(3, windowsize, minsegmentsize);
		r.addSink(new int[] {0, 1, 2}, aggr_a.getInitialSinks());
		r.addSink(new int[] {4, 5, 6}, aggr_b.getInitialSinks());
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
		prot2_a = new Protocol2Hooks(5);
		prot2_b = new Protocol2Hooks(5);
		
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
		
		r.simulateSampling();
	}

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		/* Before this is run, be sure to set up the launch configuration (Arguments->VM Arguments)
		 * for the correct SWT library path in order to run with the SWT dlls. 
		 * The dlls are located in the SWT plugin jar.  
		 * For example, on Windows the Eclipse SWT 3.1 plugin jar is:
		 *       installation_directory\plugins\org.eclipse.swt.win32_3.1.0.jar
		 */
		Display display = Display.getDefault();
		ShakingSinglePCDemonstrator thisClass = new ShakingSinglePCDemonstrator();
		thisClass.createSShell();
		thisClass.sShell.open();

		while (!thisClass.sShell.isDisposed()) {
			if (!display.readAndDispatch())
				display.sleep();
		}
		display.dispose();
	}
	
	private class Protocol1Hooks extends MotionAuthenticationProtocol1 {
		protected Protocol1Hooks() {
			super(false);
		}
		
		protected void protocolSucceededHook(InetAddress remote, 
				Object optionalRemoteId, String optionalParameterFromRemote, 
				byte[] sharedSessionKey, Socket toRemote) {
		}		

		protected void protocolFailedHook(InetAddress remote, Object optionalRemoteId, 
				Exception e, String message) {
			
		}
		
		protected void protocolProgressHook(InetAddress remote, 
				Object optionalRemoteId, int cur, int max, String message) {
		}		
	}

	private class Protocol2Hooks extends MotionAuthenticationProtocol2 {
		protected Protocol2Hooks(int numMatches) throws IOException {
			super(numMatches, false);
		}
		
		protected void protocolSucceededHook(String remote, byte[] sharedSessionKey) {
			
		}

		protected void protocolFailedHook(String remote, Exception e, String message) {
			
		}

		protected void protocolProgressHook(String remote, int cur, int max, String message) {
			
		}
	}
}
