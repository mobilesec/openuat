/* Copyright Rene Mayrhofer
 * File created 2006-03-20
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.apps;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringWriter;
import java.net.Socket;
import java.net.UnknownHostException;

import org.apache.commons.codec.binary.Hex;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Spinner;
import org.eu.mayrhofer.authentication.exceptions.ConfigurationErrorException;
import org.eu.mayrhofer.authentication.exceptions.InternalApplicationException;
import org.eu.mayrhofer.channel.X509CertificateGenerator;

import uk.ac.lancs.relate.core.Configuration;
import uk.ac.lancs.relate.core.DeviceException;
import uk.ac.lancs.relate.core.MeasurementManager;
import uk.ac.lancs.relate.filter.FilterInvalid;
import uk.ac.lancs.relate.filter.FilterList;
import uk.ac.lancs.relate.filter.FilterTransducerNo;
import uk.ac.lancs.relate.filter.KalmanFilter;
import uk.ac.lancs.relate.model.Model;
import uk.ac.lancs.relate.apps.SetupHelper;
import uk.ac.lancs.relate.apps.SimpleShowDemo;

/** @author Rene Mayrhofer
 * @version 1.0
 */
public class IPSecConnectorAdmin extends IPSecConnectorCommon {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(IPSecConnectorAdmin.class);

	private Shell adminShell = null;  //  @jve:decl-index=0:visual-constraint="4,11"
	private ProgressBar certificateProgress = null;
	private Label label = null;
	private Label label1 = null;
	private Label gatewayLabel = null;
	private Label label4 = null;
	private Label label5 = null;
	private Text commonNameInput = null;
	private Button startButton = null;
	private Label label2 = null;
	private Label caDnLabel = null;
	private Label label6 = null;
	private Label remoteNetworkLabel = null;
	private Label label3 = null;
	private Spinner validityInput = null;
	private Button cancelButton = null;
	
	/** This string holds the temporary file name of the certificate that
	 * has been created. It is also used as a state variable for synchronizing
	 * with the background thread that is creating it: if it is set to null,
	 * then no thread is running, if it is set to the empty string "", then a 
	 * thread is currently running, and if it is set to a non-empty string, the
	 * thread has finished creating the certificate.
	 * 
	 * @see #asyncCreateCertificate
	 */
	private String certificateFilename = null;
	/** This object is just used for synchronizing access to the 
	 * certificateFilename object.
	 * @see #certificateFilename
	 */
	private Object certificateFilenameLock = new Object();
	
	/** This is the X.509 certificate generator used to create new certificates.
	 * It is initialized in the constructor.
	 * 
	 * @see #asyncCreateCertificate
	 */
	private X509CertificateGenerator certGenerator;
	
	/** This represents the configuration of the IPSec tunnel. It is initialized
	 * in the constructor by loading a configuration file and is used in the
	 * authentication success handler to generate the XML config block to 
	 * transmit to the client.
	 */
	private IPSecConfigHandler config;
	
	/** The shared key as agreed by the spatial authentication protocol. Is is set
	 * in the authentication success events and used in issueCertificate.
	 */
	private byte[] sharedKey = null;
	
	/** Remembers the TCP socket to the remote host, as passed in the authentication
	 * success message.
	 */
	private Socket toRemote = null;
	
	private class AuthenticationEventsHandler extends SimpleShowDemo {
		public AuthenticationEventsHandler(Shell shell,  Configuration config, 
				MeasurementManager manager, Model model) {
			// force the GUI to display the authentication menu entry even if it does not locally use it
			super(shell, config, manager, model, false, true);
		}

		protected void authenticationStarted(String serialPort, String remoteHost, int remoteRelateId, byte numRounds) {
			// this just emits a log line right now
			super.authenticationStarted(serialPort, remoteHost, remoteRelateId, numRounds);
			
			// but start our authentication here
			try {
				auth.startAuthentication(remoteHost, (byte) remoteRelateId, numRounds);

				// authentication started, so switch to new window
				shell.close();
				shell.dispose();
				
				adminShell.open();
			} catch (UnknownHostException e) {
				logger.error("Unable to start spatial authentication: " + e);
				e.printStackTrace();
			} catch (IOException e) {
				logger.error("Unable to start spatial authentication: " + e);
				e.printStackTrace();
			}
		}		

