/* Copyright Rene Mayrhofer
 * File created 2006-03-20
 * Modified by Roswitha Gostner to use Swing instead of SWT
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.apps;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringWriter;
import java.net.Socket;
import java.net.UnknownHostException;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JTextField;

import org.apache.commons.codec.binary.Hex;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.openuat.apps.BinaryBlockStreamer;
import org.openuat.apps.IPSecConfigHandler;
import org.openuat.channel.X509CertificateGenerator;
import org.openuat.channel.main.RemoteConnection;
import org.openuat.channel.main.ip.RemoteTCPConnection;

import uk.ac.lancs.relate.apps.RelateGridDemo;
import uk.ac.lancs.relate.core.Configuration;
import uk.ac.lancs.relate.core.DeviceException;
import uk.ac.lancs.relate.core.EventDispatcher;
import uk.ac.lancs.relate.core.MeasurementManager;
import uk.ac.lancs.relate.core.SerialConnector;
import uk.ac.lancs.relate.events.DeviceInformationEvent;
import uk.ac.lancs.relate.events.MeasurementEvent;
import uk.ac.lancs.relate.filter.FilterInvalid;
import uk.ac.lancs.relate.filter.FilterList;
import uk.ac.lancs.relate.filter.FilterTransducerNo;
import uk.ac.lancs.relate.filter.KalmanFilter;
import uk.ac.lancs.relate.gui.swing.widget.AdminConfigDialog;
import uk.ac.lancs.relate.gui.swing.widget.RelateIcon;
import uk.ac.lancs.relate.gui.swing.widget.RelateMenuItem;
import uk.ac.lancs.relate.ip.HostInfoManager;
import uk.ac.lancs.relate.model.Model;
import uk.ac.lancs.relate.model.NLRAlgorithm;

public class IPSecConnectorAdmin extends IPSecConnectorCommon{
	
	/** Our log4j logger. */
	protected static Logger logger = Logger.getLogger(IPSecConnectorAdmin.class);
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
	private RemoteConnection toRemote = null;
	
	private AuthenticationEventsHandler guiHandler=null;
	
	public IPSecConnectorAdmin(Configuration relateConf, String caFile, String caPassword, String caAlias, 
			String configFilename, MeasurementManager mm) throws IOException {
		super(true, relateConf, mm);
		logger.info("Initializing IPSecConnectorAdmin");
		
		// also initialize the certificate generator
		try {
			logger.debug("Initializing certificate authority from " + caFile + "(alias " + caAlias + ")");
			certGenerator = new X509CertificateGenerator(caFile, caPassword, caAlias, true);
		}
		catch (Exception e) {
			String text ="Could not create X.509 certificate generator: ";
			logger.error(text + e);
			guiHandler.showErrorMessageBox(text+"\n"+e, "Certificate Generator");
			if (! System.getProperty("os.name").startsWith("Windows CE")) {
				System.exit(1);
			}
		}
//		 the the config block
		logger.info("Reading configuration from " + configFilename);
		config = new IPSecConfigHandler();
		if (!config.parseConfig(new FileReader(configFilename))) {
			String text ="Could not load IPSec configuration from "; 
			logger.error(text + configFilename);
			guiHandler.showErrorMessageBox(text+ configFilename, " Crating IPSEcConfigHandler");
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
		logger.info("End of constructor");


		
	}
	/**
	 * save the last id in progress... this is useful for a failaire -> redo.
	 */
	private int actualid=-1;
	// TODO: activate me again when J2ME polish can deal with Java5 sources!
	//@Override
	public void AuthenticationProgress(Object sender, Object remote, int cur, int max, String msg) {
		super.AuthenticationProgress(sender, remote, cur, max, msg);
		try {
			String number = remote.toString();
			int id =Integer.parseInt(number);
			actualid =id;
			if (guiHandler!=null){
				guiHandler.progress(sender.toString(), remote.toString(), id, cur, max, msg);
			}
		} catch(Exception e) {
			logger.error("Can't update progress bar", e);
		}
	}
	public boolean AuthenticationStarted(Object sender, Object remote) {
		// just ignore for now, but should update the GUI
		return true;
	}
	
	public void AuthenticationSuccess(Object sender, Object remote, Object result) {
		Object[] remoteParam = (Object[]) remote;
		logger.info("Received relate authentication success event with " + remoteParam[0] + "/" + remoteParam[1]);
		System.out.println("SUCCESS  ... with "+sender + " remote: "+remote);
		
		// since we use RelateAuthenticationProtocol with keepSocketConnected=true, ...
		sharedKey = (byte[]) ((Object[] ) result)[0];
		toRemote = (RemoteConnection) ((Object[] ) result)[1];
		if (guiHandler.adminconfig!=null){
			guiHandler.adminconfig.enableButton();
		}
	}
	
	// TODO: activate me again when J2ME polish can deal with Java5 sources!
	//@Override
	public void AuthenticationFailure(Object sender, Object remote, Exception e, String msg){
		super.AuthenticationFailure(sender, remote, e, msg);
		guiHandler.setPaintingToFreeze(false);
		String text =" Authenciation Failure: "+msg+"\n Would you like to restart Authenctication for id="+actualid+"?";
		int option =guiHandler.showYesNoMessageBox(text, "Authentication Failure");
		if (option == JOptionPane.YES_OPTION && actualid!=-1){
			try {
				guiHandler.doAutenticationForId(actualid);
			} catch (UnknownHostException e1) {
				guiHandler.showErrorMessageBox("Error:"+e1.getMessage(),"Uknown Host Exception");
			} catch (IOException e1) {
				guiHandler.showErrorMessageBox("Error:"+e1.getMessage(),"IO Exception");
			}
		}else {
			guiHandler.adminFrame.setVisible(false);
		}
	}
	
	
	/**
	 * dialog to set up the dongle configuration.
	 */
	 private static Configuration configureDialog(String[] ports, String[] sides, String[] types) {
		JTextField username = new JTextField();
		JComboBox cport = new JComboBox(ports);
		JComboBox csides = new JComboBox(sides);
		JComboBox ctypes = new JComboBox(types);
		int option = JOptionPane.showOptionDialog(null, new Object[] { 
				"User Name:", username,
				"Choose your port",cport, 
				"On which side of your Device is the Dongle plugged into:", csides,
				" What type of Device:", ctypes,}, 
				" Relate Dongle Configuration",
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null,
				null, null);
		if ((option == JOptionPane.CLOSED_OPTION)
				|| (option == JOptionPane.CANCEL_OPTION)) {
			System.exit(0);
		}
		Configuration config = new Configuration(cport.getSelectedItem()+"");
		config.setType(ctypes.getSelectedIndex());
		config.setSide(csides.getSelectedIndex());
		config.setUserName(username.getText());
		config.setDeviceType(Configuration.DEVICE_TYPE_DONGLE);
		logger.debug(config);
		return config;
	}
	
	public static void main(String[] args) throws DeviceException, IOException {
		String serialPort = null, caFile = null, confFile = null;
		if (System.getProperty("os.name").startsWith("Windows CE")) {
			serialPort = "COM8:";
			caFile = "\\relate\\ca-ipsec.p12";
			confFile = "\\relate\\ipsec-conf.xml";
		}
		else {
			serialPort = null;
			caFile = "ca.p12";
			confFile = "ipsec-conf.xml";
		}

		if (System.getProperty("os.name").startsWith("Windows CE")) {
			System.out.println("Configuring log4j");
			PropertyConfigurator.configure("log4j.properties");
		}
		
//		 if we have an IP address as argument, then start in simulation mode
		Configuration relateConf = null;
		if (args.length > 0)  {
			serialPort = null;
		}
		else {
			logger.info("Initializing with serial port " + serialPort);
			relateConf = configureDialog(Configuration.getDevicePorts(), Configuration.SIDE_NAMES, Configuration.TYPES);
		}
		SerialConnector connector = SerialConnector.getSerialConnector(relateConf.getDevicePortName(), relateConf.getDeviceType());
    	connector.registerEventQueue(EventDispatcher.getDispatcher().getEventQueue());
        // this will start the SerialConnector thread and start listening for incoming measurements
        MeasurementManager man = new MeasurementManager(relateConf);
        EventDispatcher.getDispatcher().addEventListener(MeasurementEvent.class, man);
		IPSecConnectorAdmin thisClass = new IPSecConnectorAdmin(relateConf, 
				caFile, "test password", "Test CA", confFile, man);
		
		// set up all necessary things for the handler.
		connector.setHostInfo(relateConf.getHostInfo());
		HostInfoManager hostInfoManager = HostInfoManager.getHostInfoManager();
		EventDispatcher.getDispatcher().addEventListener(DeviceInformationEvent.class, hostInfoManager);
		
		// filtered MM
		MeasurementManager fman = new MeasurementManager(relateConf);
		EventDispatcher.getDispatcher().addEventListener(MeasurementEvent.class, fman);
		FilterList filters = new FilterList();
        filters.addFilter(new FilterInvalid());
        filters.addFilter(new FilterTransducerNo(2));
        //filters.addFilter(new FilterOutlierDistance());
        filters.addFilter(new KalmanFilter());
        filters.addFilter(new FilterInvalid());
		fman.setFilterList(filters);
		NLRAlgorithm nlrAlgorithm = new NLRAlgorithm(relateConf);
		fman.addMeasurementListener(nlrAlgorithm);
		Model model = new Model();
		nlrAlgorithm.addListener(model);
        if (hostInfoManager != null) {
            hostInfoManager.addListener(model);
        }
		
		
		AuthenticationEventsHandler selectionGui = thisClass.new AuthenticationEventsHandler( relateConf, 
				hostInfoManager, model);
		thisClass.setAuthHandler(selectionGui);
		createAndShowGUI(selectionGui);
	}
	
	private void setAuthHandler(AuthenticationEventsHandler selectionGui) {
		guiHandler=selectionGui;
	
	}
	/**
     * Create the GUI and show it.  For thread safety,
     * this method should be invoked from the
     * event-dispatching thread.
     */
    private static void createAndShowGUI(JComponent pane) {
        //Create and set up the window.
        JFrame frame = new JFrame(" ~ ~ IPSec Admin ~ ~");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.getContentPane().add(pane, BorderLayout.CENTER);

        //Display the window.
        frame.setSize(550,500);
        frame.setVisible(true);
    }
	
	private class AuthenticationEventsHandler extends RelateGridDemo {

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		protected AdminConfigDialog adminconfig=null;
		private JFrame adminFrame;

		public AuthenticationEventsHandler(Configuration config, 
				HostInfoManager manager, Model model) {
			// force the GUI to display the authentication menu entry even if 
			// it does not locally use it
			super(false, config, manager, model);
		}

		private void authenticationStarted(String serialPort, String remoteHost, int remoteRelateId, byte numRounds) throws UnknownHostException, IOException {	
			logger.debug("start Authentication with "+remoteHost + " at port " + serialPort + " where id is: "+ remoteRelateId+ " and the number of round is "+ numRounds);
			authp.startAuthentication(remoteHost, remoteRelateId, numRounds);
		}		

		// TODO: activate me again when J2ME polish can deal with Java5 sources!
		//@Override
		public void success(String serialPort, String remoteHost, int remoteRelateId, byte numRounds, byte[] sharedSecret,
				Socket socketToRemote) {
			super.success(serialPort, remoteHost, remoteRelateId, numRounds, sharedSecret, socketToRemote);
			// remember the shared key and the socket
			sharedKey = sharedSecret;
			toRemote = new RemoteTCPConnection(socketToRemote);
		}

		// TODO: activate me again when J2ME polish can deal with Java5 sources!
		//@Override
		public void progress(String serialPort, String remoteHost, int remoteRelateId, int cur, int max, String msg) {
			super.progress(serialPort, remoteHost, remoteRelateId, cur, max, msg);
		}
		
		/**
		 * ovewrites the action perform ... basically,  it 
		 * just adds the possible user actions, deriving from
		 * the admin interface only.
		 */
		// TODO: activate me again when J2ME polish can deal with Java5 sources!
		//@Override
		public void actionPerformed(ActionEvent e) {
			super.actionPerformed(e);
			if (e.getActionCommand().equals(SEC_CON)){
				try {
					startSecureConnection(e);
				} catch (UnknownHostException e1) {
					e1.printStackTrace();
				} catch (IOException e1) {
					e1.printStackTrace();
				} 
			} else if(e.getActionCommand().equals(AdminConfigDialog.GENERATE)){
				if (adminconfig!=null){
					adminconfig.generateCertificate();
					issueCertificate(adminconfig.getCertName(), adminconfig.getDays(), adminconfig.getRelateId());
				}
			}else if (e.getActionCommand().equals(AdminConfigDialog.CANCEL)){
				if (adminFrame!=null){
					adminFrame.setVisible(false);	
				}
			}
		}
		
		/**
		 * the user has selected one of the items, and would like to 
		 * establish a secure connection; the event comes from actionPerformed
		 * and is delegated to this method here.
		 * @param e
		 * @throws IOException 
		 * @throws UnknownHostException 
		 */
		private void startSecureConnection(ActionEvent e) throws UnknownHostException, IOException {		
				if (e.getSource() instanceof JMenuItem) {
					RelateMenuItem item = (RelateMenuItem)e.getSource();
					int relateId = item.getRelateId();
					doAutenticationForId(relateId);
				}
		}

		private void doAutenticationForId(int relateId) throws UnknownHostException, IOException {
			Configuration c = hostManager.getConfigurationForId(relateId);
			String remoteAddress =null;
			logger.debug("For id: "+relateId+" hostInfoManager offers this configuration: "+c);
			if (c!= null && c.getInetAddress() != null){
				remoteAddress = c.getInetAddress().getHostAddress();
				String error ="For id: "+relateId+" we have this ip address: "+remoteAddress;
				logger.debug(error);
			} 
			if (remoteAddress == null) {
				String text ="Could not lookup up address of device id " + relateId +
				".\nHas the secure authentication code been enabled on the other host?";
				logger.info(text);
				showErrorMessageBox(text, "Start Secure Authentication");
				return;
			}
			if (auth != null) {
				if (!auth.startAuthenticationWith(remoteAddress, (byte) relateId, 10)) {
					logger.debug( "start the authentication."); 
					return;
				}
			}
			RelateIcon icon = (RelateIcon)relDevices.get(new Integer(relateId));
			icon.setProgressBar(true);
			setPaintingToFreeze(true);

			
			createAdminUI(relateId);
			authenticationStarted(relateConfig.getPort(), remoteAddress, (byte) relateId, (byte) 10);
		}
			
		
		
		
		private  void createAdminUI(int id) {
		        //Create and set up the window.
		        adminFrame = new JFrame("Admin Config Pane for "+id);
		        adminFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		        int x= getBounds().width;
		        int y= 20;
		        adminFrame.setLocation(x, y);
		        //Create and set up the content pane.
		        adminconfig =new AdminConfigDialog(id);
		        adminconfig.setCancelButtonListener(this);
		        adminconfig.setGenerateButtonListener(this);
		        adminFrame.setLayout(new BorderLayout());
		        adminFrame.getContentPane().add(adminconfig, BorderLayout.CENTER);
		        adminFrame.setVisible(true);
		        adminFrame.pack();
		        setFocusable(true);
		}
		
		public void issueCertificate(String commonNameInput, int days, int relateId) {
			logger.debug ("issue Certificate");
			// ok, got the shared password - use it to create the certificate (in the background)
			// (and need to get the text fields in the SWT UI thread) 

			asyncCreateCertificate(commonNameInput, days, new String(Hex.encodeHex(sharedKey)), relateId);
			// first of all, wait for the certificate to be generated (if not already)
			synchronized (certificateFilenameLock) {
				if (certificateFilename == null) {
					// hmm, thread not started yet - can't cope
					String text= "Certificate generation thread was not started properly, can not continue";
					logger.error(text);
					guiHandler.showErrorMessageBox(text, "Waiting for Certificate Creation");
					toRemote.close();
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
						String text = "Certificate generation failed: " + certificateFilename;
						logger.error(text);
						guiHandler.showErrorMessageBox(text, "Certificate Generation Failed");
						toRemote.close();
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
				String text ="Could not open output stream to remote host: ";
				logger.error(text + e);
				guiHandler.showErrorMessageBox(text+ "\n"+e, " OutputStream to Remote Host ");
				toRemote.close();
				return;
			}
			try {
				// first transmit the configuration for the tunnel
				StringWriter confBlock = new StringWriter();
				if (!config.writeConfig(confBlock)) {
					String text = "Could not export IPSec configuration to XML";
					logger.error(text);
					guiHandler.showErrorMessageBox(text, "Export of IPSec Configuration");
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
				String text ="Could not send to remote host: ";
				logger.error(text + e);
				guiHandler.showErrorMessageBox(text+ "\n"+ e, "Sending to Remote Host");
				return;
			}
			finally {
				// and be sure to close the socket properly
				toRemote.close();
			}
			guiHandler.setPaintingToFreeze(false);
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
		protected boolean asyncCreateCertificate(String commonName, int validityDays, String exportPassword, final int relateId) {
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
				String text = "Unable to create temporary file for certificate: ";
				logger.error(text + e);
				guiHandler.showErrorMessageBox(text+"\n"+e," Creating Temporary File" );
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
						informAdminPanelofSuccess(relateId);
						adminFrame.setVisible(false);
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
			}

			 }).start();
			return true;
		}
		
		private void informAdminPanelofSuccess(int relateId) {
			authDevices.add(new Integer(relateId));
		}
	}
}
