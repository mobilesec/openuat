/**
 *  Filename: SelectBtDeviceForm.java (in org.openbandy.log)
 *  This file is part of the OpenBandy project.
 * 
 *  OpenBandy is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  any later version.
 *
 *  OpenBandy is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with OpenBandy. If not, see <http://www.gnu.org/licenses/>.
 *
 *
 * (c) Copyright Philipp Bolliger 2007, ALL RIGHTS RESERVED.
 * 
 *  www.openbandy.org
 */

package org.openbandy.log;

import java.io.IOException;
import java.util.Hashtable;

import javax.bluetooth.BluetoothStateException;
import javax.bluetooth.DeviceClass;
import javax.bluetooth.DiscoveryAgent;
import javax.bluetooth.DiscoveryListener;
import javax.bluetooth.LocalDevice;
import javax.bluetooth.RemoteDevice;
import javax.bluetooth.ServiceRecord;
import javax.bluetooth.UUID;
import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Choice;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.Ticker;

import org.openbandy.service.LogService;
import org.openbandy.ui.BandyDisplayable;

import de.avetana.javax.obex.ClientSession;
import de.avetana.javax.obex.Connector;
import de.avetana.javax.obex.HeaderSet;
import de.avetana.javax.obex.Operation;


/* javax.obex */
// import javax.obex.HeaderSet;
// import javax.obex.Operation;
// import javax.obex.ClientSession;
// import javax.obex.Connector;
/**
 * This form is used to list all Bluetooth devices in range and, if selected,
 * send the log messages as a file (OBEX).
 * 
 * NOTE Make sure that your computer is setup to receive files!! - Mac:
 * Bluetooth Settings, Sharing - Windows (XP SP2): 'Receive File'
 * 
 * <br>
 * <br>
 * (c) Copyright P. Bolliger 2007, ALL RIGHTS RESERVED.
 * 
 * @author Philipp Bolliger (philipp@bolliger.name)
 * @version 1.0
 */
public class SelectBtDeviceForm extends List implements BandyDisplayable, CommandListener, Runnable, DiscoveryListener {

	/* Commands */
	private Command cmdSelect;

	private Command cmdCancel;

	/** Status ticker */
	private Ticker ticker = new Ticker("");

	/** Reference to the MIDlets display */
	private Display display;

	/** Reference to the log canvas */
	private LogImpl logCanvas;

	/** Hashtable of discovered devices: index -> remote device */
	private Hashtable discoveredDevices = new Hashtable();

	/** Reference to the selected remove device */
	private RemoteDevice selectedRemoteDevice;

	/** The Bluetooth Discovery Agent */
	private static DiscoveryAgent discoveryAgent;

	/** Indicator on which action the discovery agent has to undertake */
	private static final int START_INQUIRY = 0;

	private static final int SEARCH_SERVICES = 1;

	private static int threadAction = START_INQUIRY;

	/** 0x1105 is the UUID for the Object Push Profile */
	private static UUID[] uuidSet = {
		new UUID(0x1105)
	};

	/** Indicates that at least one device was discovered */
	private boolean firstDeviceDiscovered = false;

	/** The log message string that will be sent */
	private String logAsString = "";

	/**
	 * Create a new select bt device form
	 * 
	 * @param logAsString String representation of the log that will be sent to the remote device
	 */
	public SelectBtDeviceForm(String logAsString) {
		super("Select Device", Choice.IMPLICIT);

		this.logAsString = logAsString;

		setTicker(ticker);

		/* compose the GUI */
		cmdSelect = new Command("Select", Command.SCREEN, 1);
		cmdCancel = new Command("Cancel", Command.CANCEL, 1);
		addCommand(cmdCancel);

		/* set me as command listener */
		setCommandListener(this);

		/* get access to the discovery agent */
		try {
			LocalDevice localDevice = LocalDevice.getLocalDevice();
			discoveryAgent = localDevice.getDiscoveryAgent();
		}
		catch (BluetoothStateException e) {
			LogService.error(this, e.getMessage(), e);
		}

		/* start the bluetooth inquiry */
		threadAction = START_INQUIRY;
		Thread thread = new Thread(this);
		thread.start();
	}

	/* ******************** Methods for BandyDisplayable ************** */

	/* (non-Javadoc)
	 * @see javax.microedition.lcdui.CommandListener#commandAction(javax.microedition.lcdui.Command, javax.microedition.lcdui.Displayable)
	 */
	public void commandAction(Command c, Displayable d) {
		if (c == cmdSelect) {
			/* cancel the possibly running inquiry */
			discoveryAgent.cancelInquiry(this);

			/* get the selected device */
			Integer index = new Integer(getSelectedIndex());
			selectedRemoteDevice = (RemoteDevice) discoveredDevices.get(index);

			/* start the bluetooth service search for this device */
			threadAction = SEARCH_SERVICES;
			Thread thread = new Thread(this);
			thread.start();
		}
		else if (c == cmdCancel) {
			display.setCurrent(logCanvas);
		}
	}

	/* ******************** Methods for Runnable ************** */

	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	public void run() {
		try {
			if (threadAction == START_INQUIRY) {
				ticker.setString("Bluetooth inquiry running, please wait..");
				discoveryAgent.startInquiry(DiscoveryAgent.GIAC, this);
			}
			else if (threadAction == SEARCH_SERVICES) {
				ticker.setString("Bluetooth service search running");
				discoveryAgent.searchServices(null, uuidSet, selectedRemoteDevice, this);
			}
		}
		catch (BluetoothStateException bse) {
			LogService.error(this, bse.getMessage(), bse);
		}
	}