		public void success(String serialPort, String remoteHost, int remoteRelateId, byte numRounds, byte[] sharedSecret,
				Socket socketToRemote) {
			super.success(serialPort, remoteHost, remoteRelateId, numRounds, sharedSecret, socketToRemote);
			// remember the shared key and the socket
			sharedKey = sharedSecret;
			toRemote = socketToRemote;

			// authenticated, so now enable the start button
			startButton.setEnabled(true);
		}

		public void progress(String serialPort, String remoteHost, int remoteRelateId, int cur, int max, String msg) {
			// forward to update the progess bar
			AuthenticationProgress(serialPort, remoteHost + "/" + remoteRelateId, cur, max, msg);
		}
		
		boolean isDisposed() {
			return shell.isDisposed();
		}
	}

	/**
	 * @param args
	 * @throws IOException 
	 * @throws InternalApplicationException 
	 * @throws ConfigurationErrorException 
	 * @throws DongleException 
	 */
	public static void main(String[] args) throws DeviceException, ConfigurationErrorException, InternalApplicationException, IOException {
		// TODO: hard-coding is not nice...
		String serialPort = null, caFile = null, confFile = null;
		if (System.getProperty("os.name").startsWith("Windows CE")) {
			serialPort = "COM8:";
			caFile = "\\relate\\ca-ipsec.p12";
			confFile = "\\relate\\ipsec-conf.xml";
		}
		else {
			serialPort = "/dev/ttyUSB0";
			caFile = "ca.p12";
			confFile = "ipsec-conf.xml";
		}

		if (System.getProperty("os.name").startsWith("Windows CE")) {
			System.out.println("Configuring log4j");
			PropertyConfigurator.configure("log4j.properties");
		}
		
		// if we have an IP address as argument, then start in simulation mode
		Configuration relateConf = null;
		SetupHelper helper = null;
		if (args.length > 0)  {
			serialPort = null;
		}
		else {
			logger.info("Initializing with serial port " + serialPort);
		    relateConf = new Configuration(serialPort);
		    relateConf.setSide("back");

	        helper = new SetupHelper(relateConf);
	        helper.getSerialConnector().setHostInfo(relateConf.getHostInfo());
			//create filter list and add filters to the measurement manager
	        FilterList filters = new FilterList();
	        filters.addFilter(new FilterInvalid());
	        filters.addFilter(new FilterTransducerNo(2));
	        //filters.addFilter(new FilterOutlierDistance());
	        filters.addFilter(new KalmanFilter());
	        filters.addFilter(new FilterInvalid());
	        helper.getMeasurementManager().setFilterList(filters);

	        // TODO: check if we need these
            //helper.getHostInfoManager();
            //helper.getMDNSDiscovery();
		}

		IPSecConnectorAdmin thisClass = new IPSecConnectorAdmin(relateConf, 
				caFile, "test password", "Test CA", confFile, Display.getDefault());
		
		// test code, only simulating
		AuthenticationEventsHandler selectionGui = null;
		if (args.length > 0) {
			logger.info("Simulating authentication with host " + args[0]);
			thisClass.adminShell.open();
			try {
				// simulation, so just start the authentication
				thisClass.auth.startAuthentication(args[0], (byte) 0, 2);
			}
			catch (UnknownHostException ee) {
				// TODO: display error message
				logger.error("Could not start authentication: " + ee);
			} catch (IOException ee) {
				// TODO: display error message
				logger.error("Could not start authentication: " + ee);
			}
		}
		else {
			logger.info("Starting selection GUI normally");
			// when not testing, use the SimpleShowDemo instead
			Shell shell = new Shell(thisClass.display);
			// this opens the window, and the authentication is started by right-click
			selectionGui = thisClass.new AuthenticationEventsHandler(shell, relateConf, 
					helper.getMeasurementManager(), helper.getModel());
			selectionGui.open();
		}

		logger.info("Initialization complete, waiting for main window to be closed");
		while (!thisClass.adminShell.isDisposed() || (selectionGui != null && !selectionGui.isDisposed())) {
			if (!thisClass.display.readAndDispatch())
				thisClass.display.sleep();
		}
		thisClass.display.dispose();
		
		// clean up properly
		helper.getSerialConnector().stop();
		helper.getSerialConnector().dispose();
		
		logger.info("Application exiting now.");
		if (!System.getProperty("os.name").startsWith("Windows CE")/* || !logger.isDebugEnabled()*/) {
			// for Windows CE, don't call System.exit when debugging because then we wouldn't see the log output
			System.exit(0);
		}
	}
	
