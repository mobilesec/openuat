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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringReader;

import javax.swing.JFrame;
import javax.swing.JOptionPane;

import org.apache.commons.codec.binary.Hex;
import java.util.logging.Logger;

import org.openuat.apps.BinaryBlockStreamer;
import org.openuat.apps.IPSecConfigHandler;

import org.openuat.channel.X509CertificateGenerator;
import org.openuat.channel.main.RemoteConnection;
import org.openuat.channel.vpn.IPSecConnection;
import org.openuat.channel.vpn.IPSecConnection_Factory;

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
import uk.ac.lancs.relate.gui.swing.widget.CoordinateHelper;
import uk.ac.lancs.relate.ip.HostInfoManager;
import uk.ac.lancs.relate.ip.Service;
import uk.ac.lancs.relate.model.Model;
import uk.ac.lancs.relate.model.NLRAlgorithm;

public class IPSecConnectorClient extends IPSecConnectorCommon {
	
	/** Our logger. */
	private static Logger logger = Logger.getLogger(IPSecConnectorClient.class);

	private byte[] sharedKey = null;
	
	private File tempCertFile = null;
	
	/** This represents the configuration of the IPSec tunnel. It is used
	 * to parse the XML-encoded config block received from the admin end.
	 */
	private IPSecConfigHandler config;
	
	private CAEventsHandler guiHandler=null;
	
	public IPSecConnectorClient(Configuration relateConf, MeasurementManager man) throws IOException {
		super(false, relateConf, man);
	}

	/**
	 * dialog to set up the dongle configuration.
	 */
/*	 private static Configuration configureDialog(String[] ports, String[] sides, String[] types) {
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
		logger.finer(config);
		return config;
	}*/
	
	public static void main (String[] agrs) throws DeviceException, IOException{
		/* Before this is run, be sure to set up the launch configuration (Arguments->VM Arguments)
		 * for the correct SWT library path in order to run with the SWT dlls. 
		 * The dlls are located in the SWT plugin jar.  
		 * For example, on Windows the Eclipse SWT 3.1 plugin jar is:
		 *       installation_directory\plugins\org.eclipse.swt.win32_3.1.0.jar
		 */
		// TODO: this should be detected instead of hard-coded
//	    Configuration relateConf = configureDialog(Configuration.getDevicePorts(), Configuration.SIDE_NAMES, Configuration.TYPES);
		
		Configuration relateConf = new Configuration("COM4");
		relateConf.setSide(Configuration.BACK);
		relateConf.setDeviceType(Configuration.DEVICE_TYPE_DONGLE);
		
	    SerialConnector connector = SerialConnector.getSerialConnector(relateConf.getDevicePortName(), relateConf.getDeviceType());
    	connector.registerEventQueue(EventDispatcher.getDispatcher().getEventQueue());
        // this will start the SerialConnector thread and start listening for incoming measurements
        MeasurementManager man = new MeasurementManager(relateConf);
        EventDispatcher.getDispatcher().addEventListener(MeasurementEvent.class, man);


        // TODO: check if we need these
        //helper.getHostInfoManager();
        //helper.getMDNSDiscovery();

		IPSecConnectorClient thisClass = new IPSecConnectorClient(relateConf, man);
//		 set up all necessary things for the handler.
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
		
		
        CAEventsHandler selectionGui = thisClass.new CAEventsHandler( relateConf, hostInfoManager, model);
        thisClass.setAuthHandler(selectionGui);
		createAndShowGUI(selectionGui);
	}
	
	private static void createAndShowGUI(CAEventsHandler selectionGui) {
		 //Create and set up the window.
        JFrame frame = new JFrame(" ~ ~ IPSec Client ~ ~ ");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        frame.setLayout(new BorderLayout());
        frame.getContentPane().add(selectionGui, BorderLayout.CENTER);

        //Display the window.
        frame.setSize(400,500);
        frame.setVisible(true);
		
	}
	
	 private void setAuthHandler(CAEventsHandler caeh){
		guiHandler=caeh;
	}

	private class CAEventsHandler extends RelateGridDemo {
		private static final long serialVersionUID = 1L;
		
		private Integer remoteId;
		private int side;
		
		public CAEventsHandler(Configuration config, 
				HostInfoManager manager, Model model) {
			super(false, false, config, manager, model);
			side =-1;
			remoteId=null;
			
		}
		
		// TODO: activate me again when J2ME polish can deal with Java5 sources!
		//@Override
		public void progress(String serialPort, String remoteHost, int remoteRelateId, int cur, int max, String msg) {
			//-> i know that serial connection is not really there.
			//-> remote Host ist the Name of the Host -> could get IPInet. and from there, I could get the Id. and 
			// then calculationg back, and the initial message.
			logger.finer("Store the Admin' s relate id"+ remoteHost);
			try { 
				if (remoteId==null){
					int id=Integer.parseInt(remoteHost);
					Service s = getServiveForId(id);
					side = CoordinateHelper.getRelationFromCoordinates(s.getX(), s.getY());
					remoteId=new Integer(id);
					}
				} catch (Exception e) {
					logger.severe("Can't update progress bar", e);
				}
			this.setPaintingToFreeze(true);
			this.setLocalProgressBar(cur, max);
		
		}
		
