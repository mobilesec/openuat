/*
 * Created on 20.01.2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package de.avetana.javax.obex;

import java.io.IOException;
import java.util.Enumeration;

import javax.bluetooth.DataElement;
import javax.bluetooth.ServiceRecord;
import javax.bluetooth.UUID;
import javax.microedition.io.Connection;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.StreamConnectionNotifier;

/**
 * @author gmelin
 *
 * 
 */
public class Connector {

	public static Connection open (String url) throws IOException {
		if (!url.startsWith("btgoep://")) throw new IOException ("Only OBEX Connections supported");
		url = "btspp" + url.substring(6);
        if(url.startsWith("btspp://localhost")) {
        		StreamConnectionNotifier notifier = (StreamConnectionNotifier) javax.microedition.io.Connector.open (url);
        		try {

				ServiceRecord srec = javax.bluetooth.LocalDevice.getLocalDevice().getRecord(notifier);

				//Set the serviceclassID List to OBEX-OBJECT-PUSH
				DataElement serviceClassIDList = srec.getAttributeValue(0x01);
				DataElement newSCList = new DataElement (DataElement.DATSEQ);
				Enumeration v = (Enumeration)serviceClassIDList.getValue();
				while (v.hasMoreElements()) {
					DataElement de = (DataElement)v.nextElement();
					if (!(de.getValue().equals(new UUID (0x1101))))
							newSCList.addElement(de);
				}
				newSCList.addElement(new DataElement(DataElement.UUID, new UUID(0x1105)));
			    srec.setAttributeValue(0x01, newSCList);
			    
			    //Upate the protocol Descriptor list to contain OBEX
				/* Updating the protocolDescriptor list to change from SPP to OBEX does not seem to work
			    DataElement protocolDescriptorList = srec.getAttributeValue(0x04);
				DataElement newProtDescList = new DataElement (DataElement.DATSEQ);
				v = (Enumeration)protocolDescriptorList.getValue();
				while (v.hasMoreElements()) {
					DataElement de = (DataElement)v.nextElement();
					newProtDescList.addElement(de);
				}
				
			    DataElement obexDescriptor = new DataElement(DataElement.DATSEQ);
			    obexDescriptor.addElement(new DataElement(DataElement.UUID, new UUID(0x08)));

			    newProtDescList.addElement(obexDescriptor);
			    srec.setAttributeValue(0x04, newProtDescList);
			    */
			    
			    //Update Supported Formats list
	    			DataElement sfl = new DataElement (DataElement.DATSEQ);
	    			sfl.addElement(new DataElement (DataElement.U_INT_1, 1));
	    			sfl.addElement(new DataElement (DataElement.U_INT_1, 2));
	    			sfl.addElement(new DataElement (DataElement.U_INT_1, 4));
	    			sfl.addElement(new DataElement (DataElement.U_INT_1, 5));
	    			sfl.addElement(new DataElement (DataElement.U_INT_1, 6));
	    			sfl.addElement(new DataElement (DataElement.U_INT_1, 255));
	    			srec.setAttributeValue(0x0303, sfl);
	    			
	    			//Update service availability
	    			srec.setAttributeValue(0x0008, new DataElement (DataElement.U_INT_1, 255));

	    			//Update Profile Descriptor List
	    			DataElement profileDescriptorList = new DataElement(DataElement.DATSEQ);
	    		    DataElement profileDescriptor = new DataElement(DataElement.DATSEQ);
	    		    profileDescriptor.addElement(new DataElement(DataElement.UUID, new UUID(0x1105)));
	    			profileDescriptor.addElement(new DataElement(DataElement.U_INT_2, 256));
	    		    	profileDescriptorList.addElement(profileDescriptor);
	    		    	srec.setAttributeValue(0x0009, profileDescriptorList);

	    		    	//Add to public browse group
	    			DataElement elem = new DataElement(DataElement.DATSEQ);
	    			elem.addElement(new DataElement(DataElement.UUID, new UUID(0x1002)));
			    srec.setAttributeValue(0x0005, elem);

//			    update the service db:
			    javax.bluetooth.LocalDevice.getLocalDevice().updateRecord(srec);
        		} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

        		return (Connection) new SessionNotifierImpl (notifier);
          }
          else {
        		StreamConnection streamCon = (StreamConnection) javax.microedition.io.Connector.open (url);
            return (Connection)new OBEXConnection (streamCon);
          }
	}
	
}
