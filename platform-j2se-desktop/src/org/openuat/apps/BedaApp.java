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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.bluetooth.RemoteDevice;
import javax.bluetooth.ServiceRecord;
import javax.bluetooth.UUID;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
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
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.apache.commons.codec.binary.Hex;
import org.openuat.authentication.AuthenticationProgressHandler;
import org.openuat.authentication.HostProtocolHandler;
import org.openuat.authentication.OOBChannel;
import org.openuat.authentication.OOBMessageHandler;
import org.openuat.authentication.exceptions.InternalApplicationException;
import org.openuat.channel.UACAPProtocolConstants;
import org.openuat.channel.main.ProtocolCommandHandler;
import org.openuat.channel.main.RemoteConnection;
import org.openuat.channel.main.bluetooth.jsr82.BluetoothPeerManager;
import org.openuat.channel.main.bluetooth.jsr82.BluetoothRFCOMMChannel;
import org.openuat.channel.main.bluetooth.jsr82.BluetoothRFCOMMServer;
import org.openuat.channel.oob.ButtonChannel;
import org.openuat.channel.oob.ButtonChannelImpl;
import org.openuat.channel.oob.ButtonToButtonChannel;
import org.openuat.channel.oob.FlashDisplayToButtonChannel;
import org.openuat.channel.oob.LongVibrationToButtonChannel;
import org.openuat.channel.oob.PowerBarToButtonChannel;
import org.openuat.channel.oob.ProgressBarToButtonChannel;
import org.openuat.channel.oob.ShortVibrationToButtonChannel;
import org.openuat.channel.oob.TrafficLightToButtonChannel;
import org.openuat.channel.oob.desktop.AWTButtonChannelImpl;
import org.openuat.util.Hash;
import org.openuat.util.LineReaderWriter;

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

	
	/*
	 * GUI related constants
	 */
	private static final int FRAME_WIDTH	= 500;
	private static final int FRAME_HIGHT	= 500;
	private static final int PANEL_WIDTH	= 200;
	private static final int PANEL_HIGHT	= 400;
	private static final int LIST_WIDTH		= 200;
	private static final int LIST_HIGHT		= 300;
	private static final int LABEL_HIGHT	= 20;
	
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
	
	/* A label used as status bar (to inform user about what's happening) */
	private JLabel statusLabel;
	
	/* A mouse listener for the two JLists channelList and deviceList
	 * which reacts to double-clicks on list entries
	 */
	private MouseListener doubleClickListener;
	
	/* Scans for bluetooth devices around */
	private BluetoothPeerManager peerManager;
	
	/* Bluetooth server to advertise our services and accept incoming connections */
	private BluetoothRFCOMMServer btServer;
	
	/* Keep track of the currently available remote peers */
	private RemoteDevice[] devices;
	
	/* Url of the authentication service on other device */
	private String currentPeerUrl;
	
	/* Remember the currently used button channel */
	private OOBChannel currentChannel;
	
	/* Keep a mapping of the different button channels with their names */
	private HashMap<String, OOBChannel> buttonChannels;
	
	/* Is this device the initiator of the verification protocol? */
	private boolean isInitiator;
	
	/* Random number generator */
	private SecureRandom random;
	
	/* Logger instance */
	private Logger logger = Logger.getLogger(BedaApp.class.getName());
	
	
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
		random			= new SecureRandom();
		devices			= new RemoteDevice[0];
		currentPeerUrl	= null;
		currentChannel	= null;
		isInitiator		= false;
				
		// Initialize the logger. Use a wrapper around the log4j framework.
		/*LogFactory.init(new Log4jFactory());
		logger = LogFactory.getLogger(BedaApp.class.getName());
		logger.finer("Logger initiated!");*/
		
		mainWindow = new JFrame("Beda App");
		mainWindow.setSize(new Dimension(FRAME_WIDTH, FRAME_HIGHT));
		mainWindow.setPreferredSize(new Dimension(FRAME_WIDTH, FRAME_HIGHT));
		mainWindow.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		mainWindow.getContentPane().setLayout(new FlowLayout());
		URL imgURL = getClass().getResource("/resources/Button_Icon_Blue_beda.png");
		ImageIcon icon = imgURL != null ? new ImageIcon(imgURL) : new ImageIcon("resources/Button_Icon_Blue_beda.png");
		mainWindow.setIconImage(icon.getImage());
		
		// prepare the button channels
		ActionListener abortHandler = new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (e.getID() == ActionEvent.ACTION_PERFORMED) {
					logger.warning("Protocol run aborted by user");
					BluetoothRFCOMMChannel.shutdownAllChannels();
					alertError("Protocol run aborted.");
				}
			}
		};
		buttonChannels = new HashMap<String, OOBChannel>();
		ButtonChannelImpl impl = new AWTButtonChannelImpl(mainWindow.getContentPane(), abortHandler);
		OOBChannel c = new ButtonToButtonChannel(impl);
		buttonChannels.put(c.toString(), c);
		c = new FlashDisplayToButtonChannel(impl, false);
		buttonChannels.put(c.toString(), c);
		c = new TrafficLightToButtonChannel(impl);
		buttonChannels.put(c.toString(), c);
		c = new ProgressBarToButtonChannel(impl);
		buttonChannels.put(c.toString(), c);
		c = new PowerBarToButtonChannel(impl);
		buttonChannels.put(c.toString(), c);
		c = new ShortVibrationToButtonChannel(impl);
		buttonChannels.put(c.toString(), c);
		c = new LongVibrationToButtonChannel(impl);
		buttonChannels.put(c.toString(), c);
		
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
		OOBChannel[] channels = {
			new ButtonToButtonChannel(impl),
			new FlashDisplayToButtonChannel(impl, false),
			new TrafficLightToButtonChannel(impl),
			new ProgressBarToButtonChannel(impl),
			new PowerBarToButtonChannel(impl),
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
					if (source == channelList && channelList.isEnabled()) {
						currentChannel = (OOBChannel)channelList.getSelectedValue();
						startAuthentication();
					}
					else if (source == deviceList) {
						int index = deviceList.getSelectedIndex();
						if (index > -1) {
							String peerAddress = devices[index].getBluetoothAddress();
							searchForService(peerAddress);
						}
					}
				}
				event.consume();
			}
		};
		deviceList.addMouseListener(doubleClickListener);
		// note: this listener will be set on the channelList in the showHomeScreen method
		
		ListSelectionListener selectionListener = new ListSelectionListener() {
			@Override
			public void valueChanged(ListSelectionEvent event) {
				channelList.setEnabled(false);
			}
		};
		deviceList.addListSelectionListener(selectionListener);
		
		// create refresh button
		refreshButton = new JButton("Refresh list");
		ActionListener buttonListener = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				if ((JButton)event.getSource() == refreshButton) {
					refreshButton.setEnabled(false);
					statusLabel.setText("Please wait... scanning for devices...");
					peerManager.startInquiry(false);
				}
			}
		};
		refreshButton.addActionListener(buttonListener);
		
		
		// set up the bluetooth peer manager
		BluetoothPeerManager.PeerEventsListener listener =
			new BluetoothPeerManager.PeerEventsListener() {
				public void inquiryCompleted(Vector newDevices) {
					refreshButton.setEnabled(true);
					statusLabel.setText("");
					updateDeviceList();
				}
				public void serviceSearchCompleted(RemoteDevice remoteDevice, Vector services, int errorReason) {
					// ignore
				}
		};
		
		try {
			peerManager = new BluetoothPeerManager();
			peerManager.addListener(listener);
		} catch (IOException e) {
			logger.log(Level.SEVERE, "Could not initiate BluetoothPeerManager.", e);
		}
		
		// set up the bluetooth rfcomm server
		try {
			btServer = new BluetoothRFCOMMServer(null, SERVICE_UUID, SERVICE_NAME, 30000, true, false);
			btServer.addAuthenticationProgressHandler(this);
			btServer.start();
			if (logger.isLoggable(Level.INFO)) {
				logger.info("Finished starting SDP service at " + btServer.getRegisteredServiceURL());
			}
		} catch (IOException e) {
			logger.log(Level.SEVERE, "Could not create bluetooth server.", e);
		}
		ProtocolCommandHandler inputProtocolHandler = new ProtocolCommandHandler() {
			@Override
			public boolean handleProtocol(String firstLine, RemoteConnection remote) {
				if (logger.isLoggable(Level.FINER)) {
					logger.finer("Handle protocol command: " + firstLine);
				}
				if (firstLine.equals(UACAPProtocolConstants.PRE_AUTH)) {
					inputProtocol(false, remote, null);
					return true;
				}
				return false;
			}
		};
		btServer.addProtocolCommandHandler(UACAPProtocolConstants.PRE_AUTH, inputProtocolHandler);
		
		// build staus bar
		statusLabel = new JLabel("");
		statusLabel.setPreferredSize(new Dimension(FRAME_WIDTH - 40, LABEL_HIGHT));
		
		// build initial screen (the home screen)
		showHomeScreen();
		
		// launch window
		mainWindow.pack();
		mainWindow.setVisible(true);
	}
	
	@Override
	public void AuthenticationFailure(Object sender, Object remote, Exception e, String msg) {
		// in the input case, reset the shared key
		btServer.setPresharedShortSecrets(null);
		logger.log(Level.SEVERE, msg, e);
		alertError("Authentication failed!");
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
        if (logger.isLoggable(Level.INFO)) {
        	logger.info("Extracted session key of length " + sharedKey.length +
        		", authentication key of length " + (authKey != null ? authKey.length : 0) + 
        		" and optional parameter '" + param + "'");
        }
        RemoteConnection connectionToRemote = null;
        if (res.length > 3) {
        	connectionToRemote = (RemoteConnection) res[3];	
        }
        if (param != null) {
	        if (param.equals(UACAPProtocolConstants.INPUT)) {
	        	// for input: authentication successfully finished!
	        	btServer.setPresharedShortSecrets(null);
	        	if (connectionToRemote != null) {
	        		connectionToRemote.close();
	        	}
	        	logger.info("Authentication through input successful!");
	        	informSuccess();
	        }
	        else if (param.equals(UACAPProtocolConstants.TRANSFER_AUTH)) {
	        	byte[] oobMsg = getShortHash(authKey);
	        	if (oobMsg != null) {
	        		transferProtocol(isInitiator, connectionToRemote, currentChannel, oobMsg);
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
		
		statusLabel.setText("");
		
		mainWindow.getContentPane().removeAll();
		mainWindow.getContentPane().add(devicePanel);
		mainWindow.getContentPane().add(channelPanel);
		mainWindow.getContentPane().add(statusLabel);
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
		
		statusLabel.setText("");
		
		mainWindow.getContentPane().removeAll();
		mainWindow.getContentPane().add(testPanel);
		mainWindow.getContentPane().add(statusLabel);
		mainWindow.getContentPane().validate();
		mainWindow.getContentPane().repaint();
	}
	
	/* Callback method which is called when scanning for devices has finished. */
	private void updateDeviceList() {
		devices = peerManager.getPeers();
		DefaultListModel listModel = new DefaultListModel();
		for (RemoteDevice device : devices) {
			try {
				listModel.addElement(device.getFriendlyName(false));
			} catch (IOException e) {
				listModel.addElement("Unknown device");
			}
		}
		deviceList.setModel(listModel);
	}
	
	/* Initiates the authentication protocol */
	private void startAuthentication() {
		try {
			if (currentChannel != null) {
				statusLabel.setText("Please wait... Prepare authentication");
				isInitiator = true;
				BluetoothRFCOMMChannel btChannel = new BluetoothRFCOMMChannel(currentPeerUrl);
				btChannel.open();
				if (channelList.getSelectedIndex() == 0) {
					// input case
					String hello = LineReaderWriter.readLine(btChannel.getInputStream());
					if (!hello.equals(HostProtocolHandler.Protocol_Hello)) {
						logger.warning("Got wrong greeting string from server. This probably leads to protocol failure.");
					}
					LineReaderWriter.println(btChannel.getOutputStream(), UACAPProtocolConstants.PRE_AUTH);
					inputProtocol(true, btChannel, currentChannel);
				}
				else {
					// transfer case
					boolean keepConnected = true; // since the key has to be authenticated after key agreement
					HostProtocolHandler.startAuthenticationWith(btChannel, BedaApp.this, -1, keepConnected, UACAPProtocolConstants.TRANSFER_AUTH, false);
				}
			}
		} catch (IOException e) {
			logger.log(Level.SEVERE, "Failed to start authentication.", e);
		}
	}
	
	/* 
	 * Searches for the Beda service on peerAddress, sets the currentPeerURL
	 * and enables the channel list if the service is available.
	 */
	private void searchForService(String peerAddress) {
		currentPeerUrl = null;
		try {
			currentPeerUrl = BluetoothPeerManager.getRemoteServiceURL(
					peerAddress, SERVICE_UUID, ServiceRecord.NOAUTHENTICATE_NOENCRYPT, 10000);
		} catch (IOException e) {
			currentPeerUrl = null;
		}
		if (currentPeerUrl != null) {
			channelList.setEnabled(true);
		}
		else {
			channelList.setEnabled(false);
			statusLabel.setText("Sorry, but the service " + SERVICE_NAME + " is not running on the selected device");
		}
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
				if (logger.isLoggable(Level.INFO)) {
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
				if (logger.isLoggable(Level.INFO)) {
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
			logger.log(Level.SEVERE, "Could not create hash.", e);
			result = null;
		}
		return result;
	}
	
	/* Informs the user of an error and return to main screen */
	private void alertError(String msg) {
		JOptionPane.showMessageDialog(mainWindow, msg, "Error", JOptionPane.ERROR_MESSAGE);
		showHomeScreen();
	}
	
	/* Informs the user about the successfully completed authentication process */
	private void informSuccess() {
		statusLabel.setText("Successfully paired with other device!");
		JOptionPane.showMessageDialog(mainWindow, "Successfully paired with other device!",
				"Success", JOptionPane.INFORMATION_MESSAGE);
		showHomeScreen();
	}
	
	/* 
	 * Runs a transfer protocol over an authenticated out-of-band channel
	 * and verifies the provided short string.
	 * 
	 * Legend:
	 * I	initiator 
	 * R 	responder
	 * --->	insecure channel (bluetooth)
	 * o-->	authentic channel (out-of-band)
	 * 
	 * Protocol:
	 * I    --- "TRANSFER_AUTH:<channel_id>" -->    R
	 *      <--        "READY"               ---
	 *      o--         oobMsg               -->
	 *      <--         "DONE"               ---
	 *      <--         ack/nack             --o
	 * 
	 */
	private void transferProtocol(boolean isInitiator, final RemoteConnection connection, OOBChannel channel, final byte[] oobMsg) {
		this.isInitiator = false; // reset, so we are ready for further pairing attempts.
		final String READY	= "READY";
		final String DONE	= "DONE";
		
		final InputStream in;
		final OutputStream out;
		try {
			in = connection.getInputStream();
			out = connection.getOutputStream();
		} catch (IOException e) {
			logger.log(Level.SEVERE, "Failed to open stream from connection. Abort transfer protocol.", e);
			return;
		}
		
		if (isInitiator) {
			logger.finer("Running transfer as initiator");
			try {
				String initString = UACAPProtocolConstants.TRANSFER_AUTH + ":" + channel.toString();
				LineReaderWriter.println(out, initString);
				String lineIn = LineReaderWriter.readLine(in);
				if (!lineIn.equals(READY)) {
					logger.severe("Unexpected protocol string from remote device. Abort transfer protocol.");
					return;
				}
				channel.transmit(oobMsg);
				// wait for other device
				lineIn = LineReaderWriter.readLine(in);
				if (!lineIn.equals(DONE)) {
					logger.severe("Unexpected protocol string from remote device. Abort transfer protocol.");
					return;
				}
				statusLabel.setText("");
				int ret = JOptionPane.showConfirmDialog(mainWindow, 
	        			"Was the other device successful?", "Authentication", 
	        			JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
				if (ret == JOptionPane.YES_OPTION) {
					informSuccess();
				}
				else if (ret == JOptionPane.NO_OPTION) {
					alertError("Authentication failed!");
				}
			} catch (IOException e) {
				logger.severe("Failed to read/write from io stream. Abort transfer protocol.");
			}
		}
		else { // responder
			logger.finer("Running transfer as responder");
			try {
				String lineIn = LineReaderWriter.readLine(in);
				String protocolDesc = lineIn.substring(0, lineIn.indexOf(':'));
				String channelDesc = lineIn.substring(lineIn.indexOf(':') + 1);
				if (!protocolDesc.equals(UACAPProtocolConstants.TRANSFER_AUTH)) {
					logger.severe("Wrong protocol descriptor from remote device. Abort transfer protocol.");
					return;
				}
				// get appropriate channel
				OOBChannel captureChannel = buttonChannels.get(channelDesc);
				OOBMessageHandler messageHandler = new OOBMessageHandler() {
					@Override
					public void handleOOBMessage(int channelType, byte[] data) {
						try {
							LineReaderWriter.println(out, DONE);
							// check data
							if (logger.isLoggable(Level.INFO)) {
								logger.info("sent oobMsg: " + new String(Hex.encodeHex(oobMsg)) +
										" received oobMsg: " + new String(Hex.encodeHex(data)));
							}
							statusLabel.setText("");
							if (Arrays.equals(data, oobMsg)) {
								JOptionPane.showMessageDialog(mainWindow, 
					        			"Authentication successful! Please report to the other device",
					        			"Success", JOptionPane.INFORMATION_MESSAGE);
								showHomeScreen();
							}
							else {
								alertError("Authentication failed! Please report to the other device");
							}
						} catch (IOException e) {
							logger.severe("Failed to read/write to io stream. Abort transfer protocol.");
						}
						finally {
							connection.close();
						}
					}
				};
				captureChannel.setOOBMessageHandler(messageHandler);
				LineReaderWriter.println(out, READY);
				captureChannel.capture();
			} catch (IOException e) {
				logger.log(Level.SEVERE, "Failed to read/write from io stream. Abort transfer protocol.", e);
			}
		}
	}
	
	/*
	 * Runs an input protocol over a secure out-of-band channel.
	 * Key verification will be handled afterwards through a MANA III variant
	 * in the HostProtocolHandler class.
	 * 
	 * Legend:
	 * I	initiator
	 * R	responder
	 * H	human user
	 * --->	insecure channel (bluetooth)
	 * o->o secure channel, both authentic and confidential (out-of-band)
	 * 
	 * Protocol:
	 * I    ---  "INPUT:<channel_id>"   -->    R
	 *      o<-   s    --o H o--   s    ->o
	 *      ---         "DONE"          -->
	 *      <--         "DONE"          ---
	 * 
	 */
	private void inputProtocol(boolean isInitiator, final RemoteConnection connection, OOBChannel channel) {
		this.isInitiator = false; // reset, so we are ready for further pairing attempts.
		final String READY	= "READY";
		final String DONE	= "DONE";
		
		final InputStream in;
		final OutputStream out;
		try {
			in = connection.getInputStream();
			out = connection.getOutputStream();
		} catch (IOException e) {
			logger.log(Level.SEVERE, "Failed to open stream from connection. Abort input protocol.", e);
			return;
		}
		
		if (isInitiator) {
			logger.finer("Running input as initiator");
			try {
				String initString = UACAPProtocolConstants.INPUT + ":" + channel.toString();
				LineReaderWriter.println(out, initString);
				String lineIn = LineReaderWriter.readLine(in);
				if (!lineIn.equals(READY)) {
					logger.severe("Unexpected protocol string from remote device. Abort input protocol.");
					return;
				}
				OOBMessageHandler messageHandler = new OOBMessageHandler() {
					@Override
					public void handleOOBMessage(int channelType, byte[] data) {
						int length = (ButtonChannel.MESSAGE_LENGTH + 7) / 8;
						Vector sharedSecrets = new Vector();
						for (int i = 0; i < data.length; i += length) {
							byte[] temp = new byte[length];
							System.arraycopy(data, i, temp, 0, length);
							sharedSecrets.add(temp);
							if (logger.isLoggable(Level.FINER)) {
								logger.finer("Candidate secret: " + new String(Hex.encodeHex(temp)));
							}
						}
						try {
							LineReaderWriter.println(out, DONE);
							String line = LineReaderWriter.readLine(in);
							if (!line.equals(DONE)) {
								logger.severe("Unexpected protocol string from remote device. Abort input protocol.");
								return;
							}
							connection.close();
							BluetoothRFCOMMChannel btChannel = new BluetoothRFCOMMChannel(currentPeerUrl);
							try {
								btChannel.open();
							} catch (Exception be) {
								try {
									// retry after a second
									Thread.sleep(1000);
									btChannel.open();
								} catch (Exception e) {
									logger.log(Level.SEVERE, "Failed to open bluetooth channel. Abort input protocol.", e);
									return;
								}
							}
							
							HostProtocolHandler.startAuthenticationWith(btChannel, BedaApp.this, null, sharedSecrets, null, 20000, false, "INPUT", false);
							statusLabel.setText("Please wait... Authentication in progress...");
						} catch (IOException e) {
							logger.log(Level.SEVERE, "Failed to read/write from io stream. Abort input protocol.", e);
						}
					}
				};
				channel.setOOBMessageHandler(messageHandler);
				channel.capture();
			} catch (IOException e) {
				logger.log(Level.SEVERE, "Failed to read/write from io stream. Abort input protocol.", e);
			}
		}
		else { // responder
			logger.finer("Running input as responder");
			try {
				String lineIn = LineReaderWriter.readLine(in);
				String protocolDesc = lineIn.substring(0, lineIn.indexOf(':'));
				String channelDesc = lineIn.substring(lineIn.indexOf(':') + 1);
				if (!protocolDesc.equals(UACAPProtocolConstants.INPUT)) {
					logger.severe("Wrong protocol descriptor from remote device. Abort input protocol.");
					return;
				}
				// get appropriate channel
				OOBChannel captureChannel = buttonChannels.get(channelDesc);
				OOBMessageHandler messageHandler = new OOBMessageHandler() {
					@Override
					public void handleOOBMessage(int channelType, byte[] data) {
						int length = (ButtonChannel.MESSAGE_LENGTH + 7) / 8;
						Vector sharedSecrets = new Vector();
						for (int i = 0; i < data.length; i += length) {
							byte[] temp = new byte[length];
							System.arraycopy(data, i, temp, 0, length);
							sharedSecrets.add(temp);
							if (logger.isLoggable(Level.FINER)) {
								logger.finer("Candidate secret: " + new String(Hex.encodeHex(temp)));
							}
						}
						try {
							String line = LineReaderWriter.readLine(in);
							if (!line.equals(DONE)) {
								logger.severe("Unexpected protocol string from remote device. Abort input protocol.");
								return;
							}
							// prepare server to handle incoming request
							btServer.setPresharedShortSecrets(sharedSecrets);
							LineReaderWriter.println(out, DONE);
							//connection.close();
							statusLabel.setText("Please wait... Authentication in progress...");
						} catch (IOException e) {
							logger.log(Level.SEVERE, "Failed to read/write from io stream. Abort input protocol.", e);
						}
					}
				};
				captureChannel.setOOBMessageHandler(messageHandler);
				captureChannel.capture();
				LineReaderWriter.println(out, READY);
			} catch (IOException e) {
				logger.log(Level.SEVERE, "Failed to read/write from io stream. Abort input protocol.", e);
			}
		}
	}
	
}
