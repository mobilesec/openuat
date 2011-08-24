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
package net.sf.microlog.ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.midlet.MIDlet;
import javax.microedition.midlet.MIDletStateChangeException;

import net.sf.microlog.bluetooth.BluetoothMessageReceiver;
import net.sf.microlog.bluetooth.BluetoothSerialServerThread;

/**
 * The <code>BluetoothLogViewerMidlet</code> is used for receiving logging
 * messages from a <code>BluetoothSerialAppender</code>.
 * 
 * @author Johan Karlsson (johan.karlsson@jayway.se)
 * 
 * @since 0.9
 */
public class BluetoothLogViewerMidlet extends MIDlet implements
		CommandListener, BluetoothMessageReceiver {

	private Display display;
	private Form form;
	private Command exitCommand;

	private BluetoothSerialServerThread serverThread;

	protected void startApp() throws MIDletStateChangeException {
		if (display == null) {
			display = Display.getDisplay(this);
			form = new Form("BTServerMIDlet");
			exitCommand = new Command("Exit", Command.EXIT, 1);
			form.addCommand(exitCommand);
			form.setCommandListener(this);
		}

		display.setCurrent(form);

		serverThread = new BluetoothSerialServerThread();
		serverThread.setMessageReceiver(this);
		serverThread.start();
	}

	protected void pauseApp() {
		
	}

	protected void destroyApp(boolean unconditonal)
			throws MIDletStateChangeException {
	}

	
	public void commandAction(Command command, Displayable displayable) {
		if (command == exitCommand) {
			notifyDestroyed();
		}

	}
	
	/**
	 * Implementation of the <code>BluetoothMessageReceiver</code> interface. This is called when a Bluetooth message is recieved.
	 */
	public void messageReceived(String message) {
		if(form != null && message != null){
			form.append(message);
		}
	}
}