	public IPSecConnectorAdmin(Configuration relateConf, String caFile, String caPassword, String caAlias, 
			String configFilename, Display display) throws DeviceException, ConfigurationErrorException, InternalApplicationException, IOException {
		super(true, relateConf);
		logger.info("Initializing IPSecConnectorAdmin");
		this.display = display;
		
		// also initialize the certificate generator
		try {
			logger.debug("Initializing certificate authority from " + caFile + "(alias " + caAlias + ")");
			certGenerator = new X509CertificateGenerator(caFile, caPassword, caAlias, true);
		}
		catch (Exception e) {
			logger.error("Could not create X.509 certificate generator: " + e);
			// TODO: display an error message and abort
			if (! System.getProperty("os.name").startsWith("Windows CE")) {
				System.exit(1);
			}
		}

		// the the config block
		logger.info("Reading configuration from " + configFilename);
		config = new IPSecConfigHandler();
		if (!config.parseConfig(new FileReader(configFilename))) {
			logger.error("Could not load IPSec configuration from " + configFilename);
			// TODO: display an error message and abort
			if (! System.getProperty("os.name").startsWith("Windows CE")) {
				System.exit(2);
			}
		}

		// and if the CA DN is not pre-set, fetch it from the CA
		if (config.getCaDistinguishedName() == null) {
			config.setCaDistinguishedName(certGenerator.getCaDistinguishedName());
			logger.info("Set CA distinguished name from loaded CA: '" + config.getCaDistinguishedName() + "'");
		}
		else {
			logger.info("Using pre-set CA distinguished name: '" + config.getCaDistinguishedName() + "'");
		}

		// and finally create the shell (with all information now available)
		logger.info("Creating shell for admin window");
		createSShell();
	}

	/**
	 * This method initializes adminShell
	 */
	private void createSShell() {
		adminShell = new Shell(display);
		adminShell.setText("IPSec Connector Admin");
		adminShell.setSize(new org.eclipse.swt.graphics.Point(249,360));
		label1 = new Label(adminShell, SWT.NONE);
		label1.setBounds(new org.eclipse.swt.graphics.Rectangle(4,4,107,17));
		label1.setText("IPSec gateway");
		gatewayLabel = new Label(adminShell, SWT.NONE);
		gatewayLabel.setBounds(new org.eclipse.swt.graphics.Rectangle(120,4,113,18));
		gatewayLabel.setText(config.getGateway());
		label6 = new Label(adminShell, SWT.NONE);
		label6.setBounds(new org.eclipse.swt.graphics.Rectangle(4,24,107,17));
		label6.setText("Remote network");
		remoteNetworkLabel = new Label(adminShell, SWT.NONE);
		remoteNetworkLabel.setBounds(new org.eclipse.swt.graphics.Rectangle(120,24,116,17));
		remoteNetworkLabel.setText(config.getRemoteNetwork() + "/" + config.getRemoteNetmask());
		label2 = new Label(adminShell, SWT.NONE);
		label2.setBounds(new org.eclipse.swt.graphics.Rectangle(4,44,228,17));
		label2.setText("Certificate authority");
		caDnLabel = new Label(adminShell, SWT.NONE);
		caDnLabel.setBounds(new org.eclipse.swt.graphics.Rectangle(4,61,225,17));
		caDnLabel.setText(config.getCaDistinguishedName());

		label3 = new Label(adminShell, SWT.NONE);
		label3.setBounds(new org.eclipse.swt.graphics.Rectangle(4,80,105,17));
		label3.setText("Validity in days");
		validityInput = new Spinner(adminShell, SWT.NONE);
		validityInput.setMaximum(365);
		validityInput.setSelection(30);
		validityInput.setBounds(new org.eclipse.swt.graphics.Rectangle(120,80,40,20));
		label4 = new Label(adminShell, SWT.NONE);
		label4.setText("Common name for certificate");
		label4.setBounds(new org.eclipse.swt.graphics.Rectangle(4,102,171,17));
		commonNameInput = new Text(adminShell, SWT.BORDER);
		commonNameInput.setBounds(new org.eclipse.swt.graphics.Rectangle(4,120,226,20));

		startButton = new Button(adminShell, SWT.NONE);
		startButton.setBounds(new org.eclipse.swt.graphics.Rectangle(4,145,130,28));
		startButton.setText("Issue certificate");
		startButton.setEnabled(false);
		startButton.addSelectionListener(new org.eclipse.swt.events.SelectionAdapter() {
			public void widgetSelected(org.eclipse.swt.events.SelectionEvent e) {
				issueCertificate();
			}
		});
		cancelButton = new Button(adminShell, SWT.NONE);
		cancelButton.setBounds(new org.eclipse.swt.graphics.Rectangle(165,145,65,28));
		cancelButton.setText("Cancel");
		cancelButton
				.addSelectionListener(new org.eclipse.swt.events.SelectionAdapter() {
					public void widgetSelected(org.eclipse.swt.events.SelectionEvent e) {
						adminShell.close();
					}
				});
		
		label5 = new Label(adminShell, SWT.NONE);
		label5.setBounds(new org.eclipse.swt.graphics.Rectangle(4,176,201,17));
		label5.setText("Authenticating client");
		authenticationProgress = new ProgressBar(adminShell, SWT.NONE);
		authenticationProgress.setBounds(new org.eclipse.swt.graphics.Rectangle(4,195,226,20));
		label = new Label(adminShell, SWT.NONE);
		label.setBounds(new org.eclipse.swt.graphics.Rectangle(4,217,198,17));
		label.setText("Creating X.509 certificate");
		certificateProgress = new ProgressBar(adminShell, SWT.NONE);
		certificateProgress.setBounds(new org.eclipse.swt.graphics.Rectangle(4,236,226,20));
	}
	
