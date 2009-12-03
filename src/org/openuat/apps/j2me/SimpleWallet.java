/* Copyright Rene Mayrhofer
 * File created 2007-01-25
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.apps.j2me;

import java.util.Vector;

import javax.microedition.midlet.*;
import javax.microedition.rms.InvalidRecordIDException;
import javax.microedition.rms.RecordEnumeration;
import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;
import javax.microedition.rms.RecordStoreFullException;
import javax.microedition.rms.RecordStoreNotFoundException;
import javax.microedition.rms.RecordStoreNotOpenException;
import javax.microedition.lcdui.*;

import net.sf.microlog.Level;
import net.sf.microlog.appender.FormAppender;
import net.sf.microlog.ui.LogForm;

import org.apache.log4j.Logger;

/** This MIDlet demonstrates basic encryption on J2ME by keeping a
 * password-protected list of strings.
 * 
 * @author Rene Mayrhofer
 */
public class SimpleWallet extends MIDlet implements CommandListener {
	
	private TextField mainPw;
	private TextField newDesc;
	private TextField newPass;
	private Form loginForm;
	private Form newPwForm;
	
	List pw_list;

	Command exit;
	Command login;
	Command back;
	Command log;
	Command newPw;
	Command enterPw;

	Display display;
	
	private Vector names;
	private Vector passwords;
	
	LogForm logForm;
	
	RecordStore mainPwRs;
	RecordStore pwListRs;

	// our logger
	Logger logger = Logger.getLogger("org.openuat.apps.j2me.SimpleWallet");
	
	private void error(String e, Throwable t, Displayable returnTo) {
		logger.error(e, t);
		
		Alert a = new Alert(
				"Error",
				e +	(t != null ? " with: " + t : ""), 
				null, AlertType.ERROR);
		a.setTimeout(Alert.FOREVER);
		display.setCurrent(a, returnTo);
	}
	
	public SimpleWallet() {
		display = Display.getDisplay(this);

		net.sf.microlog.Logger logBackend = net.sf.microlog.Logger.getLogger();
		logForm = new LogForm();
		logForm.setDisplay(display);
		logBackend.addAppender(new FormAppender(logForm));
		//logBackend.addAppender(new RecordStoreAppender());
		logBackend.setLogLevel(Level.DEBUG);
		logger.info("Microlog initialized");
		
		loginForm = new Form("Main password");
	    mainPw = new TextField("Password:", "", 30, TextField.ANY);
		exit = new Command("Exit", Command.EXIT, 1);
	    login = new Command("Login", Command.OK, 2);
		back = new Command("Back", Command.BACK, 1);
		log = new Command("Log", Command.ITEM, 3);
		newPw = new Command("New Entry", Command.ITEM, 2);
		enterPw = new Command("Add Entry", Command.ITEM, 2);
		
		loginForm.append(mainPw);
	    loginForm.addCommand(exit);
	    loginForm.addCommand(login);
	    loginForm.setCommandListener(this);

		newPwForm = new Form("New password");
	    newDesc = new TextField("Description:", "", 30, TextField.ANY);
	    newPass = new TextField("Password:", "", 30, TextField.ANY);
	    newPwForm.append(newDesc);
	    newPwForm.append(newPass);
	    newPwForm.addCommand(enterPw);
	    newPwForm.addCommand(back);
	    newPwForm.setCommandListener(this);

		pw_list = new List("Passwords", Choice.IMPLICIT); //the list of passwords
		pw_list.addCommand(exit);
		pw_list.addCommand(log);
		pw_list.addCommand(newPw);
		pw_list.setCommandListener(this);

		try {
			mainPwRs = RecordStore.openRecordStore("MainPwHash", true);
			pwListRs = RecordStore.openRecordStore("PwList", true);
		} catch (RecordStoreFullException e) {
			error(null, e, loginForm);
		} catch (RecordStoreNotFoundException e) {
			error(null, e, loginForm);
		} catch (RecordStoreException e) {
			error(null, e, loginForm);
		}
	}

	public void startApp() {
		logForm.setPreviousScreen(pw_list);
		display.setCurrent(loginForm);
	}

	public void commandAction(Command com, Displayable dis) {
		if (com == exit) { //exit triggered from the main form
			destroyApp(false);
			notifyDestroyed();
		}
		else if (com == List.SELECT_COMMAND) {
			if (dis == pw_list) {
				int i = pw_list.getSelectedIndex(); 
				if (i >= 0) {
					Alert a = new Alert(
							"Password for '" + (String) names.elementAt(i) + "'",
							(String) passwords.elementAt(i), null, AlertType.ERROR);
					a.setTimeout(Alert.FOREVER);
					display.setCurrent(a, pw_list);
				}
			}
		}
		else if (com == login) {
			// check if PW fits
			if (mainPw.getString().equals("test")) {
				// yes - load and decrypt
				RecordEnumeration re;
				try {
					names = new Vector();
					passwords = new Vector();
					re = pwListRs.enumerateRecords(null, null, false);
					while (re.hasNextElement()) {
						byte nextRec[] = re.nextRecord();
						
						// TODO: decrypt
						
						String codedPw = new String(nextRec);
						int delim = codedPw.indexOf(0);
						if (delim > 0) {
							String desc = codedPw.substring(0, delim);
							String pw = codedPw.substring(delim+1);
							
							names.addElement(desc);
							passwords.addElement(pw);
						}
					}
				} catch (RecordStoreNotOpenException e) {
					error(null, e, loginForm);
				} catch (InvalidRecordIDException e) {
					error(null, e, loginForm);
				} catch (RecordStoreException e) {
					error(null, e, loginForm);
				}
				
				// and switch to list
				showPwList();
			}
			else {
				Alert a = new Alert("Wrong password",
						"Password doesn't match", null, AlertType.ERROR);
				a.setTimeout(Alert.FOREVER);
				display.setCurrent(a, loginForm);
			}
		}
		else if (com == newPw) {
			newDesc.setString("");
			newPass.setString("");
			display.setCurrent(newPwForm);
		}
		else if (com == enterPw) {
			String desc = newDesc.getString();
			String pass = newPass.getString();
			names.addElement(desc);
			passwords.addElement(pass);

			String coded = desc + (char) 0 + pass;
			
			// TODO: encrypt
			
			byte[] encrypted = coded.getBytes();
			try {
				pwListRs.addRecord(encrypted, 0, encrypted.length);
			} catch (RecordStoreNotOpenException e) {
				error(null, e, pw_list);
			} catch (RecordStoreFullException e) {
				error(null, e, pw_list);
			} catch (RecordStoreException e) {
				error(null, e, pw_list);
			}
			
			showPwList();
		}
		else if (com == back) {
			showPwList();
		}
		else if (com == log) {
			display.setCurrent(logForm);
		}

	}

	private void showPwList() {
		// refresh the password list
		pw_list.deleteAll();
		
		for (int i=0; i<names.size(); i++) {
			String desc = (String) names.elementAt(i);
			pw_list.append(desc, null);
		}
		
		display.setCurrent(pw_list);
	}

	public void pauseApp() {
		// nothing to do when the app is paused, leave the background actions running
	}

	public void destroyApp(boolean unconditional) {
		if (mainPwRs != null) {
			try {
				mainPwRs.closeRecordStore();
			} catch (RecordStoreNotOpenException e) {
			} catch (RecordStoreException e) {
			}
		}
		if (pwListRs != null) {
			try {
				pwListRs.closeRecordStore();
			} catch (RecordStoreNotOpenException e) {
			} catch (RecordStoreException e) {
			}
		}
	}
}