	/* ******************** Methods for BandyDisplayable ************** */

	/* (non-Javadoc)
	 * @see org.openbandy.ui.BandyDisplayable#show(javax.microedition.lcdui.Display, javax.microedition.lcdui.Displayable)
	 */
	public void show(Display display, Displayable previousDisplayable) {
		this.display = display;
		try {
			this.logCanvas = (LogImpl) previousDisplayable;
		}
		catch (ClassCastException cce) {
			LogService.error(this, "Must have LogCanvas as previous screen", cce);
		}

		display.setCurrent(this);
	}

	/* ******************** Methods for DiscoveryListener ************** */

	/* (non-Javadoc)
	 * @see javax.bluetooth.DiscoveryListener#deviceDiscovered(javax.bluetooth.RemoteDevice, javax.bluetooth.DeviceClass)
	 */
	public void deviceDiscovered(RemoteDevice remoteDevice, DeviceClass deviceClass) {
		if (!firstDeviceDiscovered) {
			addCommand(cmdSelect);
			setSelectCommand(cmdSelect);
			firstDeviceDiscovered = true;
		}
		try {
			int index = append(remoteDevice.getFriendlyName(false), null);
			discoveredDevices.put(new Integer(index), remoteDevice);
		}
		catch (IOException ioe) {
			LogService.error(this, ioe.getMessage(), ioe);
		}
	}

	/* (non-Javadoc)
	 * @see javax.bluetooth.DiscoveryListener#inquiryCompleted(int)
	 */
	public void inquiryCompleted(int discType) {
		if (discoveredDevices.size() > 0) {
			ticker.setString("Inquiry completed: select device");
		}
		else {
			ticker.setString("Inquiry completed: no devices found");
		}
	}

	/* (non-Javadoc)
	 * @see javax.bluetooth.DiscoveryListener#serviceSearchCompleted(int, int)
	 */
	public void serviceSearchCompleted(int transID, int respCode) {
		if (respCode == DiscoveryListener.SERVICE_SEARCH_COMPLETED) {
			ticker.setString("Service search completed");
		}
		else {
			/* show failure message for 3 sec */
			Alert alert = new Alert("", "Could not send log!", null, AlertType.ERROR);
			alert.setTimeout(3000);
			display.setCurrent(alert, this);

			ticker.setString("Error during service search, see log for details");
			switch (respCode) {
			case DiscoveryListener.SERVICE_SEARCH_TERMINATED:
				LogService.debug(this, "Service search terminated");
				break;
			case DiscoveryListener.SERVICE_SEARCH_ERROR:
				LogService.debug(this, "Undefined service search error");
				break;
			case DiscoveryListener.SERVICE_SEARCH_NO_RECORDS:
				LogService.debug(this, "Service search found no OBEX service on device");
				break;
			case DiscoveryListener.SERVICE_SEARCH_DEVICE_NOT_REACHABLE:
				LogService.debug(this, "Device is not reachable");
				break;
			}
		}
	}

	/* (non-Javadoc)
	 * @see javax.bluetooth.DiscoveryListener#servicesDiscovered(int, javax.bluetooth.ServiceRecord[])
	 */
	public void servicesDiscovered(int transID, ServiceRecord[] servRecord) {
		/*
		 * get the connection url to access the obex service on the remote
		 * device
		 */
		String btConnectionURL = servRecord[0].getConnectionURL(ServiceRecord.NOAUTHENTICATE_NOENCRYPT, false);
		LogService.debug(this, "Service URL: " + btConnectionURL);

		/* get the log as string */
		if (logCanvas != null) {
			try {
				byte[] file = logAsString.getBytes();

				/*
				 * NOTE (Philipp Bolliger, 30.10.2006): As most mobiles don't
				 * provide support for java.obex.* I decided to use the avetana
				 * implementation. However, if your phone DOES support
				 * javax.obex.*, you can use the commented out Connector line
				 * and change the import statements.
				 */

				/* Create a session and a headerset */
				ClientSession cs = (ClientSession) Connector.open(btConnectionURL);

				HeaderSet hs = cs.connect(cs.createHeaderSet());

				/* send the connect operation and header */
				hs.setHeader(HeaderSet.NAME, LogConfiguration.LOG_FILE_NAME);
				hs.setHeader(HeaderSet.TYPE, "text/plain");
				hs.setHeader(HeaderSet.LENGTH, new Long(file.length));
				Operation po = cs.put(hs);

				/* push the file */
				po.openOutputStream().write(file);
				po.close();

				/* close streams and connections */
				cs.disconnect(null);
				cs.close();

				/* show success message and go back to log canvas after 3 sec */
				Alert alert = new Alert("", "Log sent!", null, AlertType.CONFIRMATION);
				alert.setTimeout(3000);
				display.setCurrent(alert, logCanvas);

			}
			catch (Exception e) {
				LogService.error(this, e.getMessage(), e);

				/* show failure message and go back to log canvas after 3 sec */
				Alert alert = new Alert("", "Could not send log!", null, AlertType.ERROR);
				alert.setTimeout(3000);
				display.setCurrent(alert, logCanvas);
			}
		}
	}

}
