/* Copyright Rene Mayrhofer
 * File created 2006-03-20
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
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

import org.apache.commons.codec.binary.Hex;
import org.apache.log4j.Logger;
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

import uk.ac.lancs.relate.core.DongleException;

/** @author Rene Mayrhofer
 * @version 1.0
 */
public class IPSecConnectorAdmin extends IPSecConnectorCommon {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(IPSecConnectorAdmin.class);

	private Shell sShell = null;  //  @jve:decl-index=0:visual-constraint="4,11"
	private ProgressBar certificateProgress = null;
	private Label label = null;
	private Label label1 = null;
	private Label gatewayLabel = null;
	private Label label4 = null;
	private Text commonNameInput = null;
	private Label label5 = null;
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

	/**
	 * @param args
	 * @throws IOException 
	 * @throws InternalApplicationException 
	 * @throws ConfigurationErrorException 
	 * @throws DongleException 
	 */
	public static void main(String[] args) throws DongleException, ConfigurationErrorException, InternalApplicationException, IOException {
		/* Before this is run, be sure to set up the launch configuration (Arguments->VM Arguments)
		 * for the correct SWT library path in order to run with the SWT dlls. 
		 * The dlls are located in the SWT plugin jar.  
		 * For example, on Windows the Eclipse SWT 3.1 plugin jar is:
		 *       installation_directory\plugins\org.eclipse.swt.win32_3.1.0.jar
		 */
		Display display = Display.getDefault();
		// TODO: hard-coding is not nice...
		IPSecConnectorAdmin thisClass = new IPSecConnectorAdmin(null, //"/dev/ttyUSB0", 
				"ca.p12", "test password", "Test CA", "ipsec-conf.xml");
		thisClass.sShell.open();
		
		// test code
		if (args.length > 0)
			thisClass.auth.startAuthentication(args[0], (byte) 0, 2);

		while (!thisClass.sShell.isDisposed()) {
			if (!display.readAndDispatch())
				display.sleep();
		}
		display.dispose();
	}
	
	public IPSecConnectorAdmin(String serialPort, String caFile, String caPassword, String caAlias, 
			String configFilename) throws DongleException, ConfigurationErrorException, InternalApplicationException, IOException {
		super(true, serialPort);
		createSShell();
		
		// also initialize the certificate generator
		try {
			certGenerator = new X509CertificateGenerator(caFile, caPassword, caAlias, true);
		}
		catch (Exception e) {
			logger.error("Could not create X.509 certificate generator: " + e);
			// TODO: display an error message and abort
			System.exit(1);
		}
		
		// the the config block
		config = new IPSecConfigHandler();
		if (!config.parseConfig(new FileReader(configFilename))) {
			logger.error("Could not load IPSec configuration from " + configFilename);
			// TODO: display an error message and abort
			System.exit(2);
		}
		
		// and if the CA DN is not pre-set, fetch it from the CA
		if (config.getCaDistinguishedName() == null) {
			config.setCaDistinguishedName(certGenerator.getCaDistinguishedName());
			logger.info("Set CA distinguished name from loaded CA: '" + config.getCaDistinguishedName() + "'");
		}
		else {
			logger.info("Using pre-set CA distinguished name: '" + config.getCaDistinguishedName() + "'");
		}
	}

