package org.openuat.apps;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

public class Log4jDummyPrinterApp {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(Log4jDummyPrinterApp.class);

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		if (System.getProperty("os.name").startsWith("Windows CE")) {
			System.out.println("Configuring log4j");
			PropertyConfigurator.configure("log4j.properties");
		}

		logger.debug("TEST 1");
		logger.info("TEST 2");
		logger.warn("TEST 3");
		logger.error("TEST 4");
		logger.fatal("TEST 5");
	}

}
