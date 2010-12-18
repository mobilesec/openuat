/* Copyright Rene Mayrhofer
 * File created 2006-09-01
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package org.apache.log4j;

/** This is a wrapper class around microlog that will be added to MIDlets. */
public class Logger {
  static net.sf.microlog.Logger realLogger = net.sf.microlog.Logger.getLogger();

  /** Map trace to debug, since microlog doesn't implement the former. */
  public
  void trace(Object message) {
    realLogger.debug(message);
  }
  
  /** Map trace to debug, since microlog doesn't implement the former. */
  public
  void trace(Object message, Throwable t) {
    realLogger.debug(message, t);
  }

  public
  void debug(Object message) {
    realLogger.debug(message);
  }
  
  public
  void debug(Object message, Throwable t) {
    realLogger.debug(message, t);
  }

  public
  void error(Object message) {
    realLogger.error(message);
  }

  public
  void error(Object message, Throwable t) {
    realLogger.error(message, t);
  }

  public
  void fatal(Object message) {
    // microlog doesn't implement fatal
    realLogger.error(message);
  }
  
  public
  void fatal(Object message, Throwable t) {
    // microlog doesn't implement fatal
    realLogger.error(message, t);
  }

  public
  static
  Logger getLogger(String name) {
    return new Logger();
  }	

  public
  void info(Object message) {
    realLogger.info(message);
  }
  
  void info(Object message, Throwable t) {
    realLogger.info(message, t);
  }

  public
  void warn(Object message) {
    realLogger.warn(message);
  }
  
  public
  void warn(Object message, Throwable t) {
    realLogger.warn(message, t);
  }

  /** Map trace to debug, since microlog doesn't implement the former. */
  public
  boolean isTraceEnabled() {
	//return realLogger.isDebugEnabled();
	return false;
  }
  
  public
  boolean isDebugEnabled() {
	//return realLogger.isDebugEnabled();
	return false;
  }
  
  public
  boolean isInfoEnabled() {
	return realLogger.isInfoEnabled();
  }

  public
  boolean isWarnEnabled() {
	return realLogger.isWarnEnabled();
  }

  public
  boolean isErrorEnabled() {
	return realLogger.isErrorEnabled();
  }
}
