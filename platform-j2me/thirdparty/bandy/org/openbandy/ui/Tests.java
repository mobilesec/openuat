/**
 *  Filename: Tests.java (in org.openbandy.ui)
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

import java.io.IOException;
import java.util.Enumeration;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.List;

import org.openbandy.service.LogService;
import org.openbandy.test.Test;
import org.openbandy.util.TwoWayHashtable;


/**
 * The Tests screen is the GUI to run unit tests.
 * 
 * <br>
 * <br>
 * (c) Copyright P. Bolliger 2007, ALL RIGHTS RESERVED.
 * 
 * @author Philipp Bolliger (philipp@bolliger.name)
 * @version 1.0
 * @see org.openbandy.test.Test
 */
public class Tests extends List implements CommandListener, BandyDisplayable {

	/* Commands */
	private Command cmdBack;

	private Command cmdRun;

	private Command cmdRunAll;

	private Command cmdReset;

	/* Display reference */
	private Display display;

	/* Previous Screen */
	private Displayable previousDisplayable;

	/* Current list of tests */
	private TwoWayHashtable tests = new TwoWayHashtable();

	/* Images */
	private Image readyImage;

	private Image runningImage;

	private Image passedImage;

	private Image failedImage;

	/* count test runs */
	private int testCounter = 0;

	/**
	 * Create a new tests screen
	 */
	public Tests() {
		super("Tests", List.IMPLICIT);

		try {
			readyImage = Image.createImage("/img/unchecked.png");
			passedImage = Image.createImage("/img/checked.png");
			failedImage = Image.createImage("/img/stop.png");
			runningImage = Image.createImage("/img/running.png");
		}
		catch (IOException ioe) {
			LogService.error(this, "Failed loading images", ioe);
		}

		/* create and add Commands */
		cmdBack = new Command("Back", Command.BACK, 1);
		cmdRun = new Command("Run selected test", Command.SCREEN, 1);
		cmdRunAll = new Command("Run all tests", Command.SCREEN, 1);
		cmdReset = new Command("Reset tests", Command.SCREEN, 2);
		addCommand(cmdBack);
		addCommand(cmdRun);
		addCommand(cmdRunAll);
		addCommand(cmdReset);

		this.setSelectCommand(cmdRun);

		/* make myself command listener */
		this.setCommandListener(this);
	}

	/**
	 * Add a test to the screen
	 * 
	 * @param test
	 *            Test to be added to the gui list
	 */
	public void addTest(Test test) {
		test.setTestsScreen(this);
		Integer index = new Integer(append(" " + test.getName(), readyImage));
		tests.put(index, test);
	}

	/**
	 * Set the test to be ready for execution, an empty checkbox image is shown.
	 * 
	 * @param test
	 *            Test to be set to be ready for execution
	 */
	public void setTestReady(Test test) {
		Integer index = (Integer) tests.getKeyByValue(test);
		set(index.intValue(), test.getName(), readyImage);
	}

	/**
	 * Set the test to be running, an small forward arrow image is shown.
	 * 
	 * @param test
	 *            Test to be set as running
	 */
	public void setTestRunning(Test test) {
		Integer index = (Integer) tests.getKeyByValue(test);
		set(index.intValue(), test.getName(), runningImage);
	}

	/**
	 * Set the test to have successfully executed, a checked box image is shown.
	 * 
	 * @param test
	 *            Test to be set as passed (successfully)
	 */
	public void setTestPassed(Test test) {
		Integer index = (Integer) tests.getKeyByValue(test);
		set(index.intValue(), test.getName(), passedImage);
	}

	/**
	 * Set the test to have failed execution, a red box image is shown.
	 * 
	 * @param test
	 *            Test to be set to have failed execution
	 */
	public void setTestFailed(Test test) {
		Integer index = (Integer) tests.getKeyByValue(test);
		set(index.intValue(), test.getName(), failedImage);
	}

	/**
	 * Run all tests currently member of the list in parallel.
	 * 
	 */
	private void runAllTests() {
		Enumeration elements = tests.getValues();
		while (elements.hasMoreElements()) {
			Test test = (Test) elements.nextElement();
			setTestRunning(test);
			test.start();
		}
		testCounter++;
	}

	private void runSelectedTest() {
		Integer index = new Integer(this.getSelectedIndex());
		Test selectedTest = (Test) tests.getValueByKey(index);
		if (selectedTest != null) {
			setTestRunning(selectedTest);
			selectedTest.start();
		}
	}

	/* ************* Methods for BandyDisplayable **************** */

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.openbandy.ui.BandyDisplayable#show(javax.microedition.lcdui.Display,
	 *      javax.microedition.lcdui.Displayable)
	 */
	public void show(Display display, Displayable previousDisplayable) {
		this.display = display;
		this.previousDisplayable = previousDisplayable;
		display.setCurrent(this);
	}

	/* ************* Method for CommandListener **************** */

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.microedition.lcdui.CommandListener#commandAction(javax.microedition.lcdui.Command,
	 *      javax.microedition.lcdui.Displayable)
	 */
	public void commandAction(Command c, Displayable d) {
		if (c == cmdBack) {
			if ((display != null) && (previousDisplayable != null)) {
				display.setCurrent(previousDisplayable);
			}
			else {
				LogService.warn(this, "Previous display not set");
			}
		}
		else if (c == cmdRunAll) {
			runAllTests();
		}
		else if (c == cmdRun) {
			runSelectedTest();
		}
		else if (c == cmdReset) {
			Enumeration elements = tests.getValues();
			while (elements.hasMoreElements()) {
				Test test = (Test) elements.nextElement();
				setTestReady(test);
			}
		}
	}

}