	/** This method encapsulates the creation of a X.509 certificate in a background
	 * thread. It will fire off a thread, which will then post its result to the
	 * certificateFilename member.
	 * 
	 * If the certificate generation failed after starting the background thread, the
	 * certificateFilename member will contain the string "ERROR", optionally followed
	 * by an exception converted to a string that caused the abort.
	 * On success, the certificateFilename member will contain the name of a temporary
	 * file with the newly created certificate.
	 * In both cases, certificateFilename.notify() will be called after modifying it,
	 * so that other threads can wait for it to be modified.
	 * 
	 * @param commonName The common name to use for the DN field of the certificate.
	 * @param validityDays The number of days this certificate should be valid.
	 * @param exportPassword The password to export the certificate and the matching
	 *                       private key with.
	 * @return true if the thread was started successfully, false otherwise.
	 * 
	 * @see #certificateFilename
	 */
	protected boolean asyncCreateCertificate(String commonName, int validityDays, String exportPassword) {
		logger.debug("Starting thread to create certificate for CN='" + 
				commonName + "' valid for " + validityDays + " days");
		synchronized (certificateFilenameLock) {
			// this states that it is not yet ready, and that the thread is running		
			certificateFilename = "";
			certificateFilenameLock.notify();
		}

		// create a new temporary file for the certificate
		File tempCertFile = null;
		try {
			tempCertFile = File.createTempFile("newCert-", ".p12");
		}
		catch (IOException e) {
			logger.error("Unable to create temporary file for certificate: " + e);
			// TODO: display error message
			return false;
		}
		tempCertFile.deleteOnExit();
		logger.debug("Created temporary file '" + tempCertFile.getAbsolutePath() + "'");

		final String cn = commonName;
		final int val = validityDays;
		final String file = tempCertFile.getAbsolutePath();
		final String pass = exportPassword;
		// and start the certificate generation in the background
		new Thread(new Runnable() { public void run() {
			logger.debug("Certificate creation thread started");
			try {
				if (certGenerator.createCertificate(cn, val, file, pass)) {
					logger.debug("Finished creating certificate with success");
					// ok, finished creating the file - store the name and wake up other threads that might be waiting
					synchronized (certificateFilenameLock) {
						certificateFilename = file;
						certificateFilenameLock.notify();
					}
				}
				else {
					logger.error("Finished creating certificate with error");
					// error during creating
					synchronized (certificateFilenameLock) {
						certificateFilename = "ERROR";
						certificateFilenameLock.notify();
					}
				}
			}
			catch (Exception e) {
				logger.error("Certificate generation failed with: " + e);
				synchronized (certificateFilenameLock) {
					certificateFilename = "ERROR: " + e;
					certificateFilenameLock.notify();
				}
			}
		} }).start();
		return true;
	}

