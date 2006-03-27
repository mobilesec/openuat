/* Copyright Rene Mayrhofer
 * File created 2006-03-27
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.apps;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.Socket;

import org.apache.commons.codec.binary.Hex;
import org.apache.log4j.Logger;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Button;
import org.eu.mayrhofer.authentication.exceptions.ConfigurationErrorException;
import org.eu.mayrhofer.authentication.exceptions.InternalApplicationException;
import org.eu.mayrhofer.channel.IPSecConnection;
import org.eu.mayrhofer.channel.IPSecConnection_Factory;

import uk.ac.lancs.relate.core.DongleException;

/** @author Rene Mayrhofer
 * @version 1.0
 */
public class IPSecConnectorClient extends IPSecConnectorCommon {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(IPSecConnectorClient.class);

	private Shell sShell = null;  //  @jve:decl-index=0:visual-constraint="4,11"
	private ProgressBar certificateProgress = null;
	private Label label1 = null;
	private Label gatewayLabel = null;
	private Label label4 = null;
	private Label commonNameLabel = null;
	private Label label5 = null;
	private Button startButton = null;
	private Label label2 = null;
	private Label caDnLabel = null;
	private Label label6 = null;
	private Label remoteNetworkLabel = null;
	private Label label3 = null;
	private Label validityLabel = null;
	private Button cancelButton = null;
	
	/** This represents the configuration of the IPSec tunnel. It is used
	 * to parse the XML-encoded config block received from the admin end.
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
		// TODO Auto-generated method stub
		/* Before this is run, be sure to set up the launch configuration (Arguments->VM Arguments)
		 * for the correct SWT library path in order to run with the SWT dlls. 
		 * The dlls are located in the SWT plugin jar.  
		 * For example, on Windows the Eclipse SWT 3.1 plugin jar is:
		 *       installation_directory\plugins\org.eclipse.swt.win32_3.1.0.jar
		 */
		Display display = Display.getDefault();
		IPSecConnectorClient thisClass = new IPSecConnectorClient("/dev/ttyUSB0");
		thisClass.sShell.open();

