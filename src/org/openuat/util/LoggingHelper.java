/* Copyright Rene Mayrhofer
 * File created 2007-05-02
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.util;

import java.io.FileInputStream;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingHelper {
	public final static String LOGGING_PROPERTIES_FILE = "logging.properties"; 
	
	public static void init() {
		try {
			FileInputStream fis = new FileInputStream(LOGGING_PROPERTIES_FILE);
		    LogManager.getLogManager().readConfiguration(fis);
		} catch (IOException e) {
			Logger log = Logger.getAnonymousLogger();
			log.log(Level.SEVERE, "Unable to load logging properties file " + LOGGING_PROPERTIES_FILE, e);
		}
	}
	
	public static void debugWithException(Logger logger, String msg, Exception e) {
//#if cfg.haveReflectionSupport
		String stackTrace = "";
		if (msg != null)
			stackTrace = msg + "\n";
		
		if (logger.isLoggable(Level.FINER)) {
			for (int j=0; j<e.getStackTrace().length; j++)
				stackTrace += e.getStackTrace()[j].toString() + "\n";
			logger.finer(stackTrace);
		}
//#endif
	}
}
