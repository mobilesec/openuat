/* Copyright Lukas Huser
 * File created 2008-12-17
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.apps;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.IOException;
import java.util.Random;
import java.util.Vector;

import javax.bluetooth.RemoteDevice;
import javax.bluetooth.UUID;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;

import org.openuat.authentication.AuthenticationProgressHandler;
import org.openuat.authentication.OOBChannel;
import org.openuat.authentication.OOBMessageHandler;
import org.openuat.authentication.exceptions.InternalApplicationException;
import org.openuat.channel.oob.AWTButtonChannelImpl;
import org.openuat.channel.oob.ButtonChannelImpl;
import org.openuat.channel.oob.ButtonToButtonChannel;
import org.openuat.channel.oob.FlashDisplayToButtonChannel;
import org.openuat.channel.oob.LongVibrationToButtonChannel;
import org.openuat.channel.oob.ProgressBarToButtonChannel;
import org.openuat.channel.oob.ShortVibrationToButtonChannel;
import org.openuat.log.Log;
import org.openuat.log.Log4jFactory;
import org.openuat.log.LogFactory;
import org.openuat.util.BluetoothPeerManager;
import org.openuat.util.BluetoothRFCOMMServer;
import org.openuat.util.BluetoothSupport;
import org.openuat.util.Hash;
import org.openuat.util.ProtocolCommandHandler;
import org.openuat.util.RemoteConnection;

/**
 * This Swing application demonstrates the different button channels within
 * the OpenUAT toolkit on the J2SE platform. Most of the channels are described in 
 * 'BEDA: Button-Enabled Device Association' by C. Soriente and G. Tsudik.
 * However, there are possibly additional channels implemented here.
 * 
 * @author Lukas Huser
 * @version 1.0
 */
public class BedaApp implements AuthenticationProgressHandler {
	
	/* The authentication service uuid... */
	private static final UUID SERVICE_UUID = new UUID("447d8ecbefea4b2d93107ced5d1bba7e", false);
	
	/* ...and it's name */
	private static final String SERVICE_NAME = "UACAP - Beda";
	
	/* An identifier used to register a command handler with the RFCOMMServer */
	private static final String PRE_AUTH = "PRE_AUTH";
	
	/* Authentication method supported by BEDA: authentic transfer */
	private static final String TRANSFER_AUTH = "TRANSFER_AUTH";
	
	/* Authentication method supported by BEDA: input */
	private static final String INPUT = "INPUT";
	
	/*
	 * GUI related constants
	 */
	private static final int FRAME_WIDTH	= 500;
	private static final int FRAME_HIGHT	= 500;
	private static final int PANEL_WIDTH	= 200;
	private static final int PANEL_HIGHT	= 400;
	private static final int LIST_WIDTH		= 200;
	private static final int LIST_HIGHT		= 300;
	
	/* Main window of this application */
	private JFrame mainWindow;
	
	/* Menu bar for the main window */
	private JMenuBar menuBar;
	
	/* A gui component to list all button channels */
	private JList channelList;
	
	/* A gui component to list all found bluetooth devices */
	private JList deviceList;
	
	/* A button to refresh the deviceList */
	private JButton refreshButton;
	
	/* A mouse listener for the two JLists channelList and deviceList
	 * which reacts to double-clicks on list entries
	 */
	private MouseListener doubleClickListener;
	
	/* Scans for bluetooth devices around */
	private BluetoothPeerManager peerManager;
	
	/* Bluetooth server to advertise our services and accept incoming connections */
	private BluetoothRFCOMMServer btServer;
	
	/* Random number generator */
	private Random random;
	
	/* Logger instance */
	private Log logger;
	
	
	/**
	 * Entry point for the main application.<br/>
	 * It just creates a new instance of this class.
	 * @param args Command line arguments are ignored.
	 */
	public static void main(String[] args) {
		new BedaApp();
	}
	
