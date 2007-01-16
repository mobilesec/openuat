/* Copyright Rene Mayrhofer
 * File created 2006-03-27
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.apps;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
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

import uk.ac.lancs.relate.apps.SetupHelper;
import uk.ac.lancs.relate.core.Configuration;
import uk.ac.lancs.relate.core.DeviceException;
import uk.ac.lancs.relate.filter.FilterInvalid;
import uk.ac.lancs.relate.filter.FilterList;
import uk.ac.lancs.relate.filter.FilterTransducerNo;
import uk.ac.lancs.relate.filter.KalmanFilter;

import org.eclipse.swt.widgets.Group;
import org.openuat.authentication.exceptions.ConfigurationErrorException;
import org.openuat.authentication.exceptions.InternalApplicationException;
import org.openuat.channel.IPSecConnection;
import org.openuat.channel.IPSecConnection_Factory;
import org.openuat.channel.X509CertificateGenerator;

/** @author Rene Mayrhofer
 * @version 1.0
 */
public class IPSecConnectorClient extends IPSecConnectorCommon {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(IPSecConnectorClient.class);

	private Shell sShell = null;  //  @jve:decl-index=0:visual-constraint="1,1"
	private Label label1 = null;
	private Label gatewayLabel = null;
	private Label label5 = null;
	private Button continueButton = null;
	private Label label2 = null;
	private Label caDnLabel = null;
	private Label label6 = null;
	private Label remoteNetworkLabel = null;
	private Button cancelButton = null;
	
	/** This represents the configuration of the IPSec tunnel. It is used
	 * to parse the XML-encoded config block received from the admin end.
	 */
	private IPSecConfigHandler config;

	private Group group = null;

	private Label validityLabel = null;

	private Label label3 = null;

	private Label label4 = null;

	private Label clientDnLabel = null;

	private Label label8 = null;

	private Label statusLabel = null;
	
	private byte[] sharedKey = null;
	
	private File tempCertFile = null;

	/**
	 * This method initializes group	
	 *
	 */
	private void createGroup() {
		group = new Group(sShell, SWT.NONE);
		group.setText("Client certificate to import");
		group.setBounds(new org.eclipse.swt.graphics.Rectangle(5,134,232,93));
		validityLabel = new Label(group, SWT.NONE);
		validityLabel.setBounds(new org.eclipse.swt.graphics.Rectangle(130,22,53,17));
		label3 = new Label(group, SWT.NONE);
		label3.setBounds(new org.eclipse.swt.graphics.Rectangle(8,21,109,17));
		label3.setText("Validity in days");
		label4 = new Label(group, SWT.NONE);
		label4.setBounds(new org.eclipse.swt.graphics.Rectangle(10,43,146,20));
		label4.setText("Common name");
		clientDnLabel = new Label(group, SWT.NONE);
		clientDnLabel.setBounds(new org.eclipse.swt.graphics.Rectangle(6,67,221,20));
	}

	/**
	 * @param args
	 * @throws IOException 
	 * @throws InternalApplicationException 
	 * @throws ConfigurationErrorException 
	 * @throws DongleException 
	 */
	public static void main(String[] args) throws DeviceException, ConfigurationErrorException, InternalApplicationException, IOException {
		// TODO Auto-generated method stub
		/* Before this is run, be sure to set up the launch configuration (Arguments->VM Arguments)
		 * for the correct SWT library path in order to run with the SWT dlls. 
		 * The dlls are located in the SWT plugin jar.  
		 * For example, on Windows the Eclipse SWT 3.1 plugin jar is:
		 *       installation_directory\plugins\org.eclipse.swt.win32_3.1.0.jar
		 */
	    Configuration relateConf = new Configuration();
	    relateConf.setSide("back");

        SetupHelper helper = new SetupHelper(relateConf);
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

		IPSecConnectorClient thisClass = new IPSecConnectorClient(relateConf);
		thisClass.display = Display.getDefault();
		thisClass.sShell.open();

		while (!thisClass.sShell.isDisposed()) {
			if (!thisClass.display.readAndDispatch())
				thisClass.display.sleep();
		}
		thisClass.display.dispose();
	}
	
	public IPSecConnectorClient(Configuration relateConf) throws DeviceException, ConfigurationErrorException, InternalApplicationException, IOException {
		super(false, relateConf);
		createSShell();
	}

