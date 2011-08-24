/**
 *  Filename: PreferencesForm.java (in org.openbandy.pref)
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

package org.openbandy.pref;

import java.util.Enumeration;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.TextField;

import org.openbandy.service.LogService;
import org.openbandy.service.PreferencesService;
import org.openbandy.ui.BandyDisplayable;


/**
 * This screen displays all stored preferences that are set to be screen
 * modifiable. For each stored preference, a (editable) text field is added to
 * the the form.
 * 
 * <br>
 * <br>
 * (c) Copyright Philipp Bolliger 2008, ALL RIGHTS RESERVED.
 * 
 * @author Philipp Bolliger (philipp@bolliger.name)
 * @version 1.0
 * @see Preferences
 * @see Preference
 * 
 */

public class PreferencesForm extends Form implements BandyDisplayable, CommandListener {

	/** Reference to the MIDlets display */
	protected Display display;

	/** Reference to the MIDlets previous displayable */
	protected Displayable previousDisplayable;

	/** Maximum size of preference value (in text field) */
	private static final int MAX_SIZE = 20;

	/* Commands */
	private Command cmdSave;

	private Command cmdCancel;

	private Command cmdClear;

	/**
	 * Create a new PreferencesForm
	 */
	public PreferencesForm() {
		super("Preferences");

		/* add commands */
		cmdSave = new Command("Ok", Command.OK, 1);
		cmdCancel = new Command("Back", Command.CANCEL, 1);
		cmdClear = new Command("Clear Fields", Command.SCREEN, 2);
		addCommand(cmdSave);
		addCommand(cmdCancel);
		addCommand(cmdClear);

		/* make myself command listener */
		this.setCommandListener(this);
	}

	/**
	 * Delete the form, create a new list entry for every preference that is
	 * stored in the RMS.
	 * 
	 */
	private void update() {

		/* remove all text fields from the screen and the list */
		this.deleteAll();

		/* create text fields and add them to the array and the screen */
		for (Enumeration preferences = Preferences.preferences(); preferences.hasMoreElements();) {
			try {
				Preference preference = (Preference) preferences.nextElement();
				if (preference.isScreenModifiable) {
					/* create new text fiel */
					TextField textField = new TextField(preference.name, preference.value, MAX_SIZE, TextField.ANY);

					/* add text field to screen */
					append(textField);
				}
			}
			catch (Exception e) {
				LogService.error(this, e.getMessage(), e);
			}
		}
	}

	/* ******************* Methods for BandyDisplayable ******** */

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.openbandy.ui.BandyDisplayable#show(javax.microedition.lcdui.Display,
	 *      javax.microedition.lcdui.Displayable)
	 */
	public void show(Display display, Displayable previousDisplayable) {
		this.display = display;
		this.previousDisplayable = previousDisplayable;

		update();

		display.setCurrent(this);
	}

	/* ******************* Methods for CommandListener ******** */

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.microedition.lcdui.CommandListener#commandAction(javax.microedition.lcdui.Command,
	 *      javax.microedition.lcdui.Displayable)
	 */
	public void commandAction(Command c, Displayable d) {
		if (c == cmdCancel) {
			display.setCurrent(previousDisplayable);
		}
		else if (c == cmdSave) {
			for (int i = 0; i < this.size(); i++) {
				try {
					TextField textField = (TextField) get(i);
					PreferencesService.setValue(textField.getLabel(), textField.getString());
				}
				catch (ClassCastException cce) {
					/* do nothing, just ignore */
				}
			}

			/* go back to the previous screen */
			display.setCurrent(previousDisplayable);
		}
		else if (c == cmdClear) {
			for (int i = 0; i < this.size(); i++) {
				try {
					TextField textField = (TextField) get(i);
					PreferencesService.setValue(textField.getLabel(), "");
				}
				catch (ClassCastException cce) {
					/* do nothing, just ignore */
				}
			}

			/* update the list view */
			update();
		}
	}

}
