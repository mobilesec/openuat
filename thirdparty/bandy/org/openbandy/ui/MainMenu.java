/**
 *  Filename: MainMenu.java (in org.openbandy.ui)
 *  This file is part of the OpenBandy project.
 * 
 *  OpenBandy is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  any later version.
 *
 *  OpenBandy is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with OpenBandy. If not, see <http://www.gnu.org/licenses/>.
 *
 *
 * (c) Copyright Philipp Bolliger 2007, ALL RIGHTS RESERVED.
 * 
 *  www.openbandy.org
 */
package org.openbandy.ui;

import java.util.Hashtable;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;
import javax.microedition.midlet.MIDlet;

import org.openbandy.service.LogService;


/**
 * This class represents the main menu of an openbandy MIDlet. It holds a hash
 * table of all the main screens that make up an application.
 * 
 * <br>
 * <br>
 * (c) Copyright P. Bolliger 2007, ALL RIGHTS RESERVED.
 * 
 * @author Philipp Bolliger (philipp@bolliger.name)
 * @version 1.5
 */
public class MainMenu extends List implements CommandListener {

	/* The MIDlet */
	private MIDlet midlet;

	/*
	 * The MIDlet's display, protected static so as it can be accessed by other
	 * UI classes
	 */
	public static Display display;

	/* Screens */
	private Hashtable screens = new Hashtable();

	/* Commands */
	private Command cmdShow;

	private Command cmdBackground;

	private Command cmdExit;

	/**
	 * Create a main menu screen
	 * 
	 * @param midlet
	 *            Reference to the current MIDlet instance
	 * @param display
	 *            Reference to the current display instance
	 */
	public MainMenu(MIDlet midlet, Display display) {
		super("Main Menu", List.IMPLICIT);
		this.midlet = midlet;
		MainMenu.display = display;

		cmdShow = new Command("Show", Command.SCREEN, 1);
		cmdBackground = new Command("Run in Background", Command.SCREEN, 2);
		cmdExit = new Command("Exit", Command.EXIT, 3);

		addCommand(cmdShow);
		addCommand(cmdBackground);
		addCommand(cmdExit);

		setSelectCommand(cmdShow);
		setCommandListener(this);
	}

	/**
	 * Add a (BandyDisplayable) screen to the list of main menu entries.
	 * 
	 * @param displayable
	 *            BandyDisplayable screen that should be listed in the main menu
	 */
	public void addScreen(BandyDisplayable displayable) {
		/* add it to the screen */
		try {
			String title = ((Displayable) displayable).getTitle();
			if (title != null) {
				Integer index = new Integer(append(title, null));
				screens.put(index, displayable);
			}
			else {
				LogService.warn(this, "Displayable has no title");
			}
		}
		catch (ClassCastException cce) {
			LogService.warn(this, "BandyDisplayable not subclass of javax.microedition.lcdui.Displayable");
		}
	}

	/* ********** Method for interface CommandListener ********* */

	/* (non-Javadoc)
	 * @see javax.microedition.lcdui.CommandListener#commandAction(javax.microedition.lcdui.Command, javax.microedition.lcdui.Displayable)
	 */
	public void commandAction(Command c, Displayable d) {
		if (c == cmdShow) {
			Integer index = new Integer(getSelectedIndex());
			BandyDisplayable nextScreen = (BandyDisplayable) screens.get(index);
			nextScreen.show(display, this);
		}
		else if (c == cmdBackground) {
			display.setCurrent(null);
		}
		else if (c == cmdExit) {
			midlet.notifyDestroyed();
		}
	}

}
