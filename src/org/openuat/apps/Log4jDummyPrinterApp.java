package org.openuat.apps;

import java.util.logging.Logger;

public class Log4jDummyPrinterApp {
	/** Our logger. */
	private static Logger logger = Logger.getLogger(Log4jDummyPrinterApp.class.getName());

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		/*if (System.getProperty("os.name").startsWith("Windows CE")) {
			System.out.println("Configuring log4j");
			PropertyConfigurator.configure("log4j.properties");
		}*/

		logger.finest("TEST 0");
		logger.finer("TEST 1");
		logger.info("TEST 2");
		logger.warning("TEST 3");
		logger.severe("TEST 4");
	}

}