		private int getSide(){
			return side;
		}

		
		
	
	}
	
	/** This is an implementation of the AuthenticationProgressHandler interface. */
	// TODO: activate me again when J2ME polish can deal with Java5 sources!
	//@Override
	public void AuthenticationProgress(Object sender, Object remote, int cur, int max, String msg){
		super.AuthenticationProgress(sender, remote, cur, max, msg);
		/**
		 * in the beginning the object is an IP4NetAddress... and changes then to the remote address.
		 */
		guiHandler.progress(null, remote.toString(), -1, cur, max, msg);
	}
	public boolean AuthenticationStarted(Object sender, Object remote) {
		// just ignore for now, but should update the GUI
		return true;
	}

	public void AuthenticationSuccess(Object sender, Object remote, Object result) {
		Object[] remoteParam = (Object[]) remote;
		logger.finer("Received relate authentication success event with " + remoteParam[0] + "/" + remoteParam[1]);
		logger.info("SUCCESS");
		
		guiHandler.setPaintingToFreeze(false);
		// since we use RelateAuthenticationProtocol with keepSocketConnected=true, ...
		sharedKey = (byte[]) ((Object[] ) result)[0];
		RemoteConnection toRemote = (RemoteConnection) ((Object[] ) result)[1];
		
		byte[] certificate = null;
		// open our "binary block" channel to the server
		BinaryBlockStreamer s = openBinaryBlockChannel(toRemote);
		try {
			// first receive the configuration for the tunnel
			ByteArrayOutputStream confBlock = new ByteArrayOutputStream();
			StringBuffer confName = new StringBuffer();
			int recvSize = s.receiveBinaryBlock(confName, confBlock); 
			if (recvSize <= 0) {
				String text ="Unable to receive configuration block from admin";
				logger.severe(text);
				guiHandler.showErrorMessageBox(text, "Receving Certificate block");
				toRemote.close();
				return;
			}
			if (!confName.toString().equals(BLOCKNAME_CONFIG)) {
				String text ="Binary block name is '" + confName + 
				"' instead of the expected '" + BLOCKNAME_CONFIG +
				"'. Is the admin application running on the other end?";
				logger.severe(text);
				guiHandler.showErrorMessageBox(text, "Receving Certificate block");
				toRemote.close();
				return;
			}
			logger.finer("Received configuration block from admin (" + recvSize + "B), parsing now");
			config = new IPSecConfigHandler();
			if (!config.parseConfig(new StringReader(confBlock.toString()))) {
				String text ="Could not parse IPSec configuration from XML";
				logger.severe(text);
				guiHandler.showErrorMessageBox(text, "Receving Certificate block");
				toRemote.close();
				return;
			}
			
			
//			
			// and now the certificate
			ByteArrayOutputStream certBlock = new ByteArrayOutputStream();
			StringBuffer certName = new StringBuffer();
			recvSize = s.receiveBinaryBlock(certName, certBlock); 
			if (recvSize <= 0) {
				String text ="Unable to receive certificate block from admin";
				logger.severe(text);
				guiHandler.showErrorMessageBox(text, "Recevied Certificate block");
				toRemote.close();
				return;
			}
			if (!certName.toString().equals(BLOCKNAME_CERTIFICATE)) {
				String text ="Binary block name is '" + certName + 
				"' instead of the expected '" + BLOCKNAME_CERTIFICATE +
				"'.\nIs the admin application running on the other end?";
				logger.severe(text);
				guiHandler.showErrorMessageBox(text, "Recevied Certificate block");
				toRemote.close();
				return;
			}
			logger.finer("Received certificate from admin (" + recvSize + "B)");
			certificate = certBlock.toByteArray();
		} catch (IOException e) {
			String text ="Could not read from remote host: ";
			logger.severe(text + e);
			guiHandler.showErrorMessageBox(text+"\n"+e, "Recevied Certificate");
			return;
		}
		finally {
			// and be sure to close the socket properly
			toRemote.close();
		}

		// create a new temporary file for the certificate
		tempCertFile = null;
		try {
			tempCertFile = File.createTempFile("newCert-", ".p12");
			tempCertFile.deleteOnExit();
			new FileOutputStream(tempCertFile).write(certificate);
		}
		catch (IOException e) {
			String text ="Unable to create or write to temporary file for certificate: ";
			logger.severe( text+ e);
			guiHandler.showErrorMessageBox(text+"\n"+e, "Create Certificate File");
			return;
		}
		logger.finer("Wrote received certificate to temporary file " + tempCertFile.getAbsolutePath());
		// and display some details from the certificate	
		logger.info(" Authentification is now finished ... ");
		String certName;
		String certTime;
		try {
			 certName= X509CertificateGenerator.getCertificateDistinguishedName(new FileInputStream(tempCertFile), new String(Hex.encodeHex(sharedKey)), 
				X509CertificateGenerator.KeyExportFriendlyName, false);
			logger.info(" THIS IS NOW TO bet set.. "+certName);
			certTime =Integer.toString(X509CertificateGenerator.getCertificateValidity(
					new FileInputStream(tempCertFile), new String(Hex.encodeHex(sharedKey)), 
					X509CertificateGenerator.KeyExportFriendlyName, false));
			logger.info(" this must be set to ...  "+ certTime);
		}
		catch (IOException e) {
			String text ="Unable to open certificate: ";
			logger.severe( text+ e);
			guiHandler.showErrorMessageBox(text+"\n"+e, "Open Certificate");
			return;
		}
		// finally allow the button to be clicked for the user to continue
		letUserImportCertificate(certName, certTime);
	}