	/**
	 * This method initializes sShell
	 */
	private void createSShell() {
		sShell = new Shell();
		sShell.setText("IPSec Connector Admin");
		sShell.setSize(new org.eclipse.swt.graphics.Point(249,360));
		certificateProgress = new ProgressBar(sShell, SWT.NONE);
		certificateProgress.setBounds(new org.eclipse.swt.graphics.Rectangle(5,238,217,30));
		label = new Label(sShell, SWT.NONE);
		label.setBounds(new org.eclipse.swt.graphics.Rectangle(4,215,198,18));
		label.setText("Creating X.509 certificate");
		label1 = new Label(sShell, SWT.NONE);
		label1.setBounds(new org.eclipse.swt.graphics.Rectangle(8,8,89,18));
		label1.setText("IPSec gateway");
		gatewayLabel = new Label(sShell, SWT.NONE);
		gatewayLabel.setBounds(new org.eclipse.swt.graphics.Rectangle(123,8,81,20));
		gatewayLabel.setText("0.0.0.0");
		label4 = new Label(sShell, SWT.NONE);
		label4.setBounds(new org.eclipse.swt.graphics.Rectangle(9,113,171,19));
		label4.setText("Common name for certificate");
		commonNameInput = new Text(sShell, SWT.BORDER);
		commonNameInput.setBounds(new org.eclipse.swt.graphics.Rectangle(7,138,223,24));
		label5 = new Label(sShell, SWT.NONE);
		label5.setBounds(new org.eclipse.swt.graphics.Rectangle(6,277,201,17));
		label5.setText("Authenticating client");
		authenticationProgress = new ProgressBar(sShell, SWT.NONE);
		authenticationProgress.setBounds(new org.eclipse.swt.graphics.Rectangle(6,297,219,31));
		startButton = new Button(sShell, SWT.NONE);
		startButton.setBounds(new org.eclipse.swt.graphics.Rectangle(6,169,69,28));
		startButton.setText("Start");
		label2 = new Label(sShell, SWT.NONE);
		label2.setBounds(new org.eclipse.swt.graphics.Rectangle(7,52,107,17));
		label2.setText("Certificate authority");
		caDnLabel = new Label(sShell, SWT.NONE);
		caDnLabel.setBounds(new org.eclipse.swt.graphics.Rectangle(121,52,116,17));
		caDnLabel.setText("O=Bla, CN=My CA");
		label6 = new Label(sShell, SWT.NONE);
		label6.setBounds(new org.eclipse.swt.graphics.Rectangle(8,31,90,17));
		label6.setText("Remote network");
		remoteNetworkLabel = new Label(sShell, SWT.NONE);
		remoteNetworkLabel.setBounds(new org.eclipse.swt.graphics.Rectangle(121,32,84,16));
		remoteNetworkLabel.setText("0.0.0.0/0");
		label3 = new Label(sShell, SWT.NONE);
		label3.setBounds(new org.eclipse.swt.graphics.Rectangle(8,90,105,17));
		label3.setText("Validity in days");
		validityInput = new Spinner(sShell, SWT.NONE);
		validityInput.setMaximum(365);
		validityInput.setSelection(30);
		validityInput.setBounds(new org.eclipse.swt.graphics.Rectangle(121,89,53,20));
		cancelButton = new Button(sShell, SWT.NONE);
		cancelButton.setBounds(new org.eclipse.swt.graphics.Rectangle(163,170,62,30));
		cancelButton.setText("Cancel");
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
		synchronized (certificateFilename) {
			// this states that it is not yet ready, and that the thread is running		
			certificateFilename = "";
			certificateFilename.notify();
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

		final String cn = commonName;
		final int val = validityDays;
		final String file = tempCertFile.getAbsolutePath();
		final String pass = exportPassword;
		// and start the certificate generation in the background
		new Thread(new Runnable() { public void run() {
			try {
				if (certGenerator.createCertificate(cn, val, file, pass)) {
					// ok, finished creating the file - store the name and wake up other threads that might be waiting
					synchronized (certificateFilename) {
						certificateFilename = file;
						certificateFilename.notify();
					}
				}
				else {
					// error during creating
					synchronized (certificateFilename) {
						certificateFilename = "ERROR";
						certificateFilename.notify();
					}
				}
			}
			catch (Exception e) {
				logger.error("Certificate generation failed with: " + e);
				synchronized (certificateFilename) {
					certificateFilename = "ERROR: " + e;
					certificateFilename.notify();
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
		byte[] sharedKey = (byte[]) ((Object[] ) result)[0];
		Socket toRemote = (Socket) ((Object[] ) result)[1];
		
		// ok, got the shared password - use it to create the certificate (in the background)
		asyncCreateCertificate(commonNameInput.getText(), validityInput.getSelection(), 
				new String(Hex.encodeHex(sharedKey)));

		// first of all, wait for the certificate to be generated (if not already)
		synchronized (certificateFilename) {
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
						certificateFilename.wait();
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
