package org.openuat.apps.groupkey;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.StringItem;

import org.openuat.groupkey.GroupKeyMessageHandler;
import org.openuat.groupkey.StringEventListener;


/**
 * @author Christoph Egger
 *
 */
public class appStub implements StringEventListener,CommandListener {

	private GroupKeyMessageHandler comm;
	private String message;
	//Midlet Sachen
	private Display display;
	private Form mainForm,resultForm;
	private Command sendCommand = new Command("Send", Command.OK, 1);
	private Command recvCommand = new Command("Receive", Command.OK, 1);

	private StringItem resultItem;

	public appStub(Display _display){
		display = _display;
		comm = new CommHex();
		Thread commThread = new Thread(comm);
		commThread.start();
	}
	public void handleKeyEvent(byte[] _key, boolean _success) {
		// nothing in here
	}

	public void handleStringEvent(String _data, boolean _success) {
		message = _data;
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
		resultForm.addCommand(sendCommand);
		resultForm.addCommand(recvCommand);
		resultForm.setCommandListener(this);
		display.setCurrent(resultForm);
	}

	public void commandAction(Command c, Displayable s) {
		if (c == sendCommand) {
			sendMsg("testFolder1", "testMsg");
		} else if (c == recvCommand) {
			getMsg("testFolder1");
		} else {}
	}

	public void run(){
		mainForm = new Form("Address");
		mainForm.append("Hello World");
		mainForm.addCommand(sendCommand);
		mainForm.addCommand(recvCommand);
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
