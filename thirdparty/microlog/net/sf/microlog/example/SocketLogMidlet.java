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

import net.sf.microlog.Level;
import net.sf.microlog.Logger;
import net.sf.microlog.appender.SocketAppender;

/**
 * An example midlet that shows how to use the <code>SocketAppender</code>,
 * which uses a <code>SocketConnection</code>. This requires MIDP 2.0 or
 * greater.
 * 
 * @author Johan Karlsson
 * @since 0.6
 */
public class SocketLogMidlet extends MIDlet {

	private final static Logger log = Logger.getLogger();

	public SocketLogMidlet() {
		super();
		SocketAppender socketAppender = new SocketAppender();
		socketAppender.setLinger(20);
		log.addAppender(socketAppender);
		log.setLogLevel(Level.DEBUG);
		log.info("Setup of log finished");
		log.debug("Message 1");
		log.debug("Message 2");
		log.debug("Message 3");
		log.debug("Message 4");
		log.error("Sending an error message.");
	}

	protected void startApp() throws MIDletStateChangeException {
		log.info("Starting app");
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
