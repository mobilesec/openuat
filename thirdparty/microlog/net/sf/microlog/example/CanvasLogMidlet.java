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

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.midlet.MIDlet;
import javax.microedition.midlet.MIDletStateChangeException;

import net.sf.microlog.Level;
import net.sf.microlog.Logger;
import net.sf.microlog.appender.CanvasAppender;

/**
 * An example midlet that shows how to use the <code>CanvasAppender</code>
 * 
 * @author Johan Karlsson
 * @since 0.6
 */
public class CanvasLogMidlet extends MIDlet implements CommandListener {

	private final static Logger log = Logger.getLogger();

	private Display display;

	private CanvasAppender canvasAppender;

	private Command logCommand;

	private Command exitCommand;

	private int nofLoggedMessages;
	
	public CanvasLogMidlet() {
		super();
		canvasAppender = new CanvasAppender();
		log.addAppender(canvasAppender);
		log.setLogLevel(Level.DEBUG);
		log.info("Setup of log finished");
		log.debug("Message 1");
		log.debug("Message 2");
		log.debug("Message 3");
		log.debug("Message 4");
		log.error("This error message shall trigger sending an SMS message.");
	}

	protected void startApp() throws MIDletStateChangeException {
		log.info("Starting app");

		if (display == null) {
			display = Display.getDisplay(this);
			Canvas canvas = (Canvas) canvasAppender;
			logCommand = new Command("DoLog", Command.SCREEN, 1);
			canvas.addCommand(logCommand);
			exitCommand = new Command("Exit", Command.EXIT, 1);
			canvas.addCommand(exitCommand);
			canvas.setCommandListener(this);
			display.setCurrent(canvas);
		}
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