		while (!thisClass.sShell.isDisposed()) {
			if (!display.readAndDispatch())
				display.sleep();
		}
		display.dispose();
	}
	
	public IPSecConnectorClient(String serialPort) throws DongleException, ConfigurationErrorException, InternalApplicationException, IOException {
		super(true, serialPort);
		createSShell();
	}

	/**
	 * This method initializes sShell
	 */
	private void createSShell() {
		sShell = new Shell();
		sShell.setText("IPSec Connector Client");
		sShell.setSize(new org.eclipse.swt.graphics.Point(249,360));
		certificateProgress = new ProgressBar(sShell, SWT.NONE);
		certificateProgress.setBounds(new org.eclipse.swt.graphics.Rectangle(5,238,217,30));
		label1 = new Label(sShell, SWT.NONE);
		label1.setBounds(new org.eclipse.swt.graphics.Rectangle(8,8,89,18));
		label1.setText("IPSec gateway");
		gatewayLabel = new Label(sShell, SWT.NONE);
		gatewayLabel.setBounds(new org.eclipse.swt.graphics.Rectangle(123,8,81,20));
		gatewayLabel.setText("0.0.0.0");
		label4 = new Label(sShell, SWT.NONE);
		label4.setBounds(new org.eclipse.swt.graphics.Rectangle(9,113,171,19));
		label4.setText("Common name for certificate");
		commonNameLabel = new Label(sShell, SWT.NONE);
		commonNameLabel.setBounds(new org.eclipse.swt.graphics.Rectangle(7,138,223,24));
		label5 = new Label(sShell, SWT.NONE);
		label5.setBounds(new org.eclipse.swt.graphics.Rectangle(6,277,201,17));
		label5.setText("Authenticating admin");
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
		validityLabel = new Label(sShell, SWT.NONE);
		validityLabel.setBounds(new org.eclipse.swt.graphics.Rectangle(121,89,53,20));
		cancelButton = new Button(sShell, SWT.NONE);
		cancelButton.setBounds(new org.eclipse.swt.graphics.Rectangle(163,170,62,30));
		cancelButton.setText("Cancel");
	}
	
	public void AuthenticationSuccess(Object sender, Object remote, Object result) {
		Object[] remoteParam = (Object[]) remote;
		logger.info("Received relate authentication success event with " + remoteParam[0] + "/" + remoteParam[1]);
		System.out.println("SUCCESS");
		
		// since we use RelateAuthenticationProtocol with keepSocketConnected=true, ...
		byte[] sharedKey = (byte[]) ((Object[] ) result)[0];
		Socket toRemote = (Socket) ((Object[] ) result)[1];
		
		byte[] certificate = null;
		// open our "binary block" channel to the server
		BinaryBlockStreamer s = null;
		try {
			s = new BinaryBlockStreamer(toRemote.getInputStream(), null);
		}
		catch (IOException e) {
			logger.error("Could not open input stream to remote host: " + e);
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
			// first receive the configuration for the tunnel
			ByteArrayOutputStream confBlock = new ByteArrayOutputStream();
			StringBuffer confName = new StringBuffer();
			int recvSize = s.receiveBinaryBlock(confName, confBlock); 
			if (recvSize <= 0) {
				logger.error("Unable to receive configuration block from admin");
				// TODO: display error message
				toRemote.close();
				return;
			}
			if (!confName.equals(BLOCKNAME_CONFIG)) {
				logger.error("Binary block name is '" + confName + 
						"' instead of the expected '" + BLOCKNAME_CONFIG +
						". Is the admin application running on the other end?");
				// TODO: display error message
				toRemote.close();
				return;
			}
			logger.debug("Received configuration block from admin (" + recvSize + "B), parsing now");
			if (!config.parseConfig(new StringReader(confBlock.toString()))) {
				logger.error("Could not parse IPSec configuration from XML");
				// TODO: display error message
				toRemote.close();
				return;
			}
			// also update the GUI
			gatewayLabel.setText(config.getGateway());
			remoteNetworkLabel.setText(config.getRemoteNetwork());
			
			// and now the certificate
			ByteArrayOutputStream certBlock = new ByteArrayOutputStream();
			StringBuffer certName = new StringBuffer();
			recvSize = s.receiveBinaryBlock(certName, certBlock); 
			if (recvSize <= 0) {
				logger.error("Unable to receive certificate block from admin");
				// TODO: display error message
				toRemote.close();
				return;
			}
			if (!certName.equals(BLOCKNAME_CONFIG)) {
				logger.error("Binary block name is '" + certName + 
						"' instead of the expected '" + BLOCKNAME_CERTIFICATE +
						". Is the admin application running on the other end?");
				// TODO: display error message
				toRemote.close();
				return;
			}
			logger.debug("Received certificate from admin (" + recvSize + "B)");
			certificate = certBlock.toByteArray();
		} catch (IOException e) {
			logger.error("Could not read from remote host: " + e);
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

		// create a new temporary file for the certificate
		File tempCertFile = null;
		try {
			tempCertFile = File.createTempFile("newCert-", ".p12");
			tempCertFile.deleteOnExit();
			new FileOutputStream(tempCertFile).write(certificate);
		}
		catch (IOException e) {
			logger.error("Unable to create or write to temporary file for certificate: " + e);
			// TODO: display error message
			return;
		}

		// and import into the registry (overwriting existing certificates)
		IPSecConnection conn = IPSecConnection_Factory.getImplementation();
		conn.importCertificate(tempCertFile.getAbsolutePath(), 
				new String(Hex.encodeHex(sharedKey)), true);
		
		// finally, everything is in place, start the IPSec connection
		conn.init(config.getGateway(), config.getRemoteNetwork(), config.getRemoteNetmask());
		// TODO: fetch the CA distinguished name from the certificate and use it here
		conn.start((String) null, true);
	}
}
