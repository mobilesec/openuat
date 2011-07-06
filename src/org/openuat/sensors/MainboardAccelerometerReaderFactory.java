package org.openuat.sensors;

import java.io.FileNotFoundException;
import java.io.IOException;

import java.util.logging.Logger;

public class MainboardAccelerometerReaderFactory {
	/** Our logger. */
	private static Logger logger = Logger.getLogger(MainboardAccelerometerReaderFactory.class);

	public static AsciiLineReaderBase createInstance(int sampleRate) throws FileNotFoundException {
		if (System.getProperty("os.name").startsWith("Linux")) {
			logger.info("Detected Linux, creating MainboardAccelerometerReader_Linux instance");
			return new MainboardAccelerometerReader_Linux(sampleRate);
		}
		else {
			logger.severe("Reading mainboard accelerometers not (yet) supported with os.name='" + 
					System.getProperty("os.name") + "'");
			return null;
		}
	}


	/////////////////////////// test code begins here //////////////////////////////
	public static void main(String[] args) throws IOException {
		org.openuat.sensors.test.AsciiLineReaderRunner.mainRunner("MainboardAccelerometerReader", args);
	}
}
