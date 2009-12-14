/* Copyright Rene Mayrhofer
 * File created 2009-12-01
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
import org.openuat.authentication.exceptions.InternalApplicationException;
import org.openuat.util.Hash;
import org.openuat.util.SimpleBlockCipher;

/** This MIDlet demonstrates basic encryption on J2ME by keeping a
 * password-protected list of strings.
 * 
 * @author Rene Mayrhofer
 */
public class SimpleWallet extends MIDlet implements CommandListener {
	public final static String RECORDSTORE_MAINPW = "MainPwHash";
	public final static String RECORDSTORE_LIST = "PwList";
	
	private TextField mainPw;
	private TextField newDesc;
	private TextField newPass;
	private Form loginForm;
	private Form newPwForm;
	
	List pw_list;

	Command exit;
	Command login;
	Command reset;
	Command back;
	Command log;
	Command newPw;
	Command enterPw;

	Display display;
	
	private Vector names;
	private Vector passwords;
	private byte[] mainPwHash;
	
	LogForm logForm;
	
	RecordStore mainPwRs;
	RecordStore pwListRs;

	// our logger
	Logger logger = Logger.getLogger("org.openuat.apps.j2me.SimpleWallet");
	
	private void error(String e, Throwable t, Displayable returnTo) {
		logger.error(e, t);
		
		Alert a = new Alert(
				"Error ",
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
	    reset = new Command("Reset all data", Command.ITEM, 2);
		back = new Command("Back", Command.BACK, 1);
		log = new Command("Log", Command.ITEM, 3);
		newPw = new Command("New Entry", Command.ITEM, 2);
		enterPw = new Command("Add Entry", Command.ITEM, 2);
		
		loginForm.append(mainPw);
	    loginForm.addCommand(exit);
	    loginForm.addCommand(login);
	    loginForm.addCommand(reset);
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
			mainPwRs = RecordStore.openRecordStore(RECORDSTORE_MAINPW, true);
			pwListRs = RecordStore.openRecordStore(RECORDSTORE_LIST, true);
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
		
		// selected a specific list entry
		else if (com == List.SELECT_COMMAND) {
			// this is only defined for the password list
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

		// pressed reset - clear stores
		else if (com == reset) {
			try {
				mainPwRs.closeRecordStore();
				pwListRs.closeRecordStore();
				RecordStore.deleteRecordStore(RECORDSTORE_MAINPW);
				RecordStore.deleteRecordStore(RECORDSTORE_LIST);
				mainPwRs = RecordStore.openRecordStore(RECORDSTORE_MAINPW, true);
				pwListRs = RecordStore.openRecordStore(RECORDSTORE_LIST, true);
			} catch (RecordStoreNotOpenException e) {
				error("Unable to delete records", e, loginForm);
			} catch (InvalidRecordIDException e) {
				error("Unable to delete records", e, loginForm);
			} catch (RecordStoreException e) {
				error("Unable to delete records", e, loginForm);
			}
		}

		// pressed login - check password
		else if (com == login) {
			byte[] mainPwBytes = mainPw.getString().getBytes();
			try {
				mainPwHash = Hash.doubleSHA256(mainPwBytes, false);
			} catch (InternalApplicationException e) {
				error(null, e, loginForm);
				return;
			}
			
			// check if PW fits
			try {
				RecordEnumeration re = mainPwRs.enumerateRecords(null, null, false);

				if (! re.hasNextElement()) {
					// no password yet, setting the entered one
					// TODO: ask for password twice
					mainPwRs.addRecord(mainPwHash, 0, mainPwHash.length);
				}
				else {
					// we already stored a password, so compare now
					// Note: in contrast to _any_ other API, the records start counting at 1....
					byte[] storedHash = re.nextRecord();
					if (storedHash.length != mainPwHash.length)
						logger.warn("Expected to read " + mainPwHash.length + " bytes password hash, but got " +
								storedHash.length);
						
					boolean equals = true;
					for (int i=0; i<storedHash.length && i<mainPwHash.length && equals; i++)
						if (storedHash[i] != mainPwHash[i]) equals=false;
					if (equals) {
						// yes - load and decrypt
						loadPasswords();
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
			} catch (RecordStoreNotOpenException e) {
				error(null, e, loginForm);
			} catch (RecordStoreFullException e) {
				error(null, e, loginForm);
			} catch (InvalidRecordIDException e) {
				error(null, e, loginForm);
			} catch (RecordStoreException e) {
				error(null, e, loginForm);
			} catch (InternalApplicationException e) {
				error(null, e, loginForm);
			}
		}

		// selected menu option to add new password - simply switch to screen
		else if (com == newPw) {
			newDesc.setString("");
			newPass.setString("");
			display.setCurrent(newPwForm);
		}
		
		// entered a new password - encrypt and store it
		else if (com == enterPw) {
			String desc = newDesc.getString();
			String pass = newPass.getString();
			names.addElement(desc);
			passwords.addElement(pass);

			String coded = desc + (char) 0 + pass;
			byte[] plain = coded.getBytes();
			
			System.out.println("Storing password '" + coded + "'");
			System.out.println("Encrypting with key '" + Hash.getHexString(mainPwHash) + "'");
			
			SimpleBlockCipher c = new SimpleBlockCipher(false);
			
			try {
				byte[] encrypted = c.encrypt(plain, plain.length*8, mainPwHash);
				System.out.println("Writing encrypted string '" + Hash.getHexString(encrypted) + "' with " +
						plain.length + " plain text length to record store");
				byte[] encryptedWithLength = new byte[encrypted.length+1];
				System.arraycopy(encrypted, 0, encryptedWithLength, 0, encrypted.length);
				// and append the plain text length in bytes
				encryptedWithLength[encrypted.length] = (byte) plain.length;
				pwListRs.addRecord(encryptedWithLength, 0, encryptedWithLength.length);
			} catch (RecordStoreNotOpenException e) {
				error(null, e, pw_list);
			} catch (RecordStoreFullException e) {
				error(null, e, pw_list);
			} catch (RecordStoreException e) {
				error(null, e, pw_list);
			} catch (InternalApplicationException e) {
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
	
	// load and decrypt passwords from record store
	private void loadPasswords() throws InvalidRecordIDException, RecordStoreException, InternalApplicationException {
		RecordEnumeration re;
		SimpleBlockCipher c = new SimpleBlockCipher(false);

		System.out.println("Decrypting with key '" + Hash.getHexString(mainPwHash) + "'");

		names = new Vector();
		passwords = new Vector();
		re = pwListRs.enumerateRecords(null, null, false);
		while (re.hasNextElement()) {
			byte nextRec[] = re.nextRecord();
			int numPlaintextBytes = nextRec[nextRec.length-1];
			if (numPlaintextBytes<0) numPlaintextBytes+=0x100;
			System.out.println("Loaded encrypted string '" + Hash.getHexString(nextRec) + 
					"' from record store, trying to decrypt " + numPlaintextBytes +
					" plaintext bytes from it");
			byte[] ciphertext = new byte[nextRec.length-1];
			System.arraycopy(nextRec, 0, ciphertext, 0, ciphertext.length);
			
			// decrypting
			byte[] decrypted = c.decrypt(ciphertext, numPlaintextBytes*8, mainPwHash);
			
			String codedPw = new String(decrypted);
			System.out.println("Decoded password string to '" + codedPw + "'");
				
			int delim = codedPw.indexOf(0);
			if (delim > 0) {
				String desc = codedPw.substring(0, delim);
				String pw = codedPw.substring(delim+1);
				System.out.println("Adding description '" + desc + "' with password '" + pw + "'");
				
				names.addElement(desc);
				passwords.addElement(pw);
			}
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