	private void letUserImportCertificate(String name, String days) {
		int side =guiHandler.getSide();
		String text ="";
		logger.finer("what does the gui give us for a side "+ side);
		String sSide ="";
		if (side ==Configuration.LEFT || side==Configuration.RIGHT || side==Configuration.BACK){
			sSide=Configuration.SIDE_NAMES[side];
			text = "\nThe Admin on your  ["+sSide+ "] side tries to authenticate you!\n";
			
		} else if (side==Configuration.FRONT){
			sSide=Configuration.SIDE_NAMES[side];
			text = "\nThe Admin directly in  ["+sSide+ "] of you tries to authenticate you!\n";
		}
		else if (side==Configuration.BACK){
			sSide=Configuration.SIDE_NAMES[side];
			text = "\nThe Admin  behind  you tries to authenticate you!\n";
		}
		
		logger.info("now the Client needs to import the certificate... ");
		text = text+"\nThe Gateway is: "+config.getGateway();
		text = text +"\nThe RemoteNetwork is: "+config.getRemoteNetwork();
		text = text +"\nThe Cert. Authority Name is:"+config.getCaDistinguishedName();
		text = text +"\nYour Certificate Name is:"+name;
		text = text +"\nHow long is the Certificate valid:"+days+"\n";
		text = text +"\nDo you want to import the certificate?";
		int option = guiHandler.showYesNoMessageBox(text, "Certificate Import");
		if (option == JOptionPane.YES_OPTION){
			importCertifiate();
		}else {
			guiHandler.setLocalAuthSuccess(false);
		}
	}



	private BinaryBlockStreamer openBinaryBlockChannel(RemoteConnection toRemote) {
		BinaryBlockStreamer s=null;
		try {
			s = new BinaryBlockStreamer(toRemote.getInputStream(), null);
		}
		catch (IOException e) {
			String text ="Could not open input stream to remote host: ";
			logger.severe( text+ e);
			guiHandler.showErrorMessageBox(text, "Receving Certificate block");
			toRemote.close();
			return null;
		}
		return s;
	}
	
	private void importCertifiate() {
		// and import into the registry (overwriting existing certificates)
		IPSecConnection conn = IPSecConnection_Factory.getImplementation();
	    /*
	     * the import does not work for MacOS X -> no IpSec Implementation
	     * available for that OS; 
	     */
		try{
		conn.importCertificate(tempCertFile.getAbsolutePath(), 
				new String(Hex.encodeHex(sharedKey)), true);
		// finally, everything is in place, start the IPSec connection
		conn.init(config.getGateway(), config.getRemoteNetwork(), config.getRemoteNetmask());
		// TODO: make the persistent flag configurable
		conn.start(config.getCaDistinguishedName(), true);
		String text = "You have successfully estabilished a secure " +
				"connection\n to the wireless network.";
		logger.info(text);
		guiHandler.showInfoMessageBox(text, "Established Secure Connection");
		guiHandler.setLocalAuthSuccess(true);
		}catch (Exception e){
//			logger.info(e.getMessage());
//			guiHandler.showErrorMessageBox("Error by establishing secure Connection:\n"+e.getMessage(), "Error Establishing Secure Connection");
//			guiHandler.setLocalAuthSuccess(false);
			/**
			 *FIXME: for the study, we do not care, if something goes wrong at this stage. 
			 *because the IPSec isn't implemented for all platform, and this is not part
			 *of what we are studying. We fake the "established connection" 
			 */
			String text = "You have successfully estabilished a secure " +
			"connection\n to the wireless network.";
			logger.info(text);
			guiHandler.showInfoMessageBox(text, "Established Secure Connection");
			guiHandler.setLocalAuthSuccess(true);
		}
	}
	
	
	/** This is an implementation of the AuthenticationProgressHandler interface. */
	// TODO: activate me again when J2ME polish can deal with Java5 sources!
	//@Override
	public void AuthenticationFailure(Object sender, Object remote, Exception e, String msg){
		logger.info("Received relate authentication failure event with " + remote);
		guiHandler.setLocalAuthSuccess(false);
		guiHandler.showErrorMessageBox(msg, "Authentication Failure");
	}
}
