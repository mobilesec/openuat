package org.openuat.apps;

import java.io.IOException;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.openuat.authentication.AuthenticationProgressHandler;
import org.openuat.authentication.exceptions.ConfigurationErrorException;
import org.openuat.authentication.exceptions.InternalApplicationException;
import org.openuat.authentication.relate.RelateAuthenticationProtocol;

import uk.ac.lancs.relate.core.Configuration;
import uk.ac.lancs.relate.core.DeviceException;
import uk.ac.lancs.relate.core.MeasurementManager;


/** This class implements the basic functionality of the IPSec connector applications.
 * It is common to both the client end and the admin end and can not be instantiated
 * directly. Client and admin applications should be derived from it.
 * 
 * The AuthenticationSuccess handler is not implemented as it is specific to which
 * end it should represent.
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public abstract class IPSecConnectorCommon implements AuthenticationProgressHandler {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(IPSecConnectorCommon.class);
	
	/** This name is used as a prefix for transmitting the XML-encoded
	 * configuration block from the admin end to the client end.
	 */
	protected final static String BLOCKNAME_CONFIG = "config";
	
	/** This name is used as a prefix for transmitting the certificate
	 * from the admin end to the client end.
	 */
	protected final static String BLOCKNAME_CERTIFICATE = "certificate";

	/** True if this is the admin end, false if it is the client end.
	 * It just remembers whatever value was given to the constructor.
	 */
	protected boolean adminEnd;
	
	/** Holds our one and only instance of the relate authentication
	 * protocol. It is initialized by the constructor. For the client
	 * end, the authentication server is automatically started. 
	 */
	protected RelateAuthenticationProtocol authp;
	
	/** Holds our one and only instance of the measurement manager.
	 * It is initialized by the constructor.
	 */
	protected MeasurementManager manager;
	
	/** This variable <b>must</b> be initalized in the constructor of the 
	 * derived class, or at least before the AuthenticationProgress handler
	 * has a chance of being called.
	 */
	

	/** @param adminEnd Should be set to true on the admin side (i.e. for IPSecConnectorAdmin) and false for the
	 * 					client side (i.e. for IPSecConnectorClient).
	 * @param serialPort The serial port to use. A special value of null means that we are in simulation mode and
	 *                   not using any dongles at all (just assuming that dongle authentication always succeeds).
	 */
	protected IPSecConnectorCommon(boolean adminEnd, Configuration relateConf, MeasurementManager mm) 
			throws DeviceException, ConfigurationErrorException, InternalApplicationException, IOException {
		this.adminEnd = adminEnd;

		if (System.getProperty("os.name").startsWith("Windows CE")) {
			System.out.println("Configuring log4j");
			PropertyConfigurator.configure("log4j.properties");
		}

		if (relateConf != null) {
			logger.debug("Registering MeasuementManager with SerialConnector");
        	//SerialConnector connector = SerialConnector.getSerialConnector(relateConf.getDevicePortName(), relateConf.getDeviceType());
        	//connector.registerEventQueue(EventDispatcher.getDispatcher().getEventQueue());
            // this will start the SerialConnector thread and start listening for incoming measurements
            manager = mm;
		}
		else {
			logger.info("No serial port specified, using simulation mode");
			manager = null;
			RelateAuthenticationProtocol.setSimulationMode(true);
		}
		logger.debug("Creating RelateAuthenticationProtocol");
		authp = new RelateAuthenticationProtocol((relateConf != null ? relateConf.getPort() : null), manager, !adminEnd, true, null);
		authp.addAuthenticationProgressHandler(this);
		
		if (! adminEnd) {
			logger.debug("Client end, starting authentication server");
			authp.startServer();
		}
	}
	
	/** This is an implementation of the AuthenticationProgressHandler interface. */
	public void AuthenticationFailure(Object sender, Object remote, Exception e, String msg){
		logger.info("Received relate authentication failure event with " + remote);
		Throwable exc = e;
		while (exc != null) {
			logger.info("Exception: " + exc);
			exc = exc.getCause();
		}
		if (msg != null)
			logger.info("Message: " + msg);
		
		// TODO: display an error message and ask for retry
	}

	/** This is an implementation of the AuthenticationProgressHandler interface. */
	public void AuthenticationProgress(Object sender, Object remote, int cur, int max, String msg){
		logger.debug("Received relate authentication progress event with " + remote + " " + cur + " out of " + max + ": " + msg);
	}
}

