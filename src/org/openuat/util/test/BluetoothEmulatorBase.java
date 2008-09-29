///* Copyright Rene Mayrhofer
// * File created 2008-09-23
// * 
// * This program is free software; you can redistribute it and/or modify
// * it under the terms of the GNU Lesser General Public License as published by
// * the Free Software Foundation; either version 2 of the License, or
// * (at your option) any later version.
// */
//package org.openuat.util.test;
//
//import junit.framework.TestCase;
//
//import com.intel.bluetooth.EmulatorTestsHelper;
//
//public abstract class BluetoothEmulatorBase extends TestCase {
//	private Thread serverThread;
//	
//	protected BluetoothEmulatorBase(String s) {
//		super(s);
//	}
//
//	protected void setUp() throws Exception {
//		super.setUp();
//		EmulatorTestsHelper.startInProcessServer();
//		EmulatorTestsHelper.useThreadLocalEmulator();
//		serverThread = EmulatorTestsHelper.runNewEmulatorStack(getServerThread());
//	}
//
//	protected void tearDown() throws Exception {
//		super.tearDown();
//		if ((serverThread != null) && (serverThread.isAlive())) {
//			serverThread.interrupt();
//			serverThread.join();
//		}
//		EmulatorTestsHelper.stopInProcessServer();
//	}
//	
//	abstract protected Runnable getServerThread();
//}
