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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;
import java.util.Vector;

import javax.bluetooth.RemoteDevice;
import javax.bluetooth.ServiceRecord;
import javax.bluetooth.UUID;
import javax.swing.DefaultListModel;
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
import org.openuat.util.BluetoothRFCOMMChannel;
import org.openuat.util.BluetoothRFCOMMServer;
import org.openuat.util.Hash;
import org.openuat.util.LineReaderWriter;
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
		random			= new Random(System.currentTimeMillis());
		devices			= new RemoteDevice[0];
		currentPeerUrl	= null;
		currentChannel	= null;
		isInitiator		= false;
				
		// Initialize the logger. Use a wrapper around the log4j framework.
		LogFactory.init(new Log4jFactory());
		logger = LogFactory.getLogger(BedaApp.class.getName());
		logger.debug("Logger initiated!");
		
		mainWindow = new JFrame("Beda App");
		mainWindow.setSize(new Dimension(FRAME_WIDTH, FRAME_HIGHT));
		mainWindow.setPreferredSize(new Dimension(FRAME_WIDTH, FRAME_HIGHT));
		mainWindow.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		mainWindow.getContentPane().setLayout(new FlowLayout());
		
		// prepare the button channels
		buttonChannels = new HashMap<String, OOBChannel>();
		ButtonChannelImpl impl = new AWTButtonChannelImpl(mainWindow.getContentPane());
		OOBChannel c = new ButtonToButtonChannel(impl);
		buttonChannels.put(c.toString(), c);
		c = new FlashDisplayToButtonChannel(impl);
		buttonChannels.put(c.toString(), c);
		c = new ProgressBarToButtonChannel(impl);
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
					if (source == channelList && channelList.isEnabled()) {
						try {
							currentChannel = (OOBChannel)channelList.getSelectedValue();
							if (currentChannel != null) {
								statusLabel.setText("Please wait... Prepare authentication");
								isInitiator = true;
								BluetoothRFCOMMChannel btChannel = new BluetoothRFCOMMChannel(currentPeerUrl);
								btChannel.open();
								if (channelList.getSelectedIndex() == 0) {
									// input case
									String hello = LineReaderWriter.readLine(btChannel.getInputStream());
									if (!hello.equals(HostProtocolHandler.Protocol_Hello)) {
										logger.warn("Got wrong greeting string from server. This probably leads to protocol failure.");
									}
									LineReaderWriter.println(btChannel.getOutputStream(), PRE_AUTH);
									inputProtocol(true, btChannel, currentChannel);
								}
								else {
									// transfer case
									boolean keepConnected = true; // since the key has to be authenticated after key agreement
									HostProtocolHandler.startAuthenticationWith(btChannel, BedaApp.this, -1, keepConnected, TRANSFER_AUTH, false);
								}
							}
						} catch (IOException e) {
							logger.error("Failed to start authentication.", e);
						}
					}
					else if (source == deviceList) {
						int index = deviceList.getSelectedIndex();
						if (index > -1) {
							String peerAddress = devices[index].getBluetoothAddress();
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
								// TODO: move this warning to a status bar or similar
								statusLabel.setText("Sorry, but the service " + SERVICE_NAME + " is not running on the selected device");
								/* JOptionPane.showMessageDialog(mainWindow,
										"The service " + SERVICE_NAME + " is not running on the selected device",
										"Error", JOptionPane.ERROR_MESSAGE); */
							}
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
					inputProtocol(false, remote, null);
					return true;
				}
				return false;
			}
		};
		btServer.addProtocolCommandHandler(PRE_AUTH, inputProtocolHandler);
		
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
		btServer.setPresharedShortSecret(null);
		logger.error(msg, e);
		alertError(msg);
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
	        	statusLabel.setText("");
	        	informSuccess();
	        }
	        else if (param.equals(TRANSFER_AUTH)) {
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
	
	/* Informs the user of an error and return to main screen */
	private void alertError(String msg) {
		JOptionPane.showMessageDialog(mainWindow, msg, "Error", JOptionPane.ERROR_MESSAGE);
		showHomeScreen();
	}
	
	/* Informs the user about the successfully completed authentication process */
	private void informSuccess() {
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
			logger.error("Failed to open stream from connection. Abort transfer protocol.", e);
			return;
		}
		
		if (isInitiator) {
			logger.debug("Running transfer as initiator");
			try {
				String initString = TRANSFER_AUTH + ":" + channel.toString();
				LineReaderWriter.println(out, initString);
				String lineIn = LineReaderWriter.readLine(in);
				if (!lineIn.equals(READY)) {
					logger.error("Unexpected protocol string from remote device. Abort transfer protocol.");
					return;
				}
				channel.transmit(oobMsg);
				// wait for other device
				lineIn = LineReaderWriter.readLine(in);
				if (!lineIn.equals(DONE)) {
					logger.error("Unexpected protocol string from remote device. Abort transfer protocol.");
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
				logger.error("Failed to read/write from io stream. Abort transfer protocol.");
			}
		}
		else { // responder
			logger.debug("Running transfer as responder");
			try {
				String lineIn = LineReaderWriter.readLine(in);
				String protocolDesc = lineIn.substring(0, lineIn.indexOf(':'));
				String channelDesc = lineIn.substring(lineIn.indexOf(':') + 1);
				if (!protocolDesc.equals(TRANSFER_AUTH)) {
					logger.error("Wrong protocol descriptor from remote device. Abort transfer protocol.");
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
							if (logger.isInfoEnabled()) {
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
							logger.error("Failed to read/write to io stream. Abort transfer protocol.");
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
				logger.error("Failed to read/write from io stream. Abort transfer protocol.", e);
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
			logger.error("Failed to open stream from connection. Abort input protocol.", e);
			return;
		}
		
		if (isInitiator) {
			logger.debug("Running input as initiator");
			try {
				String initString = INPUT + ":" + channel.toString();
				LineReaderWriter.println(out, initString);
				String lineIn = LineReaderWriter.readLine(in);
				if (!lineIn.equals(READY)) {
					logger.error("Unexpected protocol string from remote device. Abort input protocol.");
					return;
				}
				OOBMessageHandler messageHandler = new OOBMessageHandler() {
					@Override
					public void handleOOBMessage(int channelType, byte[] data) {
						logger.debug("Data captured: " + new String(Hex.encodeHex(data)));
						try {
							LineReaderWriter.println(out, DONE);
							String line = LineReaderWriter.readLine(in);
							if (!line.equals(DONE)) {
								logger.error("Unexpected protocol string from remote device. Abort input protocol.");
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
									logger.error("Failed to open bluetooth channel. Abort input protocol.", e);
									return;
								}
							}
							
							HostProtocolHandler.startAuthenticationWith(btChannel, BedaApp.this, null, data, null, 20000, false, "INPUT", false);
							//display.setCurrent(welcomeScreen);
							// TODO: please wait
							statusLabel.setText("Please wait... Authentication in progress...");
						} catch (IOException e) {
							logger.error("Failed to read/write from io stream. Abort input protocol.", e);
						}
					}
				};
				channel.setOOBMessageHandler(messageHandler);
				channel.capture();
			} catch (IOException e) {
				logger.error("Failed to read/write from io stream. Abort input protocol.", e);
			}
		}
		else { // responder
			logger.debug("Running input as responder");
			try {
				String lineIn = LineReaderWriter.readLine(in);
				String protocolDesc = lineIn.substring(0, lineIn.indexOf(':'));
				String channelDesc = lineIn.substring(lineIn.indexOf(':') + 1);
				if (!protocolDesc.equals(INPUT)) {
					logger.error("Wrong protocol descriptor from remote device. Abort input protocol.");
					return;
				}
				// get appropriate channel
				OOBChannel captureChannel = buttonChannels.get(channelDesc);
				OOBMessageHandler messageHandler = new OOBMessageHandler() {
					@Override
					public void handleOOBMessage(int channelType, byte[] data) {
						logger.debug("Data captured: " + new String(Hex.encodeHex(data)));
						try {
							String line = LineReaderWriter.readLine(in);
							if (!line.equals(DONE)) {
								logger.error("Unexpected protocol string from remote device. Abort input protocol.");
								return;
							}
							// prepare server to handle incoming request
							btServer.setPresharedShortSecret(data);
							LineReaderWriter.println(out, DONE);
							//connection.close();
							//display.setCurrent(welcomeScreen);
							// TODO: please wait
							statusLabel.setText("Please wait... Authentication in progress...");
						} catch (IOException e) {
							logger.error("Failed to read/write from io stream. Abort input protocol.", e);
						}
					}
				};
				captureChannel.setOOBMessageHandler(messageHandler);
				captureChannel.capture();
				LineReaderWriter.println(out, READY);
			} catch (IOException e) {
				logger.error("Failed to read/write from io stream. Abort input protocol.", e);
			}
		}
	}
}