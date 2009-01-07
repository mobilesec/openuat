package org.openuat.apps.groupkey;

import javax.microedition.lcdui.Display;
import javax.microedition.midlet.MIDlet;

public class appStubMidlet extends MIDlet {

	private Display display;
	private appPair2Stub app;
	private Thread appThread;
	
	public appStubMidlet(){
		display = Display.getDisplay(this);
		app = new appPair2Stub(display);
		appThread = new Thread(app);
	}
	public Display getDisplay(){
		return display;
	}
	
	public void startApp() {
		appThread.start();
	}
	public void pauseApp() {}
	public void destroyApp(boolean unconditional) {}

}
