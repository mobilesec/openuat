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

import org.apache.commons.codec.binary.Hex;
import org.openuat.authentication.SimpleKeyAgreement;

public class appPairStub implements ifListener,CommandListener {

	private ifComm comm;
	private String message;
	private Display display;
	private Form mainForm,resultForm;
	private Command sendCommand1 = new Command("SendKey1", Command.OK, 1);
	private Command recvCommand1 = new Command("ReceiveKey1", Command.OK, 1);
	
	private Command sendCommand2 = new Command("SendKey2", Command.OK, 1);
	private Command recvCommand2 = new Command("ReceiveKey2", Command.OK, 1);	
	private SimpleKeyAgreement ka1;
	private SimpleKeyAgreement ka2;
	private StringItem resultItem;

	public appPairStub(Display _display){
		display = _display;
		comm = new CommPlain();
		Thread commThread = new Thread(comm);
		commThread.start();
	}
	public void handleKeyEvent(byte[] _key, boolean _success) {
		// nothing in here
	}

	public void handleStringEvent(String _data, boolean _success) {
		message = _data;
		StringTokenizer fileStringTokenizer = new StringTokenizer(_data,"|");
		StringTokenizer tempStringTokenizer = new StringTokenizer(fileStringTokenizer.nextToken(),"-");
		message=message+tempStringTokenizer.countTokens();
		if( tempStringTokenizer.countTokens()>2)
		{
		String timestamp = tempStringTokenizer.nextToken();
		String command = tempStringTokenizer.nextToken();
		message=message+"Command:"+command;
		if(command.compareTo("pubkey1")==0)
		{try {
			message=message+"in pubkey1:"+" Token:"+new String(Hex.decodeHex(tempStringTokenizer.peek().toCharArray()));
			
				ka2.addRemotePublicKey(Hex.decodeHex(tempStringTokenizer.nextToken().toCharArray()));
				message=message+"Session Key2:"+ new String(Hex.encodeHex((ka2.getSessionKey())));
			} catch (Exception e) {
				message=message+e.toString();
			}
		}
		else if(command.compareTo("pubkey2")==0)
		{
			try {
			message=message+"in pubkey2:"+" Token:"+new String(Hex.decodeHex(tempStringTokenizer.peek().toCharArray()));
			
				ka1.addRemotePublicKey(Hex.decodeHex(tempStringTokenizer.nextToken().toCharArray()));
				message=message+"Session Key1:"+ new String(Hex.encodeHex((ka1.getSessionKey())));
			} catch (Exception e) {
				message=message+e.toString();
			}
		}
		}
		
		synchronized(this){
			this.notify();
		}
	}

	public void sendMsg(String _folder, String _msg){
		(comm).sendMsg(_folder, _msg, this);
	}

	public void getMsg(String _folder){
		(comm).getMsg(_folder, this);
	}

	private void updateDisplay(){
		resultItem = new StringItem(null, message);
		resultForm = new Form("Result");
		resultItem.setText(message);
		resultForm.append(resultItem);
		resultForm.addCommand(sendCommand1);
		resultForm.addCommand(recvCommand1);
		resultForm.addCommand(sendCommand2);
		resultForm.addCommand(recvCommand2);
		resultForm.setCommandListener(this);
		display.setCurrent(resultForm);
	}

	public void commandAction(Command c, Displayable s) {
	
		if (c == sendCommand1) {
			try {
				ka1 = new SimpleKeyAgreement(true);
				ka1.init(true);
				sendMsg("Key1","pubkey1-" + new String(Hex.encodeHex(ka1.getPublicKey())));
				
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			
		} else if (c == recvCommand1) {
			getMsg("Key2");
		} else if (c == sendCommand2) {
			try {
				ka2 = new SimpleKeyAgreement(true);
				ka2.init(true);
				sendMsg("Key2","pubkey2-" + new String(Hex.encodeHex(ka2.getPublicKey())));
				
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			
		} else if (c == recvCommand2) {
			getMsg("Key1");
		} 
			
			
			else {}
	}

	public void run(){
		mainForm = new Form("Address");
		mainForm.append("Hello World");
		mainForm.addCommand(sendCommand1);
		mainForm.addCommand(recvCommand1);
		mainForm.addCommand(sendCommand2);
		mainForm.addCommand(recvCommand2);
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
