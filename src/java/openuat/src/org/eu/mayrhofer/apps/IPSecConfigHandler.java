/* Copyright Rene Mayrhofer
 * File created 2006-03-20
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.apps;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

import org.apache.log4j.Logger;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

/** This class implements reading and writing of the configuration of an IPSec tunnel
 * in XML format.
 *
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class IPSecConfigHandler {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(IPSecConfigHandler.class);
	
	/** This namespace is used for creating the XML config. */
	private final static String Namespace = "http://www.mayrhofer.eu.org/ns/security/ipsecadmin";
	/** This is the name of the XML tag representing the remote gateway. */
	private final static String GatewayTag = "gateway";
	/** This is the name of the XML tag representing the network behind the remote gateway. */
	private final static String RemoteNetworkTag = "remote_network";
	/** This is the name of the XML tag representing the netmask of the network behind the remote gateway. */
	private final static String RemoteNetmaskTag = "remote_netmask";
	/** This is the name of the XML tag representing the netmask of the network behind the remote gateway. */
	private final static String CaDistinguishedNameTag = "ca_dn";
	
	/** The remote gateway. */
	private String gateway;
	/** The network behind the remote gateway. */
	private String remoteNetwork;
	/** The netmask of the network behind the remote gateway. */
	private int remoteNetmask;
	/** The distinguished name of the CA that signed both the gateway and the client
	 * X.509 certificates (only when X.509 authentication is used).
	 */
	private String caDistinguishedName;

	/** This constructor actually does nothing. */
	public IPSecConfigHandler() {
	}

	/** Returns the value to be used for the remote gateway of the IPSec tunnel. */
	public String getGateway() {
		return gateway;
	}
	
	/** Sets the value to be used for the remote gateway of the IPSec tunnel. */
	public void setGateway(String gateway) {
		this.gateway = gateway;
	}
	
	/** Returns the value to be used for the remote network behind the remote gateway of the IPSec tunnel. */
	public String getRemoteNetwork() {
		return remoteNetwork;
	}
	
	/** Sets the value to be used for the remote network behind the remote gateway of the IPSec tunnel. */
	public void setRemoteNetwork(String remoteNetwork) {
		this.remoteNetwork = remoteNetwork;
	}

	/** Returns the value to be used for the netmask of the remote network behind the remote gateway of the IPSec tunnel. */
	public int getRemoteNetmask() {
		return remoteNetmask;
	}
	
	/** Sets the value to be used for the remote network behind the remote gateway of the IPSec tunnel. */
	public void setRemoteNetmask(int remoteNetmask) {
		this.remoteNetmask = remoteNetmask;
	}

	/** Returns the value to be used for the CA distinguished name. */
	public String getCaDistinguishedName() {
		return caDistinguishedName;
	}
	
	/** Sets the value to be used for the CA distinguished name. */
	public void setCaDistinguishedName(String caDistinguishedName) {
		this.caDistinguishedName = caDistinguishedName;
	}
	
	/** Parses an existing configuration and extracts the gateway and optionally the remote 
	 * remote network (if it exists in the configuration).
	 * 
	 * @param input The configuration.
	 * @return true if the configuration could be read and parsed correctly, false otherwise.
	 */
	public boolean parseConfig(Reader input) {
		if (gateway != null || remoteNetwork != null) {
			logger.error("gateway or remote network have already been set. Aborting reading.");
			return false;
		}
		
		try {
			XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
			factory.setNamespaceAware(true);
			XmlPullParser parser = factory.newPullParser();
			
			// use whatever input we get (might be a file or e.g. a socket)
			parser.setInput(input);
			
			// this is the main parser loop
			logger.debug("Beginning parsing of config");
			String lastTag = null;
			int eventType = parser.getEventType();
			do {
				if (eventType == XmlPullParser.START_DOCUMENT) {
					logger.debug("Found XML document start");
				} else if (eventType == XmlPullParser.END_DOCUMENT) {
					logger.debug("Found XML document end");
				} else if (eventType == XmlPullParser.START_TAG) {
					logger.debug("Found tag start: '" + parser.getName() + "'");
					lastTag = parser.getName();
				} else if (eventType == XmlPullParser.END_TAG) {
					logger.debug("Found tag end: '" + parser.getName() + "'");
				} else if (eventType == XmlPullParser.TEXT) {
					int[] startAndLength = new int[2];
					char[] ch = parser.getTextCharacters(startAndLength);
					StringBuffer str = new StringBuffer();
					str.append(ch, startAndLength[0], startAndLength[1]);
					logger.debug("Found text: '" + str + "' at tag '" + lastTag + "'");
					if (lastTag != null && lastTag.equals(GatewayTag)) {
						logger.debug("Associating with gateway field");
						gateway = str.toString();
					} else if (lastTag != null && lastTag.equals(RemoteNetworkTag)) {
						logger.debug("Associating with remote network field");
						remoteNetwork = str.toString();
					} else if (lastTag != null && lastTag.equals(RemoteNetmaskTag)) {
						logger.debug("Associating with remote netmask field");
						remoteNetmask = Integer.parseInt(str.toString());
					} else if (lastTag != null && lastTag.equals(CaDistinguishedNameTag)) {
						logger.debug("Associating with CA distinguished name field");
						caDistinguishedName = str.toString();
					} else
						logger.warn("Encountered unkown tag '" + lastTag + "', ignoring it");
				} else
					logger.error("Encountered unknown event type (" + eventType + "). This should not happen! Error in XmlPullParser.");
				eventType = parser.next();
			} while (eventType != XmlPullParser.END_DOCUMENT);
		} catch (XmlPullParserException e) {
			logger.error("Could not parse config: " + e);
			return false;
		} catch (IOException e) {
			logger.error("Could not get next element from config: " + e);
			return false;
		}
		if (gateway == null) {
			logger.error("Did not find definition for remote gateway in configuration");
			return false;
		}
		return true;
	}
	
	/** This is a small helper to write correctly formatted XML tags with a single value.
	 * 
	 * @param serializer The serializer to write the tag with.
	 * @param tag The tag name to write.
	 * @param value The tag value to write.
	 * @param addNewLine If set to true, a new line will be added after the tag.
	 */ 
	private void writeTag(XmlSerializer serializer, String tag, String value, boolean addNewLine) throws IllegalArgumentException, IllegalStateException, IOException {
		serializer.startTag(Namespace, tag);
		serializer.text(value);
		serializer.endTag(Namespace, tag);
		if (addNewLine)
			serializer.text("\n");
	}
	
	public boolean writeConfig(Writer output) {
		if (gateway == null) {
			logger.error("Can not write configuration without a defined gateway");
			return false;
		}
		
		try {
			XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
			XmlSerializer serializer = factory.newSerializer();
			
			// again, use whatever we were given
			serializer.setOutput(output);
			
			serializer.startDocument(Namespace, new Boolean(true));
			serializer.text("\n");
			
			writeTag(serializer, GatewayTag, gateway, true);
			// if we have a defined remote network, also write that...
			if (remoteNetwork != null) {
				writeTag(serializer, RemoteNetworkTag, remoteNetwork, true);
				writeTag(serializer, RemoteNetmaskTag, Integer.toString(remoteNetmask), true);
			}
			if (caDistinguishedName != null)
				writeTag(serializer, CaDistinguishedNameTag, caDistinguishedName, true);
			
			// finished with the config
			serializer.endDocument();
		} catch (XmlPullParserException e) {
			logger.error("Could not create config: " + e);
			return false;
		} catch (IllegalArgumentException e) {
			logger.error("Could not write to config: " + e);
			return false;
		} catch (IllegalStateException e) {
			logger.error("Could not write to config: " + e);
			return false;
		} catch (IOException e) {
			logger.error("Could not write to config: " + e);
			return false;
		}
		
		return true;
	}
}