	public void AuthenticationSuccess(Object sender, Object remote, Object result) {
		Object[] remoteParam = (Object[]) remote;
		logger.info("Received relate authentication success event with " + remoteParam[0] + "/" + remoteParam[1]);
		System.out.println("SUCCESS");
		
		// since we use RelateAuthenticationProtocol with keepSocketConnected=true, ...
		sharedKey = (byte[]) ((Object[] ) result)[0];
		toRemote = (Socket) ((Object[] ) result)[1];

		// authenticated, so now enable the start button
		startButton.setEnabled(true);
	}
	
	public void issueCertificate() {
		// ok, got the shared password - use it to create the certificate (in the background)
		// (and need to get the text fields in the SWT UI thread) 
		display.syncExec(new Runnable() { public void run() { 
			asyncCreateCertificate(commonNameInput.getText(), validityInput.getSelection(), 
					new String(Hex.encodeHex(sharedKey)));
		}});

		// first of all, wait for the certificate to be generated (if not already)
		synchronized (certificateFilenameLock) {
			if (certificateFilename == null) {
				// hmm, thread not started yet - can't cope
				logger.error("Certificate generation thread was not started properly, can not continue");
				// TODO: display error message
				try {
					toRemote.close();
				}
				catch (IOException e) {
					logger.warn("Could not close socket to remote host, ignoring");
				}
				return;
			} 
			else {
				if (certificateFilename.equals("")) {
					// ok, thread still running, wait for it to end
					try {
						certificateFilenameLock.wait();
					}
					catch (InterruptedException e) {
					// just ignore that
					}
				}
				// here, the thread must have finished already
				if (certificateFilename.startsWith("ERROR")) {
					// ouch, generation failed
					logger.error("Certificate generation failed: " + certificateFilename);
					// TODO: display error message
					try {
						toRemote.close();
					}
					catch (IOException e) {
						logger.warn("Could not close socket to remote host, ignoring");
					}
					return;
				}
			}
		}
		// if we come till here, certificateFilename contains the path to a new certificate

		// open our "binary block" channel to the client
		BinaryBlockStreamer s = null;
		try {
			s = new BinaryBlockStreamer(null, toRemote.getOutputStream());
		}
		catch (IOException e) {
			logger.error("Could not open output stream to remote host: " + e);
			// TODO: display error message
			try {
				toRemote.close();
			}
			catch (IOException e1) {
				logger.warn("Could not close socket to remote host, ignoring");
			}
			return;
		}
		try {
			// first transmit the configuration for the tunnel
			StringWriter confBlock = new StringWriter();
			if (!config.writeConfig(confBlock)) {
				logger.error("Could not export IPSec configuration to XML");
				// TODO: display error message
				toRemote.close();
				return;
			}
			String confTmpBlock = confBlock.toString();
			logger.debug("Sending configuration block of " + confTmpBlock.length() + "B to client");
			s.sendBinaryBlock(BLOCKNAME_CONFIG, new ByteArrayInputStream(confTmpBlock.getBytes()), confTmpBlock.length());
			
			// and now the certificate
			File certFile = new File(certificateFilename);
			logger.debug("Sending certificate block of " + certFile.length() + "B to client");
			s.sendBinaryBlock(BLOCKNAME_CERTIFICATE, new FileInputStream(certFile), (int) certFile.length());
		} catch (IOException e) {
			logger.error("Could not send to remote host: " + e);
			// TODO: display error message
			return;
		}
		finally {
			// and be sure to close the socket properly
			try {
				toRemote.close();
			}
			catch (IOException e) {
				logger.warn("Could not close socket to remote host, ignoring");
			}
		}
	}
}