	/**
	 * Creates a new instance of this class and launches the
	 * actual application.
	 */
	public BedaApp() {
		random = new Random(System.currentTimeMillis());
		// Initialize the logger. Use a wrapper around the log4j framework.
		LogFactory.init(new Log4jFactory());
		logger = LogFactory.getLogger(BedaApp.class.getName());
		logger.debug("Logger initiated!");
		
		mainWindow = new JFrame("Beda App");
		mainWindow.setSize(new Dimension(FRAME_WIDTH, FRAME_HIGHT));
		mainWindow.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		mainWindow.getContentPane().setLayout(new FlowLayout());
		
		// set up the menu bar
		menuBar = new JMenuBar();
		JMenu menu = new JMenu("Menu");
		final JMenuItem homeEntry = new JMenuItem("Home");
		final JMenuItem testEntry = new JMenuItem("Test channels");
		
		ActionListener menuListener = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				JMenuItem menuItem = (JMenuItem)event.getSource();
				if (menuItem == homeEntry) {
					showHomeScreen();
				}
				else if (menuItem == testEntry) {
					showTestScreen();
				}
			}
		};
		homeEntry.addActionListener(menuListener);
		testEntry.addActionListener(menuListener);
		
		menu.add(homeEntry);
		menu.add(testEntry);
		menuBar.add(menu);
		mainWindow.setJMenuBar(menuBar);
		
		// set up channel list
		ButtonChannelImpl impl = new AWTButtonChannelImpl(mainWindow.getContentPane());
		OOBChannel[] channels = {
			new ButtonToButtonChannel(impl),
			new FlashDisplayToButtonChannel(impl),
			new ProgressBarToButtonChannel(impl),
			new ShortVibrationToButtonChannel(impl),
			new LongVibrationToButtonChannel(impl)
		};
		channelList = new JList(channels);
		channelList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		channelList.setVisibleRowCount(15);
		channelList.setPreferredSize(new Dimension(LIST_WIDTH, LIST_HIGHT));
		
		// set up device list
		deviceList = new JList();
		deviceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		deviceList.setVisibleRowCount(15);
		deviceList.setPreferredSize(new Dimension(LIST_WIDTH, LIST_HIGHT));
		
		// enable double clicks on the two lists
		doubleClickListener = new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent event) {
				// react on double clicks
				if (event.getButton() == MouseEvent.BUTTON1 && event.getClickCount() == 2) {
					JList source = (JList)event.getSource();
					if (source == channelList) {
						OOBChannel channel = (OOBChannel)channelList.getSelectedValue();
						// TODO: launch protocol
					}
					else if (source == deviceList) {
						channelList.setEnabled(true);
					}
				}
			}
		};
		deviceList.addMouseListener(doubleClickListener);
		// note: this listener will be set on the channelList in the showHomeScreen method
		
		// create refresh button
		refreshButton = new JButton("Refresh list");
		ActionListener buttonListener = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				if ((JButton)event.getSource() == refreshButton) {
					refreshDeviceList();
				}
			}
		};
		refreshButton.addActionListener(buttonListener);
		
		// init bluetooth support
		if (! BluetoothSupport.init()) {
			logger.error("Could not initialize Bluetooth API");
		}
		
		// set up the bluetooth peer manager
		BluetoothPeerManager.PeerEventsListener listener =
			new BluetoothPeerManager.PeerEventsListener() {
				public void inquiryCompleted(Vector newDevices) {
					// TODO: populate device list
					// updateDeviceList();
				}
				public void serviceSearchCompleted(RemoteDevice remoteDevice, Vector services, int errorReason) {
					// ignore
				}
		};
		
		try {
			peerManager = new BluetoothPeerManager();
			peerManager.addListener(listener);
		} catch (IOException e) {
			logger.error("Could not initiate BluetoothPeerManager.", e);
		}
		
		// set up the bluetooth rfcomm server
		try {
			btServer = new BluetoothRFCOMMServer(null, SERVICE_UUID, SERVICE_NAME, 30000, true, false);
			btServer.addAuthenticationProgressHandler(this);
			btServer.start();
			if (logger.isInfoEnabled()) {
				logger.info("Finished starting SDP service at " + btServer.getRegisteredServiceURL());
			}
		} catch (IOException e) {
			logger.error("Could not create bluetooth server.", e);
		}
		ProtocolCommandHandler inputProtocolHandler = new ProtocolCommandHandler() {
			@Override
			public boolean handleProtocol(String firstLine, RemoteConnection remote) {
				if (logger.isDebugEnabled()) {
					logger.debug("Handle protocol command: " + firstLine);
				}
				if (firstLine.equals(PRE_AUTH)) {
					// TODO: run input protocol
					//inputProtocol(false, remote, null);
					return true;
				}
				return false;
			}
		};
		btServer.addProtocolCommandHandler(PRE_AUTH, inputProtocolHandler);
		
		
		// build initial screen (the home screen)
		showHomeScreen();
		
		// launch window
		mainWindow.pack();
		mainWindow.setVisible(true);
	}
	
	@Override
	public void AuthenticationFailure(Object sender, Object remote, Exception e, String msg) {
		// in the input case, reset the shared key
		btServer.setPresharedShortSecret(null);
		logger.error(msg, e);
		// TODO: alertError(msg);
	}

	@Override
	public void AuthenticationProgress(Object sender, Object remote, int cur, int max, String msg) {
		// is safely ignored
	}

	@Override
	public boolean AuthenticationStarted(Object sender, Object remote) {
		// not interested in it, return true
		return true;
	}

	@Override
	public void AuthenticationSuccess(Object sender, Object remote,Object result) {
		logger.info("Authentication success event.");
        Object[] res = (Object[]) result;
        // remember the secret key shared with the other device
        byte[] sharedKey = (byte[]) res[0];
        // and extract the shared authentication key for phase 2
        byte[] authKey = (byte[]) res[1];
        // then extract the optional parameter
        String param = (String) res[2];
        if (logger.isInfoEnabled()) {
        	logger.info("Extracted session key of length " + sharedKey.length +
        		", authentication key of length " + (authKey != null ? authKey.length : 0) + 
        		" and optional parameter '" + param + "'");
        }
        RemoteConnection connectionToRemote = null;
        if (res.length > 3) {
        	connectionToRemote = (RemoteConnection) res[3];	
        }
        if (param != null) {
	        if (param.equals(INPUT)) {
	        	// for input: authentication successfully finished!
	        	btServer.setPresharedShortSecret(null);
	        	logger.info("Authentication through input successful!");
				// TODO: inform success 
	        }
	        else if (param.equals(TRANSFER_AUTH)) {
	        	byte[] oobMsg = getShortHash(authKey);
	        	if (oobMsg != null) {
	        		// TODO transferProtocol(isInitiator, connectionToRemote, currentChannel, oobMsg);
	        	}
	        }
        }
	}

	/* Helper method to set up the home screen */
	private void showHomeScreen() {
		JPanel devicePanel = new JPanel();
		devicePanel.setPreferredSize(new Dimension(PANEL_WIDTH, PANEL_HIGHT));
		JLabel label = new JLabel("Bluetooth Devices");
		devicePanel.add(label);
		devicePanel.add(deviceList);
		devicePanel.add(refreshButton);
		
		channelList.addMouseListener(doubleClickListener);
		channelList.setEnabled(false);
		JPanel channelPanel = new JPanel();
		channelPanel.setPreferredSize(new Dimension(PANEL_WIDTH, PANEL_HIGHT));
		label = new JLabel("Available Channels");
		channelPanel.add(label);
		channelPanel.add(channelList);
		
		mainWindow.getContentPane().removeAll();
		mainWindow.getContentPane().add(devicePanel);
		mainWindow.getContentPane().add(channelPanel);
		mainWindow.getContentPane().validate();
		mainWindow.getContentPane().repaint();
	}
	
	/* Helper method to set up the channel test screen */
	private void showTestScreen() {
		JPanel testPanel = new JPanel();
		testPanel.setPreferredSize(new Dimension(PANEL_WIDTH, PANEL_HIGHT));
		channelList.removeMouseListener(doubleClickListener);
		channelList.setEnabled(true);
		final JButton captureButton = new JButton("Capture");
		final JButton transmitButton = new JButton("Transmit");
		
		ActionListener listener = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				JButton source = (JButton)event.getSource();
				OOBChannel channel = (OOBChannel)channelList.getSelectedValue();
				if (source == captureButton && channel != null) {
					testCapture(channel);
				}
				else if (source == transmitButton && channel != null) {
					testTransmit(channel);
				}
			}
		};
		captureButton.addActionListener(listener);
		transmitButton.addActionListener(listener);
		
		JLabel label = new JLabel("Channel Test (offline)");
		
		testPanel.add(label);
		testPanel.add(channelList);
		testPanel.add(captureButton);
		testPanel.add(transmitButton);
		
		mainWindow.getContentPane().removeAll();
		mainWindow.getContentPane().add(testPanel);
		mainWindow.getContentPane().validate();
		mainWindow.getContentPane().repaint();
	}
	
	/* Scans for new Bluetooth devices and displays them in the deviceList */
	private void refreshDeviceList() {
		// TODO
	
	}
	
	/* Test the capture functionality (offline) */
	private void testCapture(OOBChannel channel) {
		OOBMessageHandler handler = new OOBMessageHandler() {
			@Override
			public void handleOOBMessage(int channelType, byte[] data) {
				showTestScreen();
				String txt = String.format(
					"The following message has been captured: %02x%02x%02x",
					data[0], data[1], data[2]);
				if (logger.isInfoEnabled()) {
					logger.info("Capture ok. Message: " + txt);
				}
				JOptionPane.showMessageDialog(mainWindow, txt, "Capture ok", JOptionPane.INFORMATION_MESSAGE);
			}
		};
		channel.setOOBMessageHandler(handler);
		channel.capture();
	}
	
	/* Test the transmit functionality (offline) */
	private void testTransmit(OOBChannel channel) {
		final byte[] randomMessage = new byte[3];
		random.nextBytes(randomMessage);
		OOBMessageHandler handler = new OOBMessageHandler() {
			@Override
			public void handleOOBMessage(int channelType, byte[] data) {
				showTestScreen();
				String txt = String.format(
					"The following message has been transmitted: %02x%02x%02x",
					randomMessage[0], randomMessage[1], randomMessage[2]);
				if (logger.isInfoEnabled()) {
					logger.info("Transmission ok. Message: " + txt);
				}
				JOptionPane.showMessageDialog(mainWindow, txt, "Transmission ok", JOptionPane.INFORMATION_MESSAGE);
			}
		};
		channel.setOOBMessageHandler(handler);
		channel.transmit(randomMessage);
	}
	
	/* 
	 * Takes the hash of the input and returns its first 3 bytes.
	 * The result is suitable to send over a button channel
	 */
	private byte[] getShortHash(byte[] bytes) {
		byte[] result = new byte[3];
    	try {
			byte[] hash = Hash.doubleSHA256(bytes, false);
			System.arraycopy(hash, 0, result, 0, 3);
		} catch (InternalApplicationException e) {
			logger.error("Could not create hash.", e);
			result = null;
		}
		return result;
	}
}
