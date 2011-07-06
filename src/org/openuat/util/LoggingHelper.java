/* Copyright Rene Mayrhofer
 * File created 2007-05-02
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.util;

import java.util.logging.Logger;

public class LoggingHelper {
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
