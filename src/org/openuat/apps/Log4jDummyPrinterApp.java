package org.openuat.apps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Log4jDummyPrinterApp {
	/** Our logger. */
	private static Logger logger = LoggerFactory.getLogger(Log4jDummyPrinterApp.class.getName());

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
		logger.warn("TEST 3");
		logger.error("TEST 4");
	}

}
