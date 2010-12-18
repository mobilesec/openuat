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

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.midlet.MIDlet;
import javax.microedition.midlet.MIDletStateChangeException;

import net.sf.microlog.Level;
import net.sf.microlog.Logger;
import net.sf.microlog.appender.ConsoleAppender;
import net.sf.microlog.appender.RecordStoreAppender;
import net.sf.microlog.format.PatternFormatter;
import net.sf.microlog.util.ManualProperties;

/**
 * An example midlet that shows how to do the manual configuration.
 * 
 * @author Johan Karlsson
 * 
 */
public class ManualConfigMidlet extends MIDlet implements CommandListener {

	private final static Logger log = Logger.getLogger();

	private Display display;

	private Form form;

	private Command logCommand;

	private Command exitCommand;

	private int nofLoggedMessages;

	public ManualConfigMidlet() {
		super();
		ConsoleAppender consoleAppender = new ConsoleAppender();
		log.addAppender(consoleAppender);
		RecordStoreAppender rsAppender = new RecordStoreAppender();
		ManualProperties props = new ManualProperties();
		props.put(RecordStoreAppender.RECORD_STORE_MAX_ENTRIES_STRING, "50");
		rsAppender.configure(props);
		rsAppender.setFormatter(new PatternFormatter());
		log.addAppender(rsAppender);
		log.setLogLevel(Level.DEBUG);
		log.info("appender 1 added");
		rsAppender = new RecordStoreAppender();
		log.addAppender(rsAppender);
		log.info("appender 2 added");
		rsAppender = new RecordStoreAppender();
		log.addAppender(rsAppender);
		log.info("appender 3 added");
		log.info("Setup of log finished");
	}

	protected void startApp() throws MIDletStateChangeException {
		log.info("Starting app");

		if (display == null) {
			display = Display.getDisplay(this);
			form = new Form("SyslogMIDlet");
			logCommand = new Command("DoLog", Command.SCREEN, 1);
			form.addCommand(logCommand);
			exitCommand = new Command("Exit", Command.EXIT, 1);
			form.addCommand(exitCommand);
			form.setCommandListener(this);
		}

		display.setCurrent(form);
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

	public void commandAction(Command command, Displayable displayable) {
		if (command == logCommand) {
			logMessage();
		} else if (command == exitCommand) {
			notifyDestroyed();
		}

	}

	/**
	 * Log a message.
	 */
	private void logMessage() {
		log.debug("User pressed the log button. " + nofLoggedMessages++);
	}
}
