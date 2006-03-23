/* Copyright Rene Mayrhofer
 * File created 2006-03-20
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.apps;

import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Spinner;

/** @author Rene Mayrhofer
 * @version 1.0
 */
public class IPSecConnectorAdmin {

	private Shell sShell = null;  //  @jve:decl-index=0:visual-constraint="4,11"
	private ProgressBar certificateProgress = null;
	private Label label = null;
	private Label label1 = null;
	private Label gatewayLabel = null;
	private Label label4 = null;
	private Text commonNameInput = null;
	private Label label5 = null;
	private ProgressBar authenticationProgress = null;
	private Button startButton = null;
	private Label label2 = null;
	private Label caDnLabel = null;
	private Label label6 = null;
	private Label remoteNetworkLabel = null;
	private Label label3 = null;
	private Spinner validiityInput = null;
	private Button cancelButton = null;

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		/* Before this is run, be sure to set up the launch configuration (Arguments->VM Arguments)
		 * for the correct SWT library path in order to run with the SWT dlls. 
		 * The dlls are located in the SWT plugin jar.  
		 * For example, on Windows the Eclipse SWT 3.1 plugin jar is:
		 *       installation_directory\plugins\org.eclipse.swt.win32_3.1.0.jar
		 */
		Display display = Display.getDefault();
		IPSecConnectorAdmin thisClass = new IPSecConnectorAdmin();
		thisClass.createSShell();
		thisClass.sShell.open();

		while (!thisClass.sShell.isDisposed()) {
			if (!display.readAndDispatch())
				display.sleep();
		}
		display.dispose();
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
		validiityInput = new Spinner(sShell, SWT.NONE);
		validiityInput.setMaximum(365);
		validiityInput.setSelection(30);
		validiityInput.setBounds(new org.eclipse.swt.graphics.Rectangle(121,89,53,20));
		cancelButton = new Button(sShell, SWT.NONE);
		cancelButton.setBounds(new org.eclipse.swt.graphics.Rectangle(163,170,62,30));
		cancelButton.setText("Cancel");
	}

}
