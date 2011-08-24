/*
 * Copyright 2008 The Microlog project @sourceforge.net
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.sf.microlog.example;

import java.io.IOException;

import javax.microedition.midlet.MIDlet;
import javax.microedition.midlet.MIDletStateChangeException;

import net.sf.microlog.Logger;
import net.sf.microlog.appender.ConsoleAppender;
import net.sf.microlog.format.PatternFormatter;

/**
 * An example midlet that shows how to do the manual configuration.
 * 
 * @author Johan Karlsson
 * 
 */
public class PatternFormatMidlet extends MIDlet {

	private final static Logger log = Logger.getLogger();

	public PatternFormatMidlet() {
		super();
		ConsoleAppender consoleAppender = new ConsoleAppender();
		PatternFormatter patternFormatter = new PatternFormatter();
		patternFormatter.setPattern("%r: [%t] %P %m %T");
		consoleAppender.setFormatter(patternFormatter);
		log.addAppender(consoleAppender);
		log.info("Setup of log finished");
	}

	protected void startApp() throws MIDletStateChangeException {
		log.info("Starting app");
		
		try {
			Thread.sleep(4500);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		log.info("Another message");
	}

	protected void pauseApp() {
		log.info("Pausing app");

	}

	protected void destroyApp(boolean conditional)
			throws MIDletStateChangeException {
		log.info("Destroying app");
		try {
			log.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		notifyDestroyed();
	}

}
