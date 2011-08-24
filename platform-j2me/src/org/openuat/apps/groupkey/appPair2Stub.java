/* Copyright Lukas Wallentin
 * File created 2009-01-12
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package org.openuat.apps.groupkey;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.StringItem;

import net.mypapit.java.StringTokenizer;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.openuat.authentication.SimpleKeyAgreement;
import org.openuat.authentication.exceptions.InternalApplicationException;
import org.openuat.authentication.exceptions.KeyAgreementProtocolException;

/**
 * @author Lukas
 *
 */
public class appPair2Stub implements ifListener,CommandListener {

	private ifPair pair;
	private String message;
	//Midlet Sachen
	private Display display;
	private Form mainForm,resultForm;
	private Command logCommand1 = new Command("Log", Command.OK, 1);
	private Command setCommand1 = new Command("Set1", Command.OK, 1);
	private Command setCommand2 = new Command("Set2", Command.OK, 1);
	private Command setCommand3 = new Command("Set3", Command.OK, 1);	
	private SimpleKeyAgreement ka1;
	private SimpleKeyAgreement ka2;
	private StringItem resultItem;

	public appPair2Stub(Display _display){
		display = _display;
		pair = new Pair();
		Thread commThread = new Thread(pair);
		commThread.start();
	}
	public void handleKeyEvent(byte[] _key, boolean _success) {
		// nothing in here
	}

	public void handleStringEvent(String _data, boolean _success) {
	
		}
		
	
	private void updateDisplay(){
		resultItem = new StringItem(null, message);
		resultForm = new Form("Result");
		resultItem.setText(message);
		resultForm.append(resultItem);
		resultForm.addCommand(logCommand1);
		resultForm.addCommand(setCommand1);
		resultForm.addCommand(setCommand2);
		resultForm.addCommand(setCommand3);
		resultForm.setCommandListener(this);
		display.setCurrent(resultForm);
	}

	public void commandAction(Command c, Displayable s) {
		byte[] key1 = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
		byte[] key2 = new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 0};
		
		if (c == logCommand1) {
			message=pair.getLog();	
			synchronized(this){
				this.notify();
			}
			
		} else if (c == setCommand1) {
			pair.setName("ichi");
			pair.setKeyPair("ni", key1);
			
		} else if (c == setCommand2) {
			pair.setName("ni");		
			pair.setKeyPair("ichi", key1);
			pair.setKeyPair("san", key2);
		} else if (c == setCommand3) {
			pair.setName("san");
			pair.setKeyPair("ni", key2);
		} 
	}

	public void run(){
		mainForm = new Form("Address");
		mainForm.append("Hello World");
		mainForm.addCommand(logCommand1);
		mainForm.addCommand(setCommand1);
		mainForm.addCommand(setCommand2);
		mainForm.addCommand(setCommand3);
		mainForm.setCommandListener(this);
		display.setCurrent(mainForm);
		while (true){
			try {
				synchronized (this) {
					this.wait();
					updateDisplay();
				}
			} catch (InterruptedException e) {
			}
		}
	}
}