	/**
	 * This method initializes sShell
	 */
	private void createSShell() {
		sShell = new Shell();
		sShell.setText("IPSec Connector Client");
		sShell.setSize(new org.eclipse.swt.graphics.Point(249,382));
		label1 = new Label(sShell, SWT.NONE);
		label1.setBounds(new org.eclipse.swt.graphics.Rectangle(7,50,108,18));
		label1.setText("IPSec gateway");
		gatewayLabel = new Label(sShell, SWT.NONE);
		gatewayLabel.setBounds(new org.eclipse.swt.graphics.Rectangle(130,47,102,20));
		gatewayLabel.setText("0.0.0.0");
		label5 = new Label(sShell, SWT.NONE);
		label5.setBounds(new org.eclipse.swt.graphics.Rectangle(8,231,201,17));
		label5.setText("Authentication progress");
		authenticationProgress = new ProgressBar(sShell, SWT.NONE);
		authenticationProgress.setBounds(new org.eclipse.swt.graphics.Rectangle(6,270,219,31));
		continueButton = new Button(sShell, SWT.NONE);
		continueButton.setBounds(new org.eclipse.swt.graphics.Rectangle(4,317,163,28));
		continueButton.setText("Import and Start IPSec");
		continueButton.setEnabled(false);
		continueButton
				.addSelectionListener(new org.eclipse.swt.events.SelectionAdapter() {
					public void widgetSelected(org.eclipse.swt.events.SelectionEvent e) {
						// and import into the registry (overwriting existing certificates)
						IPSecConnection conn = IPSecConnection_Factory.getImplementation();
						conn.importCertificate(tempCertFile.getAbsolutePath(), 
								new String(Hex.encodeHex(sharedKey)), true);
						
						// finally, everything is in place, start the IPSec connection
						conn.init(config.getGateway(), config.getRemoteNetwork(), config.getRemoteNetmask());
						// TODO: make the persistent flag configurable
						conn.start(config.getCaDistinguishedName(), true);
					}
				});
		label2 = new Label(sShell, SWT.NONE);
		label2.setBounds(new org.eclipse.swt.graphics.Rectangle(11,91,224,17));
		label2.setText("Certificate authority to import");
		caDnLabel = new Label(sShell, SWT.NONE);
		caDnLabel.setBounds(new org.eclipse.swt.graphics.Rectangle(12,111,224,17));
		caDnLabel.setText("");
		label6 = new Label(sShell, SWT.NONE);
		label6.setBounds(new org.eclipse.swt.graphics.Rectangle(8,72,115,17));
		label6.setText("Remote network");
		remoteNetworkLabel = new Label(sShell, SWT.NONE);
		remoteNetworkLabel.setBounds(new org.eclipse.swt.graphics.Rectangle(129,72,104,17));
		remoteNetworkLabel.setText("0.0.0.0/0");
		cancelButton = new Button(sShell, SWT.NONE);
		cancelButton.setBounds(new org.eclipse.swt.graphics.Rectangle(173,317,62,30));
		cancelButton.setText("Cancel");
		cancelButton
				.addSelectionListener(new org.eclipse.swt.events.SelectionAdapter() {
					public void widgetSelected(org.eclipse.swt.events.SelectionEvent e) {
						sShell.close();
					}
				});
		createGroup();
		label8 = new Label(sShell, SWT.NONE);
		label8.setBounds(new org.eclipse.swt.graphics.Rectangle(11,7,71,21));
		label8.setText("Status:");
		statusLabel = new Label(sShell, SWT.NONE);
		statusLabel.setBounds(new org.eclipse.swt.graphics.Rectangle(87,9,150,21));
		statusLabel.setForeground(Display.getCurrent().getSystemColor(SWT.COLOR_BLUE));
		statusLabel.setText("waiting for admin");
	}
	
	public void AuthenticationSuccess(Object sender, Object remote, Object result) {
		Object[] remoteParam = (Object[]) remote;
		logger.info("Received relate authentication success event with " + remoteParam[0] + "/" + remoteParam[1]);
		System.out.println("SUCCESS");
		
		// first update the status
		display.syncExec(new Runnable() { public void run() { 
			statusLabel.setText("success, receiving");
			statusLabel.setForeground(Display.getCurrent().getSystemColor(SWT.COLOR_DARK_BLUE));
		}});
		
		// since we use RelateAuthenticationProtocol with keepSocketConnected=true, ...
		sharedKey = (byte[]) ((Object[] ) result)[0];
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
			if (!confName.toString().equals(BLOCKNAME_CONFIG)) {
				logger.error("Binary block name is '" + confName + 
						"' instead of the expected '" + BLOCKNAME_CONFIG +
						"'. Is the admin application running on the other end?");
				// TODO: display error message
				toRemote.close();
				return;
			}
			logger.debug("Received configuration block from admin (" + recvSize + "B), parsing now");
			config = new IPSecConfigHandler();
			if (!config.parseConfig(new StringReader(confBlock.toString()))) {
				logger.error("Could not parse IPSec configuration from XML");
				// TODO: display error message
				toRemote.close();
				return;
			}
			// also update the GUI
			display.syncExec(new Runnable() { public void run() { 
				gatewayLabel.setText(config.getGateway());
				remoteNetworkLabel.setText(config.getRemoteNetwork());
				caDnLabel.setText(config.getCaDistinguishedName());
			}});
			
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
			if (!certName.toString().equals(BLOCKNAME_CERTIFICATE)) {
				logger.error("Binary block name is '" + certName + 
						"' instead of the expected '" + BLOCKNAME_CERTIFICATE +
						"'. Is the admin application running on the other end?");
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
		tempCertFile = null;
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
		logger.debug("Wrote received certificate to temporary file " + tempCertFile.getAbsolutePath());
		
		// and display some details from the certificate
		display.syncExec(new Runnable() { public void run() { 
			statusLabel.setText("complete");
			statusLabel.setForeground(Display.getCurrent().getSystemColor(SWT.COLOR_GREEN));
			try {
				clientDnLabel.setText(X509CertificateGenerator.getCertificateDistinguishedName(
					new FileInputStream(tempCertFile), new String(Hex.encodeHex(sharedKey)), 
					X509CertificateGenerator.KeyExportFriendlyName, false));
				validityLabel.setText(Integer.toString(X509CertificateGenerator.getCertificateValidity(
						new FileInputStream(tempCertFile), new String(Hex.encodeHex(sharedKey)), 
						X509CertificateGenerator.KeyExportFriendlyName, false)));
			}
			catch (IOException e) {
				logger.error("Unable to open certificate: " + e);
				// TODO: display error message
				return;
			}
			
			// finally allow the button to be clicked for the user to continue
			continueButton.setEnabled(true);
		}});
	}
}
