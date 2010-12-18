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

import net.sf.microlog.Logger;
import net.sf.microlog.util.Properties;

/**
 * This MIDlet shows how to use a property file for configuration.
 * 
 * @author Johan Karlsson (johan.karlsson@jayway.se)
 * 
 */
public class PropertiesConfigMidlet extends MIDlet implements CommandListener {

	private static final Logger log = Logger.getLogger(PropertiesConfigMidlet.class);

	private static boolean firstTime = true;

	private Display display;
	private Form form = new Form("PropertiesConfigMIDlet");
	private Command logCommand;
	private Command exitCommand;

	public PropertiesConfigMidlet() {

	}

	protected void startApp() throws MIDletStateChangeException {
		
		// We only want to configure Microlog the first time
		if (firstTime) {
			Properties properties = new Properties(this,
					"/mymicrolog.properties");
			log.configure(properties);
			log.info("startApp() first time");
			firstTime = false;
		} else {
			log.info("startApp() again");
		}

		if (display == null) {
			log.debug("Creating Display object & initializing our Form.");
			display = Display.getDisplay(this);
			logCommand = new Command("DoLog", Command.SCREEN, 1);
			form.addCommand(logCommand);
			exitCommand = new Command("Exit", Command.EXIT, 1);
			form.addCommand(exitCommand);
			form.setCommandListener(this);
		}

		log.debug("Display our form.");
		display.setCurrent(form);
	}

	protected void pauseApp() {
		log.info("pauseApp()");
	}

	protected void destroyApp(boolean conditional)
			throws MIDletStateChangeException {
		log.info("destroyApp()");
		try {
			log.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		notifyDestroyed();
	}

	public void commandAction(Command command, Displayable displayable) {
		log.debug("commandAction()");

		if (command == exitCommand) {
			log
					.debug("Calling notifyDestroyed() to inform the AMS that we want to quit.");
			notifyDestroyed();
		}
	}
}
