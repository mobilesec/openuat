package org.eu.mayrhofer.sensors;

import java.io.InputStream;
import java.io.OutputStream;

import javax.bluetooth.DeviceClass;
import javax.bluetooth.DiscoveryAgent;
import javax.bluetooth.DiscoveryListener;
import javax.bluetooth.L2CAPConnection;
import javax.bluetooth.LocalDevice;
import javax.bluetooth.RemoteDevice;
import javax.bluetooth.ServiceRecord;
import javax.bluetooth.UUID;
import javax.microedition.io.Connection;
import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.StreamConnectionNotifier;
import javax.obex.*;

import de.avetana.bluetooth.hci.LinkQuality;
import de.avetana.bluetooth.hci.Rssi;


public class BluetoothRFCOMMChannel {
	  // Connection streams. These streams are only used with BluetoothStream connections (RFCOMN)
	  private InputStream is = null;
	  private OutputStream os = null;

	  // The connection instance. Can be an L2CAPConnectionImpl or an RFCOMMConnectionImpl, depending on
	  // the protocol choosen. (VERSION 1.2)
	  private Connection streamCon = null;
	  // Connection notifier for SDP server profiles
	  private Connection notify=null;
  
	  //javax.bluetooth.* classical classes
	  //Saving this two instances avoid to continuily call the static methods (getLocalDevice(), getDiscoveryAgent()) of
	  //these two classes
	  private DiscoveryAgent m_agent;
	  private LocalDevice m_local;

	  private static final int rfcommPackLen = 100;
	  
	  public BluetoothRFCOMMChannel() {
		  try {
		       // Initialize the java stack.
			  de.avetana.bluetooth.stack.BluetoothStack.init(new de.avetana.bluetooth.stack.AvetanaBTStack());
			     m_local=LocalDevice.getLocalDevice();
			     m_agent=m_local.getDiscoveryAgent();

			     String serviceURL = "btspp://00A0961360C8:1;authenticate=false;master=true;encrypt=false";
			     StreamConnection connection = (StreamConnection) Connector.open(serviceURL);
			     InputStream is = connection.openInputStream();
			     int c = is.read();
			     while (c != -1) {
			    	 System.out.print((char) c);
			    	 c = is.read();
			     }
			     
		     }catch(Exception ex) {
		       ex.printStackTrace();
		       System.exit(0);
		     }
	  }

	   /**
	    * Shows information about the remote device (name, device class, BT address ..etc..)
	    */
	   /*public void getRemoteDevInfos() {
	   	 RemoteDevice rd = null;
	   	 String name = "unknown";
	   	 int rssi = 0;
		 int lq = 0;
	   	 try {
	     	rd = RemoteDevice.getRemoteDevice(streamCon);
			name = rd.getFriendlyName(false);
			rssi = Rssi.getRssi(rd.getBTAddress());
			lq = LinkQuality.getLinkQuality(rd.getBTAddress());
		} catch (Exception e) {
			showInfo (e.getMessage(), "Error");
			return;
		}
	     showInfo("Remote Device Address, Name, Rssi und Quality\n" + rd.getBluetoothAddress() + " " + name + " " + rssi + " " + lq,"Info");
	   }*/

	   /**
	    * Turns on/off the encryption of an existing ACL link
	    */
	   /*public void encryptLink() {
	     try {
	       RemoteDevice dev=((BTConnection)streamCon).getRemoteDevice();
	       dev.encrypt(streamCon, !m_encrypt.isEnabled());
	     }catch(Exception ex) {
	       showError(ex.getMessage());
	     }
	   }*/

	   /**
	    * Authenticates the remote device connected with the local device
	    */
	   /*public void authenticateLink() {
	     try {
	       RemoteDevice dev=((BTConnection)streamCon).getRemoteDevice();
	             
	       JOptionPane.showMessageDialog(this, "Authentification " + (dev.authenticate() ? "successfull" : "Non successfull"));
	     }catch(Exception ex) {
	       showError(ex.getMessage());
	     }
	   }*/

	   /**
	    * Switches the state of the local device between Master and Slave
	    */
	   /*public void switchMaster() {
	     try {
	       throw new Exception("not yet implemented!");
	     }catch(Exception ex) {
	       showError(ex.getMessage());
	     }

	   }*/
}

