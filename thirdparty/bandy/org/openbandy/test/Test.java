/**
 *  Filename: Test.java (in org.openbandy.test)
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

package org.openbandy.test;

import org.openbandy.service.LogService;
import org.openbandy.ui.Tests;


/**
 * This class represents a generic unit test. It provides the basic methods that
 * are used by the Test GUI to start and stop the tests.
 * 
 * <br>
 * <br>
 * (c) Copyright P. Bolliger 2007, ALL RIGHTS RESERVED.
 * 
 * @author Philipp Bolliger (philipp@bolliger.name)
 * @version 1.0
 */
public abstract class Test implements Runnable {

	/* Name of a test, should give evidence what is tested */
	private String name = "";

	/* Callback to the tests screen, used to show progress and result */
	protected Tests testsScreen = null;

	/* The point in time when the test is started */
	protected long startTime = 0;

	/**
	 * Create a new test
	 * 
	 * @param name The name of the test as it will appear in the GUI
	 */
	public Test(String name) {
		this.name = name;
	}

	/* ***** Getter and Setter Methods ***** */

	public void setTestsScreen(Tests tests) {
		this.testsScreen = tests;
	}

	public String getName() {
		return name;
	}

	/* ***** Methods to time the test ***** */

	protected void testStarted() {
		testsScreen.setTestRunning(this);
		LogService.info(this, "Started test '" + name + "'");
		startTime = System.currentTimeMillis();
	}

	protected void testFinished() {
		if (startTime > 0) {
			double duration = (System.currentTimeMillis() - startTime) / 1000.0;
			LogService.info(this, "Finished test '" + name + "' after " + duration + " seconds");
		}
		else {
			LogService.info(this, "Finished test '" + name + "'");
		}
	}

	/* ***** Thread related Methods ***** */

	public void start() {
		Thread testThread = new Thread(this);
		testThread.start();
	}

}
